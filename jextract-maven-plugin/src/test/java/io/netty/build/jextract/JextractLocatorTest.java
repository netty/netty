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

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JextractLocatorTest {

    @TempDir
    File tmp;

    /** Creates an executable file named {@code jextract} inside a fresh subdirectory of tmp. */
    private File executableIn(final String dirName) throws IOException {
        final File dir = new File(tmp, dirName);
        assertTrue(dir.mkdirs());
        final File exe = new File(dir, "jextract");
        assertTrue(exe.createNewFile());
        assertTrue(exe.setExecutable(true));
        return exe;
    }

    @Test
    void explicitPathWinsOverEnvAndPath() throws Exception {
        final File explicit = executableIn("explicit");
        final File onPath = executableIn("bin");

        final File resolved = JextractLocator.builder()
                .explicitPath(explicit.getAbsolutePath())
                .jextractEnv(executableIn("env").getAbsolutePath())
                .pathEnv(onPath.getParentFile().getAbsolutePath())
                .pathSeparator(File.pathSeparator)
                .build()
                .locate();

        assertEquals(explicit, resolved);
    }

    @Test
    void jextractEnvWinsOverPath() throws Exception {
        final File env = executableIn("env");
        final File onPath = executableIn("bin");

        final File resolved = JextractLocator.builder()
                .explicitPath(null)
                .jextractEnv(env.getAbsolutePath())
                .pathEnv(onPath.getParentFile().getAbsolutePath())
                .pathSeparator(File.pathSeparator)
                .build()
                .locate();

        assertEquals(env, resolved);
    }

    @Test
    void fallsBackToPathAndScansEntriesInOrder() throws Exception {
        final File onPath = executableIn("bin");
        final String pathValue = new File(tmp, "empty").getAbsolutePath()
                + File.pathSeparator + onPath.getParentFile().getAbsolutePath();

        final File resolved = JextractLocator.builder()
                .pathEnv(pathValue)
                .pathSeparator(File.pathSeparator)
                .build()
                .locate();

        assertEquals(onPath, resolved);
    }

    @Test
    void blankExplicitPathIsTreatedAsUnset() throws Exception {
        final File env = executableIn("env");

        final File resolved = JextractLocator.builder()
                .explicitPath("")
                .jextractEnv(env.getAbsolutePath())
                .build()
                .locate();

        assertEquals(env, resolved);
    }

    @Test
    void throwsWhenNoSourceConfigured() {
        assertThrows(JextractException.class, () ->
                JextractLocator.builder().build().locate());
    }

    @Test
    void throwsWhenExplicitPathIsNotExecutable() throws Exception {
        final File notExec = new File(tmp, "jextract");
        assertTrue(notExec.createNewFile());
        assertTrue(notExec.setExecutable(false));

        final JextractException e = assertThrows(JextractException.class, () ->
                JextractLocator.builder()
                        .explicitPath(notExec.getAbsolutePath())
                        .build()
                        .locate());
        assertTrue(e.getMessage().contains("Configured jextract path"));
    }

    @Test
    void throwsWhenJextractEnvIsNotExecutable() throws Exception {
        final File notExec = new File(tmp, "jextract");
        assertTrue(notExec.createNewFile());
        assertTrue(notExec.setExecutable(false));

        final JextractException e = assertThrows(JextractException.class, () ->
                JextractLocator.builder()
                        .jextractEnv(notExec.getAbsolutePath())
                        .build()
                        .locate());
        assertTrue(e.getMessage().contains("JEXTRACT environment variable"));
    }

    @Test
    void throwsWhenNotFoundOnPath() {
        assertThrows(JextractException.class, () ->
                JextractLocator.builder()
                        .pathEnv(new File(tmp, "empty").getAbsolutePath())
                        .pathSeparator(File.pathSeparator)
                        .build()
                        .locate());
    }

    @Test
    void findsWindowsBatLauncherOnPath() throws Exception {
        // On Windows the PATH launcher is jextract.bat, not the extensionless shell script.
        final File dir = new File(tmp, "bin");
        assertTrue(dir.mkdirs());
        final File bat = new File(dir, "jextract.bat");
        assertTrue(bat.createNewFile());
        assertTrue(bat.setExecutable(true));

        final File resolved = JextractLocator.builder()
                .osName("Windows 11")
                .pathEnv(dir.getAbsolutePath())
                .pathSeparator(File.pathSeparator)
                .build()
                .locate();

        assertEquals(bat, resolved);
    }

    @Test
    void windowsDoesNotMatchExtensionlessJextractOnPath() throws Exception {
        // The extensionless shell script cannot be exec-ed on Windows, so it must not be resolved there.
        final File dir = new File(tmp, "bin");
        assertTrue(dir.mkdirs());
        final File exe = new File(dir, "jextract");
        assertTrue(exe.createNewFile());
        assertTrue(exe.setExecutable(true));

        assertThrows(JextractException.class, () ->
                JextractLocator.builder()
                        .osName("Windows 11")
                        .pathEnv(dir.getAbsolutePath())
                        .pathSeparator(File.pathSeparator)
                        .build()
                        .locate());
    }
}
