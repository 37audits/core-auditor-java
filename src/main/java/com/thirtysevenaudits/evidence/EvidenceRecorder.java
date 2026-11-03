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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.thirtysevenaudits.auditor.Auditor;
import com.thirtysevenaudits.auditor.Software;
import com.thirtysevenaudits.http.FetchResponse;

/**
 * The evidence of one run: the inputs the auditor used, in the order it used them. {@code AbstractLambdaAuditor}
 * keeps one per run, records every response of its {@code httpFetcher()} and the request's stack, and uploads the
 * file when the run ends; auditors add their other inputs (DNS answers, certificates, WHOIS replies) through
 * {@code evidence()}.
 *
 * <p>The file stays small and free of secrets:
 * <ul>
 * <li>a body is kept up to {@value #MAX_BODY_BYTES} bytes ({@code bodyTruncated} says when it was cut);</li>
 * <li>at most {@value #MAX_ENTRIES} entries and about {@value #MAX_TOTAL_BYTES} bytes of JSON are kept; later entries
 * are counted in {@link #dropped()} and left out;</li>
 * <li>the values of the {@code Set-Cookie}, {@code Cookie}, {@code Authorization} and {@code Proxy-Authorization}
 * headers are replaced with {@value #REDACTED}.</li>
 * </ul>
 * Thread-safe: auditors that fetch in parallel share one recorder.
 */
public final class EvidenceRecorder {

    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_ENTRIES = 50;
    public static final int MAX_BODY_BYTES = 1024 * 1024;
    public static final long MAX_TOTAL_BYTES = 8L * 1024 * 1024;
    public static final String REDACTED = "[redacted]";

    private static final Set<String> REDACTED_HEADERS = Set.of("set-cookie", "cookie", "authorization",
            "proxy-authorization");

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final List<EvidenceEntry> entries = new ArrayList<>();
    private long totalBytes;
    private int dropped;

    /** An HTTP response: status, final URL, headers and body. */
    public void http(String method, FetchResponse response) {
        if (response == null) {
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("method", method);
        data.put("requestedUrl", String.valueOf(response.requestedUri()));
        data.put("url", String.valueOf(response.uri()));
        data.put("status", response.statusCode());
        data.put("httpVersion", response.version() != null ? response.version().name() : null);
        data.put("elapsedMs", response.elapsed() != null ? response.elapsed().toMillis() : null);
        data.put("headers", redact(response.headers()));
        putBody(data, response.header("content-type"), response.body(), response.charset(), response.truncated());
        add(EvidenceEntry.HTTP, method + " " + response.requestedUri(), data);
    }

    /** The document a browser navigation loaded; {@code body} may be {@code null} when it was not available. */
    public void browser(String url, int status, Map<String, String> headers, String body) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("url", url);
        data.put("status", status);
        Map<String, List<String>> multi = new LinkedHashMap<>();
        if (headers != null) {
            headers.forEach((name, value) -> multi.put(name, List.of(value)));
        }
        data.put("headers", redact(multi));
        if (body != null) {
            putBody(data, headers != null ? headers.get("content-type") : null,
                    body.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8,
                    false);
        }
        add(EvidenceEntry.BROWSER, url, data);
    }

    /**
     * A DNS answer.
     *
     * @param status
     *            the lookup outcome as the resolver reports it ({@code SUCCESS}, {@code NXDOMAIN}, ...).
     * @param records
     *            the records in presentation format.
     */
    public void dns(String name, String recordType, String status, List<String> records) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", name);
        data.put("recordType", recordType);
        data.put("status", status);
        data.put("records", records != null ? List.copyOf(records) : List.of());
        add(EvidenceEntry.DNS, recordType + " " + name, data);
    }

    /** The software stack the platform sent with the request. */
    public void stack(List<Software> software) {
        if (software == null || software.isEmpty()) {
            return;
        }
        add(EvidenceEntry.STACK, "Stack", Map.of("software", List.copyOf(software)));
    }

    /**
     * Any other input, such as {@code tls} for a certificate chain or {@code whois} for a registry reply. {@code data}
     * must serialize to JSON.
     */
    public void add(String type, String label, Map<String, Object> data) {
        Map<String, Object> copy = data != null ? new LinkedHashMap<>(data) : Map.of();
        long size;
        try {
            size = MAPPER.writeValueAsBytes(copy).length;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Evidence data of type " + type + " does not serialize to JSON", e);
        }
        synchronized (this) {
            if (entries.size() >= MAX_ENTRIES || totalBytes + size > MAX_TOTAL_BYTES) {
                dropped++;
                return;
            }
            totalBytes += size;
            entries.add(new EvidenceEntry("e" + (entries.size() + 1), type, label, copy));
        }
    }

    public synchronized List<EvidenceEntry> entries() {
        return List.copyOf(entries);
    }

    public synchronized boolean isEmpty() {
        return entries.isEmpty();
    }

    /** Entries left out because the limits were reached. */
    public synchronized int dropped() {
        return dropped;
    }

    /**
     * The evidence file, gzipped JSON: {@code schemaVersion}, {@code auditor}, {@code url}, {@code capturedAt},
     * {@code entries} and {@code dropped}.
     */
    public byte[] toGzipJson(Auditor auditor, String url, Instant capturedAt) {
        Map<String, Object> file = new LinkedHashMap<>();
        file.put("schemaVersion", SCHEMA_VERSION);
        file.put("auditor", auditor);
        file.put("url", url);
        file.put("capturedAt", capturedAt);
        synchronized (this) {
            file.put("entries", List.copyOf(entries));
            file.put("dropped", dropped);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            MAPPER.writeValue(gzip, file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    private static Map<String, List<String>> redact(Map<String, List<String>> headers) {
        Map<String, List<String>> redacted = new LinkedHashMap<>();
        if (headers == null) {
            return redacted;
        }
        headers.forEach((name, values) -> redacted.put(name,
                REDACTED_HEADERS.contains(name.toLowerCase(Locale.ROOT))
                        ? values.stream().map(v -> REDACTED).toList()
                        : values));
        return redacted;
    }

    private static void putBody(Map<String, Object> data, String contentType, byte[] body, Charset charset,
            boolean truncated) {
        if (body == null) {
            body = new byte[0];
        }
        boolean cut = body.length > MAX_BODY_BYTES;
        byte[] kept = cut ? Arrays.copyOf(body, MAX_BODY_BYTES) : body;
        data.put("contentType", contentType);
        data.put("bodyBytes", body.length);
        if (isText(contentType)) {
            data.put("bodyEncoding", "text");
            data.put("body", new String(kept, charset));
        } else {
            data.put("bodyEncoding", "base64");
            data.put("body", Base64.getEncoder().encodeToString(kept));
        }
        data.put("bodyTruncated", truncated || cut);
    }

    /** Whether a body of {@code contentType} is text; a missing type is taken as text (robots.txt, llms.txt...). */
    static boolean isText(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return true;
        }
        String type = contentType.toLowerCase(Locale.ROOT).split(";", 2)[0].trim();
        return type.startsWith("text/") || type.endsWith("+xml") || type.endsWith("+json") || type.endsWith("/xml")
                || type.endsWith("/json") || type.contains("javascript") || type.contains("ecmascript")
                || type.equals("application/x-www-form-urlencoded");
    }
}
