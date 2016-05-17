/*
 * Copyright 2015 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.http.websocketx;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestEncoder;
import io.netty.handler.codec.http.HttpResponseDecoder;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.ThreadLocalRandom;
import org.junit.Test;

import java.net.URI;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public abstract class WebSocketClientHandshakerTest {
    protected abstract WebSocketClientHandshaker newHandshaker(URI uri);

    @Test
    public void testRawPath() {
        URI uri = URI.create("ws://localhost:9999/path%20with%20ws");
        WebSocketClientHandshaker handshaker = newHandshaker(uri);
        FullHttpRequest request = handshaker.newHandshakeRequest();
        try {
            assertEquals("/path%20with%20ws", request.getUri());
        } finally {
            request.release();
        }
    }

    @Test(timeout = 3000)
    public void testHttpResponseAndFrameInSameBuffer() {
        testHttpResponseAndFrameInSameBuffer(false);
    }

    @Test(timeout = 3000)
    public void testHttpResponseAndFrameInSameBufferCodec() {
        testHttpResponseAndFrameInSameBuffer(true);
    }

    private void testHttpResponseAndFrameInSameBuffer(boolean codec) {
        String url = "ws://localhost:9999/ws";
        final WebSocketClientHandshaker shaker = newHandshaker(URI.create(url));
        final WebSocketClientHandshaker handshaker = new WebSocketClientHandshaker(
                shaker.uri(), shaker.version(), null, HttpHeaders.EMPTY_HEADERS, Integer.MAX_VALUE) {
            @Override
            protected FullHttpRequest newHandshakeRequest() {
                return shaker.newHandshakeRequest();
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

        byte[] data = new byte[24];
        ThreadLocalRandom.current().nextBytes(data);

        // Create a EmbeddedChannel which we will use to encode a BinaryWebsocketFrame to bytes and so use these
        // to test the actual handshaker.
        WebSocketServerHandshakerFactory factory = new WebSocketServerHandshakerFactory(url, null, false);
        WebSocketServerHandshaker socketServerHandshaker = factory.newHandshaker(shaker.newHandshakeRequest());
        EmbeddedChannel websocketChannel = new EmbeddedChannel(socketServerHandshaker.newWebSocketEncoder(),
                socketServerHandshaker.newWebsocketDecoder());
        assertTrue(websocketChannel.writeOutbound(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(data))));

        byte[] bytes = "HTTP/1.1 101 Switching Protocols\r\nContent-Length: 0\r\n\r\n".getBytes(CharsetUtil.US_ASCII);

        CompositeByteBuf compositeByteBuf = Unpooled.compositeBuffer();
        compositeByteBuf.addComponent(true, Unpooled.wrappedBuffer(bytes));
        for (;;) {
            ByteBuf frameBytes = (ByteBuf) websocketChannel.readOutbound();
            if (frameBytes == null) {
                break;
            }
            compositeByteBuf.addComponent(true, frameBytes);
        }

        EmbeddedChannel ch = new EmbeddedChannel(new HttpObjectAggregator(Integer.MAX_VALUE),
                new SimpleChannelInboundHandler<FullHttpResponse>() {
                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse msg) throws Exception {
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
            ByteBuf buf = (ByteBuf) ch.readOutbound();
            if (buf == null) {
                break;
            }
            buf.release();
        }
        assertTrue(ch.writeInbound(compositeByteBuf));
        assertTrue(ch.finish());

        BinaryWebSocketFrame frame = (BinaryWebSocketFrame) ch.readInbound();
        ByteBuf expect = Unpooled.wrappedBuffer(data);
        try {
            assertEquals(expect, frame.content());
            assertTrue(frame.isFinalFragment());
            assertEquals(0, frame.rsv());
        } finally {
            expect.release();
            frame.release();
        }
    }
}
