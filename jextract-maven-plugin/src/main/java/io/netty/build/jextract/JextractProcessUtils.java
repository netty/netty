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

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Shared process launch-and-wait logic for the jextract invocations the plugin drives (the generation
 * run in {@link JextractCommand} and the version check in {@link JextractVersionChecker}). Kept in one
 * place so both start the process, wrap a launch failure, honor {@code timeoutSeconds}, forcibly
 * terminate a wedged process, and restore the interrupt flag in exactly the same way.
 */
final class JextractProcessUtils {

    private JextractProcessUtils() {
    }

    /**
     * Starts {@code command} through {@code starter}, wrapping a launch {@link IOException} as an
     * {@link JextractException.Category#EXECUTION_ERROR}, jextract never ran, so this is an
     * environment problem rather than a build failure.
     *
     * @param starter        starts the process; injectable so tests can substitute a fake {@link Process}
     * @param command        the full argument vector (executable as element 0)
     * @param failureMessage message for the exception thrown when the process cannot be started
     * @return the started process
     * @throws JextractException if the process cannot be started
     */
    static Process start(final ProcessStarter starter, final List<String> command,
                         final String failureMessage) throws JextractException {
        try {
            return starter.start(command);
        } catch (final IOException e) {
            throw JextractExceptions.executionError(failureMessage, e);
        }
    }

    /**
     * Waits for {@code process} to exit and returns its exit code. A run that exceeds
     * {@code timeoutSeconds} (when positive) is forcibly terminated and reported as a build failure;
     * {@code timeoutSeconds <= 0} waits without a bound. An interrupt forcibly terminates the process,
     * restores the thread's interrupt flag, and is reported as an execution error.
     *
     * @param process         the running jextract process
     * @param timeoutSeconds  per-invocation timeout; {@code <= 0} waits without a bound
     * @param invocationLabel short description of the invocation for failure messages
     *                        (e.g. {@code "jextract for socket.h"})
     * @return the process exit code (callers decide whether a non-zero value is a failure)
     * @throws JextractException if the timeout elapses or the wait is interrupted
     */
    static int await(final Process process, final long timeoutSeconds, final String invocationLabel)
            throws JextractException {
        try {
            if (timeoutSeconds <= 0) {
                return process.waitFor();
            }
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                // A build failure (not an execution error): the timeout is a user-configured limit and
                // the remedy, raise timeoutSeconds or fix a wedged header, is the user's.
                throw JextractExceptions.buildFailure(
                        invocationLabel + " timed out after " + timeoutSeconds + "s");
            }
            return process.exitValue();
        } catch (final InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw JextractExceptions.executionError("Interrupted while running " + invocationLabel, e);
        }
    }
}
