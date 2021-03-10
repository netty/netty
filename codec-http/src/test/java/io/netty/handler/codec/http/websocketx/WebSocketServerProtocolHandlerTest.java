/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.http.websocketx;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;

import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.Queue;

import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.*;
import static org.junit.Assert.*;

public class WebSocketServerProtocolHandlerTest {

    private final Queue<FullHttpResponse> responses = new ArrayDeque<FullHttpResponse>();

    @Before
    public void setUp() {
        responses.clear();
    }

    @Test
    public void testHttpUpgradeRequest() {
        EmbeddedChannel ch = createChannel(new MockOutboundHandler());
        ChannelHandlerContext handshakerCtx = ch.pipeline().context(WebSocketServerProtocolHandshakeHandler.class);
        writeUpgradeRequest(ch);

        FullHttpResponse response = responses.remove();
        assertEquals(SWITCHING_PROTOCOLS, response.status());
        response.release();
        assertNotNull(WebSocketServerProtocolHandler.getHandshaker(handshakerCtx.channel()));
        assertFalse(ch.finish());
    }

    @Test
    public void testWebSocketServerProtocolHandshakeHandlerReplacedBeforeHandshake() {
        EmbeddedChannel ch = createChannel(new MockOutboundHandler());
        ChannelHandlerContext handshakerCtx = ch.pipeline().context(WebSocketServerProtocolHandshakeHandler.class);
        ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
                    // We should have removed the handler already.
                    assertNull(ctx.pipeline().context(WebSocketServerProtocolHandshakeHandler.class));
                }
            }
        });
        writeUpgradeRequest(ch);

        FullHttpResponse response = responses.remove();
        assertEquals(SWITCHING_PROTOCOLS, response.status());
        response.release();
        assertNotNull(WebSocketServerProtocolHandler.getHandshaker(handshakerCtx.channel()));
        assertFalse(ch.finish());
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

        FullHttpResponse response = responses.remove();
        assertEquals(BAD_REQUEST, response.status());
        assertEquals("not a WebSocket handshake request: missing upgrade", getResponseMessage(response));
        response.release();
        assertFalse(ch.finish());
    }

    @Test
    public void testHttpUpgradeRequestMissingWSKeyHeader() {
        EmbeddedChannel ch = createChannel();
        HttpRequest httpRequest = new WebSocketRequestBuilder().httpVersion(HTTP_1_1)
                .method(HttpMethod.GET)
                .uri("/test")
                .key(null)
                .connection("Upgrade")
                .upgrade(HttpHeaderValues.WEBSOCKET)
                .version13()
                .build();

        ch.writeInbound(httpRequest);

        FullHttpResponse response = responses.remove();
        assertEquals(BAD_REQUEST, response.status());
        assertEquals("not a WebSocket request: missing key", getResponseMessage(response));
        response.release();
        assertFalse(ch.finish());
    }

    @Test
    public void testCreateUTF8Validator() {
        WebSocketServerProtocolConfig config = WebSocketServerProtocolConfig.newBuilder()
                .websocketPath("/test")
                .withUTF8Validator(true)
                .build();

        EmbeddedChannel ch = new EmbeddedChannel(
                new WebSocketServerProtocolHandler(config),
                new HttpRequestDecoder(),
                new HttpResponseEncoder(),
                new MockOutboundHandler());
        writeUpgradeRequest(ch);

        FullHttpResponse response = responses.remove();
        assertEquals(SWITCHING_PROTOCOLS, response.status());
        response.release();

        assertNotNull(ch.pipeline().get(Utf8FrameValidator.class));
    }

    @Test
    public void testDoNotCreateUTF8Validator() {
        WebSocketServerProtocolConfig config = WebSocketServerProtocolConfig.newBuilder()
                .websocketPath("/test")
                .withUTF8Validator(false)
                .build();

        EmbeddedChannel ch = new EmbeddedChannel(
                new WebSocketServerProtocolHandler(config),
                new HttpRequestDecoder(),
                new HttpResponseEncoder(),
                new MockOutboundHandler());
        writeUpgradeRequest(ch);

        FullHttpResponse response = responses.remove();
        assertEquals(SWITCHING_PROTOCOLS, response.status());
        response.release();

        assertNull(ch.pipeline().get(Utf8FrameValidator.class));
    }

    @Test
    public void testHandleTextFrame() {
        CustomTextFrameHandler customTextFrameHandler = new CustomTextFrameHandler();
        EmbeddedChannel ch = createChannel(customTextFrameHandler);
        writeUpgradeRequest(ch);

        FullHttpResponse response = responses.remove();
        assertEquals(SWITCHING_PROTOCOLS, response.status());
        response.release();

        if (ch.pipeline().context(HttpRequestDecoder.class) != null) {
            // Removing the HttpRequestDecoder because we are writing a TextWebSocketFrame and thus
            // decoding is not necessary.
            ch.pipeline().remove(HttpRequestDecoder.class);
        }

        ch.writeInbound(new TextWebSocketFrame("payload"));

        assertEquals("processed: payload", customTextFrameHandler.getContent());
        assertFalse(ch.finish());
    }

    @Test
    public void testCheckValidWebSocketPath() {
        HttpRequest httpRequest = new WebSocketRequestBuilder().httpVersion(HTTP_1_1)
                .method(HttpMethod.GET)
                .uri("/test")
                .key(HttpHeaderNames.SEC_WEBSOCKET_KEY)
                .connection("Upgrade")
                .upgrade(HttpHeaderValues.WEBSOCKET)
                .version13()
                .build();

        WebSocketServerProtocolConfig config = WebSocketServerProtocolConfig.newBuilder()
                .websocketPath("/test")
                .checkStartsWith(true)
                .build();

        EmbeddedChannel ch = new EmbeddedChannel(
                new WebSocketServerProtocolHandler(config),
                new HttpRequestDecoder(),
                new HttpResponseEncoder(),
                new MockOutboundHandler());
        ch.writeInbound(httpRequest);

        FullHttpResponse response = responses.remove();
        assertEquals(SWITCHING_PROTOCOLS, response.status());
        response.release();
    }

    @Test
    public void testCheckInvalidWebSocketPath() {
        HttpRequest httpRequest = new WebSocketRequestBuilder().httpVersion(HTTP_1_1)
                .method(HttpMethod.GET)
                .uri("/testabc")
                .key(HttpHeaderNames.SEC_WEBSOCKET_KEY)
                .connection("Upgrade")
                .upgrade(HttpHeaderValues.WEBSOCKET)
                .version13()
                .build();

        WebSocketServerProtocolConfig config = WebSocketServerProtocolConfig.newBuilder()
                .websocketPath("/test")
                .checkStartsWith(true)
                .build();

        EmbeddedChannel ch = new EmbeddedChannel(
                new WebSocketServerProtocolHandler(config),
                new HttpRequestDecoder(),
                new HttpResponseEncoder(),
                new MockOutboundHandler());
        ch.writeInbound(httpRequest);

        ChannelHandlerContext handshakerCtx = ch.pipeline().context(WebSocketServerProtocolHandshakeHandler.class);
        assertNull(WebSocketServerProtocolHandler.getHandshaker(handshakerCtx.channel()));
    }

    @Test
    public void testExplicitCloseFrameSentWhenServerChannelClosed() throws Exception {
        WebSocketCloseStatus closeStatus = WebSocketCloseStatus.ENDPOINT_UNAVAILABLE;
        EmbeddedChannel client = createClient();
        EmbeddedChannel server = createServer();

        assertFalse(server.writeInbound(client.readOutbound()));
        assertFalse(client.writeInbound(server.readOutbound()));

        // When server channel closed with explicit close-frame
        assertTrue(server.writeOutbound(new CloseWebSocketFrame(closeStatus)));
        server.close();

        // Then client receives provided close-frame
        assertTrue(client.writeInbound(server.readOutbound()));
        assertFalse(server.isOpen());

        CloseWebSocketFrame closeMessage = client.readInbound();
        assertEquals(closeMessage.statusCode(), closeStatus.code());
        closeMessage.release();

        client.close();
        assertTrue(ReferenceCountUtil.release(client.readOutbound()));
        assertFalse(client.finishAndReleaseAll());
        assertFalse(server.finishAndReleaseAll());
    }

    @Test
    public void testCloseFrameSentWhenServerChannelClosedSilently() throws Exception {
        EmbeddedChannel client = createClient();
        EmbeddedChannel server = createServer();

        assertFalse(server.writeInbound(client.readOutbound()));
        assertFalse(client.writeInbound(server.readOutbound()));

        // When server channel closed without explicit close-frame
        server.close();

        // Then client receives NORMAL_CLOSURE close-frame
        assertTrue(client.writeInbound(server.readOutbound()));
        assertFalse(server.isOpen());

        CloseWebSocketFrame closeMessage = client.readInbound();
        assertEquals(closeMessage.statusCode(), WebSocketCloseStatus.NORMAL_CLOSURE.code());
        closeMessage.release();

        client.close();
        assertTrue(ReferenceCountUtil.release(client.readOutbound()));
        assertFalse(client.finishAndReleaseAll());
        assertFalse(server.finishAndReleaseAll());
    }

    @Test
    public void testExplicitCloseFrameSentWhenClientChannelClosed() throws Exception {
        WebSocketCloseStatus closeStatus = WebSocketCloseStatus.INVALID_PAYLOAD_DATA;
        EmbeddedChannel client = createClient();
        EmbeddedChannel server = createServer();

        assertFalse(server.writeInbound(client.readOutbound()));
        assertFalse(client.writeInbound(server.readOutbound()));

        // When client channel closed with explicit close-frame
        assertTrue(client.writeOutbound(new CloseWebSocketFrame(closeStatus)));
        client.close();

        // Then client receives provided close-frame
        assertFalse(server.writeInbound(client.readOutbound()));
        assertFalse(client.isOpen());
        assertFalse(server.isOpen());

        CloseWebSocketFrame closeMessage = decode(server.<ByteBuf>readOutbound(), CloseWebSocketFrame.class);
        assertEquals(closeMessage.statusCode(), closeStatus.code());
        closeMessage.release();

        assertFalse(client.finishAndReleaseAll());
        assertFalse(server.finishAndReleaseAll());
    }

    @Test
    public void testCloseFrameSentWhenClientChannelClosedSilently() throws Exception {
        EmbeddedChannel client = createClient();
        EmbeddedChannel server = createServer();

        assertFalse(server.writeInbound(client.readOutbound()));
        assertFalse(client.writeInbound(server.readOutbound()));

        // When client channel closed without explicit close-frame
        client.close();

        // Then server receives NORMAL_CLOSURE close-frame
        assertFalse(server.writeInbound(client.readOutbound()));
        assertFalse(client.isOpen());
        assertFalse(server.isOpen());

        CloseWebSocketFrame closeMessage = decode(server.<ByteBuf>readOutbound(), CloseWebSocketFrame.class);
        assertEquals(closeMessage, new CloseWebSocketFrame(WebSocketCloseStatus.NORMAL_CLOSURE));
        closeMessage.release();

        assertFalse(client.finishAndReleaseAll());
        assertFalse(server.finishAndReleaseAll());
    }

    private EmbeddedChannel createClient(ChannelHandler... handlers) throws Exception {
        WebSocketClientProtocolConfig clientConfig = WebSocketClientProtocolConfig.newBuilder()
            .webSocketUri("http://test/test")
            .dropPongFrames(false)
            .handleCloseFrames(false)
            .build();
        EmbeddedChannel ch = new EmbeddedChannel(false, false,
            new HttpClientCodec(),
            new HttpObjectAggregator(8192),
            new WebSocketClientProtocolHandler(clientConfig)
        );
        ch.pipeline().addLast(handlers);
        ch.register();
        return ch;
    }

    private EmbeddedChannel createServer(ChannelHandler... handlers) throws Exception {
        WebSocketServerProtocolConfig serverConfig = WebSocketServerProtocolConfig.newBuilder()
            .websocketPath("/test")
            .dropPongFrames(false)
            .build();
        EmbeddedChannel ch = new EmbeddedChannel(false, false,
            new HttpServerCodec(),
            new HttpObjectAggregator(8192),
            new WebSocketServerProtocolHandler(serverConfig)
        );
        ch.pipeline().addLast(handlers);
        ch.register();
        return ch;
    }

    @SuppressWarnings("SameParameterValue")
    private <T> T decode(ByteBuf input, Class<T> clazz) {
        EmbeddedChannel ch = new EmbeddedChannel(new WebSocket13FrameDecoder(true, false, 65536, true));
        assertTrue(ch.writeInbound(input));
        Object decoded = ch.readInbound();
        assertNotNull(decoded);
        assertFalse(ch.finish());
        return clazz.cast(decoded);
    }

    private EmbeddedChannel createChannel() {
        return createChannel(null);
    }

    private EmbeddedChannel createChannel(ChannelHandler handler) {
        WebSocketServerProtocolConfig serverConfig = WebSocketServerProtocolConfig.newBuilder()
            .websocketPath("/test")
            .sendCloseFrame(null)
            .build();
        return new EmbeddedChannel(
                new WebSocketServerProtocolHandler(serverConfig),
                new HttpRequestDecoder(),
                new HttpResponseEncoder(),
                new MockOutboundHandler(),
                handler);
    }

    private static void writeUpgradeRequest(EmbeddedChannel ch) {
        ch.writeInbound(WebSocketRequestBuilder.successful());
    }

    private static String getResponseMessage(FullHttpResponse response) {
        return response.content().toString(CharsetUtil.UTF_8);
    }

    private class MockOutboundHandler extends ChannelOutboundHandlerAdapter {

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
            responses.add((FullHttpResponse) msg);
            promise.setSuccess();
        }

        @Override
        public void flush(ChannelHandlerContext ctx) {
        }
    }

    private static class CustomTextFrameHandler extends ChannelInboundHandlerAdapter {
        private String content;

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            assertNull(content);
            content = "processed: " + ((TextWebSocketFrame) msg).text();
            ReferenceCountUtil.release(msg);
        }

        String getContent() {
            return content;
        }
    }
}
