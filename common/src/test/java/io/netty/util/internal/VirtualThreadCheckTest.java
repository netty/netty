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

import static io.netty.util.internal.PlatformDependent0.IS_VIRTUAL_THREAD_METHOD_HANDLE;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class VirtualThreadCheckTest {

    @Test
    public void testCheckVirtualThread() throws Exception {
        assertFalse(PlatformDependent.isVirtualThread(null));
        assertFalse(PlatformDependent.isVirtualThread(Thread.currentThread()));
        FastThreadLocalThread fastThreadLocalThread = new FastThreadLocalThread();
        assertFalse(PlatformDependent.isVirtualThread(fastThreadLocalThread));
        final AtomicReference<Boolean> atomicRes = new AtomicReference<Boolean>();
        Thread subThread = new Thread() {
            @Override
            public void run() {
                atomicRes.set(PlatformDependent.isVirtualThread(Thread.currentThread()));
            }
        };
        subThread.start();
        subThread.join();
        assertFalse(atomicRes.get());

        Thread subOfSubThread = new SubThread() {
            @Override
            public void run() {
                atomicRes.set(PlatformDependent.isVirtualThread(Thread.currentThread()));
            }
        };
        subOfSubThread.start();
        subOfSubThread.join();
        assertFalse(atomicRes.get());

        Thread subOfSubOfSubThread = new SubOfSubThread() {
            @Override
            public void run() {
                atomicRes.set(PlatformDependent.isVirtualThread(Thread.currentThread()));
            }
        };
        subOfSubOfSubThread.start();
        subOfSubOfSubThread.join();
        assertFalse(atomicRes.get());

        assumeTrue(PlatformDependent.javaVersion() >= 21);
        Method startVirtualThread = getStartVirtualThreadMethod();
        Thread virtualThread = (Thread) startVirtualThread.invoke(null, new Runnable() {
            @Override
            public void run() {
            }
        });
        assertTrue(PlatformDependent.isVirtualThread(virtualThread));
    }

    @Test
    public void testGetVirtualThreadCheckMethod() throws Throwable {
        if (PlatformDependent.javaVersion() < 19) {
            assertNull(IS_VIRTUAL_THREAD_METHOD_HANDLE);
        } else {
            assumeTrue(PlatformDependent.javaVersion() >= 21);
            assumeTrue(IS_VIRTUAL_THREAD_METHOD_HANDLE != null);
            boolean isVirtual = (boolean) IS_VIRTUAL_THREAD_METHOD_HANDLE.invokeExact(Thread.currentThread());
            assertFalse(isVirtual);

            Method startVirtualThread = getStartVirtualThreadMethod();
            Thread virtualThread = (Thread) startVirtualThread.invoke(null, new Runnable() {
                @Override
                public void run() {
                }
            });
            isVirtual = (boolean) IS_VIRTUAL_THREAD_METHOD_HANDLE.invokeExact(virtualThread);
            assertTrue(isVirtual);
        }
    }

    private Method getStartVirtualThreadMethod() throws NoSuchMethodException {
        return Thread.class.getMethod("startVirtualThread", Runnable.class);
    }

    private static class SubThread extends Thread {
        // For test
    }

    private static class SubOfSubThread extends SubThread {
        // For test
    }
}
