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
package com.thirtysevenaudits.http;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.options.HttpCredentials;
import com.thirtysevenaudits.auditor.BasicAuth;
import com.thirtysevenaudits.crawler.WebBotAuthSigner;

/**
 * The Playwright side of {@link HttpFetcher}'s policy, for auditors that render the page in Chromium. Needs
 * {@code com.microsoft.playwright:playwright} on the auditor's classpath.
 */
public final class PlaywrightSupport {

    private PlaywrightSupport() {
    }

    /**
     * Context options with the bot's user agent, certificate errors ignored, and {@code basicAuth} (when set)
     * answered only for the audited URL's origin.
     */
    public static Browser.NewContextOptions newContextOptions(String userAgent, String auditedUrl,
            BasicAuth basicAuth) {
        Browser.NewContextOptions options = new Browser.NewContextOptions().setIgnoreHTTPSErrors(true)
                .setUserAgent(userAgent);
        if (basicAuth != null) {
            HttpCredentials credentials = new HttpCredentials(basicAuth.username(), basicAuth.password());
            String origin = origin(auditedUrl);
            if (origin != null) {
                credentials.setOrigin(origin);
            }
            options.setHttpCredentials(credentials);
        }
        return options;
    }

    /** {@link #signNavigations(BrowserContext, WebBotAuthSigner)} with {@link WebBotAuthSigner#defaultSigner()}. */
    public static void signNavigations(BrowserContext context) {
        signNavigations(context, WebBotAuthSigner.defaultSigner().orElse(null));
    }

    /**
     * Adds a Web Bot Auth signature to every navigation of {@code context} (the page and its frames). Other requests
     * (scripts, fetches) are left alone: signature headers on a cross-origin fetch trigger a CORS preflight that
     * many servers reject. Does nothing when {@code signer} is {@code null}.
     */
    public static void signNavigations(BrowserContext context, WebBotAuthSigner signer) {
        if (signer == null) {
            return;
        }
        context.route("**/*", route -> {
            Request request = route.request();
            if (request.isNavigationRequest()) {
                Map<String, String> headers = new HashMap<>(request.headers());
                headers.putAll(signer.headers(request.url()));
                route.resume(new Route.ResumeOptions().setHeaders(headers));
            } else {
                route.resume();
            }
        });
    }

    private static String origin(String url) {
        if (url == null) {
            return null;
        }
        try {
            URI uri = URI.create(url);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return null;
            }
            return uri.getScheme() + "://" + uri.getHost() + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
