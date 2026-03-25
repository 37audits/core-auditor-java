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
package com.thirtysevenaudits.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import com.thirtysevenaudits.util.VersionUtil;

class VersionUtilTest {

    @Test
    void pomPropertiesResourcePaths_coverLiteralAndSlashGroupIds() {
        String[] paths = VersionUtil.pomPropertiesResourcePaths("com.example.app", "my-artifact");
        assertEquals("META-INF/maven/com.example.app/my-artifact/pom.properties", paths[0]);
        assertEquals("META-INF/maven/com/example/app/my-artifact/pom.properties", paths[1]);
    }

    @Test
    void resolveVersion_returnsNonBlank() {
        String v = VersionUtil.resolveVersion(VersionUtil.class);
        assertFalse(v.isBlank());
    }

}
