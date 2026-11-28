/*
 * Copyright 2026 The Netty Project
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
package io.netty.handler.codec.http2;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.CorruptedWebSocketFrameException;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocket13FrameDecoder;
import io.netty.handler.codec.http.websocketx.WebSocketCloseStatus;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtension;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class Http2WebSocketFrameCodecTest {
    private final EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
    private Throwable error;

    @AfterEach
    void release() {
        // Skip the closing handshake when tearing down this byte-level fixture.
        channel.pipeline().firstContext().close();
        channel.finishAndReleaseAll();
    }

    @ParameterizedTest
    @ValueSource(ints = { 0xa1, 0x91, 0xc9, 0xc0 })
    void deflateOnlyReservesRsv1OnTheFirstDataFrame(int opcode) {
        install(true);
        if (opcode == 0xc0) {
            // Start an uncompressed fragmented binary message before the continuation.
            channel.writeInbound(new DefaultHttp2DataFrame(Unpooled.wrappedBuffer(new byte[] { 2, 0 })));
            WebSocketFrame first = channel.readInbound();
            first.release();
        }
        channel.writeInbound(new DefaultHttp2DataFrame(Unpooled.wrappedBuffer(new byte[] { (byte) opcode, 0 })));
        assertThat(error).isInstanceOf(CorruptedWebSocketFrameException.class);
        assertThat(((CorruptedWebSocketFrameException) error).closeStatus())
                .isEqualTo(WebSocketCloseStatus.PROTOCOL_ERROR);
        assertThat((Object) channel.readInbound()).isNull();
    }

    @Test
    void repliesToInFlightPingAfterSendingClose() {
        install(false);
        channel.close();
        channel.releaseOutbound();
        channel.writeInbound(new DefaultHttp2DataFrame(Unpooled.wrappedBuffer(new byte[] { (byte) 0x89, 1, 42 })));
        EmbeddedChannel peer = new EmbeddedChannel(new WebSocket13FrameDecoder(true, false, 256));
        try {
            Http2DataFrame data;
            while ((data = channel.readOutbound()) != null) {
                try {
                    peer.writeInbound(data.content().retain());
                } finally {
                    data.release();
                }
            }
            PongWebSocketFrame pong = peer.readInbound();
            assertThat(pong).isNotNull();
            try {
                assertThat(pong.content().readByte()).isEqualTo((byte) 42);
            } finally {
                pong.release();
            }
        } finally {
            peer.finishAndReleaseAll();
        }
    }

    @Test
    void invalidUtf8AfterDecodingIsNotDelivered() {
        install(false);
        ByteBuf payload = Unpooled.wrappedBuffer(new byte[] { (byte) 0x81, 1, (byte) 0xff });
        channel.writeInbound(new DefaultHttp2DataFrame(payload));
        assertThat(error).isInstanceOf(CorruptedWebSocketFrameException.class);
        assertThat(((CorruptedWebSocketFrameException) error).closeStatus())
                .isEqualTo(WebSocketCloseStatus.INVALID_PAYLOAD_DATA);
        assertThat((Object) channel.readInbound()).isNull();
        assertThat(payload.refCnt()).isZero();
    }

    private void install(boolean compression) {
        ChannelInboundHandlerAdapter app = new ChannelInboundHandlerAdapter() {
            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                error = cause;
            }
        };
        channel.pipeline().addLast(app);
        WebSocketExtension extension = compression ? Http2WebSocketHandshakeHeaders.clientCompression(256)
                .handshakeExtension(new WebSocketExtensionData("permessage-deflate", Collections.emptyMap())) : null;
        Http2WebSocketFrameCodec.install(channel.pipeline().context(app), false, 256, extension, 1000, true);
    }
}
