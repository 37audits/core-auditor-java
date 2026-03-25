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

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * Base utilities for checker integration tests. Provides an embedded HTTP server and simple helpers to register routes.
 */
public abstract class AbstractLambdaAuditorTest {

    protected HttpServer httpServer;
    protected int port;

    @AfterEach
    void shutdownServer() {
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
        }
    }

    /**
     * Starts an embedded HTTP server on a random port and registers static responses for the provided paths. Returns
     * the base URL.
     */
    protected String startServer(Map<String, String> pathToBody) throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        for (Map.Entry<String, String> entry : pathToBody.entrySet()) {
            httpServer.createContext(entry.getKey(), new StaticHandler(entry.getValue()));
        }
        httpServer.setExecutor(null);
        httpServer.start();
        port = httpServer.getAddress().getPort();
        return "http://localhost:" + port + "/";
    }

    /** Returns the base URL for the current embedded server. */
    protected String baseUrl() {
        if (httpServer == null) {
            throw new IllegalStateException("Server not started. Call startServer(...) first.");
        }
        return "http://localhost:" + port + "/";
    }

    /** No-op helper for readability when embedding HTML in tests. */
    protected static String html(String content) {
        return content;
    }

    /** Simple static handler to serve fixed HTML bodies with text/html content type. */
    protected static final class StaticHandler implements HttpHandler {
        private final byte[] bytes;

        StaticHandler(String body) {
            var payload = body == null ? "" : body;
            this.bytes = payload.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
}
