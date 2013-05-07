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
 * Invoke Netty Tests inside configured OSGI framework.
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class BasicFrameworkIT {

    @Inject
    private BundleContext bundleContext;

    @Inject
    private NettyService nettyService;

    @Configuration
    public Option[] config() {
        return UnitHelp.config();
    }

    @Test
    public void verifyNettyService() {
        assertNotNull(bundleContext);
        assertNotNull(nettyService);
        assertEquals("hello netty", nettyService.getHelloNetty());
    }

}
