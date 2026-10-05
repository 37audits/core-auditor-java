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

import java.util.List;
import java.util.Map;

/**
 * The audited site refused the auditor's request instead of serving the page: a firewall, a bot-protection
 * service or a rate limiter answered. Whatever came back is the block page, not the site, so an auditor must not
 * evaluate it.
 *
 * <p>
 * Auditors call {@link #throwIfBlocked(String, int, String)} on the response of the audited page, before reading
 * it. {@code AbstractLambdaAuditor.handleRequest} catches the exception and answers with an
 * {@link CheckStatus#ERROR} response that tells the site owner the bot was blocked.
 */
public class SiteBlockedException extends RuntimeException {

    /** Cloudflare sets this response header to {@code challenge} when it serves a challenge page. */
    public static final String HEADER_CF_MITIGATED = "cf-mitigated";

    /** Key under which {@code HeadersCrawler} reports the response status code. */
    private static final String HEADER_HTTP_STATUS_CODE = "http-status-code";

    private static final long serialVersionUID = 1L;

    private final String url;
    private final int statusCode;
    private final boolean challenge;

    public SiteBlockedException(String url, int statusCode, boolean challenge) {
        super(describe(statusCode, challenge));
        this.url = url;
        this.statusCode = statusCode;
        this.challenge = challenge;
    }

    /** The URL whose request was refused. */
    public String url() {
        return url;
    }

    /** The HTTP status code of the refusal. */
    public int statusCode() {
        return statusCode;
    }

    /** {@code true} when the site answered with a bot challenge page rather than a plain refusal. */
    public boolean challenge() {
        return challenge;
    }

    /**
     * Whether a response is a refusal of the request rather than the page itself: HTTP 403 (forbidden), HTTP 429
     * (rate limited) or a Cloudflare challenge, whatever its status code.
     *
     * @param cfMitigated
     *            value of the {@value #HEADER_CF_MITIGATED} response header, or {@code null} when absent.
     */
    public static boolean isBlocked(int statusCode, String cfMitigated) {
        return statusCode == 403 || statusCode == 429 || isChallenge(cfMitigated);
    }

    /**
     * Throws when the response of {@code url} is a refusal, see {@link #isBlocked(int, String)}.
     *
     * @param cfMitigated
     *            value of the {@value #HEADER_CF_MITIGATED} response header, or {@code null} when absent.
     */
    public static void throwIfBlocked(String url, int statusCode, String cfMitigated) {
        if (isBlocked(statusCode, cfMitigated)) {
            throw new SiteBlockedException(url, statusCode, isChallenge(cfMitigated));
        }
    }

    /**
     * Same as {@link #throwIfBlocked(String, int, String)} for the header map {@code HeadersCrawler} returns, which
     * carries the status code under {@code http-status-code}. Header names are matched ignoring case. A map without
     * a readable status code is not a refusal.
     */
    public static void throwIfBlocked(String url, Map<String, List<String>> headers) {
        if (headers == null) {
            return;
        }

        String status = first(headers, HEADER_HTTP_STATUS_CODE);
        int statusCode;

        try {
            statusCode = (status != null) ? Integer.parseInt(status.trim()) : 0;
        } catch (NumberFormatException e) {
            statusCode = 0;
        }

        throwIfBlocked(url, statusCode, first(headers, HEADER_CF_MITIGATED));
    }

    private static boolean isChallenge(String cfMitigated) {
        return cfMitigated != null && cfMitigated.trim().equalsIgnoreCase("challenge");
    }

    private static String first(Map<String, List<String>> headers, String name) {
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name) && entry.getValue() != null
                    && !entry.getValue().isEmpty()) {
                return entry.getValue().get(0);
            }
        }

        return null;
    }

    private static String describe(int statusCode, boolean challenge) {
        if (challenge) {
            return "The site answered 37AuditsBot with a bot challenge instead of the page (HTTP " + statusCode + ").";
        }

        if (statusCode == 429) {
            return "The site rate limited 37AuditsBot (HTTP 429).";
        }

        return "The site blocked 37AuditsBot's request (HTTP " + statusCode + ").";
    }
}
