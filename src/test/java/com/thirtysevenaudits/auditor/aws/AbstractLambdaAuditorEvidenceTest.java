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
package com.thirtysevenaudits.auditor.aws;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.thirtysevenaudits.auditor.BasicAuth;
import com.thirtysevenaudits.auditor.Check;
import com.thirtysevenaudits.auditor.CheckStatus;
import com.thirtysevenaudits.auditor.Request;
import com.thirtysevenaudits.auditor.Response;
import com.thirtysevenaudits.auditor.Software;
import com.thirtysevenaudits.evidence.EvidenceUploader;

class AbstractLambdaAuditorEvidenceTest {

    private HttpServer httpServer;
    private String base;

    @BeforeEach
    void setUp() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/robots.txt", exchange -> {
            byte[] body = "User-agent: *\nDisallow:".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain");
            exchange.getResponseHeaders().add("Set-Cookie", "session=secret");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        httpServer.start();
        base = "http://localhost:" + httpServer.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        httpServer.stop(0);
    }

    @Test
    void uploadsTheFetchedResponsesTheStackAndManualEntries() throws IOException {
        FetchingAuditor auditor = new FetchingAuditor(true);

        Response response = auditor.handleRequest(request("https://storage.example/put?sig=1",
                List.of(new Software("liferay", "dxp", "2024.q4"))), null);

        assertThat(response.status()).isEqualTo(CheckStatus.SUCCESS);
        assertThat(response.evidence()).isNotNull();
        assertThat(response.evidence().uploaded()).isTrue();
        assertThat(response.evidence().entries()).isEqualTo(3);
        assertThat(response.evidence().bytes()).isEqualTo(auditor.uploadedFile.length);
        assertThat(auditor.uploadedUrl).isEqualTo("https://storage.example/put?sig=1");

        JsonNode file = gunzip(auditor.uploadedFile);
        assertThat(file.get("auditor").get("id").asText()).isEqualTo(FetchingAuditor.class.getName());
        assertThat(file.get("url").asText()).isEqualTo(base);
        JsonNode entries = file.get("entries");
        assertThat(entries.get(0).get("type").asText()).isEqualTo("stack");
        assertThat(entries.get(1).get("type").asText()).isEqualTo("http");
        assertThat(entries.get(1).get("data").get("url").asText()).isEqualTo(base + "/robots.txt");
        assertThat(entries.get(1).get("data").get("body").asText()).isEqualTo("User-agent: *\nDisallow:");
        assertThat(entries.get(1).get("data").get("headers").get("set-cookie").get(0).asText())
                .isEqualTo("[redacted]");
        assertThat(entries.get(2).get("type").asText()).isEqualTo("dns");
    }

    @Test
    void leavesTheResponseAloneWithoutAnUploadUrl() {
        FetchingAuditor auditor = new FetchingAuditor(true);

        Response response = auditor.handleRequest(request(null, List.of()), null);

        assertThat(response.evidence()).isNull();
        assertThat(auditor.uploadedFile).isNull();
    }

    @Test
    void reportsAFailedUploadWithoutFailingTheAudit() {
        FetchingAuditor auditor = new FetchingAuditor(false);

        Response response = auditor.handleRequest(request("https://storage.example/put", List.of()), null);

        assertThat(response.status()).isEqualTo(CheckStatus.SUCCESS);
        assertThat(response.evidence().uploaded()).isFalse();
        assertThat(response.evidence().entries()).isEqualTo(2);
    }

    @Test
    void doesNotCarryEvidenceIntoTheNextInvocationOfAWarmContainer() throws IOException {
        FetchingAuditor auditor = new FetchingAuditor(true);

        auditor.handleRequest(request("https://storage.example/put",
                List.of(new Software("liferay", "dxp", "2024.q4"))), null);
        Response second = auditor.handleRequest(request("https://storage.example/put", List.of()), null);

        assertThat(second.evidence().entries()).isEqualTo(2);
        assertThat(gunzip(auditor.uploadedFile).get("entries").get(0).get("type").asText()).isEqualTo("http");
    }

    @Test
    void keepsTheEvidenceOfAnAuditThatThrew() {
        FetchingAuditor auditor = new FetchingAuditor(true);
        auditor.throwAfterFetch = true;

        Response response = auditor.handleRequest(request("https://storage.example/put", List.of()), null);

        assertThat(response.status()).isEqualTo(CheckStatus.ERROR);
        assertThat(response.evidence().uploaded()).isTrue();
        assertThat(response.evidence().entries()).isEqualTo(1);
    }

    @Test
    void serializerReadsTheUploadUrlAndLeavesAnAbsentSummaryOutOfTheJson() {
        BasePojoSerializer serializer = new BasePojoSerializer();

        assertThat(serializer.<Request>fromJson(
                "{\"id\":\"1\",\"url\":\"http://example.com\",\"evidenceUploadUrl\":\"https://s3/put\"}",
                Request.class).evidenceUploadUrl()).isEqualTo("https://s3/put");
        assertThat(serializer.<Request>fromJson("{\"id\":\"1\",\"url\":\"http://example.com\"}", Request.class)
                .evidenceUploadUrl()).isNull();
        assertThat(serializer.<Request>fromJson(
                "{\"id\":\"1\",\"url\":\"http://example.com\",\"evidenceUploadUrl\":\" \"}", Request.class)
                .evidenceUploadUrl()).isNull();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        serializer.toJson(new FetchingAuditor(true).success("ok", List.of()), out, Response.class);
        assertThat(out.toString(StandardCharsets.UTF_8)).doesNotContain("evidence");
    }

    private Request request(String uploadUrl, List<Software> stack) {
        return new Request("id", base, null, stack, null, Map.of(), null, List.of(), uploadUrl);
    }

    private static JsonNode gunzip(byte[] file) throws IOException {
        return new ObjectMapper().readTree(new GZIPInputStream(new ByteArrayInputStream(file)));
    }

    private static final class FetchingAuditor extends AbstractLambdaAuditor {
        private final boolean uploadSucceeds;
        boolean throwAfterFetch;
        String uploadedUrl;
        byte[] uploadedFile;

        FetchingAuditor(boolean uploadSucceeds) {
            this.uploadSucceeds = uploadSucceeds;
        }

        @Override
        public String getName() {
            return "Fetching";
        }

        @Override
        protected EvidenceUploader evidenceUploader() {
            return new EvidenceUploader() {
                @Override
                public boolean upload(String uploadUrl, byte[] gzipJson) {
                    uploadedUrl = uploadUrl;
                    uploadedFile = gzipJson;
                    return uploadSucceeds;
                }
            };
        }

        @Override
        public Response process(String urlStr, BasicAuth basicAuth) {
            try {
                httpFetcher(urlStr, basicAuth).get(urlStr + "/robots.txt");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            if (throwAfterFetch) {
                throw new IllegalStateException("boom");
            }
            evidence().dns("localhost", "TXT", "SUCCESS", List.of("\"v=spf1 -all\""));
            return success("ok", List.of(new Check(CheckStatus.SUCCESS, urlStr, "ok", null, 0, Map.of(), null)));
        }
    }
}
