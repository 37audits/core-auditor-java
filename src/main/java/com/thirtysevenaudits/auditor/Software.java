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
 *
 * <p>{@code ecosystem} is {@code null} for a platform the site runs on (for
 * example Liferay DXP, entered by hand). For a code dependency pushed from a
 * build it is the package ecosystem, the purl type ({@link #MAVEN},
 * {@link #NPM}), and the coordinates map to {@code vendor} (Maven groupId, npm
 * scope or {@code null}) and {@code product} (artifactId or package name).
 */
public record Software(String vendor, String product, String version, Instant updatedAt, String ecosystem) {

    public static final String MAVEN = "maven";
    public static final String NPM = "npm";

    /** Backward-compatible constructor for callers predating {@code updatedAt}. */
    public Software(String vendor, String product, String version) {
        this(vendor, product, version, null, null);
    }

    /** Backward-compatible constructor for callers predating {@code ecosystem}. */
    public Software(String vendor, String product, String version, Instant updatedAt) {
        this(vendor, product, version, updatedAt, null);
    }

    /** Whether this entry is a platform rather than a code dependency. */
    public boolean isPlatform() {
        return ecosystem == null || ecosystem.isBlank();
    }

    /** Whether this entry is a dependency of the given ecosystem, ignoring case. */
    public boolean isEcosystem(String name) {
        return !isPlatform() && ecosystem.equalsIgnoreCase(name);
    }

    /**
     * The package name as its ecosystem writes it: {@code groupId:artifactId}
     * for Maven, {@code @scope/name} or {@code name} for npm and
     * {@code namespace/name} for others. {@code null} for a platform entry.
     */
    public String packageName() {
        if (isPlatform()) {
            return null;
        }
        if (vendor == null || vendor.isBlank()) {
            return product;
        }
        return vendor + (isEcosystem(MAVEN) ? ":" : "/") + product;
    }

    @Override
    public String toString() {
        return isPlatform() ? vendor + " " + product + " " + version : packageName() + "@" + version;
    }

}
