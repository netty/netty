/*
 * Copyright 2018 The Netty Project
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
package io.netty.handler.ipfilter;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.internal.SocketUtils;
import org.junit.Assert;
import org.junit.Test;

import java.net.SocketAddress;
import java.util.concurrent.*;

public class UniqueIpFilterTest {

    @Test
    public void testUniqueIpFilterHandler() throws ExecutionException, InterruptedException {
        final CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 10000; round++) {
                final UniqueIpFilter handler = new UniqueIpFilter();
                Future<EmbeddedChannel> future1 = submit(handler, barrier, executorService);
                Future<EmbeddedChannel> future2 = submit(handler, barrier, executorService);
                EmbeddedChannel channel1 = future1.get();
                EmbeddedChannel channel2 = future2.get();
                Assert.assertTrue(channel1.isActive() || channel2.isActive());
                Assert.assertFalse(channel1.isActive() && channel2.isActive());

                barrier.reset();
                channel1.close().await();
                channel2.close().await();
            }
        } finally {
            executorService.shutdown();
        }
    }

    private static Future<EmbeddedChannel> submit(final UniqueIpFilter handler, final CyclicBarrier barrier, ExecutorService executorService) {
        return executorService.submit(new Callable<EmbeddedChannel>() {
            @Override
            public EmbeddedChannel call() throws Exception {
                barrier.await();
                return new EmbeddedChannel(handler) {
                    @Override
                    protected SocketAddress remoteAddress0() {
                        return isActive() ? SocketUtils.socketAddress("91.92.93.1", 5421) : null;
                    }
                };
            }
        });
    }

}
