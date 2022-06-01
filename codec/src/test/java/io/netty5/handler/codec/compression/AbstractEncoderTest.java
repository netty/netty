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
import io.netty5.buffer.api.CompositeBuffer;
import io.netty5.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class AbstractEncoderTest extends AbstractCompressionTest {

    protected EmbeddedChannel channel;

    /**
     * Decompresses data with some external library.
     */
    protected abstract Buffer decompress(Buffer compressed, int originalLength) throws Exception;

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

    public static Buffer[] smallData() {
        Buffer heap = BufferAllocator.onHeapUnpooled().copyOf(BYTES_SMALL);
        Buffer direct = BufferAllocator.offHeapUnpooled().copyOf(BYTES_SMALL);
        return new Buffer[] {heap, direct};
    }

    public static Buffer[] largeData() {
        Buffer heap = BufferAllocator.onHeapUnpooled().copyOf(BYTES_LARGE);
        Buffer direct = BufferAllocator.offHeapUnpooled().copyOf(BYTES_LARGE);
        return new Buffer[] {heap, direct};
    }

    @ParameterizedTest
    @MethodSource("smallData")
    public void testCompressionOfSmallChunkOfData(Buffer data) throws Exception {
        testCompression(data);
    }

    @ParameterizedTest
    @MethodSource("largeData")
    public void testCompressionOfLargeChunkOfData(Buffer data) throws Exception {
        testCompression(data);
    }

    @ParameterizedTest
    @MethodSource("largeData")
    public void testCompressionOfBatchedFlowOfData(Buffer data) throws Exception {
        testCompressionOfBatchedFlow(data);
    }

    protected void testCompression(final Buffer data) throws Exception {
        final int dataLength = data.readableBytes();
        assertTrue(channel.writeOutbound(data.copy()));
        assertTrue(channel.finish());

        try (Buffer decompressed = readDecompressed(dataLength)) {
            assertEquals(data, decompressed);
        }
    }

    protected void testCompressionOfBatchedFlow(final Buffer data) throws Exception {
        try (Buffer expected = data.copy()) {
            final int dataLength = data.readableBytes();
            int written = 0, length = rand.nextInt(100);
            while (written + length < dataLength) {
                Buffer in = data.readSplit(length);
                assertTrue(channel.writeOutbound(in));
                written += length;
                length = rand.nextInt(100);
            }
            assertTrue(channel.writeOutbound(data.readSplit(dataLength - written)));
            assertTrue(channel.finish());

            try (Buffer decompressed = readDecompressed(dataLength)) {
                assertEquals(expected, decompressed);
            }
        }
    }

    protected Buffer readDecompressed(final int dataLength) throws Exception {
        CompositeBuffer compressed =  CompressionTestUtils.compose(
                BufferAllocator.onHeapUnpooled(), channel::readOutbound);
        return decompress(compressed, dataLength);
    }
}
