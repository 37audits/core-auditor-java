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
package com.thirtysevenaudits.auditor.aws;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.thirtysevenaudits.auditor.Auditor;
import com.thirtysevenaudits.auditor.BasicAuth;
import com.thirtysevenaudits.auditor.Check;
import com.thirtysevenaudits.auditor.CheckCode;
import com.thirtysevenaudits.auditor.CheckStatus;
import com.thirtysevenaudits.auditor.Request;
import com.thirtysevenaudits.auditor.Response;
import com.thirtysevenaudits.auditor.SiteBlockedException;
import com.thirtysevenaudits.crawler.WebBotAuthSigner;
import com.thirtysevenaudits.util.VersionUtil;

public abstract class AbstractLambdaAuditor implements RequestHandler<Request, Response> {

    /** Number of the check code reported when the site refused the auditor's request. */
    public static final int CODE_SITE_BLOCKED = 590;

    private static final String BLOCKED_RECOMMENDATION = "Allow 37AuditsBot in your firewall or bot protection: "
            + "allowlist its user agent, or verify its Web Bot Auth signature. See https://www.37audits.com/bot.";

    private static final int MAX_FAILURE_MESSAGE_LENGTH = 300;

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    // Set by ensureNotBlocked for the run in progress; a Lambda instance handles one request at a time.
    private SiteBlockedException blockedBy;

    public String getId() {
        return this.getClass().getName();
    }

    public abstract String getName();

    public String getVersion() {
        return VersionUtil.resolveVersion(getClass());
    }

    public String getUserAgent() {
        return "37AuditsBot/1.0 (+https://www.37audits.com/bot)";
    }

    /**
     * Web Bot Auth headers ({@code Signature-Agent}, {@code Signature-Input},
     * {@code Signature}) for a request to {@code url}, so bot-management
     * services can verify the crawler. Empty when no signing key is configured
     * (see {@link WebBotAuthSigner#ENV_JWK}), so callers can always add them
     * unconditionally next to {@link #getUserAgent()}.
     */
    public Map<String, String> getWebBotAuthHeaders(String url) {
        return WebBotAuthSigner.defaultHeaders(url);
    }

    public Auditor getAuditor() {
        return new Auditor(getId(), getName(), getVersion());
    }

    @Override
    public Response handleRequest(Request payload, Context context) {
        if (payload.url() == null) {
            logger.error("Unable to find the url");
            return new Response(getAuditor(), CheckStatus.FAIL, "Unable to find the url", null);
        }

        String url = payload.url();
        logger.info("Starting {} audit for {}", getName(), url);
        blockedBy = null;
        try {
            Response response = process(url, payload.basicAuth(), payload.preferences());
            if (blockedBy != null) {
                // The auditor's own catch-all turned the block into a generic error.
                return blocked(blockedBy);
            }
            if (response != null) {
                logger.info("Finished {} audit for {} with status {}", getName(), url, response.status());
            } else {
                logger.info("Finished {} audit for {}", getName(), url);
            }
            return response;
        } catch (SiteBlockedException e) {
            return blocked(e);
        } catch (RuntimeException e) {
            if (blockedBy != null) {
                return blocked(blockedBy);
            }
            logger.error("Failed {} audit for {}", getName(), url, e);
            return error(describe(e), null);
        }
    }

    /**
     * Stops the audit when the response of the audited page is a refusal (HTTP 403, HTTP 429 or a Cloudflare
     * challenge) rather than the page: throws {@link SiteBlockedException}, which {@link #handleRequest} answers
     * with the {@link #blocked(SiteBlockedException) blocked} response. Call it on the response of the audited URL
     * before reading it. The block is also remembered for the rest of the run, so the blocked response is returned
     * even when a catch-all in the auditor swallows the exception.
     *
     * @param cfMitigated
     *            value of the {@code cf-mitigated} response header, or {@code null} when absent.
     */
    protected void ensureNotBlocked(String url, int statusCode, String cfMitigated) {
        try {
            SiteBlockedException.throwIfBlocked(url, statusCode, cfMitigated);
        } catch (SiteBlockedException e) {
            blockedBy = e;
            throw e;
        }
    }

    /**
     * Same as {@link #ensureNotBlocked(String, int, String)} for the header map {@code HeadersCrawler} returns.
     */
    protected void ensureNotBlocked(String url, Map<String, List<String>> headers) {
        try {
            SiteBlockedException.throwIfBlocked(url, headers);
        } catch (SiteBlockedException e) {
            blockedBy = e;
            throw e;
        }
    }

    /**
     * The {@link CheckStatus#ERROR} response for a site that refused the auditor's request: one check, with code
     * {@code 37A-<AuditorClass>-590}, that says the bot was blocked and how to allow it.
     */
    protected Response blocked(SiteBlockedException e) {
        logger.warn("Blocked {} audit: {} answered HTTP {}", getName(), e.url(), e.statusCode());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("httpStatusCode", e.statusCode());
        data.put("challenge", e.challenge());

        CheckCode code = new CheckCode("37A-" + getClass().getSimpleName() + "-" + CODE_SITE_BLOCKED,
                "The site blocked the auditor's request.",
                "While 37AuditsBot is blocked this audit cannot look at the site, so problems on it go unreported.");

        return error(new Check(CheckStatus.ERROR, e.url(), e.getMessage(), BLOCKED_RECOMMENDATION, 0, data, code));
    }

    /**
     * One-line summary of an exception the auditor did not handle, for the response message. Multi-line messages
     * (Playwright's, for instance) are collapsed and cut at {@value #MAX_FAILURE_MESSAGE_LENGTH} characters; the
     * full stack trace is in the log.
     */
    private static String describe(RuntimeException e) {
        String summary = e.getClass().getSimpleName();

        if (e.getMessage() != null && !e.getMessage().isBlank()) {
            summary += ": " + e.getMessage().strip().replaceAll("\\s+", " ");
        }

        if (summary.length() > MAX_FAILURE_MESSAGE_LENGTH) {
            summary = summary.substring(0, MAX_FAILURE_MESSAGE_LENGTH) + "...";
        }

        return "The audit could not finish: " + summary;
    }

    public abstract Response process(String urlStr, BasicAuth basicAuth);

    /**
     * Preference-aware hook. Auditors with configurable settings override this
     * one and read their keys from {@code preferences} (never {@code null});
     * the default simply ignores preferences and delegates to
     * {@link #process(String, BasicAuth)}.
     */
    public Response process(String urlStr, BasicAuth basicAuth, Map<String, Object> preferences) {
        return process(urlStr, basicAuth);
    }

    public Response success(Check check) {
        return new Response(getAuditor(), CheckStatus.SUCCESS, check.message(), List.of(check));
    }

    public Response success(String message, List<Check> checks) {
        return new Response(getAuditor(), CheckStatus.SUCCESS, message, checks);
    }

    public Response failure(Check check) {
        return new Response(getAuditor(), CheckStatus.FAIL, check.message(), List.of(check));
    }

    public Response failure(String message, List<Check> checks) {
        return new Response(getAuditor(), CheckStatus.FAIL, message, checks);
    }

    public Response warning(Check check) {
        return new Response(getAuditor(), CheckStatus.WARNING, check.message(), List.of(check));
    }

    public Response warning(String message, List<Check> checks) {
        return new Response(getAuditor(), CheckStatus.WARNING, message, checks);
    }

    public Response error(Check check) {
        return new Response(getAuditor(), CheckStatus.ERROR, check.message(), List.of(check));
    }

    public Response error(String message, List<Check> checks) {
        return new Response(getAuditor(), CheckStatus.ERROR, message, checks);
    }

    /**
     * Determines overall status based on collected issues.
     */
    protected CheckStatus determineOverallStatus(List<Check> issues) {
        if (issues.isEmpty()) {
            return CheckStatus.SUCCESS;
        }

        boolean hasAllErrors = issues.stream().allMatch(issue -> issue.status() == CheckStatus.ERROR);
        boolean hasFailures = issues.stream().anyMatch(issue -> issue.status() == CheckStatus.FAIL);
        boolean hasWarnings = issues.stream().anyMatch(issue -> issue.status() == CheckStatus.WARNING);

        if (hasAllErrors) {
            return CheckStatus.ERROR;
        } else if (hasFailures) {
            return CheckStatus.FAIL;
        } else if (hasWarnings) {
            return CheckStatus.WARNING;
        } else {
            return CheckStatus.SUCCESS;
        }
    }

}
