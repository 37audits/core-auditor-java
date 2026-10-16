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

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.thirtysevenaudits.crawler.WebBotAuthSigner;
import com.thirtysevenaudits.crawler.WebBotAuthSignerTest;

class PlaywrightSupportTest {

    @Test
    void launchOptions_shouldRunHeadlessWithoutSandboxAndKeepArgs() {
        BrowserType.LaunchOptions options = PlaywrightSupport.launchOptions(List.of("--disable-gpu"));

        assertThat(options.headless).isTrue();
        assertThat(options.chromiumSandbox).isFalse();
        assertThat(options.args).containsExactly("--disable-gpu");
        assertThat(options.executablePath).isEqualTo(PlaywrightSupport.chromiumExecutable());
    }

    @Test
    @EnabledIf("com.thirtysevenaudits.http.PlaywrightSupport#chromiumAvailable")
    void chromiumAvailable_shouldBeStableAcrossCalls() {
        assertThat(PlaywrightSupport.chromiumAvailable()).isTrue();
    }

    // Calls BrowserContext.route through a real browser: core 1.8.0, compiled against Playwright 1.52,
    // failed with NoSuchMethodError on 1.63 (route now returns AutoCloseable) only when a signer was set.
    @Test
    @EnabledIf("com.thirtysevenaudits.http.PlaywrightSupport#chromiumAvailable")
    void signNavigations_shouldSignTheNavigationOnly() throws Exception {
        Map<String, String> signatures = new ConcurrentHashMap<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String signature = exchange.getRequestHeaders().getFirst("Signature");
            signatures.put(exchange.getRequestURI().getPath(), signature == null ? "" : signature);
            boolean script = exchange.getRequestURI().getPath().endsWith(".js");
            respond(exchange, script ? "" : "<html><head><script src=\"/app.js\"></script></head><body>ok</body></html>",
                    script ? "text/javascript" : "text/html");
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
        WebBotAuthSigner signer = WebBotAuthSigner.of(WebBotAuthSignerTest.newKey(), "https://www.37audits.com");

        try (Playwright playwright = Playwright.create();
                Browser browser = playwright.chromium().launch(PlaywrightSupport.launchOptions(List.of()));
                BrowserContext context = browser.newContext()) {
            PlaywrightSupport.signNavigations(context, signer);
            Page page = context.newPage();
            page.navigate(url);
        } finally {
            server.stop(0);
        }

        assertThat(signatures.get("/")).startsWith("sig1=:");
        assertThat(signatures.get("/app.js")).isEmpty();
    }

    private static void respond(HttpExchange exchange, String body, String contentType) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, bytes.length == 0 ? -1 : bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
