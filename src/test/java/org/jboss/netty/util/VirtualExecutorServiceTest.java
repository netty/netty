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
package org.jboss.netty.util;

import static junit.framework.Assert.*;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class VirtualExecutorServiceTest {

    // https://github.com/netty/netty/issues/906
    @Test
    public void awaitTerminationTest() throws InterruptedException {
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch latch = new CountDownLatch(1);
        Runnable task = new Runnable() {
            public void run() {
                try {
                    started.countDown();
                    latch.await();
                } catch (InterruptedException e) {
                    // ignore
                }
            }
        };
        ExecutorService executorService = Executors.newCachedThreadPool();
        VirtualExecutorService virtualExecutorService = new VirtualExecutorService(executorService);
        virtualExecutorService.execute(task);

        started.await();

        virtualExecutorService.shutdown();
        assertFalse(virtualExecutorService.awaitTermination(1, TimeUnit.SECONDS));
        latch.countDown();
        assertTrue(virtualExecutorService.awaitTermination(1, TimeUnit.SECONDS));

    }
}
