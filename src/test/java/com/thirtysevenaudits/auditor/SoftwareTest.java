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

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.thirtysevenaudits.auditor.aws.BasePojoSerializer;

class SoftwareTest {

    @Test
    void serializerReadsUpdatedAtFromStackEntry() {
        Request request = new BasePojoSerializer().fromJson(
                "{\"id\":\"1\",\"url\":\"http://example.com\",\"stack\":[{\"vendor\":\"Liferay\","
                        + "\"product\":\"DXP\",\"version\":\"7.4\",\"updatedAt\":\"2026-06-16T10:15:30Z\"}]}",
                Request.class);

        assertThat(request.stack()).containsExactly(
                new Software("Liferay", "DXP", "7.4", Instant.parse("2026-06-16T10:15:30Z")));
    }

    @Test
    void serializerToleratesStackEntryWithoutUpdatedAt() {
        Request request = new BasePojoSerializer().fromJson(
                "{\"id\":\"1\",\"url\":\"http://example.com\",\"stack\":[{\"vendor\":\"Liferay\","
                        + "\"product\":\"DXP\",\"version\":\"7.4\"}]}",
                Request.class);

        assertThat(request.stack()).hasSize(1);
        assertThat(request.stack().get(0).updatedAt()).isNull();
        assertThat(request.stack().get(0).product()).isEqualTo("DXP");
    }

    @Test
    void compatibilityConstructorLeavesUpdatedAtNullAndKeepsToString() {
        Software software = new Software("Liferay", "DXP", "7.4");

        assertThat(software.updatedAt()).isNull();
        assertThat(software).hasToString("Liferay DXP 7.4");
    }

}
