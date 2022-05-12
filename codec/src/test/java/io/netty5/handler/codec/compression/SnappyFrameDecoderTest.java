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
import io.netty5.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

public class SnappyFrameDecoderTest {
    private EmbeddedChannel channel;

    @BeforeEach
    public void initChannel() {
        channel = new EmbeddedChannel(new DecompressionHandler(SnappyDecompressor.newFactory()));
    }

    @AfterEach
    public void tearDown() {
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    public void testReservedUnskippableChunkTypeCausesError() {
        CompressionTestUtils.assertDecodeInputThrows(channel, new byte[] {
                0x03, 0x01, 0x00, 0x00, 0x00
        }, DecompressionException.class);
    }

    @Test
    public void testInvalidStreamIdentifierLength() {
        CompressionTestUtils.assertDecodeInputThrows(channel, new byte[] {
                -0x80, 0x05, 0x00, 0x00, 'n', 'e', 't', 't', 'y'
        }, DecompressionException.class);
    }

    @Test
    public void testInvalidStreamIdentifierValue() {
        CompressionTestUtils.assertDecodeInputThrows(channel, new byte[] {
                (byte) 0xff, 0x06, 0x00, 0x00, 's', 'n', 'e', 't', 't', 'y'
        }, DecompressionException.class);
    }

    @Test
    public void testReservedSkippableBeforeStreamIdentifier() {
        CompressionTestUtils.assertDecodeInputThrows(channel, new byte[] {
                -0x7f, 0x06, 0x00, 0x00, 's', 'n', 'e', 't', 't', 'y'
        }, DecompressionException.class);
    }

    @Test
    public void testUncompressedDataBeforeStreamIdentifier() {
        CompressionTestUtils.assertDecodeInputThrows(channel, new byte[] {
                0x01, 0x05, 0x00, 0x00, 'n', 'e', 't', 't', 'y'
        }, DecompressionException.class);
    }

    @Test
    public void testCompressedDataBeforeStreamIdentifier() {
        CompressionTestUtils.assertDecodeInputThrows(channel, new byte[] {
                0x00, 0x05, 0x00, 0x00, 'n', 'e', 't', 't', 'y'
        }, DecompressionException.class);
    }

    @Test
    public void testReservedSkippableSkipsInput() {
        Buffer in = BufferAllocator.onHeapUnpooled().copyOf(new byte[] {
           (byte) 0xff, 0x06, 0x00, 0x00, 0x73, 0x4e, 0x61, 0x50, 0x70, 0x59,
           -0x7f, 0x05, 0x00, 0x00, 'n', 'e', 't', 't', 'y'
        });

        assertFalse(channel.writeInbound(in));
        assertNull(channel.readInbound());

        assertFalse(in.readableBytes() > 0);
    }

    @Test
    public void testUncompressedDataAppendsToOut() {
        CompressionTestUtils.assertDecodeInput(channel,
                new byte[] {
                        (byte) 0xff, 0x06, 0x00, 0x00, 0x73, 0x4e, 0x61, 0x50, 0x70, 0x59,
                        0x01, 0x09, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 'n', 'e', 't', 't', 'y'
                },
                new byte[] { 'n', 'e', 't', 't', 'y' });
    }

    @Test
    public void testCompressedDataDecodesAndAppendsToOut() {
        CompressionTestUtils.assertDecodeInput(channel,
                new byte[] {
                        (byte) 0xff, 0x06, 0x00, 0x00, 0x73, 0x4e, 0x61, 0x50, 0x70, 0x59,
                        0x00, 0x0B, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x05, // preamble length
                        0x04 << 2, // literal tag + length
                        0x6e, 0x65, 0x74, 0x74, 0x79 // "netty"
                },
                new byte[] { 'n', 'e', 't', 't', 'y' });
    }

    // The following two tests differ in only the checksum provided for the literal
    // uncompressed string "netty"

    @Test
    public void testInvalidChecksumThrowsException() {
        EmbeddedChannel channel = new EmbeddedChannel(new DecompressionHandler(SnappyDecompressor.newFactory(true)));
        try {
            // checksum here is presented as 0
            CompressionTestUtils.assertDecodeInputThrows(channel, new byte[] {
                    (byte) 0xff, 0x06, 0x00, 0x00, 0x73, 0x4e, 0x61, 0x50, 0x70, 0x59,
                    0x01, 0x09, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 'n', 'e', 't', 't', 'y'
            }, DecompressionException.class);
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    public void testInvalidChecksumDoesNotThrowException() {
        EmbeddedChannel channel = new EmbeddedChannel(new DecompressionHandler(SnappyDecompressor.newFactory(true)));
        try {
            CompressionTestUtils.assertDecodeInput(channel,
                    new byte[] {
                            // checksum here is presented as a282986f (little endian)
                            (byte) 0xff, 0x06, 0x00, 0x00, 0x73, 0x4e, 0x61, 0x50, 0x70, 0x59,
                            0x01, 0x09, 0x00, 0x00, 0x6f, -0x68, 0x2e, -0x47, 'n', 'e', 't', 't', 'y'
                    },
                    new byte[] { 'n', 'e', 't', 't', 'y' });
        } finally {
            channel.finishAndReleaseAll();
        }
    }
}
