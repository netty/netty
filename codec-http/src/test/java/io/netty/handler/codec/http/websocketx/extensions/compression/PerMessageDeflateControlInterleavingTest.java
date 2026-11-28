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
package io.netty.handler.codec.http.websocketx.extensions.compression;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionFilter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PerMessageDeflateControlInterleavingTest {
    private static final WebSocketExtensionFilter SKIP_CONTROL_FRAMES = frame ->
            frame instanceof PingWebSocketFrame || frame instanceof PongWebSocketFrame;

    @Test
    void sendingPingDuringACompressedMessageDoesNotChangeTheMessageState() {
        checkFrames(SKIP_CONTROL_FRAMES, WebSocketExtensionFilter.NEVER_SKIP);
    }

    @Test
    void receivingPingDuringACompressedMessageDoesNotChangeTheMessageState() {
        checkFrames(WebSocketExtensionFilter.NEVER_SKIP, SKIP_CONTROL_FRAMES);
    }

    @Test
    void defaultCodecPassesControlFramesThroughDuringACompressedMessage() {
        checkFrames(WebSocketExtensionFilter.NEVER_SKIP, WebSocketExtensionFilter.NEVER_SKIP);
    }

    private static void checkFrames(WebSocketExtensionFilter encoderFilter, WebSocketExtensionFilter decoderFilter) {
        EmbeddedChannel encoder = new EmbeddedChannel(new PerMessageDeflateEncoder(6, 15, false, encoderFilter));
        EmbeddedChannel decoder = new EmbeddedChannel(new PerMessageDeflateDecoder(false, decoderFilter, 1024));
        try {
            pass(encoder, decoder, new TextWebSocketFrame(false, 0, "part 1"));
            pass(encoder, decoder, new PingWebSocketFrame(Unpooled.wrappedBuffer(new byte[] { 42 })));
            pass(encoder, decoder, new PongWebSocketFrame(Unpooled.wrappedBuffer(new byte[] { 37 })));
            pass(encoder, decoder, new ContinuationWebSocketFrame(true, 0, "part 2"));
        } finally {
            encoder.finishAndReleaseAll();
            decoder.finishAndReleaseAll();
        }
    }

    private static void pass(EmbeddedChannel encoder, EmbeddedChannel decoder, WebSocketFrame frame) {
        WebSocketFrame expected = frame.copy();
        try {
            assertThat(encoder.writeOutbound(frame)).isTrue();
            assertThat(decoder.writeInbound(encoder.<WebSocketFrame>readOutbound())).isTrue();
            WebSocketFrame actual = decoder.readInbound();
            try {
                assertThat(actual).isExactlyInstanceOf(expected.getClass());
                assertThat(actual.isFinalFragment()).isEqualTo(expected.isFinalFragment());
                assertThat(actual.rsv()).isZero();
                assertThat(actual.content()).isEqualTo(expected.content());
            } finally {
                actual.release();
            }
        } finally {
            expected.release();
        }
    }
}
