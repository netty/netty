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
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;
import org.junit.Test;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

public class SocketStringEchoTest extends AbstractSocketTest {

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

    @Test(timeout = 60000)
    public void testStringEcho() throws Throwable {
        run();
    }

    public void testStringEcho(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testStringEcho(sb, cb, true);
    }

    @Test(timeout = 60000)
    public void testStringEchoNotAutoRead() throws Throwable {
        run();
    }

    public void testStringEchoNotAutoRead(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        testStringEcho(sb, cb, false);
    }

    private static void testStringEcho(ServerBootstrap sb, Bootstrap cb, boolean autoRead) throws Throwable {
        sb.childOption(ChannelOption.AUTO_READ, autoRead);
        cb.option(ChannelOption.AUTO_READ, autoRead);

        Promise<Void> serverDonePromise = ImmediateEventExecutor.INSTANCE.newPromise();
        Promise<Void> clientDonePromise = ImmediateEventExecutor.INSTANCE.newPromise();
        final StringEchoHandler sh = new StringEchoHandler(autoRead, serverDonePromise);
        final StringEchoHandler ch = new StringEchoHandler(autoRead, clientDonePromise);

        sb.childHandler(new ChannelInitializer<Channel>() {
            @Override
            public void initChannel(Channel sch) throws Exception {
                sch.pipeline().addLast("framer", new DelimiterBasedFrameDecoder(512, Delimiters.lineDelimiter()));
                sch.pipeline().addLast("decoder", new StringDecoder(CharsetUtil.ISO_8859_1));
                sch.pipeline().addBefore("decoder", "encoder", new StringEncoder(CharsetUtil.ISO_8859_1));
                sch.pipeline().addAfter("decoder", "handler", sh);
            }
        });

        cb.handler(new ChannelInitializer<Channel>() {
            @Override
            public void initChannel(Channel sch) throws Exception {
                sch.pipeline().addLast("framer", new DelimiterBasedFrameDecoder(512, Delimiters.lineDelimiter()));
                sch.pipeline().addLast("decoder", new StringDecoder(CharsetUtil.ISO_8859_1));
                sch.pipeline().addBefore("decoder", "encoder", new StringEncoder(CharsetUtil.ISO_8859_1));
                sch.pipeline().addAfter("decoder", "handler", ch);
            }
        });

        Channel sc = sb.bind().sync().channel();
        Channel cc = cb.connect(sc.localAddress()).sync().channel();
        for (String element : data) {
            String delimiter = random.nextBoolean() ? "\r\n" : "\n";
            cc.writeAndFlush(element + delimiter);
        }

        ch.donePromise.sync();
        sh.donePromise.sync();
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

    static class StringEchoHandler extends SimpleChannelInboundHandler<String> {
        private final boolean autoRead;
        private final Promise<Void> donePromise;
        private int dataIndex;
        volatile Channel channel;
        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();

        StringEchoHandler(boolean autoRead, Promise<Void> donePromise) {
            this.autoRead = autoRead;
            this.donePromise = donePromise;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            channel = ctx.channel();
            if (!autoRead) {
                ctx.read();
            }
        }

        @Override
        public void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
            if (!data[dataIndex].equals(msg)) {
                donePromise.tryFailure(new IllegalStateException("index: " + dataIndex + " didn't match!"));
                ctx.close();
                return;
            }

            if (channel.parent() != null) {
                String delimiter = random.nextBoolean() ? "\r\n" : "\n";
                channel.write(msg + delimiter);
            }

            if (++dataIndex >= data.length) {
                donePromise.setSuccess(null);
            }
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
                donePromise.tryFailure(new IllegalStateException("exceptionCaught: " + ctx.channel(), cause));
                ctx.close();
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            donePromise.tryFailure(new IllegalStateException("channelInactive: " + ctx.channel()));
        }
    }
}
