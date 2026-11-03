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
package com.thirtysevenaudits.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thirtysevenaudits.auditor.Auditor;
import com.thirtysevenaudits.auditor.Software;
import com.thirtysevenaudits.http.FetchResponse;

class EvidenceRecorderTest {

    @Test
    void recordsAnHttpResponseWithItsHeadersAndTextBody() {
        EvidenceRecorder recorder = new EvidenceRecorder();

        recorder.http("GET", response("text/html; charset=UTF-8", "<p>café</p>".getBytes(StandardCharsets.UTF_8),
                Map.of("x-frame-options", List.of("DENY"))));

        EvidenceEntry entry = recorder.entries().get(0);
        assertThat(entry.id()).isEqualTo("e1");
        assertThat(entry.type()).isEqualTo(EvidenceEntry.HTTP);
        assertThat(entry.label()).isEqualTo("GET https://example.com/");
        assertThat(entry.data()).containsEntry("method", "GET").containsEntry("requestedUrl", "https://example.com/")
                .containsEntry("url", "https://www.example.com/").containsEntry("status", 200)
                .containsEntry("httpVersion", "HTTP_2").containsEntry("elapsedMs", 42L)
                .containsEntry("bodyEncoding", "text").containsEntry("body", "<p>café</p>")
                .containsEntry("bodyTruncated", false);
        assertThat(headers(entry)).containsEntry("x-frame-options", List.of("DENY"));
    }

    @Test
    void redactsCookieAndAuthorizationHeaders() {
        EvidenceRecorder recorder = new EvidenceRecorder();
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("set-cookie", List.of("session=abc", "csrf=def"));
        headers.put("Authorization", List.of("Bearer token"));
        headers.put("content-type", List.of("text/plain"));

        recorder.http("GET", response("text/plain", new byte[0], headers));

        assertThat(headers(recorder.entries().get(0)))
                .containsEntry("set-cookie", List.of(EvidenceRecorder.REDACTED, EvidenceRecorder.REDACTED))
                .containsEntry("Authorization", List.of(EvidenceRecorder.REDACTED))
                .containsEntry("content-type", List.of("text/plain"));
    }

    @Test
    void keepsABinaryBodyAsBase64() {
        EvidenceRecorder recorder = new EvidenceRecorder();
        byte[] png = { (byte) 0x89, 'P', 'N', 'G' };

        recorder.http("GET", response("image/png", png, Map.of()));

        assertThat(recorder.entries().get(0).data()).containsEntry("bodyEncoding", "base64")
                .containsEntry("body", Base64.getEncoder().encodeToString(png));
    }

    @Test
    void cutsABodyLongerThanTheLimit() {
        EvidenceRecorder recorder = new EvidenceRecorder();

        recorder.http("GET", response("text/plain", new byte[EvidenceRecorder.MAX_BODY_BYTES + 10], Map.of()));

        Map<String, Object> data = recorder.entries().get(0).data();
        assertThat((String) data.get("body")).hasSize(EvidenceRecorder.MAX_BODY_BYTES);
        assertThat(data).containsEntry("bodyBytes", EvidenceRecorder.MAX_BODY_BYTES + 10)
                .containsEntry("bodyTruncated", true);
    }

    @Test
    void reportsABodyTheFetcherAlreadyCutAsTruncated() {
        EvidenceRecorder recorder = new EvidenceRecorder();

        recorder.http("GET", new FetchResponse(200, URI.create("https://example.com/"),
                URI.create("https://example.com/"), Map.of(), new byte[10], HttpClient.Version.HTTP_1_1,
                Duration.ZERO, true));

        assertThat(recorder.entries().get(0).data()).containsEntry("bodyTruncated", true);
    }

    @Test
    void takesABodyWithoutContentTypeAsText() {
        assertThat(EvidenceRecorder.isText(null)).isTrue();
        assertThat(EvidenceRecorder.isText("application/ld+json")).isTrue();
        assertThat(EvidenceRecorder.isText("application/xml; charset=utf-8")).isTrue();
        assertThat(EvidenceRecorder.isText("text/javascript")).isTrue();
        assertThat(EvidenceRecorder.isText("application/octet-stream")).isFalse();
        assertThat(EvidenceRecorder.isText("image/webp")).isFalse();
    }

    @Test
    void recordsDnsStackAndCustomEntriesInOrder() {
        EvidenceRecorder recorder = new EvidenceRecorder();

        recorder.dns("example.com", "TXT", "SUCCESS", List.of("\"v=spf1 -all\""));
        recorder.stack(List.of(new Software("liferay", "dxp", "2024.q4")));
        recorder.stack(List.of());
        recorder.add("whois", "example.de", Map.of("text", "Domain: example.de"));

        assertThat(recorder.entries()).extracting(EvidenceEntry::id, EvidenceEntry::type, EvidenceEntry::label)
                .containsExactly(tuple("e1", "dns", "TXT example.com"),
                        tuple("e2", "stack", "Stack"),
                        tuple("e3", "whois", "example.de"));
        assertThat(recorder.entries().get(0).data()).containsEntry("records", List.of("\"v=spf1 -all\""));
    }

    @Test
    void recordsABrowserNavigation() {
        EvidenceRecorder recorder = new EvidenceRecorder();

        recorder.browser("https://example.com/", 200, Map.of("content-type", "text/html", "set-cookie", "a=b"),
                "<html></html>");

        EvidenceEntry entry = recorder.entries().get(0);
        assertThat(entry.type()).isEqualTo(EvidenceEntry.BROWSER);
        assertThat(entry.data()).containsEntry("status", 200).containsEntry("body", "<html></html>");
        assertThat(headers(entry)).containsEntry("set-cookie", List.of(EvidenceRecorder.REDACTED));
    }

    @Test
    void dropsEntriesPastTheEntryLimit() {
        EvidenceRecorder recorder = new EvidenceRecorder();

        for (int i = 0; i < EvidenceRecorder.MAX_ENTRIES + 3; i++) {
            recorder.add("custom", "entry " + i, Map.of("i", i));
        }

        assertThat(recorder.entries()).hasSize(EvidenceRecorder.MAX_ENTRIES);
        assertThat(recorder.dropped()).isEqualTo(3);
    }

    @Test
    void dropsEntriesPastTheSizeLimit() {
        EvidenceRecorder recorder = new EvidenceRecorder();

        for (int i = 0; i < 10; i++) {
            recorder.http("GET", response("text/plain", new byte[EvidenceRecorder.MAX_BODY_BYTES], Map.of()));
        }

        assertThat(recorder.entries()).hasSizeLessThan(10);
        assertThat(recorder.entries().size() + recorder.dropped()).isEqualTo(10);
    }

    @Test
    void rejectsDataThatDoesNotSerialize() {
        EvidenceRecorder recorder = new EvidenceRecorder();

        assertThatThrownBy(() -> recorder.add("custom", "bad", Map.of("value", new Object())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(recorder.isEmpty()).isTrue();
    }

    @Test
    void writesTheFileAsGzippedJson() throws IOException {
        EvidenceRecorder recorder = new EvidenceRecorder();
        recorder.dns("example.com", "MX", "SUCCESS", List.of("10 mx.example.com."));

        byte[] file = recorder.toGzipJson(new Auditor("com.example.SpfAuditor", "SPF", "1.0"),
                "https://example.com", Instant.parse("2026-11-02T12:00:00Z"));

        JsonNode json = new ObjectMapper().readTree(new GZIPInputStream(new ByteArrayInputStream(file)));
        assertThat(json.get("schemaVersion").asInt()).isEqualTo(EvidenceRecorder.SCHEMA_VERSION);
        assertThat(json.get("auditor").get("id").asText()).isEqualTo("com.example.SpfAuditor");
        assertThat(json.get("url").asText()).isEqualTo("https://example.com");
        assertThat(json.get("capturedAt").asText()).isEqualTo("2026-11-02T12:00:00Z");
        assertThat(json.get("dropped").asInt()).isZero();
        assertThat(json.get("entries").get(0).get("type").asText()).isEqualTo("dns");
        assertThat(json.get("entries").get(0).get("data").get("records").get(0).asText())
                .isEqualTo("10 mx.example.com.");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, List<String>> headers(EvidenceEntry entry) {
        return (Map<String, List<String>>) entry.data().get("headers");
    }

    private static FetchResponse response(String contentType, byte[] body, Map<String, List<String>> headers) {
        Map<String, List<String>> all = new LinkedHashMap<>(headers);
        all.putIfAbsent("content-type", List.of(contentType));
        return new FetchResponse(200, URI.create("https://example.com/"), URI.create("https://www.example.com/"), all,
                body, HttpClient.Version.HTTP_2, Duration.ofMillis(42), false);
    }
}
