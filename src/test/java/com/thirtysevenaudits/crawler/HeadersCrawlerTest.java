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
package com.thirtysevenaudits.crawler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

class HeadersCrawlerTest {

    private HttpServer httpServer;
    private String baseUrl;
    private final List<String> receivedMethods = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        receivedMethods.clear();
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/ok", exchange -> {
            receivedMethods.add(exchange.getRequestMethod());
            respond(exchange, 200, "ok");
        });
        httpServer.createContext("/head-not-allowed", exchange -> {
            receivedMethods.add(exchange.getRequestMethod());
            if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
                respond(exchange, 405, "");
            } else {
                respond(exchange, 200, "ok");
            }
        });
        httpServer.setExecutor(null);
        httpServer.start();
        baseUrl = "http://localhost:" + httpServer.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

    @Test
    void fetch_usesHeadByDefault() throws Exception {
        var crawler = new HeadersCrawler("test-agent", null);

        Map<String, List<String>> headers = crawler.fetch(baseUrl + "/ok");

        assertEquals(List.of("HEAD"), new ArrayList<>(receivedMethods));
        assertEquals(List.of("200"), headers.get("http-status-code"));
    }

    @Test
    void fetch_fallsBackToGetWhenHeadNotAllowed() throws Exception {
        var crawler = new HeadersCrawler("test-agent", null);

        Map<String, List<String>> headers = crawler.fetch(baseUrl + "/head-not-allowed");

        assertEquals(List.of("HEAD", "GET"), new ArrayList<>(receivedMethods));
        assertEquals(List.of("200"), headers.get("http-status-code"));
    }

    @Test
    void doGet_usesGet() throws Exception {
        var crawler = new HeadersCrawler("test-agent", null);

        Map<String, List<String>> headers = crawler.doGet(baseUrl + "/ok");

        assertEquals(List.of("GET"), new ArrayList<>(receivedMethods));
        assertEquals(List.of("200"), headers.get("http-status-code"));
    }

    @Test
    void doGet_withBasicAuthFlag_usesGet() throws Exception {
        var crawler = new HeadersCrawler("test-agent", null);

        Map<String, List<String>> headers = crawler.doGet(baseUrl + "/ok", true);

        assertEquals(List.of("GET"), new ArrayList<>(receivedMethods));
        assertEquals(List.of("200"), headers.get("http-status-code"));
    }

    @Test
    void doHead_usesHead() throws Exception {
        var crawler = new HeadersCrawler("test-agent", null);

        Map<String, List<String>> headers = crawler.doHead(baseUrl + "/ok");

        assertEquals(List.of("HEAD"), new ArrayList<>(receivedMethods));
        assertEquals(List.of("200"), headers.get("http-status-code"));
    }

    @Test
    void doHead_withBasicAuthFlag_usesHead() throws Exception {
        var crawler = new HeadersCrawler("test-agent", null);

        Map<String, List<String>> headers = crawler.doHead(baseUrl + "/ok", true);

        assertEquals(List.of("HEAD"), new ArrayList<>(receivedMethods));
        assertEquals(List.of("200"), headers.get("http-status-code"));
    }

    @Test
    void doHead_doesNotFallBackToGetOn405() throws Exception {
        var crawler = new HeadersCrawler("test-agent", null);

        Map<String, List<String>> headers = crawler.doHead(baseUrl + "/head-not-allowed");

        assertEquals(List.of("HEAD"), new ArrayList<>(receivedMethods));
        assertEquals(List.of("405"), headers.get("http-status-code"));
    }

    private static void respond(HttpExchange exchange, int statusCode, String body) throws IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, "HEAD".equalsIgnoreCase(exchange.getRequestMethod()) ? -1 : bytes.length);
        if (!"HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        } else {
            exchange.close();
        }
    }
}
