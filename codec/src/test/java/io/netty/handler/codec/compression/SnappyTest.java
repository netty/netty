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
package io.netty.handler.codec.compression;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.CharsetUtil;
import org.junit.After;
import org.junit.Test;

import static io.netty.handler.codec.compression.Snappy.*;
import static org.junit.Assert.*;

import java.nio.CharBuffer;

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
        snappy.decode(in, out);

        // "netty"
        ByteBuf expected = Unpooled.wrappedBuffer(new byte[] {
            0x6e, 0x65, 0x74, 0x74, 0x79
        });
        assertEquals("Literal was not decoded correctly", expected, out);

        in.release();
        out.release();
        expected.release();
    }

    @Test
    public void testDecodeCopyWith1ByteOffset() throws Exception {
        ByteBuf in = Unpooled.wrappedBuffer(new byte[] {
            0x0a, // preamble length
            0x04 << 2, // literal tag + length
            0x6e, 0x65, 0x74, 0x74, 0x79, // "netty"
            0x01 << 2 | 0x01, // copy with 1-byte offset + length
            0x05 // offset
        });
        ByteBuf out = Unpooled.buffer(10);
        snappy.decode(in, out);

        // "nettynetty" - we saved a whole byte :)
        ByteBuf expected = Unpooled.wrappedBuffer(new byte[] {
            0x6e, 0x65, 0x74, 0x74, 0x79, 0x6e, 0x65, 0x74, 0x74, 0x79
        });
        assertEquals("Copy was not decoded correctly", expected, out);

        in.release();
        out.release();
        expected.release();
    }

    @Test(expected = DecompressionException.class)
    public void testDecodeCopyWithTinyOffset() throws Exception {
        ByteBuf in = Unpooled.wrappedBuffer(new byte[] {
            0x0b, // preamble length
            0x04 << 2, // literal tag + length
            0x6e, 0x65, 0x74, 0x74, 0x79, // "netty"
            0x05 << 2 | 0x01, // copy with 1-byte offset + length
            0x00 // INVALID offset (< 1)
        });
        ByteBuf out = Unpooled.buffer(10);
        try {
            snappy.decode(in, out);
        } finally {
            in.release();
            out.release();
        }
    }

    @Test(expected = DecompressionException.class)
    public void testDecodeCopyWithOffsetBeforeChunk() throws Exception {
        ByteBuf in = Unpooled.wrappedBuffer(new byte[] {
            0x0a, // preamble length
            0x04 << 2, // literal tag + length
            0x6e, 0x65, 0x74, 0x74, 0x79, // "netty"
            0x05 << 2 | 0x01, // copy with 1-byte offset + length
            0x0b // INVALID offset (greater than chunk size)
        });
        ByteBuf out = Unpooled.buffer(10);
        try {
            snappy.decode(in, out);
        } finally {
            in.release();
            out.release();
        }
    }

    @Test(expected = DecompressionException.class)
    public void testDecodeWithOverlyLongPreamble() throws Exception {
        ByteBuf in = Unpooled.wrappedBuffer(new byte[] {
            -0x80, -0x80, -0x80, -0x80, 0x7f, // preamble length
            0x04 << 2, // literal tag + length
            0x6e, 0x65, 0x74, 0x74, 0x79, // "netty"
        });
        ByteBuf out = Unpooled.buffer(10);
        try {
            snappy.decode(in, out);
        } finally {
            in.release();
            out.release();
        }
    }

    @Test
    public void encodeShortTextIsLiteral() throws Exception {
        ByteBuf in = Unpooled.wrappedBuffer(new byte[] {
            0x6e, 0x65, 0x74, 0x74, 0x79
        });
        ByteBuf out = Unpooled.buffer(7);
        snappy.encode(in, out, 5);

        ByteBuf expected = Unpooled.wrappedBuffer(new byte[] {
            0x05, // preamble length
            0x04 << 2, // literal tag + length
            0x6e, 0x65, 0x74, 0x74, 0x79 // "netty"
        });
        assertEquals("Encoded literal was invalid", expected, out);

        in.release();
        out.release();
        expected.release();
    }

    @Test
    public void encodeAndDecodeLongTextUsesCopy() throws Exception {
        String srcStr = "Netty has been designed carefully with the experiences " +
                   "earned from the implementation of a lot of protocols " +
                   "such as FTP, SMTP, HTTP, and various binary and " +
                   "text-based legacy protocols";
        ByteBuf in = Unpooled.wrappedBuffer(srcStr.getBytes("US-ASCII"));
        ByteBuf out = Unpooled.buffer(180);
        snappy.encode(in, out, in.readableBytes());

        // The only compressibility in the above are the words:
        // "the ", "rotocols", " of ", "TP, " and "and ". So this is a literal,
        // followed by a copy followed by another literal, followed by another copy...
        ByteBuf expected = Unpooled.wrappedBuffer(new byte[] {
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

            // copy of "the "
            0x01, 0x1c, 0x58,

            // Next literal
            0x69, 0x6d, 0x70, 0x6c, 0x65, 0x6d, 0x65, 0x6e, 0x74, 0x61,
            0x74, 0x69, 0x6f, 0x6e, 0x20, 0x6f, 0x66, 0x20, 0x61, 0x20,
            0x6c, 0x6f, 0x74,

            // copy of " of "
            0x01, 0x09, 0x60,

            // literal
            0x70, 0x72, 0x6f, 0x74, 0x6f, 0x63, 0x6f, 0x6c, 0x73, 0x20,
            0x73, 0x75, 0x63, 0x68, 0x20, 0x61, 0x73, 0x20, 0x46, 0x54,
            0x50, 0x2c, 0x20, 0x53, 0x4d,

            // copy of " TP, "
            0x01, 0x06, 0x04,

            // literal
            0x48, 0x54,

            // copy of " TP, "
            0x01, 0x06, 0x44,

            // literal
            0x61, 0x6e, 0x64, 0x20, 0x76, 0x61, 0x72, 0x69, 0x6f, 0x75,
            0x73, 0x20, 0x62, 0x69, 0x6e, 0x61, 0x72, 0x79,

            // copy of "and "
            0x05, 0x13, 0x48,

            // literal
            0x74, 0x65, 0x78, 0x74, 0x2d, 0x62, 0x61, 0x73, 0x65,
            0x64, 0x20, 0x6c, 0x65, 0x67, 0x61, 0x63, 0x79, 0x20, 0x70,

            // copy of "rotocols"
            0x11, 0x4c,
        });

        assertEquals("Encoded result was incorrect", expected, out);

        // Decode
        ByteBuf outDecoded = Unpooled.buffer();
        snappy.decode(out, outDecoded);
        assertEquals(CharBuffer.wrap(srcStr),
            CharBuffer.wrap(outDecoded.getCharSequence(0, outDecoded.writerIndex(), CharsetUtil.US_ASCII)));

        in.release();
        out.release();
        outDecoded.release();
    }

    @Test
    public void testCalculateChecksum() {
        ByteBuf input = Unpooled.wrappedBuffer(new byte[] {
                'n', 'e', 't', 't', 'y'
        });

        assertEquals(maskChecksum(0xd6cb8b55L), calculateChecksum(input));
        input.release();
    }

    @Test
    public void testMaskChecksum() {
        ByteBuf input = Unpooled.wrappedBuffer(new byte[] {
                0x00, 0x00, 0x00, 0x0f, 0x00, 0x00, 0x00, 0x00,
                0x5f, 0x68, 0x65, 0x61, 0x72, 0x74, 0x62, 0x65,
                0x61, 0x74, 0x5f,
        });
        assertEquals(0x44a4301f, calculateChecksum(input));
        input.release();
    }

    @Test
    public void testValidateChecksumMatches() {
        ByteBuf input = Unpooled.wrappedBuffer(new byte[] {
                'y', 't', 't', 'e', 'n'
        });

        validateChecksum(maskChecksum(0x2d4d3535), input);
        input.release();
    }

    @Test(expected = DecompressionException.class)
    public void testValidateChecksumFails() {
        ByteBuf input = Unpooled.wrappedBuffer(new byte[] {
                'y', 't', 't', 'e', 'n'
        });
        try {
            validateChecksum(maskChecksum(0xd6cb8b55), input);
        } finally {
            input.release();
        }
    }

    @Test
    public void testEncodeLiteralAndDecodeLiteral() {
        int[] lengths = {
            0x11, // default
            0x100, // case 60
            0x1000, // case 61
            0x100000, // case 62
            0x1000001 // case 63
        };
        for (int len : lengths) {
            ByteBuf in = Unpooled.wrappedBuffer(new byte[len]);
            ByteBuf encoded = Unpooled.buffer(10);
            ByteBuf decoded = Unpooled.buffer(10);
            ByteBuf expected = Unpooled.wrappedBuffer(new byte[len]);
            try {
                encodeLiteral(in, encoded, len);
                byte tag = encoded.readByte();
                decodeLiteral(tag, encoded, decoded);
                assertEquals("Encoded or decoded literal was incorrect", expected, decoded);
            } finally {
                in.release();
                encoded.release();
                decoded.release();
                expected.release();
            }
        }
    }

    @Test
    public void testLarge2ByteLiteralLengthAndCopyOffset() {
        ByteBuf compressed = Unpooled.buffer();
        ByteBuf actualDecompressed = Unpooled.buffer();
        ByteBuf expectedDecompressed = Unpooled.buffer().writeByte(0x01).writeZero(0x8000).writeByte(0x01);
        try {
            // Generate a Snappy-encoded buffer that can only be decompressed correctly if
            // the decoder treats 2-byte literal lengths and 2-byte copy offsets as unsigned values.

            // Write preamble, uncompressed content length (0x8002) encoded as varint.
            compressed.writeByte(0x82).writeByte(0x80).writeByte(0x02);

            // Write a literal consisting of 0x01 followed by 0x8000 zeroes.
            // The total length of this literal is 0x8001, which gets encoded as 0x8000 (length - 1).
            // This length was selected because the encoded form is one larger than the maximum value
            // representable using a signed 16-bit integer, and we want to assert the decoder is reading
            // the length as an unsigned value.
            compressed.writeByte(61 << 2); // tag for LITERAL with a 2-byte length
            compressed.writeShortLE(0x8000); // length - 1
            compressed.writeByte(0x01).writeZero(0x8000); // literal content

            // Similarly, for a 2-byte copy operation we want to ensure the offset is treated as unsigned.
            // Copy the initial 0x01 which was written 0x8001 bytes back in the stream.
            compressed.writeByte(0x02); // tag for COPY with 2-byte offset, length = 1
            compressed.writeShortLE(0x8001); // offset

            snappy.decode(compressed, actualDecompressed);
            assertEquals(expectedDecompressed, actualDecompressed);
        } finally {
            compressed.release();
            actualDecompressed.release();
            expectedDecompressed.release();
        }
    }
}
