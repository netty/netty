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

import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.util.ReferenceCounted;

/**
 * Client exception during handshaking process.
 *
 *  <p><b>IMPORTANT</b>: This exception does not contain any {@link ReferenceCounted} fields
 *  e.g. {@link FullHttpResponse}, so no special treatment is needed.
 */
public final class WebSocketClientHandshakeException extends WebSocketHandshakeException {

    private static final long serialVersionUID = 1L;

    private final HttpResponse response;

    public WebSocketClientHandshakeException(String message) {
        this(message, null);
    }

    public WebSocketClientHandshakeException(String message, HttpResponse httpResponse) {
        super(message);
        if (httpResponse != null) {
            response = new DefaultHttpResponse(httpResponse.protocolVersion(),
                                               httpResponse.status(), httpResponse.headers());
        } else {
            response = null;
        }
    }

    /**
     * Returns a {@link HttpResponse response} if exception occurs during response validation otherwise {@code null}.
     */
    public HttpResponse response() {
        return response;
    }
}
