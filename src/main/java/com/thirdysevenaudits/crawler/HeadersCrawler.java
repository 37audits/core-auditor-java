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
package com.thirdysevenaudits.crawler;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.thirdysevenaudits.auditor.BasicAuth;
import com.thirdysevenaudits.util.HeadersUtil;

public class HeadersCrawler {
    // cache hosts that needed relaxed SSL
    private final ConcurrentHashMap<String, Boolean> insecureHostCache = new ConcurrentHashMap<>();
    private final HttpClient secureClient;
    private Map<String, String> headers = new HashMap<String, String>();

    public HeadersCrawler(String userAgent, BasicAuth basicAuth) {
        this(userAgent, null, true, basicAuth);
    }

    public HeadersCrawler(String userAgent, Map<String, String> extraHeaders, boolean followRedirect,
            BasicAuth basicAuth) {
        this.headers.put("user-agent", userAgent);
        if (extraHeaders != null) {
            this.headers.putAll(extraHeaders);
        }
        this.secureClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects((followRedirect) ? HttpClient.Redirect.NORMAL : HttpClient.Redirect.NEVER).build();
        if (basicAuth != null) {
            String userAndPassword = basicAuth.username() + ":" + basicAuth.password();
            var value = Base64.getEncoder().encodeToString(userAndPassword.getBytes());
            this.headers.put("Authorization", "Basic " + value);
        }
    }

    // create an "insecure" HttpClient (trust all) - re-useable
    private HttpClient createInsecureClient() throws Exception {
        var trustAll = new TrustManager[] { new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] certs, String authType) {
            }

            public void checkServerTrusted(X509Certificate[] certs, String authType) {
            }

            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        } };
        var sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAll, new SecureRandom());
        // hostname verifier that accepts any host
        var sslParams = new SSLParameters();
        var client = HttpClient.newBuilder().sslContext(sslContext).sslParameters(sslParams)
                .connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL)
                // no direct API for hostname verifier, but Java 11+ uses the SSLParameters
                .build();
        return client;
    }

    public Map<String, List<String>> fetch(String url) throws Exception {
        return fetch(url, secureClient, true);
    }

    public Map<String, List<String>> fetch(String url, boolean addBasicAuthentication) throws Exception {
        var uri = URI.create(url);
        var host = uri.getHost();

        // fast path: if we already know this host needed insecure mode, use insecure
        // client directly
        if (insecureHostCache.getOrDefault(host, false)) {
            return HeadersUtil.convertKeyToLowercase(fetch(url, createInsecureClient(), addBasicAuthentication));
        }

        // try secure client first
        try {
            return HeadersUtil.convertKeyToLowercase(fetch(url, secureClient, addBasicAuthentication));
        } catch (Exception e) {
            // detect PKIX / SSL handshake exceptions - fallback only on those
            Throwable cause = e;
            var isSslProblem = false;
            while (cause != null) {
                var cls = cause.getClass().getName();
                if (cls.contains("SSLHandshakeException") || cls.contains("SSLPeerUnverifiedException")
                        || (cause.getMessage() != null && cause.getMessage().contains("PKIX"))) {
                    isSslProblem = true;
                    break;
                }
                cause = cause.getCause();
            }
            if (!isSslProblem)
                throw e; // some other real error - bubble up

            // cache host as requiring insecure mode
            insecureHostCache.put(host, true);
            return HeadersUtil.convertKeyToLowercase(fetch(url, createInsecureClient(), addBasicAuthentication));
        }
    }

    private Map<String, List<String>> fetch(String url, HttpClient client, boolean addBasicAuthentication)
            throws Exception {
        var req = createHttpRequest(url, "HEAD", addBasicAuthentication);
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

        var statusCode = resp.statusCode();
        if (statusCode == 405) {
            req = createHttpRequest(url, "GET", addBasicAuthentication);
            resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        }

        statusCode = resp.statusCode();

        Map<String, List<String>> headers = new HashMap<String, List<String>>();

        headers.put("http-status-code", Collections.singletonList(String.valueOf(statusCode)));
        headers.putAll(resp.headers().map());

        return headers;
    }

    private HttpRequest createHttpRequest(String url, String method, boolean addBasicAuthentication) {
        var builder = HttpRequest.newBuilder(URI.create(url));

        headers.forEach((k, v) -> {

            if (!(k.equals("Authorization") && !addBasicAuthentication)) {
                builder.header(k, v);
            }

        });

        return builder.timeout(Duration.ofSeconds(20)).method(method, BodyPublishers.noBody()).build();
    }
}
