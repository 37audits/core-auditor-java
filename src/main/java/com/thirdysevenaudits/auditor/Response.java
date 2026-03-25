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
package com.thirdysevenaudits.auditor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;

public record Response(double version, Auditor auditor, CheckStatus status,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX", timezone = "UTC") Instant date,
        String message, UUID groupId, List<Check> checks) {

    public Response(Auditor auditor, CheckStatus status, String message, List<Check> checks) {
        this(2.0, auditor, status, Instant.now(), message, null, checks);
    }

    public Response(Auditor auditor, CheckStatus status, Instant date, String message, UUID groupId,
            List<Check> checks) {
        this(2.0, auditor, status, date, message, null, checks);
    }
}
