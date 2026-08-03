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

import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
@ParameterizedClass
@MethodSource("wrappers")
public class JdkZlibDecompressorTest extends AbstractDecompressorTest {
    @Parameter
    ZlibWrapper wrapper;

    static List<ZlibWrapper> wrappers() {
        return Arrays.asList(ZlibWrapper.ZLIB, ZlibWrapper.GZIP, ZlibWrapper.NONE);
    }

    @Override
    protected ChannelHandler createCompressor() {
        return new JdkZlibEncoder(wrapper);
    }

    @Override
    protected Decompressor.AbstractDecompressorBuilder createDecompressor() {
        return JdkZlibDecompressor.builder().wrapper(wrapper);
    }

    @Test
    public void testGzipCompletesAfterFooter() throws Exception {
        assumeTrue(wrapper == ZlibWrapper.GZIP);
        byte[] compressed = gzip("complete gzip".getBytes(CharsetUtil.UTF_8));
        Decompressor decompressor = JdkZlibDecompressor.builder().wrapper(ZlibWrapper.GZIP)
                .build(ByteBufAllocator.DEFAULT);
        try {
            assertEquals(Decompressor.Status.NEED_INPUT, decompressor.status());
            decompressor.addInput(Unpooled.wrappedBuffer(compressed));
            while (decompressor.status() == Decompressor.Status.NEED_OUTPUT) {
                decompressor.takeOutput().release();
            }
            assertEquals(Decompressor.Status.COMPLETE, decompressor.status());
        } finally {
            decompressor.close();
        }
    }

    @Test
    public void testCloseBeforeCompletionIsIdempotent() {
        assumeTrue(wrapper == ZlibWrapper.GZIP);
        Decompressor decompressor = JdkZlibDecompressor.builder().wrapper(ZlibWrapper.GZIP)
                .build(ByteBufAllocator.DEFAULT);
        assertEquals(Decompressor.Status.NEED_INPUT, decompressor.status());
        decompressor.addInput(Unpooled.wrappedBuffer(new byte[] { 31 }));
        assertDoesNotThrow(decompressor::close);
        assertDoesNotThrow(decompressor::close);
    }

    @Test
    public void testTruncatedZlibInputRejectedAtEndOfInput() throws Exception {
        assumeTrue(wrapper == ZlibWrapper.ZLIB);
        byte[] compressed = deflate("truncated zlib".getBytes(CharsetUtil.UTF_8));
        assertTruncated(JdkZlibDecompressor.builder().wrapper(ZlibWrapper.ZLIB)
                .build(ByteBufAllocator.DEFAULT), Arrays.copyOf(compressed, compressed.length - 1));
    }

    @Test
    public void testTruncatedGzipInputRejectedAtEndOfInput() throws Exception {
        assumeTrue(wrapper == ZlibWrapper.GZIP);
        byte[] compressed = gzip("truncated gzip".getBytes(CharsetUtil.UTF_8));
        assertTruncated(JdkZlibDecompressor.builder().wrapper(ZlibWrapper.GZIP)
                .build(ByteBufAllocator.DEFAULT), Arrays.copyOf(compressed, compressed.length - 1));
    }

    @Test
    public void testMalformedInputDoesNotLeakOutput() {
        assumeTrue(wrapper == ZlibWrapper.ZLIB);
        Decompressor decompressor = JdkZlibDecompressor.builder().wrapper(ZlibWrapper.ZLIB)
                .build(ByteBufAllocator.DEFAULT);
        try {
            assertEquals(Decompressor.Status.NEED_INPUT, decompressor.status());
            decompressor.addInput(Unpooled.wrappedBuffer(new byte[] { 0, 0, 0, 0 }));
            assertEquals(Decompressor.Status.NEED_OUTPUT, decompressor.status());
            assertThrows(DecompressionException.class, decompressor::takeOutput);
        } finally {
            decompressor.close();
        }
    }

    @Test
    public void testNegativeMaxAllocationRejected() {
        assertThrows(IllegalArgumentException.class, () -> JdkZlibDecompressor.builder().maxAllocation(-1));
    }

    @Test
    public void testDirectInputRetainedAcrossOutputChunks() throws Exception {
        assumeTrue(wrapper == ZlibWrapper.ZLIB);
        byte[] expected = new byte[8192];
        Arrays.fill(expected, (byte) 'a');
        byte[] compressed = deflate(expected);
        CompositeByteBuf output = ByteBufAllocator.DEFAULT.compositeBuffer();
        try (Decompressor decompressor = JdkZlibDecompressor.builder().maxAllocation(1)
                .build(ByteBufAllocator.DEFAULT)) {
            decompressor.status();
            decompressor.addInput(Unpooled.directBuffer(compressed.length).writeBytes(compressed));
            for (;;) {
                switch (decompressor.status()) {
                    case NEED_OUTPUT:
                        output.addComponent(true, decompressor.takeOutput());
                        break;
                    case COMPLETE:
                        byte[] actual = new byte[output.readableBytes()];
                        output.readBytes(actual);
                        assertArrayEquals(expected, actual);
                        return;
                    case NEED_INPUT:
                        fail("Decompressor requested more input before reaching the stream end");
                        break;
                    default:
                        throw new AssertionError("Unknown decompressor status");
                }
            }
        } finally {
            output.release();
        }
    }

    @Test
    public void testHighlyCompressibleStreamIsFullyDecompressed() throws Exception {
        // The tail of such a stream expands far beyond the number of remaining input bytes: the inflater pulls the
        // last input bytes into its internal state while it still has to emit data. getRemaining() is 0 by then,
        // so sizing the output buffer by it alone leaves the inflater without room to write the rest.
        byte[] expected = new byte[100000];
        Arrays.fill(expected, (byte) 'a');
        byte[] compressed = compress(expected);
        assertArrayEquals(expected, jdkDecompress(compressed));
        assertArrayEquals(expected, decompress(createDecompressor().build(ByteBufAllocator.DEFAULT), compressed));
    }

    @Test
    public void testBadSecondGzipMagicRejected() throws Exception {
        assumeTrue(wrapper == ZlibWrapper.GZIP);
        byte[] compressed = gzip("bad magic".getBytes(CharsetUtil.UTF_8));
        compressed[1] = 0;
        Decompressor decompressor = JdkZlibDecompressor.builder().wrapper(ZlibWrapper.GZIP)
                .build(ByteBufAllocator.DEFAULT);
        try {
            assertEquals(Decompressor.Status.NEED_INPUT, decompressor.status());
            assertThrows(DecompressionException.class,
                    () -> decompressor.addInput(Unpooled.wrappedBuffer(compressed)));
        } finally {
            decompressor.close();
        }
    }

    @Test
    public void testGZIPDecodeWithExtraField() throws Exception {
        assumeTrue(wrapper == ZlibWrapper.GZIP);
        byte[] data = "Hello, gzip FEXTRA world!".getBytes(CharsetUtil.UTF_8);
        byte[] extra = { 0x42, 0x43, 0x02, 0x00, (byte) 0x99, 0x00 };
        byte[] gzipWithExtra = gzipWithExtraField(data, extra);

        assertArrayEquals(data, jdkGunzip(gzipWithExtra));
        assertArrayEquals(data, decompress(
                JdkZlibDecompressor.builder().wrapper(ZlibWrapper.GZIP).build(ByteBufAllocator.DEFAULT),
                gzipWithExtra));
    }

    @Test
    public void testConcatenatedGzipFirstStreamHasExtraField() throws Exception {
        assumeTrue(wrapper == ZlibWrapper.GZIP);
        byte[] first = "first stream".getBytes(CharsetUtil.UTF_8);
        byte[] second = "second stream".getBytes(CharsetUtil.UTF_8);
        byte[] extra = { 0x42, 0x43, 0x02, 0x00, (byte) 0x99, 0x00 };
        byte[] firstGzip = gzipWithExtraField(first, extra);
        byte[] secondGzip = gzip(second);
        byte[] compressed = new byte[firstGzip.length + secondGzip.length];
        System.arraycopy(firstGzip, 0, compressed, 0, firstGzip.length);
        System.arraycopy(secondGzip, 0, compressed, firstGzip.length, secondGzip.length);

        assertArrayEquals("first streamsecond stream".getBytes(CharsetUtil.UTF_8), decompress(
                JdkZlibDecompressor.builder().wrapper(ZlibWrapper.GZIP).decompressConcatenated(true)
                        .build(ByteBufAllocator.DEFAULT), compressed));
    }

    private static byte[] decompress(Decompressor decompressor, byte[] compressed) throws Exception {
        CompositeByteBuf output = ByteBufAllocator.DEFAULT.compositeBuffer();
        try (Decompressor ignored = decompressor) {
            decompressor.status();
            decompressor.addInput(Unpooled.wrappedBuffer(compressed));
            for (;;) {
                switch (decompressor.status()) {
                    case NEED_INPUT:
                        decompressor.endOfInput();
                        break;
                    case NEED_OUTPUT:
                        output.addComponent(true, decompressor.takeOutput());
                        break;
                    case COMPLETE:
                        byte[] bytes = new byte[output.readableBytes()];
                        output.readBytes(bytes);
                        return bytes;
                    default:
                        throw new AssertionError("Unknown decompressor status");
                }
            }
        } finally {
            output.release();
        }
    }

    /**
     * Compress with the wrapper this test instance runs with, using the JDK so that the input is known-good
     * independently of netty's own encoders.
     */
    private byte[] compress(byte[] data) throws IOException {
        switch (wrapper) {
            case GZIP:
                return gzip(data);
            case ZLIB:
                return deflate(data);
            case NONE:
                return rawDeflate(data);
            default:
                throw new AssertionError("Unexpected wrapper: " + wrapper);
        }
    }

    /** Decompress with the JDK, to prove the compressed input is valid. */
    private byte[] jdkDecompress(byte[] compressed) throws IOException {
        if (wrapper == ZlibWrapper.GZIP) {
            return jdkGunzip(compressed);
        }
        Inflater inflater = new Inflater(wrapper == ZlibWrapper.NONE);
        try {
            InflaterInputStream input = new InflaterInputStream(new ByteArrayInputStream(compressed), inflater);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[256];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            input.close();
            output.close();
            return output.toByteArray();
        } finally {
            inflater.end();
        }
    }

    private static byte[] rawDeflate(byte[] data) throws IOException {
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            DeflaterOutputStream stream = new DeflaterOutputStream(output, deflater);
            stream.write(data);
            stream.close();
            return output.toByteArray();
        } finally {
            deflater.end();
        }
    }

    private static byte[] gzip(byte[] data) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        GZIPOutputStream gzip = new GZIPOutputStream(output);
        gzip.write(data);
        gzip.close();
        return output.toByteArray();
    }

    private static byte[] deflate(byte[] data) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DeflaterOutputStream deflater = new DeflaterOutputStream(output);
        deflater.write(data);
        deflater.close();
        return output.toByteArray();
    }

    private static void assertTruncated(Decompressor decompressor, byte[] compressed) throws Exception {
        try {
            assertEquals(Decompressor.Status.NEED_INPUT, decompressor.status());
            decompressor.addInput(Unpooled.wrappedBuffer(compressed));
            while (decompressor.status() == Decompressor.Status.NEED_OUTPUT) {
                decompressor.takeOutput().release();
            }
            assertEquals(Decompressor.Status.NEED_INPUT, decompressor.status());
            assertThrows(DecompressionException.class, decompressor::endOfInput);
        } finally {
            decompressor.close();
        }
    }

    private static byte[] gzipWithExtraField(byte[] data, byte[] extra) throws IOException {
        byte[] standard = gzip(data);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] header = Arrays.copyOfRange(standard, 0, 10);
        header[3] |= 0x04;
        output.write(header);
        output.write(extra.length & 0xff);
        output.write((extra.length >>> 8) & 0xff);
        output.write(extra);
        output.write(standard, 10, standard.length - 10);
        return output.toByteArray();
    }

    private static byte[] jdkGunzip(byte[] gzip) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        GZIPInputStream input = new GZIPInputStream(new ByteArrayInputStream(gzip));
        byte[] buffer = new byte[256];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        input.close();
        return output.toByteArray();
    }
}
