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
package com.thirdysevenaudits.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Resolves the Maven project version for a class's artifact. Tries, in order:
 * <ol>
 * <li>{@code Implementation-Version} from the main section of {@code META-INF/MANIFEST.MF} for the JAR or directory
 * that contains the anchor class</li>
 * <li>{@link Package#getImplementationVersion()} for the anchor class's package</li>
 * <li>{@code META-INF/maven/.../pom.properties} ({@code version} key) on the classpath</li>
 * </ol>
 * Typical Maven JARs include both manifest entries (when configured) and {@code pom.properties}.
 */
public final class VersionUtil {

    private static final String UNKNOWN = "unknown";

    private VersionUtil() {
    }

    /**
     * Same as {@link #resolveVersion(Class, String, String)} using this module's Maven coordinates.
     */
    public static String resolveVersion(Class<?> anchor) {
        return resolveVersion(anchor, "com.37audits", "core-auditor-java");
    }

    /**
     * @param groupId
     *            Maven {@code groupId} (used to locate {@code pom.properties})
     * @param artifactId
     *            Maven {@code artifactId}
     *
     * @return the resolved version, or {@code "unknown"} if nothing could be read
     */
    public static String resolveVersion(Class<?> anchor, String groupId, String artifactId) {
        if (anchor == null) {
            return UNKNOWN;
        }

        var fromManifest = readMainImplementationVersion(anchor);
        if (isPresent(fromManifest)) {
            return fromManifest;
        }

        var pkg = anchor.getPackage();
        if (pkg != null) {
            var fromPackage = pkg.getImplementationVersion();
            if (isPresent(fromPackage)) {
                return fromPackage;
            }
        }

        var fromPomProps = readVersionFromPomProperties(anchor, groupId, artifactId);
        if (isPresent(fromPomProps)) {
            return fromPomProps;
        }

        return UNKNOWN;
    }

    private static boolean isPresent(String v) {
        return v != null && !v.isBlank();
    }

    private static String readMainImplementationVersion(Class<?> anchor) {
        var location = codeSourceLocation(anchor);
        if (location == null) {
            return null;
        }
        try {
            var path = Paths.get(location.toURI());
            if (Files.isDirectory(path)) {
                var manifestFile = path.resolve("META-INF").resolve("MANIFEST.MF");
                if (Files.isRegularFile(manifestFile)) {
                    try (var in = Files.newInputStream(manifestFile)) {
                        return implementationVersionFromManifestStream(in);
                    }
                }
                return null;
            }
            var pathStr = path.toString();
            if (pathStr.endsWith(".jar")) {
                try (var jar = new JarFile(path.toFile())) {
                    var manifest = jar.getManifest();
                    if (manifest != null) {
                        return manifest.getMainAttributes().getValue(Attributes.Name.IMPLEMENTATION_VERSION);
                    }
                }
            }
        } catch (URISyntaxException | IOException ignored) {
            // fall through
        }
        return null;
    }

    private static String implementationVersionFromManifestStream(InputStream in) throws IOException {
        var manifest = new Manifest(in);
        return manifest.getMainAttributes().getValue(Attributes.Name.IMPLEMENTATION_VERSION);
    }

    private static URL codeSourceLocation(Class<?> anchor) {
        if (anchor.getProtectionDomain() == null || anchor.getProtectionDomain().getCodeSource() == null) {
            return null;
        }
        return anchor.getProtectionDomain().getCodeSource().getLocation();
    }

    private static String readVersionFromPomProperties(Class<?> anchor, String groupId, String artifactId) {
        var cl = anchor.getClassLoader();
        if (cl == null) {
            cl = ClassLoader.getSystemClassLoader();
        }
        var paths = pomPropertiesResourcePaths(groupId, artifactId);
        for (String path : paths) {
            try (var in = cl.getResourceAsStream(path)) {
                if (in == null) {
                    continue;
                }
                var p = new Properties();
                p.load(in);
                var v = p.getProperty("version");
                if (isPresent(v)) {
                    return v.trim();
                }
            } catch (IOException ignored) {
                // try next path
            }
        }
        return null;
    }

    /**
     * Maven usually stores metadata under {@code META-INF/maven/<groupId path>/<artifactId>/}. The groupId segment is
     * sometimes the raw {@code groupId} string and sometimes dot-separated segments turned into subdirectories.
     */
    static String[] pomPropertiesResourcePaths(String groupId, String artifactId) {
        var slashGroup = groupId.replace('.', '/');
        return new String[] { "META-INF/maven/" + groupId + "/" + artifactId + "/pom.properties",
                "META-INF/maven/" + slashGroup + "/" + artifactId + "/pom.properties" };
    }

}
