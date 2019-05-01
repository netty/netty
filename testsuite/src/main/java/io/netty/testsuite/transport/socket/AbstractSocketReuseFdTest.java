/*
 * Copyright 2019 The Netty Project
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
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.testsuite.transport.TestsuitePermutation;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;
import org.junit.Test;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public abstract class AbstractSocketReuseFdTest extends AbstractSocketTest {
    @Override
    protected abstract SocketAddress newSocketAddress();

    @Override
    protected abstract List<TestsuitePermutation.BootstrapComboFactory<ServerBootstrap, Bootstrap>> newFactories();

    @Test(timeout = 60000)
    public void testReuseFd() throws Throwable {
        run();
    }

    public void testReuseFd(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        sb.childOption(ChannelOption.AUTO_READ, true);
        cb.option(ChannelOption.AUTO_READ, true);

        // Use a number which will typically not exceed /proc/sys/net/core/somaxconn (which is 128 on linux by default
        // often).
        int numChannels = 100;
        final AtomicReference<Throwable> globalException = new AtomicReference<Throwable>();
        final AtomicInteger serverRemaining = new AtomicInteger(numChannels);
        final AtomicInteger clientRemaining = new AtomicInteger(numChannels);
        final Promise<Void> serverDonePromise = ImmediateEventExecutor.INSTANCE.newPromise();
        final Promise<Void> clientDonePromise = ImmediateEventExecutor.INSTANCE.newPromise();

        sb.childHandler(new ChannelInitializer<Channel>() {
            @Override
            public void initChannel(Channel sch) {
                ReuseFdHandler sh = new ReuseFdHandler(
                    false,
                    globalException,
                    serverRemaining,
                    serverDonePromise);
                sch.pipeline().addLast("handler", sh);
            }
        });

        cb.handler(new ChannelInitializer<Channel>() {
            @Override
            public void initChannel(Channel sch) {
                ReuseFdHandler ch = new ReuseFdHandler(
                    true,
                    globalException,
                    clientRemaining,
                    clientDonePromise);
                sch.pipeline().addLast("handler", ch);
            }
        });

        ChannelFutureListener listener = new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) {
                if (!future.isSuccess()) {
                    clientDonePromise.tryFailure(future.cause());
                }
            }
        };

        Channel sc = sb.bind().sync().channel();
        for (int i = 0; i < numChannels; i++) {
            cb.connect(sc.localAddress()).addListener(listener);
        }

        clientDonePromise.sync();
        serverDonePromise.sync();
        sc.close().sync();

        if (globalException.get() != null && !(globalException.get() instanceof IOException)) {
            throw globalException.get();
        }
    }

    static class ReuseFdHandler extends ChannelInboundHandlerAdapter {
        private static final String EXPECTED_PAYLOAD = "payload";

        private final Promise<Void> donePromise;
        private final AtomicInteger remaining;
        private final boolean client;
        volatile Channel channel;
        final AtomicReference<Throwable> globalException;
        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();
        final StringBuilder received = new StringBuilder();

        ReuseFdHandler(
            boolean client,
            AtomicReference<Throwable> globalException,
            AtomicInteger remaining,
            Promise<Void> donePromise) {
            this.client = client;
            this.globalException = globalException;
            this.remaining = remaining;
            this.donePromise = donePromise;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            channel = ctx.channel();
            if (client) {
                ctx.writeAndFlush(Unpooled.copiedBuffer(EXPECTED_PAYLOAD, CharsetUtil.US_ASCII));
            }
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof ByteBuf) {
                ByteBuf buf = (ByteBuf) msg;
                received.append(buf.toString(CharsetUtil.US_ASCII));
                buf.release();

                if (received.toString().equals(EXPECTED_PAYLOAD)) {
                    if (client) {
                        ctx.close();
                    } else {
                        ctx.writeAndFlush(Unpooled.copiedBuffer(EXPECTED_PAYLOAD, CharsetUtil.US_ASCII));
                    }
                }
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            if (exception.compareAndSet(null, cause)) {
                donePromise.tryFailure(new IllegalStateException("exceptionCaught: " + ctx.channel(), cause));
                ctx.close();
            }
            globalException.compareAndSet(null, cause);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            if (remaining.decrementAndGet() == 0) {
                if (received.toString().equals(EXPECTED_PAYLOAD)) {
                    donePromise.setSuccess(null);
                } else {
                    donePromise.tryFailure(new Exception("Unexpected payload:" + received));
                }
            }
        }
    }
}
