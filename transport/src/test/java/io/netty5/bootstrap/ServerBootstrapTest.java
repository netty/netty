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
package io.netty5.bootstrap;

import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.local.LocalAddress;
import io.netty5.channel.local.LocalChannel;
import io.netty5.channel.local.LocalHandler;
import io.netty5.channel.local.LocalServerChannel;
import io.netty5.util.AttributeKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ServerBootstrapTest {

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void testHandlerRegister() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Throwable> error = new AtomicReference<>();
        EventLoopGroup group = new MultithreadEventLoopGroup(1, LocalHandler.newFactory());
        try {
            ServerBootstrap sb = new ServerBootstrap();
            sb.channel(LocalServerChannel.class)
              .group(group)
              .childHandler(new ChannelHandler() { })
              .handler(new ChannelHandler() {
                  @Override
                  public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
                      try {
                          assertTrue(ctx.executor().inEventLoop());
                      } catch (Throwable cause) {
                          error.set(cause);
                      } finally {
                          latch.countDown();
                      }
                  }
              });
            sb.register().syncUninterruptibly();
            latch.await();
            assertNull(error.get());
        } finally {
            group.shutdownGracefully();
        }
    }

    @Test
    @Timeout(value = 3000, unit = TimeUnit.MILLISECONDS)
    public void testParentHandler() throws Exception {
        testParentHandler(false);
    }

    @Test
    @Timeout(value = 3000, unit = TimeUnit.MILLISECONDS)
    public void testParentHandlerViaChannelInitializer() throws Exception {
        testParentHandler(true);
    }

    private static void testParentHandler(boolean channelInitializer) throws Exception {
        final LocalAddress addr = new LocalAddress(UUID.randomUUID().toString());
        final CountDownLatch readLatch = new CountDownLatch(1);
        final CountDownLatch initLatch = new CountDownLatch(1);

        final ChannelHandler handler = new ChannelHandler() {
            @Override
            public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
                initLatch.countDown();
                ctx.fireChannelActive();
            }

            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                readLatch.countDown();
                ctx.fireChannelRead(msg);
            }
        };

        EventLoopGroup group = new MultithreadEventLoopGroup(1, LocalHandler.newFactory());
        Channel sch = null;
        Channel cch = null;
        try {
            ServerBootstrap sb = new ServerBootstrap();
            sb.channel(LocalServerChannel.class)
                    .group(group)
                    .childHandler(new ChannelHandler() { });
            if (channelInitializer) {
                sb.handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        ch.pipeline().addLast(handler);
                    }
                });
            } else {
                sb.handler(handler);
            }

            Bootstrap cb = new Bootstrap();
            cb.group(group)
                    .channel(LocalChannel.class)
                    .handler(new ChannelHandler() { });

            sch = sb.bind(addr).get();

            cch = cb.connect(addr).get();

            initLatch.await();
            readLatch.await();
        } finally {
            if (sch != null) {
                sch.close().syncUninterruptibly();
            }
            if (cch != null) {
                cch.close().syncUninterruptibly();
            }
            group.shutdownGracefully();
        }
    }

    @Test
    public void optionsAndAttributesMustBeAvailableOnChildChannelInit() throws Exception {
        EventLoopGroup group = new MultithreadEventLoopGroup(1, LocalHandler.newFactory());
        LocalAddress addr = new LocalAddress(UUID.randomUUID().toString());
        final AttributeKey<String> key = AttributeKey.valueOf(UUID.randomUUID().toString());
        final AtomicBoolean requestServed = new AtomicBoolean();
        ServerBootstrap sb = new ServerBootstrap()
                .group(group)
                .channel(LocalServerChannel.class)
                .childOption(ChannelOption.CONNECT_TIMEOUT_MILLIS, 4242)
                .childAttr(key, "value")
                .childHandler(new ChannelInitializer<LocalChannel>() {
                    @Override
                    protected void initChannel(LocalChannel ch) throws Exception {
                        Integer option = ch.config().getOption(ChannelOption.CONNECT_TIMEOUT_MILLIS);
                        assertEquals(4242, (int) option);
                        assertEquals("value", ch.attr(key).get());
                        requestServed.set(true);
                    }
                });
        Channel serverChannel = sb.bind(addr).get();

        Bootstrap cb = new Bootstrap();
        cb.group(group)
                .channel(LocalChannel.class)
                .handler(new ChannelHandler() { });
        Channel clientChannel = cb.connect(addr).get();
        serverChannel.close().syncUninterruptibly();
        clientChannel.close().syncUninterruptibly();
        group.shutdownGracefully();
        assertTrue(requestServed.get());
    }
}
