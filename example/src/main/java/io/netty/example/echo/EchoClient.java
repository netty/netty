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
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.channel.socket.nio.SelectorEventLoop;
import io.netty.handler.logging.LoggingHandler;
import io.netty.logging.InternalLogLevel;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Sends one message when a connection is open and echoes back any received
 * data to the server.  Simply put, the echo client initiates the ping-pong
 * traffic between the echo client and server by sending the first message to
 * the server.
 */
public class EchoClient {

    private final String host;
    private final int port;
    private final int firstMessageSize;
    private final AtomicLong transferredBytes = new AtomicLong();

    public EchoClient(String host, int port, int firstMessageSize) {
        this.host = host;
        this.port = port;
        this.firstMessageSize = firstMessageSize;
    }

    public void run() throws Exception {
        EventLoop loop = new MultithreadEventLoop(SelectorEventLoop.FACTORY);
        SocketChannel s = new NioSocketChannel();
        s.config().setTcpNoDelay(true);
        s.pipeline().addLast("logger", new LoggingHandler(InternalLogLevel.INFO));
        s.pipeline().addLast("echoer", new ChannelInboundHandlerAdapter<Byte>() {

            private final ChannelBuffer firstMessage;
            {
                if (firstMessageSize <= 0) {
                    throw new IllegalArgumentException(
                            "firstMessageSize: " + firstMessageSize);
                }
                firstMessage = ChannelBuffers.buffer(firstMessageSize);
                for (int i = 0; i < firstMessage.capacity(); i ++) {
                    firstMessage.writeByte((byte) i);
                }
            }

            @Override
            public ChannelBufferHolder<Byte> newInboundBuffer(ChannelInboundHandlerContext<Byte> ctx) {
                return ChannelBufferHolders.byteBuffer(ChannelBuffers.dynamicBuffer());
            }

            @Override
            public void channelActive(ChannelInboundHandlerContext<Byte> ctx)
                    throws Exception {
                ctx.write(firstMessage);
            }

            @Override
            public void inboundBufferUpdated(
                    ChannelInboundHandlerContext<Byte> ctx) throws Exception {
                ChannelBuffer in = ctx.in().byteBuffer();
                ChannelBuffer out = ctx.out().byteBuffer();
                transferredBytes.addAndGet(in.readableBytes());

                out.discardReadBytes();
                out.writeBytes(in);
                in.clear();
                ctx.flush();
            }
        });
        loop.register(s).awaitUninterruptibly().rethrowIfFailed();
        s.connect(new InetSocketAddress(host, port)).awaitUninterruptibly().rethrowIfFailed();

        // FIXME: Wait until the connection is closed or the connection attempt fails.
        // FIXME: Show how to shut down.
    }

    public static void main(String[] args) throws Exception {
        // Print usage if no argument is specified.
        if (args.length < 2 || args.length > 3) {
            System.err.println(
                    "Usage: " + EchoClient.class.getSimpleName() +
                    " <host> <port> [<first message size>]");
            return;
        }

        // Parse options.
        final String host = args[0];
        final int port = Integer.parseInt(args[1]);
        final int firstMessageSize;
        if (args.length == 3) {
            firstMessageSize = Integer.parseInt(args[2]);
        } else {
            firstMessageSize = 256;
        }

        new EchoClient(host, port, firstMessageSize).run();
    }
}
