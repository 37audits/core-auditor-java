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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lambda input.
 *
 * <p>{@code preferences} carries the per-monitored-URL, per-auditor settings
 * the platform API resolved for this run (schema defaults overlaid with the
 * user's overrides), keyed by preference key. It is never {@code null} after
 * construction; a payload without the field yields an empty map.
 *
 * <p>{@code profile} is the kind of site the monitored URL is. It is never
 * {@code null} after construction; a payload without the field yields
 * {@link UrlProfile#PUBLIC_WEBSITE}.
 */
public record Request(String id, String url, UrlProfile profile, List<Software> stack, BasicAuth basicAuth,
        Map<String, Object> preferences) {

    public Request {
        if (profile == null) {
            profile = UrlProfile.PUBLIC_WEBSITE;
        }
        // Not Map.copyOf: a JSON null value must not blow up deserialization.
        preferences = (preferences != null)
                ? Collections.unmodifiableMap(new LinkedHashMap<>(preferences))
                : Map.of();
    }

    /** Backward-compatible constructor for callers predating profiles. */
    public Request(String id, String url, List<Software> stack, BasicAuth basicAuth,
            Map<String, Object> preferences) {
        this(id, url, null, stack, basicAuth, preferences);
    }

    /** Backward-compatible constructor for callers predating preferences. */
    public Request(String id, String url, List<Software> stack, BasicAuth basicAuth) {
        this(id, url, null, stack, basicAuth, Map.of());
    }
}
