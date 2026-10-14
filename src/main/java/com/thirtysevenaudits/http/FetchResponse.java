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

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A response read by {@link HttpFetcher}.
 *
 * @param statusCode
 *            HTTP status code of the last response (after redirects, when they are followed).
 * @param requestedUri
 *            the URL the caller asked for.
 * @param uri
 *            the URL that answered: {@code requestedUri} or the end of the redirect chain.
 * @param headers
 *            response headers, keys lowercased.
 * @param body
 *            the body, already decoded from {@code gzip} / {@code deflate}; empty for HEAD; cut at the fetcher's
 *            body limit (see {@link #truncated()}).
 * @param version
 *            HTTP version of the last response.
 * @param elapsed
 *            time from sending the last request until its response headers arrived (time to first byte).
 * @param truncated
 *            whether the body was longer than the fetcher's limit and was cut.
 */
public record FetchResponse(int statusCode, URI requestedUri, URI uri, Map<String, List<String>> headers, byte[] body,
        HttpClient.Version version, Duration elapsed, boolean truncated) {

    /** Key under which {@link #headerMap()} carries the status code, as {@code HeadersCrawler} always did. */
    public static final String HTTP_STATUS_CODE = "http-status-code";

    private static final Pattern CHARSET = Pattern.compile("charset\\s*=\\s*[\"']?([\\w.:-]+)",
            Pattern.CASE_INSENSITIVE);

    private static final int META_SNIFF_BYTES = 1024;

    /** First value of the header {@code name} (any case), or {@code null}. */
    public String header(String name) {
        List<String> values = headers.get(name.toLowerCase());
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    /** All values of the header {@code name} (any case); empty when absent. */
    public List<String> headers(String name) {
        List<String> values = headers.get(name.toLowerCase());
        return values == null ? List.of() : values;
    }

    public boolean isSuccessful() {
        return statusCode >= 200 && statusCode < 300;
    }

    /**
     * The body as text, in the charset of the {@code Content-Type} header, else of an HTML {@code <meta charset>}
     * near the top of the body, else UTF-8.
     */
    public String text() {
        return new String(body, charset());
    }

    public Charset charset() {
        Charset charset = parseCharset(header("content-type"));
        if (charset == null) {
            String head = new String(body, 0, Math.min(body.length, META_SNIFF_BYTES), StandardCharsets.ISO_8859_1);
            int meta = head.toLowerCase().indexOf("<meta");
            charset = meta < 0 ? null : parseCharset(head.substring(meta));
        }
        return charset == null ? StandardCharsets.UTF_8 : charset;
    }

    /**
     * The headers in the shape {@code HeadersCrawler} returns and {@code CDNUtil} and
     * {@code ensureNotBlocked(String, Map)} read: lowercased keys plus the status code under
     * {@value #HTTP_STATUS_CODE}.
     */
    public Map<String, List<String>> headerMap() {
        Map<String, List<String>> map = new HashMap<>(headers);
        map.put(HTTP_STATUS_CODE, Collections.singletonList(String.valueOf(statusCode)));
        return map;
    }

    private static Charset parseCharset(String value) {
        if (value == null) {
            return null;
        }
        Matcher matcher = CHARSET.matcher(value);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Charset.forName(matcher.group(1));
        } catch (RuntimeException e) {
            return null;
        }
    }
}
