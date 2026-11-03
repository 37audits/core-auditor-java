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
package com.thirtysevenaudits.auditor;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

/**
 * Encoding and normalization of {@link Check#key() check keys}.
 *
 * <p>A key is stored base64url-encoded without padding, so every auditor's keys share one format whatever the raw
 * value holds (spaces, {@code :}, {@code /}, accents). Auditors never encode keys themselves: they pass the raw value
 * to {@link Check#withKey(String)}. A raw value that can vary between runs without the finding changing must be
 * normalized first, with {@link #url(String)} or {@link #token(String)}, because the encoded key is matched exactly.
 */
public final class CheckKeys {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private CheckKeys() {
    }

    /**
     * The stored form of {@code raw}: trimmed, then base64url-encoded without padding. {@code null} for a
     * {@code null} or blank value.
     */
    public static String encode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return ENCODER.encodeToString(raw.strip().getBytes(StandardCharsets.UTF_8));
    }

    /** The raw value of an encoded key, for logs, messages and tests. {@code null} for {@code null}. */
    public static String decode(String key) {
        if (key == null) {
            return null;
        }
        return new String(DECODER.decode(key), StandardCharsets.UTF_8);
    }

    /**
     * {@code url} without its query and fragment, with a lower-case scheme and host, so cache-busting parameters
     * ({@code ?v=3}, {@code ?ver=6.4}) do not change the key. A value that is not a URI is returned trimmed.
     */
    public static String url(String url) {
        if (url == null) {
            return null;
        }
        String trimmed = url.strip();
        try {
            URI uri = new URI(trimmed);
            if (uri.getScheme() == null || uri.getRawAuthority() == null) {
                int cut = indexOfQueryOrFragment(trimmed);
                return (cut >= 0) ? trimmed.substring(0, cut) : trimmed;
            }
            String path = (uri.getRawPath() == null) ? "" : uri.getRawPath();
            return uri.getScheme().toLowerCase(Locale.ROOT) + "://" + uri.getRawAuthority().toLowerCase(Locale.ROOT)
                    + path;
        } catch (Exception e) {
            int cut = indexOfQueryOrFragment(trimmed);
            return (cut >= 0) ? trimmed.substring(0, cut) : trimmed;
        }
    }

    /** {@code value} trimmed and lower-cased, for case-insensitive names such as headers, tokens and features. */
    public static String token(String value) {
        return (value == null) ? null : value.strip().toLowerCase(Locale.ROOT);
    }

    private static int indexOfQueryOrFragment(String value) {
        int query = value.indexOf('?');
        int fragment = value.indexOf('#');
        if (query < 0) {
            return fragment;
        }
        return (fragment < 0) ? query : Math.min(query, fragment);
    }
}
