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

import static org.junit.Assert.*;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.junit.Test;

/**
 * Dependency Integration Tests.
 */
public class DependencyIT {

    /**
     * Default location of generated karaf features file.
     * <p>
     * See <a href=
     * "http://karaf.apache.org/manual/3.0.0-SNAPSHOT/developers-guide/karaf-maven-plugin.html"
     * >karaf-maven-plugin</a>
     */
    public static final String FEATURE = "./target/feature/feature.xml";

    @Test
    public void verifyKarafFeatureHasNoWrapProtocol() throws Exception {

        final File file = new File(FEATURE);

        final String text = FileUtils.readFileToString(file);

        if (text.contains("wrap:")) {
            System.err.println(text);
            fail("karaf feature.xml contains 'wrap:' protocol: some transitive dependencies are not osgi bundles");
        } else {
            System.out.println("all transitive dependencies are osgi bundles");
        }
    }
}
