/*
 * Copyright 2014 The Netty Project
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
package io.netty.handler.codec.http.websocketx.extensions.compression;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.compression.ZlibCodecFactory;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtension;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionFilter;
import org.junit.Test;

import java.util.Random;

import static io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionFilter.*;
import static io.netty.handler.codec.http.websocketx.extensions.compression.DeflateDecoder.*;
import static io.netty.util.CharsetUtil.*;
import static org.junit.Assert.*;

public class PerMessageDeflateDecoderTest {

    private static final Random random = new Random();

    @Test
    public void testCompressedFrame() {
        EmbeddedChannel encoderChannel = new EmbeddedChannel(
                ZlibCodecFactory.newZlibEncoder(ZlibWrapper.NONE, 9, 15, 8));
        EmbeddedChannel decoderChannel = new EmbeddedChannel(new PerMessageDeflateDecoder(false));

        // initialize
        byte[] payload = new byte[300];
        random.nextBytes(payload);

        assertTrue(encoderChannel.writeOutbound(Unpooled.wrappedBuffer(payload)));
        ByteBuf compressedPayload = encoderChannel.readOutbound();

        BinaryWebSocketFrame compressedFrame = new BinaryWebSocketFrame(true,
                WebSocketExtension.RSV1 | WebSocketExtension.RSV3,
                compressedPayload.slice(0, compressedPayload.readableBytes() - 4));

        // execute
        assertTrue(decoderChannel.writeInbound(compressedFrame));
        BinaryWebSocketFrame uncompressedFrame = decoderChannel.readInbound();

        // test
        assertNotNull(uncompressedFrame);
        assertNotNull(uncompressedFrame.content());
        assertEquals(WebSocketExtension.RSV3, uncompressedFrame.rsv());
        assertEquals(300, uncompressedFrame.content().readableBytes());

        byte[] finalPayload = new byte[300];
        uncompressedFrame.content().readBytes(finalPayload);
        assertArrayEquals(finalPayload, payload);
        uncompressedFrame.release();
    }

    @Test
    public void testNormalFrame() {
        EmbeddedChannel decoderChannel = new EmbeddedChannel(new PerMessageDeflateDecoder(false));

        // initialize
        byte[] payload = new byte[300];
        random.nextBytes(payload);

        BinaryWebSocketFrame frame = new BinaryWebSocketFrame(true,
                WebSocketExtension.RSV3, Unpooled.wrappedBuffer(payload));

        // execute
        assertTrue(decoderChannel.writeInbound(frame));
        BinaryWebSocketFrame newFrame = decoderChannel.readInbound();

        // test
        assertNotNull(newFrame);
        assertNotNull(newFrame.content());
        assertEquals(WebSocketExtension.RSV3, newFrame.rsv());
        assertEquals(300, newFrame.content().readableBytes());

        byte[] finalPayload = new byte[300];
        newFrame.content().readBytes(finalPayload);
        assertArrayEquals(finalPayload, payload);
        newFrame.release();
    }

    @Test
    public void testFragmentedFrame() {
        EmbeddedChannel encoderChannel = new EmbeddedChannel(
                ZlibCodecFactory.newZlibEncoder(ZlibWrapper.NONE, 9, 15, 8));
        EmbeddedChannel decoderChannel = new EmbeddedChannel(new PerMessageDeflateDecoder(false));

        // initialize
        byte[] payload = new byte[300];
        random.nextBytes(payload);

        assertTrue(encoderChannel.writeOutbound(Unpooled.wrappedBuffer(payload)));
        ByteBuf compressedPayload = encoderChannel.readOutbound();
        compressedPayload = compressedPayload.slice(0, compressedPayload.readableBytes() - 4);

        int oneThird = compressedPayload.readableBytes() / 3;
        BinaryWebSocketFrame compressedFrame1 = new BinaryWebSocketFrame(false,
                WebSocketExtension.RSV1 | WebSocketExtension.RSV3,
                compressedPayload.slice(0, oneThird));
        ContinuationWebSocketFrame compressedFrame2 = new ContinuationWebSocketFrame(false,
                WebSocketExtension.RSV3, compressedPayload.slice(oneThird, oneThird));
        ContinuationWebSocketFrame compressedFrame3 = new ContinuationWebSocketFrame(true,
                WebSocketExtension.RSV3, compressedPayload.slice(oneThird * 2,
                        compressedPayload.readableBytes() - oneThird * 2));

        // execute
        assertTrue(decoderChannel.writeInbound(compressedFrame1.retain()));
        assertTrue(decoderChannel.writeInbound(compressedFrame2.retain()));
        assertTrue(decoderChannel.writeInbound(compressedFrame3));
        BinaryWebSocketFrame uncompressedFrame1 = decoderChannel.readInbound();
        ContinuationWebSocketFrame uncompressedFrame2 = decoderChannel.readInbound();
        ContinuationWebSocketFrame uncompressedFrame3 = decoderChannel.readInbound();

        // test
        assertNotNull(uncompressedFrame1);
        assertNotNull(uncompressedFrame2);
        assertNotNull(uncompressedFrame3);
        assertEquals(WebSocketExtension.RSV3, uncompressedFrame1.rsv());
        assertEquals(WebSocketExtension.RSV3, uncompressedFrame2.rsv());
        assertEquals(WebSocketExtension.RSV3, uncompressedFrame3.rsv());

        ByteBuf finalPayloadWrapped = Unpooled.wrappedBuffer(uncompressedFrame1.content(),
                uncompressedFrame2.content(), uncompressedFrame3.content());
        assertEquals(300, finalPayloadWrapped.readableBytes());

        byte[] finalPayload = new byte[300];
        finalPayloadWrapped.readBytes(finalPayload);
        assertArrayEquals(finalPayload, payload);
        finalPayloadWrapped.release();
    }

    @Test
    public void testMultiCompressedPayloadWithinFrame() {
        EmbeddedChannel encoderChannel = new EmbeddedChannel(
                ZlibCodecFactory.newZlibEncoder(ZlibWrapper.NONE, 9, 15, 8));
        EmbeddedChannel decoderChannel = new EmbeddedChannel(new PerMessageDeflateDecoder(false));

        // initialize
        byte[] payload1 = new byte[100];
        random.nextBytes(payload1);
        byte[] payload2 = new byte[100];
        random.nextBytes(payload2);

        assertTrue(encoderChannel.writeOutbound(Unpooled.wrappedBuffer(payload1)));
        ByteBuf compressedPayload1 = encoderChannel.readOutbound();
        assertTrue(encoderChannel.writeOutbound(Unpooled.wrappedBuffer(payload2)));
        ByteBuf compressedPayload2 = encoderChannel.readOutbound();

        BinaryWebSocketFrame compressedFrame = new BinaryWebSocketFrame(true,
                WebSocketExtension.RSV1 | WebSocketExtension.RSV3,
                Unpooled.wrappedBuffer(
                        compressedPayload1,
                        compressedPayload2.slice(0, compressedPayload2.readableBytes() - 4)));

        // execute
        assertTrue(decoderChannel.writeInbound(compressedFrame));
        BinaryWebSocketFrame uncompressedFrame = decoderChannel.readInbound();

        // test
        assertNotNull(uncompressedFrame);
        assertNotNull(uncompressedFrame.content());
        assertEquals(WebSocketExtension.RSV3, uncompressedFrame.rsv());
        assertEquals(200, uncompressedFrame.content().readableBytes());

        byte[] finalPayload1 = new byte[100];
        uncompressedFrame.content().readBytes(finalPayload1);
        assertArrayEquals(finalPayload1, payload1);
        byte[] finalPayload2 = new byte[100];
        uncompressedFrame.content().readBytes(finalPayload2);
        assertArrayEquals(finalPayload2, payload2);
        uncompressedFrame.release();
    }

    @Test
    public void testDecompressionSkipForBinaryFrame() {
        EmbeddedChannel encoderChannel = new EmbeddedChannel(
                ZlibCodecFactory.newZlibEncoder(ZlibWrapper.NONE, 9, 15, 8));
        EmbeddedChannel decoderChannel = new EmbeddedChannel(new PerMessageDeflateDecoder(false, ALWAYS_SKIP));

        byte[] payload = new byte[300];
        random.nextBytes(payload);

        assertTrue(encoderChannel.writeOutbound(Unpooled.wrappedBuffer(payload)));
        ByteBuf compressedPayload = encoderChannel.readOutbound();

        BinaryWebSocketFrame compressedBinaryFrame = new BinaryWebSocketFrame(true, WebSocketExtension.RSV1,
                                                                              compressedPayload);
        assertTrue(decoderChannel.writeInbound(compressedBinaryFrame));

        WebSocketFrame inboundFrame = decoderChannel.readInbound();

        assertEquals(WebSocketExtension.RSV1, inboundFrame.rsv());
        assertEquals(compressedPayload, inboundFrame.content());
        assertTrue(inboundFrame.release());

        assertTrue(encoderChannel.finishAndReleaseAll());
        assertFalse(decoderChannel.finish());
    }

    @Test
    public void testSelectivityDecompressionSkip() {
        WebSocketExtensionFilter selectivityDecompressionFilter = new WebSocketExtensionFilter() {
            @Override
            public boolean mustSkip(WebSocketFrame frame) {
                return frame instanceof TextWebSocketFrame && frame.content().readableBytes() < 100;
            }
        };
        EmbeddedChannel encoderChannel = new EmbeddedChannel(
                ZlibCodecFactory.newZlibEncoder(ZlibWrapper.NONE, 9, 15, 8));
        EmbeddedChannel decoderChannel = new EmbeddedChannel(
                new PerMessageDeflateDecoder(false, selectivityDecompressionFilter));

        String textPayload = "compressed payload";
        byte[] binaryPayload = new byte[300];
        random.nextBytes(binaryPayload);

        assertTrue(encoderChannel.writeOutbound(Unpooled.wrappedBuffer(textPayload.getBytes(UTF_8))));
        assertTrue(encoderChannel.writeOutbound(Unpooled.wrappedBuffer(binaryPayload)));
        ByteBuf compressedTextPayload = encoderChannel.readOutbound();
        ByteBuf compressedBinaryPayload = encoderChannel.readOutbound();

        TextWebSocketFrame compressedTextFrame = new TextWebSocketFrame(true, WebSocketExtension.RSV1,
                                                                        compressedTextPayload);
        BinaryWebSocketFrame compressedBinaryFrame = new BinaryWebSocketFrame(true, WebSocketExtension.RSV1,
                                                                              compressedBinaryPayload);

        assertTrue(decoderChannel.writeInbound(compressedTextFrame));
        assertTrue(decoderChannel.writeInbound(compressedBinaryFrame));

        TextWebSocketFrame inboundTextFrame = decoderChannel.readInbound();
        BinaryWebSocketFrame inboundBinaryFrame = decoderChannel.readInbound();

        assertEquals(WebSocketExtension.RSV1, inboundTextFrame.rsv());
        assertEquals(compressedTextPayload, inboundTextFrame.content());
        assertTrue(inboundTextFrame.release());

        assertEquals(0, inboundBinaryFrame.rsv());
        assertArrayEquals(binaryPayload, ByteBufUtil.getBytes(inboundBinaryFrame.content()));
        assertTrue(inboundBinaryFrame.release());

        assertTrue(encoderChannel.finishAndReleaseAll());
        assertFalse(decoderChannel.finish());
    }

    @Test(expected = DecoderException.class)
    public void testIllegalStateWhenDecompressionInProgress() {
        WebSocketExtensionFilter selectivityDecompressionFilter = new WebSocketExtensionFilter() {
            @Override
            public boolean mustSkip(WebSocketFrame frame) {
                return frame.content().readableBytes() < 100;
            }
        };

        EmbeddedChannel encoderChannel = new EmbeddedChannel(
                ZlibCodecFactory.newZlibEncoder(ZlibWrapper.NONE, 9, 15, 8));
        EmbeddedChannel decoderChannel = new EmbeddedChannel(
                new PerMessageDeflateDecoder(false, selectivityDecompressionFilter));

        byte[] firstPayload = new byte[200];
        random.nextBytes(firstPayload);

        byte[] finalPayload = new byte[50];
        random.nextBytes(finalPayload);

        assertTrue(encoderChannel.writeOutbound(Unpooled.wrappedBuffer(firstPayload)));
        assertTrue(encoderChannel.writeOutbound(Unpooled.wrappedBuffer(finalPayload)));
        ByteBuf compressedFirstPayload = encoderChannel.readOutbound();
        ByteBuf compressedFinalPayload = encoderChannel.readOutbound();
        assertTrue(encoderChannel.finishAndReleaseAll());

        BinaryWebSocketFrame firstPart = new BinaryWebSocketFrame(false, WebSocketExtension.RSV1,
                                                                  compressedFirstPayload);
        ContinuationWebSocketFrame finalPart = new ContinuationWebSocketFrame(true, WebSocketExtension.RSV1,
                                                                              compressedFinalPayload);
        assertTrue(decoderChannel.writeInbound(firstPart));

        BinaryWebSocketFrame outboundFirstPart = decoderChannel.readInbound();
        //first part is decompressed
        assertEquals(0, outboundFirstPart.rsv());
        assertArrayEquals(firstPayload, ByteBufUtil.getBytes(outboundFirstPart.content()));
        assertTrue(outboundFirstPart.release());

        //final part throwing exception
        try {
            decoderChannel.writeInbound(finalPart);
        } finally {
            assertTrue(finalPart.release());
            assertFalse(encoderChannel.finishAndReleaseAll());
        }
    }

    @Test
    public void testEmptyFrameDecompression() {
        EmbeddedChannel decoderChannel = new EmbeddedChannel(new PerMessageDeflateDecoder(false));

        TextWebSocketFrame emptyDeflateBlockFrame = new TextWebSocketFrame(true, WebSocketExtension.RSV1,
                                                                           EMPTY_DEFLATE_BLOCK);

        assertTrue(decoderChannel.writeInbound(emptyDeflateBlockFrame));
        TextWebSocketFrame emptyBufferFrame = decoderChannel.readInbound();

        assertFalse(emptyBufferFrame.content().isReadable());

        // Composite empty buffer
        assertTrue(emptyBufferFrame.release());
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
        EmbeddedChannel encoderChannel = new EmbeddedChannel(
                ZlibCodecFactory.newZlibEncoder(ZlibWrapper.NONE, 9, 15, 8));
        EmbeddedChannel decoderChannel = new EmbeddedChannel(new PerMessageDeflateDecoder(false));

        ByteBuf originPayload = Unpooled.wrappedBuffer(ByteBufUtil.decodeHexDump(hexDump));
        assertTrue(encoderChannel.writeOutbound(originPayload.duplicate().retain()));

        ByteBuf compressedPayload = encoderChannel.readOutbound();
        compressedPayload = compressedPayload.slice(0, compressedPayload.readableBytes() - 4);

        int oneThird = compressedPayload.readableBytes() / 3;

        TextWebSocketFrame compressedFrame1 = new TextWebSocketFrame(
                false, WebSocketExtension.RSV1, compressedPayload.slice(0, oneThird));
        ContinuationWebSocketFrame compressedFrame2 = new ContinuationWebSocketFrame(
                false, WebSocketExtension.RSV3, compressedPayload.slice(oneThird, oneThird));
        ContinuationWebSocketFrame compressedFrame3 = new ContinuationWebSocketFrame(
                false, WebSocketExtension.RSV3, compressedPayload.slice(oneThird * 2, oneThird));
        int offset = oneThird * 3;
        ContinuationWebSocketFrame compressedFrameWithExtraData = new ContinuationWebSocketFrame(
                true, WebSocketExtension.RSV3, compressedPayload.slice(offset,
                     compressedPayload.readableBytes() - offset));

        // check that last fragment contains only one extra byte
        assertEquals(1, compressedFrameWithExtraData.content().readableBytes());
        assertEquals(1, compressedFrameWithExtraData.content().getByte(0));

        // write compressed frames
        assertTrue(decoderChannel.writeInbound(compressedFrame1.retain()));
        assertTrue(decoderChannel.writeInbound(compressedFrame2.retain()));
        assertTrue(decoderChannel.writeInbound(compressedFrame3.retain()));
        assertTrue(decoderChannel.writeInbound(compressedFrameWithExtraData));

        // read uncompressed frames
        TextWebSocketFrame uncompressedFrame1 = decoderChannel.readInbound();
        ContinuationWebSocketFrame uncompressedFrame2 = decoderChannel.readInbound();
        ContinuationWebSocketFrame uncompressedFrame3 = decoderChannel.readInbound();
        ContinuationWebSocketFrame uncompressedExtraData = decoderChannel.readInbound();
        assertFalse(uncompressedExtraData.content().isReadable());

        ByteBuf uncompressedPayload = Unpooled.wrappedBuffer(uncompressedFrame1.content(), uncompressedFrame2.content(),
                                      uncompressedFrame3.content(), uncompressedExtraData.content());
        assertEquals(originPayload, uncompressedPayload);

        assertTrue(originPayload.release());
        assertTrue(uncompressedPayload.release());

        assertTrue(encoderChannel.finishAndReleaseAll());
        assertFalse(decoderChannel.finish());
    }
}
