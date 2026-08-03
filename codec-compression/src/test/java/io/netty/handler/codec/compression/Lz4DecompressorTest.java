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

import static io.netty.handler.codec.compression.Decompressor.Status.NEED_INPUT;
import static io.netty.handler.codec.compression.Decompressor.Status.NEED_OUTPUT;
import static io.netty.handler.codec.compression.Lz4Constants.BLOCK_TYPE_COMPRESSED;
import static io.netty.handler.codec.compression.Lz4Constants.MAGIC_NUMBER;
import static io.netty.handler.codec.compression.Lz4Constants.MAX_BLOCK_SIZE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class Lz4DecompressorTest extends AbstractDecompressorTest {
    private static final int DEFAULT_MAX_DECOMPRESSED_LENGTH = 256 * 1024;

    @Override
    protected ChannelHandler createCompressor() {
        return new Lz4FrameEncoder();
    }

    @Override
    protected Decompressor.AbstractDecompressorBuilder createDecompressor() {
        return Lz4FrameDecompressor.builder();
    }

    @Test
    public void testFactoryRejectsNull() {
        assertThrows(NullPointerException.class, () -> Lz4FrameDecompressor.builder().factory(null));
    }

    @Test
    public void testDefaultMaxDecompressedLength() throws Exception {
        assertRejectsDecompressedLength(
                Lz4FrameDecompressor.builder(), DEFAULT_MAX_DECOMPRESSED_LENGTH + 1);
    }

    @Test
    public void testCustomMaxDecompressedLength() throws Exception {
        assertAcceptsDecompressedLength(
                Lz4FrameDecompressor.builder().maxDecompressedLength(DEFAULT_MAX_DECOMPRESSED_LENGTH + 1),
                DEFAULT_MAX_DECOMPRESSED_LENGTH + 1);
    }

    @Test
    public void testZeroMaxDecompressedLengthAllowsFormatMaximum() throws Exception {
        assertAcceptsDecompressedLength(
                Lz4FrameDecompressor.builder().maxDecompressedLength(0), MAX_BLOCK_SIZE);
    }

    @Test
    public void testInvalidMaxDecompressedLength() {
        assertThrows(IllegalArgumentException.class,
                () -> Lz4FrameDecompressor.builder().maxDecompressedLength(-1));
        assertThrows(IllegalArgumentException.class,
                () -> Lz4FrameDecompressor.builder().maxDecompressedLength(MAX_BLOCK_SIZE + 1));
    }

    @Test
    public void testRejectsCompressedDataBeyondDeclaredLength() throws Exception {
        ByteBuf input = Unpooled.buffer();
        input.writeLong(MAGIC_NUMBER);
        input.writeByte(BLOCK_TYPE_COMPRESSED);
        input.writeIntLE(1);
        input.writeIntLE(8);
        input.writeIntLE(0);
        input.writeByte(0x80);
        input.writeZero(8);

        try (Decompressor decompressor = createDecompressor().build(ByteBufAllocator.DEFAULT)) {
            assertEquals(NEED_INPUT, decompressor.status());
            decompressor.addInput(input);
            assertEquals(NEED_OUTPUT, decompressor.status());
            assertThrows(DecompressionException.class, () -> {
                ByteBuf output = decompressor.takeOutput();
                output.release();
            });
        }
    }

    @Test
    public void testRejectsDecompressedLengthMismatch() throws Exception {
        ByteBuf input = Unpooled.buffer();
        input.writeLong(MAGIC_NUMBER);
        input.writeByte(BLOCK_TYPE_COMPRESSED);
        input.writeIntLE(2);
        input.writeIntLE(8);
        input.writeIntLE(0);
        input.writeByte(0x10);
        input.writeByte(0);

        try (Decompressor decompressor = createDecompressor().build(ByteBufAllocator.DEFAULT)) {
            assertEquals(NEED_INPUT, decompressor.status());
            decompressor.addInput(input);
            assertEquals(NEED_OUTPUT, decompressor.status());
            assertThrows(DecompressionException.class, () -> {
                ByteBuf output = decompressor.takeOutput();
                output.release();
            });
        }
    }

    @Test
    public void testRejectsTruncatedHeader() throws Exception {
        ByteBuf input = Unpooled.buffer();
        input.writeLong(MAGIC_NUMBER);
        assertRejectsTruncatedInput(input);
    }

    @Test
    public void testRejectsTruncatedBlock() throws Exception {
        ByteBuf input = Unpooled.buffer();
        input.writeLong(MAGIC_NUMBER);
        input.writeByte(BLOCK_TYPE_COMPRESSED);
        input.writeIntLE(2);
        input.writeIntLE(8);
        input.writeIntLE(0);
        input.writeByte(0x10);
        assertRejectsTruncatedInput(input);
    }

    private void assertRejectsTruncatedInput(ByteBuf input) throws Exception {
        try (Decompressor decompressor = createDecompressor().build(ByteBufAllocator.DEFAULT)) {
            assertEquals(NEED_INPUT, decompressor.status());
            decompressor.addInput(input);
            assertEquals(NEED_INPUT, decompressor.status());
            assertThrows(DecompressionException.class, decompressor::endOfInput);
        }
    }

    private static void assertRejectsDecompressedLength(Decompressor.AbstractDecompressorBuilder builder,
                                                        int decompressedLength) throws Exception {
        try (Decompressor decompressor = builder.build(ByteBufAllocator.DEFAULT)) {
            assertEquals(NEED_INPUT, decompressor.status());
            assertThrows(DecompressionException.class,
                    () -> decompressor.addInput(compressedBlockHeader(decompressedLength)));
        }
    }

    private static void assertAcceptsDecompressedLength(Decompressor.AbstractDecompressorBuilder builder,
                                                        int decompressedLength) throws Exception {
        try (Decompressor decompressor = builder.build(ByteBufAllocator.DEFAULT)) {
            assertEquals(NEED_INPUT, decompressor.status());
            decompressor.addInput(compressedBlockHeader(decompressedLength));
            assertEquals(NEED_INPUT, decompressor.status());
        }
    }

    private static ByteBuf compressedBlockHeader(int decompressedLength) {
        int compressionLevel = 32 - Integer.numberOfLeadingZeros(decompressedLength - 1);
        ByteBuf input = Unpooled.buffer();
        input.writeLong(MAGIC_NUMBER);
        input.writeByte(BLOCK_TYPE_COMPRESSED | compressionLevel - Lz4Constants.COMPRESSION_LEVEL_BASE);
        input.writeIntLE(1);
        input.writeIntLE(decompressedLength);
        input.writeIntLE(0);
        return input;
    }
}
