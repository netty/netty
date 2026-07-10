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
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A throwaway directory that jextract writes into, which then promotes its generated target-package
 * subtree into the committed source root as a single all-or-nothing step.
 *
 * <p>Generating into staging first means a mid-run jextract failure never leaves the committed tree
 * half-regenerated: the caller runs every binding into {@link #directory()} and only calls
 * {@link #promoteTo(String, ProvenanceManifest)} once they all succeed. Staging lives under the
 * module's build directory (git-ignored, not a source root), which shares a filesystem with the
 * output root, so the promotion is a same-filesystem atomic rename rather than a cross-device copy,
 * and an interrupted run cannot leave a compile-breaking or checkstyle-tripping tree in the sources.
 * The swap keeps the previous tree in a backup until the new one is in place, so a mid-swap failure
 * rolls back instead of losing the committed sources.
 *
 * <p>{@link AutoCloseable} so the caller removes the staging directory in a {@code finally} regardless
 * of outcome.
 */
final class StagingDirectory implements AutoCloseable {

    private final Path root;
    private final Path outputRoot;
    private boolean backupPreserved;

    /** The atomic rename, injectable so tests can drive the swap and rollback failure paths. */
    private Mover mover = (from, to) -> Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);

    private StagingDirectory(final Path root, final Path outputRoot) {
        this.root = root;
        this.outputRoot = outputRoot;
    }

    // Test seam: replace the atomic rename. Maven never calls this; production uses the default.
    void setMover(final Mover mover) {
        this.mover = mover;
    }

    /** A single move; the production implementation is a {@link StandardCopyOption#ATOMIC_MOVE} rename. */
    @FunctionalInterface
    interface Mover {
        void move(Path from, Path to) throws IOException;
    }

    /**
     * Creates a fresh, empty staging directory under {@code buildDirectory} (typically {@code target/}).
     * That directory is git-ignored and is not a compiled source root, so a staging tree left behind by
     * an interrupted run cannot break compilation or checkstyle; it shares a filesystem with
     * {@code outputDirectory} (both under the module basedir), so the promote stays an atomic rename. A
     * fixed name, cleared up front, keeps interrupted runs from accumulating stale directories.
     */
    static StagingDirectory create(final File buildDirectory, final File outputDirectory)
            throws JextractException {
        final Path staging = buildDirectory.toPath().resolve("jextract-staging");
        try {
            deleteRecursively(staging);
            Files.createDirectories(staging);
            return new StagingDirectory(staging, outputDirectory.toPath());
        } catch (final IOException e) {
            throw JextractExceptions.executionError(
                    "Could not create a staging directory for jextract output under " + buildDirectory, e);
        }
    }

    /** The directory to pass to jextract as {@code --output}. */
    File directory() {
        return root.toFile();
    }

    /**
     * Records {@code manifest} alongside the freshly generated sources and swaps the target-package
     * subtree into the committed output root, replacing (pruning) any previous generation for that
     * package. Assumes every binding has already generated into this staging directory.
     *
     * @throws JextractException if jextract produced no output for the package, the manifest cannot be
     *                           written, or the swap into the committed tree fails
     */
    void promoteTo(final String targetPackage, final ProvenanceManifest manifest) throws JextractException {
        final Path generated = resolvePackage(root, targetPackage);
        if (!Files.isDirectory(generated)) {
            throw JextractExceptions.executionError(
                    "jextract produced no output for package " + targetPackage + " (expected " + generated + ")");
        }
        final Path destination = resolvePackage(outputRoot, targetPackage);
        try {
            final Optional<Path> stray = firstFileOutside(generated);
            if (stray.isPresent()) {
                throw JextractExceptions.executionError("jextract wrote output outside the target package "
                        + targetPackage + " (" + stray.get() + "); the package-scoped promotion would drop "
                        + "it, so the run is failed instead.");
            }
            manifest.writeInto(generated.toFile());
            Files.createDirectories(destination.getParent());
            swapIntoPlace(generated, destination);
        } catch (final AtomicMoveNotSupportedException e) {
            throw JextractExceptions.executionError("Cannot promote generated sources into " + destination
                    + ": <outputDirectory> must be on the same filesystem as the build directory ("
                    + root.getParent() + "). Use the default outputDirectory, or point it at a path under "
                    + "the module.", e);
        } catch (final IOException e) {
            throw JextractExceptions.executionError(
                    "Could not promote generated sources into " + destination, e);
        }
    }

    /**
     * Returns the first regular file in staging that is not under {@code generated}, if any. jextract
     * with {@code --target-package} writes only into that subtree, so a file elsewhere would be lost by
     * the package-scoped promotion; the caller fails the run rather than drop it silently.
     */
    private Optional<Path> firstFileOutside(final Path generated) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile)
                    .filter(file -> !file.startsWith(generated))
                    .findFirst();
        }
    }

    /** Best-effort removal of the staging directory and any promotion backup; never fatal. */
    @Override
    public void close() {
        deleteQuietly(root);
        // Leave the backup if a failed rollback made it the only copy of the previous committed tree.
        if (!backupPreserved) {
            deleteQuietly(root.getParent().resolve("jextract-promote-backup"));
        }
    }

    private static Path resolvePackage(final Path base, final String targetPackage) {
        Path path = base;
        for (final String part : targetPackage.split("\\.")) {
            path = path.resolve(part);
        }
        return path;
    }

    /**
     * Replaces {@code destination} with {@code generated}, keeping the previous tree in a backup under
     * the build directory until the new one is in place so a failure mid-swap can roll back. The moves
     * are atomic renames, which requires {@code destination} (under the output root) to share a
     * filesystem with the build directory; a cross-filesystem {@code outputDirectory} is reported by
     * {@link #promoteTo} with an actionable message. The backup lives outside the source root, so the
     * brief window in which the committed package is absent never leaves a compile-breaking tree.
     */
    private void swapIntoPlace(final Path generated, final Path destination)
            throws IOException, JextractException {
        if (!Files.exists(destination)) {
            mover.move(generated, destination);
            return;
        }
        final Path backup = root.getParent().resolve("jextract-promote-backup");
        deleteRecursively(backup); // clear any leftover from a previously interrupted swap
        mover.move(destination, backup);
        try {
            mover.move(generated, destination);
        } catch (final IOException swapFailed) {
            rollBack(backup, destination, swapFailed);
        }
        // The promotion has succeeded once the new tree is in place; removing the backup is best-effort
        // so a cleanup failure never reports a completed generation as failed.
        deleteQuietly(backup);
    }

    /**
     * Restores {@code destination} from {@code backup} after a failed forward move, then rethrows the
     * original {@code swapFailed} so the report names the real cause. If the rollback itself fails the
     * backup holds the only copy of the previous committed tree, so mark it to keep {@link #close()}
     * from deleting it, attach the rollback failure as suppressed, and point the user at it.
     */
    private void rollBack(final Path backup, final Path destination, final IOException swapFailed)
            throws IOException, JextractException {
        try {
            mover.move(backup, destination);
        } catch (final IOException rollbackFailed) {
            backupPreserved = true;
            swapFailed.addSuppressed(rollbackFailed);
            throw JextractExceptions.executionError(
                    "Promotion failed and the rollback also failed; the previous committed sources for "
                    + destination + " are preserved in " + backup + ". Restore them from there (or with git) "
                    + "and rerun.", swapFailed);
        }
        throw swapFailed;
    }

    private static void deleteQuietly(final Path path) {
        try {
            deleteRecursively(path);
        } catch (final IOException ignored) {
            // Best-effort cleanup; a leftover under the git-ignored build directory is harmless.
        }
    }

    private static void deleteRecursively(final Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs)
                    throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(final Path dir, final IOException failure)
                    throws IOException {
                if (failure != null) {
                    throw failure;
                }
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
