/*
 * Copyright 2021 The Netty Project
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

import com.aayushatharva.brotli4j.encoder.BrotliOutputStream;
import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.buffer.api.CompositeBuffer;
import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.util.internal.PlatformDependent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledIf(value = "isNotSupported", disabledReason = "Brotli is not supported on this platform")
public class BrotliDecoderTest {

    private static Random RANDOM;
    private static final byte[] BYTES_SMALL = new byte[256];
    private static final byte[] BYTES_LARGE = new byte[256 * 1024];
    private static byte[] COMPRESSED_BYTES_SMALL;
    private static byte[] COMPRESSED_BYTES_LARGE;

    @BeforeAll
    static void setUp() {
        try {
            Brotli.ensureAvailability();

            RANDOM = new Random();
            fillArrayWithCompressibleData(BYTES_SMALL);
            fillArrayWithCompressibleData(BYTES_LARGE);
            COMPRESSED_BYTES_SMALL = compress(BYTES_SMALL);
            COMPRESSED_BYTES_LARGE = compress(BYTES_LARGE);
        } catch (Throwable throwable) {
            throw new ExceptionInInitializerError(throwable);
        }
    }

    static boolean isNotSupported() {
        return PlatformDependent.isOsx() && "aarch_64".equals(PlatformDependent.normalizedArch());
    }

    private static void fillArrayWithCompressibleData(byte[] array) {
        for (int i = 0; i < array.length; i++) {
            array[i] = i % 4 != 0 ? 0 : (byte) RANDOM.nextInt();
        }
    }

    private static byte[] compress(byte[] data) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        BrotliOutputStream brotliOs = new BrotliOutputStream(os);
        brotliOs.write(data);
        brotliOs.close();
        return os.toByteArray();
    }

    private EmbeddedChannel channel;

    @BeforeEach
    public void initChannel() {
        channel = new EmbeddedChannel(new DecompressionHandler(BrotliDecompressor.newFactory()));
    }

    @AfterEach
    public void destroyChannel() {
        if (channel != null) {
            channel.finishAndReleaseAll();
            channel = null;
        }
    }

    public static Buffer[] smallData() {
        Buffer heap = BufferAllocator.onHeapUnpooled().copyOf(COMPRESSED_BYTES_SMALL);
        Buffer direct = BufferAllocator.offHeapUnpooled().copyOf(COMPRESSED_BYTES_SMALL);
        return new Buffer[]{ heap, direct};
    }

    public static Buffer[] largeData() {
        Buffer heap = BufferAllocator.onHeapUnpooled().copyOf(COMPRESSED_BYTES_LARGE);
        Buffer direct = BufferAllocator.offHeapUnpooled().copyOf(COMPRESSED_BYTES_LARGE);
        return new Buffer[]{ heap, direct};
    }

    @ParameterizedTest
    @MethodSource("smallData")
    public void testDecompressionOfSmallChunkOfData(Buffer data) {
        testDecompression(BYTES_SMALL, data.readSplit(data.readableBytes()));
    }

    @ParameterizedTest
    @MethodSource("largeData")
    public void testDecompressionOfLargeChunkOfData(Buffer data) {
        testDecompression(BYTES_LARGE, data.readSplit(data.readableBytes()));
    }

    @ParameterizedTest
    @MethodSource("largeData")
    public void testDecompressionOfBatchedFlowOfData(Buffer data) {
        testDecompressionOfBatchedFlow(BYTES_LARGE, data.readSplit(data.readableBytes()));
    }

    private void testDecompression(final byte[] expectedBytes, final Buffer data) {
        assertTrue(channel.writeInbound(data));

        try (Buffer decompressed = readDecompressed(channel);
            Buffer expected = channel.bufferAllocator().copyOf(expectedBytes)) {
            assertEquals(expected, decompressed);
        }
    }

    private void testDecompressionOfBatchedFlow(final byte[] expectedBytes, final Buffer data) {
        final int compressedLength = data.readableBytes();
        int written = 0, length = RANDOM.nextInt(100);
        while (written + length < compressedLength) {
            Buffer compressedBuf = data.readSplit(length);
            channel.writeInbound(compressedBuf);
            written += length;
            length = RANDOM.nextInt(100);
        }
        assertTrue(channel.writeInbound(data));

        try (Buffer decompressed = readDecompressed(channel);
             Buffer expected = channel.bufferAllocator().copyOf(expectedBytes)) {
            assertEquals(expected, decompressed);
        }
    }

    private static Buffer readDecompressed(final EmbeddedChannel channel) {
        CompositeBuffer decompressed = channel.bufferAllocator().compose();
        for (;;) {
            try (Buffer msg = channel.readInbound()) {
                if (msg == null) {
                    break;
                }
                decompressed.extendWith(msg.readSplit(msg.readableBytes()).send());
            }
        }
        return decompressed;
    }
}
