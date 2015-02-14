/*
 * Copyright 2015 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.osgitests;

import static org.junit.Assert.assertFalse;
import static org.ops4j.pax.exam.CoreOptions.junitBundles;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.systemProperty;
import static org.ops4j.pax.exam.CoreOptions.wrappedBundle;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;

@RunWith(PaxExam.class)
public class OsgiBundleTest {
    private static final Pattern SLASH = Pattern.compile("/", Pattern.LITERAL);
    private static final String DEPENCIES_LINE = "# dependencies";
    private static final String GROUP = "io.netty";
    private static final Collection<String> BUNDLES;

    static {
        final Set<String> artifacts = new HashSet<String>();
        final File f = new File("target/classes/META-INF/maven/dependencies.properties");
        try {
            final BufferedReader r = new BufferedReader(new FileReader(f));
            try {
                boolean haveDeps = false;

                while (true) {
                    final String line = r.readLine();
                    if (line == null) {
                        // End-of-file
                        break;
                    }

                    // We need to ignore any lines up to the dependencies
                    // line, otherwise we would include ourselves.
                    if (DEPENCIES_LINE.equals(line)) {
                        haveDeps = true;
                    } else if (haveDeps && line.startsWith(GROUP)) {
                        final String[] split = SLASH.split(line);
                        if (split.length > 1) {
                            artifacts.add(split[1]);
                        }
                    }
                }
            } finally {
                r.close();
            }
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }

        BUNDLES = artifacts;
    }

    @Configuration
    public final Option[] config() {
        final Collection<Option> options = new ArrayList<Option>();

        options.add(systemProperty("pax.exam.osgi.unresolved.fail").value("true"));
        options.addAll(Arrays.asList(junitBundles()));

        options.add(mavenBundle("com.barchart.udt", "barchart-udt-bundle").versionAsInProject());
        options.add(wrappedBundle(mavenBundle("org.rxtx", "rxtx").versionAsInProject()));

        for (String name : BUNDLES) {
            options.add(mavenBundle(GROUP, name).versionAsInProject());
        }

        return options.toArray(new Option[0]);
    }

    @Test
    public void testResolvedBundles() {
        // No-op, as we just want the bundles to be resolved. Just check if we tested something
        assertFalse("At least one bundle needs to be tested", BUNDLES.isEmpty());
    }
}

