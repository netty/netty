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

import static org.junit.Assert.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.compression.ZlibCodecFactory;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtension;

import java.util.Arrays;
import java.util.Random;

import org.junit.Test;

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

        encoderChannel.writeOutbound(Unpooled.wrappedBuffer(payload));
        ByteBuf compressedPayload = encoderChannel.readOutbound();

        BinaryWebSocketFrame compressedFrame = new BinaryWebSocketFrame(true,
                WebSocketExtension.RSV1 | WebSocketExtension.RSV3,
                compressedPayload.slice(0, compressedPayload.readableBytes() - 4));

        // execute
        decoderChannel.writeInbound(compressedFrame);
        BinaryWebSocketFrame uncompressedFrame = decoderChannel.readInbound();

        // test
        assertNotNull(uncompressedFrame);
        assertNotNull(uncompressedFrame.content());
        assertTrue(uncompressedFrame instanceof BinaryWebSocketFrame);
        assertEquals(WebSocketExtension.RSV3, uncompressedFrame.rsv());
        assertEquals(300, uncompressedFrame.content().readableBytes());

        byte[] finalPayload = new byte[300];
        uncompressedFrame.content().readBytes(finalPayload);
        assertTrue(Arrays.equals(finalPayload, payload));
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
        decoderChannel.writeInbound(frame);
        BinaryWebSocketFrame newFrame = decoderChannel.readInbound();

        // test
        assertNotNull(newFrame);
        assertNotNull(newFrame.content());
        assertTrue(newFrame instanceof BinaryWebSocketFrame);
        assertEquals(WebSocketExtension.RSV3, newFrame.rsv());
        assertEquals(300, newFrame.content().readableBytes());

        byte[] finalPayload = new byte[300];
        newFrame.content().readBytes(finalPayload);
        assertTrue(Arrays.equals(finalPayload, payload));
        newFrame.release();
    }

    @Test
    public void testFramementedFrame() {
        EmbeddedChannel encoderChannel = new EmbeddedChannel(
                ZlibCodecFactory.newZlibEncoder(ZlibWrapper.NONE, 9, 15, 8));
        EmbeddedChannel decoderChannel = new EmbeddedChannel(new PerMessageDeflateDecoder(false));

        // initialize
        byte[] payload = new byte[300];
        random.nextBytes(payload);

        encoderChannel.writeOutbound(Unpooled.wrappedBuffer(payload));
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
        decoderChannel.writeInbound(compressedFrame1.retain());
        decoderChannel.writeInbound(compressedFrame2.retain());
        decoderChannel.writeInbound(compressedFrame3);
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
        assertTrue(Arrays.equals(finalPayload, payload));
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

        encoderChannel.writeOutbound(Unpooled.wrappedBuffer(payload1));
        ByteBuf compressedPayload1 = encoderChannel.readOutbound();
        encoderChannel.writeOutbound(Unpooled.wrappedBuffer(payload2));
        ByteBuf compressedPayload2 = encoderChannel.readOutbound();

        BinaryWebSocketFrame compressedFrame = new BinaryWebSocketFrame(true,
                WebSocketExtension.RSV1 | WebSocketExtension.RSV3,
                Unpooled.wrappedBuffer(
                        compressedPayload1,
                        compressedPayload2.slice(0, compressedPayload2.readableBytes() - 4)));

        // execute
        decoderChannel.writeInbound(compressedFrame);
        BinaryWebSocketFrame uncompressedFrame = decoderChannel.readInbound();

        // test
        assertNotNull(uncompressedFrame);
        assertNotNull(uncompressedFrame.content());
        assertTrue(uncompressedFrame instanceof BinaryWebSocketFrame);
        assertEquals(WebSocketExtension.RSV3, uncompressedFrame.rsv());
        assertEquals(200, uncompressedFrame.content().readableBytes());

        byte[] finalPayload1 = new byte[100];
        uncompressedFrame.content().readBytes(finalPayload1);
        assertTrue(Arrays.equals(finalPayload1, payload1));
        byte[] finalPayload2 = new byte[100];
        uncompressedFrame.content().readBytes(finalPayload2);
        assertTrue(Arrays.equals(finalPayload2, payload2));
        uncompressedFrame.release();
    }

}
