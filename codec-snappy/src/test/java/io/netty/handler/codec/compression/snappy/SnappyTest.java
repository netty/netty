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
import io.netty.handler.codec.compression.CompressionException;

import org.junit.After;
import org.junit.Test;

public class SnappyTest {
    private final Snappy snappy = new Snappy();

    @After
    public void resetSnappy() {
        snappy.reset();
    }

    @Test
    public void testDecodeLiteral() throws Exception {
        ByteBuf in = Unpooled.wrappedBuffer(new byte[] {
            0x05, // preamble length
            0x04 << 2, // literal tag + length
            0x6e, 0x65, 0x74, 0x74, 0x79 // "netty"
        });
        ByteBuf out = Unpooled.buffer(5);
        snappy.decode(in, out, 7);

        // "netty"
        byte[] expected = {
            0x6e, 0x65, 0x74, 0x74, 0x79
        };
        assertArrayEquals("Literal was not decoded correctly", expected, out.array());
    }

    @Test
    public void testDecodeCopyWith1ByteOffset() throws Exception {
        ByteBuf in = Unpooled.wrappedBuffer(new byte[] {
            0x01, // preamble length
            0x04 << 2, // literal tag + length
            0x6e, 0x65, 0x74, 0x74, 0x79, // "netty"
            0x05 << 2 | 0x01, // copy with 1-byte offset + length
            0x05 // offset
        });
        ByteBuf out = Unpooled.buffer(10);
        snappy.decode(in, out, 9);

        // "nettynetty" - we saved a whole byte :)
        byte[] expected = {
            0x6e, 0x65, 0x74, 0x74, 0x79, 0x6e, 0x65, 0x74, 0x74, 0x79
        };
        assertArrayEquals("Copy was not decoded correctly", expected, out.array());
    }

    @Test(expected = CompressionException.class)
    public void testDecodeCopyWithTinyOffset() throws Exception {
        ByteBuf in = Unpooled.wrappedBuffer(new byte[] {
            0x01, // preamble length
            0x04 << 2, // literal tag + length
            0x6e, 0x65, 0x74, 0x74, 0x79, // "netty"
            0x05 << 2 | 0x01, // copy with 1-byte offset + length
            0x03 // INVALID offset (< 4)
        });
        ByteBuf out = Unpooled.buffer(10);
        snappy.decode(in, out, 9);
    }

    @Test(expected = CompressionException.class)
    public void testDecodeCopyWithOffsetBeforeChunk() throws Exception {
        ByteBuf in = Unpooled.wrappedBuffer(new byte[] {
            0x01, // preamble length
            0x04 << 2, // literal tag + length
            0x6e, 0x65, 0x74, 0x74, 0x79, // "netty"
            0x05 << 2 | 0x01, // copy with 1-byte offset + length
            0x0b // INVALID offset (greater than chunk size)
        });
        ByteBuf out = Unpooled.buffer(10);
        snappy.decode(in, out, 9);
    }

    @Test(expected = CompressionException.class)
    public void testDecodeWithOverlyLongPreamble() throws Exception {
        ByteBuf in = Unpooled.wrappedBuffer(new byte[] {
            -0x80, -0x80, -0x80, -0x80, 0x7f, // preamble length
            0x04 << 2, // literal tag + length
            0x6e, 0x65, 0x74, 0x74, 0x79, // "netty"
        });
        ByteBuf out = Unpooled.buffer(10);
        snappy.decode(in, out, 9);
    }

    @Test
    public void encodeShortTextIsLiteral() throws Exception {
        ByteBuf in = Unpooled.wrappedBuffer(new byte[] {
            0x6e, 0x65, 0x74, 0x74, 0x79
        });
        ByteBuf out = Unpooled.buffer(7);
        snappy.encode(in, out, 5);

        byte[] expected = {
            0x05, // preamble length
            0x04 << 2, // literal tag + length
            0x6e, 0x65, 0x74, 0x74, 0x79 // "netty"
        };
        assertArrayEquals("Encoded literal was invalid", expected, out.array());
    }

    @Test
    public void encodeLongTextUsesCopy() throws Exception {
        ByteBuf in = Unpooled.wrappedBuffer(
            ("Netty has been designed carefully with the experiences " +
            "earned from the implementation of a lot of protocols " +
            "such as FTP, SMTP, HTTP, and various binary and " +
            "text-based legacy protocols").getBytes("US-ASCII")
        );
        ByteBuf out = Unpooled.buffer(180);
        snappy.encode(in, out, in.readableBytes());

        // The only compressibility in the above are the words "the ",
        // and "protocols", so this is a literal, followed by a copy
        // followed by another literal, followed by another copy
        byte[] expected = {
            -0x49, 0x01, // preamble length
            -0x10, 0x42, // literal tag + length

            // Literal
            0x4e, 0x65, 0x74, 0x74, 0x79, 0x20, 0x68, 0x61, 0x73, 0x20,
            0x62, 0x65, 0x65, 0x6e, 0x20, 0x64, 0x65, 0x73, 0x69, 0x67,
            0x6e, 0x65, 0x64, 0x20, 0x63, 0x61, 0x72, 0x65, 0x66, 0x75,
            0x6c, 0x6c, 0x79, 0x20, 0x77, 0x69, 0x74, 0x68, 0x20, 0x74,
            0x68, 0x65, 0x20, 0x65, 0x78, 0x70, 0x65, 0x72, 0x69, 0x65,
            0x6e, 0x63, 0x65, 0x73, 0x20, 0x65, 0x61, 0x72, 0x6e, 0x65,
            0x64, 0x20, 0x66, 0x72, 0x6f, 0x6d, 0x20,

            // First copy (the)
            0x01, 0x1C, -0x10,

            // Next literal
            0x66, 0x69, 0x6d, 0x70, 0x6c, 0x65, 0x6d, 0x65, 0x6e, 0x74,
            0x61, 0x74, 0x69, 0x6f, 0x6e, 0x20, 0x6f, 0x66, 0x20, 0x61,
            0x20, 0x6c, 0x6f, 0x74, 0x20, 0x6f, 0x66, 0x20, 0x70, 0x72,
            0x6f, 0x74, 0x6f, 0x63, 0x6f, 0x6c, 0x73, 0x20, 0x73, 0x75,
            0x63, 0x68, 0x20, 0x61, 0x73, 0x20, 0x46, 0x54, 0x50, 0x2c,
            0x20, 0x53, 0x4d, 0x54, 0x50, 0x2c, 0x20, 0x48, 0x54, 0x54,
            0x50, 0x2c, 0x20, 0x61, 0x6e, 0x64, 0x20, 0x76, 0x61, 0x72,
            0x69, 0x6f, 0x75, 0x73, 0x20, 0x62, 0x69, 0x6e, 0x61, 0x72,
            0x79, 0x20, 0x61, 0x6e, 0x64, 0x20, 0x74, 0x65, 0x78, 0x74,
            0x2d, 0x62, 0x61, 0x73, 0x65, 0x64, 0x20, 0x6c, 0x65, 0x67,
            0x61, 0x63, 0x79, 0x20,

            // Second copy (protocols)
            0x15, 0x4c
        };

        assertArrayEquals("Encoded result was incorrect", expected, out.array());
    }
}
