/*
 * Copyright 2018 The Netty Project
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
package io.netty.testsuite.shading;

import io.netty.util.internal.PlatformDependent;
import org.junit.Ignore;
import org.junit.Test;

import java.lang.reflect.Method;

public class ShadingIT {

    @Test
    public void testShadingNativeTransport() throws Exception {
        testShading0(PlatformDependent.isOsx() ? "io.netty.channel.kqueue.KQueue" : "io.netty.channel.epoll.Epoll");
    }

    @Ignore("Figure out why this sometimes fail on the CI")
    @Test
    public void testShadingTcnative() throws Exception {
        testShading0("io.netty.handler.ssl.OpenSsl");
    }

    private static void testShading0(String classname) throws Exception {
        String shadingPrefix = System.getProperty("shadingPrefix");
        final Class<?> clazz = Class.forName(shadingPrefix + '.' + classname);
        Method method = clazz.getMethod("ensureAvailability");
        method.invoke(null);
    }
}
