/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
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

import java.util.concurrent.TimeUnit;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelDownstreamHandler;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelUpstreamHandler;
import org.jboss.netty.channel.local.DefaultLocalClientChannelFactory;
import org.jboss.netty.channel.local.DefaultLocalServerChannelFactory;
import org.jboss.netty.channel.local.LocalAddress;
import org.jboss.netty.handler.codec.string.StringDecoder;
import org.jboss.netty.handler.codec.string.StringEncoder;
import org.jboss.netty.handler.execution.OrderedMemoryAwareThreadPoolExecutor;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @version $Rev: 1482 $, $Date: 2009-06-19 19:48:17 +0200 (ven., 19 juin 2009) $
 */
public class LocalExampleMultiple {
    public static void main(String[] args) throws Exception {
        OrderedMemoryAwareThreadPoolExecutor orderedMemoryAwareThreadPoolExecutor =
            new OrderedMemoryAwareThreadPoolExecutor(
                    5, 1000000, 10000000, 100,
                    TimeUnit.MILLISECONDS);
        ChannelFactory factory = new DefaultLocalServerChannelFactory();
        ServerBootstrap bootstrap = new ServerBootstrap(factory);
        bootstrap.setPipelineFactory(new LocalServerPipelineFactory(orderedMemoryAwareThreadPoolExecutor));
        LocalAddress socketAddress = new LocalAddress("1");
        bootstrap.bind(socketAddress);

        ChannelFactory channelFactory = new DefaultLocalClientChannelFactory();
        ClientBootstrap clientBootstrap = new ClientBootstrap(channelFactory);
        clientBootstrap.getPipeline().addLast("decoder", new StringDecoder());
        clientBootstrap.getPipeline().addLast("encoder", new StringEncoder());
        clientBootstrap.getPipeline().addLast("handler", new PrintHandler());

        // Read commands from array
        String []commands = {
                "First", "Second", "Third", "quit"
        };
        for (int j = 0; j < 5 ; j++) {
            System.err.println("Start "+j);
            ChannelFuture channelFuture = clientBootstrap.connect(socketAddress);
            channelFuture.awaitUninterruptibly();
            if (! channelFuture.isSuccess()) {
                System.err.println("CANNOT CONNECT");
                channelFuture.getCause().printStackTrace();
                break;
            }
            ChannelFuture lastWriteFuture = null;
            for (String line: commands) {
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
            System.err.println("End "+j);
        }
        clientBootstrap.releaseExternalResources();
        bootstrap.releaseExternalResources();
        orderedMemoryAwareThreadPoolExecutor.shutdownNow();
    }

    @ChannelPipelineCoverage("all")
    static class PrintHandler implements ChannelUpstreamHandler, ChannelDownstreamHandler {
        public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e)
                throws Exception {
            System.err.println(e);
            ctx.sendUpstream(e);
        }

        public void handleDownstream(ChannelHandlerContext ctx, ChannelEvent e)
                throws Exception {
            System.err.println(e);
            ctx.sendDownstream(e);
        }
    }

}
