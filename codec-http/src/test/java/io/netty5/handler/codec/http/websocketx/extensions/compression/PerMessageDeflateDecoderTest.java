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

import io.netty5.buffer.ByteBufUtil;
import io.netty5.buffer.api.Buffer;
import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.handler.codec.DecoderException;
import io.netty5.handler.codec.compression.ZlibCodecFactory;
import io.netty5.handler.codec.compression.ZlibWrapper;
import io.netty5.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty5.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty5.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty5.handler.codec.http.websocketx.WebSocketFrame;
import io.netty5.handler.codec.http.websocketx.extensions.WebSocketExtensionFilter;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static io.netty5.handler.codec.BufferToByteBufHandler.BUFFER_TO_BYTEBUF_HANDLER;
import static io.netty5.handler.codec.ByteBufToBufferHandler.BYTEBUF_TO_BUFFER_HANDLER;
import static io.netty5.handler.codec.http.websocketx.extensions.WebSocketExtension.RSV1;
import static io.netty5.handler.codec.http.websocketx.extensions.WebSocketExtension.RSV3;
import static io.netty5.handler.codec.http.websocketx.extensions.WebSocketExtensionFilter.ALWAYS_SKIP;
import static io.netty5.handler.codec.http.websocketx.extensions.compression.DeflateEncoder.EMPTY_DEFLATE_BLOCK;
import static io.netty5.util.CharsetUtil.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PerMessageDeflateDecoderTest {

    private static final Random random = new Random();

    @Test
    public void testCompressedFrame() {
        EmbeddedChannel encoderChannel = new EmbeddedChannel(BYTEBUF_TO_BUFFER_HANDLER,
                ZlibCodecFactory.newZlibEncoder(ZlibWrapper.NONE, 9, 15, 8),
                BUFFER_TO_BYTEBUF_HANDLER);
        EmbeddedChannel decoderChannel = new EmbeddedChannel(new PerMessageDeflateDecoder(false));

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
        EmbeddedChannel decoderChannel = new EmbeddedChannel(new PerMessageDeflateDecoder(false));

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

    @Test
    public void testFragmentedFrame() {
        EmbeddedChannel encoderChannel = new EmbeddedChannel(BYTEBUF_TO_BUFFER_HANDLER,
                ZlibCodecFactory.newZlibEncoder(ZlibWrapper.NONE, 9, 15, 8),
                BUFFER_TO_BYTEBUF_HANDLER);
        EmbeddedChannel decoderChannel = new EmbeddedChannel(new PerMessageDeflateDecoder(false));

        // initialize
        byte[] payload = new byte[300];
        random.nextBytes(payload);

        assertTrue(encoderChannel.writeOutbound(encoderChannel.bufferAllocator().copyOf(payload)));
        Buffer compressedPayload = encoderChannel.readOutbound();
        BinaryWebSocketFrame compressedFrame1;
        ContinuationWebSocketFrame compressedFrame2;
        ContinuationWebSocketFrame compressedFrame3;
        compressedPayload.skipWritable(-4);

        int oneThird = compressedPayload.readableBytes() / 3;
        compressedFrame1 = new BinaryWebSocketFrame(false, RSV1 | RSV3, compressedPayload.readSplit(oneThird));
        compressedFrame2 = new ContinuationWebSocketFrame(false, RSV3, compressedPayload.readSplit(oneThird));
        compressedFrame3 = new ContinuationWebSocketFrame(true, RSV3, compressedPayload);

        // execute
        assertTrue(decoderChannel.writeInbound(compressedFrame1));
        assertTrue(decoderChannel.writeInbound(compressedFrame2));
        assertTrue(decoderChannel.writeInbound(compressedFrame3));
        BinaryWebSocketFrame uncompressedFrame1 = decoderChannel.readInbound();
        ContinuationWebSocketFrame uncompressedFrame2 = decoderChannel.readInbound();
        ContinuationWebSocketFrame uncompressedFrame3 = decoderChannel.readInbound();

        // test
        assertNotNull(uncompressedFrame1);
        assertNotNull(uncompressedFrame2);
        assertNotNull(uncompressedFrame3);
        assertEquals(RSV3, uncompressedFrame1.rsv());
        assertEquals(RSV3, uncompressedFrame2.rsv());
        assertEquals(RSV3, uncompressedFrame3.rsv());

        Buffer finalPayloadWrapped = encoderChannel.bufferAllocator()
                .allocate(uncompressedFrame1.binaryData().readableBytes() +
                        uncompressedFrame2.binaryData().readableBytes() +
                        uncompressedFrame3.binaryData().readableBytes());
        finalPayloadWrapped.writeBytes(uncompressedFrame1.binaryData());
        finalPayloadWrapped.writeBytes(uncompressedFrame2.binaryData());
        finalPayloadWrapped.writeBytes(uncompressedFrame3.binaryData());

        assertEquals(300, finalPayloadWrapped.readableBytes());

        byte[] finalPayload = new byte[300];
        finalPayloadWrapped.readBytes(finalPayload, 0, finalPayload.length);
        assertArrayEquals(finalPayload, payload);
        finalPayloadWrapped.close();
    }

    @Test
    public void testMultiCompressedPayloadWithinFrame() {
        EmbeddedChannel encoderChannel = new EmbeddedChannel(BYTEBUF_TO_BUFFER_HANDLER,
                ZlibCodecFactory.newZlibEncoder(ZlibWrapper.NONE, 9, 15, 8),
                BUFFER_TO_BYTEBUF_HANDLER);
        EmbeddedChannel decoderChannel = new EmbeddedChannel(new PerMessageDeflateDecoder(false));

        // initialize
        byte[] payload1 = new byte[100];
        random.nextBytes(payload1);
        byte[] payload2 = new byte[100];
        random.nextBytes(payload2);

        assertTrue(encoderChannel.writeOutbound(encoderChannel.bufferAllocator().copyOf(payload1)));
        Buffer compressedPayload1 = encoderChannel.readOutbound();
        assertTrue(encoderChannel.writeOutbound(encoderChannel.bufferAllocator().copyOf(payload2)));
        Buffer compressedPayload2 = encoderChannel.readOutbound();

        Buffer compressedFrameData = encoderChannel.bufferAllocator()
                .allocate(compressedPayload1.readableBytes() + compressedPayload2.readableBytes() - 4);
        compressedFrameData.writeBytes(compressedPayload1);
        compressedPayload2.skipWritable(-4);
        compressedFrameData.writeBytes(compressedPayload2);
        BinaryWebSocketFrame compressedFrame = new BinaryWebSocketFrame(true, RSV1 | RSV3, compressedFrameData);
        compressedPayload1.close();
        compressedPayload2.close();

        // execute
        assertTrue(decoderChannel.writeInbound(compressedFrame));
        BinaryWebSocketFrame uncompressedFrame = decoderChannel.readInbound();

        // test
        assertNotNull(uncompressedFrame);
        assertNotNull(uncompressedFrame.binaryData());
        assertEquals(RSV3, uncompressedFrame.rsv());
        assertEquals(200, uncompressedFrame.binaryData().readableBytes());

        byte[] finalPayload1 = new byte[100];
        uncompressedFrame.binaryData().readBytes(finalPayload1, 0, finalPayload1.length);
        assertArrayEquals(finalPayload1, payload1);
        byte[] finalPayload2 = new byte[100];
        uncompressedFrame.binaryData().readBytes(finalPayload2, 0, finalPayload2.length);
        assertArrayEquals(finalPayload2, payload2);
        uncompressedFrame.close();
    }

    @Test
    public void testDecompressionSkipForBinaryFrame() {
        EmbeddedChannel encoderChannel = new EmbeddedChannel(
                ZlibCodecFactory.newZlibEncoder(ZlibWrapper.NONE, 9, 15, 8));
        EmbeddedChannel decoderChannel = new EmbeddedChannel(new PerMessageDeflateDecoder(false, ALWAYS_SKIP));

        byte[] payload = new byte[300];
        random.nextBytes(payload);

        assertTrue(encoderChannel.writeOutbound(encoderChannel.bufferAllocator().copyOf(payload)));
        Buffer compressedPayload = encoderChannel.readOutbound();

        BinaryWebSocketFrame compressedBinaryFrame = new BinaryWebSocketFrame(true, RSV1, compressedPayload);
        assertTrue(decoderChannel.writeInbound(compressedBinaryFrame));

        WebSocketFrame inboundFrame = decoderChannel.readInbound();

        assertEquals(RSV1, inboundFrame.rsv());
        assertEquals(compressedPayload, inboundFrame.binaryData());
        assertTrue(inboundFrame.isAccessible());
        inboundFrame.close();

        assertTrue(encoderChannel.finishAndReleaseAll());
        assertFalse(decoderChannel.finish());
    }

    @Test
    public void testSelectivityDecompressionSkip() {
        WebSocketExtensionFilter selectivityDecompressionFilter =
                frame -> frame instanceof TextWebSocketFrame && frame.binaryData().readableBytes() < 100;
        EmbeddedChannel encoderChannel = new EmbeddedChannel(BYTEBUF_TO_BUFFER_HANDLER,
                ZlibCodecFactory.newZlibEncoder(ZlibWrapper.NONE, 9, 15, 8),
                BUFFER_TO_BYTEBUF_HANDLER);
        EmbeddedChannel decoderChannel = new EmbeddedChannel(
                new PerMessageDeflateDecoder(false, selectivityDecompressionFilter));

        String textPayload = "compressed payload";
        byte[] binaryPayload = new byte[300];
        random.nextBytes(binaryPayload);

        assertTrue(encoderChannel.writeOutbound(encoderChannel.bufferAllocator().copyOf(textPayload.getBytes(UTF_8))));
        assertTrue(encoderChannel.writeOutbound(encoderChannel.bufferAllocator().copyOf(binaryPayload)));
        Buffer compressedTextPayload = encoderChannel.readOutbound();
        Buffer compressedBinaryPayload = encoderChannel.readOutbound();

        TextWebSocketFrame compressedTextFrame = new TextWebSocketFrame(true, RSV1, compressedTextPayload);
        BinaryWebSocketFrame compressedBinaryFrame = new BinaryWebSocketFrame(true, RSV1, compressedBinaryPayload);

        assertTrue(decoderChannel.writeInbound(compressedTextFrame));
        assertTrue(decoderChannel.writeInbound(compressedBinaryFrame));

        TextWebSocketFrame inboundTextFrame = decoderChannel.readInbound();
        BinaryWebSocketFrame inboundBinaryFrame = decoderChannel.readInbound();

        assertEquals(RSV1, inboundTextFrame.rsv());
        assertEquals(compressedTextPayload, inboundTextFrame.binaryData());
        assertTrue(inboundTextFrame.isAccessible());
        inboundTextFrame.close();

        assertEquals(0, inboundBinaryFrame.rsv());
        assertArrayEquals(binaryPayload, readIntoByteArray(inboundBinaryFrame));
        assertTrue(inboundBinaryFrame.isAccessible());
        inboundBinaryFrame.close();

        assertTrue(encoderChannel.finishAndReleaseAll());
        assertFalse(decoderChannel.finish());
    }

    @Test
    public void testIllegalStateWhenDecompressionInProgress() {
        WebSocketExtensionFilter selectivityDecompressionFilter = frame -> frame.binaryData().readableBytes() < 100;

        EmbeddedChannel encoderChannel = new EmbeddedChannel(BYTEBUF_TO_BUFFER_HANDLER,
                ZlibCodecFactory.newZlibEncoder(ZlibWrapper.NONE, 9, 15, 8),
                BUFFER_TO_BYTEBUF_HANDLER);
        EmbeddedChannel decoderChannel = new EmbeddedChannel(
                new PerMessageDeflateDecoder(false, selectivityDecompressionFilter));

        byte[] firstPayload = new byte[200];
        random.nextBytes(firstPayload);

        byte[] finalPayload = new byte[50];
        random.nextBytes(finalPayload);

        assertTrue(encoderChannel.writeOutbound(encoderChannel.bufferAllocator().copyOf(firstPayload)));
        assertTrue(encoderChannel.writeOutbound(encoderChannel.bufferAllocator().copyOf(finalPayload)));
        Buffer compressedFirstPayload = encoderChannel.readOutbound();
        Buffer compressedFinalPayload = encoderChannel.readOutbound();
        assertTrue(encoderChannel.finishAndReleaseAll());

        BinaryWebSocketFrame firstPart = new BinaryWebSocketFrame(false, RSV1, compressedFirstPayload);
        ContinuationWebSocketFrame finalPart = new ContinuationWebSocketFrame(true, RSV1, compressedFinalPayload);
        assertTrue(decoderChannel.writeInbound(firstPart));

        BinaryWebSocketFrame outboundFirstPart = decoderChannel.readInbound();
        //first part is decompressed
        assertEquals(0, outboundFirstPart.rsv());
        assertArrayEquals(firstPayload, readIntoByteArray(outboundFirstPart));
        assertTrue(outboundFirstPart.isAccessible());
        outboundFirstPart.close();

        //final part throwing exception
        try {
            assertThrows(DecoderException.class, () -> decoderChannel.writeInbound(finalPart));
        } finally {
            assertTrue(finalPart.isAccessible());
            finalPart.close();
            assertFalse(encoderChannel.finishAndReleaseAll());
        }
    }

    @Test
    public void testEmptyFrameDecompression() {
        EmbeddedChannel decoderChannel = new EmbeddedChannel(new PerMessageDeflateDecoder(false));

        TextWebSocketFrame emptyDeflateBlockFrame = new TextWebSocketFrame(true, RSV1, EMPTY_DEFLATE_BLOCK.get());

        assertTrue(decoderChannel.writeInbound(emptyDeflateBlockFrame));
        TextWebSocketFrame emptyBufferFrame = decoderChannel.readInbound();

        assertFalse(emptyBufferFrame.binaryData().readableBytes() > 0);

        // Composite empty buffer
        assertTrue(emptyBufferFrame.isAccessible());
        emptyBufferFrame.close();
        assertFalse(decoderChannel.finish());
    }

    @Test
    public void testFragmentedFrameWithLeftOverInLastFragment() {
        String hexDump = "677170647a777a737574656b707a787a6f6a7561756578756f6b7868616371716c657a6d64697479766d726f6" +
                         "269746c6376777464776f6f72767a726f64667278676764687775786f6762766d776d706b76697773777a7072" +
                         "6a6a737279707a7078697a6c69616d7461656d646278626d786f66666e686e776a7a7461746d7a776668776b6" +
                         "f6f736e73746575637a6d727a7175707a6e74627578687871767771697a71766c64626d78726d6d7675756877" +
                         "62667963626b687a726d676e646263776e67797264706d6c6863626577616967706a78636a72697464756e627" +
                         "977616f79736475676f76736f7178746a7a7479626c64636b6b6778637768746c62";
        EmbeddedChannel encoderChannel = new EmbeddedChannel(BYTEBUF_TO_BUFFER_HANDLER,
                ZlibCodecFactory.newZlibEncoder(ZlibWrapper.NONE, 9, 15, 8),
                BUFFER_TO_BYTEBUF_HANDLER);
        EmbeddedChannel decoderChannel = new EmbeddedChannel(new PerMessageDeflateDecoder(false));

        Buffer originPayload = encoderChannel.bufferAllocator().copyOf(ByteBufUtil.decodeHexDump(hexDump));
        assertTrue(encoderChannel.writeOutbound(originPayload.copy()));

        Buffer compressedPayload = encoderChannel.readOutbound();
        TextWebSocketFrame compressedFrame1;
        ContinuationWebSocketFrame compressedFrame2;
        ContinuationWebSocketFrame compressedFrame3;
        ContinuationWebSocketFrame compressedFrameWithExtraData;

        compressedPayload.skipWritable(-4);
        int oneThird = compressedPayload.readableBytes() / 3;
        compressedFrame1 = new TextWebSocketFrame(false, RSV1, compressedPayload.readSplit(oneThird));
        compressedFrame2 = new ContinuationWebSocketFrame(false, RSV3, compressedPayload.readSplit(oneThird));
        compressedFrame3 = new ContinuationWebSocketFrame(false, RSV3, compressedPayload.readSplit(oneThird));
        compressedFrameWithExtraData = new ContinuationWebSocketFrame(true, RSV3, compressedPayload);

        // check that last fragment contains only one extra byte
        assertEquals(1, compressedFrameWithExtraData.binaryData().readableBytes());
        assertEquals(1, compressedFrameWithExtraData.binaryData().getByte(0));

        // write compressed frames
        assertTrue(decoderChannel.writeInbound(compressedFrame1));
        assertTrue(decoderChannel.writeInbound(compressedFrame2));
        assertTrue(decoderChannel.writeInbound(compressedFrame3));
        assertTrue(decoderChannel.writeInbound(compressedFrameWithExtraData));

        // read uncompressed frames
        TextWebSocketFrame uncompressedFrame1 = decoderChannel.readInbound();
        ContinuationWebSocketFrame uncompressedFrame2 = decoderChannel.readInbound();
        ContinuationWebSocketFrame uncompressedFrame3 = decoderChannel.readInbound();
        ContinuationWebSocketFrame uncompressedExtraData = decoderChannel.readInbound();
        assertFalse(uncompressedExtraData.binaryData().readableBytes() > 0);

        Buffer uncompressedPayload = encoderChannel.bufferAllocator()
                .allocate(uncompressedFrame1.binaryData().readableBytes() +
                        uncompressedFrame2.binaryData().readableBytes() +
                        uncompressedFrame3.binaryData().readableBytes());
        uncompressedPayload.writeBytes(uncompressedFrame1.binaryData());
        uncompressedPayload.writeBytes(uncompressedFrame2.binaryData());
        uncompressedPayload.writeBytes(uncompressedFrame3.binaryData());
        assertEquals(originPayload, uncompressedPayload);

        assertTrue(originPayload.isAccessible());
        originPayload.close();
        assertTrue(uncompressedPayload.isAccessible());
        uncompressedPayload.close();

        assertTrue(encoderChannel.finishAndReleaseAll());
        assertFalse(decoderChannel.finish());
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
