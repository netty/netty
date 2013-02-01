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
import static org.ops4j.pax.exam.CoreOptions.*;
import io.netty.test.udt.nio.NioUdtMessageRendezvousChannelTest;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.osgi.framework.BundleContext;

/**
 * Invoke UDT Test inside configured OSGI framework.
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class NioUdtMessageRendezvousChannelIT extends
        NioUdtMessageRendezvousChannelTest {

    @Inject
    private BundleContext bundleContext;

    @Configuration
    public Option[] config() {

        return UnitHelp.config(

                /** install UDT tests bundle */
                wrappedBundle(maven().groupId("io.netty")
                        .artifactId("netty-transport-udt").classifier("tests")
                        .versionAsInProject()),

                /** install tests dependency */
                wrappedBundle(maven().groupId("com.yammer.metrics")
                        .artifactId("metrics-core").versionAsInProject()),
                /** install tests dependency */
                wrappedBundle(maven().groupId("com.google.caliper")
                        .artifactId("caliper").versionAsInProject())

        );
    }

    @Test
    public void verify() throws Exception {
        assertNotNull(bundleContext);
        super.basicEcho();
    }

}
