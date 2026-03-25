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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HeadersUtil {

    public static Map<String, List<String>> convertKeyToLowercase(Map<String, List<String>> headers) {
        Map<String, List<String>> lowerHeaders = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            var key = entry.getKey();
            if (key != null) {
                lowerHeaders.put(key.toLowerCase(), entry.getValue());
            } else {
                lowerHeaders.put("http-status-code", entry.getValue());
            }
        }
        return lowerHeaders;
    }
}
