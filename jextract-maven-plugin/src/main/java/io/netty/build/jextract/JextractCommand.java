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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An immutable, fully-resolved jextract invocation for a single {@link Binding} that knows how to
 * run itself.
 *
 * <p>The argument vector has the shape:
 * <pre>
 * &lt;jextract&gt; --output &lt;dir&gt; --target-package &lt;pkg&gt; --header-class-name &lt;class&gt;
 *            [--include-function f]... [--include-struct s]... [--include-constant c]...
 *            [--include-typedef t]... [--include-union u]... [--include-var v]...
 *            &lt;header&gt;
 * </pre>
 *
 * <p>Build it with {@link #builder()}, then {@link #run(ProcessStarter, long)} it. The
 * {@link #arguments()} are also exposed, so unit tests can assert the exact command line without
 * executing jextract, and {@link #run(ProcessStarter, long)} takes an injectable
 * {@link ProcessStarter} so they can exercise the exit-code/timeout/interrupt paths against a fake
 * {@link Process}.
 */
final class JextractCommand {

    private final List<String> arguments;
    // The header name, kept purely for diagnostics in run()'s failure messages.
    private final String header;

    private JextractCommand(final List<String> arguments, final String header) {
        this.arguments = Collections.unmodifiableList(new ArrayList<>(arguments));
        this.header = header;
    }

    /**
     * The full argument vector, including the executable as element 0. Suitable for
     * {@link ProcessBuilder#ProcessBuilder(List)}.
     */
    List<String> arguments() {
        return arguments;
    }

    /**
     * Runs jextract for this command: starts the process via {@code starter}, waits for it (honoring
     * {@code timeoutSeconds}), and fails on a non-zero exit, a timeout, or an interrupt. Framework-
     * agnostic, {@link GenerateMojo} translates the thrown {@link JextractException} into
     * the appropriate Maven exception.
     *
     * @param starter        starts the process for {@link #arguments()}; injectable so tests can
     *                       substitute a fake {@link Process}
     * @param timeoutSeconds per-invocation timeout; {@code <= 0} waits without a bound
     * @throws JextractException if jextract cannot be started, is interrupted, exits non-zero, or
     *                           exceeds the timeout
     */
    void run(final ProcessStarter starter, final long timeoutSeconds) throws JextractException {
        final Process process = JextractProcessUtils.start(
                starter, arguments, "Failed to start jextract for " + header);
        final int exitCode = JextractProcessUtils.await(process, timeoutSeconds, "jextract for " + header);
        if (exitCode != 0) {
            throw JextractExceptions.buildFailure(
                    "jextract exited with code " + exitCode + " for " + header);
        }
    }

    @Override
    public String toString() {
        return String.join(" ", arguments);
    }

    static Builder builder() {
        return new Builder();
    }

    static final class Builder {
        // Populated fluently; each is required and validated by require(...) at build() time, so all
        // are nullable until then.
        @Nullable
        private File executable;
        @Nullable
        private File outputDirectory;
        @Nullable
        private String targetPackage;
        @Nullable
        private Binding binding;

        private Builder() {
        }

        Builder executable(final File executable) {
            this.executable = executable;
            return this;
        }

        Builder outputDirectory(final File outputDirectory) {
            this.outputDirectory = outputDirectory;
            return this;
        }

        Builder targetPackage(final String targetPackage) {
            this.targetPackage = targetPackage;
            return this;
        }

        Builder binding(final Binding binding) {
            this.binding = binding;
            return this;
        }

        /**
         * Assembles the {@link JextractCommand}.
         *
         * @throws IllegalStateException if a required input or binding field is missing
         */
        JextractCommand build() {
            requireNonNull(executable, "executable");
            requireNonNull(outputDirectory, "outputDirectory");
            requireNonBlank(targetPackage, "targetPackage");
            requireNonNull(binding, "binding");
            requireNonBlank(binding.header(), "binding.header");
            requireNonBlank(binding.className(), "binding.className");

            final List<String> args = new ArrayList<>();
            args.add(executable.getAbsolutePath());
            args.add("--output");
            args.add(outputDirectory.getAbsolutePath());
            args.add("--target-package");
            args.add(targetPackage);
            args.add("--header-class-name");
            args.add(binding.className().trim());
            addIncludes(args, "--include-function", binding.functions());
            addIncludes(args, "--include-struct", binding.structs());
            addIncludes(args, "--include-constant", binding.constants());
            addIncludes(args, "--include-typedef", binding.typedefs());
            addIncludes(args, "--include-union", binding.unions());
            addIncludes(args, "--include-var", binding.vars());
            // The header is passed to jextract verbatim; libclang resolves it (system headers come
            // from the active SDK). There is no headerDirectory prefixing, see
            // docs/FFM-Build-Strategy.md §6.
            args.add(binding.header().trim());
            return new JextractCommand(args, binding.header());
        }

        private static void addIncludes(final List<String> command, final String flag,
                                        @Nullable final List<String> values) {
            if (values == null) {
                return;
            }
            for (final String value : values) {
                if (StringUtils.isBlank(value)) {
                    throw new IllegalStateException(
                            "Empty value for " + flag + "; remove the empty element or give it a symbol name.");
                }
                command.add(flag);
                // Trim so a padded symbol (e.g. " socket ") never reaches jextract as an argument.
                command.add(value.trim());
            }
        }

        private static void requireNonNull(@Nullable final Object value, final String name) {
            if (value == null) {
                throw new IllegalStateException("Missing required value: " + name);
            }
        }

        private static void requireNonBlank(@Nullable final String value, final String name) {
            if (StringUtils.isBlank(value)) {
                throw new IllegalStateException("Missing required value: " + name);
            }
        }
    }
}
