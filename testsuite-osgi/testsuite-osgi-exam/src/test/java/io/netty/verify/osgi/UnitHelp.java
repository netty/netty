/*
 * Copyright 2013 The Netty Project
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
package io.netty.verify.osgi;

import static org.ops4j.pax.exam.CoreOptions.*;

import java.util.Arrays;
import java.util.List;

import org.ops4j.pax.exam.Option;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;

/**
 * Unit Test Utilities.
 */
public final class UnitHelp {

    private UnitHelp() {
    }

    /**
     * Default framework test configuration.
     */
    public static Option[] config() {
        return options(

                /** set test logging level inside osgi container */
                systemProperty("org.ops4j.pax.logging.DefaultServiceLog.level")
                        .value("INFO"),

                /** install logging */
                // mavenBundle().groupId("org.ops4j.pax.logging").artifactId(
                // "pax-logging-api"),
                // mavenBundle().groupId("org.ops4j.pax.logging").artifactId(
                // "pax-logging-service"),

                /** install scr runtime provider */
                mavenBundle("org.apache.felix", "org.apache.felix.scr")
                        .versionAsInProject(),

                /** install netty bundle */
                mavenBundle("io.netty", "netty-common").versionAsInProject(),

                /** install netty bundle */
                mavenBundle("io.netty", "netty-buffer").versionAsInProject(),

                /** install netty bundle */
                mavenBundle("io.netty", "netty-codec").versionAsInProject(),

                /** install netty bundle */
                mavenBundle("io.netty", "netty-codec-http")
                        .versionAsInProject(),

                /** install netty bundle */
                mavenBundle("io.netty", "netty-codec-socks")
                        .versionAsInProject(),

                /** install netty bundle */
                mavenBundle("io.netty", "netty-handler").versionAsInProject(),

                /** install netty bundle */
                mavenBundle("io.netty", "netty-transport").versionAsInProject(),

                /**
                 * DO NOT install netty-transport-rxtx bundle due to rxtx
                 * depencency:
                 * <p>
                 * 1) rxtx does not have automatic native library loader
                 * <p>
                 * 2) rxtx does not have osgi bundle
                 */
                // mavenBundle("io.netty", "netty-transport-rxtx")
                // .versionAsInProject(),

                /** install netty bundle */
                mavenBundle("io.netty", "netty-transport-sctp")
                        .versionAsInProject(),

                /** install netty bundle with dependency */
                mavenBundle("io.netty", "netty-transport-udt")
                        .versionAsInProject(),
                mavenBundle("com.barchart.udt", "barchart-udt-bundle")
                        .versionAsInProject(),

                /** install this module bundle */
                bundle("reference:file:target/classes"),

                /** install java unit bundles */
                junitBundles());
    }

    /**
     * Combine default framework options with custom options.
     */
    public static Option[] config(final Option... options) {
        return concat(config(), options);
    }

    /**
     * Concatenate generic arrays.
     */
    public static <T> T[] concat(final T[] first, final T[] second) {
        final T[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    /**
     * Display bundle dependencies.
     */
    public static void logBundleWires(final BundleContext context) {
        for (final Bundle bundle : context.getBundles()) {
            final BundleWiring wiring = bundle.adapt(BundleWiring.class);
            System.out.println("#   bundle=" + bundle);
            final List<BundleWire> provided = wiring.getProvidedWires(null);
            for (final BundleWire wire : provided) {
                System.out.println("#      provided=" + wire);
            }
            final List<BundleWire> required = wiring.getRequiredWires(null);
            for (final BundleWire wire : required) {
                System.out.println("#      required=" + wire);
            }
        }
    }

}
