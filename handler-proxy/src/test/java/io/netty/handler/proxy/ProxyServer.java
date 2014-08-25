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

package io.netty.handler.proxy;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoop;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.CharsetUtil;
import io.netty.util.NetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

abstract class ProxyServer {

    protected final InternalLogger logger = InternalLoggerFactory.getInstance(getClass());

    private final ServerSocketChannel ch;
    private final Queue<Throwable> recordedExceptions = new LinkedBlockingQueue<Throwable>();
    protected final TestMode testMode;
    protected final String username;
    protected final String password;
    protected final InetSocketAddress destination;

    /**
     * Starts a new proxy server with disabled authentication for testing purpose.
     *
     * @param useSsl {@code true} if and only if implicit SSL is enabled
     * @param testMode the test mode
     * @param destination the expected destination. If the client requests proxying to a different destination, this
     * server will reject the connection request.
     */
    protected ProxyServer(boolean useSsl, TestMode testMode, InetSocketAddress destination) {
        this(useSsl, testMode, destination, null, null);
    }

    /**
     * Starts a new proxy server with disabled authentication for testing purpose.
     *
     * @param useSsl {@code true} if and only if implicit SSL is enabled
     * @param testMode the test mode
     * @param username the expected username. If the client tries to authenticate with a different username, this server
     * will fail the authentication request.
     * @param password the expected password. If the client tries to authenticate with a different password, this server
     * will fail the authentication request.
     * @param destination the expected destination. If the client requests proxying to a different destination, this
     * server will reject the connection request.
     */
    protected ProxyServer(
            final boolean useSsl, TestMode testMode,
            InetSocketAddress destination, String username, String password) {

        this.testMode = testMode;
        this.destination = destination;
        this.username = username;
        this.password = password;

        ServerBootstrap b = new ServerBootstrap();
        b.channel(NioServerSocketChannel.class);
        b.group(ProxyHandlerTest.group);
        b.childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                ChannelPipeline p = ch.pipeline();
                if (useSsl) {
                    p.addLast(ProxyHandlerTest.serverSslCtx.newHandler(ch.alloc()));
                }

                configure(ch);
            }
        });

        ch = (ServerSocketChannel) b.bind(NetUtil.LOCALHOST, 0).syncUninterruptibly().channel();
    }

    public final InetSocketAddress address() {
        return new InetSocketAddress(NetUtil.LOCALHOST, ch.localAddress().getPort());
    }

    protected abstract void configure(SocketChannel ch) throws Exception;

    final void recordException(Throwable t) {
        logger.warn("Unexpected exception from proxy server:", t);
        recordedExceptions.add(t);
    }

    /**
     * Clears all recorded exceptions.
     */
    public final void clearExceptions() {
        recordedExceptions.clear();
    }

    /**
     * Logs all recorded exceptions and raises the last one so that the caller can fail.
     */
    public final void checkExceptions() {
        Throwable t;
        for (;;) {
            t = recordedExceptions.poll();
            if (t == null) {
                break;
            }

            logger.warn("Unexpected exception:", t);
        }

        if (t != null) {
            PlatformDependent.throwException(t);
        }
    }

    public final void stop() {
        ch.close();
    }

    protected abstract class IntermediaryHandler extends SimpleChannelInboundHandler<Object> {

        private final Queue<Object> received = new ArrayDeque<Object>();

        private boolean finished;
        private Channel backend;

        @Override
        protected final void channelRead0(final ChannelHandlerContext ctx, Object msg) throws Exception {
            if (finished) {
                received.add(ReferenceCountUtil.retain(msg));
                flush();
                return;
            }

            boolean finished = handleProxyProtocol(ctx, msg);
            if (finished) {
                this.finished = true;
                ChannelFuture f = connectToDestination(ctx.channel().eventLoop(), new BackendHandler(ctx));
                f.addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        if (!future.isSuccess()) {
                            recordException(future.cause());
                            ctx.close();
                        } else {
                            backend = future.channel();
                            flush();
                        }
                    }
                });
            }
        }

        private void flush() {
            if (backend != null) {
                boolean wrote = false;
                for (;;) {
                    Object msg = received.poll();
                    if (msg == null) {
                        break;
                    }
                    backend.write(msg);
                    wrote = true;
                }

                if (wrote) {
                    backend.flush();
                }
            }
        }

        protected abstract boolean handleProxyProtocol(ChannelHandlerContext ctx, Object msg) throws Exception;

        protected abstract SocketAddress intermediaryDestination();

        private ChannelFuture connectToDestination(EventLoop loop, ChannelHandler handler) {
            Bootstrap b = new Bootstrap();
            b.channel(NioSocketChannel.class);
            b.group(loop);
            b.handler(handler);
            return b.connect(intermediaryDestination());
        }

        @Override
        public final void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            ctx.flush();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            if (backend != null) {
                backend.close();
            }
        }

        @Override
        public final void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            recordException(cause);
            ctx.close();
        }

        private final class BackendHandler extends ChannelInboundHandlerAdapter {

            private final ChannelHandlerContext frontend;

            BackendHandler(ChannelHandlerContext frontend) {
                this.frontend = frontend;
            }

            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                frontend.write(msg);
            }

            @Override
            public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
                frontend.flush();
            }

            @Override
            public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                frontend.close();
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                recordException(cause);
                ctx.close();
            }
        }
    }

    protected abstract class TerminalHandler extends SimpleChannelInboundHandler<Object> {

        private boolean finished;

        @Override
        protected final void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (finished) {
                String str = ((ByteBuf) msg).toString(CharsetUtil.US_ASCII);
                if ("A\n".equals(str)) {
                    ctx.write(Unpooled.copiedBuffer("1\n", CharsetUtil.US_ASCII));
                } else if ("B\n".equals(str)) {
                    ctx.write(Unpooled.copiedBuffer("2\n", CharsetUtil.US_ASCII));
                } else if ("C\n".equals(str)) {
                    ctx.write(Unpooled.copiedBuffer("3\n", CharsetUtil.US_ASCII))
                       .addListener(ChannelFutureListener.CLOSE);
                } else {
                    throw new IllegalStateException("unexpected message: " + str);
                }
                return;
            }

            boolean finished = handleProxyProtocol(ctx, msg);
            if (finished) {
                this.finished = finished;
            }
        }

        protected abstract boolean handleProxyProtocol(ChannelHandlerContext ctx, Object msg) throws Exception;

        @Override
        public final void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            ctx.flush();
        }

        @Override
        public final void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            recordException(cause);
            ctx.close();
        }
    }
}
