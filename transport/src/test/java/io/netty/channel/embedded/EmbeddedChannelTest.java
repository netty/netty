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
package io.netty.channel.embedded;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.ScheduledFuture;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class EmbeddedChannelTest {

    @Test
    public void testConstructWithChannelInitializer() {
        final Integer first = 1;
        final Integer second = 2;

        final ChannelHandler handler = new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                ctx.fireChannelRead(first);
                ctx.fireChannelRead(second);
            }
        };
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ch.pipeline().addLast(handler);
            }
        });
        ChannelPipeline pipeline = channel.pipeline();
        Assert.assertSame(handler, pipeline.firstContext().handler());
        Assert.assertTrue(channel.writeInbound(3));
        Assert.assertTrue(channel.finish());
        Assert.assertSame(first, channel.readInbound());
        Assert.assertSame(second, channel.readInbound());
        Assert.assertNull(channel.readInbound());
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void testScheduling() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        final CountDownLatch latch = new CountDownLatch(2);
        ScheduledFuture future = ch.eventLoop().schedule(new Runnable() {
            @Override
            public void run() {
                latch.countDown();
            }
        }, 1, TimeUnit.SECONDS);
        future.addListener(new FutureListener() {
            @Override
            public void operationComplete(Future future) throws Exception {
                latch.countDown();
            }
        });
        long next = ch.runScheduledPendingTasks();
        Assert.assertTrue(next > 0);
        // Sleep for the nanoseconds but also give extra 50ms as the clock my not be very precise and so fail the test
        // otherwise.
        Thread.sleep(TimeUnit.NANOSECONDS.toMillis(next) + 50);
        Assert.assertEquals(-1, ch.runScheduledPendingTasks());
        latch.await();
    }

    @Test
    public void testScheduledCancelled() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
        ScheduledFuture<?> future = ch.eventLoop().schedule(new Runnable() {
            @Override
            public void run() { }
        }, 1, TimeUnit.DAYS);
        ch.finish();
        Assert.assertTrue(future.isCancelled());
    }

    @Test(timeout = 3000)
    public void testHandlerAddedExecutedInEventLoop() throws Throwable {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
        final ChannelHandler handler = new ChannelHandlerAdapter() {
            @Override
            public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
                try {
                    Assert.assertTrue(ctx.executor().inEventLoop());
                } catch (Throwable cause) {
                    error.set(cause);
                } finally {
                    latch.countDown();
                }
            }
        };
        EmbeddedChannel channel = new EmbeddedChannel(handler);
        Assert.assertFalse(channel.finish());
        latch.await();
        Throwable cause = error.get();
        if (cause != null) {
            throw cause;
        }
    }

    @Test
    public void testConstructWithOutHandler() {
        EmbeddedChannel channel = new EmbeddedChannel();
        Assert.assertTrue(channel.writeInbound(1));
        Assert.assertTrue(channel.writeOutbound(2));
        Assert.assertTrue(channel.finish());
        Assert.assertSame(1, channel.readInbound());
        Assert.assertNull(channel.readInbound());
        Assert.assertSame(2, channel.readOutbound());
        Assert.assertNull(channel.readOutbound());
    }

    @Test
    public void testConstructWithChannelId() {
        ChannelId channelId = new CustomChannelId(1);
        EmbeddedChannel channel = new EmbeddedChannel(channelId);
        Assert.assertSame(channelId, channel.id());
    }

    // See https://github.com/netty/netty/issues/4316.
    @Test(timeout = 2000)
    public void testFireChannelInactiveAndUnregisteredOnClose() throws InterruptedException {
        testFireChannelInactiveAndUnregistered(new Action() {
            @Override
            public ChannelFuture doRun(Channel channel) {
                return channel.close();
            }
        });
        testFireChannelInactiveAndUnregistered(new Action() {
            @Override
            public ChannelFuture doRun(Channel channel) {
                return channel.close(channel.newPromise());
            }
        });
    }

    @Test(timeout = 2000)
    public void testFireChannelInactiveAndUnregisteredOnDisconnect() throws InterruptedException {
        testFireChannelInactiveAndUnregistered(new Action() {
            @Override
            public ChannelFuture doRun(Channel channel) {
                return channel.disconnect();
            }
        });

        testFireChannelInactiveAndUnregistered(new Action() {
            @Override
            public ChannelFuture doRun(Channel channel) {
                return channel.disconnect(channel.newPromise());
            }
        });
    }

    private static void testFireChannelInactiveAndUnregistered(Action action) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(3);
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter() {
            @Override
            public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                latch.countDown();
                ctx.executor().execute(new Runnable() {
                    @Override
                    public void run() {
                        // Should be executed.
                        latch.countDown();
                    }
                });
            }

            @Override
            public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
                latch.countDown();
            }
        });
        action.doRun(channel).syncUninterruptibly();
        latch.await();
    }

    private interface Action {
        ChannelFuture doRun(Channel channel);
    }
}
