/*
 * Copyright 2012 The Netty Project
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
package io.netty.channel.local;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundMessageHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.*;

public class LocalChannelRegistryTest {

    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(LocalChannelRegistryTest.class);

    private static final String LOCAL_ADDR_ID = "test.id";

    @Test
    public void testLocalAddressReuse() throws Exception {

        for (int i = 0; i < 2; i ++) {
            EventLoopGroup clientGroup = new LocalEventLoopGroup();
            EventLoopGroup serverGroup = new LocalEventLoopGroup();
            LocalAddress addr = new LocalAddress(LOCAL_ADDR_ID);
            Bootstrap cb = new Bootstrap();
            ServerBootstrap sb = new ServerBootstrap();

            cb.group(clientGroup)
              .channel(LocalChannel.class)
              .handler(new TestHandler());

            sb.group(serverGroup)
              .channel(LocalServerChannel.class)
              .childHandler(new ChannelInitializer<LocalChannel>() {
                  @Override
                  public void initChannel(LocalChannel ch) throws Exception {
                      ch.pipeline().addLast(new TestHandler());
                  }
              });

            // Start server
            Channel sc = sb.bind(addr).sync().channel();

            final CountDownLatch latch = new CountDownLatch(1);
            // Connect to the server
            final Channel cc = cb.connect(addr).sync().channel();
            cc.eventLoop().execute(new Runnable() {
                @Override
                public void run() {
                    // Send a message event up the pipeline.
                    cc.pipeline().inboundMessageBuffer().add("Hello, World");
                    cc.pipeline().fireInboundBufferUpdated();
                    latch.countDown();
                }
            });
            latch.await();

            // Close the channel
            cc.close().sync();

            serverGroup.shutdownGracefully();
            clientGroup.shutdownGracefully();

            sc.closeFuture().sync();

            assertNull(String.format(
                    "Expected null, got channel '%s' for local address '%s'",
                    LocalChannelRegistry.get(addr), addr), LocalChannelRegistry.get(addr));
        }
    }

    static class TestHandler extends ChannelInboundMessageHandlerAdapter<String> {
        @Override
        public void messageReceived(ChannelHandlerContext ctx, String msg) throws Exception {
            logger.info(String.format("Received mesage: %s", msg));
        }
    }
}
