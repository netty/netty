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
package net.gleamynode.netty.example.securechat;

import java.net.InetAddress;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.gleamynode.netty.channel.Channel;
import net.gleamynode.netty.channel.ChannelEvent;
import net.gleamynode.netty.channel.ChannelEventHandlerAdapter;
import net.gleamynode.netty.channel.ChannelFuture;
import net.gleamynode.netty.channel.ChannelFutureListener;
import net.gleamynode.netty.channel.ChannelStateEvent;
import net.gleamynode.netty.channel.ExceptionEvent;
import net.gleamynode.netty.channel.MessageEvent;
import net.gleamynode.netty.handler.ssl.SslHandler;
import net.gleamynode.netty.pipeline.PipeContext;
import net.gleamynode.netty.pipeline.PipelineCoverage;

@PipelineCoverage("all")
public class SecureChatServerHandler extends ChannelEventHandlerAdapter {

    private static final Logger logger = Logger.getLogger(
            SecureChatServerHandler.class.getName());

    static final Set<Channel> channels =
        Collections.newSetFromMap(new ConcurrentHashMap<Channel, Boolean>());

    @Override
    public void handleUpstream(
            PipeContext<ChannelEvent> ctx, ChannelEvent e) throws Exception {
        if (e instanceof ChannelStateEvent) {
            logger.info(e.toString());
        }
        super.handleUpstream(ctx, e);
    }

    @Override
    protected void channelConnected(
            PipeContext<ChannelEvent> ctx, ChannelStateEvent e) throws Exception {

        // Get the SslHandler in the current pipeline.
        final SslHandler sslHandler =
            (SslHandler) ctx.getPipeline().getHandler(SslHandler.class);

        // Get notified when SSL handshake is done.
        ChannelFuture handshakeFuture = sslHandler.handshake(e.getChannel());
        handshakeFuture.addListener(new ChannelFutureListener() {
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isSuccess()) {
                    // Once session is secured, send a greeting.
                    future.getChannel().write(
                            "Welcome to " + InetAddress.getLocalHost().getHostName() +
                            " secure chat service!\n");
                    future.getChannel().write(
                            "Your session is protected by " +
                            sslHandler.getEngine().getSession().getCipherSuite() +
                            " cipher suite.\n");

                    // Register the channel to the global channel list
                    // so the channel received the messages from others.
                    channels.add(future.getChannel());
                } else {
                    future.getChannel().close();
                }
            }
        });
    }

    @Override
    protected void channelDisconnected(
            PipeContext<ChannelEvent> ctx, ChannelStateEvent e) throws Exception {
        // Unregister the channel from the global channel list
        // so the channel doesn't receive messages anymore.
        channels.remove(e.getChannel());
    }

    @Override
    protected void messageReceived(
            PipeContext<ChannelEvent> ctx, MessageEvent e) {

        // Convert to a String first.
        String request = (String) e.getMessage();

        // Send the received message to all channels but the current one.
        for (Channel c: channels) {
            if (c != e.getChannel()) {
                c.write("[" + e.getChannel().getRemoteAddress() + "] " +
                        request + '\n');
            }
        }

        // Close the connection if the client sent 'bye'.
        if (request.toLowerCase().equals("bye")) {
            e.getChannel().close();
        }
    }

    @Override
    protected void exceptionCaught(
            PipeContext<ChannelEvent> ctx, ExceptionEvent e) {
        logger.log(
                Level.WARNING,
                "Unexpected exception from downstream.",
                e.getCause());
        e.getChannel().close();
    }
}
