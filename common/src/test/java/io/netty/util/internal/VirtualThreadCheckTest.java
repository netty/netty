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

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

import static io.netty.util.internal.PlatformDependent0.BASE_VIRTUAL_THREAD_CLASS;
import static io.netty.util.internal.PlatformDependent0.IS_VIRTUAL_THREAD_METHOD;
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

        Thread subOfSubThread = new SubThread() {
            @Override
            public void run() {
                atomicRes.set(PlatformDependent.checkVirtualThread(Thread.currentThread()));
            }
        };
        subOfSubThread.start();
        subOfSubThread.join();
        assertEquals(NOT_VIRTUAL, atomicRes.get());

        Thread subOfSubOfSubThread = new SubOfSubThread() {
            @Override
            public void run() {
                atomicRes.set(PlatformDependent.checkVirtualThread(Thread.currentThread()));
            }
        };
        subOfSubOfSubThread.start();
        subOfSubOfSubThread.join();
        assertEquals(NOT_VIRTUAL, atomicRes.get());

        assumeTrue(PlatformDependent.javaVersion() >= 21);
        Method startVirtualThread = getStartVirtualThreadMethod();
        Thread virtualThread = (Thread) startVirtualThread.invoke(null, new Runnable() {
            @Override
            public void run() {
            }
        });
        if (IS_VIRTUAL_THREAD_METHOD != null || BASE_VIRTUAL_THREAD_CLASS != null) {
            assertEquals(IS_VIRTUAL, PlatformDependent.checkVirtualThread(virtualThread));
        } else {
            assertEquals(UNKNOWN, PlatformDependent.checkVirtualThread(virtualThread));
        }
    }

    @Test
    public void testGetVirtualThreadCheckMethod() throws Exception {
        if (PlatformDependent.javaVersion() < 19) {
            assertNull(IS_VIRTUAL_THREAD_METHOD);
        } else {
            assumeTrue(PlatformDependent.javaVersion() >= 21);
            assumeTrue(IS_VIRTUAL_THREAD_METHOD != null);
            boolean isVirtual = (Boolean) IS_VIRTUAL_THREAD_METHOD.invoke(Thread.currentThread());
            assertFalse(isVirtual);

            Method startVirtualThread = getStartVirtualThreadMethod();
            Thread virtualThread = (Thread) startVirtualThread.invoke(null, new Runnable() {
                @Override
                public void run() {
                }
            });
            isVirtual = (Boolean) IS_VIRTUAL_THREAD_METHOD.invoke(virtualThread);
            assertTrue(isVirtual);
        }
    }

    @Test
    public void testGetBaseVirtualThreadClass() throws Exception {
        if (PlatformDependent.javaVersion() < 19) {
            assertNull(BASE_VIRTUAL_THREAD_CLASS);
        } else {
            assumeTrue(PlatformDependent.javaVersion() >= 21);
            assumeTrue(BASE_VIRTUAL_THREAD_CLASS != null);
            assertFalse(BASE_VIRTUAL_THREAD_CLASS.isInstance(Thread.currentThread()));

            Method startVirtualThread = getStartVirtualThreadMethod();
            Thread virtualThread = (Thread) startVirtualThread.invoke(null, new Runnable() {
                @Override
                public void run() {
                }
            });
            assertTrue(BASE_VIRTUAL_THREAD_CLASS.isInstance(virtualThread));
        }
    }

    private Method getStartVirtualThreadMethod() throws NoSuchMethodException {
        return Thread.class.getMethod("startVirtualThread", Runnable.class);
    }

    private static class SubThread extends Thread {
        // for test
    }

    private static class SubOfSubThread extends SubThread {
        // for test
    }
}
