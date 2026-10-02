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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.thirtysevenaudits.auditor.aws.BasePojoSerializer;

class CheckTest {

    private static final CheckCode CODE = new CheckCode("37A-Test-001", "test");

    @Test
    void constructorWithoutOrderLeavesItNull() {
        Check check = new Check(CheckStatus.SUCCESS, "https://example.com", "ok", null, 0, Map.of(), CODE);

        assertThat(check.order()).isNull();
    }

    @Test
    void convertFailToWarningKeepsOrder() {
        Check check = new Check(CheckStatus.FAIL, "https://example.com", "broken", "fix it", 0, Map.of(), CODE, 3);

        Check converted = Check.convertFailToWarning(check);

        assertThat(converted.status()).isEqualTo(CheckStatus.WARNING);
        assertThat(converted.order()).isEqualTo(3);
    }

    @Test
    void orderIsSerialized() {
        Check ordered = new Check(CheckStatus.SUCCESS, "https://example.com", "ok", null, 0, Map.of(), CODE, 2);
        Check unordered = new Check(CheckStatus.SUCCESS, "https://example.com", "ok", null, 0, Map.of(), CODE);

        assertThat(toJson(ordered)).contains("\"order\":2");
        assertThat(toJson(unordered)).contains("\"order\":null");
    }

    @Test
    void codeConstructorWithoutRationaleLeavesItNull() {
        assertThat(CODE.rationale()).isNull();
    }

    @Test
    void codeRationaleIsSerialized() {
        CheckCode code = new CheckCode("37A-Test-400", "test", "Visitors leave slow pages.");
        Check explained = new Check(CheckStatus.FAIL, "https://example.com", "broken", "fix it", 0, Map.of(), code);
        Check unexplained = new Check(CheckStatus.SUCCESS, "https://example.com", "ok", null, 0, Map.of(), CODE);

        assertThat(toJson(explained)).contains("\"rationale\":\"Visitors leave slow pages.\"");
        assertThat(toJson(unexplained)).contains("\"rationale\":null");
    }

    private static String toJson(Check check) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        new BasePojoSerializer().toJson(check, output, Check.class);

        return output.toString(StandardCharsets.UTF_8);
    }
}
