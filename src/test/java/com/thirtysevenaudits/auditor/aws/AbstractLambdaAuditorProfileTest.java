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
import com.thirtysevenaudits.auditor.UrlProfile;

class AbstractLambdaAuditorProfileTest {

    @Test
    void handleRequestForwardsProfileToProfileAwareHook() {
        ProfileAuditor auditor = new ProfileAuditor();

        auditor.handleRequest(new Request("id", "http://example.com", UrlProfile.INTRANET, List.of(), null,
                Map.of("sizeLimit", 1024)), null);

        assertThat(auditor.profile).isEqualTo(UrlProfile.INTRANET);
        assertThat(auditor.preferences).containsExactly(Map.entry("sizeLimit", 1024));
    }

    @Test
    void defaultProfileAwareHookDelegatesToPreferenceAwareHook() {
        PreferenceAuditor auditor = new PreferenceAuditor();

        auditor.handleRequest(new Request("id", "http://example.com", UrlProfile.API, List.of(), null,
                Map.of("strict", true)), null);

        assertThat(auditor.preferences).containsExactly(Map.entry("strict", true));
    }

    @Test
    void compatibilityConstructorsYieldPublicWebsite() {
        assertThat(new Request("id", "http://example.com", List.of(), null).profile())
                .isEqualTo(UrlProfile.PUBLIC_WEBSITE);
        assertThat(new Request("id", "http://example.com", List.of(), null, Map.of()).profile())
                .isEqualTo(UrlProfile.PUBLIC_WEBSITE);
        assertThat(new Request("id", "http://example.com", null, List.of(), null, Map.of()).profile())
                .isEqualTo(UrlProfile.PUBLIC_WEBSITE);
    }

    @Test
    void serializerReadsProfileFromPayload() {
        Request request = new BasePojoSerializer().fromJson(
                "{\"id\":\"1\",\"url\":\"http://example.com\",\"profile\":\"intranet\",\"stack\":[]}",
                Request.class);

        assertThat(request.profile()).isEqualTo(UrlProfile.INTRANET);
    }

    @Test
    void serializerToleratesMissingAndUnknownProfiles() {
        assertThat(read("{\"id\":\"1\",\"url\":\"http://example.com\"}").profile())
                .isEqualTo(UrlProfile.PUBLIC_WEBSITE);
        assertThat(read("{\"id\":\"1\",\"url\":\"http://example.com\",\"profile\":\"kiosk\"}").profile())
                .isEqualTo(UrlProfile.PUBLIC_WEBSITE);
        assertThat(read("{\"id\":\"1\",\"url\":\"http://example.com\",\"profile\":null}").profile())
                .isEqualTo(UrlProfile.PUBLIC_WEBSITE);
    }

    private static Request read(String json) {
        return new BasePojoSerializer().fromJson(json, Request.class);
    }

    private static final class ProfileAuditor extends AbstractLambdaAuditor {
        UrlProfile profile;
        Map<String, Object> preferences;

        @Override
        public String getName() {
            return "Profile";
        }

        @Override
        public Response process(String urlStr, BasicAuth basicAuth) {
            throw new AssertionError("legacy hook must not be called when the profile-aware one is overridden");
        }

        @Override
        public Response process(String urlStr, UrlProfile profile, BasicAuth basicAuth,
                Map<String, Object> preferences) {
            this.profile = profile;
            this.preferences = preferences;
            return new Response(getAuditor(), CheckStatus.SUCCESS, "ok", null);
        }
    }

    private static final class PreferenceAuditor extends AbstractLambdaAuditor {
        Map<String, Object> preferences;

        @Override
        public String getName() {
            return "Preference";
        }

        @Override
        public Response process(String urlStr, BasicAuth basicAuth) {
            throw new AssertionError("legacy hook must not be called when the preference-aware one is overridden");
        }

        @Override
        public Response process(String urlStr, BasicAuth basicAuth, Map<String, Object> preferences) {
            this.preferences = preferences;
            return new Response(getAuditor(), CheckStatus.SUCCESS, "ok", null);
        }
    }
}
