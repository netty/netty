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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    public void testGzipWithExtraField() throws Exception {
        assumeTrue(wrapper == ZlibWrapper.GZIP);
        byte[] expected = "gzip extra field".getBytes("UTF-8");
        byte[] extra = { 0x42, 0x43, 0x02, 0x00, (byte) 0x99, 0x00 };

        assertArrayEquals(expected, decompress(
                JdkZlibDecompressor.builder().wrapper(ZlibWrapper.GZIP).build(ByteBufAllocator.DEFAULT),
                gzipWithExtraField(expected, extra)));
    }

    @Test
    public void testConcatenatedGzipResetsExtraFieldLength() throws Exception {
        assumeTrue(wrapper == ZlibWrapper.GZIP);
        byte[] first = "first".getBytes("UTF-8");
        byte[] second = "second".getBytes("UTF-8");
        byte[] extra = { 0x42, 0x43, 0x02, 0x00, (byte) 0x99, 0x00 };
        byte[] firstGzip = gzipWithExtraField(first, extra);
        byte[] secondGzip = gzip(second);
        byte[] compressed = new byte[firstGzip.length + secondGzip.length];
        System.arraycopy(firstGzip, 0, compressed, 0, firstGzip.length);
        System.arraycopy(secondGzip, 0, compressed, firstGzip.length, secondGzip.length);

        assertArrayEquals("firstsecond".getBytes("UTF-8"), decompress(
                JdkZlibDecompressor.builder().wrapper(ZlibWrapper.GZIP).decompressConcatenated(true)
                        .build(ByteBufAllocator.DEFAULT), compressed));
    }

    @Test
    public void testTruncatedInputRejected() throws Exception {
        assumeTrue(wrapper == ZlibWrapper.ZLIB);
        byte[] compressed = deflate("truncated".getBytes("UTF-8"));

        assertThrows(DecompressionException.class, () -> decompress(
                JdkZlibDecompressor.builder().wrapper(ZlibWrapper.ZLIB).build(ByteBufAllocator.DEFAULT),
                Arrays.copyOf(compressed, compressed.length - 1)));
    }

    @Test
    public void testNegativeMaxAllocationRejected() {
        assumeTrue(wrapper == ZlibWrapper.GZIP);
        assertThrows(IllegalArgumentException.class, () ->
                JdkZlibDecompressor.builder().maxAllocation(-1).build(ByteBufAllocator.DEFAULT));
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
}
