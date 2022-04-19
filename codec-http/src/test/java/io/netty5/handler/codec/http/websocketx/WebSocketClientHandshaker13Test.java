/*
 * Copyright 2015 The Netty Project
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

import io.netty5.handler.codec.http.DefaultFullHttpResponse;
import io.netty5.handler.codec.http.FullHttpResponse;
import io.netty5.handler.codec.http.HttpHeaderNames;
import io.netty5.handler.codec.http.HttpHeaderValues;
import io.netty5.handler.codec.http.HttpHeaders;
import io.netty5.handler.codec.http.HttpResponseStatus;
import io.netty5.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static io.netty5.buffer.api.DefaultBufferAllocators.preferredAllocator;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class WebSocketClientHandshaker13Test extends WebSocketClientHandshakerTest {

    @Override
    protected WebSocketClientHandshaker newHandshaker(URI uri, String subprotocol, HttpHeaders headers,
                                                      boolean absoluteUpgradeUrl) {
        return new WebSocketClientHandshaker13(uri, subprotocol, false, headers,
                                               1024, true, true, 10000,
                                               absoluteUpgradeUrl);
    }

    @Override
    protected CharSequence getProtocolHeaderName() {
        return HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL;
    }

    @Override
    protected CharSequence[] getHandshakeRequiredHeaderNames() {
        return new CharSequence[] {
                HttpHeaderNames.HOST,
                HttpHeaderNames.UPGRADE,
                HttpHeaderNames.CONNECTION,
                HttpHeaderNames.SEC_WEBSOCKET_KEY,
                HttpHeaderNames.SEC_WEBSOCKET_VERSION,
        };
    }

    @Test
    void testWebSocketClientInvalidUpgrade() {
        var handshaker = newHandshaker(URI.create("ws://localhost:9999/ws"), null,
                                       null, false);
        var response = websocketUpgradeResponse();
        response.headers().remove(HttpHeaderNames.UPGRADE);

        final WebSocketClientHandshakeException exception;
        try (response) {
            exception = assertThrows(WebSocketClientHandshakeException.class,
                                     () -> handshaker.finishHandshake(null, response));
        }

        assertEquals("Invalid handshake response upgrade: null", exception.getMessage());
        assertNotNull(exception.response());
        assertEquals(response.headers(), exception.response().headers());
    }

    @Test
    void testWebSocketClientInvalidConnection() {
        var handshaker = newHandshaker(URI.create("ws://localhost:9999/ws"), null,
                                       null, false);
        var response = websocketUpgradeResponse();
        response.headers().set(HttpHeaderNames.CONNECTION, "Close");

        final WebSocketClientHandshakeException exception;
        try (response) {
            exception = assertThrows(WebSocketClientHandshakeException.class,
                                     () -> handshaker.finishHandshake(null, response));
        }

        assertEquals("Invalid handshake response connection: Close", exception.getMessage());
        assertNotNull(exception.response());
        assertEquals(response.headers(), exception.response().headers());
    }

    @Test
    void testWebSocketClientInvalidNullAccept() {
        var handshaker = newHandshaker(URI.create("ws://localhost:9999/ws"), null,
                                       null, false);
        var response = websocketUpgradeResponse();

        final WebSocketClientHandshakeException exception;
        try (response) {
            exception = assertThrows(WebSocketClientHandshakeException.class,
                                     () -> handshaker.finishHandshake(null, response));
        }

        assertEquals("Invalid handshake response sec-websocket-accept: null", exception.getMessage());
        assertNotNull(exception.response());
        assertEquals(response.headers(), exception.response().headers());
    }

    @Test
    void testWebSocketClientInvalidExpectedAccept() {
        var handshaker = newHandshaker(URI.create("ws://localhost:9999/ws"), null,
                                       null, false);
        final String sentNonce;
        try (var request = handshaker.newHandshakeRequest(preferredAllocator())) {
            sentNonce = request.headers().get(HttpHeaderNames.SEC_WEBSOCKET_KEY);
        }

        String fakeAccept = WebSocketUtil.base64(WebSocketUtil.randomBytes(16));
        var response = websocketUpgradeResponse();
        response.headers().set(HttpHeaderNames.SEC_WEBSOCKET_ACCEPT, fakeAccept);

        final WebSocketClientHandshakeException exception;
        try (response) {
            exception = assertThrows(WebSocketClientHandshakeException.class,
                                     () -> handshaker.finishHandshake(null, response));
        }

        String expectedAccept = WebSocketUtil.calculateV13Accept(sentNonce);
        assertEquals("Invalid handshake response sec-websocket-accept: " + fakeAccept + ", expected: "
                     + expectedAccept, exception.getMessage());
        assertNotNull(exception.response());
        assertEquals(response.headers(), exception.response().headers());
    }

    private static FullHttpResponse websocketUpgradeResponse() {
        var response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.SWITCHING_PROTOCOLS, preferredAllocator().allocate(0));
        response.headers()
                .set(HttpHeaderNames.CONNECTION, HttpHeaderValues.UPGRADE)
                .set(HttpHeaderNames.UPGRADE, HttpHeaderValues.WEBSOCKET);
        return response;
    }
}
