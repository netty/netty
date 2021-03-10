/*
 * Copyright 2013 The Netty Project
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

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

public class GlobalEventExecutorTest {

    private static final GlobalEventExecutor e = GlobalEventExecutor.INSTANCE;

    @Before
    public void setUp() throws Exception {
        // Wait until the global executor is stopped (just in case there is a task running due to previous test cases)
        for (;;) {
            if (e.thread == null || !e.thread.isAlive()) {
                break;
            }

            Thread.sleep(50);
        }
    }

    @Test(timeout = 5000)
    public void testAutomaticStartStop() throws Exception {
        final TestRunnable task = new TestRunnable(500);
        e.execute(task);

        // Ensure the new thread has started.
        Thread thread = e.thread;
        assertThat(thread, is(not(nullValue())));
        assertThat(thread.isAlive(), is(true));

        thread.join();
        assertThat(task.ran.get(), is(true));

        // Ensure another new thread starts again.
        task.ran.set(false);
        e.execute(task);
        assertThat(e.thread, not(sameInstance(thread)));
        thread = e.thread;

        thread.join();

        assertThat(task.ran.get(), is(true));
    }

    @Test(timeout = 5000)
    public void testScheduledTasks() throws Exception {
        TestRunnable task = new TestRunnable(0);
        ScheduledFuture<?> f = e.schedule(task, 1500, TimeUnit.MILLISECONDS);
        f.sync();
        assertThat(task.ran.get(), is(true));

        // Ensure the thread is still running.
        Thread thread = e.thread;
        assertThat(thread, is(not(nullValue())));
        assertThat(thread.isAlive(), is(true));

        thread.join();
    }

    // ensure that when a task submission causes a new thread to be created, the thread inherits the thread group of the
    // submitting thread
    @Test(timeout = 2000)
    public void testThreadGroup() throws InterruptedException {
        final ThreadGroup group = new ThreadGroup("group");
        final AtomicReference<ThreadGroup> capturedGroup = new AtomicReference<ThreadGroup>();
        final Thread thread = new Thread(group, new Runnable() {
            @Override
            public void run() {
                final Thread t = e.threadFactory.newThread(new Runnable() {
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

    @Test(timeout = 5000)
    public void testTakeTask() throws Exception {
        //add task
        TestRunnable beforeTask = new TestRunnable(0);
        e.execute(beforeTask);

        //add scheduled task
        TestRunnable scheduledTask = new TestRunnable(0);
        ScheduledFuture<?> f = e.schedule(scheduledTask , 1500, TimeUnit.MILLISECONDS);

        //add task
        TestRunnable afterTask = new TestRunnable(0);
        e.execute(afterTask);

        f.sync();

        assertThat(beforeTask.ran.get(), is(true));
        assertThat(scheduledTask.ran.get(), is(true));
        assertThat(afterTask.ran.get(), is(true));
    }

    @Test(timeout = 5000)
    public void testTakeTaskAlwaysHasTask() throws Exception {
        //for https://github.com/netty/netty/issues/1614
        //add scheduled task
        TestRunnable t = new TestRunnable(0);
        final ScheduledFuture<?> f = e.schedule(t, 1500, TimeUnit.MILLISECONDS);

        //ensure always has at least one task in taskQueue
        //check if scheduled tasks are triggered
        e.execute(new Runnable() {
            @Override
            public void run() {
                if (!f.isDone()) {
                    e.execute(this);
                }
            }
        });

        f.sync();

        assertThat(t.ran.get(), is(true));
    }

    private static final class TestRunnable implements Runnable {
        final AtomicBoolean ran = new AtomicBoolean();
        final long delay;

        TestRunnable(long delay) {
            this.delay = delay;
        }

        @Override
        public void run() {
            try {
                Thread.sleep(delay);
                ran.set(true);
            } catch (InterruptedException ignored) {
                // Ignore
            }
        }
    }
}
