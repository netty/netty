/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.util;

import io.netty.util.internal.PlatformDependent;

import java.io.InputStream;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

/**
 * Retrieves the version information of available Netty artifacts.
 * <p>
 * This class retrieves the version information from {@code META-INF/io.netty.versions.properties}, which is
 * generated in build time.  Note that it may not be possible to retrieve the information completely, depending on
 * your environment, such as the specified {@link ClassLoader}, the current {@link SecurityManager}.
 * </p>
 */
public final class Version {

    private static final String PROP_VERSION = ".version";
    private static final String PROP_BUILD_DATE = ".buildDate";
    private static final String PROP_COMMIT_DATE = ".commitDate";
    private static final String PROP_SHORT_COMMIT_HASH = ".shortCommitHash";
    private static final String PROP_LONG_COMMIT_HASH = ".longCommitHash";
    private static final String PROP_REPO_STATUS = ".repoStatus";

    /**
     * Retrieves the version information of Netty artifacts using the current
     * {@linkplain Thread#getContextClassLoader() context class loader}.
     *
     * @return A {@link Map} whose keys are Maven artifact IDs and whose values are {@link Version}s
     */
    public static Map<String, Version> identify() {
        return identify(null);
    }

    /**
     * Retrieves the version information of Netty artifacts using the specified {@link ClassLoader}.
     *
     * @return A {@link Map} whose keys are Maven artifact IDs and whose values are {@link Version}s
     */
    public static Map<String, Version> identify(ClassLoader classLoader) {
        if (classLoader == null) {
            classLoader = PlatformDependent.getContextClassLoader();
        }

        // Collect all properties.
        Properties props = new Properties();
        try {
            Enumeration<URL> resources = classLoader.getResources("META-INF/io.netty.versions.properties");
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                InputStream in = url.openStream();
                try {
                    props.load(in);
                } finally {
                    try {
                        in.close();
                    } catch (Exception ignore) {
                        // Ignore.
                    }
                }
            }
        } catch (Exception ignore) {
            // Not critical. Just ignore.
        }

        // Collect all artifactIds.
        Set<String> artifactIds = new HashSet<String>();
        for (Object o: props.keySet()) {
            String k = (String) o;

            int dotIndex = k.indexOf('.');
            if (dotIndex <= 0) {
                continue;
            }

            String artifactId = k.substring(0, dotIndex);

            // Skip the entries without required information.
            if (!props.containsKey(artifactId + PROP_VERSION) ||
                !props.containsKey(artifactId + PROP_BUILD_DATE) ||
                !props.containsKey(artifactId + PROP_COMMIT_DATE) ||
                !props.containsKey(artifactId + PROP_SHORT_COMMIT_HASH) ||
                !props.containsKey(artifactId + PROP_LONG_COMMIT_HASH) ||
                !props.containsKey(artifactId + PROP_REPO_STATUS)) {
                continue;
            }

            artifactIds.add(artifactId);
        }

        Map<String, Version> versions = new TreeMap<String, Version>();
        for (String artifactId: artifactIds) {
            versions.put(
                    artifactId,
                    new Version(
                            artifactId,
                            props.getProperty(artifactId + PROP_VERSION),
                            parseIso8601(props.getProperty(artifactId + PROP_BUILD_DATE)),
                            parseIso8601(props.getProperty(artifactId + PROP_COMMIT_DATE)),
                            props.getProperty(artifactId + PROP_SHORT_COMMIT_HASH),
                            props.getProperty(artifactId + PROP_LONG_COMMIT_HASH),
                            props.getProperty(artifactId + PROP_REPO_STATUS)));
        }

        return versions;
    }

    private static long parseIso8601(String value) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z").parse(value).getTime();
        } catch (ParseException ignored) {
            return 0;
        }
    }

    /**
     * Prints the version information to {@link System#err}.
     */
    public static void main(String[] args) {
        for (Version v: identify().values()) {
            System.err.println(v);
        }
    }

    private final String artifactId;
    private final String artifactVersion;
    private final long buildTimeMillis;
    private final long commitTimeMillis;
    private final String shortCommitHash;
    private final String longCommitHash;
    private final String repositoryStatus;

    private Version(
            String artifactId, String artifactVersion,
            long buildTimeMillis, long commitTimeMillis,
            String shortCommitHash, String longCommitHash, String repositoryStatus) {
        this.artifactId = artifactId;
        this.artifactVersion = artifactVersion;
        this.buildTimeMillis = buildTimeMillis;
        this.commitTimeMillis = commitTimeMillis;
        this.shortCommitHash = shortCommitHash;
        this.longCommitHash = longCommitHash;
        this.repositoryStatus = repositoryStatus;
    }

    public String artifactId() {
        return artifactId;
    }

    public String artifactVersion() {
        return artifactVersion;
    }

    public long buildTimeMillis() {
        return buildTimeMillis;
    }

    public long commitTimeMillis() {
        return commitTimeMillis;
    }

    public String shortCommitHash() {
        return shortCommitHash;
    }

    public String longCommitHash() {
        return longCommitHash;
    }

    public String repositoryStatus() {
        return repositoryStatus;
    }

    @Override
    public String toString() {
        return artifactId + '-' + artifactVersion + '.' + shortCommitHash +
               ("clean".equals(repositoryStatus)? "" : " (repository: " + repositoryStatus + ')');
    }
}
