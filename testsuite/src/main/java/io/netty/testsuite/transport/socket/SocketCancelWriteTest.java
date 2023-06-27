/*
 * Copyright 2014 The Netty Project
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
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SocketCancelWriteTest extends AbstractSocketTest {

    @Test
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void testCancelWrite(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<ServerBootstrap, Bootstrap>() {
            @Override
            public void run(ServerBootstrap serverBootstrap, Bootstrap bootstrap) throws Throwable {
                testCancelWrite(serverBootstrap, bootstrap);
            }
        });
    }

    public void testCancelWrite(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        final TestHandler sh = new TestHandler();
        final TestHandler ch = new TestHandler();
        final ByteBuf a = Unpooled.buffer().writeByte('a');
        final ByteBuf b = Unpooled.buffer().writeByte('b');
        final ByteBuf c = Unpooled.buffer().writeByte('c');
        final ByteBuf d = Unpooled.buffer().writeByte('d');
        final ByteBuf e = Unpooled.buffer().writeByte('e');

        cb.handler(ch);
        sb.childHandler(sh);

        Channel sc = sb.bind().sync().channel();
        Channel cc = cb.connect(sc.localAddress()).sync().channel();

        ChannelFuture f = cc.write(a);
        assertTrue(f.cancel(false));
        cc.writeAndFlush(b);
        cc.write(c);
        ChannelFuture f2 = cc.write(d);
        assertTrue(f2.cancel(false));
        cc.writeAndFlush(e);

        while (sh.counter < 3) {
            if (sh.exception.get() != null) {
                break;
            }
            if (ch.exception.get() != null) {
                break;
            }
            Thread.sleep(50);
        }
        sh.channel.close().sync();
        ch.channel.close().sync();
        sc.close().sync();

        if (sh.exception.get() != null && !(sh.exception.get() instanceof IOException)) {
            throw sh.exception.get();
        }
        if (sh.exception.get() != null) {
            throw sh.exception.get();
        }
        if (ch.exception.get() != null && !(ch.exception.get() instanceof IOException)) {
            throw ch.exception.get();
        }
        if (ch.exception.get() != null) {
            throw ch.exception.get();
        }
        assertEquals(0, ch.counter);
        assertEquals(Unpooled.wrappedBuffer(new byte[]{'b', 'c', 'e'}), sh.received);
    }

    private static class TestHandler extends SimpleChannelInboundHandler<ByteBuf> {
        volatile Channel channel;
        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();
        volatile int counter;
        final ByteBuf received = Unpooled.buffer();
        @Override
        public void channelActive(ChannelHandlerContext ctx)
                throws Exception {
            channel = ctx.channel();
        }

        @Override
        public void channelRead0(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
            counter += in.readableBytes();
            received.writeBytes(in);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx,
                Throwable cause) throws Exception {
            if (exception.compareAndSet(null, cause)) {
                ctx.close();
            }
        }
    }
}
