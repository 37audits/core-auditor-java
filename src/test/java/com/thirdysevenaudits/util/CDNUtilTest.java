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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class CDNUtilTest {

    private Map<String, List<String>> createHeaders(String headerName, String... values) {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put(headerName, Arrays.asList(values));
        return headers;
    }

    private Map<String, List<String>> createMultipleHeaders(Map<String, String[]> headerMap) {
        Map<String, List<String>> headers = new HashMap<>();
        for (Map.Entry<String, String[]> entry : headerMap.entrySet()) {
            headers.put(entry.getKey(), Arrays.asList(entry.getValue()));
        }
        return headers;
    }

    // Tests for isServerHeaderPointingtoCDN method

    @Test
    void isServerHeaderPointingtoCDN_cloudflare_shouldReturnTrue() {
        Map<String, List<String>> headers = createHeaders("Server", "cloudflare");
        assertTrue(CDNUtil.isServerHeaderPointingtoCDN(headers));
    }

    @Test
    void isServerHeaderPointingtoCDN_cloudfront_shouldReturnTrue() {
        Map<String, List<String>> headers = createHeaders("Server", "CloudFront");
        assertTrue(CDNUtil.isServerHeaderPointingtoCDN(headers));
    }

    @Test
    void isServerHeaderPointingtoCDN_akamai_shouldReturnTrue() {
        Map<String, List<String>> headers = createHeaders("Server", "AkamaiGHost");
        assertTrue(CDNUtil.isServerHeaderPointingtoCDN(headers));
    }

    @Test
    void isServerHeaderPointingtoCDN_caseInsensitive_shouldReturnTrue() {
        Map<String, List<String>> headers = createHeaders("SERVER", "CLOUDFLARE");
        assertTrue(CDNUtil.isServerHeaderPointingtoCDN(headers));
    }

    @Test
    void isServerHeaderPointingtoCDN_mixedCase_shouldReturnTrue() {
        Map<String, List<String>> headers = createHeaders("Server", "nginx via CloudFlare");
        assertTrue(CDNUtil.isServerHeaderPointingtoCDN(headers));
    }

    @Test
    void isServerHeaderPointingtoCDN_nonCdnServer_shouldReturnFalse() {
        Map<String, List<String>> headers = createHeaders("Server", "nginx/1.18.0");
        assertFalse(CDNUtil.isServerHeaderPointingtoCDN(headers));
    }

    @Test
    void isServerHeaderPointingtoCDN_noServerHeader_shouldReturnFalse() {
        Map<String, List<String>> headers = createHeaders("X-Powered-By", "PHP/7.4.3");
        assertFalse(CDNUtil.isServerHeaderPointingtoCDN(headers));
    }

    @Test
    void isServerHeaderPointingtoCDN_emptyHeaders_shouldReturnFalse() {
        Map<String, List<String>> headers = new HashMap<>();
        assertFalse(CDNUtil.isServerHeaderPointingtoCDN(headers));
    }

    @Test
    void isServerHeaderPointingtoCDN_nullServerValue_shouldReturnFalse() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Server", Arrays.asList((String) null));
        assertFalse(CDNUtil.isServerHeaderPointingtoCDN(headers));
    }

    @Test
    void isServerHeaderPointingtoCDN_multipleServerValues_oneCdn_shouldReturnTrue() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Server", Arrays.asList("nginx", "cloudflare"));
        assertTrue(CDNUtil.isServerHeaderPointingtoCDN(headers));
    }

    // Tests for hasHeaderCdnFingerprints method

    @Test
    void hasHeaderCdnFingerprints_cloudflareHeader_shouldReturnTrue() {
        Map<String, List<String>> headers = createHeaders("CF-Cache-Status", "HIT");
        assertTrue(CDNUtil.hasHeaderCdnFingerprints(headers));
    }

    @Test
    void hasHeaderCdnFingerprints_cloudfrontHeaders_shouldReturnTrue() {
        Map<String, List<String>> headers = createHeaders("X-Amz-Cf-Id",
                "E1234567890123456789012345678901234567890123456789012345678901234-abcdefghijklmnop==");
        assertTrue(CDNUtil.hasHeaderCdnFingerprints(headers));
    }

    @Test
    void hasHeaderCdnFingerprints_cloudfrontPop_shouldReturnTrue() {
        Map<String, List<String>> headers = createHeaders("X-Amz-Cf-Pop", "LAX1-C1");
        assertTrue(CDNUtil.hasHeaderCdnFingerprints(headers));
    }

    @Test
    void hasHeaderCdnFingerprints_viaCloudfront_shouldReturnTrue() {
        Map<String, List<String>> headers = createHeaders("Via", "1.1 cloudfront");
        assertTrue(CDNUtil.hasHeaderCdnFingerprints(headers));
    }

    @Test
    void hasHeaderCdnFingerprints_viaVarnish_shouldReturnTrue() {
        Map<String, List<String>> headers = createHeaders("Via", "1.1 varnish");
        assertTrue(CDNUtil.hasHeaderCdnFingerprints(headers));
    }

    @Test
    void hasHeaderCdnFingerprints_viaAkamai_shouldReturnTrue() {
        Map<String, List<String>> headers = createHeaders("Via", "1.1 akamai");
        assertTrue(CDNUtil.hasHeaderCdnFingerprints(headers));
    }

    @Test
    void hasHeaderCdnFingerprints_fastlyServedBy_shouldReturnTrue() {
        Map<String, List<String>> headers = createHeaders("X-Served-By", "cache-lax13374-LAX");
        assertTrue(CDNUtil.hasHeaderCdnFingerprints(headers));
    }

    @Test
    void hasHeaderCdnFingerprints_fastlyCache_shouldReturnTrue() {
        Map<String, List<String>> headers = createHeaders("X-Cache", "HIT");
        assertTrue(CDNUtil.hasHeaderCdnFingerprints(headers));
    }

    @Test
    void hasHeaderCdnFingerprints_fastlyCacheHits_shouldReturnTrue() {
        Map<String, List<String>> headers = createHeaders("X-Cache-Hits", "1");
        assertTrue(CDNUtil.hasHeaderCdnFingerprints(headers));
    }

    @Test
    void hasHeaderCdnFingerprints_akamaiHeader_shouldReturnTrue() {
        Map<String, List<String>> headers = createHeaders("X-Akamai-Edgescape", "georegion=246,country_code=US");
        assertTrue(CDNUtil.hasHeaderCdnFingerprints(headers));
    }

    @Test
    void hasHeaderCdnFingerprints_vercelId_shouldReturnTrue() {
        Map<String, List<String>> headers = createHeaders("X-Vercel-Id", "cle1::abcd-1234567890abcdef");
        assertTrue(CDNUtil.hasHeaderCdnFingerprints(headers));
    }

    @Test
    void hasHeaderCdnFingerprints_vercelIdCaseInsensitive_shouldReturnTrue() {
        Map<String, List<String>> headers = createHeaders("x-vercel-id", "cle1::abcd-1234567890abcdef");
        assertTrue(CDNUtil.hasHeaderCdnFingerprints(headers));
    }

    @Test
    void hasHeaderCdnFingerprints_serverHeaderCdn_shouldReturnTrue() {
        Map<String, List<String>> headers = createHeaders("Server", "cloudflare");
        assertTrue(CDNUtil.hasHeaderCdnFingerprints(headers));
    }

    @Test
    void hasHeaderCdnFingerprints_caseInsensitive_shouldReturnTrue() {
        Map<String, List<String>> headers = createHeaders("cf-cache-status", "HIT");
        assertTrue(CDNUtil.hasHeaderCdnFingerprints(headers));
    }

    @Test
    void hasHeaderCdnFingerprints_multipleCdnHeaders_shouldReturnTrue() {
        Map<String, String[]> headerMap = new HashMap<>();
        headerMap.put("CF-Cache-Status", new String[] { "HIT" });
        headerMap.put("X-Vercel-Id", new String[] { "cle1::abcd" });
        Map<String, List<String>> headers = createMultipleHeaders(headerMap);
        assertTrue(CDNUtil.hasHeaderCdnFingerprints(headers));
    }

    @Test
    void hasHeaderCdnFingerprints_noCdnHeaders_shouldReturnFalse() {
        Map<String, List<String>> headers = createHeaders("X-Powered-By", "PHP/7.4.3");
        assertFalse(CDNUtil.hasHeaderCdnFingerprints(headers));
    }

    @Test
    void hasHeaderCdnFingerprints_regularServerHeader_shouldReturnFalse() {
        Map<String, List<String>> headers = createHeaders("Server", "nginx/1.18.0");
        assertFalse(CDNUtil.hasHeaderCdnFingerprints(headers));
    }

    @Test
    void hasHeaderCdnFingerprints_emptyHeaders_shouldReturnFalse() {
        Map<String, List<String>> headers = new HashMap<>();
        assertFalse(CDNUtil.hasHeaderCdnFingerprints(headers));
    }

    @Test
    void hasHeaderCdnFingerprints_nullHeaders_shouldReturnFalse() {
        assertFalse(CDNUtil.hasHeaderCdnFingerprints(null));
    }

    @Test
    void hasHeaderCdnFingerprints_nullHeaderKey_shouldHandleGracefully() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put(null, Arrays.asList("some-value"));
        headers.put("X-Powered-By", Arrays.asList("PHP/7.4.3"));
        assertFalse(CDNUtil.hasHeaderCdnFingerprints(headers));
    }

    @Test
    void hasHeaderCdnFingerprints_nullHeaderValue_shouldHandleGracefully() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Via", Arrays.asList((String) null));
        assertFalse(CDNUtil.hasHeaderCdnFingerprints(headers));
    }

    @Test
    void hasHeaderCdnFingerprints_multipleViaValues_oneCdn_shouldReturnTrue() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Via", Arrays.asList("1.1 proxy", "1.1 cloudfront", "1.1 nginx"));
        assertTrue(CDNUtil.hasHeaderCdnFingerprints(headers));
    }

    // Edge case tests

    @Test
    void hasHeaderCdnFingerprints_partialAkamaiHeader_shouldReturnTrue() {
        Map<String, List<String>> headers = createHeaders("X-Akamai-Config-Log-Detail", "true");
        assertTrue(CDNUtil.hasHeaderCdnFingerprints(headers));
    }

    @Test
    void hasHeaderCdnFingerprints_nonAkamaiXHeader_shouldReturnFalse() {
        Map<String, List<String>> headers = createHeaders("X-Something-Else", "value");
        assertFalse(CDNUtil.hasHeaderCdnFingerprints(headers));
    }

    @Test
    void hasHeaderCdnFingerprints_realWorldExample_shouldReturnTrue() {
        // Real-world example with multiple headers
        Map<String, String[]> headerMap = new HashMap<>();
        headerMap.put("Server", new String[] { "cloudflare" });
        headerMap.put("CF-Cache-Status", new String[] { "HIT" });
        headerMap.put("CF-Ray", new String[] { "123456789abcdef-LAX" });
        headerMap.put("Content-Type", new String[] { "text/html; charset=UTF-8" });
        headerMap.put("Date", new String[] { "Mon, 01 Jan 2024 12:00:00 GMT" });

        Map<String, List<String>> headers = createMultipleHeaders(headerMap);
        assertTrue(CDNUtil.hasHeaderCdnFingerprints(headers));
    }

    @Test
    void hasHeaderCdnFingerprints_vercelRealWorldExample_shouldReturnTrue() {
        // Real-world Vercel example
        Map<String, String[]> headerMap = new HashMap<>();
        headerMap.put("Server", new String[] { "Vercel" });
        headerMap.put("X-Vercel-Id", new String[] { "cle1::abcd-1234567890abcdef" });
        headerMap.put("X-Vercel-Cache", new String[] { "HIT" });
        headerMap.put("Content-Type", new String[] { "text/html; charset=utf-8" });

        Map<String, List<String>> headers = createMultipleHeaders(headerMap);
        assertTrue(CDNUtil.hasHeaderCdnFingerprints(headers));
    }
}
