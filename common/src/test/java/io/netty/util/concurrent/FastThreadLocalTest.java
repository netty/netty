/*
 * Copyright 2014 The Netty Project
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

package io.netty.util.concurrent;

import io.netty.util.internal.InternalThreadLocalMap;
import io.netty.util.internal.ObjectCleaner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FastThreadLocalTest {
    @BeforeEach
    public void setUp() {
        FastThreadLocal.removeAll();
        assertThat(FastThreadLocal.size(), is(0));
    }

    @Test
    public void testGetIfExists() {
        FastThreadLocal<Boolean> threadLocal = new FastThreadLocal<Boolean>() {
            @Override
            protected Boolean initialValue() {
                return Boolean.TRUE;
            }
        };

        assertNull(threadLocal.getIfExists());
        assertTrue(threadLocal.get());
        assertTrue(threadLocal.getIfExists());

        FastThreadLocal.removeAll();
        assertNull(threadLocal.getIfExists());
    }

    @Test
    @Timeout(value = 10000, unit = TimeUnit.MILLISECONDS)
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

    @Test
    @Timeout(value = 10000, unit = TimeUnit.MILLISECONDS)
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

        assertEquals(0, ObjectCleaner.getLiveSetCount() - sizeWhenStart);

        Thread thread2 = new Thread(runnable);
        thread2.start();
        thread2.join();

        assertEquals(0, ObjectCleaner.getLiveSetCount() - sizeWhenStart);
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

        assertEquals(0, ObjectCleaner.getLiveSetCount() - sizeWhenStart);

        Thread thread2 = new Thread(runnable);
        thread2.start();
        thread2.join();

        assertEquals(0, ObjectCleaner.getLiveSetCount() - sizeWhenStart);
    }

    @Test
    @Timeout(value = 4000, unit = TimeUnit.MILLISECONDS)
    public void testOnRemoveCalledForFastThreadLocalGet() throws Exception {
        testOnRemoveCalled(true, true);
    }

    @Disabled("onRemoval(...) not called with non FastThreadLocal")
    @Test
    @Timeout(value = 4000, unit = TimeUnit.MILLISECONDS)
    public void testOnRemoveCalledForNonFastThreadLocalGet() throws Exception {
        testOnRemoveCalled(false, true);
    }

    @Test
    @Timeout(value = 4000, unit = TimeUnit.MILLISECONDS)
    public void testOnRemoveCalledForFastThreadLocalSet() throws Exception {
        testOnRemoveCalled(true, false);
    }

    @Disabled("onRemoval(...) not called with non FastThreadLocal")
    @Test
    @Timeout(value = 4000, unit = TimeUnit.MILLISECONDS)
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

    @Test
    public void testConstructionWithIndex() throws Exception {
        int ARRAY_LIST_CAPACITY_MAX_SIZE = Integer.MAX_VALUE - 8;
        Field nextIndexField =
                InternalThreadLocalMap.class.getDeclaredField("nextIndex");
        nextIndexField.setAccessible(true);
        AtomicInteger nextIndex = (AtomicInteger) nextIndexField.get(AtomicInteger.class);
        int nextIndex_before = nextIndex.get();
        final AtomicReference<Throwable> throwable = new AtomicReference<Throwable>();
        try {
            while (nextIndex.get() < ARRAY_LIST_CAPACITY_MAX_SIZE) {
                new FastThreadLocal<Boolean>();
            }
            assertEquals(ARRAY_LIST_CAPACITY_MAX_SIZE - 1, InternalThreadLocalMap.lastVariableIndex());
            try {
                new FastThreadLocal<Boolean>();
            } catch (Throwable t) {
                throwable.set(t);
            } finally {
                // Assert the max index cannot greater than (ARRAY_LIST_CAPACITY_MAX_SIZE - 1).
                assertThat(throwable.get(), is(instanceOf(IllegalStateException.class)));
                // Assert the index was reset to ARRAY_LIST_CAPACITY_MAX_SIZE
                // after it reaches ARRAY_LIST_CAPACITY_MAX_SIZE.
                assertEquals(ARRAY_LIST_CAPACITY_MAX_SIZE - 1, InternalThreadLocalMap.lastVariableIndex());
            }
        } finally {
            // Restore the index.
            nextIndex.set(nextIndex_before);
        }
    }

    @EnabledIfEnvironmentVariable(named = "CI", matches = "true", disabledReason = "" +
            "This deliberately causes OutOfMemoryErrors, for which heap dumps are automatically generated. " +
            "To avoid confusion, wasted time investigating heap dumps, and to avoid heap dumps accidentally " +
            "getting committed to the Git repository, we should only enable this test when running in a CI " +
            "environment. We make this check by assuming a 'CI' environment variable. " +
            "This matches what Github Actions is doing for us currently.")
    @Test
    public void testInternalThreadLocalMapExpand() throws Exception {
        final AtomicReference<Throwable> throwable = new AtomicReference<Throwable>();
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                int expand_threshold = 1 << 30;
                try {
                    InternalThreadLocalMap.get().setIndexedVariable(expand_threshold, null);
                } catch (Throwable t) {
                    throwable.set(t);
                }
            }
        };
        FastThreadLocalThread fastThreadLocalThread = new FastThreadLocalThread(runnable);
        fastThreadLocalThread.start();
        fastThreadLocalThread.join();
        // assert the expanded size is not overflowed to negative value
        assertThat(throwable.get(), is(not(instanceOf(NegativeArraySizeException.class))));
    }

    @Test
    public void testFastThreadLocalSize() throws Exception {
        int originSize = FastThreadLocal.size();
        assertTrue(originSize >= 0);

        InternalThreadLocalMap.get();
        assertEquals(originSize, FastThreadLocal.size());

        new FastThreadLocal<Boolean>();
        assertEquals(originSize, FastThreadLocal.size());

        FastThreadLocal<Boolean> fst2 = new FastThreadLocal<Boolean>();
        fst2.get();
        assertEquals(1 + originSize, FastThreadLocal.size());

        FastThreadLocal<Boolean> fst3 = new FastThreadLocal<Boolean>();
        fst3.set(null);
        assertEquals(2 + originSize, FastThreadLocal.size());

        FastThreadLocal<Boolean> fst4 = new FastThreadLocal<Boolean>();
        fst4.set(Boolean.TRUE);
        assertEquals(3 + originSize, FastThreadLocal.size());

        fst4.set(Boolean.TRUE);
        assertEquals(3 + originSize, FastThreadLocal.size());

        fst4.remove();
        assertEquals(2 + originSize, FastThreadLocal.size());

        FastThreadLocal.removeAll();
        assertEquals(0, FastThreadLocal.size());
    }

    @Test
    public void testFastThreadLocalInitialValueWithUnset() throws Exception {
        final AtomicReference<Throwable> throwable = new AtomicReference<Throwable>();
        final FastThreadLocal fst = new FastThreadLocal() {
            @Override
            protected Object initialValue() throws Exception {
                return InternalThreadLocalMap.UNSET;
            }
        };
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    fst.get();
                } catch (Throwable t) {
                    throwable.set(t);
                }
            }
        };
        FastThreadLocalThread fastThreadLocalThread = new FastThreadLocalThread(runnable);
        fastThreadLocalThread.start();
        fastThreadLocalThread.join();
        assertThat(throwable.get(), is(instanceOf(IllegalArgumentException.class)));
    }
}
