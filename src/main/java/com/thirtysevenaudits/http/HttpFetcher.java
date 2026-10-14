/*
 * Copyright © 2026 37 Audits (thiago.moreira@37audits.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.thirtysevenaudits.http;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodySubscriber;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.thirtysevenaudits.auditor.BasicAuth;
import com.thirtysevenaudits.crawler.WebBotAuthSigner;

/**
 * The one HTTP client of the auditors, on {@link java.net.http.HttpClient}. Every request it sends:
 * <ul>
 * <li>carries the bot's {@code User-Agent} and a fresh Web Bot Auth signature (when a signer is set), on every
 * redirect hop too;</li>
 * <li>carries the audited site's Basic Auth credentials only when it goes to the audited host (or its {@code www.}
 * twin), never to a third-party host;</li>
 * <li>times out (10 s to connect, 20 s for the whole exchange by default);</li>
 * <li>retries once without certificate validation when the TLS handshake fails, and remembers the host;</li>
 * <li>reads at most {@code maxBodyBytes} of the body (5 MB by default) and decodes {@code gzip} / {@code deflate}.</li>
 * </ul>
 * Redirects are followed by the fetcher itself (up to {@value #MAX_REDIRECTS}), so the headers above are
 * recomputed for each hop. Instances are immutable and thread-safe; create one per audit with
 * {@code AbstractLambdaAuditor.httpFetcher(url, basicAuth)} and reuse it for every request of the audit. Parse HTML
 * with jsoup: {@code Jsoup.parse(response.text(), response.uri().toString())}.
 */
public final class HttpFetcher {

    public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    public static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(20);
    public static final int DEFAULT_MAX_BODY_BYTES = 5 * 1024 * 1024;
    public static final int MAX_REDIRECTS = 10;

    private static final Set<Integer> REDIRECT_CODES = Set.of(301, 302, 303, 307, 308);

    private final Builder settings;
    private final HttpClient secureClient;
    private volatile HttpClient insecureClient;
    private final Set<String> insecureHosts = ConcurrentHashMap.newKeySet();

    private HttpFetcher(Builder settings) {
        this.settings = settings.copy();
        this.secureClient = newClient(null);
    }

    public static Builder builder(String userAgent) {
        return new Builder(userAgent);
    }

    /** A builder pre-filled with this fetcher's settings, to derive a fetcher that differs in one setting. */
    public Builder toBuilder() {
        return settings.copy();
    }

    public FetchResponse get(String url) throws IOException {
        return send("GET", url, Map.of());
    }

    public FetchResponse get(String url, Map<String, String> extraHeaders) throws IOException {
        return send("GET", url, extraHeaders);
    }

    public FetchResponse head(String url) throws IOException {
        return send("HEAD", url, Map.of());
    }

    /** HEAD, then GET when the server answers HEAD with HTTP 405. */
    public FetchResponse headOrGet(String url) throws IOException {
        return headOrGet(url, Map.of());
    }

    public FetchResponse headOrGet(String url, Map<String, String> extraHeaders) throws IOException {
        FetchResponse response = send("HEAD", url, extraHeaders);
        return response.statusCode() == 405 ? send("GET", url, extraHeaders) : response;
    }

    /**
     * Sends {@code method} (without a body) to {@code url}. {@code extraHeaders} are added to, and override, the
     * standard headers (a {@code User-Agent} here replaces the bot's for this request).
     *
     * @throws IOException
     *             on a network error, a TLS error the insecure retry could not get past, or a timeout
     *             ({@link HttpTimeoutException}).
     */
    public FetchResponse send(String method, String url, Map<String, String> extraHeaders) throws IOException {
        URI requested = URI.create(url);
        URI current = requested;
        String currentMethod = method;

        for (int hop = 0;; hop++) {
            Exchange exchange = exchange(currentMethod, current, extraHeaders);
            int status = exchange.statusCode;
            String location = first(exchange.headers, "location");

            if (!settings.followRedirects || !REDIRECT_CODES.contains(status) || location == null
                    || hop >= MAX_REDIRECTS) {
                return new FetchResponse(status, requested, current, exchange.headers, exchange.body,
                        exchange.version, exchange.elapsed, exchange.truncated);
            }

            current = current.resolve(location.trim().replace(" ", "%20"));
            if (status == 303 && !"HEAD".equals(currentMethod)) {
                currentMethod = "GET";
            }
        }
    }

    private Exchange exchange(String method, URI uri, Map<String, String> extraHeaders) throws IOException {
        String host = uri.getHost();
        if (host != null && insecureHosts.contains(host)) {
            return exchange(insecureClient(), method, uri, extraHeaders);
        }
        try {
            return exchange(secureClient, method, uri, extraHeaders);
        } catch (IOException e) {
            if (host == null || !isTlsProblem(e)) {
                throw e;
            }
            insecureHosts.add(host);
            return exchange(insecureClient(), method, uri, extraHeaders);
        }
    }

    private Exchange exchange(HttpClient client, String method, URI uri, Map<String, String> extraHeaders)
            throws IOException {
        HttpRequest request = request(method, uri, extraHeaders);
        long[] headersAt = new long[1];
        long start = System.nanoTime();

        CompletableFuture<HttpResponse<Capped>> future = client.sendAsync(request, info -> {
            headersAt[0] = System.nanoTime();
            return new CappedSubscriber(settings.maxBodyBytes);
        });

        HttpResponse<Capped> response;
        try {
            response = future.get(settings.requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new HttpTimeoutException("Request to " + uri + " timed out after " + settings.requestTimeout);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fetching " + uri, e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException io) {
                throw io;
            }
            throw new IOException(cause == null ? e : cause);
        }

        Map<String, List<String>> headers = new LinkedHashMap<>();
        response.headers().map().forEach((name, values) -> headers.put(name.toLowerCase(Locale.ROOT), values));

        Capped capped = response.body();
        byte[] body = decode(capped.bytes, first(headers, "content-encoding"), settings.maxBodyBytes);

        return new Exchange(response.statusCode(), headers, body, response.version(),
                Duration.ofNanos(headersAt[0] - start), capped.truncated);
    }

    private HttpRequest request(String method, URI uri, Map<String, String> extraHeaders) {
        Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.put("User-Agent", settings.userAgent);
        headers.put("Accept-Encoding", "gzip, deflate");
        headers.putAll(settings.headers);
        if (settings.basicAuth != null && isAuditedHost(uri.getHost())) {
            String credentials = settings.basicAuth.username() + ":" + settings.basicAuth.password();
            headers.put("Authorization",
                    "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
        }
        if (extraHeaders != null) {
            headers.putAll(extraHeaders);
        }
        if (settings.signer != null) {
            headers.putAll(settings.signer.headers(uri));
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(settings.requestTimeout)
                .method(method, BodyPublishers.noBody());
        headers.forEach(builder::header);
        return builder.build();
    }

    /** Whether {@code host} is the audited host, ignoring case and a leading {@code www.}. */
    boolean isAuditedHost(String host) {
        if (host == null || settings.auditedHost == null) {
            return false;
        }
        return stripWww(host).equalsIgnoreCase(stripWww(settings.auditedHost));
    }

    private static String stripWww(String host) {
        return host.regionMatches(true, 0, "www.", 0, 4) ? host.substring(4) : host;
    }

    private HttpClient insecureClient() throws IOException {
        if (insecureClient == null) {
            synchronized (this) {
                if (insecureClient == null) {
                    insecureClient = newClient(trustAllContext());
                }
            }
        }
        return insecureClient;
    }

    private HttpClient newClient(SSLContext sslContext) {
        HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(settings.connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER);
        if (settings.version != null) {
            builder.version(settings.version);
        }
        if (sslContext != null) {
            builder.sslContext(sslContext);
        }
        return builder.build();
    }

    private static SSLContext trustAllContext() throws IOException {
        TrustManager[] trustAll = { new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] certs, String authType) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] certs, String authType) {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        } };
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, trustAll, new SecureRandom());
            return context;
        } catch (Exception e) {
            throw new IOException("Unable to create the relaxed TLS context", e);
        }
    }

    private static boolean isTlsProblem(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            String name = cause.getClass().getName();
            if (name.contains("SSLHandshakeException") || name.contains("SSLPeerUnverifiedException")
                    || (cause.getMessage() != null && cause.getMessage().contains("PKIX"))) {
                return true;
            }
        }
        return false;
    }

    private static byte[] decode(byte[] body, String contentEncoding, int maxBytes) {
        if (body.length == 0 || contentEncoding == null) {
            return body;
        }
        String encoding = contentEncoding.trim().toLowerCase(Locale.ROOT);
        try {
            InputStream in;
            if (encoding.equals("gzip") || encoding.equals("x-gzip")) {
                in = new GZIPInputStream(new ByteArrayInputStream(body));
            } else if (encoding.equals("deflate")) {
                in = new InflaterInputStream(new ByteArrayInputStream(body));
            } else {
                return body; // br, zstd, ...: left as received
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            try (in) {
                int read;
                while (out.size() < maxBytes && (read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, Math.min(read, maxBytes - out.size()));
                }
            } catch (IOException e) {
                // a truncated stream: keep what could be decoded
                if (out.size() == 0) {
                    return body;
                }
            }
            return out.toByteArray();
        } catch (IOException e) {
            return body;
        }
    }

    private static String first(Map<String, List<String>> headers, String name) {
        List<String> values = headers.get(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    private record Exchange(int statusCode, Map<String, List<String>> headers, byte[] body,
            HttpClient.Version version, Duration elapsed, boolean truncated) {
    }

    private record Capped(byte[] bytes, boolean truncated) {
    }

    /** Collects the body up to a limit, then cancels the rest of the download. */
    private static final class CappedSubscriber implements BodySubscriber<Capped> {
        private final CompletableFuture<Capped> result = new CompletableFuture<>();
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private final int limit;
        private Flow.Subscription subscription;

        CappedSubscriber(int limit) {
            this.limit = limit;
        }

        @Override
        public CompletionStage<Capped> getBody() {
            return result;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(List<ByteBuffer> items) {
            if (result.isDone()) {
                return;
            }
            for (ByteBuffer item : items) {
                int room = limit - out.size();
                if (item.remaining() > room) {
                    byte[] chunk = new byte[room];
                    item.get(chunk);
                    out.write(chunk, 0, room);
                    result.complete(new Capped(out.toByteArray(), true));
                    subscription.cancel();
                    return;
                }
                byte[] chunk = new byte[item.remaining()];
                item.get(chunk);
                out.write(chunk, 0, chunk.length);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            result.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            result.complete(new Capped(out.toByteArray(), false));
        }
    }

    /** Settings of an {@link HttpFetcher}. */
    public static final class Builder {
        private final String userAgent;
        private String auditedHost;
        private BasicAuth basicAuth;
        private WebBotAuthSigner signer;
        private Duration connectTimeout = DEFAULT_CONNECT_TIMEOUT;
        private Duration requestTimeout = DEFAULT_REQUEST_TIMEOUT;
        private boolean followRedirects = true;
        private HttpClient.Version version;
        private int maxBodyBytes = DEFAULT_MAX_BODY_BYTES;
        private final Map<String, String> headers = new HashMap<>();

        private Builder(String userAgent) {
            this.userAgent = userAgent;
        }

        /** The audited page: Basic Auth is sent only to its host. */
        public Builder auditedUrl(String auditedUrl) {
            this.auditedHost = auditedUrl == null ? null : URI.create(auditedUrl).getHost();
            return this;
        }

        public Builder basicAuth(BasicAuth basicAuth) {
            this.basicAuth = basicAuth;
            return this;
        }

        /** Signs every request; {@code null} sends them unsigned. */
        public Builder signer(WebBotAuthSigner signer) {
            this.signer = signer;
            return this;
        }

        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        /** Limit for one exchange (request, response headers and body). */
        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
            return this;
        }

        public Builder followRedirects(boolean followRedirects) {
            this.followRedirects = followRedirects;
            return this;
        }

        /** Forces an HTTP version; by default HTTP/2 is offered and HTTP/1.1 accepted. */
        public Builder version(HttpClient.Version version) {
            this.version = version;
            return this;
        }

        public Builder maxBodyBytes(int maxBodyBytes) {
            this.maxBodyBytes = maxBodyBytes;
            return this;
        }

        /** A header sent with every request of the fetcher. */
        public Builder header(String name, String value) {
            this.headers.put(name, value);
            return this;
        }

        public HttpFetcher build() {
            return new HttpFetcher(this);
        }

        private Builder copy() {
            Builder copy = new Builder(userAgent);
            copy.auditedHost = auditedHost;
            copy.basicAuth = basicAuth;
            copy.signer = signer;
            copy.connectTimeout = connectTimeout;
            copy.requestTimeout = requestTimeout;
            copy.followRedirects = followRedirects;
            copy.version = version;
            copy.maxBodyBytes = maxBodyBytes;
            copy.headers.putAll(headers);
            return copy;
        }
    }
}
