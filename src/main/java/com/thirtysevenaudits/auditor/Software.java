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

import java.time.Instant;

/**
 * One entry of the project's software stack.
 *
 * <p>{@code updatedAt} is when the entry was last entered, edited or confirmed
 * on the platform. It is {@code null} when the caller does not send it.
 */
public record Software(String vendor, String product, String version, Instant updatedAt) {

    /** Backward-compatible constructor for callers predating {@code updatedAt}. */
    public Software(String vendor, String product, String version) {
        this(vendor, product, version, null);
    }

    @Override
    public String toString() {
        return vendor + " " + product + " " + version;
    }

}
