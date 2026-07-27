/*
 * Copyright 2025 The Netty Project
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
package io.netty.handler.codec.compression;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SnappyDecompressorTest extends AbstractDecompressorTest {
    @Override
    protected ChannelHandler createCompressor() {
        return new SnappyFrameEncoder();
    }

    @Override
    protected Decompressor.AbstractDecompressorBuilder createDecompressor() {
        return SnappyFrameDecompressor.builder();
    }

    @ParameterizedTest
    @CsvSource({
            "false, 0", "false, 1", "false, 2", "false, 3", "false, 4",
            "true, 0", "true, 1", "true, 2", "true, 3", "true, 4"
    })
    public void testCompressedDataWithTooShortChunkLengthThrowsException(
            boolean validateChecksums, int chunkLength) throws Exception {
        assertInvalidChunkLength(validateChecksums, (byte) 0x00, chunkLength);
    }

    @ParameterizedTest
    @CsvSource({
            "false, 0", "false, 1", "false, 2", "false, 3",
            "true, 0", "true, 1", "true, 2", "true, 3"
    })
    public void testUncompressedDataWithTooShortChunkLengthThrowsException(
            boolean validateChecksums, int chunkLength) throws Exception {
        assertInvalidChunkLength(validateChecksums, (byte) 0x01, chunkLength);
    }

    @Test
    public void testEndOfInputWithPartialFrameHeaderThrowsException() throws Exception {
        assertTruncated(Unpooled.wrappedBuffer(new byte[] { (byte) 0xff, 0x06 }));
    }

    @Test
    public void testEndOfInputWithPartialStreamIdentifierThrowsException() throws Exception {
        assertTruncated(Unpooled.wrappedBuffer(new byte[] { (byte) 0xff, 0x06, 0x00, 0x00, 's', 'N' }));
    }

    @Test
    public void testEndOfInputWithPartialUncompressedDataThrowsException() throws Exception {
        assertTruncated(Unpooled.wrappedBuffer(new byte[] {
                (byte) 0xff, 0x06, 0x00, 0x00, 's', 'N', 'a', 'P', 'p', 'Y',
                0x01, 0x09, 0x00, 0x00, 0x00, 0x00
        }));
    }

    @Test
    public void testEndOfInputWithPartialSkippableChunkThrowsException() throws Exception {
        assertTruncated(Unpooled.wrappedBuffer(new byte[] {
                (byte) 0xff, 0x06, 0x00, 0x00, 's', 'N', 'a', 'P', 'p', 'Y',
                (byte) 0x81, 0x05, 0x00, 0x00, 'n', 'e'
        }));
    }

    private static void assertInvalidChunkLength(boolean validateChecksums, byte chunkType, int chunkLength)
            throws Exception {
        try (Decompressor decompressor = SnappyFrameDecompressor.builder()
                .validateChecksums(validateChecksums).build(ByteBufAllocator.DEFAULT)) {
            final ByteBuf in = ByteBufAllocator.DEFAULT.buffer(14 + chunkLength);

            // Snappy stream identifier chunk: type 0xff, 3-byte little-endian length 6, payload "sNaPpY".
            in.writeByte(0xff);
            in.writeMediumLE(6);
            in.writeByte('s');
            in.writeByte('N');
            in.writeByte('a');
            in.writeByte('P');
            in.writeByte('p');
            in.writeByte('Y');

            // Invalid data chunk header: caller-supplied type and too-short 3-byte little-endian length.
            in.writeByte(chunkType);
            in.writeMediumLE(chunkLength);
            in.writeZero(chunkLength);

            assertEquals(Decompressor.Status.NEED_INPUT, decompressor.status());
            assertThrows(DecompressionException.class, () -> decompressor.addInput(in));
        }
    }

    private static void assertTruncated(ByteBuf in) throws Exception {
        try (Decompressor decompressor = SnappyFrameDecompressor.builder().build(ByteBufAllocator.DEFAULT)) {
            assertEquals(Decompressor.Status.NEED_INPUT, decompressor.status());
            decompressor.addInput(in);
            assertEquals(Decompressor.Status.NEED_INPUT, decompressor.status());
            assertThrows(DecompressionException.class, decompressor::endOfInput);
        }
    }
}
