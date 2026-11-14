/*
 * Copyright 2026 The Netty Project
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
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static io.netty.handler.codec.spdy.SpdyCodecUtil.SPDY_HEADER_SIZE;
import static io.netty.handler.codec.spdy.SpdyCodecUtil.SPDY_MAX_LENGTH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SpdyFrameEncoderTest {

    private final SpdyFrameEncoder encoder = new SpdyFrameEncoder(SpdyVersion.SPDY_3_1);
    private final ByteBufAllocator alloc = UnpooledByteBufAllocator.DEFAULT;

    @Test
    public void testDataFrameWithMaximumLength() {
        ByteBuf data = alloc.buffer(SPDY_MAX_LENGTH).writerIndex(SPDY_MAX_LENGTH);
        ByteBuf frame = encoder.encodeDataFrame(alloc, 1, false, data);
        try {
            assertEquals(SPDY_MAX_LENGTH, frame.getUnsignedMedium(5));
            assertEquals(SPDY_HEADER_SIZE + SPDY_MAX_LENGTH, frame.readableBytes());
        } finally {
            frame.release();
            data.release();
        }
    }

    @Test
    public void testDataFrameLongerThanTheLengthFieldIsRejected() {
        // A 2^24 byte body would be written with the length 0.
        final ByteBuf data = alloc.buffer(SPDY_MAX_LENGTH + 1).writerIndex(SPDY_MAX_LENGTH + 1);
        try {
            assertThrows(IllegalArgumentException.class, new Executable() {
                @Override
                public void execute() {
                    encoder.encodeDataFrame(alloc, 1, false, data);
                }
            });
        } finally {
            data.release();
        }
    }

    @Test
    public void testHeaderBlockLongerThanTheLengthFieldIsRejected() {
        // SYN_REPLY and HEADERS add 4 bytes for the stream id, SYN_STREAM 10.
        final ByteBuf headerBlock = alloc.buffer(SPDY_MAX_LENGTH - 3).writerIndex(SPDY_MAX_LENGTH - 3);
        try {
            assertThrows(IllegalArgumentException.class, new Executable() {
                @Override
                public void execute() {
                    encoder.encodeSynReplyFrame(alloc, 1, false, headerBlock);
                }
            });
            assertThrows(IllegalArgumentException.class, new Executable() {
                @Override
                public void execute() {
                    encoder.encodeHeadersFrame(alloc, 1, false, headerBlock);
                }
            });
            assertThrows(IllegalArgumentException.class, new Executable() {
                @Override
                public void execute() {
                    encoder.encodeSynStreamFrame(alloc, 1, 0, (byte) 0, false, false, headerBlock);
                }
            });
        } finally {
            headerBlock.release();
        }
    }
}
