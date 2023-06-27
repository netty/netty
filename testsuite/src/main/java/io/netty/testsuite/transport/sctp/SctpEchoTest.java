/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.testsuite.transport.sctp;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.sctp.SctpChannel;
import io.netty.handler.codec.sctp.SctpInboundByteStreamHandler;
import io.netty.handler.codec.sctp.SctpMessageCompletionHandler;
import io.netty.handler.codec.sctp.SctpOutboundByteStreamHandler;
import io.netty.testsuite.util.TestUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.TestInfo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class SctpEchoTest extends AbstractSctpTest {

    private static final Random random = new Random();
    static final byte[] data = new byte[4096]; //could not test ultra jumbo frames

    static {
        random.nextBytes(data);
    }

    @Test
    public void testSimpleEcho(TestInfo testInfo) throws Throwable {
        assumeTrue(TestUtils.isSctpSupported());
        run(testInfo, new Runner<ServerBootstrap, Bootstrap>() {
            @Override
            public void run(ServerBootstrap serverBootstrap, Bootstrap bootstrap) throws Throwable {
                testSimpleEcho(serverBootstrap, bootstrap);
            }
        });
    }

    public void testSimpleEcho(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testSimpleEcho0(sb, cb, false);
    }

    @Test
    public void testSimpleEchoUnordered(TestInfo testInfo) throws Throwable {
        assumeTrue(TestUtils.isSctpSupported());
        run(testInfo, new Runner<ServerBootstrap, Bootstrap>() {
            @Override
            public void run(ServerBootstrap serverBootstrap, Bootstrap bootstrap) throws Throwable {
                testSimpleEchoUnordered(serverBootstrap, bootstrap);
            }
        });
    }

    public void testSimpleEchoUnordered(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testSimpleEcho0(sb, cb, true);
    }

    private static void testSimpleEcho0(ServerBootstrap sb, Bootstrap cb, final boolean unordered) throws Throwable {
        final EchoHandler sh = new EchoHandler();
        final EchoHandler ch = new EchoHandler();

        sb.childHandler(new ChannelInitializer<SctpChannel>() {
            @Override
            public void initChannel(SctpChannel c) throws Exception {
                c.pipeline().addLast(
                        new SctpMessageCompletionHandler(),
                        new SctpInboundByteStreamHandler(0, 0),
                        new SctpOutboundByteStreamHandler(0, 0, unordered),
                        sh);
            }
        });
        cb.handler(new ChannelInitializer<SctpChannel>() {
            @Override
            public void initChannel(SctpChannel c) throws Exception {
                c.pipeline().addLast(
                        new SctpMessageCompletionHandler(),
                        new SctpInboundByteStreamHandler(0, 0),
                        new SctpOutboundByteStreamHandler(0, 0, unordered),
                        ch);
            }
        });

        Channel sc = sb.bind().sync().channel();
        Channel cc = cb.connect(sc.localAddress()).sync().channel();

        for (int i = 0; i < data.length;) {
            int length = Math.min(random.nextInt(1024 * 64), data.length - i);
            cc.writeAndFlush(Unpooled.wrappedBuffer(data, i, length));
            i += length;
        }

        while (ch.counter < data.length) {
            if (sh.exception.get() != null) {
                break;
            }
            if (ch.exception.get() != null) {
                break;
            }

            Thread.sleep(50);
        }

        while (sh.counter < data.length) {
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

    private static class EchoHandler extends SimpleChannelInboundHandler<ByteBuf> {
        volatile Channel channel;
        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();
        volatile int counter;

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            channel = ctx.channel();
        }

        @Override
        public void channelRead0(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
            byte[] actual = new byte[in.readableBytes()];
            in.readBytes(actual);

            int lastIdx = counter;
            for (int i = 0; i < actual.length; i++) {
                assertEquals(data[i + lastIdx], actual[i]);
            }

            if (channel.parent() != null) {
                channel.writeAndFlush(Unpooled.wrappedBuffer(actual));
            }

            counter += actual.length;
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            if (exception.compareAndSet(null, cause)) {
                ctx.close();
            }
        }
    }
}
