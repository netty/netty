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

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.buffer.api.CompositeBuffer;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.SimpleChannelInboundHandler;
import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.handler.codec.http.DefaultFullHttpResponse;
import io.netty5.handler.codec.http.DefaultHttpContent;
import io.netty5.handler.codec.http.DefaultHttpHeaders;
import io.netty5.handler.codec.http.EmptyHttpHeaders;
import io.netty5.handler.codec.http.FullHttpRequest;
import io.netty5.handler.codec.http.FullHttpResponse;
import io.netty5.handler.codec.http.HttpClientCodec;
import io.netty5.handler.codec.http.HttpHeaderNames;
import io.netty5.handler.codec.http.HttpHeaders;
import io.netty5.handler.codec.http.HttpObjectAggregator;
import io.netty5.handler.codec.http.HttpRequestEncoder;
import io.netty5.handler.codec.http.HttpResponseDecoder;
import io.netty5.handler.codec.http.HttpResponseStatus;
import io.netty5.handler.codec.http.HttpVersion;
import io.netty5.util.CharsetUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import static io.netty5.buffer.api.DefaultBufferAllocators.preferredAllocator;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class WebSocketClientHandshakerTest {
    protected abstract WebSocketClientHandshaker newHandshaker(URI uri, String subprotocol, HttpHeaders headers,
                                                               boolean absoluteUpgradeUrl);

    protected WebSocketClientHandshaker newHandshaker(URI uri) {
        return newHandshaker(uri, null, null, false);
    }

    protected abstract CharSequence getOriginHeaderName();

    protected abstract CharSequence getProtocolHeaderName();

    protected abstract CharSequence[] getHandshakeRequiredHeaderNames();

    @Test
    public void hostHeaderWs() {
        for (String scheme : new String[]{"ws://", "http://"}) {
            for (String host : new String[]{"localhost", "127.0.0.1", "[::1]", "Netty.io"}) {
                String enter = scheme + host;

                testHostHeader(enter, host);
                testHostHeader(enter + '/', host);
                testHostHeader(enter + ":80", host);
                testHostHeader(enter + ":443", host + ":443");
                testHostHeader(enter + ":9999", host + ":9999");
                testHostHeader(enter + "/path", host);
                testHostHeader(enter + ":80/path", host);
                testHostHeader(enter + ":443/path", host + ":443");
                testHostHeader(enter + ":9999/path", host + ":9999");
            }
        }
    }

    @Test
    public void hostHeaderWss() {
        for (String scheme : new String[]{"wss://", "https://"}) {
            for (String host : new String[]{"localhost", "127.0.0.1", "[::1]", "Netty.io"}) {
                String enter = scheme + host;

                testHostHeader(enter, host);
                testHostHeader(enter + '/', host);
                testHostHeader(enter + ":80", host + ":80");
                testHostHeader(enter + ":443", host);
                testHostHeader(enter + ":9999", host + ":9999");
                testHostHeader(enter + "/path", host);
                testHostHeader(enter + ":80/path", host + ":80");
                testHostHeader(enter + ":443/path", host);
                testHostHeader(enter + ":9999/path", host + ":9999");
            }
        }
    }

    @Test
    public void hostHeaderWithoutScheme() {
        testHostHeader("//localhost/", "localhost");
        testHostHeader("//localhost/path", "localhost");
        testHostHeader("//localhost:80/", "localhost:80");
        testHostHeader("//localhost:443/", "localhost:443");
        testHostHeader("//localhost:9999/", "localhost:9999");
    }

    @Test
    public void originHeaderWs() {
        for (String scheme : new String[]{"ws://", "http://"}) {
            for (String host : new String[]{"localhost", "127.0.0.1", "[::1]", "NETTY.IO"}) {
                String enter = scheme + host;
                String expect = "http://" + host.toLowerCase();

                testOriginHeader(enter, expect);
                testOriginHeader(enter + '/', expect);
                testOriginHeader(enter + ":80", expect);
                testOriginHeader(enter + ":443", expect + ":443");
                testOriginHeader(enter + ":9999", expect + ":9999");
                testOriginHeader(enter + "/path%20with%20ws", expect);
                testOriginHeader(enter + ":80/path%20with%20ws", expect);
                testOriginHeader(enter + ":443/path%20with%20ws", expect + ":443");
                testOriginHeader(enter + ":9999/path%20with%20ws", expect + ":9999");
            }
        }
    }

    @Test
    public void originHeaderWss() {
        for (String scheme : new String[]{"wss://", "https://"}) {
            for (String host : new String[]{"localhost", "127.0.0.1", "[::1]", "NETTY.IO"}) {
                String enter = scheme + host;
                String expect = "https://" + host.toLowerCase();

                testOriginHeader(enter, expect);
                testOriginHeader(enter + '/', expect);
                testOriginHeader(enter + ":80", expect + ":80");
                testOriginHeader(enter + ":443", expect);
                testOriginHeader(enter + ":9999", expect + ":9999");
                testOriginHeader(enter + "/path%20with%20ws", expect);
                testOriginHeader(enter + ":80/path%20with%20ws", expect + ":80");
                testOriginHeader(enter + ":443/path%20with%20ws", expect);
                testOriginHeader(enter + ":9999/path%20with%20ws", expect + ":9999");
            }
        }
    }

    @Test
    public void originHeaderWithoutScheme() {
        testOriginHeader("//localhost/", "http://localhost");
        testOriginHeader("//localhost/path", "http://localhost");

        // http scheme by port
        testOriginHeader("//localhost:80/", "http://localhost");
        testOriginHeader("//localhost:80/path", "http://localhost");

        // https scheme by port
        testOriginHeader("//localhost:443/", "https://localhost");
        testOriginHeader("//localhost:443/path", "https://localhost");

        // http scheme for non standard port
        testOriginHeader("//localhost:9999/", "http://localhost:9999");
        testOriginHeader("//localhost:9999/path", "http://localhost:9999");

        // convert host to lower case
        testOriginHeader("//LOCALHOST/", "http://localhost");
    }

    @Test
    public void testSetOriginFromCustomHeaders() {
        HttpHeaders customHeaders = new DefaultHttpHeaders().set(getOriginHeaderName(), "http://example.com");
        WebSocketClientHandshaker handshaker = newHandshaker(URI.create("ws://server.example.com/chat"), null,
                                                             customHeaders, false);
        try (FullHttpRequest request = handshaker.newHandshakeRequest(preferredAllocator())) {
            assertEquals("http://example.com", request.headers().get(getOriginHeaderName()));
        }
    }

    private void testHostHeader(String uri, String expected) {
        testHeaderDefaultHttp(uri, HttpHeaderNames.HOST, expected);
    }

    private void testOriginHeader(String uri, String expected) {
        testHeaderDefaultHttp(uri, getOriginHeaderName(), expected);
    }

    protected void testHeaderDefaultHttp(String uri, CharSequence header, String expectedValue) {
        WebSocketClientHandshaker handshaker = newHandshaker(URI.create(uri));
        try (FullHttpRequest request = handshaker.newHandshakeRequest(preferredAllocator())) {
            assertEquals(expectedValue, request.headers().get(header));
        }
    }

    @Test
    public void testUpgradeUrl() {
        URI uri = URI.create("ws://localhost:9999/path%20with%20ws");
        WebSocketClientHandshaker handshaker = newHandshaker(uri);
        try (FullHttpRequest request = handshaker.newHandshakeRequest(preferredAllocator())) {
            assertEquals("/path%20with%20ws", request.uri());
        }
    }

    @Test
    public void testUpgradeUrlWithQuery() {
        URI uri = URI.create("ws://localhost:9999/path%20with%20ws?a=b%20c");
        WebSocketClientHandshaker handshaker = newHandshaker(uri);
        try (FullHttpRequest request = handshaker.newHandshakeRequest(preferredAllocator())) {
            assertEquals("/path%20with%20ws?a=b%20c", request.uri());
        }
    }

    @Test
    public void testUpgradeUrlWithoutPath() {
        URI uri = URI.create("ws://localhost:9999");
        WebSocketClientHandshaker handshaker = newHandshaker(uri);
        try (FullHttpRequest request = handshaker.newHandshakeRequest(preferredAllocator())) {
            assertEquals("/", request.uri());
        }
    }

    @Test
    public void testUpgradeUrlWithoutPathWithQuery() {
        URI uri = URI.create("ws://localhost:9999?a=b%20c");
        WebSocketClientHandshaker handshaker = newHandshaker(uri);
        try (FullHttpRequest request = handshaker.newHandshakeRequest(preferredAllocator())) {
            assertEquals("/?a=b%20c", request.uri());
        }
    }

    @Test
    public void testAbsoluteUpgradeUrlWithQuery() {
        URI uri = URI.create("ws://localhost:9999/path%20with%20ws?a=b%20c");
        WebSocketClientHandshaker handshaker = newHandshaker(uri, null, null, true);
        try (FullHttpRequest request = handshaker.newHandshakeRequest(preferredAllocator())) {
            assertEquals("ws://localhost:9999/path%20with%20ws?a=b%20c", request.uri());
        }
    }

    @Test
    @Timeout(value = 3000, unit = TimeUnit.MILLISECONDS)
    public void testHttpResponseAndFrameInSameBuffer() {
        testHttpResponseAndFrameInSameBuffer(false);
    }

    @Test
    @Timeout(value = 3000, unit = TimeUnit.MILLISECONDS)
    public void testHttpResponseAndFrameInSameBufferCodec() {
        testHttpResponseAndFrameInSameBuffer(true);
    }

    private void testHttpResponseAndFrameInSameBuffer(boolean codec) {
        String url = "ws://localhost:9999/ws";
        final WebSocketClientHandshaker shaker = newHandshaker(URI.create(url));
        final WebSocketClientHandshaker handshaker = new WebSocketClientHandshaker(
                shaker.uri(), shaker.version(), null, EmptyHttpHeaders.INSTANCE, Integer.MAX_VALUE, -1) {
            @Override
            protected FullHttpRequest newHandshakeRequest(BufferAllocator allocator) {
                return shaker.newHandshakeRequest(allocator);
            }

            @Override
            protected void verify(FullHttpResponse response) {
                // Not do any verification, so we not need to care sending the correct headers etc in the test,
                // which would just make things more complicated.
            }

            @Override
            protected WebSocketFrameDecoder newWebsocketDecoder() {
                return shaker.newWebsocketDecoder();
            }

            @Override
            protected WebSocketFrameEncoder newWebSocketEncoder() {
                return shaker.newWebSocketEncoder();
            }
        };

        // use randomBytes helper from utils to check that it functions properly
        byte[] data = WebSocketUtil.randomBytes(24);

        // Create a EmbeddedChannel which we will use to encode a BinaryWebsocketFrame to bytes and so use these
        // to test the actual handshaker.
        WebSocketServerHandshakerFactory factory = new WebSocketServerHandshakerFactory(url, null, false);
        FullHttpRequest request = shaker.newHandshakeRequest(preferredAllocator());
        WebSocketServerHandshaker socketServerHandshaker = factory.newHandshaker(request);
        request.close();
        EmbeddedChannel websocketChannel = new EmbeddedChannel(socketServerHandshaker.newWebSocketEncoder(),
                socketServerHandshaker.newWebsocketDecoder());
        assertTrue(websocketChannel.writeOutbound(
                new BinaryWebSocketFrame(websocketChannel.bufferAllocator().copyOf(data))));

        byte[] bytes = "HTTP/1.1 101 Switching Protocols\r\nContent-Length: 0\r\n\r\n".getBytes(CharsetUtil.US_ASCII);

        CompositeBuffer compositeBuffer = CompositeBuffer.compose(websocketChannel.bufferAllocator());
        compositeBuffer.extendWith(websocketChannel.bufferAllocator().allocate(bytes.length).writeBytes(bytes).send());
        for (;;) {
            final Buffer buffer = websocketChannel.readOutbound();
            if (buffer == null) {
                break;
            }
            compositeBuffer.extendWith(buffer.send());
        }

        EmbeddedChannel ch = new EmbeddedChannel(new HttpObjectAggregator<DefaultHttpContent>(Integer.MAX_VALUE),
                new SimpleChannelInboundHandler<FullHttpResponse>() {
                    @Override
                    protected void messageReceived(ChannelHandlerContext ctx, FullHttpResponse msg) {
                        handshaker.finishHandshake(ctx.channel(), msg);
                        ctx.pipeline().remove(this);
                    }
                });
        if (codec) {
            ch.pipeline().addFirst(new HttpClientCodec());
        } else {
            ch.pipeline().addFirst(new HttpRequestEncoder(), new HttpResponseDecoder());
        }
        // We need to first write the request as HttpClientCodec will fail if we receive a response before a request
        // was written.
        shaker.handshake(ch).syncUninterruptibly();
        for (;;) {
            // Just consume the bytes, we are not interested in these.
            try (Buffer buf = ch.readOutbound()) {
                if (buf == null) {
                    break;
                }
            }
        }
        assertTrue(ch.writeInbound(compositeBuffer));
        assertTrue(ch.finish());

        try (BinaryWebSocketFrame frame = ch.readInbound();
             Buffer expect = ch.bufferAllocator().allocate(data.length).writeBytes(data)) {
            assertEquals(expect, frame.binaryData());
            assertTrue(frame.isFinalFragment());
            assertEquals(0, frame.rsv());
        }
    }

    @Test
    public void testDuplicateWebsocketHandshakeHeaders() {
        URI uri = URI.create("ws://localhost:9999/foo");

        HttpHeaders inputHeaders = new DefaultHttpHeaders();
        String bogusSubProtocol = "bogusSubProtocol";
        String bogusHeaderValue = "bogusHeaderValue";

        // add values for the headers that are reserved for use in the websockets handshake
        for (CharSequence header : getHandshakeRequiredHeaderNames()) {
            if (!HttpHeaderNames.HOST.equals(header)) {
                inputHeaders.add(header, bogusHeaderValue);
            }
        }
        inputHeaders.add(getProtocolHeaderName(), bogusSubProtocol);

        String realSubProtocol = "realSubProtocol";
        WebSocketClientHandshaker handshaker = newHandshaker(uri, realSubProtocol, inputHeaders, false);
        FullHttpRequest request = handshaker.newHandshakeRequest(preferredAllocator());
        HttpHeaders outputHeaders = request.headers();

        // the header values passed in originally have been replaced with values generated by the Handshaker
        for (CharSequence header : getHandshakeRequiredHeaderNames()) {
            assertEquals(1, outputHeaders.getAll(header).size());
            assertNotEquals(bogusHeaderValue, outputHeaders.get(header));
        }

        // the subprotocol header value is that of the subprotocol string passed into the Handshaker
        assertEquals(1, outputHeaders.getAll(getProtocolHeaderName()).size());
        assertEquals(realSubProtocol, outputHeaders.get(getProtocolHeaderName()));

        request.close();
    }

    @Test
    public void testWebSocketClientHandshakeException() {
        URI uri = URI.create("ws://localhost:9999/exception");
        WebSocketClientHandshaker handshaker = newHandshaker(uri, null, null, false);
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.UNAUTHORIZED, preferredAllocator().allocate(0));

        try (response) {
            response.headers().set(HttpHeaderNames.WWW_AUTHENTICATE, "realm = access token required");
            handshaker.finishHandshake(null, response);
        } catch (WebSocketClientHandshakeException exception) {
            assertEquals("Invalid handshake response getStatus: 401 Unauthorized", exception.getMessage());
            assertEquals(HttpResponseStatus.UNAUTHORIZED, exception.response().status());
            assertTrue(exception.response().headers().contains(HttpHeaderNames.WWW_AUTHENTICATE,
                    "realm = access token required", false));
        }
    }
}

