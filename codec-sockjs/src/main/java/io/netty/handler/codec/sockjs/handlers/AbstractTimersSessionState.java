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

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.sockjs.protocol.HeartbeatFrame;
import io.netty.handler.codec.sockjs.util.ArgumentUtil;
import io.netty.util.concurrent.ScheduledFuture;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

/**
 * Base class for SessionState implementations that require timers.
 *
 * This class provides a session timeout timer and a heartbeat timer
 * which are started when the onConnect method is called..
 */
abstract class AbstractTimersSessionState implements SessionState {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(AbstractTimersSessionState.class);

    private final ConcurrentMap<String, SockJSSession> sessions;
    private ScheduledFuture<?> heartbeatFuture;
    private ScheduledFuture<?> sessionTimer;

    public AbstractTimersSessionState(final ConcurrentMap<String, SockJSSession> sessions) {
        ArgumentUtil.checkNotNull(sessions, "sessions");
        this.sessions = sessions;
    }

    @Override
    public void onConnect(final SockJSSession session, final ChannelHandlerContext ctx) {
        startSessionTimer(ctx, session);
        startHeartbeatTimer(ctx, session);
    }

    private void startSessionTimer(final ChannelHandlerContext ctx, final SockJSSession session) {
        if (sessionTimer == null) {
            sessionTimer = ctx.executor().scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    final long now = TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
                    if (isInUse(session)) {
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
    }

    private void startHeartbeatTimer(final ChannelHandlerContext ctx, final SockJSSession session) {
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
    }

}
