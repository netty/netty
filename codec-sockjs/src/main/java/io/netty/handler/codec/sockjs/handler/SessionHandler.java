/*
 * Copyright 2013 The Netty Project
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
package io.netty.handler.codec.sockjs.handler;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.sockjs.SockJsSessionContext;
import io.netty.handler.codec.sockjs.handler.SockJsSession.States;
import io.netty.handler.codec.sockjs.protocol.CloseFrame;
import io.netty.handler.codec.sockjs.protocol.MessageFrame;
import io.netty.handler.codec.sockjs.protocol.OpenFrame;
import io.netty.handler.codec.sockjs.util.ArgumentUtil;
import io.netty.util.ReferenceCountUtil;
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
public class SessionHandler extends ChannelHandlerAdapter implements SockJsSessionContext {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(SessionHandler.class);
    public enum Events { CLOSE_SESSION, HANDLE_SESSION }

    private final SessionState sessionState;
    private final SockJsSession session;
    private ChannelHandlerContext currentContext;

    public SessionHandler(final SessionState sessionState, final SockJsSession session) {
        ArgumentUtil.checkNotNull(sessionState, "sessionState");
        this.sessionState = sessionState;
        this.session = session;
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        if (msg instanceof HttpRequest) {
            handleSession(ctx);
        } else if (msg instanceof String) {
            handleMessage((String) msg);
        } else {
            ctx.fireChannelRead(ReferenceCountUtil.retain(msg));
        }
    }

    private void handleSession(final ChannelHandlerContext ctx) throws Exception {
        currentContext = ctx;
        if (logger.isDebugEnabled()) {
            logger.debug("handleSession {}", sessionState);
        }
        switch (session.getState()) {
        case CONNECTING:
            logger.debug("State.CONNECTING sending open frame");
            ctx.channel().writeAndFlush(new OpenFrame());
            session.setContext(ctx);
            session.onOpen(this);
            sessionState.onConnect(session, ctx);
            break;
        case OPEN:
            if (sessionState.isInUse(session)) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Another connection still in open for [{}]", session.sessionId());
                }
                ctx.writeAndFlush(new CloseFrame(2010, "Another connection still open"));
                session.setState(States.INTERRUPTED);
            } else {
                session.setInuse();
                sessionState.onOpen(session, ctx);
            }
            break;
        case INTERRUPTED:
            ctx.writeAndFlush(new CloseFrame(1002, "Connection interrupted"));
            break;
        case CLOSED:
            ctx.writeAndFlush(new CloseFrame(3000, "Go away!"));
            session.resetInuse();
            break;
        }
    }

    private void handleMessage(final String message) throws Exception {
        session.onMessage(message);
    }

    @Override
    public void send(String message) {
        final Channel channel = getActiveChannel();
        if (isWritable(channel)) {
            channel.writeAndFlush(new MessageFrame(message));
        } else {
            session.addMessage(message);
        }
    }

    private Channel getActiveChannel() {
        final Channel sessionChannel = session.context().channel();
        return sessionChannel.isActive() && sessionChannel.isRegistered() ? sessionChannel : currentContext.channel();
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
        session.resetInuse();
        ctx.fireChannelInactive();
    }

    private static boolean isWritable(final Channel channel) {
        return channel.isActive() && channel.isRegistered();
    }

    @Override
    public void close() {
        session.onClose();
        sessionState.onClose();
        final Channel channel = getActiveChannel();
        if (isWritable(channel)) {
            final CloseFrame closeFrame = new CloseFrame(3000, "Go away!");
            if (logger.isDebugEnabled()) {
                logger.debug("Writing {}", closeFrame);
            }
            channel.writeAndFlush(closeFrame).addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx, final Object event) throws Exception {
        if (event == Events.CLOSE_SESSION) {
            sessionState.onSockJSServerInitiatedClose(session);
        } else if (event == Events.HANDLE_SESSION) {
            handleSession(ctx);
        }
    }

    @Override
    public ChannelHandlerContext getContext() {
        return currentContext;
    }

}
