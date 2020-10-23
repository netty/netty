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

package io.netty.util;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ThreadDeathWatcherTest {

    @Test(timeout = 10000)
    public void testWatch() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final Thread t = new Thread() {
            @Override
            public void run() {
                for (;;) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ignore) {
                        break;
                    }
                }
            }
        };

        final Runnable task = new Runnable() {
            @Override
            public void run() {
                if (!t.isAlive()) {
                    latch.countDown();
                }
            }
        };

        try {
            ThreadDeathWatcher.watch(t, task);
            fail("must reject to watch a non-alive thread.");
        } catch (IllegalArgumentException e) {
            // expected
        }

        t.start();
        ThreadDeathWatcher.watch(t, task);

        // As long as the thread is alive, the task should not run.
        assertThat(latch.await(750, TimeUnit.MILLISECONDS), is(false));

        // Interrupt the thread to terminate it.
        t.interrupt();

        // The task must be run on termination.
        latch.await();
    }

    @Test(timeout = 10000)
    public void testUnwatch() throws Exception {
        final AtomicBoolean run = new AtomicBoolean();
        final Thread t = new Thread() {
            @Override
            public void run() {
                for (;;) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ignore) {
                        break;
                    }
                }
            }
        };

        final Runnable task = new Runnable() {
            @Override
            public void run() {
                run.set(true);
            }
        };

        t.start();

        // Watch and then unwatch.
        ThreadDeathWatcher.watch(t, task);
        ThreadDeathWatcher.unwatch(t, task);

        // Interrupt the thread to terminate it.
        t.interrupt();

        // Wait until the thread dies.
        t.join();

        // Wait until the watcher thread terminates itself.
        assertThat(ThreadDeathWatcher.awaitInactivity(Long.MAX_VALUE, TimeUnit.SECONDS), is(true));

        // And the task should not run.
        assertThat(run.get(), is(false));
    }

    @Test(timeout = 2000)
    public void testThreadGroup() throws InterruptedException {
        final ThreadGroup group = new ThreadGroup("group");
        final AtomicReference<ThreadGroup> capturedGroup = new AtomicReference<ThreadGroup>();
        final Thread thread = new Thread(group, new Runnable() {
            @Override
            public void run() {
                final Thread t = ThreadDeathWatcher.threadFactory.newThread(new Runnable() {
                    @Override
                    public void run() {
                    }
                });
                capturedGroup.set(t.getThreadGroup());
            }
        });
        thread.start();
        thread.join();

        assertEquals(group, capturedGroup.get());
    }
}
