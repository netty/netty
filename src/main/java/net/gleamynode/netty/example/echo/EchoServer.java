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
import net.gleamynode.netty.channel.socket.nio.NioServerSocketChannelFactory;
import net.gleamynode.netty.pipeline.Pipeline;

public class EchoServer {

    public static void main(String[] args) throws Exception {
        // Start server.
        ChannelFactory channelFactory =
            new NioServerSocketChannelFactory(
                    Executors.newCachedThreadPool(),
                    Executors.newCachedThreadPool());

        EchoHandler echoHandler = new EchoHandler();

        Pipeline<ChannelEvent> pipeline = newPipeline();
        pipeline.addLast(newPipe("handler", echoHandler));
        newServerChannel(channelFactory, new InetSocketAddress(8080), pipeline);

        // Start performance monitor.
        new ThroughputMonitor(echoHandler).start();
    }
}
