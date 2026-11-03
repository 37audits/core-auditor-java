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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.thirtysevenaudits.evidence.EvidenceSummary;

/**
 * Lambda output.
 *
 * <p>{@code evidence} says what became of the run's evidence file; {@code null} (and left out of the JSON) when the
 * request asked for no evidence.
 */
public record Response(double version, Auditor auditor, CheckStatus status,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX", timezone = "UTC") Instant date,
        String message, UUID groupId, List<Check> checks,
        @JsonInclude(JsonInclude.Include.NON_NULL) EvidenceSummary evidence) {

    /** Backward-compatible constructor for callers predating evidence. */
    public Response(double version, Auditor auditor, CheckStatus status, Instant date, String message, UUID groupId,
            List<Check> checks) {
        this(version, auditor, status, date, message, groupId, checks, null);
    }

    public Response(Auditor auditor, CheckStatus status, String message, List<Check> checks) {
        this(2.0, auditor, status, Instant.now(), message, null, checks);
    }

    public Response(Auditor auditor, CheckStatus status, Instant date, String message, UUID groupId,
            List<Check> checks) {
        this(2.0, auditor, status, date, message, null, checks);
    }

    /** This response with {@code evidence} in place of its current summary. */
    public Response withEvidence(EvidenceSummary evidence) {
        return new Response(version, auditor, status, date, message, groupId, checks, evidence);
    }
}
