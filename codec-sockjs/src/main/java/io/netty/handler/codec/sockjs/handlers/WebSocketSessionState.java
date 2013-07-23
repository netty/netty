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

import io.netty.channel.ChannelHandlerContext;
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

    @Override
    public void onConnect(final SockJSSession session, final ChannelHandlerContext ctx) {
    }

    @Override
    public void onOpen(final SockJSSession session, final ChannelHandlerContext ctx) {
    }

    @Override
    public boolean isInUse(final SockJSSession session) {
        return session.context().channel().isActive();
    }

    @Override
    public void onSockJSServerInitiatedClose(final SockJSSession session) {
        logger.debug("Will close session context " + session.context());
        session.context().close();
    }

    @Override
    public String toString() {
        return "WebSocketSessionState";
    }
}
