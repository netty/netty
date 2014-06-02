/*
 * Copyright 2013 The Netty Project
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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

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

    @Test
    public void testAutomaticStartStop() throws Exception {
        final TestRunnable task = new TestRunnable(500);
        e.execute(task);

        // Ensure the new thread has started.
        Thread thread = e.thread;
        assertThat(thread, is(not(nullValue())));
        assertThat(thread.isAlive(), is(true));

        Thread.sleep(1500);

        // Ensure the thread stopped itself after running the task.
        assertThat(thread.isAlive(), is(false));
        assertThat(task.ran.get(), is(true));
        assertThat(e.thread, sameInstance(thread));

        // Ensure another new thread starts again.
        task.ran.set(false);
        e.execute(task);
        assertThat(e.thread, not(sameInstance(thread)));
        thread = e.thread;

        Thread.sleep(1500);

        // Ensure the thread stopped itself after running the task.
        assertThat(thread.isAlive(), is(false));
        assertThat(task.ran.get(), is(true));
        assertThat(e.thread, sameInstance(thread));
    }

    @Test
    public void testScheduledTasks() throws Exception {
        TestRunnable task = new TestRunnable(0);
        ScheduledFuture<?> f = e.schedule(task, 1500, TimeUnit.MILLISECONDS);
        f.sync();
        assertThat(task.ran.get(), is(true));

        // Ensure the thread is still running.
        Thread thread = e.thread;
        assertThat(thread, is(not(nullValue())));
        assertThat(thread.isAlive(), is(true));

        Thread.sleep(1500);

        // Now it should be stopped.
        assertThat(thread.isAlive(), is(false));
        assertThat(e.thread, sameInstance(thread));
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
