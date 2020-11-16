/*
 * Copyright 2020 The Netty Project
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
package io.netty.incubator.codec.quic;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.SimpleChannelInboundHandler;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class QuicChannelEchoTest {

    private static final Random random = new Random();
    static final byte[] data = new byte[1048576];

    static {
        random.nextBytes(data);
    }

    @Test
    public void testEchoStartedFromClientWithAutoRead() throws Throwable {
        testEchoStartedFromClient(true);
    }

    @Test
    public void testEchoStartedFromClientWithoutAutoRead() throws Throwable {
        testEchoStartedFromClient(false);
    }

    private static void testEchoStartedFromClient(boolean autoRead) throws Throwable {
        final EchoHandler sh = new EchoHandler(autoRead);
        final EchoHandler ch = new EchoHandler(autoRead);
        ChannelFuture future = null;
        Channel server = QuicTestUtils.newServer(
                new QuicChannelInitializer(sh));
        InetSocketAddress address = (InetSocketAddress) server.localAddress();
        try {
            Bootstrap bootstrap = QuicTestUtils.newClientBootstrap();
            future = bootstrap
                    .handler(new ChannelInboundHandlerAdapter())
                    .connect(QuicConnectionAddress.random(address));
            assertTrue(future.await().isSuccess());

            QuicChannel channel = (QuicChannel) future.channel();
            QuicStreamChannel stream = channel.createStream(QuicStreamType.BIDIRECTIONAL, ch).sync().getNow();

            List<ChannelFuture> futures = new ArrayList<ChannelFuture>();
            for (int i = 0; i < data.length;) {
                int length = Math.min(random.nextInt(1024 * 64), data.length - i);
                ByteBuf buf = Unpooled.directBuffer().writeBytes(data, i, length);
                futures.add(stream.writeAndFlush(buf));
                i += length;
            }

            for (ChannelFuture f: futures) {
                f.sync();
            }

            while (ch.counter < data.length) {
                if (sh.exception.get() != null) {
                    break;
                }
                if (ch.exception.get() != null) {
                    break;
                }

                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    // Ignore.
                }
            }

            while (sh.counter < data.length) {
                if (sh.exception.get() != null) {
                    break;
                }
                if (ch.exception.get() != null) {
                    break;
                }

                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    // Ignore.
                }
            }

            // Close underlying streams.
            sh.channel.close().sync();
            ch.channel.close().sync();

            // Close underlying quic channels
            sh.channel.parent().close().sync();
            ch.channel.parent().close().sync();

            if (sh.exception.get() != null && !(sh.exception.get() instanceof IOException)) {
                throw sh.exception.get();
            }
            if (ch.exception.get() != null && !(ch.exception.get() instanceof IOException)) {
                throw ch.exception.get();
            }
            if (sh.exception.get() != null) {
                throw sh.exception.get();
            }
            if (ch.exception.get() != null) {
                throw ch.exception.get();
            }
        } finally {
            server.close().syncUninterruptibly();
            // Close the parent Datagram channel as well.
            QuicTestUtils.closeParent(future);
        }
    }

    private static class EchoHandler extends SimpleChannelInboundHandler<ByteBuf> {
        private final boolean autoRead;
        volatile Channel channel;
        final AtomicReference<Throwable> exception = new AtomicReference<>();
        volatile int counter;

        EchoHandler(boolean autoRead) {
            this.autoRead = autoRead;
        }

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) {
            ctx.channel().config().setAutoRead(autoRead);
            ctx.fireChannelRegistered();
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            channel = ctx.channel();
            if (!autoRead) {
                ctx.read();
            }
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf in) {
            byte[] actual = new byte[in.readableBytes()];
            in.readBytes(actual);

            int lastIdx = counter;
            for (int i = 0; i < actual.length; i ++) {
                assertEquals(data[i + lastIdx], actual[i]);
            }

            if (!((QuicStreamChannel) ctx.channel()).isLocalCreated()) {
                channel.write(Unpooled.wrappedBuffer(actual));
            }

            counter += actual.length;
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) {
            try {
                ctx.flush();
            } finally {
                if (!autoRead) {
                    ctx.read();
                }
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx,
                                    Throwable cause) {
            if (exception.compareAndSet(null, cause)) {
                cause.printStackTrace();
                ctx.close();
            }
        }
    }
}
