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

/**
 * A SessionState represents differences in the types of sessions possible with SockJS.
 *
 * @see PollingSessionState
 * @see StreamingSessionState
 * @see SendingSessionState
 * @see WebSocketSessionState
 */
interface SessionState {

    /**
     * Called when a new session is connecting.
     *
     * @param session the {@link SockJsSession}.
     * @param ctx the {@link ChannelHandlerContext} for the current connection/channel
     */
    void onConnect(SockJsSession session, ChannelHandlerContext ctx);

    /**
     * Called when a request for a connected session is received.
     *
     * @param session the {@link SockJsSession}.
     * @param ctx the {@link ChannelHandlerContext} for the current connection/channel. Note
     *            that this ChannelHandlerContext is different from the one that opened the
     *            the sesssion and was passed to the onConnect method.
     */
    void onOpen(SockJsSession session, ChannelHandlerContext ctx);

    /**
     * Called after the {@link SockJsSession#onClose()} method has been called enabling
     * this SessionState to perform any clean up actions requried.
     */
    void onClose();

    /**
     * Called when the SockJS server has initiated a close of the session.
     */
    void onSockJSServerInitiatedClose(SockJsSession session);

    /**
     * Indicates if this session is in use.
     *
     * @return {@code true} if the session is in use.
     */
    boolean isInUse(SockJsSession session);

}
