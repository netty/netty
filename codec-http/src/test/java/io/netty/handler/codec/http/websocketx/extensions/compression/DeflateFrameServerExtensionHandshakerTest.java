/*
 * Copyright 2014 The Netty Project
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

import static io.netty.handler.codec.http.websocketx.extensions.compression.
        DeflateFrameServerExtensionHandshaker.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketServerExtension;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionData;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class DeflateFrameServerExtensionHandshakerTest {

    @Test
    public void testNormalHandshake() {
        // initialize
        DeflateFrameServerExtensionHandshaker handshaker =
                new DeflateFrameServerExtensionHandshaker(
                        DeflateFrameServerExtensionHandshaker.DEFAULT_COMPRESSION_LEVEL, 0);

        // execute
        WebSocketServerExtension extension = handshaker.handshakeExtension(
                new WebSocketExtensionData(DEFLATE_FRAME_EXTENSION, Collections.<String, String>emptyMap()));

        // test
        assertNotNull(extension);
        assertEquals(WebSocketServerExtension.RSV1, extension.rsv());
        assertInstanceOf(PerFrameDeflateDecoder.class, extension.newExtensionDecoder());
        assertInstanceOf(PerFrameDeflateEncoder.class, extension.newExtensionEncoder());
    }

    @Test
    public void testWebkitHandshake() {
        // initialize
        DeflateFrameServerExtensionHandshaker handshaker =
                new DeflateFrameServerExtensionHandshaker(
                        DeflateFrameServerExtensionHandshaker.DEFAULT_COMPRESSION_LEVEL, 0);

        // execute
        WebSocketServerExtension extension = handshaker.handshakeExtension(
                new WebSocketExtensionData(X_WEBKIT_DEFLATE_FRAME_EXTENSION, Collections.<String, String>emptyMap()));

        // test
        assertNotNull(extension);
        assertEquals(WebSocketServerExtension.RSV1, extension.rsv());
        assertInstanceOf(PerFrameDeflateDecoder.class, extension.newExtensionDecoder());
        assertInstanceOf(PerFrameDeflateEncoder.class, extension.newExtensionEncoder());
    }

    @Test
    public void testFailedHandshake() {
        // initialize
        DeflateFrameServerExtensionHandshaker handshaker =
                new DeflateFrameServerExtensionHandshaker(
                        DeflateFrameServerExtensionHandshaker.DEFAULT_COMPRESSION_LEVEL, 0);

        Map<String, String> parameters;
        parameters = new HashMap<String, String>();
        parameters.put("unknown", "11");

        // execute
        WebSocketServerExtension extension = handshaker.handshakeExtension(
                new WebSocketExtensionData(DEFLATE_FRAME_EXTENSION, parameters));

        // test
        assertNull(extension);
    }

    @Test
    public void testCustomWindowAndMemLevel() {
        DeflateFrameServerExtensionHandshaker handshaker =
                new DeflateFrameServerExtensionHandshaker(
                        DeflateFrameServerExtensionHandshaker.DEFAULT_COMPRESSION_LEVEL, 10, 4, 0);

        WebSocketServerExtension extension = handshaker.handshakeExtension(
                new WebSocketExtensionData(DEFLATE_FRAME_EXTENSION, Collections.<String, String>emptyMap()));

        assertNotNull(extension);
        assertEquals(WebSocketServerExtension.RSV1, extension.rsv());
        assertInstanceOf(PerFrameDeflateEncoder.class, extension.newExtensionEncoder());
    }

    @Test
    public void testWindowSizeOutOfRangeRejected() {
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                new DeflateFrameServerExtensionHandshaker(
                        DeflateFrameServerExtensionHandshaker.DEFAULT_COMPRESSION_LEVEL, 16, 8, 0);
            }
        });
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                new DeflateFrameServerExtensionHandshaker(
                        DeflateFrameServerExtensionHandshaker.DEFAULT_COMPRESSION_LEVEL, 7, 8, 0);
            }
        });
    }

    @Test
    public void testMemLevelOutOfRangeRejected() {
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                new DeflateFrameServerExtensionHandshaker(
                        DeflateFrameServerExtensionHandshaker.DEFAULT_COMPRESSION_LEVEL, 15, 10, 0);
            }
        });
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                new DeflateFrameServerExtensionHandshaker(
                        DeflateFrameServerExtensionHandshaker.DEFAULT_COMPRESSION_LEVEL, 15, 0, 0);
            }
        });
    }

    @Test
    public void testEightBitWindowRejected() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new DeflateFrameServerExtensionHandshaker(DEFAULT_COMPRESSION_LEVEL, 8, DEFAULT_MEM_LEVEL, 0));
    }

    @ParameterizedTest
    @CsvSource({ "deflate-frame, 9", "deflate-frame, 15",
                 "x-webkit-deflate-frame, 9", "x-webkit-deflate-frame, 15" })
    public void testConfiguredWindowCanCompress(String extensionName, int windowSize) {
        DeflateFrameServerExtensionHandshaker handshaker = new DeflateFrameServerExtensionHandshaker(
                DEFAULT_COMPRESSION_LEVEL, windowSize, DEFAULT_MEM_LEVEL, 0);
        WebSocketServerExtension extension = handshaker.handshakeExtension(
                new WebSocketExtensionData(extensionName, Collections.<String, String>emptyMap()));
        assertThat(extension).isNotNull();
        EmbeddedChannel encoder = new EmbeddedChannel(extension.newExtensionEncoder());
        EmbeddedChannel decoder = new EmbeddedChannel(extension.newExtensionDecoder());
        try {
            assertThat(encoder.writeOutbound(new TextWebSocketFrame("hello websocket"))).isTrue();
            WebSocketFrame compressed = encoder.readOutbound();
            assertThat(decoder.writeInbound(compressed)).isTrue();
            TextWebSocketFrame decoded = decoder.readInbound();
            try {
                assertThat(decoded.text()).isEqualTo("hello websocket");
            } finally {
                decoded.release();
            }
        } finally {
            encoder.finishAndReleaseAll();
            decoder.finishAndReleaseAll();
        }
    }

}
