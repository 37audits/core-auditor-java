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

import org.junit.jupiter.api.Test;

class UrlProfileTest {

    @Test
    void fromReadsSlugsIgnoringCase() {
        assertThat(UrlProfile.from("intranet")).isEqualTo(UrlProfile.INTRANET);
        assertThat(UrlProfile.from("Customer_Portal")).isEqualTo(UrlProfile.CUSTOMER_PORTAL);
        assertThat(UrlProfile.from(" redirect_domain ")).isEqualTo(UrlProfile.REDIRECT_DOMAIN);
    }

    @Test
    void fromDefaultsToPublicWebsite() {
        assertThat(UrlProfile.from(null)).isEqualTo(UrlProfile.PUBLIC_WEBSITE);
        assertThat(UrlProfile.from(" ")).isEqualTo(UrlProfile.PUBLIC_WEBSITE);
        assertThat(UrlProfile.from("kiosk")).isEqualTo(UrlProfile.PUBLIC_WEBSITE);
    }

    @Test
    void slugIsTheLowerCaseName() {
        assertThat(UrlProfile.WEB_APP.slug()).isEqualTo("web_app");
    }
}
