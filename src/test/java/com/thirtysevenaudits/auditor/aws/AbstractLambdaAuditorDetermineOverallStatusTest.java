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

import org.junit.jupiter.api.Test;

import com.thirtysevenaudits.auditor.BasicAuth;
import com.thirtysevenaudits.auditor.Check;
import com.thirtysevenaudits.auditor.CheckCode;
import com.thirtysevenaudits.auditor.CheckStatus;
import com.thirtysevenaudits.auditor.Response;

class AbstractLambdaAuditorDetermineOverallStatusTest {

    private final TestAuditor auditor = new TestAuditor();

    @Test
    void emptyChecksReturnsSuccess() {
        assertThat(auditor.determineOverallStatus(List.of())).isEqualTo(CheckStatus.SUCCESS);
    }

    @Test
    void allErrorsReturnsError() {
        assertThat(auditor.determineOverallStatus(List.of(check(CheckStatus.ERROR), check(CheckStatus.ERROR))))
                .isEqualTo(CheckStatus.ERROR);
    }

    @Test
    void anyFailureReturnsFail() {
        assertThat(auditor.determineOverallStatus(List.of(check(CheckStatus.WARNING), check(CheckStatus.FAIL))))
                .isEqualTo(CheckStatus.FAIL);
    }

    @Test
    void mixedErrorAndFailureReturnsFail() {
        assertThat(auditor.determineOverallStatus(List.of(check(CheckStatus.ERROR), check(CheckStatus.FAIL))))
                .isEqualTo(CheckStatus.FAIL);
    }

    @Test
    void anyWarningWithoutFailureReturnsWarning() {
        assertThat(auditor.determineOverallStatus(List.of(check(CheckStatus.SUCCESS), check(CheckStatus.WARNING))))
                .isEqualTo(CheckStatus.WARNING);
    }

    @Test
    void mixedErrorAndWarningReturnsWarning() {
        assertThat(auditor.determineOverallStatus(List.of(check(CheckStatus.ERROR), check(CheckStatus.WARNING))))
                .isEqualTo(CheckStatus.WARNING);
    }

    @Test
    void allSuccessReturnsSuccess() {
        assertThat(auditor.determineOverallStatus(List.of(check(CheckStatus.SUCCESS), check(CheckStatus.SUCCESS))))
                .isEqualTo(CheckStatus.SUCCESS);
    }

    private static Check check(CheckStatus status) {
        return new Check(status, "resource", "message", "recommendation", 0, Map.of(),
                new CheckCode("37A-001", "test"));
    }

    private static final class TestAuditor extends AbstractLambdaAuditor {

        @Override
        public String getName() {
            return "test";
        }

        @Override
        public Response process(String urlStr, BasicAuth basicAuth) {
            throw new UnsupportedOperationException();
        }
    }
}
