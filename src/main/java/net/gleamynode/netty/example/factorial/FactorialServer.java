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
package net.gleamynode.netty.example.factorial;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import net.gleamynode.netty.bootstrap.ServerBootstrap;
import net.gleamynode.netty.channel.ChannelEvent;
import net.gleamynode.netty.channel.ChannelEventHandlerAdapter;
import net.gleamynode.netty.channel.ChannelFactory;
import net.gleamynode.netty.channel.ChannelStateEvent;
import net.gleamynode.netty.channel.socket.nio.NioServerSocketChannelFactory;
import net.gleamynode.netty.pipeline.PipeContext;

public class FactorialServer {

    public static void main(String[] args) throws Exception {
        // Start server.
        ChannelFactory factory =
            new NioServerSocketChannelFactory(
                    Executors.newCachedThreadPool(),
                    Executors.newCachedThreadPool());

        ServerBootstrap bootstrap = new ServerBootstrap(factory);

        // Create a handler which creates a new handler per channel
        // because SummationServerHandler is stateful.
        bootstrap.getPipeline().addLast(
                "bootstrap", new ChannelEventHandlerAdapter() {
                    @Override
                    protected void channelOpen(
                            PipeContext<ChannelEvent> ctx, ChannelStateEvent e) throws Exception {
                        // FIXME better pipeline initialization because this handler is stateful.
                        e.getChannel().getPipeline().addLast(
                                "decoder", new BigIntegerDecoder());
                        e.getChannel().getPipeline().addLast(
                                "encoder", new NumberEncoder());
                        e.getChannel().getPipeline().addLast(
                                "handler", new FactorialServerHandler());
                        ctx.sendUpstream(e);
                        e.getChannel().getPipeline().remove("bootstrap");
                    }
                });
        bootstrap.setOption("child.tcpNoDelay", true);
        bootstrap.setOption("child.keepAlive", true);

        bootstrap.bind(new InetSocketAddress(8080));
    }
}
