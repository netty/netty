/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.sockjs.handlers;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.sockjs.SessionContext;
import io.netty.handler.codec.sockjs.protocol.CloseFrame;
import io.netty.handler.codec.sockjs.protocol.MessageFrame;
import io.netty.handler.codec.sockjs.protocol.OpenFrame;
import io.netty.handler.codec.sockjs.util.ArgumentUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

/**
 * A Handler that manages SockJS sessions.
 *
 * For every connection received a new SessionHandler will be created
 * and added to the pipeline
 * Depending on the type of connection (polling, streaming, send, or websocket)
 * the type of {@link SessionState} that this session handles will differ.
 *
 */
public class SessionHandler extends ChannelInboundHandlerAdapter implements SessionContext {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(SessionHandler.class);
    public enum Events { CLOSE_SESSION, HANDLE_SESSION };

    private final SessionState sessionState;
    private ChannelHandlerContext currentContext;

    public SessionHandler(final SessionState sessionState) {
        ArgumentUtil.checkNotNull(sessionState, "sessionState");
        this.sessionState = sessionState;
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        if (msg instanceof HttpRequest) {
            handleSession(ctx);
        } else if (msg instanceof String) {
            handleMessage((String) msg);
        }
    }

    private void handleSession(final ChannelHandlerContext ctx) throws Exception {
        currentContext = ctx;
        logger.debug("handleSession " + sessionState);
        switch (sessionState.getState()) {
        case CONNECTING:
            logger.debug("State.Connecting sending open frame");
            ctx.channel().writeAndFlush(new OpenFrame());
            sessionState.onConnect(this, ctx);
            break;
        case OPEN:
            sessionState.onOpen(ctx);
            break;
        case INTERRUPTED:
            ctx.writeAndFlush(new CloseFrame(1002, "Connection interrupted"));
            break;
        case CLOSED:
            ctx.writeAndFlush(new CloseFrame(3000, "Go away!"));
            sessionState.onClose();
            break;
        }
    }

    private void handleMessage(final String message) throws Exception {
        sessionState.onMessage(message);
    }

    @Override
    public void send(String message) {
        final Channel channel = getActiveChannel();
        if (isWritable(channel)) {
            channel.writeAndFlush(new MessageFrame(message));
        } else {
            sessionState.addMessage(message);
        }
    }

    private Channel getActiveChannel() {
        final Channel sessionChannel = sessionState.getSessionChannelHandlerContext().channel();
        return sessionChannel.isActive() && sessionChannel.isRegistered() ? sessionChannel : currentContext.channel();
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
        sessionState.onRequestCompleted(ctx);
        ctx.fireChannelInactive();
    }

    private boolean isWritable(final Channel channel) {
        return channel.isActive() && channel.isRegistered();
    }

    @Override
    public void close() {
        sessionState.onSessionContextClose();
        final Channel channel = getActiveChannel();
        if (isWritable(channel)) {
            final CloseFrame closeFrame = new CloseFrame(3000, "Go away!");
            logger.debug("Writing " + closeFrame);
            channel.writeAndFlush(closeFrame).addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx, final Object event) throws Exception {
        if (event == Events.CLOSE_SESSION) {
            sessionState.onSockJSServerInitiatedClose();
        } else if (event == Events.HANDLE_SESSION) {
            handleSession(ctx);
        }
    }

    @Override
    public ChannelHandlerContext getContext() {
        return currentContext;
    }

}
