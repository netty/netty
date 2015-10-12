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
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtension;

import java.util.Arrays;
import java.util.Random;

import org.junit.Test;

public class PerFrameDeflateDecoderTest {

    private static final Random random = new Random();

    @Test
    public void testCompressedFrame() {
        EmbeddedChannel encoderChannel = new EmbeddedChannel(
                ZlibCodecFactory.newZlibEncoder(ZlibWrapper.NONE, 9, 15, 8));
        EmbeddedChannel decoderChannel = new EmbeddedChannel(new PerFrameDeflateDecoder(false));

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
        EmbeddedChannel decoderChannel = new EmbeddedChannel(new PerFrameDeflateDecoder(false));

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

    // See https://github.com/netty/netty/issues/4348
    @Test
    public void testCompressedEmptyFrame() {
        EmbeddedChannel encoderChannel = new EmbeddedChannel(
                ZlibCodecFactory.newZlibEncoder(ZlibWrapper.NONE, 9, 15, 8));
        EmbeddedChannel decoderChannel = new EmbeddedChannel(new PerFrameDeflateDecoder(false));

        encoderChannel.writeOutbound(Unpooled.EMPTY_BUFFER);
        ByteBuf compressedPayload = encoderChannel.readOutbound();
        BinaryWebSocketFrame compressedFrame =
                new BinaryWebSocketFrame(true, WebSocketExtension.RSV1 | WebSocketExtension.RSV3, compressedPayload);

        // execute
        decoderChannel.writeInbound(compressedFrame);
        BinaryWebSocketFrame uncompressedFrame = decoderChannel.readInbound();

        // test
        assertNotNull(uncompressedFrame);
        assertNotNull(uncompressedFrame.content());
        assertTrue(uncompressedFrame instanceof BinaryWebSocketFrame);
        assertEquals(WebSocketExtension.RSV3, uncompressedFrame.rsv());
        assertEquals(0, uncompressedFrame.content().readableBytes());
        uncompressedFrame.release();
    }

}
