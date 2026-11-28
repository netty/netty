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

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocket13FrameDecoder;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class Http2WebSocketApplicationControlTest {
    private final EmbeddedChannel app = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
    private EmbeddedChannel peerDecoder = new EmbeddedChannel(new WebSocket13FrameDecoder(true, false, 256));

    @AfterEach
    void release() {
        if (app.isOpen()) {
            app.pipeline().firstContext().close();
        }
        app.finishAndReleaseAll();
        peerDecoder.finishAndReleaseAll();
    }

    @Test
    void appReceivesPingAndOwnsThePong() {
        install();
        app.writeInbound(new DefaultHttp2DataFrame(Unpooled.wrappedBuffer(new byte[] { (byte) 0x89, 1, 42 })));
        PingWebSocketFrame ping = app.readInbound();
        assertThat(ping).isNotNull();
        assertThat((Object) app.readOutbound()).isNull();
        pong(ping);
        assertThat((Object) app.readOutbound()).isNull();
    }

    @Test
    void canPongForAnInFlightPingAfterAClientInitiatedClose() {
        install();
        app.writeOutbound(new CloseWebSocketFrame(1000, ""));
        expectClose(1000);
        // The peer decoder stops reading after Close. Decode the in-flight Pong as a separate wire observation.
        peerDecoder.finishAndReleaseAll();
        peerDecoder = new EmbeddedChannel(new WebSocket13FrameDecoder(true, false, 256));
        app.writeInbound(new DefaultHttp2DataFrame(Unpooled.wrappedBuffer(new byte[] { (byte) 0x89, 1, 42 })));
        pong(app.readInbound());

        receiveClose(true);
        CloseWebSocketFrame close = app.readInbound();
        assertThat(close.statusCode()).isEqualTo(1000);
        close.release();
        expectEndStream();
    }

    @Test
    void waitsForAppReplyEvenWhenPeerCloseCarriesEndStream() {
        install();
        receiveClose(true);
        CloseWebSocketFrame close = app.readInbound();
        assertThat(close.statusCode()).isEqualTo(1000);
        close.release();
        assertThat(app.isActive()).isTrue();
        assertThat((Object) app.readOutbound()).isNull();

        ChannelFuture write = app.writeOneOutbound(new CloseWebSocketFrame(1001, "application chose this"));
        assertThat(write.isDone()).isFalse();
        assertThat((Object) app.readOutbound()).isNull();
        app.flushOutbound();
        assertThat(write.isSuccess()).isTrue();
        expectClose(1001);
        expectEndStream();
    }

    @Test
    void timesOutWithoutSynthesizingAReplyIfTheAppDoesNotReply() {
        install();
        app.freezeTime();
        receiveClose(true);
        app.<CloseWebSocketFrame>readInbound().release();
        assertThat(app.isActive()).isTrue();
        app.advanceTimeBy(1, TimeUnit.SECONDS);
        app.runScheduledPendingTasks();
        assertThat(app.isActive()).isFalse();
        assertThat((Object) app.readOutbound()).isNull();
    }

    @Test
    void bareChannelCloseAbortsWithoutSynthesizingAFrame() {
        install();
        assertThat(app.close().isSuccess()).isTrue();
        assertThat(app.isOpen()).isFalse();
        assertThat((Object) app.readOutbound()).isNull();
    }

    private void install() {
        Http2WebSocketFrameCodec.install(app.pipeline().firstContext(), false, 256, null, 1000, false);
    }

    private void receiveClose(boolean endStream) {
        app.writeInbound(new DefaultHttp2DataFrame(Unpooled.wrappedBuffer(
                new byte[] { (byte) 0x88, 2, 3, (byte) 0xe8 }), endStream));
    }

    private void pong(PingWebSocketFrame ping) {
        try {
            assertThat(ping.content().getUnsignedByte(0)).isEqualTo((short) 42);
            assertThat(app.writeOutbound(new PongWebSocketFrame(ping.content().retain()))).isTrue();
            PongWebSocketFrame pong = nextWireFrame();
            try {
                assertThat(pong.content().getUnsignedByte(0)).isEqualTo((short) 42);
            } finally {
                pong.release();
            }
        } finally {
            ping.release();
        }
    }

    private void expectClose(int code) {
        CloseWebSocketFrame close = nextWireFrame();
        try {
            assertThat(close.statusCode()).isEqualTo(code);
        } finally {
            close.release();
        }
    }

    private void expectEndStream() {
        Http2DataFrame data = app.readOutbound();
        try {
            assertThat(data.isEndStream()).isTrue();
        } finally {
            data.release();
        }
        assertThat((Object) app.readOutbound()).isNull();
    }

    private <T extends WebSocketFrame> T nextWireFrame() {
        T decoded;
        do {
            Http2DataFrame data = app.readOutbound();
            assertThat(data).isNotNull();
            try {
                assertThat(data.isEndStream()).isFalse();
                peerDecoder.writeInbound(data.content().retain());
            } finally {
                data.release();
            }
            decoded = peerDecoder.readInbound();
        } while (decoded == null);
        return decoded;
    }
}
