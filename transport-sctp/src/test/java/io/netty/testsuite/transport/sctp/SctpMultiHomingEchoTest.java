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
package io.netty.testsuite.transport.sctp;

import static org.junit.Assert.*;
import io.netty.bootstrap.ClientBootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ChannelBuffer;
import io.netty.buffer.ChannelBuffers;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelStateEvent;
import io.netty.channel.ExceptionEvent;
import io.netty.channel.MessageEvent;
import io.netty.channel.sctp.SctpChannel;
import io.netty.channel.sctp.SctpClientSocketChannelFactory;
import io.netty.channel.sctp.SctpNotificationEvent;
import io.netty.channel.sctp.SctpServerChannel;
import io.netty.channel.sctp.SctpServerSocketChannelFactory;
import io.netty.channel.sctp.codec.SctpFrameDecoder;
import io.netty.channel.sctp.codec.SctpFrameEncoder;
import io.netty.channel.sctp.handler.SimpleSctpChannelHandler;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;
import io.netty.testsuite.util.SctpTestUtil;
import io.netty.util.internal.ExecutorUtil;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

public class SctpMultiHomingEchoTest {
    
    private static final InternalLogger logger =
        InternalLoggerFactory.getInstance(SctpMultiHomingEchoTest.class);
    
    private static final Random random = new Random();
    static final byte[] data = new byte[4096];//could not test ultra jumbo frames

    private static ExecutorService executor;

    static {
        random.nextBytes(data);
    }

    @BeforeClass
    public static void init() {
        executor = Executors.newCachedThreadPool();
    }

    @AfterClass
    public static void destroy() {
        ExecutorUtil.terminate(executor);
    }

    protected ChannelFactory newServerSocketChannelFactory(Executor executor) {
        return new SctpServerSocketChannelFactory(executor);
    }

    protected ChannelFactory newClientSocketChannelFactory(Executor executor) {
        return new SctpClientSocketChannelFactory(executor);
    }

    @Test(timeout = 15000)
    public void testSimpleEcho() throws Throwable {
        Assume.assumeTrue(SctpTestUtil.isSctpSupported());

        ServerBootstrap sb = new ServerBootstrap(newServerSocketChannelFactory(executor));

        ClientBootstrap cb = new ClientBootstrap(newClientSocketChannelFactory(executor));


        EchoHandler sh = new EchoHandler();
        EchoHandler ch = new EchoHandler();

        sb.getPipeline().addLast("sctp-decoder", new SctpFrameDecoder());
        sb.getPipeline().addLast("sctp-encoder", new SctpFrameEncoder());
        sb.getPipeline().addLast("handler", sh);

        cb.getPipeline().addLast("sctp-decoder", new SctpFrameDecoder());
        cb.getPipeline().addLast("sctp-encoder", new SctpFrameEncoder());
        cb.getPipeline().addLast("handler", ch);

        SctpServerChannel serverChannel = (SctpServerChannel) sb.bind(new InetSocketAddress(SctpTestUtil.LOOP_BACK, 0));
        int port = serverChannel.getLocalAddress().getPort();

        ChannelFuture multiHomingServerBindFuture = serverChannel.bindAddress(InetAddress.getByName(SctpTestUtil.LOOP_BACK2));
        assertTrue(multiHomingServerBindFuture.awaitUninterruptibly().isSuccess());

        ChannelFuture bindFuture = cb.bind(new InetSocketAddress(SctpTestUtil.LOOP_BACK, 0));
        assertTrue(bindFuture.awaitUninterruptibly().isSuccess());

        SctpChannel clientChannel = (SctpChannel) bindFuture.getChannel();

        //adding a muti-homing address to client channel
        ChannelFuture multiHomingBindFuture = clientChannel.bindAddress(InetAddress.getByName(SctpTestUtil.LOOP_BACK2));
        assertTrue(multiHomingBindFuture.awaitUninterruptibly().isSuccess());

        ChannelFuture connectFuture = clientChannel.connect(new InetSocketAddress(SctpTestUtil.LOOP_BACK, port));
        assertTrue(connectFuture.awaitUninterruptibly().isSuccess());

        assertEquals("Client local addresses count should be 2", 2, clientChannel.getAllLocalAddresses().size());
        assertEquals("Client remote addresses count should be 2", 2, clientChannel.getAllRemoteAddresses().size());

        assertEquals("Server local addresses count should be 2", 2, serverChannel.getAllLocalAddresses().size());

        for (int i = 0; i < data.length;) {
            int length = Math.min(random.nextInt(1024 * 64), data.length - i);
            clientChannel.write(ChannelBuffers.wrappedBuffer(data, i, length));
            i += length;
        }

        while (ch.counter < data.length) {
            if (sh.exception.get() != null) {
                break;
            }
            if (ch.exception.get() != null) {
                break;
            }

            try {
                Thread.sleep(5);
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
                Thread.sleep(5);
            } catch (InterruptedException e) {
                // Ignore.
            }
        }

        //removing already added muti-homing address from client channel
        ChannelFuture multiHomingUnbindFuture = clientChannel.unbindAddress(InetAddress.getByName(SctpTestUtil.LOOP_BACK2));
        assertTrue(multiHomingUnbindFuture.awaitUninterruptibly().isSuccess());

        ChannelFuture multiHomingServerUnbindFuture = serverChannel.unbindAddress(InetAddress.getByName(SctpTestUtil.LOOP_BACK2));
        assertTrue(multiHomingServerUnbindFuture.awaitUninterruptibly().isSuccess());


        sh.channel.close().awaitUninterruptibly();
        ch.channel.close().awaitUninterruptibly();
        serverChannel.close().awaitUninterruptibly();

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

    private static class EchoHandler extends SimpleSctpChannelHandler {
        volatile Channel channel;
        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();
        volatile int counter;

        EchoHandler() {
        }

        @Override
        public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e)
                throws Exception {
            channel = e.getChannel();
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
                throws Exception {
            ChannelBuffer m = (ChannelBuffer) e.getMessage();
            byte[] actual = new byte[m.readableBytes()];
            m.getBytes(0, actual);

            int lastIdx = counter;
            for (int i = 0; i < actual.length; i ++) {
                assertEquals(data[i + lastIdx], actual[i]);
            }

            if (channel.getParent() != null) {
                channel.write(m);
            }

            counter += actual.length;
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
                throws Exception {
            if (exception.compareAndSet(null, e.getCause())) {
                e.getChannel().close();
            }
        }

        @Override
        public void sctpNotificationReceived(ChannelHandlerContext ctx, SctpNotificationEvent event) {
            logger.info("SCTP notification event received :" + event);
        }
    }
}
