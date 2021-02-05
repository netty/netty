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
package io.netty.testsuite.transport.socket;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.StringUtil;
import org.junit.AssumptionViolatedException;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.Charset;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static io.netty.buffer.ByteBufUtil.writeAscii;
import static io.netty.buffer.UnpooledByteBufAllocator.DEFAULT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SocketConnectTest extends AbstractSocketTest {

    @Test(timeout = 30000)
    public void testLocalAddressAfterConnect() throws Throwable {
        run();
    }

    public void testLocalAddressAfterConnect(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        Channel serverChannel = null;
        Channel clientChannel = null;
        try {
            final Promise<InetSocketAddress> localAddressPromise = ImmediateEventExecutor.INSTANCE.newPromise();
            serverChannel = sb.childHandler(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelActive(ChannelHandlerContext ctx) throws Exception {
                            localAddressPromise.setSuccess((InetSocketAddress) ctx.channel().localAddress());
                        }
                    }).bind().syncUninterruptibly().channel();

            clientChannel = cb.handler(new ChannelInboundHandlerAdapter()).register().syncUninterruptibly().channel();

            assertNull(clientChannel.localAddress());
            assertNull(clientChannel.remoteAddress());

            clientChannel.connect(serverChannel.localAddress()).syncUninterruptibly().channel();
            assertLocalAddress((InetSocketAddress) clientChannel.localAddress());
            assertNotNull(clientChannel.remoteAddress());

            assertLocalAddress(localAddressPromise.get());
        } finally {
            if (clientChannel != null) {
                clientChannel.close().syncUninterruptibly();
            }
            if (serverChannel != null) {
                serverChannel.close().syncUninterruptibly();
            }
        }
    }

    @Test(timeout = 3000)
    public void testChannelEventsFiredWhenClosedDirectly() throws Throwable {
        run();
    }

    public void testChannelEventsFiredWhenClosedDirectly(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        final BlockingQueue<Integer> events = new LinkedBlockingQueue<Integer>();

        Channel sc = null;
        Channel cc = null;
        try {
            sb.childHandler(new ChannelInboundHandlerAdapter());
            sc = sb.bind().syncUninterruptibly().channel();

            cb.handler(new ChannelInboundHandlerAdapter() {
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
            cc = cb.connect(sc.localAddress()).addListener(ChannelFutureListener.CLOSE).
                    syncUninterruptibly().channel();
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

    @Test//(timeout = 3000)
    public void testWriteWithFastOpenBeforeConnect() throws Throwable {
        run();
    }

    public void testWriteWithFastOpenBeforeConnect(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        enableTcpFastOpen(sb, cb);

        cb.handler(new BootstrapHandler());
        sb.childHandler(new ServerBootstrapHandler());

        Channel sc = sb.bind().sync().channel();
        {
            ChannelFuture register = cb.register();
            Channel channel = register.sync().channel();
            ChannelFuture write = channel.write(writeAscii(DEFAULT, "[fastopen]"));
            SocketAddress remoteAddress = sc.localAddress();
            ChannelFuture connectFuture = channel.connect(remoteAddress);
//        ChannelFuture connectFuture = cb.connect(remoteAddress); // xxx doesn't work
            Channel cc = connectFuture.sync().channel();
            cc.writeAndFlush(writeAscii(DEFAULT, "[normal data]")).sync();
            write.sync().isDone();
            cc.disconnect().sync();
        }
        {
            ChannelFuture register = cb.register();
            Channel channel = register.sync().channel();
            ChannelFuture write = channel.write(writeAscii(DEFAULT, "[fastopen]"));
            SocketAddress remoteAddress = sc.localAddress();
            ChannelFuture connectFuture = channel.connect(remoteAddress);
//        ChannelFuture connectFuture = cb.connect(remoteAddress); // xxx doesn't work
            Channel cc = connectFuture.sync().channel();
            cc.writeAndFlush(writeAscii(DEFAULT, "[normal data]")).sync();
            write.sync().isDone();
            cc.disconnect().sync();
        }
    }

    protected void enableTcpFastOpen(ServerBootstrap sb, Bootstrap cb) {
        throw new AssumptionViolatedException(
                "Support for testing TCP_FASTOPEN not enabled for " + StringUtil.simpleClassName(this));
    }

    private static void assertLocalAddress(InetSocketAddress address) {
        assertTrue(address.getPort() > 0);
        assertFalse(address.getAddress().isAnyLocalAddress());
    }

    @ChannelHandler.Sharable
    private static class BootstrapHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            debug("cb channel active: " + ctx);
            super.channelActive(ctx);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            debug("cb channel read: " + ctx + " => " + msg);
            if (msg instanceof ByteBuf) {
                ByteBuf buf = (ByteBuf) msg;
                debug(buf.readCharSequence(buf.readableBytes(), Charset.forName("US-ASCII")).toString());
            }
            super.channelRead(ctx, msg);
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            debug("cb channel read complete: " + ctx);
            super.channelReadComplete(ctx);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            debug("cb exception:");
            cause.printStackTrace();
            super.exceptionCaught(ctx, cause);
        }
    }

    @ChannelHandler.Sharable
    private static class ServerBootstrapHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            debug("sb channel active: " + ctx);
            ctx.read();
            super.channelActive(ctx);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            debug("sb channel read: " + ctx + " => " + msg);
            if (msg instanceof ByteBuf) {
                ByteBuf buf = (ByteBuf) msg;
                debug(buf.readCharSequence(buf.readableBytes(), Charset.forName("US-ASCII")).toString());
            }
            super.channelRead(ctx, msg);
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            debug("sb channel read complete: " + ctx);
            super.channelReadComplete(ctx);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            debug("sb exception:");
            cause.printStackTrace();
            super.exceptionCaught(ctx, cause);
        }
    }

    private static void debug(String output) {
        synchronized (System.out) {
            System.out.flush();
            System.err.println(output);
            System.err.flush();
        }
    }
}
