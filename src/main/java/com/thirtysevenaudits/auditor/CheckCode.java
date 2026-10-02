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

/**
 * Identity of a check <em>definition</em>, shared by every finding the rule produces.
 *
 * @param id
 *            stable key of the rule, e.g. {@code 37A-MyAuditor-400}.
 * @param description
 *            short human-readable label of the rule.
 * @param rationale
 *            why the rule matters to the site owner: the impact of leaving it unaddressed (lost traffic, security
 *            exposure, broken analytics...). Static text, without observed values. {@code null} when the rule has
 *            none.
 */
public record CheckCode(String id, String description, String rationale) {

    public CheckCode(String id, String description) {
        this(id, description, null);
    }
}
