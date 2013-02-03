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

import org.ops4j.pax.exam.Option;

/**
 * Unit Test Utilities.
 */
public class UnitHelp {

    private UnitHelp() {
    }

    /**
     * Default framework test configuration.
     */
    public static Option[] config() {
        return options(
                /** install logging */
                mavenBundle("org.slf4j", "slf4j-api").versionAsInProject(),
                mavenBundle("ch.qos.logback", "logback-core")
                        .versionAsInProject(),
                mavenBundle("ch.qos.logback", "logback-classic")
                        .versionAsInProject(),

                /** install scr annotations */
                mavenBundle("com.carrotgarden.osgi",
                        "carrot-osgi-anno-scr-core").versionAsInProject(),
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

}
