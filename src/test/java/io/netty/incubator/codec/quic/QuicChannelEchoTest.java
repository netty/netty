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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.concurrent.Future;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class QuicChannelEchoTest {

    private static final Random random = new Random();
    static final byte[] data = new byte[1048576];

    static {
        random.nextBytes(data);
    }

    private final boolean autoRead;
    private final ByteBufAllocator allocator;
    private final boolean composite;

    @Parameterized.Parameters(name =
            "{index}: autoRead = {0}, directBuffer = {1}, composite = {2}")
    public static Collection<Object[]> data() {
        List<Object[]> config = new ArrayList<>();
        for (int a = 0; a < 2; a++) {
            for (int b = 0; b < 2; b++) {
                for (int c = 0; c < 2; c++) {
                    config.add(new Object[] { a == 0, b == 0, c == 0 });
                }
            }
        }
        return config;
    }

    public QuicChannelEchoTest(boolean autoRead, boolean directBuffer, boolean composite) {
        this.autoRead = autoRead;
        if (directBuffer) {
            allocator = new UnpooledByteBufAllocator(true);
        } else {
            allocator = new UnpooledByteBufAllocator(false);
        }
        this.composite = composite;
    }

    private ByteBuf allocateBuffer() {
        return allocator.buffer();
    }

    private void setAllocator(Channel channel) {
        channel.config().setAllocator(allocator);
    }

    @Test
    public void testEchoStartedFromServer() throws Throwable {
        final EchoHandler sh = new EchoHandler(true, autoRead);
        final EchoHandler ch = new EchoHandler(false, autoRead);
        AtomicReference<List<ChannelFuture>> writeFutures = new AtomicReference<>();
        Channel server = QuicTestUtils.newServer(new ChannelInboundHandlerAdapter() {
            @Override
            public void channelActive(ChannelHandlerContext ctx) {
                setAllocator(ctx.channel());
                ((QuicChannel) ctx.channel()).createStream(QuicStreamType.BIDIRECTIONAL, sh)
                        .addListener((Future<QuicStreamChannel> future) -> {
                            QuicStreamChannel stream = future.getNow();
                            setAllocator(stream);
                            List<ChannelFuture> futures = writeAllData(stream);
                            writeFutures.set(futures);
                        });

                ctx.channel().config().setAutoRead(autoRead);
                if (!autoRead) {
                    ctx.read();
                }
            }

            @Override
            public void channelReadComplete(ChannelHandlerContext ctx) {
                if (!autoRead) {
                    ctx.read();
                }
            }
        }, sh);
        setAllocator(server);
        InetSocketAddress address = (InetSocketAddress) server.localAddress();
        Channel channel = QuicTestUtils.newClient();
        QuicChannel quicChannel = null;
        try {
            quicChannel = QuicChannel.newBootstrap(channel)
                    .handler(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelActive(ChannelHandlerContext ctx) {
                            if (!autoRead) {
                                ctx.read();
                            }
                        }

                        @Override
                        public void channelReadComplete(ChannelHandlerContext ctx) {
                            if (!autoRead) {
                                ctx.read();
                            }
                        }
                    })
                    .streamHandler(ch)
                    .remoteAddress(address)
                    .option(ChannelOption.AUTO_READ, autoRead)
                    .option(ChannelOption.ALLOCATOR, allocator)
                    .connect()
                    .get();

            waitForData(ch, sh);

            for (;;) {
                List<ChannelFuture> futures = writeFutures.get();
                if (futures != null) {
                    for (ChannelFuture f: futures) {
                        f.sync();
                    }
                    break;
                }

                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    // Ignore.
                }
            }
            waitForData(sh, ch);

            // Close underlying streams.
            sh.channel.close().sync();
            ch.channel.close().sync();

            // Close underlying quic channels
            sh.channel.parent().close().sync();
            ch.channel.parent().close().sync();

            checkForException(ch, sh);
        } finally {
            server.close().sync();
            QuicTestUtils.closeIfNotNull(quicChannel);
            // Close the parent Datagram channel as well.
            channel.close().sync();
        }
    }

    @Test
    public void testEchoStartedFromClient() throws Throwable {
        final EchoHandler sh = new EchoHandler(true, autoRead);
        final EchoHandler ch = new EchoHandler(false, autoRead);
        ChannelFuture future = null;
        Channel server = QuicTestUtils.newServer(new ChannelInboundHandlerAdapter() {
            @Override
            public void channelActive(ChannelHandlerContext ctx) {
                setAllocator(ctx.channel());
                ctx.channel().config().setAutoRead(autoRead);
                if (!autoRead) {
                    ctx.read();
                }
            }

            @Override
            public void channelReadComplete(ChannelHandlerContext ctx) {
                if (!autoRead) {
                    ctx.read();
                }
            }
        }, sh);
        setAllocator(server);
        InetSocketAddress address = (InetSocketAddress) server.localAddress();
        Channel channel = QuicTestUtils.newClient();
        QuicChannel quicChannel = null;
        try {
            quicChannel = QuicChannel.newBootstrap(channel)
                    .handler(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelActive(ChannelHandlerContext ctx) {
                            if (!autoRead) {
                                ctx.read();
                            }
                        }

                        @Override
                        public void channelReadComplete(ChannelHandlerContext ctx) {
                            if (!autoRead) {
                                ctx.read();
                            }
                        }
                    })
                    .streamHandler(ch)
                    .remoteAddress(address)
                    .option(ChannelOption.AUTO_READ, autoRead)
                    .option(ChannelOption.ALLOCATOR, allocator)
                    .connect()
                    .get();

            QuicStreamChannel stream = quicChannel.createStream(QuicStreamType.BIDIRECTIONAL, ch).sync().getNow();
            setAllocator(stream);

            assertEquals(QuicStreamType.BIDIRECTIONAL, stream.type());
            assertEquals(0, stream.streamId());
            assertTrue(stream.isLocalCreated());
            List<ChannelFuture> futures = writeAllData(stream);

            for (ChannelFuture f: futures) {
                f.sync();
            }
            waitForData(ch, sh);
            waitForData(sh, ch);

            // Close underlying streams.
            sh.channel.close().sync();
            ch.channel.close().sync();

            // Close underlying quic channels
            sh.channel.parent().close().sync();
            ch.channel.parent().close().sync();
            checkForException(ch, sh);
        } finally {
            server.close().syncUninterruptibly();
            QuicTestUtils.closeIfNotNull(quicChannel);
            // Close the parent Datagram channel as well.
            channel.close().sync();
        }
    }

    private List<ChannelFuture> writeAllData(Channel channel) {
        if (composite) {
            CompositeByteBuf compositeByteBuf = allocator.compositeBuffer();
            for (int i = 0; i < data.length;) {
                int length = Math.min(random.nextInt(1024 * 64), data.length - i);
                ByteBuf buf = allocateBuffer().writeBytes(data, i, length);
                compositeByteBuf.addComponent(true, buf);
                i += length;
            }
            return Collections.singletonList(channel.writeAndFlush(compositeByteBuf));
        } else {
            List<ChannelFuture> futures = new ArrayList<>();
            for (int i = 0; i < data.length;) {
                int length = Math.min(random.nextInt(1024 * 64), data.length - i);
                ByteBuf buf = allocateBuffer().writeBytes(data, i, length);
                futures.add(channel.writeAndFlush(buf));
                i += length;
            }
            return futures;
        }
    }

    private static void waitForData(EchoHandler h1, EchoHandler h2) {
        while (h1.counter < data.length) {
            if (h2.exception.get() != null) {
                break;
            }
            if (h1.exception.get() != null) {
                break;
            }

            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                // Ignore.
            }
        }
    }

    private static void checkForException(EchoHandler h1, EchoHandler h2) throws Throwable {
        if (h1.exception.get() != null && !(h1.exception.get() instanceof IOException)) {
            throw h1.exception.get();
        }
        if (h2.exception.get() != null && !(h2.exception.get() instanceof IOException)) {
            throw h2.exception.get();
        }
        if (h1.exception.get() != null) {
            throw h1.exception.get();
        }
        if (h2.exception.get() != null) {
            throw h2.exception.get();
        }
    }

    private class EchoHandler extends SimpleChannelInboundHandler<ByteBuf> {
        private final boolean server;
        private final boolean autoRead;
        volatile Channel channel;
        final AtomicReference<Throwable> exception = new AtomicReference<>();
        volatile int counter;

        EchoHandler(boolean server, boolean autoRead) {
            this.server = server;
            this.autoRead = autoRead;
        }

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) {
            ctx.channel().config().setAutoRead(autoRead);
            setAllocator(ctx.channel());
            ctx.fireChannelRegistered();
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            channel = ctx.channel();
            QuicStreamChannel channel = (QuicStreamChannel)  ctx.channel();
            assertEquals(QuicStreamType.BIDIRECTIONAL, channel.type());
            if (channel.isLocalCreated()) {
                // Server starts with 1, client with 0
                assertEquals(server ? 1 : 0, channel.streamId());
            } else {
                // Server starts with 1, client with 0
                assertEquals(server ? 0 : 1, channel.streamId());
            }
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
