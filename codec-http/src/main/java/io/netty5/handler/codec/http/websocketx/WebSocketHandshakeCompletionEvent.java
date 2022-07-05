/*
 * Copyright 2022 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty5.handler.codec.http.websocketx;

import io.netty5.handler.codec.ProtocolEvent;

import static java.util.Objects.requireNonNull;

/**
 * {@link ProtocolEvent} that indicate the completion of a websocket handshake.
 */
public final class WebSocketHandshakeCompletionEvent implements ProtocolEvent<WebSocketVersion> {

    private final WebSocketVersion version;
    private final Throwable cause;

    /**
     * Create a new event that indicate a successful websocket handshake.
     *
     * @param version the version.
     */
    public WebSocketHandshakeCompletionEvent(WebSocketVersion version) {
        this.version = requireNonNull(version, "version");
        this.cause = null;
    }

    /**
     * Create a new event that indicate a failed websocket handshake.
     *
     * @param cause the cause of the failure
     */
    public WebSocketHandshakeCompletionEvent(Throwable cause) {
        this.version = null;
        this.cause = requireNonNull(cause, "cause");
    }

    @Override
    public Throwable cause() {
        return cause;
    }

    @Override
    public WebSocketVersion data() {
        return version;
    }

    @Override
    public String toString() {
        final Throwable cause = cause();
        return cause == null? getClass().getSimpleName() + "(SUCCESS)" :
                getClass().getSimpleName() +  '(' + cause + ')';
    }
}
