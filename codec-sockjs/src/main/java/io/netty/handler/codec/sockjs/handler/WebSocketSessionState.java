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

import java.util.concurrent.TimeUnit;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.sockjs.protocol.HeartbeatFrame;
import io.netty.util.concurrent.ScheduledFuture;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

/**
 * A session state for WebSockets.
 * Though the concept of a session for a WebSocket is not really required in SockJS,
 * as the sessions life time is the same as the life time of the connection, this is
 * included to keep concepts clear.
 */
class WebSocketSessionState implements SessionState {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(WebSocketSessionState.class);

    private ScheduledFuture<?> heartbeatFuture;

    @Override
    public void onConnect(final SockJsSession session, final ChannelHandlerContext ctx) {
        startHeartbeatTimer(ctx, session);
    }

    private void startHeartbeatTimer(final ChannelHandlerContext ctx, final SockJsSession session) {
        final long interval = session.config().webSocketHeartbeatInterval();
        if (interval > 0) {
            if (logger.isDebugEnabled()) {
                logger.info("Starting heartbeat with interval {}", interval);
            }
            heartbeatFuture = ctx.executor().scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    if (ctx.channel().isActive() && ctx.channel().isRegistered()) {
                        if (logger.isDebugEnabled()) {
                            logger.debug("Sending heartbeat for {}", session);
                        }
                        ctx.channel().writeAndFlush(new HeartbeatFrame());
                    }
                }
            }, interval, interval, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void onOpen(final SockJsSession session, final ChannelHandlerContext ctx) {
    }

    @Override
    public boolean isInUse(final SockJsSession session) {
        return session.context().channel().isActive();
    }

    @Override
    public void onSockJSServerInitiatedClose(final SockJsSession session) {
        shutdownHearbeat();
    }

    @Override
    public String toString() {
        return StringUtil.simpleClassName(this);
    }

    @Override
    public void onClose() {
        shutdownHearbeat();
    }

    private void shutdownHearbeat() {
        if (heartbeatFuture != null) {
            logger.debug("Stopping heartbeat job");
            heartbeatFuture.cancel(true);
        }
    }
}
