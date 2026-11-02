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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.thirtysevenaudits.auditor.BasicAuth;
import com.thirtysevenaudits.auditor.Check;
import com.thirtysevenaudits.auditor.CheckCode;
import com.thirtysevenaudits.auditor.CheckIgnore;
import com.thirtysevenaudits.auditor.CheckKeys;
import com.thirtysevenaudits.auditor.CheckStatus;
import com.thirtysevenaudits.auditor.Request;
import com.thirtysevenaudits.auditor.Response;

class AbstractLambdaAuditorIgnoresTest {

    private static final CheckCode HIGH = new CheckCode("37A-Test-400", "high");
    private static final CheckCode LOW = new CheckCode("37A-Test-300", "low");

    @Test
    void aMatchingIgnoreTurnsTheOnlyFailIntoInformationAndRecomputesTheStatus() {
        StubAuditor auditor = new StubAuditor(CheckStatus.FAIL, "1 of 20 affect 2026.Q1.5.",
                fail(HIGH, "LSV-1793"));

        Response response = auditor.handleRequest(request(ignore(HIGH, "LSV-1793", "Hotfix 96 fixes it")), null);

        assertThat(response.status()).isEqualTo(CheckStatus.SUCCESS);
        Check check = response.checks().get(0);
        assertThat(check.status()).isEqualTo(CheckStatus.INFORMATION);
        assertThat(check.key()).isEqualTo(CheckKeys.encode("LSV-1793"));
        assertThat(check.data()).containsEntry("ignoredStatus", "FAIL").containsEntry("ignoreId", "ig-1")
                .containsEntry("ignoreReason", "Hotfix 96 fixes it").containsEntry("existing", "kept");
        assertThat(response.message())
                .isEqualTo("1 of 20 affect 2026.Q1.5. Ignored: LSV-1793 (Hotfix 96 fixes it).");
    }

    @Test
    void theStatusFollowsTheRemainingChecks() {
        StubAuditor auditor = new StubAuditor(CheckStatus.FAIL, "m", fail(HIGH, "LSV-1793"),
                new Check(CheckStatus.WARNING, "u", "w", null, 50, Map.of(), LOW).withKey("LSV-1000"));

        Response response = auditor.handleRequest(request(ignore(HIGH, "LSV-1793", "r")), null);

        assertThat(response.status()).isEqualTo(CheckStatus.WARNING);
        assertThat(response.checks().get(1).status()).isEqualTo(CheckStatus.WARNING);
    }

    @Test
    void anotherKeyOrCodeDoesNotMatch() {
        StubAuditor auditor = new StubAuditor(CheckStatus.FAIL, "m", fail(HIGH, "LSV-2000"));

        Response response = auditor.handleRequest(
                request(ignore(HIGH, "LSV-1793", "r"), ignore(LOW, "LSV-2000", "r")), null);

        assertThat(response.status()).isEqualTo(CheckStatus.FAIL);
        assertThat(response.checks().get(0).status()).isEqualTo(CheckStatus.FAIL);
        assertThat(response.message()).isEqualTo("m");
    }

    @Test
    void anUnkeyedCheckIsNeverMatched() {
        StubAuditor auditor = new StubAuditor(CheckStatus.FAIL, "m",
                new Check(CheckStatus.FAIL, "u", "f", null, 0, Map.of(), HIGH));

        Response response = auditor.handleRequest(request(new CheckIgnore("ig", HIGH.id(), null, "r")), null);

        assertThat(response.status()).isEqualTo(CheckStatus.FAIL);
    }

    @Test
    void anErrorResponseIsLeftAlone() {
        StubAuditor auditor = new StubAuditor(CheckStatus.ERROR, "m", fail(HIGH, "LSV-1793"));

        Response response = auditor.handleRequest(request(ignore(HIGH, "LSV-1793", "r")), null);

        assertThat(response.status()).isEqualTo(CheckStatus.ERROR);
        assertThat(response.checks().get(0).status()).isEqualTo(CheckStatus.FAIL);
    }

    @Test
    void theStatusIsNeverMadeMoreSevere() {
        // The auditor chose WARNING for a run whose only FAIL check is ignored; the remaining WARNING stays.
        StubAuditor auditor = new StubAuditor(CheckStatus.WARNING, "m", fail(HIGH, "a"),
                new Check(CheckStatus.FAIL, "u", "f", null, 0, Map.of(), LOW).withKey("b"));

        Response response = auditor.handleRequest(request(ignore(HIGH, "a", "r")), null);

        assertThat(response.status()).isEqualTo(CheckStatus.WARNING);
    }

    @Test
    void serializerReadsIgnoresAndToleratesTheirAbsence() {
        BasePojoSerializer serializer = new BasePojoSerializer();

        Request withIgnores = serializer.fromJson("{\"id\":\"1\",\"url\":\"http://example.com\",\"ignores\":"
                + "[{\"id\":\"ig\",\"code\":\"37A-Test-400\",\"checkKey\":\"TFNWLTE3OTM\",\"reason\":\"r\"}]}",
                Request.class);
        Request without = serializer.fromJson("{\"id\":\"1\",\"url\":\"http://example.com\"}", Request.class);

        assertThat(withIgnores.ignores()).containsExactly(new CheckIgnore("ig", "37A-Test-400", "TFNWLTE3OTM", "r"));
        assertThat(without.ignores()).isEmpty();
    }

    private static Check fail(CheckCode code, String key) {
        return new Check(CheckStatus.FAIL, "http://example.com", "affected", "update", 0, Map.of("existing", "kept"),
                code).withKey(key);
    }

    private static CheckIgnore ignore(CheckCode code, String rawKey, String reason) {
        return new CheckIgnore("ig-1", code.id(), CheckKeys.encode(rawKey), reason);
    }

    private static Request request(CheckIgnore... ignores) {
        return new Request("id", "http://example.com", null, List.of(), null, Map.of(), null, List.of(ignores));
    }

    private static final class StubAuditor extends AbstractLambdaAuditor {
        private final CheckStatus status;
        private final String message;
        private final List<Check> checks;

        StubAuditor(CheckStatus status, String message, Check... checks) {
            this.status = status;
            this.message = message;
            this.checks = new ArrayList<>(List.of(checks));
        }

        @Override
        public String getName() {
            return "Stub";
        }

        @Override
        public Response process(String urlStr, BasicAuth basicAuth) {
            return new Response(getAuditor(), status, message, checks);
        }
    }
}
