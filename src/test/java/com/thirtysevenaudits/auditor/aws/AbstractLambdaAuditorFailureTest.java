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
package com.thirtysevenaudits.auditor.aws;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import com.thirtysevenaudits.auditor.BasicAuth;
import com.thirtysevenaudits.auditor.Check;
import com.thirtysevenaudits.auditor.CheckStatus;
import com.thirtysevenaudits.auditor.Request;
import com.thirtysevenaudits.auditor.Response;
import com.thirtysevenaudits.auditor.SiteBlockedException;

class AbstractLambdaAuditorFailureTest {

    private static final String URL = "https://example.com/";

    @Test
    void uncaughtExceptionBecomesAnErrorResponseInsteadOfALambdaFailure() {
        Response response = run(() -> {
            throw new IllegalStateException("browser has been closed");
        });

        assertThat(response.status()).isEqualTo(CheckStatus.ERROR);
        assertThat(response.message())
                .isEqualTo("The audit could not finish: IllegalStateException: browser has been closed");
        assertThat(response.auditor().name()).isEqualTo("Failing");
        assertThat(response.checks()).isNull();
    }

    @Test
    void multiLineExceptionMessageIsCollapsedToOneLine() {
        Response response = run(() -> {
            throw new IllegalStateException("Error {\n  message='Target closed\n  name='TargetClosedError\n}");
        });

        assertThat(response.message()).isEqualTo(
                "The audit could not finish: IllegalStateException: Error { message='Target closed name='TargetClosedError }");
    }

    @Test
    void longExceptionMessageIsCut() {
        Response response = run(() -> {
            throw new IllegalStateException("x".repeat(1000));
        });

        assertThat(response.message()).hasSize("The audit could not finish: ".length() + 300 + "...".length())
                .endsWith("...");
    }

    @Test
    void exceptionWithoutMessageIsNamedByItsClass() {
        Response response = run(() -> {
            throw new NullPointerException();
        });

        assertThat(response.message()).isEqualTo("The audit could not finish: NullPointerException");
    }

    @Test
    void blockedSiteBecomesAnErrorResponseThatSaysHowToAllowTheBot() {
        Response response = run(() -> {
            throw new SiteBlockedException(URL, 403, false);
        });

        assertThat(response.status()).isEqualTo(CheckStatus.ERROR);
        assertThat(response.message()).isEqualTo("The site blocked 37AuditsBot's request (HTTP 403).");
        assertThat(response.checks()).hasSize(1);

        Check check = response.checks().get(0);

        assertThat(check.status()).isEqualTo(CheckStatus.ERROR);
        assertThat(check.resource()).isEqualTo(URL);
        assertThat(check.message()).isEqualTo(response.message());
        assertThat(check.recommendation()).contains("37AuditsBot").contains("https://www.37audits.com/bot");
        assertThat(check.code().id()).isEqualTo("37A-FailingAuditor-590");
        assertThat(check.code().rationale()).isNotBlank();
        assertThat(check.data()).containsEntry("httpStatusCode", 403).containsEntry("challenge", false);
    }

    @Test
    void ensureNotBlockedStopsTheAuditWithTheBlockedResponse() {
        FailingAuditor auditor = new FailingAuditor(null);
        auditor.behavior = () -> {
            auditor.ensureNotBlocked(URL, 429, null);
            return new Response(null, CheckStatus.SUCCESS, "not reached", List.of());
        };

        Response response = auditor.handleRequest(new Request("id", URL, List.of(), null), null);

        assertThat(response.status()).isEqualTo(CheckStatus.ERROR);
        assertThat(response.message()).isEqualTo("The site rate limited 37AuditsBot (HTTP 429).");
        assertThat(response.checks().get(0).code().id()).isEqualTo("37A-FailingAuditor-590");
    }

    @Test
    void blockSwallowedByTheAuditorsCatchAllStillAnswersWithTheBlockedResponse() {
        FailingAuditor auditor = new FailingAuditor(null);
        auditor.behavior = () -> {
            try {
                auditor.ensureNotBlocked(URL, Map.of("http-status-code", List.of("403")));
                return new Response(null, CheckStatus.SUCCESS, "not reached", List.of());
            } catch (Exception e) {
                return new Response(null, CheckStatus.ERROR, "generic: " + e.getMessage(), null);
            }
        };

        Response response = auditor.handleRequest(new Request("id", URL, List.of(), null), null);

        assertThat(response.message()).isEqualTo("The site blocked 37AuditsBot's request (HTTP 403).");
        assertThat(response.checks()).hasSize(1);
    }

    @Test
    void blockOfOneRunDoesNotLeakIntoTheNext() {
        FailingAuditor auditor = new FailingAuditor(null);
        auditor.behavior = () -> {
            try {
                auditor.ensureNotBlocked(URL, 403, null);
            } catch (Exception e) {
                // swallowed
            }
            return null;
        };
        auditor.handleRequest(new Request("id", URL, List.of(), null), null);

        Response expected = new Response(null, CheckStatus.SUCCESS, "served", List.of());
        auditor.behavior = () -> {
            auditor.ensureNotBlocked(URL, 200, null);
            return expected;
        };

        assertThat(auditor.handleRequest(new Request("id", URL, List.of(), null), null)).isSameAs(expected);
    }

    @Test
    void auditorsOwnResponseIsReturnedUntouched() {
        Response expected = new Response(null, CheckStatus.WARNING, "as is", List.of());

        assertThat(run(() -> expected)).isSameAs(expected);
    }

    private static Response run(Supplier<Response> behavior) {
        return new FailingAuditor(behavior).handleRequest(new Request("id", URL, List.of(), null), null);
    }

    private static final class FailingAuditor extends AbstractLambdaAuditor {
        private Supplier<Response> behavior;

        FailingAuditor(Supplier<Response> behavior) {
            this.behavior = behavior;
        }

        @Override
        public String getName() {
            return "Failing";
        }

        @Override
        public Response process(String urlStr, BasicAuth basicAuth) {
            return behavior.get();
        }
    }
}
