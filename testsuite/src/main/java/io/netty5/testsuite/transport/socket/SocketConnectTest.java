/*
 * Copyright 2016 The Netty Project
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
package io.netty5.testsuite.transport.socket;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.buffer.ByteBuf;
import io.netty5.buffer.ByteBufUtil;
import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.DefaultBufferAllocators;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerAdapter;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.socket.SocketChannel;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.ImmediateEventExecutor;
import io.netty5.util.concurrent.Promise;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static io.netty5.buffer.UnpooledByteBufAllocator.DEFAULT;
import static io.netty5.util.CharsetUtil.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SocketConnectTest extends AbstractSocketTest {

    @Test
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void testLocalAddressAfterConnect(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testLocalAddressAfterConnect);
    }

    public void testLocalAddressAfterConnect(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        Channel serverChannel = null;
        Channel clientChannel = null;
        try {
            final Promise<InetSocketAddress> localAddressPromise = ImmediateEventExecutor.INSTANCE.newPromise();
            serverChannel = sb.childHandler(new ChannelHandler() {
                        @Override
                        public void channelActive(ChannelHandlerContext ctx) throws Exception {
                            localAddressPromise.setSuccess((InetSocketAddress) ctx.channel().localAddress());
                        }
                    }).bind().get();

            clientChannel = cb.handler(new ChannelHandler() { }).register().get();

            assertNull(clientChannel.localAddress());
            assertNull(clientChannel.remoteAddress());

            clientChannel.connect(serverChannel.localAddress()).get();
            assertLocalAddress((InetSocketAddress) clientChannel.localAddress());
            assertNotNull(clientChannel.remoteAddress());

            assertLocalAddress(localAddressPromise.asFuture().get());
        } finally {
            if (clientChannel != null) {
                clientChannel.close().syncUninterruptibly();
            }
            if (serverChannel != null) {
                serverChannel.close().syncUninterruptibly();
            }
        }
    }

    @Test
    @Timeout(value = 3000, unit = TimeUnit.MILLISECONDS)
    public void testChannelEventsFiredWhenClosedDirectly(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testChannelEventsFiredWhenClosedDirectly);
    }

    public void testChannelEventsFiredWhenClosedDirectly(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        final BlockingQueue<Integer> events = new LinkedBlockingQueue<>();

        Channel sc = null;
        Channel cc = null;
        try {
            sb.childHandler(new ChannelHandler() { });
            sc = sb.bind().get();

            cb.handler(new ChannelHandler() {
                @Override
                public void channelActive(ChannelHandlerContext ctx) throws Exception {
                    events.add(0);
                }

                @Override
                public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                    events.add(1);
                }
            });
            // Connect and directly close again.
            cc = cb.connect(sc.localAddress()).addListener(future -> future.getNow().close()).get();
            assertEquals(0, events.take().intValue());
            assertEquals(1, events.take().intValue());
        } finally {
            if (cc != null) {
                cc.close();
            }
            if (sc != null) {
                sc.close();
            }
        }
    }

    @Test
    @Timeout(value = 3000, unit = TimeUnit.MILLISECONDS)
    public void testWriteWithFastOpenBeforeConnectByteBuf(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testWriteWithFastOpenBeforeConnectByteBuf);
    }

    public void testWriteWithFastOpenBeforeConnectByteBuf(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        enableTcpFastOpen(sb, cb);
        sb.childOption(ChannelOption.AUTO_READ, true);
        cb.option(ChannelOption.AUTO_READ, true);

        sb.childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addLast(new EchoServerHandler());
            }
        });

        Channel sc = sb.bind().get();
        connectAndVerifyDataTransfer(cb, sc);
        connectAndVerifyDataTransfer(cb, sc);
    }

    @Test
    @Timeout(value = 3000, unit = TimeUnit.MILLISECONDS)
    public void testWriteWithFastOpenBeforeConnect(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testWriteWithFastOpenBeforeConnect);
    }

    public void testWriteWithFastOpenBeforeConnect(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        enableNewBufferAPI(sb, cb);
        enableTcpFastOpen(sb, cb);
        sb.childOption(ChannelOption.AUTO_READ, true);
        cb.option(ChannelOption.AUTO_READ, true);

        sb.childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addLast(new EchoServerHandler());
            }
        });

        Channel sc = sb.bind().get();
        connectAndVerifyDataTransfer(cb, sc);
        connectAndVerifyDataTransfer(cb, sc);
    }

    private static void connectAndVerifyDataTransfer(Bootstrap cb, Channel sc)
            throws Exception {
        BufferingClientHandler handler = new BufferingClientHandler();
        cb.handler(handler);
        Future<Channel> register = cb.register();
        Channel channel = register.get();
        Future<Void> write = channel.write(writeAsciiBuffer(sc, "[fastopen]"));
        SocketAddress remoteAddress = sc.localAddress();
        Future<Void> connectFuture = channel.connect(remoteAddress);
        connectFuture.sync();
        channel.writeAndFlush(writeAsciiBuffer(sc, "[normal data]")).sync();
        write.sync();
        String expectedString = "[fastopen][normal data]";
        String result = handler.collectBuffer(expectedString.getBytes(US_ASCII).length);
        channel.disconnect().sync();
        assertEquals(expectedString, result);
    }

    private static Object writeAsciiBuffer(Channel sc, String seq) {
        if (sc.config().getRecvBufferAllocatorUseBuffer()) {
            return DefaultBufferAllocators.preferredAllocator().copyOf(seq.getBytes(US_ASCII));
        }
        return ByteBufUtil.writeAscii(DEFAULT, seq);
    }

    protected void enableTcpFastOpen(ServerBootstrap sb, Bootstrap cb) {
        // TFO is an almost-pure optimisation and should not change any observable behaviour in our tests.
        sb.option(ChannelOption.TCP_FASTOPEN, 5);
        cb.option(ChannelOption.TCP_FASTOPEN_CONNECT, true);
    }

    private static void assertLocalAddress(InetSocketAddress address) {
        assertTrue(address.getPort() > 0);
        assertFalse(address.getAddress().isAnyLocalAddress());
    }

    private static class BufferingClientHandler extends ChannelHandlerAdapter {
        private final Semaphore semaphore = new Semaphore(0);
        private final ByteArrayOutputStream streamBuffer = new ByteArrayOutputStream();

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof Buffer) {
                try (Buffer buf = (Buffer) msg) {
                    int readableBytes = buf.readableBytes();
                    byte[] array = new byte[readableBytes];
                    buf.readBytes(array, 0, array.length);
                    streamBuffer.write(array);
                    semaphore.release(readableBytes);
                }
            } else if (msg instanceof ByteBuf) {
                ByteBuf buf = (ByteBuf) msg;
                int readableBytes = buf.readableBytes();
                buf.readBytes(streamBuffer, readableBytes);
                semaphore.release(readableBytes);
                buf.release();
            } else {
                throw new IllegalArgumentException("Unexpected message type: " + msg);
            }
        }

        String collectBuffer(int expectedBytes) throws InterruptedException {
            semaphore.acquire(expectedBytes);
            String result = streamBuffer.toString(US_ASCII);
            streamBuffer.reset();
            return result;
        }
    }

    private static final class EchoServerHandler extends ChannelHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof Buffer) {
                try (Buffer buf = (Buffer) msg) {
                    Buffer buffer = ctx.bufferAllocator().allocate(buf.readableBytes());
                    buffer.writeBytes(buf);
                    ctx.channel().writeAndFlush(buffer);
                }
            } else if (msg instanceof ByteBuf) {
                ByteBuf buffer = ctx.alloc().buffer();
                ByteBuf buf = (ByteBuf) msg;
                buffer.writeBytes(buf);
                buf.release();
                ctx.channel().writeAndFlush(buffer);
            } else {
                throw new IllegalArgumentException("Unexpected message type: " + msg);
            }
        }
    }
}
