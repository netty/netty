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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * Verifies that a resolved {@code jextract} executable is the version the build expects, by running
 * {@code jextract --version} and checking the configured version appears in its banner.
 *
 * <p>Because the generated bindings are committed to git, the jextract version is a reproducibility
 * input: a different version can emit subtly different sources, so a mismatch between the configured
 * version and the binary on {@code PATH} would silently produce a divergent regeneration diff. This
 * check turns that silent divergence into an up-front, actionable build failure.
 *
 * <p>The check matches jextract's own {@code jextract <version>} banner line specifically, not the
 * version as a token anywhere in the output: the banner also prints a
 * {@code JDK version X.Y.Z+build-NN} line whose build suffix would otherwise let, say, a
 * {@code jextract 21} satisfy a configured {@code 22}. Anchoring to the {@code jextract} line also
 * keeps a configured {@code 22} from matching {@code 225} or {@code 22.1}.
 */
final class JextractVersionChecker {

    private final File executable;
    private final String expectedVersion;

    JextractVersionChecker(final File executable, final String expectedVersion) {
        this.executable = executable;
        this.expectedVersion = expectedVersion;
    }

    /**
     * Runs {@code jextract --version} and fails unless {@link #expectedVersion} appears in the output.
     *
     * @param starter        starts the process for the {@code --version} argv; injectable so tests can
     *                       substitute a fake {@link Process}. Its output must be readable (the
     *                       production starter pipes it), so this must not be the generation starter
     *                       that inherits stdout.
     * @param timeoutSeconds per-invocation timeout; {@code <= 0} waits without a bound
     * @throws JextractException if jextract cannot be started, is interrupted, exits non-zero, times
     *                           out, or reports a version other than {@link #expectedVersion}
     */
    void verify(final ProcessStarter starter, final long timeoutSeconds)
            throws JextractException {
        final Process process = JextractProcessUtils.start(
                starter, Arrays.asList(executable.getAbsolutePath(), "--version"),
                "Failed to start jextract to check its version");

        // Wait first (honoring the timeout), then drain: the --version banner is a few lines, well
        // within the OS pipe buffer, so the process can exit without us reading concurrently. Draining
        // before the wait would instead block on the pipe with no timeout if the tool ever wedged.
        try {
            final int exitCode = JextractProcessUtils.await(process, timeoutSeconds, "jextract --version");
            final String output = readOutput(process);
            if (exitCode != 0) {
                throw JextractExceptions.buildFailure("jextract --version exited with code " + exitCode
                        + ", so its version could not be verified. Output was:\n" + output);
            }
            if (!outputContainsExpectedVersion(output)) {
                throw JextractExceptions.buildFailure("jextract version mismatch: expected " + expectedVersion
                        + " but " + executable + " --version reported:\n" + output
                        + "\nInstall jextract " + expectedVersion
                        + ", point <jextract>/JEXTRACT at it, or update <jextractVersion>.");
            }
        } finally {
            // await() throws before readOutput() on a timeout, leaving the captured pipe open; close it
            // so the version check never leaks a descriptor. On the normal path readOutput has already
            // closed it and close() is idempotent.
            closeQuietly(process.getInputStream());
        }
    }

    private boolean outputContainsExpectedVersion(final String output) {
        // Match jextract's dedicated "jextract <version>" banner line, not the version as a token
        // anywhere: the banner also prints a "JDK version X.Y.Z+build-NN" line whose build suffix
        // (e.g. -22) would otherwise let a jextract 21 satisfy a configured 22. MULTILINE binds ^/$
        // to that single line; \s*$ tolerates a trailing CR or spaces.
        final Pattern versionLine = Pattern.compile(
                "(?m)^jextract\\s+" + Pattern.quote(expectedVersion) + "\\s*$");
        return versionLine.matcher(output).find();
    }

    private static String readOutput(final Process process) throws JextractException {
        try (InputStream in = process.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (final IOException e) {
            throw JextractExceptions.executionError("Failed to read jextract --version output", e);
        }
    }

    private static void closeQuietly(final InputStream in) {
        try {
            in.close();
        } catch (final IOException ignored) {
            // The process is already gone or the stream already closed; nothing actionable.
        }
    }
}
