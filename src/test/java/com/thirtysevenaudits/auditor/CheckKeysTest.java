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

import java.util.Map;

import org.junit.jupiter.api.Test;

class CheckKeysTest {

    @Test
    void encodeIsBase64UrlWithoutPadding() {
        assertThat(CheckKeys.encode("LSV-1793")).isEqualTo("TFNWLTE3OTM");
        assertThat(CheckKeys.encode("JSESSIONID")).isEqualTo("SlNFU1NJT05JRA");
        assertThat(CheckKeys.encode("Disallow: /search?")).isEqualTo("RGlzYWxsb3c6IC9zZWFyY2g_");
    }

    @Test
    void encodedKeysUseOnlyUrlSafeCharacters() {
        for (String raw : new String[] { "Disallow: /search?", "a>b?c", "Ação & Serviços", "~~~???>>>" }) {
            assertThat(CheckKeys.encode(raw)).matches("[A-Za-z0-9_-]+");
        }
    }

    @Test
    void decodeRoundTripsUtf8() {
        assertThat(CheckKeys.decode(CheckKeys.encode("Ação & Serviços"))).isEqualTo("Ação & Serviços");
    }

    @Test
    void encodeTrimsAndTreatsBlankAsNoKey() {
        assertThat(CheckKeys.encode("  compress ")).isEqualTo(CheckKeys.encode("compress"));
        assertThat(CheckKeys.encode("   ")).isNull();
        assertThat(CheckKeys.encode(null)).isNull();
        assertThat(CheckKeys.decode(null)).isNull();
    }

    @Test
    void urlDropsQueryAndFragmentAndLowerCasesSchemeAndHost() {
        assertThat(CheckKeys.url("HTTPS://Example.COM/o/Main.js?v=3#top"))
                .isEqualTo("https://example.com/o/Main.js");
        assertThat(CheckKeys.url("https://example.com")).isEqualTo("https://example.com");
        assertThat(CheckKeys.url("/api/jsonws?x=1")).isEqualTo("/api/jsonws");
        assertThat(CheckKeys.url("not a uri ?x")).isEqualTo("not a uri ");
        assertThat(CheckKeys.url(null)).isNull();
    }

    @Test
    void tokenTrimsAndLowerCases() {
        assertThat(CheckKeys.token("  Permissions-Policy ")).isEqualTo("permissions-policy");
        assertThat(CheckKeys.token(null)).isNull();
    }

    @Test
    void withKeyEncodesTheRawValueAndKeepsEverythingElse() {
        Check check = new Check(CheckStatus.FAIL, "https://example.com", "m", "r", 0, Map.of("a", 1),
                new CheckCode("37A-Test-400", "t"), 2).withKey("LSV-1793");

        assertThat(check.key()).isEqualTo("TFNWLTE3OTM");
        assertThat(check.order()).isEqualTo(2);
        assertThat(check.data()).containsEntry("a", 1);
    }
}
