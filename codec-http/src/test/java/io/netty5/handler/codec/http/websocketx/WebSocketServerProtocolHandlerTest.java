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
package io.netty5.handler.codec.http.websocketx;

import io.netty5.buffer.api.Buffer;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.handler.codec.http.DefaultHttpContent;
import io.netty5.handler.codec.http.DefaultHttpRequest;
import io.netty5.handler.codec.http.EmptyLastHttpContent;
import io.netty5.handler.codec.http.FullHttpRequest;
import io.netty5.handler.codec.http.FullHttpResponse;
import io.netty5.handler.codec.http.HttpClientCodec;
import io.netty5.handler.codec.http.HttpHeaderNames;
import io.netty5.handler.codec.http.HttpHeaderValues;
import io.netty5.handler.codec.http.HttpMethod;
import io.netty5.handler.codec.http.HttpObjectAggregator;
import io.netty5.handler.codec.http.HttpRequest;
import io.netty5.handler.codec.http.HttpRequestDecoder;
import io.netty5.handler.codec.http.HttpResponseEncoder;
import io.netty5.handler.codec.http.HttpServerCodec;
import io.netty5.util.CharsetUtil;
import io.netty5.util.ReferenceCountUtil;
import io.netty5.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Queue;

import static io.netty5.buffer.api.DefaultBufferAllocators.preferredAllocator;
import static io.netty5.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty5.handler.codec.http.HttpResponseStatus.SWITCHING_PROTOCOLS;
import static io.netty5.handler.codec.http.HttpVersion.HTTP_1_1;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WebSocketServerProtocolHandlerTest {

    private final Queue<FullHttpResponse> responses = new ArrayDeque<>();

    @BeforeEach
    public void setUp() {
        responses.clear();
    }

    @Test
    public void testHttpUpgradeRequestFull() {
        testHttpUpgradeRequest0(true);
    }

    @Test
    public void testHttpUpgradeRequestNonFull() {
        testHttpUpgradeRequest0(false);
    }

    private void testHttpUpgradeRequest0(boolean full) {
        EmbeddedChannel ch = createChannel(new MockOutboundHandler());
        ChannelHandlerContext handshakerCtx = ch.pipeline().context(WebSocketServerProtocolHandshakeHandler.class);
        writeUpgradeRequest(ch, full);

        FullHttpResponse response = responses.remove();
        assertEquals(SWITCHING_PROTOCOLS, response.status());
        response.close();
        assertNotNull(WebSocketServerProtocolHandler.getHandshaker(handshakerCtx.channel()));
        assertFalse(ch.finish());
    }

    @Test
    public void testWebSocketServerProtocolHandshakeHandlerRemovedAfterHandshake() {
        EmbeddedChannel ch = createChannel(new MockOutboundHandler());
        ChannelHandlerContext handshakerCtx = ch.pipeline().context(WebSocketServerProtocolHandshakeHandler.class);
        ch.pipeline().addLast(new ChannelHandler() {
            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
                    // We should have removed the handler already.
                    ctx.executor().execute(() -> ctx.pipeline().context(WebSocketServerProtocolHandshakeHandler.class));
                }
            }
        });
        writeUpgradeRequest(ch);

        FullHttpResponse response = responses.remove();
        assertEquals(SWITCHING_PROTOCOLS, response.status());
        response.close();
        assertNotNull(WebSocketServerProtocolHandler.getHandshaker(handshakerCtx.channel()));
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
        response.close();
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
        response.close();

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
        response.close();

        assertNull(ch.pipeline().get(Utf8FrameValidator.class));
    }

    @Test
    public void testHandleTextFrame() {
        CustomTextFrameHandler customTextFrameHandler = new CustomTextFrameHandler();
        EmbeddedChannel ch = createChannel(customTextFrameHandler);
        writeUpgradeRequest(ch);

        FullHttpResponse response = responses.remove();
        assertEquals(SWITCHING_PROTOCOLS, response.status());
        response.close();

        if (ch.pipeline().context(HttpRequestDecoder.class) != null) {
            // Removing the HttpRequestDecoder because we are writing a TextWebSocketFrame and thus
            // decoding is not necessary.
            ch.pipeline().remove(HttpRequestDecoder.class);
        }

        ch.writeInbound(new TextWebSocketFrame(ch.bufferAllocator(), "payload"));

        assertEquals("processed: payload", customTextFrameHandler.getContent());
        assertFalse(ch.finish());
    }

    @Test
    public void testCheckWebSocketPathStartWithSlash() {
        WebSocketRequestBuilder builder = new WebSocketRequestBuilder().httpVersion(HTTP_1_1)
                .method(HttpMethod.GET)
                .key(HttpHeaderNames.SEC_WEBSOCKET_KEY)
                .connection("Upgrade")
                .upgrade(HttpHeaderValues.WEBSOCKET)
                .version13();

        WebSocketServerProtocolConfig config = WebSocketServerProtocolConfig.newBuilder()
                .websocketPath("/")
                .checkStartsWith(true)
                .build();

        FullHttpResponse response;

        createChannel(config, null).writeInbound(builder.uri("/test").build());
        response = responses.remove();
        assertEquals(SWITCHING_PROTOCOLS, response.status());
        response.close();

        createChannel(config, null).writeInbound(builder.uri("/?q=v").build());
        response = responses.remove();
        assertEquals(SWITCHING_PROTOCOLS, response.status());
        response.close();

        createChannel(config, null).writeInbound(builder.uri("/").build());
        response = responses.remove();
        assertEquals(SWITCHING_PROTOCOLS, response.status());
        response.close();
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
        response.close();
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
    public void testExplicitCloseFrameSentWhenServerChannelClosed() {
        WebSocketCloseStatus closeStatus = WebSocketCloseStatus.ENDPOINT_UNAVAILABLE;
        EmbeddedChannel client = createClient();
        EmbeddedChannel server = createServer();

        assertFalse(server.writeInbound((Buffer) client.readOutbound()));
        assertFalse(client.writeInbound((Buffer) server.readOutbound()));

        // When server channel closed with explicit close-frame
        assertTrue(server.writeOutbound(new CloseWebSocketFrame(server.bufferAllocator(), closeStatus)));
        server.close();

        // Then client receives provided close-frame
        assertTrue(client.writeInbound((Buffer) server.readOutbound()));
        assertFalse(server.isOpen());

        CloseWebSocketFrame closeMessage = client.readInbound();
        assertEquals(closeStatus.code(), closeMessage.statusCode());
        closeMessage.close();

        client.close();
        assertTrue(ReferenceCountUtil.release(client.readOutbound()));
        assertFalse(client.finishAndReleaseAll());
        assertFalse(server.finishAndReleaseAll());
    }

    @Test
    public void testCloseFrameSentWhenServerChannelClosedSilently() {
        EmbeddedChannel client = createClient();
        EmbeddedChannel server = createServer();

        assertFalse(server.writeInbound((Buffer) client.readOutbound()));
        assertFalse(client.writeInbound((Buffer) server.readOutbound()));

        // When server channel closed without explicit close-frame
        server.close();

        // Then client receives NORMAL_CLOSURE close-frame
        assertTrue(client.writeInbound((Buffer) server.readOutbound()));
        assertFalse(server.isOpen());

        CloseWebSocketFrame closeMessage = client.readInbound();
        assertEquals(closeMessage.statusCode(), WebSocketCloseStatus.NORMAL_CLOSURE.code());
        closeMessage.close();

        client.close();
        assertTrue(ReferenceCountUtil.release(client.readOutbound()));
        assertFalse(client.finishAndReleaseAll());
        assertFalse(server.finishAndReleaseAll());
    }

    @Test
    public void testExplicitCloseFrameSentWhenClientChannelClosed() {
        WebSocketCloseStatus closeStatus = WebSocketCloseStatus.INVALID_PAYLOAD_DATA;
        EmbeddedChannel client = createClient();
        EmbeddedChannel server = createServer();

        assertFalse(server.writeInbound((Buffer) client.readOutbound()));
        assertFalse(client.writeInbound((Buffer) server.readOutbound()));

        // When client channel closed with explicit close-frame
        assertTrue(client.writeOutbound(new CloseWebSocketFrame(client.bufferAllocator(), closeStatus)));
        client.close();

        // Then client receives provided close-frame
        assertFalse(server.writeInbound((Buffer) client.readOutbound()));
        assertFalse(client.isOpen());
        assertFalse(server.isOpen());

        CloseWebSocketFrame closeMessage = decode(server.readOutbound(), CloseWebSocketFrame.class);
        assertEquals(closeMessage.statusCode(), closeStatus.code());
        closeMessage.close();

        assertFalse(client.finishAndReleaseAll());
        assertFalse(server.finishAndReleaseAll());
    }

    @Test
    public void testCloseFrameSentWhenClientChannelClosedSilently() {
        EmbeddedChannel client = createClient();
        EmbeddedChannel server = createServer();

        assertFalse(server.writeInbound((Buffer) client.readOutbound()));
        assertFalse(client.writeInbound((Buffer) server.readOutbound()));

        // When client channel closed without explicit close-frame
        client.close();

        // Then server receives NORMAL_CLOSURE close-frame
        assertFalse(server.writeInbound((Buffer) client.readOutbound()));
        assertFalse(client.isOpen());
        assertFalse(server.isOpen());

        CloseWebSocketFrame closeMessage = decode(server.readOutbound(), CloseWebSocketFrame.class);
        assertEquals(closeMessage, new CloseWebSocketFrame(client.bufferAllocator(),
                WebSocketCloseStatus.NORMAL_CLOSURE));
        closeMessage.close();

        assertFalse(client.finishAndReleaseAll());
        assertFalse(server.finishAndReleaseAll());
    }

    private static EmbeddedChannel createClient(ChannelHandler... handlers) {
        WebSocketClientProtocolConfig clientConfig = WebSocketClientProtocolConfig.newBuilder()
            .webSocketUri("http://test/test")
            .dropPongFrames(false)
            .handleCloseFrames(false)
            .build();
        EmbeddedChannel ch = new EmbeddedChannel(false, false,
            new HttpClientCodec(),
            new HttpObjectAggregator<DefaultHttpContent>(8192),
            new WebSocketClientProtocolHandler(clientConfig)
        );
        ch.pipeline().addLast(handlers);
        ch.register();
        return ch;
    }

    private static EmbeddedChannel createServer(ChannelHandler... handlers) {
        WebSocketServerProtocolConfig serverConfig = WebSocketServerProtocolConfig.newBuilder()
            .websocketPath("/test")
            .dropPongFrames(false)
            .build();
        EmbeddedChannel ch = new EmbeddedChannel(false, false,
            new HttpServerCodec(),
            new HttpObjectAggregator<DefaultHttpContent>(8192),
            new WebSocketServerProtocolHandler(serverConfig)
        );
        ch.pipeline().addLast(handlers);
        ch.register();
        return ch;
    }

    @SuppressWarnings("SameParameterValue")
    private static <T> T decode(Buffer input, Class<T> clazz) {
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
        return createChannel(serverConfig, handler);
    }

    private EmbeddedChannel createChannel(WebSocketServerProtocolConfig serverConfig, ChannelHandler handler) {
        return new EmbeddedChannel(
                new WebSocketServerProtocolHandler(serverConfig),
                new HttpRequestDecoder(),
                new HttpResponseEncoder(),
                new MockOutboundHandler(),
                handler);
    }

    private static void writeUpgradeRequest(EmbeddedChannel ch) {
        writeUpgradeRequest(ch, true);
    }

    private static void writeUpgradeRequest(EmbeddedChannel ch, boolean full) {
        HttpRequest request = WebSocketRequestBuilder.successful();
        if (full) {
            ch.writeInbound(request);
        } else {
            if (request instanceof FullHttpRequest) {
                FullHttpRequest fullHttpRequest = (FullHttpRequest) request;
                try (fullHttpRequest) {
                    HttpRequest req = new DefaultHttpRequest(fullHttpRequest.protocolVersion(),
                            fullHttpRequest.method(), fullHttpRequest.uri(), fullHttpRequest.headers().copy());
                    ch.writeInbound(req);
                    ch.writeInbound(new DefaultHttpContent(fullHttpRequest.payload().copy()));
                    ch.writeInbound(new EmptyLastHttpContent(preferredAllocator()));
                }
            } else {
                ch.writeInbound(request);
            }
        }
    }

    private static String getResponseMessage(FullHttpResponse response) {
        return response.payload().toString(CharsetUtil.UTF_8);
    }

    private class MockOutboundHandler implements ChannelHandler {

        @Override
        public Future<Void> write(ChannelHandlerContext ctx, Object msg) {
            responses.add((FullHttpResponse) msg);
            return ctx.newSucceededFuture();
        }

        @Override
        public void flush(ChannelHandlerContext ctx) {
        }
    }

    private static class CustomTextFrameHandler implements ChannelHandler {
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
