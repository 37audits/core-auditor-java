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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class SiteBlockedExceptionTest {

    private static final String URL = "https://example.com/";

    @Test
    void forbiddenAndRateLimitedResponsesAreBlocks() {
        assertThat(SiteBlockedException.isBlocked(403, null)).isTrue();
        assertThat(SiteBlockedException.isBlocked(429, null)).isTrue();
    }

    @Test
    void otherStatusCodesAreNotBlocks() {
        assertThat(List.of(200, 301, 401, 404, 500, 503))
                .noneMatch(statusCode -> SiteBlockedException.isBlocked(statusCode, null));
    }

    @Test
    void cloudflareChallengeIsABlockWhateverTheStatusCode() {
        assertThat(SiteBlockedException.isBlocked(503, "challenge")).isTrue();
        assertThat(SiteBlockedException.isBlocked(200, " Challenge ")).isTrue();
        assertThat(SiteBlockedException.isBlocked(200, "something-else")).isFalse();
    }

    @Test
    void throwIfBlockedCarriesUrlStatusAndReason() {
        assertThatThrownBy(() -> SiteBlockedException.throwIfBlocked(URL, 403, null))
                .isInstanceOfSatisfying(SiteBlockedException.class, e -> {
                    assertThat(e.url()).isEqualTo(URL);
                    assertThat(e.statusCode()).isEqualTo(403);
                    assertThat(e.challenge()).isFalse();
                    assertThat(e).hasMessage("The site blocked 37AuditsBot's request (HTTP 403).");
                });
        assertThatThrownBy(() -> SiteBlockedException.throwIfBlocked(URL, 429, null))
                .hasMessage("The site rate limited 37AuditsBot (HTTP 429).");
        assertThatThrownBy(() -> SiteBlockedException.throwIfBlocked(URL, 403, "challenge"))
                .isInstanceOfSatisfying(SiteBlockedException.class, e -> assertThat(e.challenge()).isTrue())
                .hasMessage("The site answered 37AuditsBot with a bot challenge instead of the page (HTTP 403).");
    }

    @Test
    void throwIfBlockedLetsAServedPageThrough() {
        assertThatCode(() -> SiteBlockedException.throwIfBlocked(URL, 200, null)).doesNotThrowAnyException();
    }

    @Test
    void headerMapOverloadReadsTheCrawlerStatusCodeIgnoringCase() {
        assertThatThrownBy(
                () -> SiteBlockedException.throwIfBlocked(URL, Map.of("http-status-code", List.of("403"))))
                .isInstanceOf(SiteBlockedException.class);
        assertThatThrownBy(() -> SiteBlockedException.throwIfBlocked(URL,
                Map.of("http-status-code", List.of("503"), "CF-Mitigated", List.of("challenge"))))
                .isInstanceOfSatisfying(SiteBlockedException.class, e -> {
                    assertThat(e.statusCode()).isEqualTo(503);
                    assertThat(e.challenge()).isTrue();
                });
    }

    @Test
    void headerMapOverloadIgnoresMapsWithoutAReadableStatusCode() {
        assertThatCode(() -> SiteBlockedException.throwIfBlocked(URL, (Map<String, List<String>>) null))
                .doesNotThrowAnyException();
        assertThatCode(() -> SiteBlockedException.throwIfBlocked(URL, Map.of("server", List.of("nginx"))))
                .doesNotThrowAnyException();
        assertThatCode(() -> SiteBlockedException.throwIfBlocked(URL, Map.of("http-status-code", List.of("abc"))))
                .doesNotThrowAnyException();
        assertThatCode(() -> SiteBlockedException.throwIfBlocked(URL, Map.of("http-status-code", List.of("200"))))
                .doesNotThrowAnyException();
    }
}
