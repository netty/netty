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

package io.netty.handler.codec.compression;

import com.aayushatharva.brotli4j.encoder.BrotliOutputStream;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.internal.PlatformDependent;
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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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

    private static final ByteBuf WRAPPED_BYTES_SMALL = Unpooled.unreleasableBuffer(
            Unpooled.wrappedBuffer(BYTES_SMALL)).asReadOnly();
    private static final ByteBuf WRAPPED_BYTES_LARGE = Unpooled.unreleasableBuffer(
            Unpooled.wrappedBuffer(BYTES_LARGE)).asReadOnly();

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
        channel = new EmbeddedChannel(new BrotliDecoder());
    }

    @AfterEach
    public void destroyChannel() {
        if (channel != null) {
            channel.finishAndReleaseAll();
            channel = null;
        }
    }

    public static ByteBuf[] smallData() {
        ByteBuf heap = Unpooled.wrappedBuffer(COMPRESSED_BYTES_SMALL);
        ByteBuf direct = Unpooled.directBuffer(COMPRESSED_BYTES_SMALL.length);
        direct.writeBytes(COMPRESSED_BYTES_SMALL);
        return new ByteBuf[]{heap, direct};
    }

    public static ByteBuf[] largeData() {
        ByteBuf heap = Unpooled.wrappedBuffer(COMPRESSED_BYTES_LARGE);
        ByteBuf direct = Unpooled.directBuffer(COMPRESSED_BYTES_LARGE.length);
        direct.writeBytes(COMPRESSED_BYTES_LARGE);
        return new ByteBuf[]{heap, direct};
    }

    @ParameterizedTest
    @MethodSource("smallData")
    public void testDecompressionOfSmallChunkOfData(ByteBuf data) {
        testDecompression(WRAPPED_BYTES_SMALL.duplicate(), data);
    }

    @ParameterizedTest
    @MethodSource("largeData")
    public void testDecompressionOfLargeChunkOfData(ByteBuf data) {
        testDecompression(WRAPPED_BYTES_LARGE.duplicate(), data);
    }

    @ParameterizedTest
    @MethodSource("largeData")
    public void testDecompressionOfBatchedFlowOfData(ByteBuf data) {
        testDecompressionOfBatchedFlow(WRAPPED_BYTES_LARGE, data);
    }

    private void testDecompression(final ByteBuf expected, final ByteBuf data) {
        assertTrue(channel.writeInbound(data));

        ByteBuf decompressed = readDecompressed(channel);
        assertEquals(expected, decompressed);

        decompressed.release();
    }

    private void testDecompressionOfBatchedFlow(final ByteBuf expected, final ByteBuf data) {
        final int compressedLength = data.readableBytes();
        int written = 0, length = RANDOM.nextInt(100);
        while (written + length < compressedLength) {
            ByteBuf compressedBuf = data.retainedSlice(written, length);
            channel.writeInbound(compressedBuf);
            written += length;
            length = RANDOM.nextInt(100);
        }
        ByteBuf compressedBuf = data.slice(written, compressedLength - written);
        assertTrue(channel.writeInbound(compressedBuf.retain()));

        ByteBuf decompressedBuf = readDecompressed(channel);
        assertEquals(expected, decompressedBuf);

        decompressedBuf.release();
        data.release();
    }

    private static ByteBuf readDecompressed(final EmbeddedChannel channel) {
        CompositeByteBuf decompressed = Unpooled.compositeBuffer();
        ByteBuf msg;
        while ((msg = channel.readInbound()) != null) {
            decompressed.addComponent(true, msg);
        }
        return decompressed;
    }
}
