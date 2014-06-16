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

package io.netty.util;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

public class ThreadDeathWatcherTest {

    @Test(timeout = 10000)
    public void testSimple() throws Exception {
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
}
