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
import static org.ops4j.pax.exam.CoreOptions.frameworkProperty;
import static org.ops4j.pax.exam.CoreOptions.junitBundles;
import static org.ops4j.pax.exam.CoreOptions.systemProperty;
import static org.ops4j.pax.exam.CoreOptions.url;
import static org.osgi.framework.Constants.FRAMEWORK_BOOTDELEGATION;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import io.netty.util.internal.PlatformDependent;

@RunWith(PaxExam.class)
public class OsgiBundleTest {
    private static final Collection<String> LINKS;

    static {
        final Set<String> links = new HashSet<String>();

        final File directory = new File("target/generated-test-resources/alta/");
        File[] files = directory.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return (name.startsWith("io.netty") || name.startsWith("com.barchart.udt")) && name.endsWith(".link");
            }
        });
        if (files == null) {
            throw new IllegalStateException(directory + " is not found or is not a directory");
        }
        for (File f: files) {
            links.add(f.getName());
        }
        LINKS = links;
    }

    @Configuration
    public final Option[] config() {
        final Collection<Option> options = new ArrayList<Option>();

        // Avoid boot delegating sun.misc which would fail testCanLoadPlatformDependent()
        options.add(frameworkProperty(FRAMEWORK_BOOTDELEGATION).value("com.sun.*"));
        options.add(systemProperty("pax.exam.osgi.unresolved.fail").value("true"));
        options.add(junitBundles());

        for (String link : LINKS) {
            options.add(url("link:classpath:" + link));
        }

        return options.toArray(new Option[0]);
    }

    @Test
    public void testResolvedBundles() {
        // No-op, as we just want the bundles to be resolved. Just check if we tested something
        assertFalse("At least one bundle needs to be tested", LINKS.isEmpty());
    }

    @Test
    public void testCanLoadPlatformDependent() {
        assertFalse(PlatformDependent.addressSize() == 0);
    }
}
