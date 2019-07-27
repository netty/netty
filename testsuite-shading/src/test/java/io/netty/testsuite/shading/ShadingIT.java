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
import org.junit.Test;
import org.junit.Assume;

import java.lang.reflect.Method;

public class ShadingIT {

    private static final String SHADING_PREFIX = System.getProperty("shadingPrefix2");
    private static final String SHADING_PREFIX2 = System.getProperty("shadingPrefix");

    @Test
    public void testShadingNativeTransport() throws Exception {
        // Skip on windows.
        Assume.assumeFalse(PlatformDependent.isWindows());

        String className = PlatformDependent.isOsx() ?
                "io.netty.channel.kqueue.KQueue" : "io.netty.channel.epoll.Epoll";
        testShading0(SHADING_PREFIX, className);
        testShading0(SHADING_PREFIX2, className);
    }

    @Test
    public void testShadingTcnative() throws Exception {
        // Skip on windows.
        Assume.assumeFalse(PlatformDependent.isWindows());

        String className = "io.netty.handler.ssl.OpenSsl";
        testShading0(SHADING_PREFIX, className);
        testShading0(SHADING_PREFIX2, className);
    }

    private static void testShading0(String shadingPrefix, String classname) throws Exception {
        final Class<?> clazz = Class.forName(shadingPrefix + '.' + classname);
        Method method = clazz.getMethod("ensureAvailability");
        method.invoke(null);
    }
}
