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

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class SiteBlockedExceptionTest {

    private static final String URL = "https://example.com/";

    /** The page Imperva served www.tokiomarine.com.br's privacy policy with, HTTP 200. */
    static final String IMPERVA_CHALLENGE = "<html style=\"height:100%\"><head><META NAME=\"ROBOTS\" CONTENT=\"NOINDEX, NOFOLLOW\">"
            + "<script src=\"/gly-thould-and-Drum-Macduffe-with-gayne-the-Roya\" async></script></head>"
            + "<body style=\"margin:0px;height:100%\"><iframe id=\"main-iframe\" src=\"/_Incapsula_Resource?"
            + "SWUDNSAI=31&xinfo=17-877612-0&incident_id=1694000060140308914-4292708254220689&mth=GET\" "
            + "frameborder=0 width=\"100%\" height=\"100%\">Request unsuccessful. Incapsula incident ID: "
            + "1694000060140308914-4292708254220689</iframe></body></html>";

    @Test
    void botWallHostsAndTheirSubdomainsAreBotWalls() {
        assertThat(SiteBlockedException.isBotWall(URI.create("http://validate.perfdrive.com/x/?ssa=1"))).isTrue();
        assertThat(SiteBlockedException.isBotWall(URI.create("https://geo.Captcha-Delivery.com/captcha/"))).isTrue();
        assertThat(SiteBlockedException.isBotWall(URI.create("https://perfdrive.com/"))).isTrue();
    }

    @Test
    void otherHostsAreNotBotWalls() {
        assertThat(SiteBlockedException.isBotWall(URI.create("https://telmex.com/"))).isFalse();
        assertThat(SiteBlockedException.isBotWall(URI.create("https://notperfdrive.com/"))).isFalse();
        assertThat(SiteBlockedException.isBotWall(URI.create("/relative"))).isFalse();
        assertThat(SiteBlockedException.isBotWall(null)).isFalse();
    }

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

    @Test
    void impervaChallengePageIsABotWall() {
        assertThat(SiteBlockedException.botWallVendor(IMPERVA_CHALLENGE)).isEqualTo("Imperva");
        assertThat(SiteBlockedException.botWallVendor("<p>Request unsuccessful. Incapsula incident ID: 1-2</p>"))
                .isEqualTo("Imperva");
    }

    @Test
    void pageServedThroughImpervaIsNotABotWall() {
        assertThat(SiteBlockedException.botWallVendor("<html><head><script src=\"/_Incapsula_Resource?"
                + "SWJIYLWA=719d34d31c8e3a6e6fffd425f7e032f3\"></script></head><body>Privacy policy</body></html>"))
                .isNull();
        assertThat(SiteBlockedException.botWallVendor(null)).isNull();
        assertThat(SiteBlockedException.botWallVendor("")).isNull();
    }

    @Test
    void markerPastTheSniffedHeadIsIgnored() {
        String page = "x".repeat(SiteBlockedException.BOT_WALL_PAGE_SNIFF_CHARS) + "Incapsula incident ID";

        assertThat(SiteBlockedException.botWallVendor(page)).isNull();
    }

    @Test
    void botWallPageCarriesUrlStatusAndVendor() {
        SiteBlockedException e = SiteBlockedException.botWallPage(URL, 200, "Imperva");

        assertThat(e.url()).isEqualTo(URL);
        assertThat(e.statusCode()).isEqualTo(200);
        assertThat(e.challenge()).isTrue();
        assertThat(e.getMessage())
                .isEqualTo("The site answered 37AuditsBot with a bot challenge (Imperva) instead of the page (HTTP 200).");
    }
}
