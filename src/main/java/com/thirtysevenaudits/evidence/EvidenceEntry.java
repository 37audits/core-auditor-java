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

import java.util.Map;

/**
 * One input an auditor used: an HTTP response, a DNS answer, the stack, a certificate chain, a WHOIS reply...
 *
 * @param id
 *            {@code e1}, {@code e2}, ... in the order the entries were recorded.
 * @param type
 *            what the entry is ({@link #HTTP}, {@link #DNS}, {@link #STACK}, {@link #BROWSER} or an auditor's own
 *            type, such as {@code tls} or {@code whois}).
 * @param label
 *            a short human-readable name, such as the URL or {@code TXT example.com}.
 * @param data
 *            the input itself; its fields depend on {@code type}.
 */
public record EvidenceEntry(String id, String type, String label, Map<String, Object> data) {

    public static final String HTTP = "http";
    public static final String DNS = "dns";
    public static final String STACK = "stack";
    public static final String BROWSER = "browser";
}
