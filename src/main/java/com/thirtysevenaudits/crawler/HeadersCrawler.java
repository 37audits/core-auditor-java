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
package com.thirtysevenaudits.crawler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import com.thirtysevenaudits.auditor.BasicAuth;
import com.thirtysevenaudits.http.FetchResponse;
import com.thirtysevenaudits.http.HttpFetcher;

/**
 * Response headers of a URL, with the status code under {@code http-status-code}.
 *
 * @deprecated use {@link HttpFetcher} ({@code AbstractLambdaAuditor.httpFetcher(url, basicAuth)}) and
 *             {@link FetchResponse#headerMap()}. This class now delegates to it; unlike the fetcher it sends
 *             the Basic Auth credentials to every host when asked to.
 */
@Deprecated
public class HeadersCrawler {
    private final HttpFetcher fetcher;
    private final String authorization;

    public HeadersCrawler(String userAgent, BasicAuth basicAuth) {
        this(userAgent, null, true, basicAuth);
    }

    /**
     * Requests are signed with {@link WebBotAuthSigner#defaultSigner()} when the
     * environment provides a key; see the five-argument constructor to control
     * signing explicitly.
     */
    public HeadersCrawler(String userAgent, Map<String, String> extraHeaders, boolean followRedirect,
            BasicAuth basicAuth) {
        this(userAgent, extraHeaders, followRedirect, basicAuth, WebBotAuthSigner.defaultSigner().orElse(null));
    }

    public HeadersCrawler(String userAgent, Map<String, String> extraHeaders, boolean followRedirect,
            BasicAuth basicAuth, WebBotAuthSigner signer) {
        // headers only: the body is not downloaded
        HttpFetcher.Builder builder = HttpFetcher.builder(userAgent).signer(signer).followRedirects(followRedirect)
                .maxBodyBytes(0);
        if (extraHeaders != null) {
            extraHeaders.forEach(builder::header);
        }
        this.fetcher = builder.build();
        if (basicAuth != null) {
            String userAndPassword = basicAuth.username() + ":" + basicAuth.password();
            this.authorization = "Basic "
                    + Base64.getEncoder().encodeToString(userAndPassword.getBytes(StandardCharsets.UTF_8));
        } else {
            this.authorization = null;
        }
    }

    public Map<String, List<String>> fetch(String url) throws Exception {
        return fetch(url, true);
    }

    public Map<String, List<String>> fetch(String url, boolean addBasicAuthentication) throws Exception {
        return fetcher.headOrGet(url, authorization(addBasicAuthentication)).headerMap();
    }

    public Map<String, List<String>> doGet(String url) throws Exception {
        return doGet(url, true);
    }

    public Map<String, List<String>> doGet(String url, boolean addBasicAuthentication) throws Exception {
        return send("GET", url, addBasicAuthentication);
    }

    public Map<String, List<String>> doHead(String url) throws Exception {
        return doHead(url, true);
    }

    public Map<String, List<String>> doHead(String url, boolean addBasicAuthentication) throws Exception {
        return send("HEAD", url, addBasicAuthentication);
    }

    private Map<String, List<String>> send(String method, String url, boolean addBasicAuthentication)
            throws IOException {
        return fetcher.send(method, url, authorization(addBasicAuthentication)).headerMap();
    }

    private Map<String, String> authorization(boolean addBasicAuthentication) {
        return authorization != null && addBasicAuthentication ? Map.of("Authorization", authorization) : Map.of();
    }
}
