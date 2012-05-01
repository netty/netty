/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.testsuite.transport;

import io.netty.bootstrap.ClientBootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.sctp.codec.SctpFrameDecoder;
import io.netty.channel.sctp.codec.SctpFrameEncoder;
import io.netty.handler.codec.frame.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.frame.Delimiters;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.testsuite.util.SctpTestUtil;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.ExecutorUtil;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public abstract class AbstractSocketStringEchoTest {

    static final Random random = new Random();
    static final String[] data = new String[1024];

    private static ExecutorService executor;

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

    @BeforeClass
    public static void init() {
        executor = Executors.newCachedThreadPool();
    }

    @AfterClass
    public static void destroy() {
        ExecutorUtil.terminate(executor);
    }

    protected abstract ChannelFactory newServerSocketChannelFactory(Executor executor);
    protected abstract ChannelFactory newClientSocketChannelFactory(Executor executor);

    @Test
    public void testStringEcho() throws Throwable {
        Assume.assumeTrue(SctpTestUtil.isSctpSupported());

        ServerBootstrap sb = new ServerBootstrap(newServerSocketChannelFactory(executor));
        ClientBootstrap cb = new ClientBootstrap(newClientSocketChannelFactory(executor));

        EchoHandler sh = new EchoHandler();
        EchoHandler ch = new EchoHandler();

        sb.getPipeline().addFirst("sctp-decoder", new SctpFrameDecoder());
        sb.getPipeline().addFirst("sctp-encoder", new SctpFrameEncoder());
        sb.getPipeline().addLast("framer", new DelimiterBasedFrameDecoder(512, Delimiters.lineDelimiter()));
        sb.getPipeline().addLast("decoder", new StringDecoder(CharsetUtil.ISO_8859_1));
        sb.getPipeline().addLast("encoder", new StringEncoder(CharsetUtil.ISO_8859_1));
        sb.getPipeline().addLast("handler", sh);

        cb.getPipeline().addFirst("sctp-decoder", new SctpFrameDecoder());
        cb.getPipeline().addFirst("sctp-encoder", new SctpFrameEncoder());
        cb.getPipeline().addLast("framer", new DelimiterBasedFrameDecoder(512, Delimiters.lineDelimiter()));
        cb.getPipeline().addLast("decoder", new StringDecoder(CharsetUtil.ISO_8859_1));
        cb.getPipeline().addLast("encoder", new StringEncoder(CharsetUtil.ISO_8859_1));
        cb.getPipeline().addLast("handler", ch);

        Channel sc = sb.bind(new InetSocketAddress(SctpTestUtil.LOOP_BACK, 0));
        int port = ((InetSocketAddress) sc.getLocalAddress()).getPort();

        ChannelFuture ccf = cb.connect(new InetSocketAddress(SctpTestUtil.LOOP_BACK, port));
        assertTrue(ccf.awaitUninterruptibly().isSuccess());

        Channel cc = ccf.channel();
        for (String element : data) {
            String delimiter = random.nextBoolean() ? "\r\n" : "\n";
            cc.write(element + delimiter);
        }

        while (ch.counter < data.length) {
            if (sh.exception.get() != null) {
                break;
            }
            if (ch.exception.get() != null) {
                break;
            }

            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                // Ignore.
            }
        }

        while (sh.counter < data.length) {
            if (sh.exception.get() != null) {
                break;
            }
            if (ch.exception.get() != null) {
                break;
            }

            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                // Ignore.
            }
        }

        sh.channel.close().awaitUninterruptibly();
        ch.channel.close().awaitUninterruptibly();
        sc.close().awaitUninterruptibly();

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

    private static class EchoHandler extends SimpleChannelUpstreamHandler {
        volatile Channel channel;
        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();
        volatile int counter;

        EchoHandler() {
        }

        @Override
        public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e)
                throws Exception {
            channel = e.channel();
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
                throws Exception {

            String m = (String) e.getMessage();
            assertEquals(data[counter], m);

            if (channel.getParent() != null) {
                String delimiter = random.nextBoolean() ? "\r\n" : "\n";
                channel.write(m + delimiter);
            }

            counter ++;
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
                throws Exception {
            if (exception.compareAndSet(null, e.cause())) {
                e.channel().close();
            }
        }
    }
}
