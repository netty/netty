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
import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.buffer.api.Buffer;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.SimpleChannelInboundHandler;
import io.netty5.handler.codec.FixedLengthFrameDecoder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SocketFixedLengthEchoTest extends AbstractSocketTest {

    private static final Random random = new Random();
    static final byte[] data = new byte[1048576];

    static {
        random.nextBytes(data);
    }

    @Test
    public void testFixedLengthEcho(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testFixedLengthEcho);
    }

    @Test
    public void testFixedLengthEchoNotAutoRead(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testFixedLengthEchoNotAutoRead);
    }

    public void testFixedLengthEcho(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        enableNewBufferAPI(sb, cb);
        testFixedLengthEcho(sb, cb, true);
    }

    public void testFixedLengthEchoNotAutoRead(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        enableNewBufferAPI(sb, cb);
        testFixedLengthEcho(sb, cb, false);
    }

    private static void testFixedLengthEcho(ServerBootstrap sb, Bootstrap cb, boolean autoRead) throws Throwable {
        final EchoHandler sh = new EchoHandler(autoRead);
        final EchoHandler ch = new EchoHandler(autoRead);

        sb.childOption(ChannelOption.AUTO_READ, autoRead);
        sb.childHandler(new ChannelInitializer<Channel>() {
            @Override
            public void initChannel(Channel sch) throws Exception {
                sch.pipeline().addLast("decoder", new FixedLengthFrameDecoder(1024));
                sch.pipeline().addAfter("decoder", "handler", sh);
            }
        });

        cb.option(ChannelOption.AUTO_READ, autoRead);
        cb.handler(new ChannelInitializer<Channel>() {
            @Override
            public void initChannel(Channel sch) throws Exception {
                sch.pipeline().addLast("decoder", new FixedLengthFrameDecoder(1024));
                sch.pipeline().addAfter("decoder", "handler", ch);
            }
        });

        Channel sc = sb.bind().get();
        Channel cc = cb.connect(sc.localAddress()).get();

        try (Buffer buffer = sc.bufferAllocator().copyOf(data)) {
            for (int i = 0; i < data.length;) {
                int length = Math.min(random.nextInt(1024 * 3), data.length - i);
                cc.writeAndFlush(buffer.readSplit(length));
                i += length;
            }
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

    private static class EchoHandler extends SimpleChannelInboundHandler<Buffer> {
        private final boolean autoRead;
        volatile Channel channel;
        final AtomicReference<Throwable> exception = new AtomicReference<>();
        volatile int counter;

        EchoHandler(boolean autoRead) {
            this.autoRead = autoRead;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            channel = ctx.channel();
            if (!autoRead) {
                ctx.read();
            }
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, Buffer msg) throws Exception {
            assertEquals(1024, msg.readableBytes());

            byte[] actual = new byte[msg.readableBytes()];
            msg.copyInto(msg.readerOffset(), actual, 0, msg.readableBytes());

            int lastIdx = counter;
            for (int i = 0; i < actual.length; i ++) {
                assertEquals(data[i + lastIdx], actual[i]);
            }

            if (channel.parent() != null) {
                channel.write(msg.split());
            }

            counter += actual.length;
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            try {
                ctx.flush();
            } finally {
                if (!autoRead) {
                    ctx.read();
                }
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            if (exception.compareAndSet(null, cause)) {
                ctx.close();
            }
        }
    }
}
