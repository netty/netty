/*
 * Copyright 2015 The Netty Project
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
package io.netty.channel.pool;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import io.netty.channel.pool.FixedChannelPool.AcquireTimeoutAction;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static io.netty.channel.pool.ChannelPoolTestUtils.getLocalAddrId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FixedChannelPoolTest {
    private static EventLoopGroup group;

    @BeforeAll
    public static void createEventLoop() {
        group = new DefaultEventLoopGroup();
    }

    @AfterAll
    public static void destroyEventLoop() {
        if (group != null) {
            group.shutdownGracefully();
        }
    }

    @Test
    public void testAcquire() throws Exception {
        LocalAddress addr = new LocalAddress(getLocalAddrId());
        Bootstrap cb = new Bootstrap();
        cb.remoteAddress(addr);
        cb.group(group)
          .channel(LocalChannel.class);

        ServerBootstrap sb = new ServerBootstrap();
        sb.group(group)
          .channel(LocalServerChannel.class)
          .childHandler(new ChannelInitializer<LocalChannel>() {
              @Override
              public void initChannel(LocalChannel ch) throws Exception {
                  ch.pipeline().addLast(new ChannelInboundHandlerAdapter());
              }
          });

        // Start server
        Channel sc = sb.bind(addr).syncUninterruptibly().channel();
        CountingChannelPoolHandler handler = new CountingChannelPoolHandler();

        ChannelPool pool = new FixedChannelPool(cb, handler, 1, Integer.MAX_VALUE);

        Channel channel = pool.acquire().syncUninterruptibly().getNow();
        Future<Channel> future = pool.acquire();
        assertFalse(future.isDone());

        pool.release(channel).syncUninterruptibly();
        assertTrue(future.await(1, TimeUnit.SECONDS));

        Channel channel2 = future.getNow();
        assertSame(channel, channel2);
        assertEquals(1, handler.channelCount());

        assertEquals(2, handler.acquiredCount());
        assertEquals(1, handler.releasedCount());

        sc.close().syncUninterruptibly();
        channel2.close().syncUninterruptibly();
        pool.close();
    }

    @Test
    public void testAcquireTimeout() throws Exception {
        testAcquireTimeout(500);
    }

    @Test
    public void testAcquireWithZeroTimeout() throws Exception {
        testAcquireTimeout(0);
    }

    private static void testAcquireTimeout(long timeoutMillis) throws Exception {
        LocalAddress addr = new LocalAddress(getLocalAddrId());
        Bootstrap cb = new Bootstrap();
        cb.remoteAddress(addr);
        cb.group(group)
          .channel(LocalChannel.class);

        ServerBootstrap sb = new ServerBootstrap();
        sb.group(group)
          .channel(LocalServerChannel.class)
          .childHandler(new ChannelInitializer<LocalChannel>() {
              @Override
              public void initChannel(LocalChannel ch) throws Exception {
                  ch.pipeline().addLast(new ChannelInboundHandlerAdapter());
              }
          });

        // Start server
        Channel sc = sb.bind(addr).syncUninterruptibly().channel();
        ChannelPoolHandler handler = new TestChannelPoolHandler();
        ChannelPool pool = new FixedChannelPool(cb, handler, ChannelHealthChecker.ACTIVE,
                                                AcquireTimeoutAction.FAIL, timeoutMillis, 1, Integer.MAX_VALUE);

        Channel channel = pool.acquire().syncUninterruptibly().getNow();
        final Future<Channel> future = pool.acquire();
        assertThrows(TimeoutException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                future.syncUninterruptibly();
            }
        });
        sc.close().syncUninterruptibly();
        channel.close().syncUninterruptibly();
        pool.close();
    }

    @Test
    public void testAcquireNewConnection() throws Exception {
        LocalAddress addr = new LocalAddress(getLocalAddrId());
        Bootstrap cb = new Bootstrap();
        cb.remoteAddress(addr);
        cb.group(group)
          .channel(LocalChannel.class);

        ServerBootstrap sb = new ServerBootstrap();
        sb.group(group)
          .channel(LocalServerChannel.class)
          .childHandler(new ChannelInitializer<LocalChannel>() {
              @Override
              public void initChannel(LocalChannel ch) throws Exception {
                  ch.pipeline().addLast(new ChannelInboundHandlerAdapter());
              }
          });

        // Start server
        Channel sc = sb.bind(addr).syncUninterruptibly().channel();
        ChannelPoolHandler handler = new TestChannelPoolHandler();
        ChannelPool pool = new FixedChannelPool(cb, handler, ChannelHealthChecker.ACTIVE,
                AcquireTimeoutAction.NEW, 500, 1, Integer.MAX_VALUE);

        Channel channel = pool.acquire().syncUninterruptibly().getNow();
        Channel channel2 = pool.acquire().syncUninterruptibly().getNow();
        assertNotSame(channel, channel2);
        sc.close().syncUninterruptibly();
        channel.close().syncUninterruptibly();
        channel2.close().syncUninterruptibly();
        pool.close();
    }

    /**
     * Tests that the acquiredChannelCount is not added up several times for the same channel acquire request.
     * @throws Exception
     */
    @Test
    public void testAcquireNewConnectionWhen() throws Exception {
        LocalAddress addr = new LocalAddress(getLocalAddrId());
        Bootstrap cb = new Bootstrap();
        cb.remoteAddress(addr);
        cb.group(group)
          .channel(LocalChannel.class);

        ServerBootstrap sb = new ServerBootstrap();
        sb.group(group)
          .channel(LocalServerChannel.class)
          .childHandler(new ChannelInitializer<LocalChannel>() {
              @Override
              public void initChannel(LocalChannel ch) throws Exception {
                  ch.pipeline().addLast(new ChannelInboundHandlerAdapter());
              }
          });

        // Start server
        Channel sc = sb.bind(addr).syncUninterruptibly().channel();
        ChannelPoolHandler handler = new TestChannelPoolHandler();
        ChannelPool pool = new FixedChannelPool(cb, handler, 1);
        Channel channel1 = pool.acquire().syncUninterruptibly().getNow();
        channel1.close().syncUninterruptibly();
        pool.release(channel1);

        Channel channel2 = pool.acquire().syncUninterruptibly().getNow();

        assertNotSame(channel1, channel2);
        sc.close().syncUninterruptibly();
        channel2.close().syncUninterruptibly();
        pool.close();
    }

    @Test
    public void testAcquireBoundQueue() throws Exception {
        LocalAddress addr = new LocalAddress(getLocalAddrId());
        Bootstrap cb = new Bootstrap();
        cb.remoteAddress(addr);
        cb.group(group)
          .channel(LocalChannel.class);

        ServerBootstrap sb = new ServerBootstrap();
        sb.group(group)
          .channel(LocalServerChannel.class)
          .childHandler(new ChannelInitializer<LocalChannel>() {
              @Override
              public void initChannel(LocalChannel ch) throws Exception {
                  ch.pipeline().addLast(new ChannelInboundHandlerAdapter());
              }
          });

        // Start server
        Channel sc = sb.bind(addr).syncUninterruptibly().channel();
        ChannelPoolHandler handler = new TestChannelPoolHandler();
        final ChannelPool pool = new FixedChannelPool(cb, handler, 1, 1);

        Channel channel = pool.acquire().syncUninterruptibly().getNow();
        Future<Channel> future = pool.acquire();
        assertFalse(future.isDone());

        assertThrows(IllegalStateException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                pool.acquire().syncUninterruptibly();
            }
        });
        sc.close().syncUninterruptibly();
        channel.close().syncUninterruptibly();
        pool.close();
    }

    @Test
    public void testReleaseDifferentPool() throws Exception {
        LocalAddress addr = new LocalAddress(getLocalAddrId());
        Bootstrap cb = new Bootstrap();
        cb.remoteAddress(addr);
        cb.group(group)
          .channel(LocalChannel.class);

        ServerBootstrap sb = new ServerBootstrap();
        sb.group(group)
          .channel(LocalServerChannel.class)
          .childHandler(new ChannelInitializer<LocalChannel>() {
              @Override
              public void initChannel(LocalChannel ch) throws Exception {
                  ch.pipeline().addLast(new ChannelInboundHandlerAdapter());
              }
          });

        // Start server
        Channel sc = sb.bind(addr).syncUninterruptibly().channel();
        ChannelPoolHandler handler = new TestChannelPoolHandler();
        ChannelPool pool = new FixedChannelPool(cb, handler, 1, 1);
        final ChannelPool pool2 = new FixedChannelPool(cb, handler, 1, 1);

        final Channel channel = pool.acquire().syncUninterruptibly().getNow();

        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                pool2.release(channel).syncUninterruptibly();
            }
        });
        sc.close().syncUninterruptibly();
        channel.close().syncUninterruptibly();
        pool.close();
        pool2.close();
    }

    @Test
    public void testReleaseAfterClosePool() throws Exception {
        LocalAddress addr = new LocalAddress(getLocalAddrId());
        Bootstrap cb = new Bootstrap();
        cb.remoteAddress(addr);
        cb.group(group).channel(LocalChannel.class);

        ServerBootstrap sb = new ServerBootstrap();
        sb.group(group)
                .channel(LocalServerChannel.class)
                .childHandler(new ChannelInitializer<LocalChannel>() {
                    @Override
                    public void initChannel(LocalChannel ch) throws Exception {
                        ch.pipeline().addLast(new ChannelInboundHandlerAdapter());
                    }
                });

        // Start server
        Channel sc = sb.bind(addr).syncUninterruptibly().channel();

        final FixedChannelPool pool = new FixedChannelPool(cb, new TestChannelPoolHandler(), 2);
        final Future<Channel> acquire = pool.acquire();
        final Channel channel = acquire.get();
        pool.close();
        group.submit(new Runnable() {
            @Override
            public void run() {
                // NOOP
            }
        }).syncUninterruptibly();
        assertThrows(IllegalStateException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                pool.release(channel).syncUninterruptibly();
            }
        });
        // Since the pool is closed, the Channel should have been closed as well.
        channel.closeFuture().syncUninterruptibly();
        assertFalse(channel.isOpen());
        sc.close().syncUninterruptibly();
        pool.close();
    }

    @Test
    public void testReleaseClosed() {
        LocalAddress addr = new LocalAddress(getLocalAddrId());
        Bootstrap cb = new Bootstrap();
        cb.remoteAddress(addr);
        cb.group(group).channel(LocalChannel.class);

        ServerBootstrap sb = new ServerBootstrap();
        sb.group(group)
                .channel(LocalServerChannel.class)
                .childHandler(new ChannelInitializer<LocalChannel>() {
                    @Override
                    public void initChannel(LocalChannel ch) throws Exception {
                        ch.pipeline().addLast(new ChannelInboundHandlerAdapter());
                    }
                });

        // Start server
        Channel sc = sb.bind(addr).syncUninterruptibly().channel();

        FixedChannelPool pool = new FixedChannelPool(cb, new TestChannelPoolHandler(), 2);
        Channel channel = pool.acquire().syncUninterruptibly().getNow();
        channel.close().syncUninterruptibly();
        pool.release(channel).syncUninterruptibly();

        sc.close().syncUninterruptibly();
        pool.close();
    }

    @Test
    public void testCloseAsync() throws ExecutionException, InterruptedException {
        LocalAddress addr = new LocalAddress(getLocalAddrId());
        Bootstrap cb = new Bootstrap();
        cb.remoteAddress(addr);
        cb.group(group).channel(LocalChannel.class);

        ServerBootstrap sb = new ServerBootstrap();
        sb.group(group)
                .channel(LocalServerChannel.class)
                .childHandler(new ChannelInitializer<LocalChannel>() {
                    @Override
                    public void initChannel(LocalChannel ch) throws Exception {
                        ch.pipeline().addLast(new ChannelInboundHandlerAdapter());
                    }
                });

        // Start server
        final Channel sc = sb.bind(addr).syncUninterruptibly().channel();

        final FixedChannelPool pool = new FixedChannelPool(cb, new TestChannelPoolHandler(), 2);

        pool.acquire().get();
        pool.acquire().get();

        final ChannelPromise closePromise = sc.newPromise();
        pool.closeAsync().addListener(new GenericFutureListener<Future<? super Void>>() {
            @Override
            public void operationComplete(Future<? super Void> future) throws Exception {
                assertEquals(0, pool.acquiredChannelCount());
                sc.close(closePromise).syncUninterruptibly();
            }
        }).awaitUninterruptibly();
        closePromise.awaitUninterruptibly();
    }

    @Test
    public void testChannelAcquiredException() throws InterruptedException {
        LocalAddress addr = new LocalAddress(getLocalAddrId());
        Bootstrap cb = new Bootstrap();
        cb.remoteAddress(addr);
        cb.group(group).channel(LocalChannel.class);

        ServerBootstrap sb = new ServerBootstrap();
        sb.group(group)
              .channel(LocalServerChannel.class)
              .childHandler(new ChannelInitializer<LocalChannel>() {
                  @Override
                  public void initChannel(LocalChannel ch) throws Exception {
                      ch.pipeline().addLast(new ChannelInboundHandlerAdapter());
                  }
              });

        // Start server
        Channel sc = sb.bind(addr).syncUninterruptibly().channel();
        final NullPointerException exception = new NullPointerException();
        FixedChannelPool pool = new FixedChannelPool(cb, new ChannelPoolHandler() {
            @Override
            public void channelReleased(Channel ch) {
            }
            @Override
            public void channelAcquired(Channel ch) {
                throw exception;
            }
            @Override
            public void channelCreated(Channel ch) {
            }
        }, 2);

        try {
            pool.acquire().sync();
        } catch (NullPointerException e) {
            assertSame(e, exception);
        }

        sc.close().sync();
        pool.close();
    }

    private static final class TestChannelPoolHandler extends AbstractChannelPoolHandler {
        @Override
        public void channelCreated(Channel ch) throws Exception {
            // NOOP
        }
    }
}
