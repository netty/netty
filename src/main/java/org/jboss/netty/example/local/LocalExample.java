/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005-2008, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.example.local;

import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.local.LocalServerChannelFactory;
import org.jboss.netty.channel.local.LocalClientChannelFactory;
import org.jboss.netty.channel.local.LocalAddress;
import org.jboss.netty.channel.local.LocalServerChannels;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.example.echo.EchoHandler;
import org.jboss.netty.example.echo.ThroughputMonitor;
import org.jboss.netty.handler.codec.string.StringDecoder;
import org.jboss.netty.handler.codec.string.StringEncoder;
import org.jboss.netty.handler.codec.oneone.OneToOneDecoder;

import java.util.concurrent.Executors;
import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a>
 */
public class LocalExample {
    public static void main(String[] args) throws Exception {
        LocalServerChannelFactory factory = LocalServerChannels.registerServerChannel("localChannel");
        ServerBootstrap bootstrap = new ServerBootstrap(factory);
        EchoHandler handler = new EchoHandler();
        LocalAddress socketAddress = new LocalAddress("1");
        bootstrap.getPipeline().addLast("handler", handler);
        bootstrap.bind(socketAddress);

        ChannelFactory channelFactory = LocalServerChannels.getClientChannelFactory("localChannel");
        ClientBootstrap clientBootstrap = new ClientBootstrap(channelFactory);

        clientBootstrap.getPipeline().addLast("decoder", new StringDecoder());
        clientBootstrap.getPipeline().addLast("encoder", new StringEncoder());
        clientBootstrap.getPipeline().addLast("handler", new PrintHandler());
        ChannelFuture channelFuture = clientBootstrap.connect(socketAddress);
        channelFuture.awaitUninterruptibly();
        System.out.println("Enter text (quit to end)");
        // Read commands from the stdin.
        ChannelFuture lastWriteFuture = null;
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        for (; ;) {
            String line = in.readLine();
            if (line == null || "quit".equalsIgnoreCase(line)) {
                break;
            }

            // Sends the received line to the server.
            lastWriteFuture = channelFuture.getChannel().write(line);
        }

        // Wait until all messages are flushed before closing the channel.
        if (lastWriteFuture != null) {
            lastWriteFuture.awaitUninterruptibly();
        }
        channelFuture.getChannel().close();
        // Wait until the connection is closed or the connection attempt fails.
        channelFuture.getChannel().getCloseFuture().awaitUninterruptibly();

        // Shut down thread pools to exit.
        channelFactory.releaseExternalResources();
        factory.releaseExternalResources();
    }

    @ChannelPipelineCoverage("all")
    static class PrintHandler extends OneToOneDecoder {
        protected Object decode(ChannelHandlerContext ctx, Channel channel, Object msg) throws Exception {
            String message = (String) msg;
            System.out.println("received message back '" + message + "'");
            return null;
        }
    }
}
