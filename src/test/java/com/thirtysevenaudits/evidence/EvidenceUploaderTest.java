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
package com.thirtysevenaudits.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;

class EvidenceUploaderTest {

    private HttpServer httpServer;
    private String base;
    private final AtomicReference<String> method = new AtomicReference<>();
    private final AtomicReference<Headers> headers = new AtomicReference<>();
    private final AtomicReference<byte[]> body = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/ok", exchange -> {
            method.set(exchange.getRequestMethod());
            headers.set(exchange.getRequestHeaders());
            body.set(exchange.getRequestBody().readAllBytes());
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        httpServer.createContext("/denied", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(403, -1);
            exchange.close();
        });
        httpServer.start();
        base = "http://localhost:" + httpServer.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        httpServer.stop(0);
    }

    @Test
    void putsTheFileWithTheSignedHeaders() {
        boolean uploaded = new EvidenceUploader().upload(base + "/ok?X-Amz-Signature=abc", new byte[] { 1, 2, 3 });

        assertThat(uploaded).isTrue();
        assertThat(method.get()).isEqualTo("PUT");
        assertThat(headers.get().getFirst("Content-Type")).isEqualTo("application/json");
        assertThat(headers.get().getFirst("Content-Encoding")).isEqualTo("gzip");
        assertThat(body.get()).containsExactly(1, 2, 3);
    }

    @Test
    void reportsARefusedUploadWithoutThrowing() {
        assertThat(new EvidenceUploader().upload(base + "/denied", new byte[] { 1 })).isFalse();
    }

    @Test
    void reportsAnUnreachableOrMalformedUrlWithoutThrowing() {
        httpServer.stop(0);

        assertThat(new EvidenceUploader().upload(base + "/ok", new byte[] { 1 })).isFalse();
        assertThat(new EvidenceUploader().upload("not a url", new byte[] { 1 })).isFalse();
    }
}
