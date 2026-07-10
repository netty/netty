/*
 * Copyright 2026 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.build.jextract;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Locates the {@code jextract} executable. jextract is a developer prerequisite: it is never
 * downloaded by the build.
 *
 * <p>Configure the candidate sources through {@link #builder()} (all optional, a missing source
 * is simply skipped), then call {@link #locate()} to resolve. Sources are tried in descending
 * priority:
 *
 * <ol>
 *   <li>the explicit path (the {@code jextract} plugin parameter);</li>
 *   <li>the {@code JEXTRACT} environment variable;</li>
 *   <li>{@code jextract} on the {@code PATH}.</li>
 * </ol>
 *
 * <p>{@link Builder#fromSystem()} fills the environment-derived sources from the real process
 * environment; unit tests populate them explicitly and never touch {@link System}.
 */
final class JextractLocator {

    private final List<Source> sources;

    private JextractLocator(final Builder builder) {
        final String explicitPath = builder.explicitPath;
        final String jextractEnv = builder.jextractEnv;
        final String pathEnv = builder.pathEnv;
        final String pathSeparator = builder.pathSeparator;
        // Priority order: explicit parameter, then JEXTRACT env, then PATH.
        this.sources = Arrays.asList(
                () -> fromConfiguredPath(explicitPath,
                        "Configured jextract path is not an executable file: "),
                () -> fromConfiguredPath(jextractEnv,
                        "JEXTRACT environment variable does not point at an executable file: "),
                () -> searchPath(pathEnv, pathSeparator));
    }

    /**
     * One candidate way of finding jextract. Returns the executable when this source resolves,
     * {@code null} when the source is absent (fall through to the next), or throws when the source
     * is configured but does not point at an executable.
     */
    @FunctionalInterface
    private interface Source {
        File resolve() throws JextractException;
    }

    /**
     * Resolves the jextract executable by walking the configured sources in priority order and
     * returning the first existing executable.
     *
     * @return the resolved, executable file
     * @throws JextractException an {@link JextractException.Category#EXECUTION_ERROR} if no source
     *                           yields an executable jextract
     */
    File locate() throws JextractException {
        for (final Source source : sources) {
            final File resolved = source.resolve();
            if (resolved != null) {
                return resolved;
            }
        }

        throw JextractExceptions.executionError(
                "jextract not found. Install it (https://jdk.java.net/jextract/) and put it on the "
                        + "PATH, set the JEXTRACT environment variable, or configure the <jextract> "
                        + "plugin parameter.");
    }

    /**
     * Resolves a single configured path (the plugin parameter or {@code JEXTRACT}). Returns
     * {@code null} when unset, the executable when it resolves, or throws when the path is set but
     * does not point at an executable file.
     */
    private static File fromConfiguredPath(@Nullable final String value, final String invalidMessage)
            throws JextractException {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        final File candidate = new File(value);
        if (isExecutableFile(candidate)) {
            return candidate;
        }
        throw JextractExceptions.executionError(invalidMessage + value);
    }

    private static File searchPath(@Nullable final String pathEnv, @Nullable final String pathSeparator) {
        if (StringUtils.isBlank(pathEnv) || StringUtils.isBlank(pathSeparator)) {
            return null;
        }
        for (final String dir : pathEnv.split(Pattern.quote(pathSeparator))) {
            // Skip empty PATH entries. POSIX shells treat an empty entry as the current working
            // directory; we deliberately diverge to avoid ever exec-ing a "./jextract" picked up
            // from whatever directory the build happens to run in.
            if (dir.isEmpty()) {
                continue;
            }
            final File candidate = new File(dir, "jextract");
            if (isExecutableFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean isExecutableFile(final File file) {
        return file.isFile() && file.canExecute();
    }

    static Builder builder() {
        return new Builder();
    }

    static final class Builder {
        @Nullable
        private String explicitPath;
        @Nullable
        private String jextractEnv;
        @Nullable
        private String pathEnv;
        @Nullable
        private String pathSeparator;

        private Builder() {
        }

        /**
         * Sets the explicit path from the {@code jextract} plugin parameter. {@code null}/blank is
         * tolerated and means "not configured".
         */
        Builder explicitPath(@Nullable final String explicitPath) {
            this.explicitPath = explicitPath;
            return this;
        }

        Builder jextractEnv(@Nullable final String jextractEnv) {
            this.jextractEnv = jextractEnv;
            return this;
        }

        Builder pathEnv(@Nullable final String pathEnv) {
            this.pathEnv = pathEnv;
            return this;
        }

        Builder pathSeparator(@Nullable final String pathSeparator) {
            this.pathSeparator = pathSeparator;
            return this;
        }

        /**
         * Fills the environment-derived sources ({@code JEXTRACT}, {@code PATH} and the path
         * separator) from the real process environment. The explicit path is left untouched, set
         * it separately via {@link #explicitPath(String)}.
         */
        Builder fromSystem() {
            this.jextractEnv = System.getenv("JEXTRACT");
            this.pathEnv = System.getenv("PATH");
            this.pathSeparator = File.pathSeparator;
            return this;
        }

        JextractLocator build() {
            return new JextractLocator(this);
        }
    }
}
