/*
 * Copyright 2012 The Netty Project
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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.ReferenceCounted;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;

import java.util.Iterator;

import static io.netty.handler.codec.http.HttpVersion.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

public class WebSocketServerHandshaker13Test extends WebSocketServerHandshakerTest {

    @Override
    protected WebSocketServerHandshaker newHandshaker(String webSocketURL, String subprotocols,
            WebSocketDecoderConfig decoderConfig) {
        return new WebSocketServerHandshaker13(webSocketURL, subprotocols, decoderConfig);
    }

    @Override
    protected WebSocketVersion webSocketVersion() {
        return WebSocketVersion.V13;
    }

    @Test
    public void testPerformOpeningHandshake() {
        testPerformOpeningHandshake0(true);
    }

    @Test
    public void testPerformOpeningHandshakeSubProtocolNotSupported() {
        testPerformOpeningHandshake0(false);
    }

    private static void testPerformOpeningHandshake0(boolean subProtocol) {
        EmbeddedChannel ch = new EmbeddedChannel(
                new HttpObjectAggregator(42), new HttpResponseEncoder(), new HttpRequestDecoder());

        if (subProtocol) {
            testUpgrade0(ch, new WebSocketServerHandshaker13(
                    "ws://example.com/chat", "chat", false, Integer.MAX_VALUE, false));
        } else {
            testUpgrade0(ch, new WebSocketServerHandshaker13(
                    "ws://example.com/chat", null, false, Integer.MAX_VALUE, false));
        }
        assertFalse(ch.finish());
    }

    @Test
    public void testCloseReasonWithEncoderAndDecoder() {
        testCloseReason0(new HttpResponseEncoder(), new HttpRequestDecoder());
    }

    @Test
    public void testCloseReasonWithCodec() {
        testCloseReason0(new HttpServerCodec());
    }

    private static void testCloseReason0(ChannelHandler... handlers) {
        EmbeddedChannel ch = new EmbeddedChannel(
                new HttpObjectAggregator(42));
        ch.pipeline().addLast(handlers);
        testUpgrade0(ch, new WebSocketServerHandshaker13("ws://example.com/chat", "chat",
                WebSocketDecoderConfig.newBuilder().maxFramePayloadLength(4).closeOnProtocolViolation(true).build()));

        ch.writeOutbound(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(new byte[8])));
        ByteBuf buffer = ch.readOutbound();
        try {
            ch.writeInbound(buffer);
            fail();
        } catch (CorruptedWebSocketFrameException expected) {
            // expected
        }
        ReferenceCounted closeMessage = ch.readOutbound();
        assertThat(closeMessage, CoreMatchers.instanceOf(ByteBuf.class));
        closeMessage.release();
        assertFalse(ch.finish());
    }

    private static void testUpgrade0(EmbeddedChannel ch, WebSocketServerHandshaker13 handshaker) {
        FullHttpRequest req = new DefaultFullHttpRequest(HTTP_1_1, HttpMethod.GET, "/chat");
        req.headers().set(HttpHeaderNames.HOST, "server.example.com");
        req.headers().set(HttpHeaderNames.UPGRADE, HttpHeaderValues.WEBSOCKET);
        req.headers().set(HttpHeaderNames.CONNECTION, "Upgrade");
        req.headers().set(HttpHeaderNames.SEC_WEBSOCKET_KEY, "dGhlIHNhbXBsZSBub25jZQ==");
        req.headers().set(HttpHeaderNames.SEC_WEBSOCKET_ORIGIN, "http://example.com");
        req.headers().set(HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL, "chat, superchat");
        req.headers().set(HttpHeaderNames.SEC_WEBSOCKET_VERSION, "13");

        handshaker.handshake(ch, req);

        ByteBuf resBuf = ch.readOutbound();

        EmbeddedChannel ch2 = new EmbeddedChannel(new HttpResponseDecoder());
        ch2.writeInbound(resBuf);
        HttpResponse res = ch2.readInbound();

        assertEquals(
                "s3pPLMBiTxaQ9kYGzzhZRbK+xOo=", res.headers().get(HttpHeaderNames.SEC_WEBSOCKET_ACCEPT));
        Iterator<String> subProtocols = handshaker.subprotocols().iterator();
        if (subProtocols.hasNext()) {
            assertEquals(subProtocols.next(),
                    res.headers().get(HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL));
        } else {
            assertNull(res.headers().get(HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL));
        }
        ReferenceCountUtil.release(res);
        req.release();
    }
}
