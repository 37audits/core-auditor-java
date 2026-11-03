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
package com.thirtysevenaudits.auditor.testing;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.thirtysevenaudits.auditor.Check;
import com.thirtysevenaudits.auditor.CheckCode;
import com.thirtysevenaudits.auditor.CheckStatus;

class CheckAssertionsTest {

    private static final CheckCode CODE = new CheckCode("37A-Test-400", "t");

    @Test
    void passesWhenEveryFailAndWarningHasAKey() {
        assertThatCode(() -> CheckAssertions.assertKeyed(List.of(check(CheckStatus.FAIL).withKey("a"),
                check(CheckStatus.WARNING).withKey("b"), check(CheckStatus.SUCCESS), check(CheckStatus.ERROR))))
                .doesNotThrowAnyException();
    }

    @Test
    void failsNamingTheUnkeyedCodes() {
        assertThatThrownBy(() -> CheckAssertions.assertKeyed(List.of(check(CheckStatus.WARNING))))
                .isInstanceOf(AssertionError.class).hasMessageContaining("WARNING 37A-Test-400");
    }

    private static Check check(CheckStatus status) {
        return new Check(status, "u", "m", null, 0, Map.of(), CODE);
    }
}
