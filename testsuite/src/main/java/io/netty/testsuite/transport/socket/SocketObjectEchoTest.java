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
package io.netty.testsuite.transport.socket;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SocketObjectEchoTest extends AbstractSocketTest {

    static final Random random = new Random();
    static final String[] data = new String[1024];

    static {
        for (int i = 0; i < data.length; i ++) {
            int eLen = random.nextInt(512);
            char[] e = new char[eLen];
            for (int j = 0; j < eLen; j ++) {
                e[j] = (char) ('a' + random.nextInt(26));
            }

            data[i] = new String(e);
        }
    }

    @Test
    public void testObjectEcho(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<ServerBootstrap, Bootstrap>() {
            @Override
            public void run(ServerBootstrap serverBootstrap, Bootstrap bootstrap) throws Throwable {
                testObjectEcho(serverBootstrap, bootstrap);
            }
        });
    }

    public void testObjectEcho(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testObjectEcho(sb, cb, true);
    }

    @Test
    public void testObjectEchoNotAutoRead(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<ServerBootstrap, Bootstrap>() {
            @Override
            public void run(ServerBootstrap serverBootstrap, Bootstrap bootstrap) throws Throwable {
                testObjectEchoNotAutoRead(serverBootstrap, bootstrap);
            }
        });
    }

    public void testObjectEchoNotAutoRead(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testObjectEcho(sb, cb, false);
    }

    private static void testObjectEcho(ServerBootstrap sb, Bootstrap cb, boolean autoRead) throws Throwable {
        sb.childOption(ChannelOption.AUTO_READ, autoRead);
        cb.option(ChannelOption.AUTO_READ, autoRead);

        final EchoHandler sh = new EchoHandler(autoRead);
        final EchoHandler ch = new EchoHandler(autoRead);

        sb.childHandler(new ChannelInitializer<Channel>() {
            @Override
            public void initChannel(Channel sch) throws Exception {
                sch.pipeline().addLast(
                        new ObjectDecoder(ClassResolvers.cacheDisabled(getClass().getClassLoader())),
                        new ObjectEncoder(),
                        sh);
            }
        });

        cb.handler(new ChannelInitializer<Channel>() {
            @Override
            public void initChannel(Channel sch) throws Exception {
                sch.pipeline().addLast(
                        new ObjectDecoder(ClassResolvers.cacheDisabled(getClass().getClassLoader())),
                        new ObjectEncoder(),
                        ch);
            }
        });

        Channel sc = sb.bind().sync().channel();
        Channel cc = cb.connect(sc.localAddress()).sync().channel();
        for (String element : data) {
            cc.writeAndFlush(element);
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

    private static class EchoHandler extends ChannelInboundHandlerAdapter {
        private final boolean autoRead;
        volatile Channel channel;
        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();
        volatile int counter;

        EchoHandler(boolean autoRead) {
            this.autoRead = autoRead;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx)
                throws Exception {
            channel = ctx.channel();
            if (!autoRead) {
                ctx.read();
            }
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            assertEquals(data[counter], msg);

            if (channel.parent() != null) {
                channel.write(msg);
            }

            counter ++;
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
        public void exceptionCaught(ChannelHandlerContext ctx,
                Throwable cause) throws Exception {
            if (exception.compareAndSet(null, cause)) {
                ctx.close();
            }
        }
    }
}
