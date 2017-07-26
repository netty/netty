/*
 * Copyright 2016 The Netty Project
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
package io.netty.microbench.channel;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.microbench.util.AbstractSharedExecutorMicrobenchmark;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.Future;
import io.netty.util.internal.ThreadLocalRandom;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.TearDown;

import java.net.SocketAddress;

import static java.util.concurrent.TimeUnit.*;

abstract class AbstractChannelBenchmark extends AbstractSharedExecutorMicrobenchmark {

    public static final ChannelInitializer<Channel> EMPTY_INITIALIZER = new ChannelInitializer<Channel>() {
        @Override
        protected void initChannel(Channel ch) throws Exception {
            ch.pipeline().addLast(BufferReleaseHandler.INSTANCE);
        }
    };
    protected ChannelPipeline pipeline;
    protected ByteBuf payload;
    protected Channel serverChannel;
    protected Channel clientChannel;
    protected NioEventLoopGroup serverEventloop;
    protected NioEventLoopGroup clientEventLoop;

    protected void setup0(ChannelInitializer<Channel> serverInitializer,
                          ChannelInitializer<Channel> clientInitializer) {
        serverEventloop = new NioEventLoopGroup(1, new DefaultThreadFactory("server", true));
        clientEventLoop = new NioEventLoopGroup(1, new DefaultThreadFactory("client", true));
        ServerBootstrap sb = new ServerBootstrap();
        sb.group(serverEventloop)
          .channel(NioServerSocketChannel.class)
          .childHandler(serverInitializer);

        payload = createData(1024);

        Bootstrap cb = new Bootstrap();
        cb.group(clientEventLoop)
          .channel(NioSocketChannel.class)
          .handler(clientInitializer);

        ChannelFuture bind = sb.bind(0);
        SocketAddress serverAddr;
        try {
            bind.sync();
            serverChannel = bind.channel();
            serverAddr = serverChannel.localAddress();
            ChannelFuture clientChannelFuture = cb.connect(serverAddr);
            clientChannelFuture.sync();
            clientChannel = clientChannelFuture.channel();
            pipeline = clientChannel.pipeline();
        } catch (InterruptedException ie) {
            throw new IllegalStateException(ie);
        }

        AbstractSharedExecutorMicrobenchmark.executor(clientEventLoop.next());
    }

    @TearDown(Level.Trial)
    public void teardown() throws Exception {
        if (clientChannel != null) {
            clientChannel.close();
        }
        if (serverChannel != null) {
            serverChannel.close();
        }
        Future<?> serverGroup = null;
        Future<?> clientGroup = null;

        if (serverEventloop != null) {
            serverGroup = serverEventloop.shutdownGracefully(0, 0, MILLISECONDS);
        }
        if (clientEventLoop != null) {
            clientGroup = clientEventLoop.shutdownGracefully(0, 0, MILLISECONDS);
        }
        if (serverGroup != null) {
            serverGroup.sync();
        }
        if (clientGroup != null) {
            clientGroup.sync();
        }
        payload.release();
    }

    protected static ByteBuf createData(int length) {
        byte[] result = new byte[length];
        ThreadLocalRandom.current().nextBytes(result);
        return Unpooled.directBuffer().writeBytes(result);
    }

    protected void awaitCompletion(ChannelFuture lastWriteFuture) throws Exception {
        if (lastWriteFuture != null) {
            lastWriteFuture.await();
        }
    }

    @Sharable
    static final class BufferReleaseHandler extends SimpleChannelInboundHandler<Object> {

        public static final BufferReleaseHandler INSTANCE = new BufferReleaseHandler();

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
            // No Op, just to release the buffer.
        }
    }
}
