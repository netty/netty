/*
 * Copyright 2014 The Netty Project
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
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractDecoderTest extends AbstractCompressionTest {

    protected EmbeddedChannel channel;

    protected byte[] compressedBytesSmall;
    protected byte[] compressedBytesLarge;

    protected AbstractDecoderTest() throws Exception {
        compressedBytesSmall = compress(BYTES_SMALL);
        compressedBytesLarge = compress(BYTES_LARGE);
    }

    /**
     * Compresses data with some external library.
     */
    protected abstract byte[] compress(byte[] data) throws Exception;

    @BeforeEach
    public final void initChannel() {
        channel = createChannel();
    }

    protected abstract EmbeddedChannel createChannel();

    @AfterEach
    public void destroyChannel() {
        if (channel != null) {
            channel.finishAndReleaseAll();
            channel = null;
        }
    }

    public Buffer[] smallData() {
        Buffer heap = BufferAllocator.onHeapUnpooled().copyOf(compressedBytesSmall);
        Buffer direct = BufferAllocator.offHeapUnpooled().copyOf(compressedBytesSmall);
        return new Buffer[]{ heap, direct};
    }

    public Buffer[] largeData() {
        Buffer heap = BufferAllocator.onHeapUnpooled().copyOf(compressedBytesLarge);
        Buffer direct = BufferAllocator.offHeapUnpooled().copyOf(compressedBytesLarge);
        return new Buffer[]{ heap, direct};
    }

    @ParameterizedTest
    @MethodSource("smallData")
    public void testDecompressionOfSmallChunkOfData(Buffer data) throws Exception {
        try (Buffer in = BufferAllocator.onHeapUnpooled().copyOf(BYTES_SMALL)) {
            testDecompression(in, data);
        }
    }

    @ParameterizedTest
    @MethodSource("largeData")
    public void testDecompressionOfLargeChunkOfData(Buffer data) throws Exception {
        try (Buffer in = BufferAllocator.onHeapUnpooled().copyOf(BYTES_LARGE)) {
            testDecompression(in, data);
        }
    }

    @ParameterizedTest
    @MethodSource("largeData")
    public void testDecompressionOfBatchedFlowOfData(Buffer data) throws Exception {
        try (Buffer in = BufferAllocator.onHeapUnpooled().copyOf(BYTES_LARGE)) {
            testDecompressionOfBatchedFlow(in, data);
        }
    }

    protected void testDecompression(final Buffer expected, final Buffer data) throws Exception {
        assertTrue(channel.writeInbound(data.readSplit(data.readableBytes())));

        try (Buffer decompressed = readDecompressed(channel)) {
            assertEquals(expected, decompressed);
        }
    }

    protected void testDecompressionOfBatchedFlow(final Buffer expected, final Buffer data) throws Exception {
        final int compressedLength = data.readableBytes();
        int written = 0, length = rand.nextInt(100);
        while (written + length < compressedLength) {
            Buffer compressedBuf = data.readSplit(length);
            channel.writeInbound(compressedBuf);
            written += length;
            length = rand.nextInt(100);
        }
        Buffer compressedBuf = data.readSplit(data.readableBytes());
        assertTrue(channel.writeInbound(compressedBuf));

        try (Buffer decompressedBuf = readDecompressed(channel)) {
            assertEquals(expected, decompressedBuf);
        }
    }

    protected static Buffer readDecompressed(final EmbeddedChannel channel) {
        return CompressionTestUtils.compose(channel.bufferAllocator(), channel::readInbound);
    }

    protected static void tryDecodeAndCatchBufLeaks(final EmbeddedChannel channel, final Buffer data) {
        try {
            channel.writeInbound(data);
        } finally {
            channel.finishAndReleaseAll();
        }
    }
}
