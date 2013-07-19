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
 * A polling state does not have a persistent connection to the client, instead a client
 * will connect, poll, to request data.
 *
 * The first poll request will open the session and the ChannelHandlerContext for
 * that request will be stored along with the new session. Subsequent request will
 * use the same sessionId and hence use the same session.
 *
 * A polling request will flush any queued up messages in the session and write them
 * out to the current channel. It cannot use the original channel for the session as it
 * most likely will have been closed. Instead it will use current channel effectively
 * writing out the queued up messages to the pipeline to be handled, and eventually returned
 * in the response.
 *
 */
class PollingSessionState implements SessionState {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(PollingSessionState.class);
    private final SockJSSession session;
    private final ConcurrentMap<String, SockJSSession> sessions;
    private ScheduledFuture<?> sessionTimer;
    private ScheduledFuture<?> heartbeatFuture;

    public PollingSessionState(final SockJSSession session, final ConcurrentMap<String, SockJSSession> sessions) {
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
        if (isInUse()) {
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

    private void flushMessages(final ChannelHandlerContext ctx) {
        final String[] allMessages = session.getAllMessages();
        if (allMessages.length == 0) {
            return;
        }
        final MessageFrame messageFrame = new MessageFrame(allMessages);
        ctx.channel().writeAndFlush(messageFrame).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(final ChannelFuture future) throws Exception {
                if (!future.isSuccess()) {
                    session.addMessages(allMessages);
                }
            }
        });
    }

    @Override
    public boolean isInUse() {
        synchronized (session) {
            return session.context().channel().isActive() || session.inuse();
        }
    }

    private void startSessionTimer(final ChannelHandlerContext ctx) {
        try {
            if (sessionTimer == null) {
                sessionTimer = ctx.executor().scheduleAtFixedRate(new Runnable() {
                    @Override
                    public void run() {
                        final long now = System.currentTimeMillis();
                        if (isInUse()) {
                            return;
                        }
                        if (session.timestamp() + session.config().sessionTimeout() < now) {
                            final SockJSSession removed = sessions.remove(session.sessionId());
                            session.context().close();
                            sessionTimer.cancel(true);
                            heartbeatFuture.cancel(true);
                            logger.debug("Removed " + removed.sessionId() + " from map[" + sessions.size() + "]");
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
                        ctx.channel().writeAndFlush(new HeartbeatFrame());
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
        return "PollingSessionState[session=" + session + "]";
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
        sessions.remove(session.sessionId());
    }

}
