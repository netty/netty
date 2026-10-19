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
import com.aayushatharva.brotli4j.encoder.Encoder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    private static void fillArrayWithCompressibleData(byte[] array) {
        SplittableRandom rng = new SplittableRandom(RANDOM.nextLong());
        for (int i = 0; i < array.length; i++) {
            array[i] = i % 4 != 0 ? 0 : (byte) rng.nextInt();
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
        SplittableRandom rng = new SplittableRandom(RANDOM.nextLong());
        int written = 0, length = rng.nextInt(100);
        while (written + length < compressedLength) {
            ByteBuf compressedBuf = data.retainedSlice(written, length);
            channel.writeInbound(compressedBuf);
            written += length;
            length = rng.nextInt(100);
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

    @ParameterizedTest
    @CsvSource({
            "524288, 18, 1000000",
            "524288, 18, 1",
            "100000, 10, 1000000",
            "100000, 10, 1",
            "1048576, 10, 1000000",
            "1048576, 10, 3",
    })
    public void testDecompressionOfHighlyCompressibleData(int length, int window, int chunkSize) throws Exception {
        // a long run of zeros compresses to a few bytes, and the decoder has to produce many windows of output
        // after all input was consumed
        byte[] compressed = Encoder.compress(new byte[length], new Encoder.Parameters().setWindow(window));

        long decompressedLength = 0;
        for (int i = 0; i < compressed.length; i += chunkSize) {
            int n = Math.min(chunkSize, compressed.length - i);
            channel.writeInbound(channel.alloc().buffer(n).writeBytes(compressed, i, n));
            decompressedLength += readZeros(channel);
        }
        assertFalse(channel.finish());
        assertEquals(length, decompressedLength);
    }

    @Test
    public void testDecompressionOfTruncatedHighlyCompressibleData() throws Exception {
        byte[] compressed = Encoder.compress(new byte[1048576], new Encoder.Parameters().setWindow(10));

        channel.writeInbound(Unpooled.wrappedBuffer(compressed, 0, compressed.length - 1));
        assertTrue(readZeros(channel) < 1048576);
    }

    private static long readZeros(EmbeddedChannel channel) {
        long n = 0;
        ByteBuf msg;
        while ((msg = channel.readInbound()) != null) {
            try {
                for (int i = msg.readerIndex(); i < msg.writerIndex(); i++) {
                    assertEquals(0, msg.getByte(i));
                }
                n += msg.readableBytes();
            } finally {
                msg.release();
            }
        }
        return n;
    }
}
