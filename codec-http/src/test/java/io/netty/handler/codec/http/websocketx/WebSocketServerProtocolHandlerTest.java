/*
 * Copyright 2012 The Netty Project
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
package io.netty.handler.codec.http.websocketx;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseEncoder;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.Queue;

import static io.netty.handler.codec.http.HttpHeaders.Values.*;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.*;
import static org.junit.Assert.*;

public class WebSocketServerProtocolHandlerTest {

    @Test
    public void testHttpUpgradeRequest() throws Exception {
        EmbeddedChannel ch = createChannel(new MockOutboundHandler());
        ChannelHandlerContext handshakerCtx = ch.pipeline().context(WebSocketServerProtocolHandshakeHandler.class);
        writeUpgradeRequest(ch);
        assertEquals(SWITCHING_PROTOCOLS, ((HttpResponse) ch.lastOutboundBuffer().poll()).getStatus());
        assertNotNull(WebSocketServerProtocolHandler.getHandshaker(handshakerCtx));
    }

    @Test
    public void testSubsequentHttpRequestsAfterUpgradeShouldReturn403() throws Exception {
        EmbeddedChannel ch = createChannel(new MockOutboundHandler());

        writeUpgradeRequest(ch);
        assertEquals(SWITCHING_PROTOCOLS, ((HttpResponse) ch.lastOutboundBuffer().poll()).getStatus());

        ch.writeInbound(new DefaultFullHttpRequest(HTTP_1_1, HttpMethod.GET, "/test"));
        assertEquals(FORBIDDEN, ((HttpResponse) ch.lastOutboundBuffer().poll()).getStatus());
    }

    @Test
    public void testHttpUpgradeRequestInvalidUpgradeHeader() {
        EmbeddedChannel ch = createChannel();
        FullHttpRequest httpRequestWithEntity = new WebSocketRequestBuilder().httpVersion(HTTP_1_1)
                .method(HttpMethod.GET)
                .uri("/test")
                .connection("Upgrade")
                .version00()
                .upgrade("BogusSocket")
                .build();

        ch.writeInbound(httpRequestWithEntity);

        FullHttpResponse response = getHttpResponse(ch);
        assertEquals(BAD_REQUEST, response.getStatus());
        assertEquals("not a WebSocket handshake request: missing upgrade", getResponseMessage(response));
    }

    @Test
    public void testHttpUpgradeRequestMissingWSKeyHeader() {
        EmbeddedChannel ch = createChannel();
        HttpRequest httpRequest = new WebSocketRequestBuilder().httpVersion(HTTP_1_1)
                .method(HttpMethod.GET)
                .uri("/test")
                .key(null)
                .connection("Upgrade")
                .upgrade(WEBSOCKET.toLowerCase())
                .version13()
                .build();

        ch.writeInbound(httpRequest);

        FullHttpResponse response = getHttpResponse(ch);
        assertEquals(BAD_REQUEST, response.getStatus());
        assertEquals("not a WebSocket request: missing key", getResponseMessage(response));
    }

    @Test
    public void testHandleTextFrame() {
        CustomTextFrameHandler customTextFrameHandler = new CustomTextFrameHandler();
        EmbeddedChannel ch = createChannel(customTextFrameHandler);
        writeUpgradeRequest(ch);
        // Removing the HttpRequestDecoder as we are writing a TextWebSocketFrame so decoding is not neccessary.
        ch.pipeline().remove(HttpRequestDecoder.class);

        ch.writeInbound(new TextWebSocketFrame("payload"));

        assertEquals("processed: payload", customTextFrameHandler.getContent());
    }

    private static EmbeddedChannel createChannel() {
        return createChannel(null);
    }

    private static EmbeddedChannel createChannel(ChannelHandler handler) {
        return new EmbeddedChannel(
                new WebSocketServerProtocolHandler("/test", null, false),
                new HttpRequestDecoder(),
                new HttpResponseEncoder(),
                new MockOutboundHandler(),
                handler);
    }

    private static void writeUpgradeRequest(EmbeddedChannel ch) {
        ch.writeInbound(WebSocketRequestBuilder.sucessful());
    }

    private static String getResponseMessage(FullHttpResponse response) {
        return new String(response.content().array());
    }

    private static FullHttpResponse getHttpResponse(EmbeddedChannel ch) {
        Queue<FullHttpResponse> outbound = ch.pipeline().get(MockOutboundHandler.class).responses;
        return outbound.poll();
    }

    private static class MockOutboundHandler
        extends ChannelOutboundHandlerAdapter {

        final Queue<FullHttpResponse> responses = new ArrayDeque<FullHttpResponse>();

        @Override
        public void write(ChannelHandlerContext ctx, Object[] msgs, int index, int length, ChannelPromise promise) throws Exception {
            for (int i = index; i < length; i++) {
                responses.add((FullHttpResponse) msgs[i]);
            }
            promise.setSuccess();
        }
    }

    private static class CustomTextFrameHandler extends ChannelInboundHandlerAdapter {
        private String content;

        @Override
        public void messageReceived(ChannelHandlerContext ctx, Object[] msgs, int index, int length) throws Exception {
            assertEquals(1, length);
            content = "processed: " + ((TextWebSocketFrame) msgs[index]).text();

        }

        String getContent() {
            return content;
        }
    }
}
