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

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.*;

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

    /**
     * Make sure threads created by the {@link DefaultExecutorServiceFactory} and {@link DefaultThreadFactory}
     * implement the {@link FastThreadLocalAccess} interface.
     */
    @Test
    public void testIsFastThreadLocalThread() {
        ExecutorServiceFactory executorServiceFactory = new DefaultExecutorServiceFactory(FastThreadLocalTest.class);
        int parallelism = Runtime.getRuntime().availableProcessors() * 2;

        Executor executor = executorServiceFactory.newExecutorService(parallelism);
        // submit a "high" number of tasks, to get a good chance to touch every thread.
        for (int i = 0; i < parallelism * 100; i++) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    assertTrue(Thread.currentThread() instanceof FastThreadLocalAccess);
                }
            });
        }

        ThreadFactory threadFactory = new DefaultThreadFactory(FastThreadLocalTest.class);
        Thread t = threadFactory.newThread(new Runnable() {
            @Override
            public void run() {
            }
        });

        assertTrue(t instanceof FastThreadLocalAccess);
    }
}
