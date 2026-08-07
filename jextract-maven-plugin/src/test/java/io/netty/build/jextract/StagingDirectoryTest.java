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
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StagingDirectoryTest {

    private static final String PACKAGE = "io.netty.gen";

    @TempDir
    File tmp;

    @Test
    void crossFilesystemOutputReportsActionableMessage() throws Exception {
        final StagingDirectory staging = staging();
        // Every ATOMIC_MOVE across a filesystem boundary throws this; simulate it on the first move.
        staging.setMover((from, to) -> {
            throw new AtomicMoveNotSupportedException(from.toString(), to.toString(), "different filesystem");
        });

        final JextractException e = assertThrows(JextractException.class,
                () -> staging.promoteTo(PACKAGE, manifest()));
        assertTrue(e.getMessage().contains("same filesystem"), e.getMessage());
    }

    @Test
    void rollbackSuccessRestoresTheTreeAndReportsTheOriginalCause() throws Exception {
        final Path committed = committedPackageWith("Old.java");
        final StagingDirectory staging = staging();
        // move 1 (destination -> backup) and move 3 (backup -> destination) run for real; the forward
        // move 2 fails, so the rollback restores the previous tree and the original cause is reported.
        final int[] move = {0};
        staging.setMover((from, to) -> {
            if (++move[0] == 2) {
                throw new IOException("forward move failed");
            }
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
        });

        final JextractException e = assertThrows(JextractException.class,
                () -> staging.promoteTo(PACKAGE, manifest()));
        assertTrue(e.getMessage().contains("Could not promote"), e.getMessage());
        assertEquals("forward move failed", e.getCause().getMessage());
        assertTrue(Files.exists(committed.resolve("Old.java")), "the previous tree must be restored");
    }

    @Test
    void rollbackFailurePreservesTheBackupAndKeepsBothCauses() throws Exception {
        committedPackageWith("Old.java");
        final StagingDirectory staging = staging();
        // move 1 (destination -> backup) succeeds; the forward move 2 and the rollback move 3 both fail,
        // leaving the previous tree only in the backup.
        final int[] move = {0};
        staging.setMover((from, to) -> {
            if (++move[0] == 1) {
                Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
                return;
            }
            throw new IOException(move[0] == 2 ? "forward move failed" : "rollback move failed");
        });

        final JextractException e = assertThrows(JextractException.class,
                () -> staging.promoteTo(PACKAGE, manifest()));
        assertTrue(e.getMessage().contains("preserved in"), e.getMessage());
        assertEquals("forward move failed", e.getCause().getMessage());
        assertEquals(1, e.getCause().getSuppressed().length);
        assertEquals("rollback move failed", e.getCause().getSuppressed()[0].getMessage());

        final Path backup = new File(tmp, "target").toPath().resolve("jextract-promote-backup");
        assertTrue(Files.exists(backup.resolve("Old.java")), "the backup must hold the previous tree");
        staging.close();
        assertTrue(Files.exists(backup.resolve("Old.java")), "close() must not delete the preserved backup");
    }

    @Test
    void rollbackRunsWhenTheForwardMoveThrowsUnchecked() throws Exception {
        final Path committed = committedPackageWith("Old.java");
        final StagingDirectory staging = staging();
        // move 1 (destination -> backup) runs for real; the forward move 2 throws an unchecked
        // exception, which must still trigger the rollback rather than skip it.
        final int[] move = {0};
        staging.setMover((from, to) -> {
            if (++move[0] == 2) {
                throw new IllegalStateException("unchecked forward move failure");
            }
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
        });

        final JextractException e = assertThrows(JextractException.class,
                () -> staging.promoteTo(PACKAGE, manifest()));
        assertEquals("unchecked forward move failure", e.getCause().getMessage());
        assertTrue(Files.exists(committed.resolve("Old.java")),
                "the previous tree must be restored even when the forward move fails unchecked");
    }

    @Test
    void backupPreservedWhenTheRollbackMoveThrowsUnchecked() throws Exception {
        committedPackageWith("Old.java");
        final StagingDirectory staging = staging();
        // move 1 (destination -> backup) succeeds; the forward move 2 fails and the rollback move 3
        // throws an unchecked exception, so the backup must be preserved rather than deleted by close().
        final int[] move = {0};
        staging.setMover((from, to) -> {
            move[0]++;
            if (move[0] == 1) {
                Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
                return;
            }
            if (move[0] == 2) {
                throw new IOException("forward move failed");
            }
            throw new IllegalStateException("unchecked rollback move failure");
        });

        final JextractException e = assertThrows(JextractException.class,
                () -> staging.promoteTo(PACKAGE, manifest()));
        assertTrue(e.getMessage().contains("preserved in"), e.getMessage());
        assertEquals("forward move failed", e.getCause().getMessage());
        assertEquals(1, e.getCause().getSuppressed().length);
        assertEquals("unchecked rollback move failure", e.getCause().getSuppressed()[0].getMessage());

        final Path backup = new File(tmp, "target").toPath().resolve("jextract-promote-backup");
        assertTrue(Files.exists(backup.resolve("Old.java")), "the backup must hold the previous tree");
        staging.close();
        assertTrue(Files.exists(backup.resolve("Old.java")), "close() must not delete the preserved backup");
    }

    // --- helpers -----------------------------------------------------------------------------

    /** A staging directory holding one freshly generated file in {@link #PACKAGE}. */
    private StagingDirectory staging() throws Exception {
        final StagingDirectory staging =
                StagingDirectory.create(new File(tmp, "target"), new File(tmp, "out"));
        final Path generated = packageDir(staging.directory().toPath());
        Files.createDirectories(generated);
        write(generated.resolve("New.java"), "// new");
        return staging;
    }

    /** Seeds a previous committed generation in the output tree and returns its package directory. */
    private Path committedPackageWith(final String fileName) throws IOException {
        final Path pkg = packageDir(new File(tmp, "out").toPath());
        Files.createDirectories(pkg);
        write(pkg.resolve(fileName), "// old");
        return pkg;
    }

    private static Path packageDir(final Path root) {
        Path dir = root;
        for (final String part : PACKAGE.split("\\.")) {
            dir = dir.resolve(part);
        }
        return dir;
    }

    private static void write(final Path file, final String content) throws IOException {
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
    }

    private static ProvenanceManifest manifest() {
        return new ProvenanceManifest("22", "macos", null);
    }
}
