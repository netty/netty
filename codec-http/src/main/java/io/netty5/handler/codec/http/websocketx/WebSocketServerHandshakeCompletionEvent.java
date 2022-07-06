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

import io.netty5.handler.codec.http.HttpHeaders;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * A websocket handshake was completed on the server-side.
 */
public final class WebSocketServerHandshakeCompletionEvent extends WebSocketHandshakeCompletionEvent {
    private final String requestUri;
    private final HttpHeaders requestHeaders;
    private final String selectedSubprotocol;

    public WebSocketServerHandshakeCompletionEvent(WebSocketVersion version,
            String requestUri, HttpHeaders requestHeaders, String selectedSubprotocol) {
        super(version);
        this.requestUri = requireNonNull(requestUri, "requestUri");
        this.requestHeaders = requireNonNull(requestHeaders, "requestHeaders");
        this.selectedSubprotocol = selectedSubprotocol;
    }

    public WebSocketServerHandshakeCompletionEvent(Throwable cause) {
        super(cause);
        requestUri = null;
        requestHeaders = null;
        selectedSubprotocol = null;
    }

    /**
     * Return the request uri of the handshake if {@link #isSuccess()} returns {@code true}, {@code null} otherwise.
     *
     * @return the uri.
     */
    public String requestUri() {
        return requestUri;
    }

    /**
     * Return the request {@link HttpHeaders} of the handshake if {@link #isSuccess()} returns {@code true},
     * {@code null} otherwise.
     *
     * @return the headers.
     */
    public HttpHeaders requestHeaders() {
        return requestHeaders;
    }

    /**
     * Return the selected sub-protocol of the handshake if {@link #isSuccess()} returns {@code true} and one was
     * selected, {@code null} otherwise.
     *
     * @return the sub-protocol.
     */
    public String selectedSubprotocol() {
        return selectedSubprotocol;
    }
}
