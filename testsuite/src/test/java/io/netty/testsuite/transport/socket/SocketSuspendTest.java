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

import static org.junit.Assert.*;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundByteHandlerAdapter;
import io.netty.channel.ChannelInboundMessageHandlerAdapter;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.socket.SocketChannel;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Ignore;
import org.junit.Test;

public class SocketSuspendTest extends AbstractSocketTest {

    private static final Random random = new Random();
    static final byte[] data = new byte[1048576];

    static {
        random.nextBytes(data);
    }

    @Ignore
    @Test
    public void testSuspendAccept() throws Throwable {
        run();
    }

    public void testSuspendAccept(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        ServerHandler handler = new ServerHandler();
        GroupHandler sh = new GroupHandler();
        GroupHandler ch = new GroupHandler();
        
        sb.handler(handler);
        sb.childHandler(sh);
        Channel sc = sb.bind().sync().channel();

        cb.handler(ch);
        cb.connect().sync();
        Thread.sleep(1000);
        
        Bootstrap cb2 = currentBootstrap.newInstance();
        cb2.handler(ch);

        cb2.remoteAddress(addr);
        
        ChannelFuture cf = cb2.connect();
        assertFalse(cf.await(2, TimeUnit.SECONDS));
        sc.pipeline().context(handler).readable(true);
        assertTrue(cf.await(2, TimeUnit.SECONDS));
        sh.group.close().awaitUninterruptibly();
        ch.group.close().awaitUninterruptibly();
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
    private static class ServerHandler extends ChannelInboundMessageHandlerAdapter<SocketChannel> {

        @Override
        public void messageReceived(ChannelHandlerContext ctx, SocketChannel msg) throws Exception {
            ctx.nextInboundMessageBuffer().add(msg);
            ctx.readable(false);
        }

    }
    
    @ChannelHandler.Sharable
    private static class GroupHandler extends ChannelInboundByteHandlerAdapter {
        final ChannelGroup group = new DefaultChannelGroup();
        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();

        @Override
        public void channelActive(ChannelHandlerContext ctx)
                throws Exception {
            group.add(ctx.channel());
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx,
                Throwable cause) throws Exception {
            if (exception.compareAndSet(null, cause)) {
                ctx.close();
            }
        }

        @Override
        public void inboundBufferUpdated(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
            in.clear();
        }
    }
}
