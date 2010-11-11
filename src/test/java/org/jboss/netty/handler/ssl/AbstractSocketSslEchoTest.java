/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.handler.ssl;

import static org.junit.Assert.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLEngine;

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
import org.jboss.netty.example.securechat.SecureChatSslContextFactory;
import org.jboss.netty.handler.execution.ExecutionHandler;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.util.TestUtil;
import org.jboss.netty.util.internal.ExecutorUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;


/**
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2119 $, $Date: 2010-02-01 20:46:09 +0900 (Mon, 01 Feb 2010) $
 *
 */
public abstract class AbstractSocketSslEchoTest {
    static final InternalLogger logger =
        InternalLoggerFactory.getInstance(AbstractSocketSslEchoTest.class);

    private static final Random random = new Random();
    static final byte[] data = new byte[1048576];

    private static ExecutorService executor;
    private static ExecutorService eventExecutor;

    static {
        random.nextBytes(data);
    }

    @BeforeClass
    public static void init() {
        executor = Executors.newCachedThreadPool();
        eventExecutor = new OrderedMemoryAwareThreadPoolExecutor(16, 0, 0);
    }

    @AfterClass
    public static void destroy() {
        ExecutorUtil.terminate(executor, eventExecutor);
    }

    protected abstract ChannelFactory newServerSocketChannelFactory(Executor executor);
    protected abstract ChannelFactory newClientSocketChannelFactory(Executor executor);

    protected boolean isExecutorRequired() {
        return false;
    }

    @Test
    public void testSslEcho() throws Throwable {
        ServerBootstrap sb = new ServerBootstrap(newServerSocketChannelFactory(executor));
        ClientBootstrap cb = new ClientBootstrap(newClientSocketChannelFactory(executor));

        EchoHandler sh = new EchoHandler(true);
        EchoHandler ch = new EchoHandler(false);

        SSLEngine sse = SecureChatSslContextFactory.getServerContext().createSSLEngine();
        SSLEngine cse = SecureChatSslContextFactory.getClientContext().createSSLEngine();
        sse.setUseClientMode(false);
        cse.setUseClientMode(true);

        // Workaround for blocking I/O transport write-write dead lock.
        sb.setOption("receiveBufferSize", 1048576);
        sb.setOption("receiveBufferSize", 1048576);

        sb.getPipeline().addFirst("ssl", new SslHandler(sse));
        sb.getPipeline().addLast("handler", sh);
        cb.getPipeline().addFirst("ssl", new SslHandler(cse));
        cb.getPipeline().addLast("handler", ch);

        if (isExecutorRequired()) {
            sb.getPipeline().addFirst("executor",new ExecutionHandler(eventExecutor));
            cb.getPipeline().addFirst("executor",new ExecutionHandler(eventExecutor));
        }

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
            ch.channel.close().awaitUninterruptibly();
            sc.close().awaitUninterruptibly();
        }

        assertTrue(hf.isSuccess());

        for (int i = 0; i < data.length;) {
            int length = Math.min(random.nextInt(1024 * 64), data.length - i);
            cc.write(ChannelBuffers.wrappedBuffer(data, i, length));
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

    private class EchoHandler extends SimpleChannelUpstreamHandler {
        volatile Channel channel;
        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();
        volatile int counter;
        private final boolean server;

        EchoHandler(boolean server) {
            this.server = server;
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
            logger.warn(
                    "Unexpected exception from the " +
                    (server? "server" : "client") + " side", e.getCause());

            exception.compareAndSet(null, e.getCause());
            e.getChannel().close();
        }
    }
}
