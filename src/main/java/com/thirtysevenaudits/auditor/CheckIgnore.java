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
 * A finding the monitored URL's users chose to ignore, sent by the platform API with the {@link Request}. It matches
 * a check whose {@link CheckCode#id() code} equals {@code code} and whose {@link Check#key() key} equals
 * {@code checkKey} (both encoded); the matched check is reported as {@link CheckStatus#INFORMATION} with the reason.
 *
 * @param id
 *            the ignore's id in the platform, echoed in the check's {@code data.ignoreId}.
 * @param code
 *            the check code id, for example {@code 37A-CVEAuditor-400}.
 * @param checkKey
 *            the encoded check key, for example {@code TFNWLTE3OTM} for {@code LSV-1793}.
 * @param reason
 *            why the finding is ignored, written by the user.
 */
public record CheckIgnore(String id, String code, String checkKey, String reason) {

    /** Whether this ignore applies to {@code check}. */
    public boolean matches(Check check) {
        return check != null && check.code() != null && check.key() != null && check.code().id().equals(code)
                && check.key().equals(checkKey);
    }
}
