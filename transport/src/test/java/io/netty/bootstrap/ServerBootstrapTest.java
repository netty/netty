/*
 * Copyright 2015 The Netty Project
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
package io.netty.bootstrap;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.MultithreadEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalHandler;
import io.netty.channel.local.LocalServerChannel;
import org.junit.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ServerBootstrapTest {

    @Test(timeout = 5000)
    public void testHandlerRegister() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Throwable> error = new AtomicReference<>();
        EventLoopGroup group = new MultithreadEventLoopGroup(1, LocalHandler.newFactory());
        try {
            ServerBootstrap sb = new ServerBootstrap();
            sb.channel(LocalServerChannel.class)
              .group(group)
              .childHandler(new ChannelInboundHandlerAdapter())
              .handler(new ChannelHandlerAdapter() {
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

    @Test(timeout = 3000)
    public void testParentHandler() throws Exception {
        testParentHandler(false);
    }

    @Test(timeout = 3000)
    public void testParentHandlerViaChannelInitializer() throws Exception {
        testParentHandler(true);
    }

    private static void testParentHandler(boolean channelInitializer) throws Exception {
        final LocalAddress addr = new LocalAddress(UUID.randomUUID().toString());
        final CountDownLatch readLatch = new CountDownLatch(1);
        final CountDownLatch initLatch = new CountDownLatch(1);

        final ChannelHandler handler = new ChannelInboundHandlerAdapter() {
            @Override
            public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
                initLatch.countDown();
                super.handlerAdded(ctx);
            }

            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                readLatch.countDown();
                super.channelRead(ctx, msg);
            }
        };

        EventLoopGroup group = new MultithreadEventLoopGroup(1, LocalHandler.newFactory());
        Channel sch = null;
        Channel cch = null;
        try {
            ServerBootstrap sb = new ServerBootstrap();
            sb.channel(LocalServerChannel.class)
                    .group(group)
                    .childHandler(new ChannelInboundHandlerAdapter());
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
                    .handler(new ChannelInboundHandlerAdapter());

            sch = sb.bind(addr).syncUninterruptibly().channel();

            cch = cb.connect(addr).syncUninterruptibly().channel();

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
}
