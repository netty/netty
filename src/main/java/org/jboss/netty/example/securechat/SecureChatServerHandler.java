/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.example.securechat;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.handler.ssl.SslHandler;

import java.net.InetAddress;

/**
 * Handles a server-side channel.
 */
public class SecureChatServerHandler extends SimpleChannelUpstreamHandler {

    static final ChannelGroup channels = new DefaultChannelGroup();

    @Override
    public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e) throws Exception {
        if (e instanceof ChannelStateEvent) {
            System.err.println(e);
        }
        super.handleUpstream(ctx, e);
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) {
        // Get the SslHandler in the current pipeline.
        // We added it in SecureChatPipelineFactory.
        final SslHandler sslHandler = ctx.getPipeline().get(SslHandler.class);

        // Get notified when SSL handshake is done.
        ChannelFuture handshakeFuture = sslHandler.handshake();
        handshakeFuture.addListener(new Greeter(sslHandler));
    }

    @Override
    public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e) {
        // Unregister the channel from the global channel list
        // so the channel does not receive messages anymore.
        channels.remove(e.getChannel());
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) {

        // Convert to a String first.
        String request = (String) e.getMessage();

        // Send the received message to all channels but the current one.
        for (Channel c: channels) {
            if (c != e.getChannel()) {
                c.write("[" + e.getChannel().getRemoteAddress() + "] " +
                        request + '\n');
            } else {
                c.write("[you] " + request + '\n');
            }
        }

        // Close the connection if the client has sent 'bye'.
        if ("bye".equals(request.toLowerCase())) {
            e.getChannel().close();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
        e.getCause().printStackTrace();
        e.getChannel().close();
    }

    private static final class Greeter implements ChannelFutureListener {

        private final SslHandler sslHandler;

        Greeter(SslHandler sslHandler) {
            this.sslHandler = sslHandler;
        }

        public void operationComplete(ChannelFuture future) throws Exception {
            if (future.isSuccess()) {
                // Once session is secured, send a greeting.
                future.getChannel().write(
                        "Welcome to " + InetAddress.getLocalHost().getHostName() + " secure chat service!\n");
                future.getChannel().write(
                        "Your session is protected by " + sslHandler.getEngine().getSession().getCipherSuite() +
                        " cipher suite.\n");

                // Register the channel to the global channel list
                // so the channel received the messages from others.
                channels.add(future.getChannel());
            } else {
                future.getChannel().close();
            }
        }
    }
}
