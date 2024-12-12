/*
 * Copyright 2024 The Netty Project
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
package io.netty.handler.codec.spdy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SpdyFrameCodecTest {
    private final SpdyFrameCodec codec = new SpdyFrameCodec(
            SpdyVersion.SPDY_3_1, 8192, 16384, 6, 15, 8, true, true) {
        @Override
        protected boolean isValidUnknownFrameHeader(final int streamId,
                                                    final int type,
                                                    final byte flags,
                                                    final int length) {
            return true;
        }
    };
    private final EmbeddedChannel channel = new EmbeddedChannel(
        codec
    );

    @Test
    public void testDecodeUnknownFrame() {
        final SpdyFrameEncoder encoder = new SpdyFrameEncoder(SpdyVersion.SPDY_3_1);
        final ByteBuf buf = encoder.encodeUnknownFrame(
            UnpooledByteBufAllocator.DEFAULT,
            200,
            (byte) 13,
            Unpooled.wrappedBuffer("Hello, world!".getBytes(CharsetUtil.UTF_8)));
        channel.writeInbound(buf);
        SpdyUnknownFrame frame = channel.readInbound();
        Assertions.assertNotNull(frame);
        Assertions.assertEquals(200, frame.frameType());
        Assertions.assertEquals((byte) 13, frame.flags());
        ByteBuf data = frame.content();
        Assertions.assertEquals("Hello, world!", data.toString(CharsetUtil.UTF_8));
        data.release();
    }

    @Test
    public void testEncodeUnknownFrame() {
        final SpdyUnknownFrame spdyUnknownFrame = new DefaultSpdyUnknownFrame(
            200,
            (byte) 13,
            Unpooled.wrappedBuffer("Hello, world!".getBytes(CharsetUtil.UTF_8)));
        channel.writeOutbound(spdyUnknownFrame);
        ByteBuf buf = channel.readOutbound();
        Assertions.assertNotNull(buf);
        channel.writeInbound(buf);
        SpdyUnknownFrame frame = channel.readInbound();
        Assertions.assertNotNull(frame);
        Assertions.assertEquals(200, frame.frameType());
        Assertions.assertEquals((byte) 13, frame.flags());
        ByteBuf data = frame.content();
        Assertions.assertEquals("Hello, world!", data.toString(CharsetUtil.UTF_8));
        data.release();
    }

}
