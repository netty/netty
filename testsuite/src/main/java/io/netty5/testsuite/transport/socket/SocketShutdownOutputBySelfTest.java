/*
 * Copyright 2012 The Netty Project
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
import io.netty5.buffer.ByteBuf;
import io.netty5.buffer.Unpooled;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.SimpleChannelInboundHandler;
import io.netty5.channel.WriteBufferWaterMark;
import io.netty5.channel.socket.SocketChannel;
import io.netty5.util.concurrent.Future;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class SocketShutdownOutputBySelfTest extends AbstractClientSocketTest {

    @Test
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void testShutdownOutput(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testShutdownOutput);
    }

    public void testShutdownOutput(Bootstrap cb) throws Throwable {
        TestHandler h = new TestHandler();
        ServerSocket ss = new ServerSocket();
        Socket s = null;
        SocketChannel ch = null;
        try {
            ss.bind(newSocketAddress());
            ch = (SocketChannel) cb.handler(h).connect(ss.getLocalSocketAddress()).get();
            assertTrue(ch.isActive());
            assertFalse(ch.isOutputShutdown());

            s = ss.accept();
            ch.writeAndFlush(Unpooled.wrappedBuffer(new byte[] { 1 })).sync();
            assertEquals(1, s.getInputStream().read());

            assertTrue(h.ch.isOpen());
            assertTrue(h.ch.isActive());
            assertFalse(h.ch.isInputShutdown());
            assertFalse(h.ch.isOutputShutdown());

            // Make the connection half-closed and ensure read() returns -1.
            ch.shutdownOutput().sync();
            assertEquals(-1, s.getInputStream().read());

            assertTrue(h.ch.isOpen());
            assertTrue(h.ch.isActive());
            assertFalse(h.ch.isInputShutdown());
            assertTrue(h.ch.isOutputShutdown());

            // If half-closed, the peer should be able to write something.
            s.getOutputStream().write(new byte[] { 1 });
            assertEquals(1, (int) h.queue.take());
        } finally {
            if (s != null) {
                s.close();
            }
            if (ch != null) {
                ch.close();
            }
            ss.close();
        }
    }

    @Test
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void testShutdownOutputAfterClosed(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testShutdownOutputAfterClosed);
    }

    public void testShutdownOutputAfterClosed(Bootstrap cb) throws Throwable {
        TestHandler h = new TestHandler();
        ServerSocket ss = new ServerSocket();
        Socket s = null;
        try {
            ss.bind(newSocketAddress());
            SocketChannel ch = (SocketChannel) cb.handler(h).connect(ss.getLocalSocketAddress()).get();
            assertTrue(ch.isActive());
            s = ss.accept();

            ch.close().syncUninterruptibly();
            try {
                ch.shutdownInput().syncUninterruptibly();
                fail();
            } catch (Throwable cause) {
                checkThrowable(cause.getCause());
            }
            try {
                ch.shutdownOutput().syncUninterruptibly();
                fail();
            } catch (Throwable cause) {
                checkThrowable(cause.getCause());
            }
        } finally {
            if (s != null) {
                s.close();
            }
            ss.close();
        }
    }

    @Disabled
    @Test
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void testWriteAfterShutdownOutputNoWritabilityChange(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testWriteAfterShutdownOutputNoWritabilityChange);
    }

    public void testWriteAfterShutdownOutputNoWritabilityChange(Bootstrap cb) throws Throwable {
        final TestHandler h = new TestHandler();
        ServerSocket ss = new ServerSocket();
        Socket s = null;
        SocketChannel ch = null;
        try {
            ss.bind(newSocketAddress());
            cb.option(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(2, 4));
            ch = (SocketChannel) cb.handler(h).connect(ss.getLocalSocketAddress()).get();
            assertTrue(ch.isActive());
            assertFalse(ch.isOutputShutdown());

            s = ss.accept();

            byte[] expectedBytes = { 1, 2, 3, 4, 5, 6 };
            Future<Void> writeFuture = ch.write(Unpooled.wrappedBuffer(expectedBytes));
            h.assertWritability(false);
            ch.flush();
            writeFuture.sync();
            h.assertWritability(true);
            for (byte expectedByte : expectedBytes) {
                assertEquals(expectedByte, s.getInputStream().read());
            }

            assertTrue(h.ch.isOpen());
            assertTrue(h.ch.isActive());
            assertFalse(h.ch.isInputShutdown());
            assertFalse(h.ch.isOutputShutdown());

            // Make the connection half-closed and ensure read() returns -1.
            ch.shutdownOutput().sync();
            assertEquals(-1, s.getInputStream().read());

            assertTrue(h.ch.isOpen());
            assertTrue(h.ch.isActive());
            assertFalse(h.ch.isInputShutdown());
            assertTrue(h.ch.isOutputShutdown());

            try {
                // If half-closed, the local endpoint shouldn't be able to write
                ch.writeAndFlush(Unpooled.wrappedBuffer(new byte[]{ 2 })).sync();
                fail();
            } catch (Throwable cause) {
                checkThrowable(cause.getCause());
            }
            assertNull(h.writabilityQueue.poll());
        } finally {
            if (s != null) {
                s.close();
            }
            if (ch != null) {
                ch.close();
            }
            ss.close();
        }
    }

    @Test
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void testShutdownOutputSoLingerNoAssertError(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testShutdownOutputSoLingerNoAssertError);
    }

    public void testShutdownOutputSoLingerNoAssertError(Bootstrap cb) throws Throwable {
        testShutdownSoLingerNoAssertError0(cb, true);
    }

    @Test
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void testShutdownSoLingerNoAssertError(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testShutdownSoLingerNoAssertError);
    }

    public void testShutdownSoLingerNoAssertError(Bootstrap cb) throws Throwable {
        testShutdownSoLingerNoAssertError0(cb, false);
    }

    private void testShutdownSoLingerNoAssertError0(Bootstrap cb, boolean output) throws Throwable {
        ServerSocket ss = new ServerSocket();
        Socket s = null;

        Channel client = null;
        try {
            ss.bind(newSocketAddress());
            client = cb.option(ChannelOption.SO_LINGER, 1).handler(new ChannelHandler() { })
                    .connect(ss.getLocalSocketAddress()).get();
            s = ss.accept();

            if (output) {
                ((SocketChannel) client).shutdownOutput().sync();
            } else {
                ((SocketChannel) client).shutdown().sync();
            }
        } finally {
            if (s != null) {
                s.close();
            }
            if (client != null) {
                client.close();
            }
            ss.close();
        }
    }
    private static void checkThrowable(Throwable cause) throws Throwable {
        // Depending on OIO / NIO both are ok
        if (!(cause instanceof ClosedChannelException) && !(cause instanceof SocketException)) {
            throw cause;
        }
    }

    private static final class TestHandler extends SimpleChannelInboundHandler<ByteBuf> {
        volatile SocketChannel ch;
        final BlockingQueue<Byte> queue = new LinkedBlockingQueue<>();
        final BlockingDeque<Boolean> writabilityQueue = new LinkedBlockingDeque<>();

        @Override
        public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
            writabilityQueue.add(ctx.channel().isWritable());
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            ch = (SocketChannel) ctx.channel();
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
            queue.offer(msg.readByte());
        }

        private void drainWritabilityQueue() throws InterruptedException {
            while (writabilityQueue.poll(100, TimeUnit.MILLISECONDS) != null) {
                // Just drain the queue.
            }
        }

        void assertWritability(boolean isWritable) throws InterruptedException {
            try {
                Boolean writability = writabilityQueue.takeLast();
                assertEquals(isWritable, writability);
                // TODO(scott): why do we get multiple writability changes here ... race condition?
                drainWritabilityQueue();
            } catch (Throwable c) {
                c.printStackTrace();
            }
        }
    }
}
