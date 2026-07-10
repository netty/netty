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

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

/**
 * Generates {@code java.lang.foreign} (FFM) bindings from C headers by driving a pre-installed
 * {@code jextract} binary, one invocation per configured {@link Binding}.
 *
 * <p>Generation is on-demand: the produced sources are committed to git, so ordinary builds and CI
 * never run this goal (and never need jextract). Re-run it only when the requested symbols or the
 * system headers change.
 *
 * <p>The goal is deliberately not bound to a lifecycle phase; invoke it explicitly
 * ({@code mvn io.netty:netty5-jextract-maven-plugin:generate}). Declaring a {@code defaultPhase}
 * would let a bare {@code <execution>} silently bind it to a normal build, which is exactly the
 * jextract-at-build-time dependency the on-demand design forbids.
 */
@Mojo(name = "generate", threadSafe = true)
public final class GenerateMojo extends AbstractMojo {

    /**
     * Directory jextract writes generated sources into. Defaults to a {@code generated}
     * subdirectory of the module's main source root so the output is committed to git and
     * kept separate from hand-written sources.
     */
    @Parameter(defaultValue = "${project.basedir}/src/main/java/generated")
    private File outputDirectory;

    /**
     * The module's build directory, used only as the parent for the throwaway staging tree jextract
     * writes into before its output is promoted. Kept out of the source root so an interrupted run
     * cannot leave generated files that break compilation or trip checkstyle.
     */
    @Parameter(defaultValue = "${project.build.directory}", readonly = true, required = true)
    private File buildDirectory;

    /**
     * Java package for the generated bindings (jextract {@code --target-package}).
     */
    @Parameter(required = true)
    private String targetPackage;

    /**
     * The bindings to generate, one generated class per entry. Deliberately not {@code required}: an
     * absent or empty {@code <bindings>} is a no-op (warn and return), not a build failure.
     */
    @Nullable
    @Parameter
    private List<Binding> bindings;

    /**
     * Explicit path to the {@code jextract} executable. When unset, the {@code JEXTRACT}
     * environment variable and then the {@code PATH} are consulted.
     */
    @Nullable
    @Parameter(property = "jextract")
    private String jextract;

    /**
     * The jextract version the committed bindings must be generated with, the token jextract prints
     * in its {@code --version} banner (e.g. {@code 22}). Required: the goal runs {@code jextract
     * --version} first and fails unless the resolved binary reports this version, so a mismatched
     * jextract can never silently produce a divergent regeneration.
     */
    @Nullable
    @Parameter(property = "jextract.version", required = true)
    private String jextractVersion;

    /**
     * Optional identifier of the SDK/toolchain the committed bindings were generated against (e.g.
     * {@code MacOSX14.5}). Recorded verbatim in the generated {@code GENERATED.properties} provenance
     * manifest so a reviewer can see which ABI snapshot produced the bindings, a change shows up as a
     * reviewable diff. Not verified against the machine (that is a later, OS-specific extension).
     */
    @Nullable
    @Parameter(property = "jextract.sdk")
    private String sdk;

    /**
     * Per-invocation timeout in seconds. A jextract run that exceeds it is forcibly terminated and
     * the build fails, so a wedged tool can never hang the build indefinitely. Set to {@code 0} (or
     * a negative value) to wait without a bound.
     */
    @Parameter(property = "jextract.timeoutSeconds", defaultValue = "300")
    private long timeoutSeconds;

    /**
     * Starts the jextract process. Injected so unit tests can drive the exit-code, timeout and
     * interrupt handling without launching a real process; production inherits stdout so jextract's
     * progress shows in the build log (see {@link ProcessStarters#inheriting()}).
     */
    private ProcessStarter processStarter = ProcessStarters.inheriting();

    /**
     * Starts the {@code jextract --version} process for the pre-run version check. Distinct from
     * {@link #processStarter} because the version check must <em>read</em> jextract's output, whereas
     * generation inherits stdout (see {@link ProcessStarters#capturing()}). Injected so tests can
     * drive the version banner without a real process.
     */
    private ProcessStarter versionProcessStarter = ProcessStarters.capturing();

    // Test seam: swap in a fake starter. Maven never calls this. It uses the default.
    void setProcessStarter(final ProcessStarter processStarter) {
        this.processStarter = processStarter;
    }

    // Test seam: swap in a fake starter for the version check. Maven never calls this.
    void setVersionProcessStarter(final ProcessStarter versionProcessStarter) {
        this.versionProcessStarter = versionProcessStarter;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (bindings == null || bindings.isEmpty()) {
            getLog().warn("No <bindings> configured; nothing to generate.");
            return;
        }

        // Every internal step raises the framework-agnostic JextractException; this method owns the
        // sole translation into the Maven exception taxonomy, so the collaborators stay Maven-free and
        // the mapping lives in exactly one place (rethrowAsMojo).
        try {
            // Validate the whole configuration up front so a bad <binding> fails fast with an
            // actionable message rather than leaving a partially regenerated tree.
            BindingValidator.validateTargetPackage(targetPackage);
            // Use the trimmed value everywhere downstream: the command and the staging path both
            // consume targetPackage raw, and validation only checked the trimmed form.
            targetPackage = targetPackage.trim();
            BindingValidator.validate(bindings);

            final File executable = resolveJextract();
            verifyVersion(executable);
            ensureOutputDirectory();
            generate(executable);
        } catch (final JextractException e) {
            rethrowAsMojo(e);
        }
    }

    /**
     * Runs jextract once per binding. Assumes {@link BindingValidator#validate(java.util.List)} has
     * already passed, so a builder {@link IllegalStateException} here signals an internal assembly bug
     * rather than user input, and is surfaced as a build failure with the offending binding for context.
     */
    private void generate(final File executable) throws JextractException {
        getLog().info("Generating " + bindings.size() + " FFM binding(s) into package "
                + targetPackage + " using " + executable);

        // Generate into a staging directory and promote atomically once every binding succeeds. A
        // mid-run jextract failure therefore never leaves the committed source tree half-regenerated,
        // and promotion prunes the previously generated output for the target package so a removed or
        // renamed <binding> leaves no orphaned committed files.
        final StagingDirectory staging = StagingDirectory.create(buildDirectory, outputDirectory);
        try {
            for (final Binding binding : bindings) {
                final JextractCommand command = buildCommand(executable, staging.directory(), binding);
                getLog().info(binding.className() + " <- " + binding.header());
                getLog().debug(command.toString());
                command.run(processStarter, timeoutSeconds);
            }
            staging.promoteTo(targetPackage,
                    ProvenanceManifest.forCurrentOs(jextractVersion.trim(), sdk));
        } finally {
            staging.close();
        }
    }

    /**
     * Assembles the {@link JextractCommand} for one binding, mapping the builder's
     * {@link IllegalStateException} to an actionable {@link JextractException}. That mapping should be
     * unreachable after {@link BindingValidator#validate(java.util.List)}, but we wrap it regardless so
     * no configuration mistake ever surfaces as an internal stack trace.
     */
    private JextractCommand buildCommand(final File executable, final File output, final Binding binding)
            throws JextractException {
        try {
            return JextractCommand.builder()
                    .executable(executable)
                    .outputDirectory(output)
                    .targetPackage(targetPackage)
                    .binding(binding)
                    .build();
        } catch (final IllegalStateException e) {
            throw JextractExceptions.buildFailure("Invalid <binding> " + binding + ": " + e.getMessage(), e);
        }
    }

    /**
     * Runs {@code jextract --version} and fails the build unless the resolved binary reports the
     * required {@link #jextractVersion}.
     */
    private void verifyVersion(final File executable) throws JextractException {
        if (StringUtils.isBlank(jextractVersion)) {
            // required = true, so Maven rejects a missing value before execute(); guard the reflective
            // and programmatic paths so a blank never turns into an empty version token to match.
            throw JextractExceptions.buildFailure("<jextractVersion> is required; set it (or pass "
                    + "-Djextract.version=<version>, e.g. -Djextract.version=22) to the jextract version "
                    + "the committed bindings use.");
        }
        new JextractVersionChecker(executable, jextractVersion.trim())
                .verify(versionProcessStarter, timeoutSeconds);
        getLog().info("Verified jextract version " + jextractVersion);
    }

    private void ensureOutputDirectory() throws JextractException {
        if (!outputDirectory.isDirectory() && !outputDirectory.mkdirs()) {
            throw JextractExceptions.executionError("Could not create output directory: " + outputDirectory);
        }
    }

    private File resolveJextract() throws JextractException {
        return JextractLocator.builder()
                .explicitPath(jextract)
                .fromSystem()
                .build()
                .locate();
    }

    /**
     * The plugin's single translation of a framework-agnostic {@link JextractException} into the Maven
     * exception taxonomy: a {@link JextractException.Category#BUILD_FAILURE} becomes a
     * {@link MojoFailureException}, everything else a {@link MojoExecutionException}. Message and cause
     * are preserved either way.
     */
    private static void rethrowAsMojo(final JextractException e)
            throws MojoExecutionException, MojoFailureException {
        if (e.isBuildFailure()) {
            throw new MojoFailureException(e.getMessage(), e);
        }
        throw new MojoExecutionException(e.getMessage(), e);
    }
}
