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
import io.netty.handler.codec.sockjs.SockJsSessionContext;
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
    private final SockJsSession session;

    private ScheduledFuture<?> heartbeatFuture;

    WebSocketSessionState(final SockJsSession session) {
        this.session = session;
    }

    @Override
    public State getState() {
        return session.getState();
    }

    @Override
    public void setState(final State state) {
        session.setState(state);
    }

    @Override
    public void onConnect(final ChannelHandlerContext ctx, final SockJsSessionContext sockJsSessionContext) {
        session.setConnectionContext(ctx);
        session.onOpen(sockJsSessionContext);
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
    public void onOpen(final ChannelHandlerContext ctx) {
        session.setInuse(true);
    }

    @Override
    public void onMessage(String message) throws Exception {
        session.onMessage(message);
    }

    @Override
    public void storeMessage(String message) {
        session.addMessage(message);
    }

    @Override
    public ChannelHandlerContext getSendingContext() {
        return session.connectionContext();
    }

    @Override
    public boolean isInUse() {
        return session.connectionContext().channel().isActive();
    }

    @Override
    public void setInuse() {
        // NoOp
    }

    @Override
    public void resetInuse() {
        // NoOp
    }

    @Override
    public void onSockJSServerInitiatedClose() {
        shutdownHearbeat();
    }

    @Override
    public String toString() {
        return StringUtil.simpleClassName(this);
    }

    @Override
    public void onClose() {
        session.onClose();
        session.setInuse(false);
        shutdownHearbeat();
    }

    private void shutdownHearbeat() {
        if (heartbeatFuture != null) {
            logger.debug("Stopping heartbeat job");
            heartbeatFuture.cancel(true);
        }
    }
}
