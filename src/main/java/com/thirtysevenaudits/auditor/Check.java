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

import java.util.Collection;
import java.util.Map;

/**
 * A single audit finding.
 *
 * @param code
 *            structured identity for this check <em>definition</em> (not the individual finding), modeled as
 *            {@link CheckCode}. Use {@link CheckCode#id()} as the stable key when grouping findings—similar in spirit
 *            to Hibernate ORM message ids such as {@code HHH000406}. Recommended {@code id} shapes include
 *            {@code 37A-AuditorName-NNN} (e.g. {@code 37A-MyAuditor-001}) or compact values such as {@code 37A-000123};
 *            {@link CheckCode#description()} may hold a short human-readable label. Must not be {@code null}.
 */
public record Check(CheckStatus status, String resource, String message, String recommendation, int score,
        Map<String, Object> data, CheckCode code) {

    public static long countStatus(Collection<Check> checks, CheckStatus status) {
        if (checks != null) {
            return checks.stream().filter(c -> c.status == status).count();
        }

        return 0;
    }

    public static Check convertFailToWarning(Check c) {
        if (c.status() != CheckStatus.FAIL)
            return c;

        return new Check(CheckStatus.WARNING, c.resource(), c.message(), c.recommendation(), c.score(), c.data(),
                c.code());
    }
}
