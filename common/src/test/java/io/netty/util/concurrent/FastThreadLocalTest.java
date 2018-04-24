/*
 * Copyright 2014 The Netty Project
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

package io.netty.util.concurrent;

import io.netty.util.internal.ObjectCleaner;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class FastThreadLocalTest {
    @Before
    public void setUp() {
        FastThreadLocal.removeAll();
        assertThat(FastThreadLocal.size(), is(0));
    }

    @Test(timeout = 10000)
    public void testRemoveAll() throws Exception {
        final AtomicBoolean removed = new AtomicBoolean();
        final FastThreadLocal<Boolean> var = new FastThreadLocal<Boolean>() {
            @Override
            protected void onRemoval(Boolean value) {
                removed.set(true);
            }
        };

        // Initialize a thread-local variable.
        assertThat(var.get(), is(nullValue()));
        assertThat(FastThreadLocal.size(), is(1));

        // And then remove it.
        FastThreadLocal.removeAll();
        assertThat(removed.get(), is(true));
        assertThat(FastThreadLocal.size(), is(0));
    }

    @Test(timeout = 10000)
    public void testRemoveAllFromFTLThread() throws Throwable {
        final AtomicReference<Throwable> throwable = new AtomicReference<Throwable>();
        final Thread thread = new FastThreadLocalThread() {
            @Override
            public void run() {
                try {
                    testRemoveAll();
                } catch (Throwable t) {
                    throwable.set(t);
                }
            }
        };

        thread.start();
        thread.join();

        Throwable t = throwable.get();
        if (t != null) {
            throw t;
        }
    }

    @Test
    public void testMultipleSetRemove() throws Exception {
        final FastThreadLocal<String> threadLocal = new FastThreadLocal<String>();
        final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                threadLocal.set("1");
                threadLocal.remove();
                threadLocal.set("2");
                threadLocal.remove();
            }
        };

        final int sizeWhenStart = ObjectCleaner.getLiveSetCount();
        Thread thread = new Thread(runnable);
        thread.start();
        thread.join();

        assertEquals(1, ObjectCleaner.getLiveSetCount() - sizeWhenStart);

        Thread thread2 = new Thread(runnable);
        thread2.start();
        thread2.join();

        assertEquals(2, ObjectCleaner.getLiveSetCount() - sizeWhenStart);
    }

    @Test
    public void testMultipleSetRemove_multipleThreadLocal() throws Exception {
        final FastThreadLocal<String> threadLocal = new FastThreadLocal<String>();
        final FastThreadLocal<String> threadLocal2 = new FastThreadLocal<String>();
        final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                threadLocal.set("1");
                threadLocal.remove();
                threadLocal.set("2");
                threadLocal.remove();
                threadLocal2.set("1");
                threadLocal2.remove();
                threadLocal2.set("2");
                threadLocal2.remove();
            }
        };

        final int sizeWhenStart = ObjectCleaner.getLiveSetCount();
        Thread thread = new Thread(runnable);
        thread.start();
        thread.join();

        assertEquals(2, ObjectCleaner.getLiveSetCount() - sizeWhenStart);

        Thread thread2 = new Thread(runnable);
        thread2.start();
        thread2.join();

        assertEquals(4, ObjectCleaner.getLiveSetCount() - sizeWhenStart);
    }

    @Test(timeout = 4000)
    public void testOnRemoveCalledForFastThreadLocalGet() throws Exception {
        testOnRemoveCalled(true, true);
    }

    @Test(timeout = 4000)
    public void testOnRemoveCalledForNonFastThreadLocalGet() throws Exception {
        testOnRemoveCalled(false, true);
    }

    @Test(timeout = 4000)
    public void testOnRemoveCalledForFastThreadLocalSet() throws Exception {
        testOnRemoveCalled(true, false);
    }

    @Test(timeout = 4000)
    public void testOnRemoveCalledForNonFastThreadLocalSet() throws Exception {
        testOnRemoveCalled(false, false);
    }

    private static void testOnRemoveCalled(boolean fastThreadLocal, final boolean callGet) throws Exception {

        final TestFastThreadLocal threadLocal = new TestFastThreadLocal();
        final TestFastThreadLocal threadLocal2 = new TestFastThreadLocal();

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if (callGet) {
                    assertEquals(Thread.currentThread().getName(), threadLocal.get());
                    assertEquals(Thread.currentThread().getName(), threadLocal2.get());
                } else {
                    threadLocal.set(Thread.currentThread().getName());
                    threadLocal2.set(Thread.currentThread().getName());
                }
            }
        };
        Thread thread = fastThreadLocal ? new FastThreadLocalThread(runnable) : new Thread(runnable);
        thread.start();
        thread.join();

        String threadName = thread.getName();

        // Null this out so it can be collected
        thread = null;

        // Loop until onRemoval(...) was called. This will fail the test if this not works due a timeout.
        while (threadLocal.onRemovalCalled.get() == null || threadLocal2.onRemovalCalled.get() == null) {
            System.gc();
            System.runFinalization();
            Thread.sleep(50);
        }

        assertEquals(threadName, threadLocal.onRemovalCalled.get());
        assertEquals(threadName, threadLocal2.onRemovalCalled.get());
    }

    private static final class TestFastThreadLocal extends FastThreadLocal<String> {

        final AtomicReference<String> onRemovalCalled = new AtomicReference<String>();

        @Override
        protected String initialValue() throws Exception {
            return Thread.currentThread().getName();
        }

        @Override
        protected void onRemoval(String value) throws Exception {
            onRemovalCalled.set(value);
        }
    }
}
