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
package io.netty.channel;

import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.SingleThreadEventExecutor;
import io.netty.util.concurrent.TaskScheduler;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class SingleThreadEventExecutorTest {

    @Test
    public void test() throws Exception {
        final Task task = new Task();
        final CountDownLatch latch = new CountDownLatch(1);
        final CountDownLatch runLatch = new CountDownLatch(1);
        final SingleThreadEventExecutor executor = new SingleThreadEventExecutor(null, Executors.defaultThreadFactory(), new TaskScheduler(Executors.defaultThreadFactory()), 10, TimeUnit.MILLISECONDS.toNanos(500)) {
            @Override
            protected void run() {
                try {
                    latch.await();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                int count = 0;
                for (;;) {
                    if (++count < 2) {
                        Assert.assertEquals(TaskRunState.TOO_LONG_EXECUTED, runAllTasks());
                    } else {
                        task.stop = true;
                        Assert.assertEquals(TaskRunState.TASK_EXECUTED, runAllTasks());
                        runLatch.countDown();
                    }

                }
 
            }
        };
        task.executor = executor;
        executor.execute(task);
        latch.countDown();
        Assert.assertTrue(runLatch.await(5, TimeUnit.SECONDS));
    }

    private final static class Task implements Runnable {
        EventExecutor executor;
        boolean stop;
        @Override
        public void run() {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    if (!stop) {
                        executor.execute(this);
                    }
                }
            });
        }
    }
}
