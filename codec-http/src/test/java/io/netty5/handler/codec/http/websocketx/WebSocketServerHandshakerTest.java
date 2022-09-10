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
package io.netty5.handler.codec.http.websocketx;

import io.netty5.buffer.Buffer;
import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.handler.codec.http.DefaultFullHttpRequest;
import io.netty5.handler.codec.http.DefaultHttpRequest;
import io.netty5.handler.codec.http.EmptyLastHttpContent;
import io.netty5.handler.codec.http.FullHttpRequest;
import io.netty5.handler.codec.http.FullHttpResponse;
import io.netty5.handler.codec.http.HttpHeaderNames;
import io.netty5.handler.codec.http.HttpHeaderValues;
import io.netty5.handler.codec.http.HttpMethod;
import io.netty5.handler.codec.http.HttpRequest;
import io.netty5.handler.codec.http.HttpRequestDecoder;
import io.netty5.handler.codec.http.HttpResponse;
import io.netty5.handler.codec.http.HttpResponseDecoder;
import io.netty5.handler.codec.http.HttpResponseEncoder;
import io.netty5.handler.codec.http.HttpVersion;
import io.netty5.handler.codec.http.headers.HttpHeaders;
import io.netty5.util.concurrent.Future;
import org.junit.jupiter.api.Test;

import static io.netty5.buffer.DefaultBufferAllocators.preferredAllocator;
import static io.netty5.handler.codec.http.HttpResponseStatus.SWITCHING_PROTOCOLS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class WebSocketServerHandshakerTest {

    protected abstract WebSocketServerHandshaker newHandshaker(String webSocketURL, String subprotocols,
            WebSocketDecoderConfig decoderConfig);

    protected abstract WebSocketVersion webSocketVersion();

    @Test
    public void testDuplicateHandshakeResponseHeaders() {
        WebSocketServerHandshaker serverHandshaker = newHandshaker("ws://example.com/chat",
                                                                   "chat", WebSocketDecoderConfig.DEFAULT);
        FullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.GET, "/chat", preferredAllocator().allocate(0));
        request.headers()
               .set(HttpHeaderNames.HOST, "example.com")
               .set(HttpHeaderNames.ORIGIN, "example.com")
               .set(HttpHeaderNames.UPGRADE, HttpHeaderValues.WEBSOCKET)
               .set(HttpHeaderNames.CONNECTION, HttpHeaderValues.UPGRADE)
               .set(HttpHeaderNames.SEC_WEBSOCKET_KEY, "dGhlIHNhbXBsZSBub25jZQ==")
               .set(HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL, "chat, superchat")
               .set(HttpHeaderNames.SEC_WEBSOCKET_VERSION, webSocketVersion().toAsciiString());
        HttpHeaders customResponseHeaders = HttpHeaders.newHeaders();
        // set duplicate required headers and one custom
        customResponseHeaders
                .set(HttpHeaderNames.CONNECTION, HttpHeaderValues.UPGRADE)
                .set(HttpHeaderNames.UPGRADE, HttpHeaderValues.WEBSOCKET)
                .set(HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL, "superchat")
                .set("custom", "header");

        customResponseHeaders.set(HttpHeaderNames.SEC_WEBSOCKET_ACCEPT, "12345");

        FullHttpResponse response = null;
        try {
            response = serverHandshaker.newHandshakeResponse(preferredAllocator(), request, customResponseHeaders);
            HttpHeaders responseHeaders = response.headers();

            assertThat(responseHeaders.values(HttpHeaderNames.CONNECTION)).hasSize(1);
            assertThat(responseHeaders.values(HttpHeaderNames.UPGRADE)).hasSize(1);
            assertTrue(responseHeaders.containsIgnoreCase("custom", "header"));

            assertFalse(responseHeaders.contains(HttpHeaderNames.SEC_WEBSOCKET_ACCEPT, "12345"));
            assertThat(responseHeaders.values(HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL)).hasSize(1);
            assertEquals("chat", responseHeaders.get(HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL));
        } finally {
            request.close();
            if (response != null) {
                response.close();
            }
        }
    }

    @Test
    public void testWebSocketServerHandshakeException() {
        WebSocketServerHandshaker serverHandshaker = newHandshaker("ws://example.com/chat",
                                                                   "chat", WebSocketDecoderConfig.DEFAULT);

        FullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.GET, "ws://example.com/chat", preferredAllocator().allocate(0));
        request.headers().set("x-client-header", "value");
        try {
            serverHandshaker.handshake(new EmbeddedChannel(), request, null);
        } catch (WebSocketServerHandshakeException exception) {
            assertNotNull(exception.getMessage());
            assertEquals(request.headers(), exception.request().headers());
            assertEquals(HttpMethod.GET, exception.request().method());
        } finally {
            request.close();
        }
    }

    @Test
    public void testHandshakeForHttpRequestWithoutAggregator() {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestDecoder(), new HttpResponseEncoder());
        WebSocketServerHandshaker serverHandshaker = newHandshaker("ws://example.com/chat",
                                                                   "chat", WebSocketDecoderConfig.DEFAULT);

        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/chat");
        request.headers()
               .set(HttpHeaderNames.HOST, "example.com")
               .set(HttpHeaderNames.UPGRADE, HttpHeaderValues.WEBSOCKET)
               .set(HttpHeaderNames.CONNECTION, HttpHeaderValues.UPGRADE)
               .set(HttpHeaderNames.SEC_WEBSOCKET_KEY, "dGhlIHNhbXBsZSBub25jZQ==")
               .set(HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL, "chat, superchat")
               .set(HttpHeaderNames.SEC_WEBSOCKET_VERSION, webSocketVersion().toAsciiString());

        Future<Void> future = serverHandshaker.handshake(channel, request);
        assertFalse(future.isDone());
        assertNotNull(channel.pipeline().get("handshaker"));
        assertNull(channel.pipeline().get("httpAggregator"));
        channel.writeInbound(new EmptyLastHttpContent(channel.bufferAllocator()));

        assertTrue(future.isDone());
        assertNull(channel.pipeline().get("handshaker"));

        Buffer buf = channel.readOutbound();
        assertFalse(channel.finish());

        channel = new EmbeddedChannel(new HttpResponseDecoder());
        assertTrue(channel.writeInbound(buf));

        HttpResponse response = channel.readInbound();
        assertEquals(SWITCHING_PROTOCOLS, response.status());
        assertTrue(response.headers().containsIgnoreCase(HttpHeaderNames.UPGRADE, HttpHeaderValues.WEBSOCKET));

        EmptyLastHttpContent emptyLastHttpContent = channel.readInbound();
        emptyLastHttpContent.close();

        assertFalse(channel.finish());
    }
}
