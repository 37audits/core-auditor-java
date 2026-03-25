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
package com.thirdysevenaudits.auditor.aws;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.thirdysevenaudits.auditor.Auditor;
import com.thirdysevenaudits.auditor.BasicAuth;
import com.thirdysevenaudits.auditor.Check;
import com.thirdysevenaudits.auditor.CheckStatus;
import com.thirdysevenaudits.auditor.Request;
import com.thirdysevenaudits.auditor.Response;
import com.thirdysevenaudits.util.VersionUtil;

public abstract class AbstractLambdaAuditor implements RequestHandler<Request, Response> {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

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

    public Auditor getAuditor() {
        return new Auditor(getId(), getName(), getVersion());
    }

    @Override
    public Response handleRequest(Request payload, Context context) {
        if (payload.url() != null) {
            return process(payload.url(), payload.basicAuth());
        }

        return new Response(getAuditor(), CheckStatus.FAIL, "Unable to find the url", null);
    }

    public abstract Response process(String urlStr, BasicAuth basicAuth);

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
