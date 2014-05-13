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

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.sockjs.SockJsSessionContext;
import io.netty.handler.codec.sockjs.util.ArgumentUtil;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.concurrent.ConcurrentMap;

/**
 * A sending session state is conserned about dealing with session interactions
 * for sending data to a SockJsSession/SockJsService.
 *
 */
class SendingSessionState implements SessionState {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(SendingSessionState.class);
    private final ConcurrentMap<String, SockJsSession> sessions;
    private final SockJsSession session;

    SendingSessionState(final ConcurrentMap<String, SockJsSession> sessions, final SockJsSession session) {
        ArgumentUtil.checkNotNull(sessions, "sessions");
        this.sessions = sessions;
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
    public ChannelHandlerContext getSendingContext() {
        return session.openContext();
    }

    @Override
    public void onSockJSServerInitiatedClose() {
        if (logger.isDebugEnabled()) {
            logger.debug("Will close session connectionContext {}", session.connectionContext());
        }
        session.connectionContext().close();
        sessions.remove(session.sessionId());
    }

    @Override
    public boolean isInUse() {
        return false;
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
    public String toString() {
        return StringUtil.simpleClassName(this);
    }

    @Override
    public void onClose() {
        session.onClose();
    }

    @Override
    public void storeMessage(String message) {
        session.addMessage(message);
    }

}
