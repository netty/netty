/*
 * Copyright 2020 The Netty Project
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
package io.netty.handler.codec.http.websocketx;

import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.ReferenceCounted;

/**
 * Server exception during handshaking process.
 *
 * <p><b>IMPORTANT</b>: This exception does not contain any {@link ReferenceCounted} fields
 * e.g. {@link FullHttpRequest}, so no special treatment is needed.
 */
public final class WebSocketServerHandshakeException extends WebSocketHandshakeException {

    private static final long serialVersionUID = 1L;

    private final HttpRequest request;

    public WebSocketServerHandshakeException(String message) {
        this(message, null);
    }

    public WebSocketServerHandshakeException(String message, HttpRequest httpRequest) {
        super(message);
        if (httpRequest != null) {
            request = new DefaultHttpRequest(httpRequest.protocolVersion(), httpRequest.method(),
                                             httpRequest.uri(), httpRequest.headers());
        } else {
            request = null;
        }
    }

    /**
     * Returns a {@link HttpRequest request} if exception occurs during request validation otherwise {@code null}.
     */
    public HttpRequest request() {
        return request;
    }
}
