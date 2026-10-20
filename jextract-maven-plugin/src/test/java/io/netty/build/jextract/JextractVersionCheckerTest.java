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
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JextractVersionCheckerTest {

    private static final File JEXTRACT = new File("/opt/jextract/bin/jextract");

    @Test
    void passesWhenBannerContainsExpectedVersion() throws Exception {
        // A clean run whose banner carries the configured version must return without throwing.
        checker("22").verify(command -> FakeProcess.succeedingWithOutput("jextract 22\nJDK version 22"), 300L);
    }

    @Test
    void requestsTheVersionFlag() throws Exception {
        final AtomicReference<List<String>> seen = new AtomicReference<>();
        checker("22").verify(command -> {
            seen.set(command);
            return FakeProcess.succeedingWithOutput("jextract 22");
        }, 300L);
        final List<String> command = seen.get();
        assertEquals(JEXTRACT.getAbsolutePath(), command.get(0));
        assertEquals("--version", command.get(1));
    }

    @Test
    void mismatchThrowsBuildFailure() {
        final JextractException e = assertThrows(JextractException.class,
                () -> checker("22").verify(command -> FakeProcess.succeedingWithOutput("jextract 21"), 300L));
        assertTrue(e.isBuildFailure());
        assertTrue(e.getMessage().contains("version mismatch"), e.getMessage());
        assertTrue(e.getMessage().contains("22"), e.getMessage());
    }

    @Test
    void expectedVersionMustMatchAsAWholeToken() {
        // "2" must not match inside "22": the version has to be the whole token on the jextract line.
        final JextractException e = assertThrows(JextractException.class,
                () -> checker("2").verify(command -> FakeProcess.succeedingWithOutput("jextract 22"), 300L));
        assertTrue(e.isBuildFailure());
        assertTrue(e.getMessage().contains("version mismatch"), e.getMessage());
    }

    @Test
    void matchesTheJextractLineNotTheJdkLine() throws Exception {
        // Real multi-line banner: the version lives on the "jextract <version>" line and must pass.
        checker("25").verify(command -> FakeProcess.succeedingWithOutput(
                "jextract 25\nJDK version 25+37-3491\nLibClang version 13.0.0"), 300L);
    }

    @Test
    void rejectsVersionAppearingOnlyInTheJdkBuildSuffix() {
        // "JDK version 21.0.2+13-22" carries "22" in the build suffix; it must not satisfy a configured
        // 22 when the jextract line says 21, or a wrong binary would silently pass the gate.
        final JextractException e = assertThrows(JextractException.class,
                () -> checker("22").verify(command -> FakeProcess.succeedingWithOutput(
                        "jextract 21\nJDK version 21.0.2+13-22"), 300L));
        assertTrue(e.isBuildFailure());
        assertTrue(e.getMessage().contains("version mismatch"), e.getMessage());
    }

    @Test
    void nonZeroExitThrowsBuildFailure() {
        final JextractException e = assertThrows(JextractException.class,
                () -> checker("22").verify(command -> FakeProcess.withExitCode(2), 300L));
        assertTrue(e.isBuildFailure());
        assertTrue(e.getMessage().contains("exited with code 2"), e.getMessage());
    }

    @Test
    void startFailureThrowsExecutionError() {
        final JextractException e = assertThrows(JextractException.class,
                () -> checker("22").verify(command -> {
                    throw new IOException("boom");
                }, 300L));
        assertFalse(e.isBuildFailure());
        assertTrue(e.getMessage().contains("check its version"), e.getMessage());
    }

    private static JextractVersionChecker checker(final String expectedVersion) {
        return new JextractVersionChecker(JEXTRACT, expectedVersion);
    }
}
