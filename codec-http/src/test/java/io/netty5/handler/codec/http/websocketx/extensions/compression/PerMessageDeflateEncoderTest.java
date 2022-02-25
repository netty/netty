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
import io.netty5.handler.codec.EncoderException;
import io.netty5.handler.codec.compression.ZlibCodecFactory;
import io.netty5.handler.codec.compression.ZlibWrapper;
import io.netty5.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty5.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty5.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty5.handler.codec.http.websocketx.WebSocketFrame;
import io.netty5.handler.codec.http.websocketx.extensions.WebSocketExtension;
import io.netty5.handler.codec.http.websocketx.extensions.WebSocketExtensionFilter;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static io.netty5.handler.codec.ByteBufToBufferHandler.BYTEBUF_TO_BUFFER_HANDLER;
import static io.netty5.handler.codec.http.websocketx.extensions.WebSocketExtensionFilter.ALWAYS_SKIP;
import static io.netty5.handler.codec.http.websocketx.extensions.WebSocketExtensionFilter.NEVER_SKIP;
import static io.netty5.handler.codec.http.websocketx.extensions.compression.DeflateEncoder.EMPTY_DEFLATE_BLOCK;
import static io.netty5.util.CharsetUtil.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PerMessageDeflateEncoderTest {

    private static final Random random = new Random();

    @Test
    public void testCompressedFrame() {
        EmbeddedChannel encoderChannel = new EmbeddedChannel(new PerMessageDeflateEncoder(9, 15, false));
        EmbeddedChannel decoderChannel = new EmbeddedChannel(
                ZlibCodecFactory.newZlibDecoder(ZlibWrapper.NONE), BYTEBUF_TO_BUFFER_HANDLER);

        // initialize
        byte[] payload = new byte[300];
        random.nextBytes(payload);
        BinaryWebSocketFrame frame = new BinaryWebSocketFrame(true, WebSocketExtension.RSV3,
                encoderChannel.bufferAllocator().copyOf(payload));

        // execute
        assertTrue(encoderChannel.writeOutbound(frame));
        BinaryWebSocketFrame compressedFrame = encoderChannel.readOutbound();

        // test
        assertNotNull(compressedFrame);
        assertNotNull(compressedFrame.binaryData());
        assertEquals(WebSocketExtension.RSV1 | WebSocketExtension.RSV3, compressedFrame.rsv());

        assertTrue(decoderChannel.writeInbound(compressedFrame.binaryData()));
        assertTrue(decoderChannel.writeInbound(DeflateDecoder.FRAME_TAIL.get()));
        Buffer uncompressedPayload = decoderChannel.readInbound();
        assertEquals(300, uncompressedPayload.readableBytes());

        byte[] finalPayload = new byte[300];
        uncompressedPayload.readBytes(finalPayload, 0, finalPayload.length);
        assertArrayEquals(finalPayload, payload);
        uncompressedPayload.close();
    }

    @Test
    public void testAlreadyCompressedFrame() {
        EmbeddedChannel encoderChannel = new EmbeddedChannel(new PerMessageDeflateEncoder(9, 15, false));

        // initialize
        byte[] payload = new byte[300];
        random.nextBytes(payload);

        BinaryWebSocketFrame frame = new BinaryWebSocketFrame(true,
                                                              WebSocketExtension.RSV3 | WebSocketExtension.RSV1,
                                                              encoderChannel.bufferAllocator().copyOf(payload));

        // execute
        assertTrue(encoderChannel.writeOutbound(frame));
        BinaryWebSocketFrame newFrame = encoderChannel.readOutbound();

        // test
        assertNotNull(newFrame);
        assertNotNull(newFrame.binaryData());
        assertEquals(WebSocketExtension.RSV3 | WebSocketExtension.RSV1, newFrame.rsv());
        assertEquals(300, newFrame.binaryData().readableBytes());

        byte[] finalPayload = readIntoByteArray(newFrame, 300);
        assertArrayEquals(finalPayload, payload);
        newFrame.close();
    }

    @Test
    public void testFragmentedFrame() {
        EmbeddedChannel encoderChannel = new EmbeddedChannel(
                new PerMessageDeflateEncoder(9, 15, false, NEVER_SKIP));
        EmbeddedChannel decoderChannel = new EmbeddedChannel(
                ZlibCodecFactory.newZlibDecoder(ZlibWrapper.NONE), BYTEBUF_TO_BUFFER_HANDLER);

        // initialize
        byte[] payload1 = new byte[100];
        random.nextBytes(payload1);
        byte[] payload2 = new byte[100];
        random.nextBytes(payload2);
        byte[] payload3 = new byte[100];
        random.nextBytes(payload3);

        BinaryWebSocketFrame frame1 =
                new BinaryWebSocketFrame(false, WebSocketExtension.RSV3,
                        encoderChannel.bufferAllocator().copyOf(payload1));
        ContinuationWebSocketFrame frame2 =
                new ContinuationWebSocketFrame(false, WebSocketExtension.RSV3,
                        encoderChannel.bufferAllocator().copyOf(payload2));
        ContinuationWebSocketFrame frame3 =
                new ContinuationWebSocketFrame(true, WebSocketExtension.RSV3,
                        encoderChannel.bufferAllocator().copyOf(payload3));

        // execute
        assertTrue(encoderChannel.writeOutbound(frame1));
        assertTrue(encoderChannel.writeOutbound(frame2));
        assertTrue(encoderChannel.writeOutbound(frame3));
        BinaryWebSocketFrame compressedFrame1 = encoderChannel.readOutbound();
        ContinuationWebSocketFrame compressedFrame2 = encoderChannel.readOutbound();
        ContinuationWebSocketFrame compressedFrame3 = encoderChannel.readOutbound();

        // test
        assertNotNull(compressedFrame1);
        assertNotNull(compressedFrame2);
        assertNotNull(compressedFrame3);
        assertEquals(WebSocketExtension.RSV1 | WebSocketExtension.RSV3, compressedFrame1.rsv());
        assertEquals(WebSocketExtension.RSV3, compressedFrame2.rsv());
        assertEquals(WebSocketExtension.RSV3, compressedFrame3.rsv());
        assertFalse(compressedFrame1.isFinalFragment());
        assertFalse(compressedFrame2.isFinalFragment());
        assertTrue(compressedFrame3.isFinalFragment());

        assertTrue(decoderChannel.writeInbound(compressedFrame1.binaryData()));
        Buffer uncompressedPayload1 = decoderChannel.readInbound();
        byte[] finalPayload1 = new byte[100];
        uncompressedPayload1.readBytes(finalPayload1, 0, finalPayload1.length);
        assertArrayEquals(finalPayload1, payload1);
        uncompressedPayload1.close();

        assertTrue(decoderChannel.writeInbound(compressedFrame2.binaryData()));
        Buffer uncompressedPayload2 = decoderChannel.readInbound();
        byte[] finalPayload2 = new byte[100];
        uncompressedPayload2.readBytes(finalPayload2, 0, finalPayload2.length);
        assertArrayEquals(finalPayload2, payload2);
        uncompressedPayload2.close();

        assertTrue(decoderChannel.writeInbound(compressedFrame3.binaryData()));
        assertTrue(decoderChannel.writeInbound(DeflateDecoder.FRAME_TAIL.get()));
        Buffer uncompressedPayload3 = decoderChannel.readInbound();
        byte[] finalPayload3 = new byte[100];
        uncompressedPayload3.readBytes(finalPayload3, 0, finalPayload3.length);
        assertArrayEquals(finalPayload3, payload3);
        uncompressedPayload3.close();
    }

    @Test
    public void testCompressionSkipForBinaryFrame() {
        EmbeddedChannel encoderChannel = new EmbeddedChannel(new PerMessageDeflateEncoder(9, 15, false,
                                                                                          ALWAYS_SKIP));
        byte[] payload = new byte[300];
        random.nextBytes(payload);

        WebSocketFrame binaryFrame = new BinaryWebSocketFrame(encoderChannel.bufferAllocator().copyOf(payload));

        assertTrue(encoderChannel.writeOutbound(binaryFrame));
        WebSocketFrame outboundFrame = encoderChannel.readOutbound();

        assertEquals(0, outboundFrame.rsv());
        assertArrayEquals(payload, readIntoByteArray(outboundFrame));
        assertTrue(outboundFrame.isAccessible());
        outboundFrame.close();

        assertFalse(encoderChannel.finish());
    }

    @Test
    public void testSelectivityCompressionSkip() {
        WebSocketExtensionFilter selectivityCompressionFilter =
                frame -> (frame instanceof TextWebSocketFrame || frame instanceof BinaryWebSocketFrame)
            && frame.binaryData().readableBytes() < 100;
        EmbeddedChannel encoderChannel = new EmbeddedChannel(
                new PerMessageDeflateEncoder(9, 15, false, selectivityCompressionFilter));
        EmbeddedChannel decoderChannel = new EmbeddedChannel(
                ZlibCodecFactory.newZlibDecoder(ZlibWrapper.NONE), BYTEBUF_TO_BUFFER_HANDLER);

        String textPayload = "not compressed payload";
        byte[] binaryPayload = new byte[101];
        random.nextBytes(binaryPayload);

        WebSocketFrame textFrame = new TextWebSocketFrame(encoderChannel.bufferAllocator(), textPayload);
        BinaryWebSocketFrame binaryFrame =
                new BinaryWebSocketFrame(encoderChannel.bufferAllocator().copyOf(binaryPayload));

        assertTrue(encoderChannel.writeOutbound(textFrame));
        assertTrue(encoderChannel.writeOutbound(binaryFrame));

        WebSocketFrame outboundTextFrame = encoderChannel.readOutbound();

        //compression skipped for textFrame
        assertEquals(0, outboundTextFrame.rsv());
        assertEquals(textPayload, outboundTextFrame.binaryData().toString(UTF_8));
        assertTrue(outboundTextFrame.isAccessible());
        outboundTextFrame.close();

        WebSocketFrame outboundBinaryFrame = encoderChannel.readOutbound();

        //compression not skipped for binaryFrame
        assertEquals(WebSocketExtension.RSV1, outboundBinaryFrame.rsv());

        assertTrue(decoderChannel.writeInbound(outboundBinaryFrame.binaryData()));
        Buffer uncompressedBinaryPayload = decoderChannel.readInbound();

        byte[] uncompressedBinaryPayloadBytes = new byte[uncompressedBinaryPayload.readableBytes()];
        uncompressedBinaryPayload.readBytes(uncompressedBinaryPayloadBytes, 0, uncompressedBinaryPayloadBytes.length);
        assertArrayEquals(binaryPayload, uncompressedBinaryPayloadBytes);

        assertFalse(outboundBinaryFrame.isAccessible());
        assertTrue(uncompressedBinaryPayload.isAccessible());
        uncompressedBinaryPayload.close();

        assertFalse(encoderChannel.finish());
        assertFalse(decoderChannel.finish());
    }

    @Test
    public void testIllegalStateWhenCompressionInProgress() {
        WebSocketExtensionFilter selectivityCompressionFilter = frame -> frame.binaryData().readableBytes() < 100;
        EmbeddedChannel encoderChannel = new EmbeddedChannel(
                new PerMessageDeflateEncoder(9, 15, false, selectivityCompressionFilter));

        byte[] firstPayload = new byte[200];
        random.nextBytes(firstPayload);

        byte[] finalPayload = new byte[90];
        random.nextBytes(finalPayload);

        BinaryWebSocketFrame firstPart = new BinaryWebSocketFrame(false, 0,
                encoderChannel.bufferAllocator().copyOf(firstPayload));
        ContinuationWebSocketFrame finalPart =
                new ContinuationWebSocketFrame(true, 0,
                        encoderChannel.bufferAllocator().copyOf(finalPayload));
        assertTrue(encoderChannel.writeOutbound(firstPart));

        BinaryWebSocketFrame outboundFirstPart = encoderChannel.readOutbound();
        //first part is compressed
        assertEquals(WebSocketExtension.RSV1, outboundFirstPart.rsv());
        assertFalse(Arrays.equals(firstPayload, readIntoByteArray(outboundFirstPart)));
        assertTrue(outboundFirstPart.isAccessible());
        outboundFirstPart.close();

        //final part throwing exception
        try {
            assertThrows(EncoderException.class, () -> encoderChannel.writeOutbound(finalPart));
        } finally {
            assertTrue(finalPart.isAccessible());
            finalPart.close();
            assertFalse(encoderChannel.finishAndReleaseAll());
        }
    }

    @Test
    public void testEmptyFrameCompression() {
        EmbeddedChannel encoderChannel = new EmbeddedChannel(new PerMessageDeflateEncoder(9, 15, false));

        TextWebSocketFrame emptyFrame = new TextWebSocketFrame(encoderChannel.bufferAllocator(), "");

        assertTrue(encoderChannel.writeOutbound(emptyFrame));
        TextWebSocketFrame emptyDeflateFrame = encoderChannel.readOutbound();

        assertEquals(WebSocketExtension.RSV1, emptyDeflateFrame.rsv());
        try (Buffer expected = EMPTY_DEFLATE_BLOCK.get()) {
            assertEquals(expected, emptyDeflateFrame.binaryData());
        }
        emptyDeflateFrame.close();

        assertFalse(encoderChannel.finish());
    }

    @Test
    public void testCodecExceptionForNotFinEmptyFrame() {
        EmbeddedChannel encoderChannel = new EmbeddedChannel(new PerMessageDeflateEncoder(9, 15, false));

        TextWebSocketFrame emptyNotFinFrame = new TextWebSocketFrame(encoderChannel.bufferAllocator(),
                false, 0, "");

        try {
            assertThrows(EncoderException.class, () -> encoderChannel.writeOutbound(emptyNotFinFrame));
        } finally {
            // EmptyBuffer buffer
            assertFalse(emptyNotFinFrame.isAccessible());
            assertFalse(encoderChannel.finish());
        }
    }

    private static byte[] readIntoByteArray(WebSocketFrame frame) {
        return readIntoByteArray(frame, frame.binaryData().readableBytes());
    }

    private static byte[] readIntoByteArray(WebSocketFrame outboundFrame, int length) {
        Buffer frameBuffer = outboundFrame.binaryData();
        byte[] frameBytes = new byte[length];
        frameBuffer.readBytes(frameBytes, 0, frameBytes.length);
        return frameBytes;
    }
}
