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

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JextractCommandTest {

    private static Binding binding(final String header, final String className,
                                   final List<String> functions, final List<String> structs,
                                   final List<String> constants) {
        final Binding binding = new Binding();
        binding.setHeader(header);
        binding.setClassName(className);
        binding.setFunctions(functions);
        binding.setStructs(structs);
        binding.setConstants(constants);
        return binding;
    }

    @Test
    void buildsExactArgumentVectorInOrder() {
        final File jextract = new File("/opt/jextract/bin/jextract");
        final File output = new File("/repo/module/src/main/java");

        final Binding binding = binding("socket.h", "BsdSocket",
                Arrays.asList("socket", "bind"),
                singletonList("sockaddr_in"),
                Arrays.asList("AF_INET", "SOCK_STREAM"));
        binding.setTypedefs(singletonList("socklen_t"));
        binding.setUnions(singletonList("sockaddr_storage_u"));
        binding.setVars(singletonList("errno"));

        final JextractCommand command = JextractCommand.builder()
                .executable(jextract)
                .outputDirectory(output)
                .targetPackage("io.netty.channel.unix.ffm.generated")
                .binding(binding)
                .build();

        assertEquals(Arrays.asList(
                jextract.getAbsolutePath(),
                "--output", output.getAbsolutePath(),
                "--target-package", "io.netty.channel.unix.ffm.generated",
                "--header-class-name", "BsdSocket",
                "--include-function", "socket",
                "--include-function", "bind",
                "--include-struct", "sockaddr_in",
                "--include-constant", "AF_INET",
                "--include-constant", "SOCK_STREAM",
                "--include-typedef", "socklen_t",
                "--include-union", "sockaddr_storage_u",
                "--include-var", "errno",
                "socket.h"),
                command.arguments());
    }

    @Test
    void omitsIncludeFlagsWhenNoSymbolsRequested() {
        final File jextract = new File("/opt/jextract/bin/jextract");
        final File output = new File("/out");

        final JextractCommand command = JextractCommand.builder()
                .executable(jextract)
                .outputDirectory(output)
                .targetPackage("pkg")
                .binding(binding("tcp.h", "Tcp", emptyList(), emptyList(),
                        singletonList("TCP_NODELAY")))
                .build();

        assertEquals(Arrays.asList(
                jextract.getAbsolutePath(),
                "--output", output.getAbsolutePath(),
                "--target-package", "pkg",
                "--header-class-name", "Tcp",
                "--include-constant", "TCP_NODELAY",
                "tcp.h"),
                command.arguments());
    }

    @Test
    void passesHeaderVerbatimWithoutResolvingAgainstADirectory() {
        // With headerDirectory removed, the header reaches jextract exactly as written, libclang
        // resolves it against the active SDK. A path-shaped header is passed through unchanged.
        final JextractCommand command = JextractCommand.builder()
                .executable(new File("/j"))
                .outputDirectory(new File("/o"))
                .targetPackage("p")
                .binding(binding("sys/socket.h", "BsdSocket", emptyList(), emptyList(), emptyList()))
                .build();

        final List<String> args = command.arguments();
        assertEquals("sys/socket.h", args.get(args.size() - 1));
    }

    @Test
    void argumentsAreImmutable() {
        final JextractCommand command = JextractCommand.builder()
                .executable(new File("/j"))
                .outputDirectory(new File("/o"))
                .targetPackage("p")
                .binding(binding("a.h", "A", emptyList(), emptyList(), emptyList()))
                .build();

        assertThrows(UnsupportedOperationException.class, () -> command.arguments().add("x"));
    }

    @Test
    void rejectsMissingRequiredInputs() {
        final JextractCommand.Builder builder = JextractCommand.builder()
                .executable(new File("/j"))
                .outputDirectory(new File("/o"))
                .binding(binding("a.h", "A", emptyList(), emptyList(), emptyList()));
        // targetPackage never set.
        assertThrows(IllegalStateException.class, builder::build);
    }

    @Test
    void rejectsBindingWithoutClassName() {
        final Binding incomplete = new Binding();
        incomplete.setHeader("a.h");
        final JextractCommand.Builder builder = JextractCommand.builder()
                .executable(new File("/j"))
                .outputDirectory(new File("/o"))
                .targetPackage("p")
                .binding(incomplete);
        assertThrows(IllegalStateException.class, builder::build);
    }

    @Test
    void rejectsBlankIncludeValue() {
        final JextractCommand.Builder builder = JextractCommand.builder()
                .executable(new File("/j"))
                .outputDirectory(new File("/o"))
                .targetPackage("p")
                .binding(binding("a.h", "A", singletonList("   "), emptyList(), emptyList()));
        final IllegalStateException e = assertThrows(IllegalStateException.class, builder::build);
        assertTrue(e.getMessage().contains("--include-function"), e.getMessage());
    }

    @Test
    void trimsIncludeValues() {
        // A padded symbol (e.g. "  socket  ") must reach jextract trimmed, never with its whitespace.
        final JextractCommand command = JextractCommand.builder()
                .executable(new File("/j"))
                .outputDirectory(new File("/o"))
                .targetPackage("p")
                .binding(binding("a.h", "A", singletonList("  socket  "), emptyList(), emptyList()))
                .build();

        final List<String> args = command.arguments();
        assertEquals("socket", args.get(args.indexOf("--include-function") + 1));
        assertFalse(args.contains("  socket  "), args.toString());
    }

    // --- run() execution paths (driven directly, no GenerateMojo) --------------------------------

    @Test
    void nonZeroExitThrowsBuildFailure() {
        final JextractException e = assertThrows(JextractException.class,
                () -> runnableCommand().run(command -> FakeProcess.withExitCode(3), 300L));
        assertTrue(e.isBuildFailure());
        assertTrue(e.getMessage().contains("exited with code 3"), e.getMessage());
    }

    @Test
    void startFailureThrowsExecutionError() {
        final JextractException e = assertThrows(JextractException.class,
                () -> runnableCommand().run(command -> {
                    throw new IOException("boom");
                }, 300L));
        assertFalse(e.isBuildFailure());
        assertTrue(e.getMessage().contains("Failed to start jextract"), e.getMessage());
        assertInstanceOf(IOException.class, e.getCause());
    }

    @Test
    void interruptTerminatesProcessAndRestoresInterrupt() {
        final boolean[] destroyed = {false};
        final ProcessStarter starter = command -> new FakeProcess() {
            @Override
            public boolean waitFor(final long timeout, final TimeUnit unit) throws InterruptedException {
                throw new InterruptedException();
            }

            @Override
            public Process destroyForcibly() {
                destroyed[0] = true;
                return this;
            }
        };

        try {
            final JextractException e = assertThrows(JextractException.class,
                    () -> runnableCommand().run(starter, 300L));
            assertFalse(e.isBuildFailure());
            assertTrue(e.getMessage().contains("Interrupted"), e.getMessage());
            assertTrue(destroyed[0], "the process must be forcibly destroyed on interrupt");
            assertTrue(Thread.currentThread().isInterrupted(), "the interrupt flag must be restored");
        } finally {
            // Clear the interrupt flag so it cannot leak into other tests.
            Thread.interrupted();
        }
    }

    @Test
    void unboundedWaitReturnsWhenProcessSucceeds() throws Exception {
        // timeoutSeconds <= 0 takes the no-arg waitFor() path; a clean exit must return normally.
        runnableCommand().run(command -> FakeProcess.withExitCode(0), 0L);
    }

    @Test
    void timeoutTerminatesProcessAndThrowsBuildFailure() {
        // Exercises the timeout *branch* only: the fake reports waitFor(...) == false immediately, so
        // it proves a timed-out process is destroyed and reported as a build failure, it does NOT
        // verify the real elapsed-time behavior (a genuine timing test would be slow/flaky).
        final boolean[] destroyed = {false};
        final ProcessStarter starter = command -> new FakeProcess() {
            @Override
            public boolean waitFor(final long timeout, final TimeUnit unit) {
                return false; // never exits within the timeout
            }

            @Override
            public Process destroyForcibly() {
                destroyed[0] = true;
                return this;
            }
        };

        final JextractException e = assertThrows(JextractException.class,
                () -> runnableCommand().run(starter, 1L));
        assertTrue(e.isBuildFailure());
        assertTrue(e.getMessage().contains("timed out"), e.getMessage());
        assertTrue(destroyed[0], "a timed-out process must be forcibly destroyed");
    }

    /** A fully-built command whose header ({@code socket.h}) shows up in run() failure messages. */
    private static JextractCommand runnableCommand() {
        return JextractCommand.builder()
                .executable(new File("/opt/jextract/bin/jextract"))
                .outputDirectory(new File("/out"))
                .targetPackage("pkg")
                .binding(binding("socket.h", "BsdSocket", emptyList(), emptyList(), emptyList()))
                .build();
    }
}
