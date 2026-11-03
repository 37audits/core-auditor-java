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
import java.util.Objects;

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
 *
 * <p>{@code userAgent} is the custom User-Agent the monitored URL's auditor
 * configuration sets for requests to the audited site. {@code null} (also for a
 * blank or absent field) means the default bot agent; see
 * {@link com.thirtysevenaudits.auditor.aws.AbstractLambdaAuditor#getUserAgent()}.
 *
 * <p>{@code ignores} lists the findings the monitored URL's users chose to ignore that have not expired; the auditor
 * reports a matching check as {@link CheckStatus#INFORMATION} (see {@link CheckIgnore}). It is never {@code null}
 * after construction; a payload without the field yields an empty list.
 */
public record Request(String id, String url, UrlProfile profile, List<Software> stack, BasicAuth basicAuth,
        Map<String, Object> preferences, String userAgent, List<CheckIgnore> ignores) {

    public Request {
        if (profile == null) {
            profile = UrlProfile.PUBLIC_WEBSITE;
        }
        // Not Map.copyOf: a JSON null value must not blow up deserialization.
        preferences = (preferences != null)
                ? Collections.unmodifiableMap(new LinkedHashMap<>(preferences))
                : Map.of();
        if (userAgent != null && userAgent.isBlank()) {
            userAgent = null;
        }
        ignores = (ignores != null) ? ignores.stream().filter(Objects::nonNull).toList() : List.of();
    }

    /** Backward-compatible constructor for callers predating check ignores. */
    public Request(String id, String url, UrlProfile profile, List<Software> stack, BasicAuth basicAuth,
            Map<String, Object> preferences, String userAgent) {
        this(id, url, profile, stack, basicAuth, preferences, userAgent, null);
    }

    /** Backward-compatible constructor for callers predating the custom user agent. */
    public Request(String id, String url, UrlProfile profile, List<Software> stack, BasicAuth basicAuth,
            Map<String, Object> preferences) {
        this(id, url, profile, stack, basicAuth, preferences, null, null);
    }

    /** Backward-compatible constructor for callers predating profiles. */
    public Request(String id, String url, List<Software> stack, BasicAuth basicAuth,
            Map<String, Object> preferences) {
        this(id, url, null, stack, basicAuth, preferences, null, null);
    }

    /** Backward-compatible constructor for callers predating preferences. */
    public Request(String id, String url, List<Software> stack, BasicAuth basicAuth) {
        this(id, url, null, stack, basicAuth, Map.of(), null, null);
    }
}
