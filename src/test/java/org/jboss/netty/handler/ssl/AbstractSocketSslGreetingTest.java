/*
 * Copyright 2014 The Netty Project
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
package org.jboss.netty.handler.ssl;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.ssl.AbstractSocketSslEchoTest.SSLEngineFactory;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.util.TestUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public abstract class AbstractSocketSslGreetingTest {
    static final InternalLogger logger =
            InternalLoggerFactory.getInstance(AbstractSocketSslGreetingTest.class);

    @Parameters(name = "{index}: serverEngine = {0}, clientEngine = {1}")
    public static Collection<SSLEngineFactory[]> engines() throws Exception {
        return AbstractSocketSslEchoTest.engines();
    }

    private final ChannelBuffer greeting = ChannelBuffers.wrappedBuffer(new byte[] {'a'});

    protected abstract ChannelFactory newServerSocketChannelFactory(Executor executor);
    protected abstract ChannelFactory newClientSocketChannelFactory(Executor executor);

    private final SSLEngineFactory serverEngineFactory;
    private final SSLEngineFactory clientEngineFactory;

    protected AbstractSocketSslGreetingTest(
            SSLEngineFactory serverEngineFactory, SSLEngineFactory clientEngineFactory) {
        this.serverEngineFactory = serverEngineFactory;
        this.clientEngineFactory = clientEngineFactory;
    }

    @Test
    public void testSslEcho() throws Throwable {
        ServerBootstrap sb = new ServerBootstrap(newServerSocketChannelFactory(Executors.newCachedThreadPool()));
        ClientBootstrap cb = new ClientBootstrap(newClientSocketChannelFactory(Executors.newCachedThreadPool()));

        ServerHandler sh = new ServerHandler();
        ClientHandler ch = new ClientHandler();

        SSLEngine sse = serverEngineFactory.newEngine();
        SSLEngine cse = clientEngineFactory.newEngine();
        sse.setUseClientMode(false);
        cse.setUseClientMode(true);

        // Workaround for blocking I/O transport write-write dead lock.
        sb.setOption("receiveBufferSize", 1048576);
        sb.setOption("receiveBufferSize", 1048576);

        sb.getPipeline().addFirst("ssl", new SslHandler(sse));
        sb.getPipeline().addLast("handler", sh);
        cb.getPipeline().addFirst("ssl", new SslHandler(cse));
        cb.getPipeline().addLast("handler", ch);

        Channel sc = sb.bind(new InetSocketAddress(0));
        int port = ((InetSocketAddress) sc.getLocalAddress()).getPort();

        ChannelFuture ccf = cb.connect(new InetSocketAddress(TestUtil.getLocalHost(), port));
        ccf.awaitUninterruptibly();
        if (!ccf.isSuccess()) {
            logger.error("Connection attempt failed", ccf.getCause());
            sc.close().awaitUninterruptibly();
        }
        assertTrue(ccf.isSuccess());

        Channel cc = ccf.getChannel();
        ChannelFuture hf = cc.getPipeline().get(SslHandler.class).handshake();
        hf.awaitUninterruptibly();
        if (!hf.isSuccess()) {
            logger.error("Handshake failed", hf.getCause());
            sh.channel.close().awaitUninterruptibly();
            cc.close().awaitUninterruptibly();
            sc.close().awaitUninterruptibly();
        }

        assertTrue(hf.isSuccess());
        ch.latch.await();

        sh.channel.close().awaitUninterruptibly();
        cc.close().awaitUninterruptibly();
        sc.close().awaitUninterruptibly();
        cb.shutdown();
        sb.shutdown();
        cb.releaseExternalResources();
        sb.releaseExternalResources();

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

    private class ClientHandler extends SimpleChannelUpstreamHandler {
        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();
        final CountDownLatch latch = new CountDownLatch(1);

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
                throws Exception {
            ChannelBuffer buffer = (ChannelBuffer) e.getMessage();

            assertEquals(greeting, buffer);
            latch.countDown();
            ctx.getChannel().close();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
                throws Exception {
            logger.warn(
                    "Unexpected exception from the client side", e.getCause());

            exception.compareAndSet(null, e.getCause());
            e.getChannel().close();
        }
    }

    private class ServerHandler extends SimpleChannelUpstreamHandler {
        volatile Channel channel;
        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();

        @Override
        public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e)
                throws Exception {
            channel = e.getChannel();
            channel.write(greeting.duplicate());
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
                throws Exception {
            logger.warn(
                    "Unexpected exception from the server side", e.getCause());

            exception.compareAndSet(null, e.getCause());
            e.getChannel().close();
        }
    }
}