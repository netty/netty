/*
 * Copyright 2019 The Netty Project
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

package io.netty.channel.pool;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.DefaultEventLoop;
import io.netty.channel.EventLoop;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.util.concurrent.Future;
import org.junit.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.*;

/**
 * This is a test case for the deadlock scenario described in https://github.com/netty/netty/issues/8238.
 */
public class FixedChannelPoolMapDeadlockTest {

    private static final NoopHandler NOOP_HANDLER = new NoopHandler();

    @Test
    public void testDeadlockOnAcquire() throws Exception {

        final EventLoop threadA1 = new DefaultEventLoop();
        final Bootstrap bootstrapA1 = new Bootstrap()
                .channel(LocalChannel.class).group(threadA1).localAddress(new LocalAddress("A1"));
        final EventLoop threadA2 = new DefaultEventLoop();
        final Bootstrap bootstrapA2 = new Bootstrap()
                .channel(LocalChannel.class).group(threadA2).localAddress(new LocalAddress("A2"));
        final EventLoop threadB1 = new DefaultEventLoop();
        final Bootstrap bootstrapB1 = new Bootstrap()
                .channel(LocalChannel.class).group(threadB1).localAddress(new LocalAddress("B1"));
        final EventLoop threadB2 = new DefaultEventLoop();
        final Bootstrap bootstrapB2 = new Bootstrap()
                .channel(LocalChannel.class).group(threadB2).localAddress(new LocalAddress("B2"));

        final FixedChannelPool poolA1 = new FixedChannelPool(bootstrapA1, NOOP_HANDLER, 1);
        final FixedChannelPool poolA2 = new FixedChannelPool(bootstrapB2, NOOP_HANDLER, 1);
        final FixedChannelPool poolB1 = new FixedChannelPool(bootstrapB1, NOOP_HANDLER, 1);
        final FixedChannelPool poolB2 = new FixedChannelPool(bootstrapA2, NOOP_HANDLER, 1);

        // Synchronize threads on these barriers to ensure order of execution, first wait until each thread is inside
        // the newPool callbak, then hold the two threads that should lose the match until the first two returns, then
        // release them to test if they deadlock when trying to release their pools on each other's threads.
        final CyclicBarrier arrivalBarrier = new CyclicBarrier(4);
        final CyclicBarrier releaseBarrier = new CyclicBarrier(3);

        final AbstractChannelPoolMap<String, FixedChannelPool> channelPoolMap =
                new AbstractChannelPoolMap<String, FixedChannelPool>() {

            @Override
            protected FixedChannelPool newPool(String key) {

                // Thread A1 gets a new pool on eventexecutor thread A1 (anywhere but A2 or B2)
                // Thread B1 gets a new pool on eventexecutor thread B1 (anywhere but A2 or B2)
                // Thread A2 gets a new pool on eventexecutor thread B2
                // Thread B2 gets a new pool on eventexecutor thread A2

                if ("A".equals(key)) {
                    if (threadA1.inEventLoop()) {
                        // Thread A1 gets pool A with thread A1
                        await(arrivalBarrier);
                        return poolA1;
                    } else if (threadA2.inEventLoop()) {
                        // Thread A2 gets pool A with thread B2, but only after A1 won
                        await(arrivalBarrier);
                        await(releaseBarrier);
                        return poolA2;
                    }
                } else if ("B".equals(key)) {
                    if (threadB1.inEventLoop()) {
                        // Thread B1 gets pool with thread B1
                        await(arrivalBarrier);
                        return poolB1;
                    } else if (threadB2.inEventLoop()) {
                        // Thread B2 gets pool with thread A2
                        await(arrivalBarrier);
                        await(releaseBarrier);
                        return poolB2;
                    }
                }
                throw new AssertionError("Unexpected key=" + key + " or thread="
                                         + Thread.currentThread().getName());
            }
        };

        // Thread A1 calls ChannelPoolMap.get(A)
        // Thread A2 calls ChannelPoolMap.get(A)
        // Thread B1 calls ChannelPoolMap.get(B)
        // Thread B2 calls ChannelPoolMap.get(B)

        Future<FixedChannelPool> futureA1 = threadA1.submit(new Callable<FixedChannelPool>() {
            @Override
            public FixedChannelPool call() throws Exception {
                return channelPoolMap.get("A");
            }
        });

        Future<FixedChannelPool> futureA2 = threadA2.submit(new Callable<FixedChannelPool>() {
            @Override
            public FixedChannelPool call() throws Exception {
                return channelPoolMap.get("A");
            }
        });

        Future<FixedChannelPool> futureB1 = threadB1.submit(new Callable<FixedChannelPool>() {
            @Override
            public FixedChannelPool call() throws Exception {
                return channelPoolMap.get("B");
            }
        });

        Future<FixedChannelPool> futureB2 = threadB2.submit(new Callable<FixedChannelPool>() {
            @Override
            public FixedChannelPool call() throws Exception {
                return channelPoolMap.get("B");
            }
        });

        // Thread A1 succeeds on updating the map and moves on
        // Thread B1 succeeds on updating the map and moves on
        // These should always succeed and return with new pools
        try {
            assertSame(poolA1, futureA1.get(1, TimeUnit.SECONDS));
            assertSame(poolB1, futureB1.get(1, TimeUnit.SECONDS));
        } catch (Exception e) {
            shutdown(threadA1, threadA2, threadB1, threadB2);
            throw e;
        }

        // Now release the other two threads which at this point lost the race and will try to clean up the acquired
        // pools. The expected scenario is that both pools close, in case of a deadlock they will hang.
        await(releaseBarrier);

        // Thread A2 fails to update the map and submits close to thread B2
        // Thread B2 fails to update the map and submits close to thread A2
        // If the close is blocking, then these calls will time out as the threads are waiting for each other
        // If the close is not blocking, then the previously created pools will be returned
        try {
            assertSame(poolA1, futureA2.get(1, TimeUnit.SECONDS));
            assertSame(poolB1, futureB2.get(1, TimeUnit.SECONDS));
        } catch (TimeoutException e) {
            // Fail the test on timeout to distinguish from other errors
            throw new AssertionError(e);
        } finally {
            poolA1.close();
            poolA2.close();
            poolB1.close();
            poolB2.close();
            channelPoolMap.close();
            shutdown(threadA1, threadA2, threadB1, threadB2);
        }
    }

    @Test
    public void testDeadlockOnRemove() throws Exception {

        final EventLoop thread1 = new DefaultEventLoop();
        final Bootstrap bootstrap1 = new Bootstrap()
                .channel(LocalChannel.class).group(thread1).localAddress(new LocalAddress("#1"));
        final EventLoop thread2 = new DefaultEventLoop();
        final Bootstrap bootstrap2 = new Bootstrap()
                .channel(LocalChannel.class).group(thread2).localAddress(new LocalAddress("#2"));

        // pool1 runs on thread2, pool2 runs on thread1
        final FixedChannelPool pool1 = new FixedChannelPool(bootstrap2, NOOP_HANDLER, 1);
        final FixedChannelPool pool2 = new FixedChannelPool(bootstrap1, NOOP_HANDLER, 1);

        final AbstractChannelPoolMap<String, FixedChannelPool> channelPoolMap =
                new AbstractChannelPoolMap<String, FixedChannelPool>() {

            @Override
            protected FixedChannelPool newPool(String key) {
                if ("#1".equals(key)) {
                    return pool1;
                } else if ("#2".equals(key)) {
                    return pool2;
                } else {
                    throw new AssertionError("Unexpected key=" + key);
                }
            }
        };

        assertSame(pool1, channelPoolMap.get("#1"));
        assertSame(pool2, channelPoolMap.get("#2"));

        // thread1 tries to remove pool1 which is running on thread2
        // thread2 tries to remove pool2 which is running on thread1

        final CyclicBarrier barrier = new CyclicBarrier(2);

        Future<?> future1 = thread1.submit(new Runnable() {
            @Override
            public void run() {
                await(barrier);
                channelPoolMap.remove("#1");
            }
        });

        Future<?> future2 = thread2.submit(new Runnable() {
            @Override
            public void run() {
                await(barrier);
                channelPoolMap.remove("#2");
            }
        });

        // A blocking close on remove will cause a deadlock here and the test will time out
        try {
            future1.get(1, TimeUnit.SECONDS);
            future2.get(1, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            // Fail the test on timeout to distinguish from other errors
            throw new AssertionError(e);
        } finally {
            pool1.close();
            pool2.close();
            channelPoolMap.close();
            shutdown(thread1, thread2);
        }
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(1, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void shutdown(EventLoop... eventLoops) {
        for (EventLoop eventLoop : eventLoops) {
            eventLoop.shutdownGracefully(0, 0, TimeUnit.SECONDS);
        }
    }

    private static class NoopHandler extends AbstractChannelPoolHandler {
        @Override
        public void channelCreated(Channel ch) throws Exception {
            // noop
        }
    };
}
