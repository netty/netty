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
package io.netty.handler.codec.sockjs;

import io.netty.channel.ChannelHandlerContext;

/**
 * Allows a {@link SockJSService} to interact with its session by providing
 * methods to send data, and to close the session.
 */
public interface SessionContext {
    /**
     * Send data to the current session. This data might be delivered immediately
     * of queued up in the session depending on the type of session (polling, streaming etc)
     *
     * @param message the message to be sent.
     */
    void send(final String message);

    /**
     * Close the current session.
     */
    void close();

    /**
     * Get the underlying ChannelHandlerContext.
     */
    ChannelHandlerContext getContext();
}
