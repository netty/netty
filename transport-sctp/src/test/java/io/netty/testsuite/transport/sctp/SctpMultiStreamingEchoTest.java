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

import io.netty.bootstrap.ClientBootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ChannelBuffer;
import io.netty.buffer.ChannelBuffers;
import io.netty.channel.*;
import io.netty.channel.sctp.SctpClientSocketChannelFactory;
import io.netty.channel.sctp.SctpFrame;
import io.netty.channel.sctp.SctpServerSocketChannelFactory;
import io.netty.testsuite.util.SctpSocketAddresses;
import io.netty.util.internal.ExecutorUtil;
import org.junit.AfterClass;
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

public class SctpMultiStreamingEchoTest {
    private static final Random random = new Random();

    static final SctpFrame [] sctpFrames = new SctpFrame [4];


    private static ExecutorService executor;

    static ChannelBuffer makeRandomFrame() {
        byte [] data = new byte[512];
        random.nextBytes(data);
        return ChannelBuffers.wrappedBuffer(data);
    }

    static {
        int protocolId = 0;//unknown
        for(int streamNumber = 0; streamNumber <= 3; streamNumber ++) {
            sctpFrames [streamNumber] = new SctpFrame(protocolId, streamNumber, makeRandomFrame());
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

    protected ChannelFactory newServerSocketChannelFactory(Executor executor) {
        return new SctpServerSocketChannelFactory(executor);
    }

    protected ChannelFactory newClientSocketChannelFactory(Executor executor) {
        return new SctpClientSocketChannelFactory(executor);
    }

    @Test(timeout = 10000)
    public void testMultiStreamingEcho() throws Throwable {
        ServerBootstrap sb = new ServerBootstrap(newServerSocketChannelFactory(executor));

        ClientBootstrap cb = new ClientBootstrap(newClientSocketChannelFactory(executor));
        cb.setOption("sctpInitMaxStreams", 4);

        EchoHandler sh = new EchoHandler();
        EchoHandler ch = new EchoHandler();

        sb.getPipeline().addLast("handler", sh);

        cb.getPipeline().addLast("handler", ch);

        Channel sc = sb.bind(new InetSocketAddress(SctpSocketAddresses.LOOP_BACK, 0));
        int port = ((InetSocketAddress) sc.getLocalAddress()).getPort();

        ChannelFuture ccf = cb.connect(new InetSocketAddress(SctpSocketAddresses.LOOP_BACK, port));
        assertTrue(ccf.awaitUninterruptibly().isSuccess());

        Channel cc = ccf.getChannel();

        for(SctpFrame sctpFrame: sctpFrames) {
             cc.write(sctpFrame);
        }

        while (sh.counter < sctpFrames.length) {
            Thread.sleep(5);
        }
        while (ch.counter < sctpFrames.length) {
            Thread.sleep(5);
        }

        assertEquals(sctpFrames.length, sh.counter);
        assertEquals(sctpFrames.length, ch.counter);


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
            channel = e.getChannel();
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
                throws Exception {
            SctpFrame sctpFrame = (SctpFrame) e.getMessage();

            assertEquals(sctpFrames[counter], sctpFrame);

            if (channel.getParent() != null) {
                channel.write(sctpFrame);
            }

            counter ++ ;
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
                throws Exception {
            if (exception.compareAndSet(null, e.getCause())) {
                e.getChannel().close();
            }
        }
    }
}
