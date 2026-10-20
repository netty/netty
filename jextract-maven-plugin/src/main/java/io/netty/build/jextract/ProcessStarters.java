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

/**
 * The production {@link ProcessStarter} implementations, kept out of {@link GenerateMojo} so the
 * process-launch policy lives in one place rather than as field initializers on the mojo.
 *
 * <p>The generation and version-check runs need different stream handling, see
 * {@link #inheriting()} and {@link #capturing()}, and getting that redirect configuration subtly
 * wrong is the kind of mistake this single home is meant to prevent.
 */
final class ProcessStarters {

    private ProcessStarters() {
    }

    /**
     * A starter for a generation run: jextract's combined output is inherited by this process so its
     * progress shows directly in the build log. Its output is therefore <em>not</em> readable, so this
     * must not be used for the version check.
     */
    static ProcessStarter inheriting() {
        return command -> new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .start();
    }

    /**
     * A starter for the {@code --version} check: jextract's combined output is piped so the caller can
     * read the banner back and match the expected version against it.
     */
    static ProcessStarter capturing() {
        return command -> new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
    }
}
