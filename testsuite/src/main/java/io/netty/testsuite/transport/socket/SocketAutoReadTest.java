/*
 * Copyright 2014 The Netty Project
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
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import org.junit.Test;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class SocketAutoReadTest extends AbstractSocketTest {
    private static final Random random = new Random();
    static final byte[] data = new byte[1024];

    static {
        random.nextBytes(data);
    }

    // See https://github.com/netty/netty/pull/2375
    @Test(timeout = 30000)
    public void testAutoReadDisableOutsideChannelRead() throws Throwable {
        run();
    }

    public void testAutoReadDisableOutsideChannelRead(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        TestHandler sh = new TestHandler() {
            private boolean allBytesReceived;
            @Override
            public void channelRead(final ChannelHandlerContext ctx, Object msg) throws Exception {
                assertFalse(allBytesReceived);
                ctx.writeAndFlush(msg);
                ctx.channel().eventLoop().execute(new Runnable() {
                    @Override
                    public void run() {
                        ctx.channel().config().setAutoRead(false);
                        allBytesReceived = true;
                    }
                });
            }
        };
        sb.childHandler(sh);

        TestHandler ch = new TestHandler();
        cb.handler(ch);
        Channel sc = sb.bind().sync().channel();
        Channel cc = cb.connect().sync().channel();
        cc.writeAndFlush(Unpooled.wrappedBuffer(data)).sync();
        Thread.sleep(500);
        cc.writeAndFlush(Unpooled.wrappedBuffer(data)).sync();
        Thread.sleep(500);
        cc.writeAndFlush(Unpooled.wrappedBuffer(data)).sync();
        Thread.sleep(500);

        cc.close().sync();
        sc.close().sync();

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
    }

    // See https://github.com/netty/netty/pull/2375
    @Test(timeout = 30000)
    public void testAutoReadDisableOutsideChannelReadManualRead() throws Throwable {
        run();
    }

    public void testAutoReadDisableOutsideChannelReadManualRead(ServerBootstrap sb, Bootstrap cb) throws Throwable {

        ServerTestHandler sh = new ServerTestHandler();
        sb.childHandler(sh);

        TestHandler ch = new TestHandler();
        cb.handler(ch);
        Channel sc = sb.bind().sync().channel();
        Channel cc = cb.connect().sync().channel();
        cc.writeAndFlush(Unpooled.wrappedBuffer(data)).sync();
        Thread.sleep(500);
        cc.writeAndFlush(Unpooled.wrappedBuffer(data)).sync();
        Thread.sleep(500);
        cc.writeAndFlush(Unpooled.wrappedBuffer(data)).sync();
        Thread.sleep(500);
        sh.await();
        cc.close().sync();
        sc.close().sync();

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
    }

    public static class ServerTestHandler extends TestHandler {
        enum State {
            AUTO_READ,
            SCHEDULED,
            BYTES_RECEIVED,
            READ_SCHEDULED
        }
        private final CountDownLatch latch = new CountDownLatch(1);

        private State state = State.AUTO_READ;

        @Override
        public void channelRead(final ChannelHandlerContext ctx, Object msg) throws Exception {
            ctx.writeAndFlush(msg);
            switch (state) {
                case READ_SCHEDULED:
                    latch.countDown();
                    break;
                case AUTO_READ:
                    state = State.SCHEDULED;
                    ctx.channel().eventLoop().execute(new Runnable() {
                        @Override
                        public void run() {
                            ctx.channel().config().setAutoRead(false);
                            state = State.BYTES_RECEIVED;
                            ctx.channel().eventLoop().schedule(new Runnable() {
                                @Override
                                public void run() {
                                    state = State.READ_SCHEDULED;
                                    ctx.channel().read();
                                }
                            }, 2, TimeUnit.SECONDS);
                        }
                    });
                    break;
                case BYTES_RECEIVED:
                    // Once the state is BYTES_RECEIVED we should not receive anymore data.
                    fail();
                    break;
                case SCHEDULED:
                    // nothing to do
                    break;
            }
        }

        public void await() throws InterruptedException {
            latch.await();
        }
    }

    private static class TestHandler extends ChannelInboundHandlerAdapter {
        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx,
                Throwable cause) throws Exception {
            if (exception.compareAndSet(null, cause)) {
                cause.printStackTrace();
                ctx.close();
            }
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            ReferenceCountUtil.release(msg);
        }
    }
}
