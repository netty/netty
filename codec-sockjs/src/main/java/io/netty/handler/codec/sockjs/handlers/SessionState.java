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
import io.netty.handler.codec.sockjs.SessionContext;

/**
 * A SessionState represents differences in the types of sessions possible
 * with SockJS.
 *
 * @see PollingSessionState
 * @see StreamingSessionState
 * @see SendingSessionState
 * @see WebSocketSessionState
 */
interface SessionState {

    enum States { CONNECTING, OPEN, CLOSED, INTERRUPTED }

    /**
     * Get the state that this session is currently in.
     * @return
     */
    States getState();

    /**
     * Called when a new session is connecting.
     *
     * @param sessionContext the session context for the SockJS service.
     * @param ctx the {@link ChannelHandlerContext} for the current connection/channel
     */
    void onConnect(final SessionContext sessionContext, final ChannelHandlerContext ctx);

    /**
     * Called when a request for a connected session is received.
     *
     * @param ctx the {@link ChannelHandlerContext} for the current connection/channel. Note
     *            that this ChannelHandlerContext is different from the one that opened the
     *            the sesssion and was passed to the onConnect method.
     */
    void onOpen(final ChannelHandlerContext ctx);

    /**
     * Called when the current request has completed and the current ChannelHandlerContext
     * has become inactive.
     *
     * This can happen at different times for different types of SessionStates. For example,
     * a streaming connection would be held open for a longer period of time compared
     * to a polling connection which would have many short lived connections.
     *
     */
    void onRequestCompleted(final ChannelHandlerContext ctx);

    /**
     * Called when the a request is received and the session is in the CLOSED state.
     */
    void onClose();

    /**
     * Called when the SockJSService called close on the {@link SessionContext}
     */
    void onSessionContextClose();

    /**
     * Called when the SockJS server has initiated a close of the session.
     */
    void onSockJSServerInitiatedClose();

    /**
     * Returns the ChannelHandlerContext that opened the session.
     *
     * @return {@link ChannelHandlerContext} that was inuse when opening the session.
     */
    ChannelHandlerContext getSessionChannelHandlerContext();

    /**
     * Called when a message is to be processed by the underlying SockJS service
     */
    void onMessage(final String message) throws Exception;

    /**
     * Called when a message is sent to the session.
     */
    void addMessage(final String message);

    /**
     * Indicates if this session is in use.
     *
     * @return {@code true} if the session is in use.
     */
    boolean isInUse();

}
