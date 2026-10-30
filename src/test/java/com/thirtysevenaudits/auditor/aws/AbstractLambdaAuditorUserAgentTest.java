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

class AbstractLambdaAuditorUserAgentTest {

    private static final String BOT_UA = "37AuditsBot/1.0 (+https://www.37audits.com/bot)";

    @Test
    void getUserAgentReturnsTheCustomAgentWhenTheRequestSetsOne() {
        RecordingAuditor auditor = new RecordingAuditor();

        auditor.handleRequest(request("Custom/1.0"), null);

        assertThat(auditor.seenUserAgent).isEqualTo("Custom/1.0");
    }

    @Test
    void getUserAgentFallsBackToTheBotAgentWhenNoCustomAgentIsSet() {
        RecordingAuditor auditor = new RecordingAuditor();

        auditor.handleRequest(request(null), null);

        assertThat(auditor.seenUserAgent).isEqualTo(BOT_UA);
    }

    @Test
    void aBlankCustomAgentIsTreatedAsUnset() {
        RecordingAuditor auditor = new RecordingAuditor();

        auditor.handleRequest(request("   "), null);

        assertThat(auditor.seenUserAgent).isEqualTo(BOT_UA);
    }

    @Test
    void aCustomAgentDoesNotLeakIntoTheNextInvocationOfAWarmContainer() {
        RecordingAuditor auditor = new RecordingAuditor();

        auditor.handleRequest(request("Custom/1.0"), null);
        auditor.handleRequest(request(null), null);

        assertThat(auditor.seenUserAgent).isEqualTo(BOT_UA);
    }

    @Test
    void get37AuditsUserAgentAlwaysReturnsTheBotAgentRegardlessOfTheOverride() {
        RecordingAuditor auditor = new RecordingAuditor();

        auditor.handleRequest(request("Custom/1.0"), null);

        assertThat(auditor.seenBotUserAgent).isEqualTo(BOT_UA);
    }

    @Test
    void serializerReadsUserAgentFromThePayloadAndToleratesItsAbsence() {
        assertThat(read("{\"id\":\"1\",\"url\":\"http://example.com\",\"userAgent\":\"Custom/1.0\"}").userAgent())
                .isEqualTo("Custom/1.0");
        assertThat(read("{\"id\":\"1\",\"url\":\"http://example.com\"}").userAgent()).isNull();
        assertThat(read("{\"id\":\"1\",\"url\":\"http://example.com\",\"userAgent\":\"  \"}").userAgent()).isNull();
    }

    private static Request read(String json) {
        return new BasePojoSerializer().fromJson(json, Request.class);
    }

    private static Request request(String userAgent) {
        return new Request("id", "http://example.com", null, List.of(), null, Map.of(), userAgent);
    }

    private static final class RecordingAuditor extends AbstractLambdaAuditor {
        String seenUserAgent;
        String seenBotUserAgent;

        @Override
        public String getName() {
            return "Recording";
        }

        @Override
        public Response process(String urlStr, BasicAuth basicAuth) {
            seenUserAgent = getUserAgent();
            seenBotUserAgent = get37AuditsUserAgent();
            return new Response(getAuditor(), CheckStatus.SUCCESS, "ok", null);
        }
    }
}
