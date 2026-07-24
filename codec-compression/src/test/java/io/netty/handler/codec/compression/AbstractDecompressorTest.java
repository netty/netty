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
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractDecompressorTest extends AbstractCompressionTest {
    protected static final ByteBuf WRAPPED_BYTES_SMALL = Unpooled.unreleasableBuffer(
            Unpooled.wrappedBuffer(BYTES_SMALL)).asReadOnly();
    protected static final ByteBuf WRAPPED_BYTES_LARGE = Unpooled.unreleasableBuffer(
            Unpooled.wrappedBuffer(BYTES_LARGE)).asReadOnly();

    protected abstract ChannelHandler createCompressor();

    protected abstract Decompressor.AbstractDecompressorBuilder createDecompressor();

    public ByteBuf[] smallData() {
        return data(compressToByteArray(BYTES_SMALL));
    }

    public ByteBuf[] largeData() {
        return data(compressToByteArray(BYTES_LARGE));
    }

    @ParameterizedTest
    @MethodSource("smallData")
    public void testDecompressionOfSmallChunkOfData(ByteBuf data) throws Exception {
        testDecompression(WRAPPED_BYTES_SMALL.duplicate(), data);
    }

    @ParameterizedTest
    @MethodSource("largeData")
    public void testDecompressionOfLargeChunkOfData(ByteBuf data) throws Exception {
        testDecompression(WRAPPED_BYTES_LARGE.duplicate(), data);
    }

    @ParameterizedTest
    @MethodSource("largeData")
    public void testDecompressionOfBatchedFlowOfData(ByteBuf data) throws Exception {
        testDecompressionOfBatchedFlow(WRAPPED_BYTES_LARGE.duplicate(), data);
    }

    private static ByteBuf[] data(byte[] compressedBytes) {
        ByteBuf heap = Unpooled.wrappedBuffer(compressedBytes.clone());
        ByteBuf direct = Unpooled.directBuffer(compressedBytes.length);
        direct.writeBytes(compressedBytes);
        return new ByteBuf[] { heap, direct };
    }

    protected void testDecompression(final ByteBuf expected, final ByteBuf data) throws Exception {
        try (Decompressor decompressor = createDecompressor().build(ByteBufAllocator.DEFAULT)) {
            assertEquals(Decompressor.Status.NEED_INPUT, decompressor.status());
            decompressor.addInput(data);
            ByteBuf decompressed = readDecompressed(decompressor);
            try {
                assertEquals(expected, decompressed);
            } finally {
                decompressed.release();
            }

            // test that .close is idempotent.
            assertDoesNotThrow(decompressor::close);
        }
    }

    protected void testDecompressionOfBatchedFlow(final ByteBuf expected, final ByteBuf data) throws Exception {
        try (Decompressor decompressor = createDecompressor().build(ByteBufAllocator.DEFAULT)) {
            CompositeByteBuf decompressed = ByteBufAllocator.DEFAULT.compositeBuffer();
            try {
                final int compressedLength = data.readableBytes();
                int written = 0;
                int length = rand.nextInt(100);
                while (written + length < compressedLength) {
                    feedInput(decompressor, decompressed, data.retainedSlice(written, length));
                    written += length;
                    length = rand.nextInt(100);
                }
                feedInput(decompressor, decompressed, data.retainedSlice(written, compressedLength - written));
                finishDecompression(decompressor, decompressed);
                assertEquals(expected, decompressed);
            } finally {
                decompressed.release();
                data.release();
            }
        }
    }

    private byte[] compressToByteArray(byte[] data) {
        ByteBuf compressed = compress(data);
        try {
            byte[] bytes = new byte[compressed.readableBytes()];
            compressed.getBytes(compressed.readerIndex(), bytes);
            return bytes;
        } finally {
            compressed.release();
        }
    }

    private ByteBuf compress(byte[] data) {
        EmbeddedChannel ch = new EmbeddedChannel(createCompressor());
        ch.writeOutbound(Unpooled.wrappedBuffer(data));
        assertTrue(ch.close().isSuccess());
        assertTrue(ch.finish());

        CompositeByteBuf composite = ch.alloc().compositeBuffer();
        while (true) {
            ByteBuf b = ch.readOutbound();
            if (b == null) {
                break;
            }
            composite.addComponent(true, b);
        }
        return composite;
    }

    private static ByteBuf readDecompressed(Decompressor decompressor) throws DecompressionException {
        CompositeByteBuf decompressed = ByteBufAllocator.DEFAULT.compositeBuffer();
        try {
            finishDecompression(decompressor, decompressed);
            ByteBuf result = decompressed;
            decompressed = null;
            return result;
        } finally {
            if (decompressed != null) {
                decompressed.release();
            }
        }
    }

    private static void feedInput(Decompressor decompressor, CompositeByteBuf decompressed, ByteBuf input)
            throws DecompressionException {
        assertEquals(Decompressor.Status.NEED_INPUT, decompressor.status());
        decompressor.addInput(input);
        drainOutput(decompressor, decompressed);
    }

    private static void finishDecompression(Decompressor decompressor, CompositeByteBuf decompressed)
            throws DecompressionException {
        boolean sentEof = false;
        while (true) {
            switch (decompressor.status()) {
                case NEED_INPUT:
                    assertFalse(sentEof);
                    sentEof = true;
                    decompressor.endOfInput();
                    break;
                case NEED_OUTPUT:
                    decompressed.addComponent(true, decompressor.takeOutput());
                    break;
                case COMPLETE:
                    return;
                default:
                    throw new AssertionError("Unknown status: " + decompressor.status());
            }
        }
    }

    private static void drainOutput(Decompressor decompressor, CompositeByteBuf decompressed)
            throws DecompressionException {
        while (decompressor.status() == Decompressor.Status.NEED_OUTPUT) {
            decompressed.addComponent(true, decompressor.takeOutput());
        }
    }

    @Test
    public void completeAfterEndOfInput() throws DecompressionException {
        ByteBuf compressed = compress("foo".getBytes(StandardCharsets.UTF_8));

        try (Decompressor decompressor = createDecompressor().build(ByteBufAllocator.DEFAULT)) {
            assertEquals(Decompressor.Status.NEED_INPUT, decompressor.status());
            decompressor.addInput(compressed);

            ByteBuf decompressed = readDecompressed(decompressor);
            try {
                assertEquals("foo", decompressed.toString(StandardCharsets.UTF_8));
            } finally {
                decompressed.release();
            }
        }
    }
}
