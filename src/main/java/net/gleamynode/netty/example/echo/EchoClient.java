/*
 * Copyright (C) 2008  Trustin Heuiseung Lee
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, 5th Floor, Boston, MA 02110-1301 USA
 */
package net.gleamynode.netty.example.echo;

import static net.gleamynode.netty.channel.Channels.*;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import net.gleamynode.netty.channel.ChannelEvent;
import net.gleamynode.netty.channel.ChannelFactory;
import net.gleamynode.netty.channel.socket.nio.NioClientSocketChannelFactory;
import net.gleamynode.netty.pipeline.Pipeline;

public class EchoClient {

    public static void main(String[] args) throws Exception {
        // Print usage if no argument is specified.
        if (args.length < 2 || args.length > 3) {
            System.err.println(
                    "Usage: " + EchoClient.class.getSimpleName() +
                    " <host> <port> [<first message size>]");
            return;
        }

        // Parse options.
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        int firstMessageSize;

        if (args.length == 3) {
            firstMessageSize = Integer.parseInt(args[2]);
        } else {
            firstMessageSize = 256;
        }

        // Start client.
        ChannelFactory channelFactory =
            new NioClientSocketChannelFactory(
                    Executors.newCachedThreadPool(),
                    Executors.newCachedThreadPool());

        EchoHandler echoHandler = new EchoHandler(firstMessageSize);
        Pipeline<ChannelEvent> pipeline = newPipeline();
        pipeline.addLast(newPipe("handler", echoHandler));
        newClientChannel(
                channelFactory, new InetSocketAddress(host, port), pipeline);

        // Start performance monitor.
        new ThroughputMonitor(echoHandler).start();
    }
}
