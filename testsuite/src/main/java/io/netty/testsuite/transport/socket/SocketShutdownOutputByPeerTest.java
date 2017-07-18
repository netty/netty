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

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.internal.SocketUtils;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.channel.socket.SocketChannel;
import org.junit.Test;

import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class SocketShutdownOutputByPeerTest extends AbstractServerSocketTest {

    @Test(timeout = 30000)
    public void testShutdownOutput() throws Throwable {
        run();
    }

    public void testShutdownOutput(ServerBootstrap sb) throws Throwable {
        TestHandler h = new TestHandler();
        Socket s = new Socket();
        Channel sc = null;
        try {
            sc = sb.childHandler(h).childOption(ChannelOption.ALLOW_HALF_CLOSURE, true).bind().sync().channel();

            SocketUtils.connect(s, sc.localAddress(), 10000);
            s.getOutputStream().write(1);

            assertEquals(1, (int) h.queue.take());

            assertTrue(h.ch.isOpen());
            assertTrue(h.ch.isActive());
            assertFalse(h.ch.isInputShutdown());
            assertFalse(h.ch.isOutputShutdown());

            s.shutdownOutput();

            h.halfClosure.await();

            assertTrue(h.ch.isOpen());
            assertTrue(h.ch.isActive());
            assertTrue(h.ch.isInputShutdown());
            assertFalse(h.ch.isOutputShutdown());
            assertEquals(1, h.closure.getCount());
            Thread.sleep(100);
            assertEquals(1, h.halfClosureCount.intValue());
        } finally {
            if (sc != null) {
                sc.close();
            }
            s.close();
        }
    }

    @Test(timeout = 30000)
    public void testShutdownOutputWithoutOption() throws Throwable {
        run();
    }

    public void testShutdownOutputWithoutOption(ServerBootstrap sb) throws Throwable {
        TestHandler h = new TestHandler();
        Socket s = new Socket();
        Channel sc = null;
        try {
            sc = sb.childHandler(h).bind().sync().channel();

            SocketUtils.connect(s, sc.localAddress(), 10000);
            s.getOutputStream().write(1);

            assertEquals(1, (int) h.queue.take());

            assertTrue(h.ch.isOpen());
            assertTrue(h.ch.isActive());
            assertFalse(h.ch.isInputShutdown());
            assertFalse(h.ch.isOutputShutdown());

            s.shutdownOutput();

            h.closure.await();

            assertFalse(h.ch.isOpen());
            assertFalse(h.ch.isActive());
            assertTrue(h.ch.isInputShutdown());
            assertTrue(h.ch.isOutputShutdown());

            assertEquals(1, h.halfClosure.getCount());
            Thread.sleep(100);
            assertEquals(0, h.halfClosureCount.intValue());
        } finally {
            if (sc != null) {
                sc.close();
            }
            s.close();
        }
    }

    private static class TestHandler extends SimpleChannelInboundHandler<ByteBuf> {
        volatile SocketChannel ch;
        final BlockingQueue<Byte> queue = new LinkedBlockingQueue<Byte>();
        final CountDownLatch halfClosure = new CountDownLatch(1);
        final CountDownLatch closure = new CountDownLatch(1);
        final AtomicInteger halfClosureCount = new AtomicInteger();

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            ch = (SocketChannel) ctx.channel();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            closure.countDown();
        }

        @Override
        public void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
            queue.offer(msg.readByte());
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof ChannelInputShutdownEvent) {
                halfClosureCount.incrementAndGet();
                halfClosure.countDown();
            }
        }
    }
}
