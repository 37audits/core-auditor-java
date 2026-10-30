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

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * What kind of site the monitored URL is, as chosen on the platform. Rules use
 * it to tell a real problem from the expected setup of that kind of site (for
 * example crawlers blocked on an intranet).
 *
 * <p>On the wire the profile is its {@link #slug()}. {@link #from(String)} is
 * tolerant: a missing, blank or unknown value yields {@link #PUBLIC_WEBSITE},
 * so the platform can add a profile before the auditors know it.
 */
public enum UrlProfile {

    PUBLIC_WEBSITE, CUSTOMER_PORTAL, INTRANET, ECOMMERCE, WEB_APP, API, REDIRECT_DOMAIN;

    /** The platform's name for the profile, for example {@code public_website}. */
    @JsonValue
    public String slug() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static UrlProfile from(String value) {
        if (value == null || value.isBlank()) {
            return PUBLIC_WEBSITE;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return PUBLIC_WEBSITE;
        }
    }

}
