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
package io.netty.testsuite.transport.socket;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import org.junit.Test;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.Assert.*;

public class SocketShutdownOutputBySelfTest extends AbstractClientSocketTest {

    @Test(timeout = 30000)
    public void testShutdownOutput() throws Throwable {
        run();
    }

    public void testShutdownOutput(Bootstrap cb) throws Throwable {
        TestHandler h = new TestHandler();
        ServerSocket ss = new ServerSocket();
        Socket s = null;
        try {
            ss.bind(addr);
            SocketChannel ch = (SocketChannel) cb.handler(h).connect().sync().channel();
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
            s.getOutputStream().write(1);
            assertEquals(1, (int) h.queue.take());
        } finally {
            if (s != null) {
                s.close();
            }
            ss.close();
        }
    }

    @Test(timeout = 30000)
    public void testShutdownOutputAfterClosed() throws Throwable {
        run();
    }

    public void testShutdownOutputAfterClosed(Bootstrap cb) throws Throwable {
        TestHandler h = new TestHandler();
        ServerSocket ss = new ServerSocket();
        Socket s = null;
        try {
            ss.bind(addr);
            SocketChannel ch = (SocketChannel) cb.handler(h).connect().sync().channel();
            assertTrue(ch.isActive());
            s = ss.accept();

            ch.close().syncUninterruptibly();
            try {
                ch.shutdownOutput().syncUninterruptibly();
                fail();
            } catch (Throwable cause) {
                checkThrowable(cause);
            }
        } finally {
            if (s != null) {
                s.close();
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
    private static class TestHandler extends SimpleChannelInboundHandler<ByteBuf> {
        volatile SocketChannel ch;
        final BlockingQueue<Byte> queue = new LinkedBlockingQueue<Byte>();

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            ch = (SocketChannel) ctx.channel();
        }

        @Override
        public void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
            queue.offer(msg.readByte());
        }
    }
}
