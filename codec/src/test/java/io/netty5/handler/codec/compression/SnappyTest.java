/*
 * Copyright 2012 The Netty Project
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
package io.netty5.handler.codec.compression;

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;

import static io.netty5.handler.codec.compression.Snappy.calculateChecksum;
import static io.netty5.handler.codec.compression.Snappy.decodeLiteral;
import static io.netty5.handler.codec.compression.Snappy.encodeLiteral;
import static io.netty5.handler.codec.compression.Snappy.maskChecksum;
import static io.netty5.handler.codec.compression.Snappy.validateChecksum;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SnappyTest {
    private final Snappy snappy = new Snappy();

    @AfterEach
    public void resetSnappy() {
        snappy.reset();
    }

    private static void assertBufferContent(byte[] expected, Buffer actual, String msg) {
        try (Buffer expectedBuffer =  BufferAllocator.offHeapUnpooled().copyOf(expected)) {
            assertEquals(expectedBuffer, actual, msg);
        }
    }

    private void assertDecode(byte[] inBytes, byte[] expected, String msg) {
        try (Buffer in = BufferAllocator.offHeapUnpooled().copyOf(inBytes);
             Buffer out = BufferAllocator.offHeapUnpooled().allocate(5)) {
            snappy.decode(in, out);
            // "netty"
            assertBufferContent(expected, out, msg);
        }
    }

    private void assertDecodeThrows(byte[] inBytes) {
        try (Buffer in = BufferAllocator.offHeapUnpooled().copyOf(inBytes);
             Buffer out = BufferAllocator.offHeapUnpooled().allocate(5)) {
            assertThrows(DecompressionException.class, () -> snappy.decode(in, out));
        }
    }

    @Test
    public void testDecodeLiteral() {
        assertDecode(new byte[]{
                0x05, // preamble length
                0x04 << 2, // literal tag + length
                0x6e, 0x65, 0x74, 0x74, 0x79 // "netty"
        }, new byte[] {
                0x6e, 0x65, 0x74, 0x74, 0x79 // "netty"
        }, "Literal was not decoded correctly");
    }

    @Test
    public void testDecodeCopyWith1ByteOffset() {
        assertDecode(new byte[]{
                0x0a, // preamble length
                0x04 << 2, // literal tag + length
                0x6e, 0x65, 0x74, 0x74, 0x79, // "netty"
                0x01 << 2 | 0x01, // copy with 1-byte offset + length
                0x05 // offset
        }, new byte[] {
                0x6e, 0x65, 0x74, 0x74, 0x79, 0x6e, 0x65, 0x74, 0x74, 0x79 // "nettynetty" - we saved a whole byte :)

        }, "Copy was not decoded correctly");
    }

    @Test
    public void testDecodeCopyWithTinyOffset() {
        assertDecodeThrows(new byte[] {
            0x0b, // preamble length
            0x04 << 2, // literal tag + length
            0x6e, 0x65, 0x74, 0x74, 0x79, // "netty"
            0x05 << 2 | 0x01, // copy with 1-byte offset + length
            0x00 // INVALID offset (< 1)
        });
    }

    @Test
    public void testDecodeCopyWithOffsetBeforeChunk() {
        assertDecodeThrows(new byte[] {
                0x0a, // preamble length
                0x04 << 2, // literal tag + length
                0x6e, 0x65, 0x74, 0x74, 0x79, // "netty"
                0x05 << 2 | 0x01, // copy with 1-byte offset + length
                0x0b // INVALID offset (greater than chunk size)
        });
    }

    @Test
    public void testDecodeWithOverlyLongPreamble() {
        assertDecodeThrows(new byte[] {
                -0x80, -0x80, -0x80, -0x80, 0x7f, // preamble length
                0x04 << 2, // literal tag + length
                0x6e, 0x65, 0x74, 0x74, 0x79, // "netty"
        });
    }

    @Test
    public void encodeShortTextIsLiteral() {
        try (Buffer in =  BufferAllocator.onHeapUnpooled().copyOf(new byte[] {
                0x6e, 0x65, 0x74, 0x74, 0x79
        }); Buffer out = BufferAllocator.onHeapUnpooled().allocate(7);
        Buffer expected = BufferAllocator.onHeapUnpooled().copyOf(new byte[] {
                0x05, // preamble length
                0x04 << 2, // literal tag + length
                0x6e, 0x65, 0x74, 0x74, 0x79 // "netty"
        })) {
            snappy.encode(in, out, 5);
            assertEquals(expected, out, "Encoded literal was invalid");
        }
    }

    @Test
    public void encodeAndDecodeLongTextUsesCopy() {
        String srcStr = "Netty has been designed carefully with the experiences " +
                   "earned from the implementation of a lot of protocols " +
                   "such as FTP, SMTP, HTTP, and various binary and " +
                   "text-based legacy protocols";
        // The only compressibility in the above are the words:
        // "the ", "rotocols", " of ", "TP, " and "and ". So this is a literal,
        // followed by a copy followed by another literal, followed by another copy...
        byte[] expectedBytes = new byte[] {
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
        };
        try (Buffer in = BufferAllocator.onHeapUnpooled().copyOf(srcStr, StandardCharsets.US_ASCII);
            Buffer out = BufferAllocator.onHeapUnpooled().allocate(180);
            Buffer outDecoded = BufferAllocator.onHeapUnpooled().allocate(256);
            Buffer expected = BufferAllocator.onHeapUnpooled().copyOf(expectedBytes);
            Buffer expectedDecoded = BufferAllocator.onHeapUnpooled().copyOf(
                    srcStr, StandardCharsets.US_ASCII)) {
            snappy.encode(in, out, in.readableBytes());

            assertEquals(expected, out, "Encoded result was incorrect");

            // Decode
            snappy.decode(out, outDecoded);
            assertEquals(expectedDecoded, outDecoded);
        }
    }

    @Test
    public void testCalculateChecksum() {
        try (Buffer input = BufferAllocator.onHeapUnpooled().copyOf(new byte[] {
                'n', 'e', 't', 't', 'y'
        })) {
            assertEquals(maskChecksum(0xd6cb8b55L), calculateChecksum(input));
        }
    }

    @Test
    public void testMaskChecksum() {
        try (Buffer input = BufferAllocator.onHeapUnpooled().copyOf(new byte[] {
                0x00, 0x00, 0x00, 0x0f, 0x00, 0x00, 0x00, 0x00,
                0x5f, 0x68, 0x65, 0x61, 0x72, 0x74, 0x62, 0x65,
                0x61, 0x74, 0x5f,
        })) {
            assertEquals(0x44a4301f, calculateChecksum(input));
        }
    }

    @Test
    public void testValidateChecksumMatches() {
        try (Buffer input = BufferAllocator.onHeapUnpooled().copyOf(new byte[] {
                'y', 't', 't', 'e', 'n'
        })) {
            validateChecksum(maskChecksum(0x2d4d3535), input);
        }
    }

    @Test
    public void testValidateChecksumFails() {
        try (Buffer input = BufferAllocator.onHeapUnpooled().copyOf(new byte[] {
                'y', 't', 't', 'e', 'n'
        })) {
            assertThrows(DecompressionException.class, () -> validateChecksum(maskChecksum(0xd6cb8b55), input));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {
            0x11, // default
            0x100, // case 60
            0x1000, // case 61
            0x100000, // case 62
            0x1000001 // case 63
    })
    public void testEncodeLiteralAndDecodeLiteral(int len) {
        try (Buffer in = BufferAllocator.onHeapUnpooled().copyOf(new byte[len]);
            Buffer encoded = BufferAllocator.onHeapUnpooled().allocate(10);
            Buffer decoded = BufferAllocator.onHeapUnpooled().allocate(10);
            Buffer expected = BufferAllocator.onHeapUnpooled().copyOf(new byte[len])) {
            encodeLiteral(in, encoded, len);
            byte tag = encoded.readByte();
            decodeLiteral(tag, encoded, decoded);
            assertEquals(expected, decoded, "Encoded or decoded literal was incorrect");
        }
    }

    @Test
    public void testLarge2ByteLiteralLengthAndCopyOffset() {
        try (Buffer compressed = BufferAllocator.onHeapUnpooled().allocate(256);
            Buffer actualDecompressed = BufferAllocator.onHeapUnpooled().allocate(256);
             Buffer expectedDecompressed = BufferAllocator.onHeapUnpooled().allocate(256)) {
            expectedDecompressed.writeByte((byte) 0x01);
            for (int i = 0; i < 0x8000; i++) {
                expectedDecompressed.writeByte((byte) 0);
            }
            expectedDecompressed.writeByte((byte) 0x01);
            // Generate a Snappy-encoded buffer that can only be decompressed correctly if
            // the decoder treats 2-byte literal lengths and 2-byte copy offsets as unsigned values.

            // Write preamble, uncompressed content length (0x8002) encoded as varint.
            compressed.writeByte((byte) 0x82).writeByte((byte) 0x80).writeByte((byte) 0x02);

            // Write a literal consisting of 0x01 followed by 0x8000 zeroes.
            // The total length of this literal is 0x8001, which gets encoded as 0x8000 (length - 1).
            // This length was selected because the encoded form is one larger than the maximum value
            // representable using a signed 16-bit integer, and we want to assert the decoder is reading
            // the length as an unsigned value.
            compressed.writeByte((byte) (61 << 2)); // tag for LITERAL with a 2-byte length
            compressed.writeShort(Short.reverseBytes((short) 0x8000)); // length - 1
            compressed.writeByte((byte) 0x01);

            for (int i = 0; i < 0x8000; i++) {
                compressed.writeByte((byte) 0); // literal content
            }
            // Similarly, for a 2-byte copy operation we want to ensure the offset is treated as unsigned.
            // Copy the initial 0x01 which was written 0x8001 bytes back in the stream.
            compressed.writeByte((byte) 0x02); // tag for COPY with 2-byte offset, length = 1
            compressed.writeShort(Short.reverseBytes((short) 0x8001)); // offset

            snappy.decode(compressed, actualDecompressed);
            assertEquals(expectedDecompressed, actualDecompressed);
        }
    }
}
