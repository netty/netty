/*
 * Copyright 2012 The Netty Project
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
package io.netty.handler.codec.compression.snappy;

import static org.junit.Assert.assertArrayEquals;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import org.junit.Test;

public class SnappyFramedEncoderTest {
    private final SnappyFramedEncoder encoder = new SnappyFramedEncoder();

    @Test
    public void testSmallAmountOfDataIsUncompressed() throws Exception {
        ByteBuf in = Unpooled.wrappedBuffer(new byte[] {
            'n', 'e', 't', 't', 'y'
        });

        ByteBuf out = Unpooled.buffer(21);

        encoder.encode(null, in, out);

        byte[] expected = {
            -0x80, 0x06, 0x00, 0x73, 0x4e, 0x61, 0x50, 0x70, 0x59,
             0x01, 0x05, 0x00, 0x2d, -0x5a, -0x7e, -0x5e, 'n', 'e', 't', 't', 'y'
        };
        assertArrayEquals(expected, out.array());
    }

    @Test
    public void testLargeAmountOfDataIsCompressed() throws Exception {
        ByteBuf in = Unpooled.wrappedBuffer(new byte[] {
            'n', 'e', 't', 't', 'y', 'n', 'e', 't', 't', 'y',
            'n', 'e', 't', 't', 'y', 'n', 'e', 't', 't', 'y'
        });

        ByteBuf out = Unpooled.buffer(26);

        encoder.encode(null, in, out);

        byte[] expected = {
            -0x80, 0x06, 0x00, 0x73, 0x4e, 0x61, 0x50, 0x70, 0x59,
             0x00, 0x14, 0x00, 0x7b, 0x1f, 0x65, 0x64,
                   0x14, 0x10,
                   'n', 'e', 't', 't', 'y',
                   0x3a, 0x05, 0x00
        };
        assertArrayEquals(expected, out.array());
    }

    @Test
    public void testStreamStartIsOnlyWrittenOnce() throws Exception {
        ByteBuf in = Unpooled.wrappedBuffer(new byte[] {
            'n', 'e', 't', 't', 'y'
        });

        ByteBuf out = Unpooled.buffer(33);

        encoder.encode(null, in, out);
        in.readerIndex(0); // rewind the buffer to write the same data
        encoder.encode(null, in, out);

        byte[] expected = {
            -0x80, 0x06, 0x00, 0x73, 0x4e, 0x61, 0x50, 0x70, 0x59,
             0x01, 0x05, 0x00, 0x2d, -0x5a, -0x7e, -0x5e, 'n', 'e', 't', 't', 'y',
             0x01, 0x05, 0x00, 0x2d, -0x5a, -0x7e, -0x5e, 'n', 'e', 't', 't', 'y',
        };
        assertArrayEquals(expected, out.array());
    }
}
