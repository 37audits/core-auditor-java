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
package com.thirdysevenaudits.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CDNUtil {

    public static boolean isServerHeaderPointingtoCDN(Map<String, List<String>> headers) {
        Map<String, List<String>> lowerHeaders = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey() != null) {
                lowerHeaders.put(entry.getKey().toLowerCase(), entry.getValue());
            }
        }
        List<String> serverHeaders = lowerHeaders.get("server");
        if (serverHeaders != null) {
            for (String serverValue : serverHeaders) {
                if (serverValue != null) {
                    var server = serverValue.toLowerCase();
                    if (server.contains("cloudflare") || server.contains("akamaighost") || server.contains("cloudfront")
                            || server.contains("google tag manager")) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public static boolean hasHeaderCdnFingerprints(Map<String, List<String>> headers) {
        if (headers == null) {
            return false;
        }

        // Convert headers to lowercase for case-insensitive comparison
        Map<String, List<String>> lowerHeaders = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey() != null) {
                lowerHeaders.put(entry.getKey().toLowerCase(), entry.getValue());
            }
        }

        // Check for Cloudflare
        if (lowerHeaders.containsKey("cf-cache-status")) {
            return true;
        }

        var isServerHeaderPointingtoCDN = isServerHeaderPointingtoCDN(headers);
        if (isServerHeaderPointingtoCDN) {
            return true;
        }

        // Check for AWS CloudFront
        if (lowerHeaders.containsKey("x-amz-cf-id") || lowerHeaders.containsKey("x-amz-cf-pop")) {
            return true;
        }

        List<String> viaHeaders = lowerHeaders.get("via");
        if (viaHeaders != null) {
            for (String viaValue : viaHeaders) {
                if (viaValue != null) {
                    var via = viaValue.toLowerCase();
                    if (via.contains("cloudfront") || via.contains("varnish") || via.contains("akamai")) {
                        return true;
                    }
                }
            }
        }

        // Check for Fastly
        if (lowerHeaders.containsKey("x-served-by") || lowerHeaders.containsKey("x-cache")
                || lowerHeaders.containsKey("x-cache-hits")) {
            return true;
        }

        // Check for Akamai headers
        for (String headerName : lowerHeaders.keySet()) {
            if (headerName.startsWith("x-akamai-")) {
                return true;
            }
        }

        // Check for Vercel
        if (lowerHeaders.containsKey("x-vercel-id")) {
            return true;
        }

        // Check for Google
        List<String> viaValues = lowerHeaders.get("via");
        if (viaValues != null) {
            for (String viaValue : viaValues) {
                if ("1.1 google".equalsIgnoreCase(viaValue)) {
                    return true;
                }
            }
        }

        return false;
    }

}
