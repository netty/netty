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

import io.netty.channel.EventLoop;
import io.netty.channel.MultithreadEventLoop;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.channel.socket.nio.SelectorEventLoop;
import io.netty.handler.logging.LoggingHandler;
import io.netty.logging.InternalLogLevel;

import java.net.InetSocketAddress;

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

    public EchoClient(String host, int port, int firstMessageSize) {
        this.host = host;
        this.port = port;
        this.firstMessageSize = firstMessageSize;
    }

    public void run() throws Exception {
        // Create a new socket and configure it.
        SocketChannel s = new NioSocketChannel();
        s.config().setTcpNoDelay(true);
        s.pipeline().addLast("logger", new LoggingHandler(InternalLogLevel.INFO));
        s.pipeline().addLast("echoer", new EchoClientHandler(firstMessageSize));

        // Begin the communication by registering the channel to an event loop and connecting
        // to the peer.
        EventLoop loop = new MultithreadEventLoop(SelectorEventLoop.FACTORY);
        loop.register(s).awaitUninterruptibly().rethrowIfFailed();
        s.connect(new InetSocketAddress(host, port));

        // Wait until the connection is closed.
        s.closeFuture().awaitUninterruptibly();

        // Terminate the event loop.
        loop.shutdown();
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
