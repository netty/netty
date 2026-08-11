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
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LicenseHeaderTest {

    @TempDir
    File tmp;

    @Test
    void prependsToEveryJavaSourceAndLeavesOtherFiles() throws Exception {
        final Path dir = tmp.toPath().resolve("io/netty/gen");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("A.java"), "// a");
        Files.writeString(dir.resolve("B.java"), "package io.netty.gen;");
        Files.writeString(dir.resolve("GENERATED.properties"), "jextract.version=25");

        LicenseHeader.of("/* header */").prependToFilesIn(tmp);

        assertEquals("/* header */\n// a", Files.readString(dir.resolve("A.java")));
        assertEquals("/* header */\npackage io.netty.gen;", Files.readString(dir.resolve("B.java")));
        assertEquals("jextract.version=25", Files.readString(dir.resolve("GENERATED.properties")),
                "non-.java files must be left untouched");
    }

    @Test
    void prependsToASingleFile() throws Exception {
        final Path file = tmp.toPath().resolve("A.java");
        Files.writeString(file, "// a");

        LicenseHeader.of("/* header */").prependTo(file.toFile());

        assertEquals("/* header */\n// a", Files.readString(file));
    }

    @Test
    void headerAlreadyEndingWithNewlineIsNotDoubled() throws Exception {
        final Path file = tmp.toPath().resolve("A.java");
        Files.writeString(file, "// a");

        LicenseHeader.of("/* header */\n").prependTo(file.toFile());

        assertEquals("/* header */\n// a", Files.readString(file));
    }

    @Test
    void normalisesCarriageReturnsToLf() throws Exception {
        final Path file = tmp.toPath().resolve("A.java");
        Files.writeString(file, "// body");

        LicenseHeader.of("/* a */\r\n/* b */\r").prependTo(file.toFile());

        final String result = Files.readString(file);
        assertEquals("/* a */\n/* b */\n// body", result);
        assertFalse(result.contains("\r"), "no carriage return should survive normalization");
    }

    @Test
    void missingResourceFailsWithActionableMessage() {
        final JextractException e = assertThrows(JextractException.class,
                () -> LicenseHeader.fromResource("/io/netty/build/jextract/does-not-exist.txt"));
        assertTrue(e.getMessage().contains("missing"), e.getMessage());
    }

    @Test
    void bundledHeaderCarriesTheNettyCopyrightAndPrecedesTheSource() throws Exception {
        final Path file = tmp.toPath().resolve("A.java");
        Files.writeString(file, "// generated");

        LicenseHeader.bundled().prependTo(file.toFile());

        final String result = Files.readString(file);
        assertTrue(result.startsWith("/*"), result);
        assertTrue(result.contains("The Netty Project"), result);
        assertTrue(result.endsWith("// generated"), result);
    }
}
