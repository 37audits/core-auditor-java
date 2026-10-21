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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.thirtysevenaudits.auditor.BasicAuth;
import com.thirtysevenaudits.auditor.SiteBlockedException;
import com.thirtysevenaudits.crawler.WebBotAuthSigner;
import com.thirtysevenaudits.crawler.WebBotAuthSignerTest;

class HttpFetcherTest {

    private static final BasicAuth CREDENTIALS = new BasicAuth("user", "secret");

    private HttpServer httpServer;
    private String localhost;
    private String loopback;
    private final List<String> receivedMethods = new CopyOnWriteArrayList<>();
    private final List<Headers> receivedHeaders = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/ok", exchange -> respond(exchange, 200, "text/html; charset=UTF-8", bytes("ok")));
        httpServer.createContext("/head-not-allowed", exchange -> {
            if ("HEAD".equals(exchange.getRequestMethod())) {
                respond(exchange, 405, "text/plain", new byte[0]);
            } else {
                respond(exchange, 200, "text/plain", bytes("ok"));
            }
        });
        httpServer.createContext("/redirect", exchange -> {
            record(exchange);
            exchange.getResponseHeaders().add("Location", "/ok");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        httpServer.createContext("/walled", exchange -> {
            record(exchange);
            // Radware's real Location: an unencoded space and parentheses in the query (the user agent).
            exchange.getResponseHeaders().add("Location", "http://validate.perfdrive.com/c77b/?ssa=1"
                    + "&sst=37AuditsBot/1.0 (+https://www.37audits.com/bot)&ssu=");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        httpServer.createContext("/gzip", exchange -> {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
                gzip.write(bytes("<p>compressed</p>"));
            }
            exchange.getResponseHeaders().add("Content-Encoding", "gzip");
            respond(exchange, 200, "text/html", out.toByteArray());
        });
        httpServer.createContext("/latin1", exchange -> respond(exchange, 200, "text/html",
                "<html><head><meta charset=\"ISO-8859-1\"></head><body>café</body></html>"
                        .getBytes(StandardCharsets.ISO_8859_1)));
        httpServer.createContext("/echo", exchange -> {
            byte[] received = exchange.getRequestBody().readAllBytes();
            respond(exchange, 200, "text/plain", received);
        });
        httpServer.createContext("/post-redirect", exchange -> {
            record(exchange);
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().add("Location", "/echo");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        httpServer.createContext("/post-redirect-307", exchange -> {
            record(exchange);
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().add("Location", "/echo");
            exchange.sendResponseHeaders(307, -1);
            exchange.close();
        });
        httpServer.createContext("/big", exchange -> respond(exchange, 200, "text/plain", new byte[100_000]));
        httpServer.createContext("/slow", exchange -> {
            try {
                Thread.sleep(2_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            respond(exchange, 200, "text/plain", bytes("late"));
        });
        httpServer.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        httpServer.start();
        localhost = "http://localhost:" + httpServer.getAddress().getPort();
        loopback = "http://127.0.0.1:" + httpServer.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        httpServer.stop(0);
    }

    @Test
    void get_sendsTheUserAgentAndReturnsTheBody() throws Exception {
        FetchResponse response = fetcher().get(localhost + "/ok");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.text()).isEqualTo("ok");
        assertThat(response.header("Content-Type")).isEqualTo("text/html; charset=UTF-8");
        assertThat(response.headers()).containsKey("content-type");
        assertThat(response.headerMap().get(FetchResponse.HTTP_STATUS_CODE)).containsExactly("200");
        assertThat(receivedHeaders.get(0).getFirst("User-Agent")).isEqualTo("test-agent");
    }

    @Test
    void redirectToABotWall_throwsBlockedWithoutFollowingIt() {
        List<SiteBlockedException> reported = new CopyOnWriteArrayList<>();
        HttpFetcher fetcher = HttpFetcher.builder("test-agent").onBlocked(reported::add).build();

        assertThatThrownBy(() -> fetcher.get(localhost + "/walled")).isInstanceOf(SiteBlockedException.class)
                .hasMessage("The site redirected 37AuditsBot to a bot challenge (validate.perfdrive.com) instead "
                        + "of answering (HTTP 302).")
                .satisfies(e -> {
                    SiteBlockedException blocked = (SiteBlockedException) e;
                    assertThat(blocked.url()).isEqualTo(localhost + "/walled");
                    assertThat(blocked.statusCode()).isEqualTo(302);
                    assertThat(blocked.challenge()).isTrue();
                    assertThat(reported).containsExactly(blocked);
                });
    }

    @Test
    void onBlocked_survivesToBuilder() {
        List<SiteBlockedException> reported = new CopyOnWriteArrayList<>();
        HttpFetcher fetcher = HttpFetcher.builder("test-agent").onBlocked(reported::add).build().toBuilder()
                .maxBodyBytes(0).build();

        assertThatThrownBy(() -> fetcher.head(localhost + "/walled")).isInstanceOf(SiteBlockedException.class);
        assertThat(reported).hasSize(1);
    }

    @Test
    void basicAuth_isSentToTheAuditedHostOnly() throws Exception {
        HttpFetcher fetcher = HttpFetcher.builder("test-agent").auditedUrl(localhost + "/").basicAuth(CREDENTIALS)
                .build();

        fetcher.get(localhost + "/ok");
        fetcher.get(loopback + "/ok");

        assertThat(receivedHeaders.get(0).getFirst("Authorization")).startsWith("Basic ");
        assertThat(receivedHeaders.get(1).containsKey("Authorization")).isFalse();
    }

    @Test
    void basicAuth_isNotSentWithoutAnAuditedUrl() throws Exception {
        HttpFetcher.builder("test-agent").basicAuth(CREDENTIALS).build().get(localhost + "/ok");

        assertThat(receivedHeaders.get(0).containsKey("Authorization")).isFalse();
    }

    @Test
    void isAuditedHost_ignoresCaseAndWww() {
        HttpFetcher fetcher = HttpFetcher.builder("test-agent").auditedUrl("https://Example.com/page").build();

        assertThat(fetcher.isAuditedHost("www.example.com")).isTrue();
        assertThat(fetcher.isAuditedHost("example.com")).isTrue();
        assertThat(fetcher.isAuditedHost("cdn.example.com")).isFalse();
        assertThat(fetcher.isAuditedHost("www.googletagmanager.com")).isFalse();
    }

    @Test
    void failures_alwaysCarryAMessage() {
        String closedPort = "http://127.0.0.1:1/";

        assertThatThrownBy(() -> fetcher().get(closedPort)).isInstanceOf(IOException.class)
                .satisfies(e -> assertThat(e.getMessage()).isNotBlank());
    }

    @Test
    void auditedUrl_toleratesAMalformedUrl() {
        HttpFetcher fetcher = HttpFetcher.builder("test-agent").auditedUrl("not a url").basicAuth(CREDENTIALS)
                .build();

        assertThat(fetcher.isAuditedHost("example.com")).isFalse();
    }

    @Test
    void requests_areSignedOnEveryHop() throws Exception {
        WebBotAuthSigner signer = WebBotAuthSigner.of(WebBotAuthSignerTest.newKey(), "https://www.37audits.com");
        HttpFetcher fetcher = HttpFetcher.builder("test-agent").signer(signer).build();

        FetchResponse response = fetcher.get(localhost + "/redirect");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(receivedHeaders).hasSize(2);
        for (Headers headers : receivedHeaders) {
            assertThat(headers.getFirst("Signature-Agent")).isEqualTo("\"https://www.37audits.com\"");
            assertThat(headers.getFirst("Signature")).startsWith("sig1=:");
        }
    }

    @Test
    void redirects_areFollowedAndTheFinalUrlReported() throws Exception {
        FetchResponse response = fetcher().get(localhost + "/redirect");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.requestedUri().getPath()).isEqualTo("/redirect");
        assertThat(response.uri().getPath()).isEqualTo("/ok");
    }

    @Test
    void redirects_areNotFollowedWhenDisabled() throws Exception {
        FetchResponse response = fetcher().toBuilder().followRedirects(false).build().get(localhost + "/redirect");

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.header("location")).isEqualTo("/ok");
        assertThat(response.uri().getPath()).isEqualTo("/redirect");
    }

    @Test
    void send_postsTheBodyWithTheGivenContentType() throws Exception {
        FetchResponse response = fetcher().send("POST", localhost + "/echo", Map.of("content-type", "text/xml"),
                bytes("<methodCall/>"));

        assertThat(receivedMethods).containsExactly("POST");
        assertThat(receivedHeaders.get(0).getFirst("Content-Type")).isEqualTo("text/xml");
        assertThat(receivedHeaders.get(0).getFirst("User-Agent")).isEqualTo("test-agent");
        assertThat(response.text()).isEqualTo("<methodCall/>");
    }

    @Test
    void send_dropsTheBodyWhenA302TurnsThePostIntoAGet() throws Exception {
        FetchResponse response = fetcher().send("POST", localhost + "/post-redirect", Map.of(), bytes("payload"));

        assertThat(receivedMethods).containsExactly("POST", "GET");
        assertThat(response.text()).isEmpty();
    }

    @Test
    void send_resendsTheBodyOnA307() throws Exception {
        FetchResponse response = fetcher().send("POST", localhost + "/post-redirect-307", Map.of(),
                bytes("payload"));

        assertThat(receivedMethods).containsExactly("POST", "POST");
        assertThat(response.text()).isEqualTo("payload");
    }

    @Test
    void headOrGet_fallsBackToGetOn405() throws Exception {
        FetchResponse response = fetcher().headOrGet(localhost + "/head-not-allowed");

        assertThat(receivedMethods).containsExactly("HEAD", "GET");
        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void head_doesNotFallBack() throws Exception {
        FetchResponse response = fetcher().head(localhost + "/head-not-allowed");

        assertThat(receivedMethods).containsExactly("HEAD");
        assertThat(response.statusCode()).isEqualTo(405);
        assertThat(response.body()).isEmpty();
    }

    @Test
    void gzipBodies_areDecoded() throws Exception {
        FetchResponse response = fetcher().get(localhost + "/gzip");

        assertThat(receivedHeaders.get(0).getFirst("Accept-Encoding")).contains("gzip");
        assertThat(response.text()).isEqualTo("<p>compressed</p>");
        assertThat(response.header("content-encoding")).isEqualTo("gzip");
    }

    @Test
    void extraHeaders_overrideTheDefaults() throws Exception {
        fetcher().get(localhost + "/ok", Map.of("user-agent", "Mozilla/5.0", "accept-encoding", "br"));

        assertThat(receivedHeaders.get(0).getFirst("User-Agent")).isEqualTo("Mozilla/5.0");
        assertThat(receivedHeaders.get(0).getFirst("Accept-Encoding")).isEqualTo("br");
    }

    @Test
    void text_usesTheMetaCharsetWhenTheHeaderHasNone() throws Exception {
        assertThat(fetcher().get(localhost + "/latin1").text()).contains("café");
    }

    @Test
    void body_isCutAtTheLimit() throws Exception {
        FetchResponse response = fetcher().toBuilder().maxBodyBytes(1_000).build().get(localhost + "/big");

        assertThat(response.body()).hasSize(1_000);
        assertThat(response.truncated()).isTrue();
    }

    @Test
    void slowResponses_timeOut() {
        HttpFetcher fetcher = fetcher().toBuilder().requestTimeout(Duration.ofMillis(300)).build();

        assertThatThrownBy(() -> fetcher.get(localhost + "/slow")).isInstanceOf(HttpTimeoutException.class);
    }

    private HttpFetcher fetcher() {
        return HttpFetcher.builder("test-agent").build();
    }

    private void record(HttpExchange exchange) {
        receivedMethods.add(exchange.getRequestMethod());
        receivedHeaders.add(exchange.getRequestHeaders());
    }

    private void respond(HttpExchange exchange, int statusCode, String contentType, byte[] body) throws IOException {
        record(exchange);
        exchange.getResponseHeaders().add("Content-Type", contentType);
        boolean head = "HEAD".equals(exchange.getRequestMethod());
        exchange.sendResponseHeaders(statusCode, head || body.length == 0 ? -1 : body.length);
        if (!head && body.length > 0) {
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        } else {
            exchange.close();
        }
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
