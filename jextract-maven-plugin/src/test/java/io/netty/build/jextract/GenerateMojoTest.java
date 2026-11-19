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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenerateMojoTest {

    @TempDir
    File tmp;

    @Test
    void emptyBindingsWarnsAndReturnsWithoutRunningJextract() throws Exception {
        final GenerateMojo mojo = mojo(new ArrayList<>());
        final boolean[] started = {false};
        mojo.setProcessStarter(command -> {
            started[0] = true;
            return succeedingProcess();
        });

        mojo.execute();

        assertFalse(started[0], "jextract must not be started when there are no bindings");
    }

    @Test
    void nullBindingsWarnsAndReturns() throws Exception {
        final GenerateMojo mojo = mojo(null);
        mojo.setProcessStarter(command -> {
            throw new AssertionError("must not start jextract");
        });

        mojo.execute(); // no exception
    }

    @Test
    void bindingMissingHeaderFailsWithActionableMessage() throws Exception {
        final Binding incomplete = new Binding();
        incomplete.setClassName("BsdSocket");
        final GenerateMojo mojo = mojo(singletonList(incomplete));
        mojo.setProcessStarter(command -> {
            throw new AssertionError("must not start jextract");
        });

        final MojoFailureException e = assertThrows(MojoFailureException.class, mojo::execute);
        assertTrue(e.getMessage().contains("<header>"), e.getMessage());
    }

    @Test
    void bindingMissingClassNameFailsWithActionableMessage() throws Exception {
        final Binding incomplete = new Binding();
        incomplete.setHeader("socket.h");
        final GenerateMojo mojo = mojo(singletonList(incomplete));
        mojo.setProcessStarter(command -> {
            throw new AssertionError("must not start jextract");
        });

        final MojoFailureException e = assertThrows(MojoFailureException.class, mojo::execute);
        assertTrue(e.getMessage().contains("<className>"), e.getMessage());
    }

    @Test
    void duplicateClassNameFailsWithActionableMessage() throws Exception {
        final GenerateMojo mojo = mojo(Arrays.asList(
                binding("socket.h", "BsdSocket"),
                binding("socket2.h", "BsdSocket")));
        mojo.setProcessStarter(command -> {
            throw new AssertionError("must not start jextract");
        });

        final MojoFailureException e = assertThrows(MojoFailureException.class, mojo::execute);
        assertTrue(e.getMessage().contains("Duplicate <className>"), e.getMessage());
        assertTrue(e.getMessage().contains("BsdSocket"), e.getMessage());
    }

    @Test
    void sharedStructAcrossBindingsFailsWithActionableMessage() throws Exception {
        final Binding one = binding("a.h", "Aaa");
        one.setStructs(singletonList("sockaddr_in"));
        final Binding two = binding("b.h", "Bbb");
        two.setStructs(singletonList("sockaddr_in"));
        final GenerateMojo mojo = mojo(Arrays.asList(one, two));
        mojo.setProcessStarter(command -> {
            throw new AssertionError("must not start jextract");
        });

        final MojoFailureException e = assertThrows(MojoFailureException.class, mojo::execute);
        assertTrue(e.getMessage().contains("sockaddr_in"), e.getMessage());
        assertTrue(e.getMessage().contains("clobber"), e.getMessage());
    }

    @Test
    void sharedTypeSymbolAcrossDifferentKindsIsRejected() throws Exception {
        // jextract names the standalone type file by the bare symbol, so a <struct> in one binding
        // and a <typedef> of the same name in another still collide on disk.
        final Binding one = binding("a.h", "Aaa");
        one.setStructs(singletonList("shared_t"));
        final Binding two = binding("b.h", "Bbb");
        two.setTypedefs(singletonList("shared_t"));
        final GenerateMojo mojo = mojo(Arrays.asList(one, two));
        mojo.setProcessStarter(command -> {
            throw new AssertionError("must not start jextract");
        });

        final MojoFailureException e = assertThrows(MojoFailureException.class, mojo::execute);
        assertTrue(e.getMessage().contains("shared_t"), e.getMessage());
    }

    @Test
    void sameStructRepeatedWithinOneBindingIsAllowed() throws Exception {
        // A single binding requesting the same struct twice writes one file, no cross-binding
        // clobber, so it must not trip the shared-symbol guard.
        final Binding one = binding("a.h", "Aaa");
        one.setStructs(Arrays.asList("sockaddr_in", "sockaddr_in"));
        final GenerateMojo mojo = mojo(singletonList(one));
        mojo.setProcessStarter(GenerateMojoTest::generateStub);

        mojo.execute(); // no exception
    }

    @Test
    void assemblesArgumentsAndSucceedsOnZeroExit() throws Exception {
        final File executable = executable();
        final GenerateMojo mojo = mojo(singletonList(binding("socket.h", "BsdSocket")), executable);
        final AtomicReference<List<String>> seen = new AtomicReference<>();
        mojo.setProcessStarter(command -> {
            seen.set(command);
            return generateStub(command);
        });

        mojo.execute();

        final List<String> args = seen.get();
        assertNotNull(args);
        assertEquals(executable.getAbsolutePath(), args.get(0));
        assertTrue(args.contains("--header-class-name"));
        assertTrue(args.contains("BsdSocket"));
        assertEquals("socket.h", args.get(args.size() - 1));
    }

    @Test
    void nonZeroExitFailsBuild() throws Exception {
        final GenerateMojo mojo = mojo(singletonList(binding("socket.h", "BsdSocket")), executable());
        mojo.setProcessStarter(command -> fixedExitProcess(3));

        // Mojo-level concern: a build-failure JextractException maps to MojoFailureException.
        final MojoFailureException e = assertThrows(MojoFailureException.class, mojo::execute);
        assertTrue(e.getMessage().contains("exited with code 3"), e.getMessage());
    }

    @Test
    void startFailureIsWrapped() throws Exception {
        final GenerateMojo mojo = mojo(singletonList(binding("socket.h", "BsdSocket")), executable());
        mojo.setProcessStarter(command -> {
            throw new IOException("boom");
        });

        // Mojo-level concern: a non-build-failure JextractException maps to
        // MojoExecutionException. The execution mechanics themselves are tested in JextractCommandTest.
        final MojoExecutionException e = assertThrows(MojoExecutionException.class, mojo::execute);
        assertTrue(e.getMessage().contains("Failed to start jextract"), e.getMessage());
    }

    @Test
    void createsOutputDirectoryWhenMissing() throws Exception {
        final File output = new File(tmp, "out/nested");
        assertFalse(output.exists());
        final GenerateMojo mojo = mojo(singletonList(binding("socket.h", "BsdSocket")), executable());
        setField(mojo, "outputDirectory", output);
        mojo.setProcessStarter(GenerateMojoTest::generateStub);

        mojo.execute();

        assertTrue(output.isDirectory());
    }

    @Test
    void regenerationPrunesStaleGeneratedOutput() throws Exception {
        final File output = new File(tmp, "generated");
        final GenerateMojo mojo = mojo(singletonList(binding("socket.h", "BsdSocket")), executable());
        setField(mojo, "outputDirectory", output);
        // A stale class from a previous generation sits in the committed target package.
        final Path pkg = packageDir(output, "io.netty.channel.unix.ffm.generated");
        Files.createDirectories(pkg);
        final Path stale = pkg.resolve("Stale.java");
        Files.write(stale, "// stale".getBytes(StandardCharsets.UTF_8));
        mojo.setProcessStarter(GenerateMojoTest::generateStub);

        mojo.execute();

        assertFalse(Files.exists(stale), "a stale generated file must be pruned on regeneration");
        assertTrue(Files.exists(pkg.resolve("BsdSocket.java")), "the freshly generated file must be present");
    }

    @Test
    void bindingFailureLeavesCommittedOutputUntouched() throws Exception {
        final File output = new File(tmp, "generated");
        final GenerateMojo mojo = mojo(singletonList(binding("socket.h", "BsdSocket")), executable());
        setField(mojo, "outputDirectory", output);
        final Path pkg = packageDir(output, "io.netty.channel.unix.ffm.generated");
        Files.createDirectories(pkg);
        final Path committed = pkg.resolve("Existing.java");
        Files.write(committed, "// committed".getBytes(StandardCharsets.UTF_8));
        // jextract fails: nothing is promoted, so the committed tree is left exactly as it was.
        mojo.setProcessStarter(command -> fixedExitProcess(3));

        assertThrows(MojoFailureException.class, mojo::execute);

        assertTrue(Files.exists(committed), "a failed generation must not touch the committed output");
    }

    @Test
    void writesProvenanceManifestAlongsideGeneratedSources() throws Exception {
        final File output = new File(tmp, "generated");
        final GenerateMojo mojo = mojo(singletonList(binding("socket.h", "BsdSocket")), executable());
        setField(mojo, "outputDirectory", output);
        setField(mojo, "sdk", "MacOSX14.5");
        mojo.setProcessStarter(GenerateMojoTest::generateStub);

        mojo.execute();

        final Path manifest = packageDir(output, "io.netty.channel.unix.ffm.generated")
                .resolve("GENERATED.properties");
        assertTrue(Files.exists(manifest), "a provenance manifest must be written beside the sources");
        final String content = new String(Files.readAllBytes(manifest), StandardCharsets.UTF_8);
        assertTrue(content.contains("jextract.version=22"), content);
        assertTrue(content.contains("sdk=MacOSX14.5"), content);
        assertTrue(content.contains("os="), content);
    }

    @Test
    void versionMismatchFailsBuildAndSkipsGeneration() throws Exception {
        final GenerateMojo mojo = mojo(singletonList(binding("socket.h", "BsdSocket")), executable());
        mojo.setVersionProcessStarter(command -> FakeProcess.succeedingWithOutput("jextract 21"));
        mojo.setProcessStarter(command -> {
            throw new AssertionError("generation must not start when the version check fails");
        });

        final MojoFailureException e = assertThrows(MojoFailureException.class, mojo::execute);
        assertTrue(e.getMessage().contains("version mismatch"), e.getMessage());
        assertTrue(e.getMessage().contains("22"), e.getMessage());
    }

    @Test
    void versionCheckStartFailureIsExecutionError() throws Exception {
        final GenerateMojo mojo = mojo(singletonList(binding("socket.h", "BsdSocket")), executable());
        mojo.setVersionProcessStarter(command -> {
            throw new IOException("boom");
        });

        final MojoExecutionException e = assertThrows(MojoExecutionException.class, mojo::execute);
        assertTrue(e.getMessage().contains("check its version"), e.getMessage());
    }

    @Test
    void runsAllBindingsInDeclarationOrder() throws Exception {
        final GenerateMojo mojo = mojo(Arrays.asList(
                binding("socket.h", "BsdSocket"),
                binding("tcp.h", "Tcp")), executable());
        final List<List<String>> commands = new ArrayList<>();
        mojo.setProcessStarter(command -> {
            commands.add(command);
            return generateStub(command);
        });

        mojo.execute();

        assertEquals(2, commands.size());
        assertTrue(commands.get(0).contains("socket.h"), commands.get(0).toString());
        assertTrue(commands.get(1).contains("tcp.h"), commands.get(1).toString());
    }

    @Test
    void failureInABindingAbortsRemainingBindings() throws Exception {
        final GenerateMojo mojo = mojo(Arrays.asList(
                binding("socket.h", "BsdSocket"),
                binding("tcp.h", "Tcp")), executable());
        final List<List<String>> commands = new ArrayList<>();
        mojo.setProcessStarter(command -> {
            commands.add(command);
            return fixedExitProcess(3); // the first binding fails
        });

        final MojoFailureException e = assertThrows(MojoFailureException.class, mojo::execute);
        assertTrue(e.getMessage().contains("exited with code 3"), e.getMessage());
        assertEquals(1, commands.size(), "generation must stop after the first binding fails");
    }

    @Test
    void blankJextractVersionFailsBuildAndStartsNothing() throws Exception {
        final GenerateMojo mojo = mojo(singletonList(binding("socket.h", "BsdSocket")), executable());
        setField(mojo, "jextractVersion", "   ");
        mojo.setVersionProcessStarter(command -> {
            throw new AssertionError("the version check must not start when <jextractVersion> is blank");
        });
        mojo.setProcessStarter(command -> {
            throw new AssertionError("generation must not start when <jextractVersion> is blank");
        });

        final MojoFailureException e = assertThrows(MojoFailureException.class, mojo::execute);
        assertTrue(e.getMessage().contains("<jextractVersion> is required"), e.getMessage());
    }

    @Test
    void outputDirectoryCreationFailureIsExecutionError() throws Exception {
        // A regular file where the output directory's parent should be, so mkdirs() cannot succeed.
        final File blocker = new File(tmp, "blocker");
        assertTrue(blocker.createNewFile());
        final File output = new File(blocker, "generated");
        final GenerateMojo mojo = mojo(singletonList(binding("socket.h", "BsdSocket")), executable());
        setField(mojo, "outputDirectory", output);
        mojo.setProcessStarter(command -> {
            throw new AssertionError("generation must not start when the output directory cannot be created");
        });

        final MojoExecutionException e = assertThrows(MojoExecutionException.class, mojo::execute);
        assertTrue(e.getMessage().contains("Could not create output directory"), e.getMessage());
    }

    @Test
    void jextractNotFoundIsExecutionError() throws Exception {
        final GenerateMojo mojo = mojo(singletonList(binding("socket.h", "BsdSocket")), executable());
        // A configured-but-missing explicit path is checked first, so resolution fails deterministically
        // without consulting the real JEXTRACT/PATH environment.
        setField(mojo, "jextract", new File(tmp, "does-not-exist").getAbsolutePath());
        mojo.setVersionProcessStarter(command -> {
            throw new AssertionError("the version check must not start when jextract cannot be located");
        });
        mojo.setProcessStarter(command -> {
            throw new AssertionError("generation must not start when jextract cannot be located");
        });

        final MojoExecutionException e = assertThrows(MojoExecutionException.class, mojo::execute);
        assertTrue(e.getMessage().contains("Configured jextract path"), e.getMessage());
    }

    @Test
    void invalidTargetPackageFailsBuildBeforeGenerating() throws Exception {
        final GenerateMojo mojo = mojo(singletonList(binding("socket.h", "BsdSocket")), executable());
        setField(mojo, "targetPackage", "io.netty../tmp");
        mojo.setProcessStarter(command -> {
            throw new AssertionError("generation must not start for an invalid <targetPackage>");
        });

        final MojoFailureException e = assertThrows(MojoFailureException.class, mojo::execute);
        assertTrue(e.getMessage().contains("valid Java package name"), e.getMessage());
    }

    @Test
    void strayOutputOutsideTargetPackageFailsBuild() throws Exception {
        final GenerateMojo mojo = mojo(singletonList(binding("socket.h", "BsdSocket")), executable());
        mojo.setProcessStarter(command -> {
            writeStubOutput(command);  // the expected in-package output
            writeStrayOutput(command); // plus a file outside the target package
            return succeedingProcess();
        });

        final MojoExecutionException e = assertThrows(MojoExecutionException.class, mojo::execute);
        assertTrue(e.getMessage().contains("outside the target package"), e.getMessage());
    }

    @Test
    void leftoverStagingFromAnInterruptedRunIsClearedAndNotPromoted() throws Exception {
        final File output = new File(tmp, "generated");
        final File build = new File(tmp, "target");
        final GenerateMojo mojo = mojo(singletonList(binding("socket.h", "BsdSocket")), executable());
        setField(mojo, "outputDirectory", output);
        setField(mojo, "buildDirectory", build);
        // Simulate a prior interrupted run: junk left in the staging directory under the build dir.
        final Path leftover = build.toPath().resolve("jextract-staging").resolve("io").resolve("Junk.java");
        Files.createDirectories(leftover.getParent());
        Files.write(leftover, "// junk".getBytes(StandardCharsets.UTF_8));
        mojo.setProcessStarter(GenerateMojoTest::generateStub);

        mojo.execute();

        assertFalse(Files.exists(leftover), "stale staging must be cleared before regenerating");
        final Path pkg = packageDir(output, "io.netty.channel.unix.ffm.generated");
        assertTrue(Files.exists(pkg.resolve("BsdSocket.java")), "fresh output must be present");
        assertFalse(Files.exists(pkg.resolve("Junk.java")), "leftover must not be promoted into the output");
    }

    // --- helpers -----------------------------------------------------------------------------

    private Binding binding(final String header, final String className) {
        final Binding binding = new Binding();
        binding.setHeader(header);
        binding.setClassName(className);
        binding.setFunctions(singletonList("socket"));
        binding.setStructs(new ArrayList<>());
        binding.setConstants(new ArrayList<>());
        return binding;
    }

    private GenerateMojo mojo(final List<Binding> bindings) throws Exception {
        return mojo(bindings, executable());
    }

    private GenerateMojo mojo(final List<Binding> bindings, final File executable) throws Exception {
        final GenerateMojo mojo = new GenerateMojo();
        setField(mojo, "outputDirectory", new File(tmp, "generated"));
        setField(mojo, "buildDirectory", new File(tmp, "target"));
        setField(mojo, "targetPackage", "io.netty.channel.unix.ffm.generated");
        setField(mojo, "bindings", bindings);
        setField(mojo, "jextract", executable.getAbsolutePath());
        setField(mojo, "jextractVersion", "22");
        setField(mojo, "timeoutSeconds", 300L);
        // Version check is required and runs before generation; default it to a matching banner so the
        // existing generation-focused tests sail through the gate. Version-specific tests override it.
        mojo.setVersionProcessStarter(command -> FakeProcess.succeedingWithOutput("jextract 22\nJDK version 22"));
        return mojo;
    }

    /** A real, executable file so {@link JextractLocator} resolves without touching the PATH. */
    private File executable() throws IOException {
        final File exe = new File(tmp, "jextract");
        if (!exe.exists()) {
            assertTrue(exe.createNewFile());
            assertTrue(exe.setExecutable(true));
        }
        return exe;
    }

    private static void setField(final Object target, final String name, final Object value)
            throws Exception {
        final Field field = GenerateMojo.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Process succeedingProcess() {
        return FakeProcess.succeeding();
    }

    private static Process fixedExitProcess(final int exitCode) {
        return FakeProcess.withExitCode(exitCode);
    }

    /** A fake jextract that writes a stub source file for the binding, so promotion has real output. */
    private static Process generateStub(final List<String> command) {
        writeStubOutput(command);
        return FakeProcess.succeeding();
    }

    private static void writeStubOutput(final List<String> command) {
        final String output = valueAfter(command, "--output");
        final String targetPackage = valueAfter(command, "--target-package");
        final String className = valueAfter(command, "--header-class-name");
        Path dir = Paths.get(output);
        for (final String part : targetPackage.split("\\.")) {
            dir = dir.resolve(part);
        }
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve(className + ".java"), "// generated".getBytes(StandardCharsets.UTF_8));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void writeStrayOutput(final List<String> command) {
        final String output = valueAfter(command, "--output");
        try {
            final Path stray = Paths.get(output, "stray");
            Files.createDirectories(stray);
            Files.write(stray.resolve("Stray.java"), "// stray".getBytes(StandardCharsets.UTF_8));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String valueAfter(final List<String> command, final String flag) {
        return command.get(command.indexOf(flag) + 1);
    }

    private static Path packageDir(final File outputRoot, final String targetPackage) {
        Path dir = outputRoot.toPath();
        for (final String part : targetPackage.split("\\.")) {
            dir = dir.resolve(part);
        }
        return dir;
    }
}
