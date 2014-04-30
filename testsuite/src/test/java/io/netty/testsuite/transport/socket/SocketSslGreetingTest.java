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
package io.netty.testsuite.transport.socket;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.testsuite.util.BogusSslContextFactory;
import io.netty.util.ReferenceCountUtil;
import org.junit.Test;

import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;


public class SocketSslGreetingTest extends AbstractSocketTest {

    private static final LogLevel LOG_LEVEL = LogLevel.TRACE;
    private final ByteBuf greeting = ReferenceCountUtil.releaseLater(Unpooled.buffer().writeByte('a'));

    // Test for https://github.com/netty/netty/pull/2437
    @Test(timeout = 30000)
    public void testSslGreeting() throws Throwable {
        run();
    }

    public void testSslGreeting(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        final ServerHandler sh = new ServerHandler();
        final ClientHandler ch = new ClientHandler();

        sb.childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel sch) throws Exception {
                final SSLEngine sse = BogusSslContextFactory.getServerContext().createSSLEngine();
                sse.setUseClientMode(false);

                ChannelPipeline p = sch.pipeline();
                p.addLast(new SslHandler(sse));
                p.addLast("logger", new LoggingHandler(LOG_LEVEL));
                p.addLast(sh);
            }
        });

        cb.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel sch) throws Exception {
                final SSLEngine cse = BogusSslContextFactory.getClientContext().createSSLEngine();
                cse.setUseClientMode(true);

                ChannelPipeline p = sch.pipeline();
                p.addLast(new SslHandler(cse));
                p.addLast("logger", new LoggingHandler(LOG_LEVEL));
                p.addLast(ch);
            }
        });

        Channel sc = sb.bind().sync().channel();
        Channel cc = cb.connect().sync().channel();

        ch.latch.await();

        sh.channel.close().awaitUninterruptibly();
        cc.close().awaitUninterruptibly();
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

    private class ClientHandler extends SimpleChannelInboundHandler<ByteBuf> {

        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();
        final CountDownLatch latch = new CountDownLatch(1);

        @Override
        public void messageReceived(ChannelHandlerContext ctx, ByteBuf buf) throws Exception {
            assertEquals(greeting, buf);
            latch.countDown();
            ctx.close();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx,
                                    Throwable cause) throws Exception {
            if (logger.isWarnEnabled()) {
                logger.warn("Unexpected exception from the client side", cause);
            }

            exception.compareAndSet(null, cause);
            ctx.close();
        }
    }

    private class ServerHandler extends SimpleChannelInboundHandler<ByteBuf> {
        volatile Channel channel;
        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();

        @Override
        protected void messageReceived(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
            // discard
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx)
                throws Exception {
            channel = ctx.channel();
            channel.writeAndFlush(greeting.duplicate().retain());
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx,
                                    Throwable cause) throws Exception {
            if (logger.isWarnEnabled()) {
                logger.warn("Unexpected exception from the server side", cause);
            }

            exception.compareAndSet(null, cause);
            ctx.close();
        }
    }
}
