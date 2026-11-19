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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The license header prepended to generated sources. jextract emits none, so the committed bindings
 * would otherwise carry no license; this restores it. The header text is bundled with the plugin as a
 * classpath resource, so every module that generates bindings gets the same header with no per-module
 * configuration, and regeneration reproduces the committed output byte for byte.
 */
final class LicenseHeader {

    private static final String RESOURCE = "/io/netty/build/jextract/license-header.txt";

    private final String text;

    private LicenseHeader(final String text) {
        this.text = text;
    }

    /**
     * The header bundled with the plugin.
     *
     * @throws JextractException if the bundled resource is missing (a packaging error) or unreadable
     */
    static LicenseHeader bundled() throws JextractException {
        return fromResource(RESOURCE);
    }

    /**
     * Reads a header from a classpath resource. Visible for tests, including the missing-resource path.
     *
     * @throws JextractException if the resource is missing (a packaging error) or unreadable
     */
    static LicenseHeader fromResource(final String resource) throws JextractException {
        return of(readResource(resource));
    }

    /**
     * Builds a header from raw text, normalising line endings to {@code \n} and ensuring a trailing
     * newline. Normalising every line ending (not just the trailing one) keeps the prepended header pure
     * LF even if the bundled resource is checked out with CRLF endings, so a CRLF header can never be
     * spliced onto an LF body and regeneration stays byte-for-byte reproducible across platforms.
     * Visible for tests.
     */
    static LicenseHeader of(final String raw) {
        final String lf = raw.replace("\r\n", "\n").replace("\r", "\n");
        return new LicenseHeader(lf.endsWith("\n") ? lf : lf + "\n");
    }

    /**
     * Prepends this header to every {@code .java} source under {@code directory} (recursively). Other
     * files, such as the provenance manifest, are left untouched.
     *
     * @throws JextractException if the directory cannot be scanned or a source cannot be rewritten
     */
    void prependToFilesIn(final File directory) throws JextractException {
        for (final File source : javaSourcesIn(directory)) {
            prependTo(source);
        }
    }

    /**
     * Prepends this header to a single {@code file}.
     *
     * @throws JextractException if the file cannot be read or rewritten
     */
    void prependTo(final File file) throws JextractException {
        try {
            Files.writeString(file.toPath(), text + Files.readString(file.toPath()));
        } catch (final IOException e) {
            throw JextractExceptions.executionError("Could not prepend the license header to " + file, e);
        }
    }

    private static List<File> javaSourcesIn(final File directory) throws JextractException {
        final Path root = directory.toPath();
        try (Stream<Path> files = Files.walk(root)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".java"))
                    .map(Path::toFile)
                    .collect(Collectors.toList());
        } catch (final IOException e) {
            throw JextractExceptions.executionError("Could not scan generated sources in " + root, e);
        }
    }

    private static String readResource(final String resource) throws JextractException {
        try (InputStream in = LicenseHeader.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw JextractExceptions.buildFailure(
                        "Bundled license header resource is missing from the plugin: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw JextractExceptions.executionError("Could not read bundled license header " + resource, e);
        }
    }
}
