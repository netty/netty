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

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class WebSocketServerHandshakerTest {

    protected abstract WebSocketServerHandshaker newHandshaker(String webSocketURL, String subprotocols,
            WebSocketDecoderConfig decoderConfig);

    protected abstract WebSocketVersion webSocketVersion();

    @Test
    public void testDuplicateHandshakeResponseHeaders() {
        WebSocketServerHandshaker serverHandshaker = newHandshaker("ws://example.com/chat",
                                                                   "chat", WebSocketDecoderConfig.DEFAULT);
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/chat");
        request.headers()
               .set(HttpHeaderNames.HOST, "example.com")
               .set(HttpHeaderNames.ORIGIN, "example.com")
               .set(HttpHeaderNames.UPGRADE, HttpHeaderValues.WEBSOCKET)
               .set(HttpHeaderNames.CONNECTION, HttpHeaderValues.UPGRADE)
               .set(HttpHeaderNames.SEC_WEBSOCKET_KEY, "dGhlIHNhbXBsZSBub25jZQ==")
               .set(HttpHeaderNames.SEC_WEBSOCKET_ORIGIN, "http://example.com")
               .set(HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL, "chat, superchat")
               .set(HttpHeaderNames.WEBSOCKET_PROTOCOL, "chat, superchat")
               .set(HttpHeaderNames.SEC_WEBSOCKET_VERSION, webSocketVersion().toAsciiString());
        HttpHeaders customResponseHeaders = new DefaultHttpHeaders();
        // set duplicate required headers and one custom
        customResponseHeaders
                .set(HttpHeaderNames.CONNECTION, HttpHeaderValues.UPGRADE)
                .set(HttpHeaderNames.UPGRADE, HttpHeaderValues.WEBSOCKET)
                .set(HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL, "superchat")
                .set(HttpHeaderNames.WEBSOCKET_PROTOCOL, "superchat")
                .set("custom", "header");

        if (webSocketVersion() != WebSocketVersion.V00) {
            customResponseHeaders.set(HttpHeaderNames.SEC_WEBSOCKET_ACCEPT, "12345");
        }

        FullHttpResponse response = null;
        try {
            response = serverHandshaker.newHandshakeResponse(request, customResponseHeaders);
            HttpHeaders responseHeaders = response.headers();

            assertEquals(1, responseHeaders.getAll(HttpHeaderNames.CONNECTION).size());
            assertEquals(1, responseHeaders.getAll(HttpHeaderNames.UPGRADE).size());
            assertTrue(responseHeaders.containsValue("custom", "header", true));

            if (webSocketVersion() != WebSocketVersion.V00) {
                assertFalse(responseHeaders.containsValue(HttpHeaderNames.SEC_WEBSOCKET_ACCEPT, "12345", false));
                assertEquals(1, responseHeaders.getAll(HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL).size());
                assertEquals("chat", responseHeaders.get(HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL));
            } else {
                assertEquals(1, responseHeaders.getAll(HttpHeaderNames.WEBSOCKET_PROTOCOL).size());
                assertEquals("chat", responseHeaders.get(HttpHeaderNames.WEBSOCKET_PROTOCOL));
            }
        } finally {
            request.release();
            if (response != null) {
                response.release();
            }
        }
    }

    @Test
    public void testWebSocketServerHandshakeException() {
        WebSocketServerHandshaker serverHandshaker = newHandshaker("ws://example.com/chat",
                                                                   "chat", WebSocketDecoderConfig.DEFAULT);

        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET,
                                                             "ws://example.com/chat");
        request.headers().set("x-client-header", "value");
        try {
            serverHandshaker.handshake(null, request, null, null);
        } catch (WebSocketServerHandshakeException exception) {
            assertNotNull(exception.getMessage());
            assertEquals(request.headers(), exception.request().headers());
            assertEquals(HttpMethod.GET, exception.request().method());
        } finally {
            request.release();
        }
    }
}

