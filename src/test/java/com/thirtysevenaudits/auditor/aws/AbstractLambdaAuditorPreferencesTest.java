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
import com.thirtysevenaudits.auditor.CheckStatus;
import com.thirtysevenaudits.auditor.Request;
import com.thirtysevenaudits.auditor.Response;

class AbstractLambdaAuditorPreferencesTest {

    @Test
    void handleRequestForwardsPreferencesToPreferenceAwareHook() {
        RecordingAuditor auditor = new RecordingAuditor();
        Request request = new Request("id", "http://example.com", List.of(), null, Map.of("sizeLimit", 1024));

        auditor.handleRequest(request, null);

        assertThat(auditor.received).containsExactly(Map.entry("sizeLimit", 1024));
    }

    @Test
    void compatibilityConstructorYieldsEmptyPreferences() {
        Request request = new Request("id", "http://example.com", List.of(), null);

        assertThat(request.preferences()).isEmpty();
    }

    @Test
    void nullPreferencesBecomeEmptyMap() {
        Request request = new Request("id", "http://example.com", List.of(), null, null);

        assertThat(request.preferences()).isEmpty();
    }

    @Test
    void defaultPreferenceAwareHookDelegatesToLegacyProcess() {
        LegacyAuditor auditor = new LegacyAuditor();

        Response response = auditor.handleRequest(
                new Request("id", "http://example.com", List.of(), null, Map.of("ignored", true)), null);

        assertThat(auditor.legacyCalls).isEqualTo(1);
        assertThat(response.status()).isEqualTo(CheckStatus.SUCCESS);
    }

    @Test
    void serializerReadsPreferencesFromPayload() {
        Request request = new BasePojoSerializer().fromJson(
                "{\"id\":\"1\",\"url\":\"http://example.com\",\"stack\":[],\"checker\":\"x\","
                        + "\"preferences\":{\"sizeLimit\":1048576,\"strict\":true}}",
                Request.class);

        assertThat(request.preferences())
                .containsEntry("sizeLimit", 1048576)
                .containsEntry("strict", true);
    }

    @Test
    void serializerToleratesPayloadWithoutPreferences() {
        Request request = new BasePojoSerializer().fromJson(
                "{\"id\":\"1\",\"url\":\"http://example.com\",\"stack\":[],\"checker\":\"x\"}",
                Request.class);

        assertThat(request.url()).isEqualTo("http://example.com");
        assertThat(request.preferences()).isEmpty();
    }

    private static final class RecordingAuditor extends AbstractLambdaAuditor {
        Map<String, Object> received;

        @Override
        public String getName() {
            return "Recording";
        }

        @Override
        public Response process(String urlStr, BasicAuth basicAuth) {
            throw new AssertionError("legacy hook must not be called when the preference-aware one is overridden");
        }

        @Override
        public Response process(String urlStr, BasicAuth basicAuth, Map<String, Object> preferences) {
            received = preferences;
            return new Response(getAuditor(), CheckStatus.SUCCESS, "ok", null);
        }
    }

    private static final class LegacyAuditor extends AbstractLambdaAuditor {
        int legacyCalls;

        @Override
        public String getName() {
            return "Legacy";
        }

        @Override
        public Response process(String urlStr, BasicAuth basicAuth) {
            legacyCalls++;
            return new Response(getAuditor(), CheckStatus.SUCCESS, "ok", null);
        }
    }
}
