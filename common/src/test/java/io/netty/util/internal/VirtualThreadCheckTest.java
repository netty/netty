/*
 * Copyright 2025 The Netty Project
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
package io.netty.util.internal;

import io.netty.util.concurrent.FastThreadLocalThread;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

import static io.netty.util.internal.VirtualThreadCheckResult.IS_VIRTUAL;
import static io.netty.util.internal.VirtualThreadCheckResult.NOT_VIRTUAL;
import static io.netty.util.internal.VirtualThreadCheckResult.UNKNOWN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class VirtualThreadCheckTest {

    @Test
    public void testCheckVirtualThread() throws Exception {
        assertEquals(NOT_VIRTUAL, PlatformDependent.checkVirtualThread(null));
        assertEquals(NOT_VIRTUAL, PlatformDependent.checkVirtualThread(Thread.currentThread()));
        FastThreadLocalThread fastThreadLocalThread = new FastThreadLocalThread();
        assertEquals(NOT_VIRTUAL, PlatformDependent.checkVirtualThread(fastThreadLocalThread));
        final AtomicReference<VirtualThreadCheckResult> atomicRes = new AtomicReference<VirtualThreadCheckResult>();
        Thread subThread = new Thread() {
            @Override
            public void run() {
                atomicRes.set(PlatformDependent.checkVirtualThread(Thread.currentThread()));
            }
        };
        subThread.start();
        subThread.join();
        assertEquals(NOT_VIRTUAL, atomicRes.get());

        assumeTrue(PlatformDependent.javaVersion() >= 21);
        Method startVirtualThread = getStartVirtualThreadMethod();
        assumeTrue(startVirtualThread != null);
        Thread virtualThread = (Thread) startVirtualThread.invoke(null, new Runnable() {
            @Override
            public void run() {
            }
        });
        Method isVirtualMethod = PlatformDependent0.getVirtualThreadCheckMethod();
        Class<?> baseVirtualThreadClass = PlatformDependent0.getBaseVirtualThreadClass();
        if (isVirtualMethod != null || baseVirtualThreadClass != null) {
            assertEquals(IS_VIRTUAL, PlatformDependent.checkVirtualThread(virtualThread));
        } else {
            assertEquals(UNKNOWN, PlatformDependent.checkVirtualThread(virtualThread));
        }
    }

    @Test
    public void testGetVirtualThreadCheckMethod() throws InvocationTargetException, IllegalAccessException {
        if (PlatformDependent.javaVersion() < 19) {
            assertNull(PlatformDependent0.getVirtualThreadCheckMethod());
        } else {
            assumeTrue(PlatformDependent.javaVersion() >= 21);
            Method isVirtualMethod = PlatformDependent0.getVirtualThreadCheckMethod();
            assumeTrue(isVirtualMethod != null);
            boolean isVirtual = (Boolean) isVirtualMethod.invoke(Thread.currentThread());
            assertFalse(isVirtual);

            Method startVirtualThread = getStartVirtualThreadMethod();
            assumeTrue(startVirtualThread != null);
            Thread virtualThread = (Thread) startVirtualThread.invoke(null, new Runnable() {
                @Override
                public void run() {
                }
            });
            isVirtual = (Boolean) isVirtualMethod.invoke(virtualThread);
            assertTrue(isVirtual);
        }
    }

    @Test
    public void testGetBaseVirtualThreadClass() throws InvocationTargetException, IllegalAccessException {
        if (PlatformDependent.javaVersion() < 19) {
            assertNull(PlatformDependent0.getBaseVirtualThreadClass());
        } else {
            assumeTrue(PlatformDependent.javaVersion() >= 21);
            Class<?> baseVirtualThreadClass = PlatformDependent0.getBaseVirtualThreadClass();
            assumeTrue(baseVirtualThreadClass != null);
            boolean isVirtual = baseVirtualThreadClass.isInstance(Thread.currentThread());
            assertFalse(isVirtual);

            Method startVirtualThread = getStartVirtualThreadMethod();
            assumeTrue(startVirtualThread != null);
            Thread virtualThread = (Thread) startVirtualThread.invoke(null, new Runnable() {
                @Override
                public void run() {
                }
            });
            isVirtual = baseVirtualThreadClass.isInstance(virtualThread);
            assertTrue(isVirtual);
        }
    }

    private Method getStartVirtualThreadMethod() {
        try {
            Method startVirtualThread = Thread.class.getMethod("startVirtualThread", Runnable.class);
            Thread virtualThread = (Thread) startVirtualThread.invoke(null, new Runnable() {
                @Override
                public void run() {
                }
            });
            return startVirtualThread;
        } catch (Throwable e) {
            return null;
        }
    }
}
