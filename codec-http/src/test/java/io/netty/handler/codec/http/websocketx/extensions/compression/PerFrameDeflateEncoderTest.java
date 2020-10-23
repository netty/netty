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
import io.netty.handler.codec.compression.ZlibCodecFactory;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtension;
import org.junit.Test;

import java.util.Random;

import static io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionFilter.*;
import static org.junit.Assert.*;

public class PerFrameDeflateEncoderTest {

    private static final Random random = new Random();

    @Test
    public void testCompressedFrame() {
        EmbeddedChannel encoderChannel = new EmbeddedChannel(new PerFrameDeflateEncoder(9, 15, false));
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
        EmbeddedChannel encoderChannel = new EmbeddedChannel(new PerFrameDeflateEncoder(9, 15, false));

        // initialize
        byte[] payload = new byte[300];
        random.nextBytes(payload);

        BinaryWebSocketFrame frame = new BinaryWebSocketFrame(true,
                WebSocketExtension.RSV3 | WebSocketExtension.RSV1, Unpooled.wrappedBuffer(payload));

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
    public void testFramementedFrame() {
        EmbeddedChannel encoderChannel = new EmbeddedChannel(new PerFrameDeflateEncoder(9, 15, false));
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
                WebSocketExtension.RSV3, Unpooled.wrappedBuffer(payload1));
        ContinuationWebSocketFrame frame2 = new ContinuationWebSocketFrame(false,
                WebSocketExtension.RSV3, Unpooled.wrappedBuffer(payload2));
        ContinuationWebSocketFrame frame3 = new ContinuationWebSocketFrame(true,
                WebSocketExtension.RSV3, Unpooled.wrappedBuffer(payload3));

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
        assertEquals(WebSocketExtension.RSV1 | WebSocketExtension.RSV3, compressedFrame2.rsv());
        assertEquals(WebSocketExtension.RSV1 | WebSocketExtension.RSV3, compressedFrame3.rsv());
        assertFalse(compressedFrame1.isFinalFragment());
        assertFalse(compressedFrame2.isFinalFragment());
        assertTrue(compressedFrame3.isFinalFragment());

        assertTrue(decoderChannel.writeInbound(compressedFrame1.content()));
        assertTrue(decoderChannel.writeInbound(DeflateDecoder.FRAME_TAIL.duplicate()));
        ByteBuf uncompressedPayload1 = decoderChannel.readInbound();
        byte[] finalPayload1 = new byte[100];
        uncompressedPayload1.readBytes(finalPayload1);
        assertArrayEquals(finalPayload1, payload1);
        uncompressedPayload1.release();

        assertTrue(decoderChannel.writeInbound(compressedFrame2.content()));
        assertTrue(decoderChannel.writeInbound(DeflateDecoder.FRAME_TAIL.duplicate()));
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
    public void testCompressionSkip() {
        EmbeddedChannel encoderChannel = new EmbeddedChannel(
                new PerFrameDeflateEncoder(9, 15, false, ALWAYS_SKIP));
        byte[] payload = new byte[300];
        random.nextBytes(payload);
        BinaryWebSocketFrame binaryFrame = new BinaryWebSocketFrame(true,
                                                                    0, Unpooled.wrappedBuffer(payload));

        // execute
        assertTrue(encoderChannel.writeOutbound(binaryFrame.copy()));
        BinaryWebSocketFrame outboundFrame = encoderChannel.readOutbound();

        // test
        assertNotNull(outboundFrame);
        assertNotNull(outboundFrame.content());
        assertArrayEquals(payload, ByteBufUtil.getBytes(outboundFrame.content()));
        assertEquals(0, outboundFrame.rsv());
        assertTrue(outboundFrame.release());

        assertFalse(encoderChannel.finish());
    }

}
