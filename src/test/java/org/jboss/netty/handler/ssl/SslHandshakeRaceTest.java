/*
 * Copyright 2012 The Netty Project
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
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

public class SslHandshakeRaceTest extends SslTest {

    private static final int COUNT = 10;

    private static final Random random = new Random();
    static final byte[] data = new byte[1048576];
    private int count;

    static {
        random.nextBytes(data);
    }

    public SslHandshakeRaceTest(
            SslContext serverCtx, SslContext clientCtx,
            ChannelFactory serverChannelFactory, ChannelFactory clientChannelFactory) {
        super(serverCtx, clientCtx, serverChannelFactory, clientChannelFactory);
    }

    @Test
    public void testHandshakeRace() throws Throwable {
        ClientBootstrap cb = new ClientBootstrap(clientChannelFactory);
        cb.setPipelineFactory(new ChannelPipelineFactory() {
            public ChannelPipeline getPipeline() throws Exception {
                ChannelPipeline cp = Channels.pipeline();
                cp.addFirst("ssl", clientCtx.newHandler());
                cp.addLast("handler", new TestHandler());
                return cp;
            }
        });

        ServerBootstrap sb = new ServerBootstrap(serverChannelFactory);
        sb.setPipelineFactory(new ChannelPipelineFactory() {
            public ChannelPipeline getPipeline() throws Exception {
                ChannelPipeline cp = Channels.pipeline();

                cp.addFirst("counter", new SimpleChannelUpstreamHandler() {
                    @Override
                    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
                        ctx.getPipeline().get(SslHandler.class).handshake().addListener(new ChannelFutureListener() {
                            public void operationComplete(ChannelFuture future) throws Exception {
                                if (!future.isSuccess()) {
                                    future.getCause().printStackTrace();
                                    future.getChannel().close();
                                }
                            }
                        });

                        ++count;
                        if (count % 100 == 0) {
                            System.out.println("Connection #" + count);
                        }
                    }
                });

                cp.addFirst("ssl", serverCtx.newHandler());
                cp.addLast("handler",  new TestHandler());
                return cp;
            }
        });

        Channel sc = sb.bind(new InetSocketAddress(0));
        int port = ((InetSocketAddress) sc.getLocalAddress()).getPort();

        for (int i = 0; i < COUNT; i++) {
            connectAndSend(cb, port);
        }

        sc.close().awaitUninterruptibly();
    }

    private static void connectAndSend(ClientBootstrap cb, int port) throws Throwable {
        ChannelFuture ccf = cb.connect(new InetSocketAddress("127.0.0.1", port));
        ccf.awaitUninterruptibly();
        if (!ccf.isSuccess()) {
            throw ccf.getCause();
        }
        TestHandler ch = ccf.getChannel().getPipeline().get(TestHandler.class);

        Channel cc = ccf.getChannel();
        ChannelFuture hf = cc.getPipeline().get(SslHandler.class).handshake();
        hf.awaitUninterruptibly();
        if (!hf.isSuccess()) {
            ch.channel.close();
            throw hf.getCause();
        }

        for (int i = 0; i < data.length;) {
            int length = Math.min(random.nextInt(1024 * 64), data.length - i);
            ChannelFuture future = cc.write(ChannelBuffers.wrappedBuffer(data, i, length));
            i += length;
            if (i >= data.length) {
                future.awaitUninterruptibly();
            }
        }

        ch.channel.close().awaitUninterruptibly();

        if (ch.exception.get() != null) {
            throw ch.exception.get();
        }
    }

    private static class TestHandler extends SimpleChannelUpstreamHandler {
        volatile Channel channel;
        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();

        @Override
        public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e)
                throws Exception {
            channel = e.getChannel();
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
                throws Exception {
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
                throws Exception {
            e.getCause().printStackTrace();

            exception.compareAndSet(null, e.getCause());
            e.getChannel().close();
        }
    }
}
