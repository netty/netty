/*
 * Copyright 2026 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.microbench.channel;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoop;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.FixedLengthFrameDecoder;
import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.NetUtil;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

public class NioCrossEventLoopBenchmark extends AbstractMicrobenchmark {
    @State(Scope.Benchmark)
    public static class Server {
        private MultiThreadIoEventLoopGroup group;
        private Channel listener;

        @Setup
        public void setup() throws InterruptedException {
            group = new MultiThreadIoEventLoopGroup(2, NioIoHandler.newFactory());
            EventLoop first = group.next();
            EventLoop second = group.next();
            listener = new ServerBootstrap().group(group).channel(NioServerSocketChannel.class)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel channel) {
                            EventLoop owner = channel.executor();
                            EventLoop other = owner == first ? second : first;
                            channel.pipeline().addLast(new FixedLengthFrameDecoder(Long.BYTES));
                            channel.pipeline().addLast(new SimpleChannelInboundHandler<ByteBuf>() {
                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, ByteBuf message) {
                                    long value = message.readLong();
                                    other.execute(() -> owner.execute(() ->
                                            ctx.writeAndFlush(ctx.alloc().buffer(Long.BYTES).writeLong(value))));
                                }
                            });
                        }
                    }).bind(new InetSocketAddress(NetUtil.LOCALHOST, 0)).sync().getNow();
        }

        @TearDown
        public void tearDown() throws InterruptedException {
            try {
                if (listener != null) {
                    listener.close().sync();
                }
            } finally {
                group.shutdownGracefully(0, 5, TimeUnit.SECONDS).sync();
            }
        }
    }

    @State(Scope.Thread)
    public static class Client {
        private Socket socket;
        private DataInputStream input;
        private DataOutputStream output;
        private long sequence;

        @Setup
        public void setup(Server server) throws IOException {
            socket = new Socket();
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(10000);
            socket.connect(server.listener.localAddress());
            input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            output = new DataOutputStream(socket.getOutputStream());
        }

        @TearDown
        public void tearDown() throws IOException {
            socket.close();
        }
    }

    @Benchmark
    @Threads(4)
    public long roundTrip(Client client) throws IOException {
        long expected = ++client.sequence;
        client.output.writeLong(expected);
        long received = client.input.readLong();
        if (received != expected) {
            throw new IllegalStateException("Unexpected response: " + received + ", expected " + expected);
        }
        return received;
    }
}
