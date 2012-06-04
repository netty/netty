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
package io.netty.example.securechat;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInboundHandlerContext;
import io.netty.channel.ChannelInboundMessageHandlerAdapter;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.ssl.SslHandler;

import java.net.InetAddress;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles a server-side channel.
 */
public class SecureChatServerHandler extends ChannelInboundMessageHandlerAdapter<String> {

    private static final Logger logger = Logger.getLogger(
            SecureChatServerHandler.class.getName());

    static final ChannelGroup channels = new DefaultChannelGroup();

    @Override
    public void channelActive(ChannelInboundHandlerContext<String> ctx) throws Exception {
        // Get the SslHandler in the current pipeline.
        // We added it in SecureChatPipelineFactory.
        final SslHandler sslHandler = ctx.pipeline().get(SslHandler.class);

        // Get notified when SSL handshake is done.
        ChannelFuture handshakeFuture = sslHandler.handshake();
        handshakeFuture.addListener(new Greeter(sslHandler));
    }

    @Override
    public void messageReceived(ChannelInboundHandlerContext<String> ctx, String request) throws Exception {
        // Send the received message to all channels but the current one.
        for (Channel c: channels) {
            if (c != ctx.channel()) {
                c.write("[" + ctx.channel().remoteAddress() + "] " +
                        request + '\n');
            } else {
                c.write("[you] " + request + '\n');
            }
        }

        // Close the connection if the client has sent 'bye'.
        if (request.toLowerCase().equals("bye")) {
            ctx.close();
        }
    }

    @Override
    public void exceptionCaught(ChannelInboundHandlerContext<String> ctx, Throwable cause) throws Exception {
        logger.log(
                Level.WARNING,
                "Unexpected exception from downstream.", cause);
        ctx.close();
    }

    private static final class Greeter implements ChannelFutureListener {

        private final SslHandler sslHandler;

        Greeter(SslHandler sslHandler) {
            this.sslHandler = sslHandler;
        }

        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            if (future.isSuccess()) {
                // Once session is secured, send a greeting.
                future.channel().write(
                        "Welcome to " + InetAddress.getLocalHost().getHostName() +
                        " secure chat service!\n");
                future.channel().write(
                        "Your session is protected by " +
                        sslHandler.getEngine().getSession().getCipherSuite() +
                        " cipher suite.\n");

                // Register the channel to the global channel list
                // so the channel received the messages from others.
                channels.add(future.channel());
            } else {
                future.channel().close();
            }
        }
    }
}
