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
import io.netty.handler.codec.sockjs.protocol.HeartbeatFrame;
import io.netty.handler.codec.sockjs.protocol.MessageFrame;
import io.netty.handler.codec.sockjs.util.ArgumentUtil;
import io.netty.util.concurrent.ScheduledFuture;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * A streaming session state holds a session which in turn has a persistent
 * connection to the client. This connection/channel is created when the first
 * request from the client is made. This request opens a new session and that
 * requests channel will be used to when flushing queued data from the sessson.
 */
class StreamingSessionState implements SessionState {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(StreamingSessionState.class);
    private final SockJSSession session;
    private final ConcurrentMap<String, SockJSSession> sessions;

    private ScheduledFuture<?> heartbeatFuture;
    private ScheduledFuture<?> sessionTimer;

    public StreamingSessionState(final SockJSSession session, final ConcurrentMap<String, SockJSSession> sessions) {
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
            startSessionTimer(ctx);
            startHeartbeatTimer(ctx);
        }
    }

    @Override
    public void onOpen(final ChannelHandlerContext ctx) {
        if (isInuse()) {
            logger.debug("Another connection still in open for [" + session.sessionId() + "]");
            final CloseFrame closeFrame = new CloseFrame(2010, "Another connection still open");
            ctx.write(closeFrame);
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
            channel.write(messageFrame).addListener(new ChannelFutureListener() {
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

    private void startSessionTimer(final ChannelHandlerContext ctx) {
        try {
            if (sessionTimer == null) {
                sessionTimer = ctx.executor().scheduleAtFixedRate(new Runnable() {
                    @Override
                    public void run() {
                        final long now = System.currentTimeMillis();
                        if (isInuse()) {
                            return;
                        }
                        if (session.timestamp() + session.config().sessionTimeout() < now) {
                            final SockJSSession removed = sessions.remove(session.sessionId());
                            logger.debug("Removed " + removed.sessionId() + " from sessions map");
                            session.context().close();
                            boolean cancel = sessionTimer.cancel(true);
                            logger.info("was sessionTimer cancelled: " + cancel);
                            heartbeatFuture.cancel(true);
                            logger.debug("Remaining [" + sessions.size() + "]");
                        }
                    }
                }, session.config().sessionTimeout(), session.config().sessionTimeout(), TimeUnit.MILLISECONDS);
            }
        } catch (final UnsupportedOperationException e) {
            logger.debug("Caught: " + e);
            // ignoring this exception which needs to be fixed.
            // The issue is when testing embedded and AbstractEventExecutor throws
            // an UnsupportedOperationException when calling scheduleAtFixedRate.
        }
    }

    private void startHeartbeatTimer(final ChannelHandlerContext ctx) {
        try {
            heartbeatFuture = ctx.executor().scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    if (ctx.channel().isActive() && ctx.channel().isRegistered()) {
                        logger.debug("Sending heartbeat for " + session);
                        ctx.channel().write(new HeartbeatFrame());
                    }
                }
            },
            session.config().heartbeatInterval(),
            session.config().heartbeatInterval(),
            TimeUnit.MILLISECONDS);
        } catch (final UnsupportedOperationException e) {
            logger.debug("Caught: " + e);
            // ignoring this exception which needs to be fixed.
            // The issue is when testing embedded and AbstractEventExecutor throws
            // an UnsupportedOperationException when calling scheduleAtFixedRate.
        }
    }

    @Override
    public States getState() {
        return session.getState();
    }

    @Override
    public String toString() {
        return "StreamingSessionState[session=" + session + "]";
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
        synchronized (session) {
            final ChannelHandlerContext context = session.context();
            if (context != null) { //could be null if the request is aborted, for example due to missing callback.
                logger.debug("Will close session context " + session.context());
                context.close();
            }
        }
    }
}
