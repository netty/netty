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
import io.netty.handler.codec.sockjs.util.ArgumentUtil;
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

    public SendingSessionState(final ConcurrentMap<String, SockJsSession> sessions) {
        ArgumentUtil.checkNotNull(sessions, "sessions");
        this.sessions = sessions;
    }

    @Override
    public void onConnect(final SockJsSession session, final ChannelHandlerContext ctx) {
    }

    @Override
    public void onOpen(final SockJsSession session, final ChannelHandlerContext ctx) {
    }

    @Override
    public void onSockJSServerInitiatedClose(final SockJsSession session) {
        if (logger.isDebugEnabled()) {
            logger.debug("Will close session context {}", session.context());
        }
        session.context().close();
        sessions.remove(session.sessionId());
    }

    @Override
    public boolean isInUse(final SockJsSession session) {
        return false;
    }

    @Override
    public String toString() {
        return "SendingSessionState";
    }

    @Override
    public void onClose() {
    }

}
