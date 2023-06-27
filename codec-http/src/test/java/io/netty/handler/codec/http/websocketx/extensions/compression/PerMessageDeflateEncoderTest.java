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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.codec.compression.ZlibCodecFactory;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtension;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.Arrays;
import java.util.Random;

import static io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionFilter.*;
import static io.netty.handler.codec.http.websocketx.extensions.compression.DeflateDecoder.*;
import static io.netty.util.CharsetUtil.*;
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
                ZlibCodecFactory.newZlibDecoder(ZlibWrapper.NONE));

        // initialize
        byte[] payload = new byte[300];
        random.nextBytes(payload);
        BinaryWebSocketFrame frame = new BinaryWebSocketFrame(true,
                                                              WebSocketExtension.RSV3, Unpooled.wrappedBuffer(payload));

        // execute
        assertTrue(encoderChannel.writeOutbound(frame));
        BinaryWebSocketFrame compressedFrame = encoderChannel.readOutbound();

        // test
        assertNotNull(compressedFrame);
        assertNotNull(compressedFrame.content());
        assertEquals(WebSocketExtension.RSV1 | WebSocketExtension.RSV3, compressedFrame.rsv());

        assertTrue(decoderChannel.writeInbound(compressedFrame.content()));
        assertTrue(decoderChannel.writeInbound(DeflateDecoder.FRAME_TAIL.duplicate()));
        ByteBuf uncompressedPayload = decoderChannel.readInbound();
        assertEquals(300, uncompressedPayload.readableBytes());

        byte[] finalPayload = new byte[300];
        uncompressedPayload.readBytes(finalPayload);
        assertArrayEquals(finalPayload, payload);
        uncompressedPayload.release();
    }

    @Test
    public void testAlreadyCompressedFrame() {
        EmbeddedChannel encoderChannel = new EmbeddedChannel(new PerMessageDeflateEncoder(9, 15, false));

        // initialize
        byte[] payload = new byte[300];
        random.nextBytes(payload);

        BinaryWebSocketFrame frame = new BinaryWebSocketFrame(true,
                                                              WebSocketExtension.RSV3 | WebSocketExtension.RSV1,
                                                              Unpooled.wrappedBuffer(payload));

        // execute
        assertTrue(encoderChannel.writeOutbound(frame));
        BinaryWebSocketFrame newFrame = encoderChannel.readOutbound();

        // test
        assertNotNull(newFrame);
        assertNotNull(newFrame.content());
        assertEquals(WebSocketExtension.RSV3 | WebSocketExtension.RSV1, newFrame.rsv());
        assertEquals(300, newFrame.content().readableBytes());

        byte[] finalPayload = new byte[300];
        newFrame.content().readBytes(finalPayload);
        assertArrayEquals(finalPayload, payload);
        newFrame.release();
    }

    @Test
    public void testFragmentedFrame() {
        EmbeddedChannel encoderChannel = new EmbeddedChannel(new PerMessageDeflateEncoder(9, 15, false,
                                                                                          NEVER_SKIP));
        EmbeddedChannel decoderChannel = new EmbeddedChannel(
                ZlibCodecFactory.newZlibDecoder(ZlibWrapper.NONE));

        // initialize
        byte[] payload1 = new byte[100];
        random.nextBytes(payload1);
        byte[] payload2 = new byte[100];
        random.nextBytes(payload2);
        byte[] payload3 = new byte[100];
        random.nextBytes(payload3);

        BinaryWebSocketFrame frame1 = new BinaryWebSocketFrame(false,
                                                               WebSocketExtension.RSV3,
                                                               Unpooled.wrappedBuffer(payload1));
        ContinuationWebSocketFrame frame2 = new ContinuationWebSocketFrame(false,
                                                                           WebSocketExtension.RSV3,
                                                                           Unpooled.wrappedBuffer(payload2));
        ContinuationWebSocketFrame frame3 = new ContinuationWebSocketFrame(true,
                                                                           WebSocketExtension.RSV3,
                                                                           Unpooled.wrappedBuffer(payload3));

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

        assertTrue(decoderChannel.writeInbound(compressedFrame1.content()));
        ByteBuf uncompressedPayload1 = decoderChannel.readInbound();
        byte[] finalPayload1 = new byte[100];
        uncompressedPayload1.readBytes(finalPayload1);
        assertArrayEquals(finalPayload1, payload1);
        uncompressedPayload1.release();

        assertTrue(decoderChannel.writeInbound(compressedFrame2.content()));
        ByteBuf uncompressedPayload2 = decoderChannel.readInbound();
        byte[] finalPayload2 = new byte[100];
        uncompressedPayload2.readBytes(finalPayload2);
        assertArrayEquals(finalPayload2, payload2);
        uncompressedPayload2.release();

        assertTrue(decoderChannel.writeInbound(compressedFrame3.content()));
        assertTrue(decoderChannel.writeInbound(DeflateDecoder.FRAME_TAIL.duplicate()));
        ByteBuf uncompressedPayload3 = decoderChannel.readInbound();
        byte[] finalPayload3 = new byte[100];
        uncompressedPayload3.readBytes(finalPayload3);
        assertArrayEquals(finalPayload3, payload3);
        uncompressedPayload3.release();
    }

    @Test
    public void testCompressionSkipForBinaryFrame() {
        EmbeddedChannel encoderChannel = new EmbeddedChannel(new PerMessageDeflateEncoder(9, 15, false,
                                                                                          ALWAYS_SKIP));
        byte[] payload = new byte[300];
        random.nextBytes(payload);

        WebSocketFrame binaryFrame = new BinaryWebSocketFrame(Unpooled.wrappedBuffer(payload));

        assertTrue(encoderChannel.writeOutbound(binaryFrame.copy()));
        WebSocketFrame outboundFrame = encoderChannel.readOutbound();

        assertEquals(0, outboundFrame.rsv());
        assertArrayEquals(payload, ByteBufUtil.getBytes(outboundFrame.content()));
        assertTrue(outboundFrame.release());

        assertFalse(encoderChannel.finish());
    }

    @Test
    public void testSelectivityCompressionSkip() {
        WebSocketExtensionFilter selectivityCompressionFilter = new WebSocketExtensionFilter() {
            @Override
            public boolean mustSkip(WebSocketFrame frame) {
                return  (frame instanceof TextWebSocketFrame || frame instanceof BinaryWebSocketFrame)
                    && frame.content().readableBytes() < 100;
            }
        };
        EmbeddedChannel encoderChannel = new EmbeddedChannel(
                new PerMessageDeflateEncoder(9, 15, false, selectivityCompressionFilter));
        EmbeddedChannel decoderChannel = new EmbeddedChannel(
                ZlibCodecFactory.newZlibDecoder(ZlibWrapper.NONE));

        String textPayload = "not compressed payload";
        byte[] binaryPayload = new byte[101];
        random.nextBytes(binaryPayload);

        WebSocketFrame textFrame = new TextWebSocketFrame(textPayload);
        BinaryWebSocketFrame binaryFrame = new BinaryWebSocketFrame(Unpooled.wrappedBuffer(binaryPayload));

        assertTrue(encoderChannel.writeOutbound(textFrame));
        assertTrue(encoderChannel.writeOutbound(binaryFrame));

        WebSocketFrame outboundTextFrame = encoderChannel.readOutbound();

        //compression skipped for textFrame
        assertEquals(0, outboundTextFrame.rsv());
        assertEquals(textPayload, outboundTextFrame.content().toString(UTF_8));
        assertTrue(outboundTextFrame.release());

        WebSocketFrame outboundBinaryFrame = encoderChannel.readOutbound();

        //compression not skipped for binaryFrame
        assertEquals(WebSocketExtension.RSV1, outboundBinaryFrame.rsv());

        assertTrue(decoderChannel.writeInbound(outboundBinaryFrame.content().retain()));
        ByteBuf uncompressedBinaryPayload = decoderChannel.readInbound();

        assertArrayEquals(binaryPayload, ByteBufUtil.getBytes(uncompressedBinaryPayload));

        assertTrue(outboundBinaryFrame.release());
        assertTrue(uncompressedBinaryPayload.release());

        assertFalse(encoderChannel.finish());
        assertFalse(decoderChannel.finish());
    }

    @Test
    public void testIllegalStateWhenCompressionInProgress() {
        WebSocketExtensionFilter selectivityCompressionFilter = new WebSocketExtensionFilter() {
            @Override
            public boolean mustSkip(WebSocketFrame frame) {
                return frame.content().readableBytes() < 100;
            }
        };
        final EmbeddedChannel encoderChannel = new EmbeddedChannel(
                new PerMessageDeflateEncoder(9, 15, false, selectivityCompressionFilter));

        byte[] firstPayload = new byte[200];
        random.nextBytes(firstPayload);

        byte[] finalPayload = new byte[90];
        random.nextBytes(finalPayload);

        BinaryWebSocketFrame firstPart = new BinaryWebSocketFrame(false, 0, Unpooled.wrappedBuffer(firstPayload));
        final ContinuationWebSocketFrame finalPart = new ContinuationWebSocketFrame(true, 0,
                                                                              Unpooled.wrappedBuffer(finalPayload));
        assertTrue(encoderChannel.writeOutbound(firstPart));

        BinaryWebSocketFrame outboundFirstPart = encoderChannel.readOutbound();
        //first part is compressed
        assertEquals(WebSocketExtension.RSV1, outboundFirstPart.rsv());
        assertFalse(Arrays.equals(firstPayload, ByteBufUtil.getBytes(outboundFirstPart.content())));
        assertTrue(outboundFirstPart.release());

        //final part throwing exception
        try {
            assertThrows(EncoderException.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    encoderChannel.writeOutbound(finalPart);
                }
            });
        } finally {
            assertTrue(finalPart.release());
            assertFalse(encoderChannel.finishAndReleaseAll());
        }
    }

    @Test
    public void testEmptyFrameCompression() {
        EmbeddedChannel encoderChannel = new EmbeddedChannel(new PerMessageDeflateEncoder(9, 15, false));

        TextWebSocketFrame emptyFrame = new TextWebSocketFrame("");

        assertTrue(encoderChannel.writeOutbound(emptyFrame));
        TextWebSocketFrame emptyDeflateFrame = encoderChannel.readOutbound();

        assertEquals(WebSocketExtension.RSV1, emptyDeflateFrame.rsv());
        assertTrue(ByteBufUtil.equals(EMPTY_DEFLATE_BLOCK, emptyDeflateFrame.content()));
        // Unreleasable buffer
        assertFalse(emptyDeflateFrame.release());

        assertFalse(encoderChannel.finish());
    }

    @Test
    public void testCodecExceptionForNotFinEmptyFrame() {
        final EmbeddedChannel encoderChannel = new EmbeddedChannel(new PerMessageDeflateEncoder(9, 15, false));

        final TextWebSocketFrame emptyNotFinFrame = new TextWebSocketFrame(false, 0, "");

        try {
            assertThrows(EncoderException.class, new Executable() {
                @Override
                public void execute() {
                    encoderChannel.writeOutbound(emptyNotFinFrame);
                }
            });
        } finally {
            // EmptyByteBuf buffer
            assertFalse(emptyNotFinFrame.release());
            assertFalse(encoderChannel.finish());
        }
    }

}
