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

/**
 * A SessionState represents differences in the types of sessions possible with SockJS.
 *
 * @see PollingSessionState
 * @see StreamingSessionState
 * @see SendingSessionState
 * @see WebSocketSessionState
 */
interface SessionState {

    enum State {
        CONNECTING,
        OPEN,
        CLOSED,
        INTERRUPTED }

    /**
     * Returns the current session state.
     *
     * @return {@code State} the {@link State} of the session
     */
    State getState();

    /**
     * Sets this session state.
     *
     * @param state the new state for this session
     */
    void setState(State state);

    /**
     * Called when a new session is connecting.
     *
     * @param ctx the {@link ChannelHandlerContext} for the current connection/channel
     * @param sockJsSessionContext the {@link SockJsSessionContext} for the current connection/channel
     */
    void onConnect(ChannelHandlerContext ctx, SockJsSessionContext sockJsSessionContext);

    /**
     * Called when a request for a connected session is received.
     *
     * @param ctx the {@link ChannelHandlerContext} for the current connection/channel. Note
     *            that this ChannelHandlerContext is different from the one that opened the
     *            the sesssion and was passed to the onConnect method.
     */
    void onOpen(ChannelHandlerContext ctx);

    /**
     * Called when a message is to be sent to the SockJS service.
     *
     * @param message the message.
     */
    void onMessage(String message) throws Exception;

    /**
     * Stores the message in the session for later delivery to when a client is
     * connected.
     *
     */
    void storeMessage(String message);

    /**
     * Returns the ChannelHandlerContext that should be used to communicate with the client.
     * This may be different for different transports. For some transports this will be the
     * context that opened the connection and others it will be the current context.
     *
     * @return {@code ChannelHandlerContext} the context to be used for sending.
     */
    ChannelHandlerContext getSendingContext();

    /**
     * Called after the {@link SockJsSession#onClose()} method has been called enabling
     * this SessionState to perform any clean up actions requried.
     */
    void onClose();

    /**
     * Called when the SockJS server has initiated a close of the session.
     */
    void onSockJSServerInitiatedClose();

    /**
     * Indicates if the underlying session is in use.
     *
     * @return {@code true} if the session is in use.
     */
    boolean isInUse();

    /**
     * Sets the underlying session as inuse.
     */
    void setInuse();

    /**
     * Resets the underlyig session as no longer inuse.
     */
    void resetInuse();

}
