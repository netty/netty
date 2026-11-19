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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Minimal {@link Process} stand-in shared by the plugin's tests. Subclass it (anonymously) and
 * override only the behavior a test needs, or use {@link #succeeding()} / {@link #withExitCode(int)}
 * for the common "ran and exited" cases.
 */
abstract class FakeProcess extends Process {

    /** A process that exits 0. */
    static Process succeeding() {
        return withExitCode(0);
    }

    /** A process that exits with {@code exitCode}, for both the bounded and unbounded waits. */
    static Process withExitCode(final int exitCode) {
        return new FakeProcess() {
            @Override
            public int waitFor() {
                return exitCode;
            }

            @Override
            public boolean waitFor(final long timeout, final TimeUnit unit) {
                return true;
            }

            @Override
            public int exitValue() {
                return exitCode;
            }
        };
    }

    /** A process that exits 0 and emits {@code output} on its (combined) stdout, for --version. */
    static Process succeedingWithOutput(final String output) {
        return new FakeProcess() {
            @Override
            public InputStream getInputStream() {
                return new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8));
            }

            @Override
            public int waitFor() {
                return 0;
            }

            @Override
            public boolean waitFor(final long timeout, final TimeUnit unit) {
                return true;
            }

            @Override
            public int exitValue() {
                return 0;
            }
        };
    }

    @Override
    public OutputStream getOutputStream() {
        return OutputStream.nullOutputStream();
    }

    @Override
    public InputStream getInputStream() {
        return new ByteArrayInputStream(new byte[0]);
    }

    @Override
    public InputStream getErrorStream() {
        return new ByteArrayInputStream(new byte[0]);
    }

    @Override
    public int waitFor() throws InterruptedException {
        return 0;
    }

    @Override
    public int exitValue() {
        return 0;
    }

    @Override
    public void destroy() {
    }

    @Override
    public Process destroyForcibly() {
        return this;
    }
}
