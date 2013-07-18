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
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.sockjs.SessionContext;
import io.netty.handler.codec.sockjs.protocol.CloseFrame;
import io.netty.handler.codec.sockjs.protocol.MessageFrame;
import io.netty.handler.codec.sockjs.util.ArgumentUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.concurrent.ConcurrentMap;

/**
 * A session state for WebSockets.
 * Though the concept of a session for a WebSocket is not really required in SockJS,
 * as the sessions life time is the same as the life time of the connection, this is
 * included to keep concepts clear.
 */
class WebSocketSessionState implements SessionState {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(WebSocketSessionState.class);

    private final SockJSSession session;
    private final ConcurrentMap<String, SockJSSession> sessions;

    public WebSocketSessionState(final SockJSSession session, final ConcurrentMap<String, SockJSSession> sessions) {
        ArgumentUtil.checkNotNull(session, "session");
        ArgumentUtil.checkNotNull(sessions, "sessions");
        this.session = session;
        this.sessions = sessions;
    }

    @Override
    public void onConnect(final SessionContext s, final ChannelHandlerContext ctx) {
        synchronized (session) {
            session.setContext(ctx);
            session.setState(States.OPEN);
            session.onOpen(s);
        }
    }

    @Override
    public void onOpen(final ChannelHandlerContext ctx) {
        if (isInuse()) {
            logger.debug("Another connection still in open for [" + session.sessionId() + "]");
            final CloseFrame closeFrame = new CloseFrame(2010, "Another connection still open");
            ctx.writeAndFlush(closeFrame);
            session.setState(States.INTERRUPTED);
        } else {
            session.setInuse();
            flushMessages(ctx);
        }
    }

    @Override
    public void onMessage(final String message) throws Exception {
        session.onMessage(message);
    }

    @Override
    public void addMessage(final String message) {
        session.addMessage(message);
    }

    @Override
    public void onClose() {
        session.resetInuse();
    }

    @Override
    public void onSessionContextClose() {
        synchronized (session) {
            session.setState(States.CLOSED);
            session.onClose();
        }
    }

    private void flushMessages(final ChannelHandlerContext ignored) {
        final Channel channel = session.context().channel();
        if (channel.isActive() && channel.isRegistered()) {
            final String[] allMessages = session.getAllMessages();

            if (allMessages.length == 0) {
                return;
            }
            final MessageFrame messageFrame = new MessageFrame(allMessages);
            logger.debug("flushing [" + messageFrame + "]");
            channel.writeAndFlush(messageFrame).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(final ChannelFuture future) throws Exception {
                    if (!future.isSuccess()) {
                        session.addMessages(allMessages);
                    }
                }
            });
        }
    }

    @Override
    public boolean isInuse() {
        return session.context().channel().isActive();
    }

    @Override
    public States getState() {
        return session.getState();
    }

    @Override
    public String toString() {
        return "WebSocketSessionState[session=" + session + "]";
    }

    @Override
    public void onRequestCompleted(final ChannelHandlerContext ctx) {
        session.resetInuse();
    }

    @Override
    public ChannelHandlerContext getSessionChannelHandlerContext() {
        return session.context();
    }

    @Override
    public void onSockJSServerInitiatedClose() {
        logger.debug("Will close session context " + session.context());
        synchronized (session) {
            session.context().close();
            sessions.remove(session.sessionId());
        }
    }

}
