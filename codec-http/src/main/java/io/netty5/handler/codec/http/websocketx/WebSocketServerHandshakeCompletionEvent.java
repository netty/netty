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

public final class WebSocketServerHandshakeCompletionEvent
        extends WebSocketHandshakeCompletionEvent<WebSocketServerHandshakeCompletionEvent.WebSocketHandshakeData> {

    public WebSocketServerHandshakeCompletionEvent(
            String requestUri, HttpHeaders requestHeaders, String selectedSubprotocol) {
        super(new WebSocketHandshakeData(requestUri, requestHeaders, selectedSubprotocol));
    }

    public WebSocketServerHandshakeCompletionEvent(Throwable cause) {
        super(cause);
    }

    public static final class WebSocketHandshakeData {
        private final String requestUri;
        private final HttpHeaders requestHeaders;
        private final String selectedSubprotocol;

        WebSocketHandshakeData(String requestUri, HttpHeaders requestHeaders, String selectedSubprotocol) {
            this.requestUri = requestUri;
            this.requestHeaders = requestHeaders;
            this.selectedSubprotocol = selectedSubprotocol;
        }

        public String requestUri() {
            return requestUri;
        }

        public HttpHeaders requestHeaders() {
            return requestHeaders;
        }

        public String selectedSubprotocol() {
            return selectedSubprotocol;
        }
    }
}
