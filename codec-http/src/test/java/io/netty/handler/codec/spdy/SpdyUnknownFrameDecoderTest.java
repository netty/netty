/*
 * Copyright 2015 The Netty Project
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static io.netty.handler.codec.spdy.SpdyCodecUtil.SPDY_HEADER_SIZE;
import static io.netty.handler.codec.spdy.SpdyFrameDecoderTest.encodeControlFrameHeader;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class SpdyUnknownFrameDecoderTest {

    private static final Random RANDOM = new Random();

    private final SpdyFrameDecoderDelegate delegate = mock(SpdyFrameDecoderDelegate.class);
    private final TestSpdyFrameDecoderDelegate testDelegate = new TestSpdyFrameDecoderDelegate(delegate);
    private SpdyFrameDecoder decoder;

    @BeforeEach
    public void createDecoder() {
        decoder = new SpdyFrameDecoder(SpdyVersion.SPDY_3_1, testDelegate) {
            @Override
            protected boolean isValidUnknownFrameHeader(final int streamId,
                                                        final int type,
                                                        final byte flags,
                                                        final int length) {
                return true;
            }
        };
    }

    @AfterEach
    public void releaseBuffers() {
        testDelegate.releaseAll();
    }

    @Test
    public void testDecodeUnknownFrame() throws Exception {
        short type = 200;
        byte flags = (byte) 0xFF;
        int length = 8;

        ByteBuf buf = Unpooled.buffer(SPDY_HEADER_SIZE + length);
        encodeControlFrameHeader(buf, type, flags, length);
        final long value = RANDOM.nextLong();
        buf.writeLong(value);

        decoder.decode(buf);
        verify(delegate).readUnknownFrame(type, flags, buf.slice(SPDY_HEADER_SIZE, 8));
        assertFalse(buf.isReadable());
    }

}
