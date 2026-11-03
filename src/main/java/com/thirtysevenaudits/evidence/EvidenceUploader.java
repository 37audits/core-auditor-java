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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stores an evidence file with a {@code PUT} to the presigned URL the platform sent with the request. The file goes
 * as {@code Content-Type: application/json} with {@code Content-Encoding: gzip}, the headers the URL was signed with.
 * This is a plain client on purpose: the URL points at the platform's storage, not at the audited site, so it gets
 * neither the bot's user agent nor a Web Bot Auth signature.
 */
public class EvidenceUploader {

    public static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final Logger LOGGER = LoggerFactory.getLogger(EvidenceUploader.class);

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    /** Whether the file was stored; a failure is logged, never thrown, so it cannot fail the audit. */
    public boolean upload(String uploadUrl, byte[] gzipJson) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(uploadUrl)).timeout(TIMEOUT)
                    .header("Content-Type", "application/json").header("Content-Encoding", "gzip")
                    .PUT(BodyPublishers.ofByteArray(gzipJson)).build();
            int status = client.send(request, BodyHandlers.discarding()).statusCode();
            if (status >= 200 && status < 300) {
                return true;
            }
            LOGGER.warn("Evidence upload answered HTTP {}", status);
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Evidence upload failed: {}", e.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Evidence upload interrupted");
        }
        return false;
    }
}
