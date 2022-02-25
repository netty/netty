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
package io.netty5.handler.codec.http.websocketx.extensions.compression;

import io.netty5.buffer.api.Buffer;
import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.handler.codec.compression.ZlibCodecFactory;
import io.netty5.handler.codec.compression.ZlibWrapper;
import io.netty5.handler.codec.http.websocketx.BinaryWebSocketFrame;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static io.netty5.handler.codec.BufferToByteBufHandler.BUFFER_TO_BYTEBUF_HANDLER;
import static io.netty5.handler.codec.ByteBufToBufferHandler.BYTEBUF_TO_BUFFER_HANDLER;
import static io.netty5.handler.codec.http.websocketx.extensions.WebSocketExtension.RSV1;
import static io.netty5.handler.codec.http.websocketx.extensions.WebSocketExtension.RSV3;
import static io.netty5.handler.codec.http.websocketx.extensions.WebSocketExtensionFilter.ALWAYS_SKIP;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PerFrameDeflateDecoderTest {

    private static final Random random = new Random();

    @Test
    public void testCompressedFrame() {
        EmbeddedChannel encoderChannel = new EmbeddedChannel(BYTEBUF_TO_BUFFER_HANDLER,
                ZlibCodecFactory.newZlibEncoder(ZlibWrapper.NONE, 9, 15, 8),
                BUFFER_TO_BYTEBUF_HANDLER);
        EmbeddedChannel decoderChannel = new EmbeddedChannel(new PerFrameDeflateDecoder(false));

        // initialize
        byte[] payload = new byte[300];
        random.nextBytes(payload);

        assertTrue(encoderChannel.writeOutbound(encoderChannel.bufferAllocator().copyOf(payload)));

        BinaryWebSocketFrame compressedFrame;
        try (Buffer compressedPayload = encoderChannel.readOutbound()) {
            Buffer binaryData = compressedPayload.readSplit(compressedPayload.readableBytes() - 4);
            compressedFrame = new BinaryWebSocketFrame(true, RSV1 | RSV3, binaryData);
        }

        // execute
        assertTrue(decoderChannel.writeInbound(compressedFrame));
        BinaryWebSocketFrame uncompressedFrame = decoderChannel.readInbound();

        // test
        assertNotNull(uncompressedFrame);
        assertNotNull(uncompressedFrame.binaryData());
        assertEquals(RSV3, uncompressedFrame.rsv());
        assertEquals(300, uncompressedFrame.binaryData().readableBytes());

        byte[] finalPayload = new byte[300];
        uncompressedFrame.binaryData().readBytes(finalPayload, 0, finalPayload.length);
        assertArrayEquals(finalPayload, payload);
        uncompressedFrame.close();
    }

    @Test
    public void testNormalFrame() {
        EmbeddedChannel decoderChannel = new EmbeddedChannel(new PerFrameDeflateDecoder(false));

        // initialize
        byte[] payload = new byte[300];
        random.nextBytes(payload);

        BinaryWebSocketFrame frame = new BinaryWebSocketFrame(true,
                RSV3, decoderChannel.bufferAllocator().copyOf(payload));

        // execute
        assertTrue(decoderChannel.writeInbound(frame));
        BinaryWebSocketFrame newFrame = decoderChannel.readInbound();

        // test
        assertNotNull(newFrame);
        assertNotNull(newFrame.binaryData());
        assertEquals(RSV3, newFrame.rsv());
        assertEquals(300, newFrame.binaryData().readableBytes());

        byte[] finalPayload = new byte[300];
        newFrame.binaryData().readBytes(finalPayload, 0, finalPayload.length);
        assertArrayEquals(finalPayload, payload);
        newFrame.close();
    }

    // See https://github.com/netty/netty/issues/4348
    @Test
    public void testCompressedEmptyFrame() {
        EmbeddedChannel encoderChannel = new EmbeddedChannel(
                ZlibCodecFactory.newZlibEncoder(ZlibWrapper.NONE, 9, 15, 8));
        EmbeddedChannel decoderChannel = new EmbeddedChannel(new PerFrameDeflateDecoder(false));

        assertTrue(encoderChannel.writeOutbound(encoderChannel.bufferAllocator().allocate(0)));
        Buffer compressedPayload = encoderChannel.readOutbound();
        BinaryWebSocketFrame compressedFrame =
                new BinaryWebSocketFrame(true, RSV1 | RSV3, compressedPayload);

        // execute
        assertTrue(decoderChannel.writeInbound(compressedFrame));
        BinaryWebSocketFrame uncompressedFrame = decoderChannel.readInbound();

        // test
        assertNotNull(uncompressedFrame);
        assertNotNull(uncompressedFrame.binaryData());
        assertEquals(RSV3, uncompressedFrame.rsv());
        assertEquals(0, uncompressedFrame.binaryData().readableBytes());
        uncompressedFrame.close();
    }

    @Test
    public void testDecompressionSkip() {
        EmbeddedChannel encoderChannel = new EmbeddedChannel(
                ZlibCodecFactory.newZlibEncoder(ZlibWrapper.NONE, 9, 15, 8));
        EmbeddedChannel decoderChannel = new EmbeddedChannel(new PerFrameDeflateDecoder(false, ALWAYS_SKIP));

        byte[] payload = new byte[300];
        random.nextBytes(payload);

        assertTrue(encoderChannel.writeOutbound(encoderChannel.bufferAllocator().copyOf(payload)));
        Buffer compressedPayload = encoderChannel.readOutbound();

        BinaryWebSocketFrame compressedBinaryFrame = new BinaryWebSocketFrame(true, RSV1 | RSV3, compressedPayload);

        assertTrue(decoderChannel.writeInbound(compressedBinaryFrame));

        BinaryWebSocketFrame inboundBinaryFrame = decoderChannel.readInbound();

        assertNotNull(inboundBinaryFrame);
        assertNotNull(inboundBinaryFrame.binaryData());
        assertEquals(compressedPayload, inboundBinaryFrame.binaryData());
        assertEquals(5, inboundBinaryFrame.rsv());

        assertTrue(inboundBinaryFrame.isAccessible());
        inboundBinaryFrame.close();

        assertTrue(encoderChannel.finishAndReleaseAll());
        assertFalse(decoderChannel.finish());
    }
}
