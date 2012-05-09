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
package io.netty.example.echo;

import io.netty.buffer.ChannelBuffer;
import io.netty.buffer.ChannelBuffers;
import io.netty.channel.ChannelBufferHolder;
import io.netty.channel.ChannelBufferHolders;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInboundHandlerContext;
import io.netty.channel.EventLoop;
import io.netty.channel.MultithreadEventLoop;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.SelectorEventLoop;

import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Echoes back any received data from a client.
 */
public class EchoServer {

    private final int port;

    public EchoServer(int port) {
        this.port = port;
    }

    public void run() throws Exception {
        // Configure the server.
        final EventLoop loop = new MultithreadEventLoop(SelectorEventLoop.class);
        ServerSocketChannel ssc = new NioServerSocketChannel();
        ssc.pipeline().addLast("acceptor", new ChannelInboundHandlerAdapter<SocketChannel>() {

            @Override
            public ChannelBufferHolder<SocketChannel> newInboundBuffer(
                    ChannelInboundHandlerContext<SocketChannel> ctx)
                    throws Exception {
                return ChannelBufferHolders.messageBuffer(new ArrayDeque<SocketChannel>());
            }

            @Override
            public void inboundBufferUpdated(
                    ChannelInboundHandlerContext<SocketChannel> ctx)
                    throws Exception {
                Queue<SocketChannel> in = ctx.in().messageBuffer();
                for (;;) {
                    SocketChannel s = in.poll();
                    if (s == null) {
                        break;
                    }
                    s.pipeline().addLast("echoer", new ChannelInboundHandlerAdapter<Byte>() {
                        @Override
                        public ChannelBufferHolder<Byte> newInboundBuffer(ChannelInboundHandlerContext<Byte> ctx) {
                            return ChannelBufferHolders.byteBuffer(ChannelBuffers.dynamicBuffer());
                        }

                        @Override
                        public void inboundBufferUpdated(ChannelInboundHandlerContext<Byte> ctx) {
                            ChannelBuffer in = ctx.in().byteBuffer();
                            ChannelBuffer out = ctx.out().byteBuffer();
                            out.discardReadBytes();
                            out.writeBytes(in);
                            in.clear();
                            ctx.flush();
                        }
                    });
                    loop.register(s);
                }
            }
        });

        loop.register(ssc).awaitUninterruptibly().rethrowIfFailed();
        ssc.bind(new InetSocketAddress(port), ssc.newFuture()).awaitUninterruptibly().rethrowIfFailed();
    }

    public static void main(String[] args) throws Exception {
        int port;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        } else {
            port = 8080;
        }
        new EchoServer(port).run();
    }
}
