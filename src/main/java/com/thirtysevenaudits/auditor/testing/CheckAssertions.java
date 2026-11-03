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
package com.thirtysevenaudits.auditor.testing;

import java.util.List;

import com.thirtysevenaudits.auditor.Check;
import com.thirtysevenaudits.auditor.CheckStatus;
import com.thirtysevenaudits.auditor.Response;

/**
 * Assertions for auditor tests that need no test library: they throw {@link AssertionError}, which every test
 * framework reports as a failure.
 */
public final class CheckAssertions {

    private CheckAssertions() {
    }

    /**
     * Fails unless every {@link CheckStatus#FAIL} and {@link CheckStatus#WARNING} check of {@code response} has a
     * {@link Check#key() key}. Call it on the response of every test that produces a failure or a warning.
     */
    public static void assertKeyed(Response response) {
        if (response == null) {
            throw new AssertionError("Expected a response, got null");
        }
        assertKeyed(response.checks());
    }

    /** Same as {@link #assertKeyed(Response)} for a list of checks. */
    public static void assertKeyed(List<Check> checks) {
        if (checks == null) {
            return;
        }
        List<String> unkeyed = checks.stream()
                .filter(c -> c != null && c.key() == null
                        && (c.status() == CheckStatus.FAIL || c.status() == CheckStatus.WARNING))
                .map(c -> c.status() + " " + ((c.code() != null) ? c.code().id() : "<no code>"))
                .toList();
        if (!unkeyed.isEmpty()) {
            throw new AssertionError("FAIL/WARNING checks without a key: " + unkeyed);
        }
    }
}
