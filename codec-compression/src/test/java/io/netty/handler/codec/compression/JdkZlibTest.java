/*
 * Copyright 2013 The Netty Project
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

import io.netty.buffer.AbstractByteBufAllocator;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import org.apache.commons.compress.utils.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class JdkZlibTest extends ZlibTest {

    @Override
    protected ZlibEncoder createEncoder(ZlibWrapper wrapper) {
        return new JdkZlibEncoder(wrapper);
    }

    @Override
    protected ZlibDecoder createDecoder(ZlibWrapper wrapper, int maxAllocation) {
        return new JdkZlibDecoder(wrapper, maxAllocation);
    }

    @Test
    @Override
    public void testZLIB_OR_NONE3() throws Exception {
        assertThrows(DecompressionException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                JdkZlibTest.super.testZLIB_OR_NONE3();
            }
        });
    }

    @Test
    // verifies backward compatibility
    public void testConcatenatedStreamsReadFirstOnly() throws IOException {
        EmbeddedChannel chDecoderGZip = new EmbeddedChannel(createDecoder(ZlibWrapper.GZIP));

        try (InputStream resourceAsStream = getClass().getResourceAsStream("/multiple.gz")) {
            byte[] bytes = IOUtils.toByteArray(resourceAsStream);

            assertTrue(chDecoderGZip.writeInbound(Unpooled.copiedBuffer(bytes)));
            Queue<Object> messages = chDecoderGZip.inboundMessages();
            assertEquals(1, messages.size());

            ByteBuf msg = (ByteBuf) messages.poll();
            assertEquals("a", msg.toString(CharsetUtil.UTF_8));
            ReferenceCountUtil.release(msg);
        } finally {
            assertFalse(chDecoderGZip.finish());
            chDecoderGZip.close();
        }
    }

    @Test
    public void testConcatenatedStreamsReadFully() throws IOException {
        EmbeddedChannel chDecoderGZip = new EmbeddedChannel(new JdkZlibDecoder(true, 0));

        try (InputStream resourceAsStream = getClass().getResourceAsStream("/multiple.gz")) {
            byte[] bytes = IOUtils.toByteArray(resourceAsStream);

            assertTrue(chDecoderGZip.writeInbound(Unpooled.copiedBuffer(bytes)));
            Queue<Object> messages = chDecoderGZip.inboundMessages();
            assertEquals(2, messages.size());

            for (String s : Arrays.asList("a", "b")) {
                ByteBuf msg = (ByteBuf) messages.poll();
                assertEquals(s, msg.toString(CharsetUtil.UTF_8));
                ReferenceCountUtil.release(msg);
            }
        } finally {
            assertFalse(chDecoderGZip.finish());
            chDecoderGZip.close();
        }
    }

    @Test
    public void testConcatenatedStreamsReadFullyWhenFragmented() throws IOException {
        EmbeddedChannel chDecoderGZip = new EmbeddedChannel(new JdkZlibDecoder(true, 0));

        try (InputStream resourceAsStream = getClass().getResourceAsStream("/multiple.gz")) {
            byte[] bytes = IOUtils.toByteArray(resourceAsStream);

            // Let's feed the input byte by byte to simulate fragmentation.
            ByteBuf buf = Unpooled.copiedBuffer(bytes);
            boolean written = false;
            while (buf.isReadable()) {
                written |= chDecoderGZip.writeInbound(buf.readRetainedSlice(1));
            }
            buf.release();

            assertTrue(written);
            Queue<Object> messages = chDecoderGZip.inboundMessages();
            assertEquals(2, messages.size());

            for (String s : Arrays.asList("a", "b")) {
                ByteBuf msg = (ByteBuf) messages.poll();
                assertEquals(s, msg.toString(CharsetUtil.UTF_8));
                ReferenceCountUtil.release(msg);
            }
        } finally {
            assertFalse(chDecoderGZip.finish());
            chDecoderGZip.close();
        }
    }

    @Test
    public void testGZIPDecodeWithExtraField() throws Exception {
        byte[] data = "Hello, gzip FEXTRA world!".getBytes(CharsetUtil.UTF_8);
        byte[] extra = { 0x42, 0x43, 0x02, 0x00, (byte) 0x99, 0x00 }; // 6 arbitrary bytes
        byte[] gzipWithExtra = gzipWithExtraField(data, extra);

        // Sanity-check the crafted stream is a valid gzip by decoding it with the JDK itself.
        assertArrayEquals(data, jdkGunzip(gzipWithExtra));

        // netty must decode it identically; before the FEXTRA fix the extra bytes were never
        // skipped, corrupting the deflate stream and throwing DecompressionException.
        EmbeddedChannel ch = new EmbeddedChannel(createDecoder(ZlibWrapper.GZIP));
        try {
            assertTrue(ch.writeInbound(Unpooled.copiedBuffer(gzipWithExtra)));
            ByteBuf out = ch.readInbound();
            assertEquals(new String(data, CharsetUtil.UTF_8), out.toString(CharsetUtil.UTF_8));
            out.release();
        } finally {
            assertFalse(ch.finish());
            ch.close();
        }
    }

    @Test
    public void testConcatenatedGzipFirstStreamHasExtraField() throws Exception {
        // Regression guard: with concatenated streams, an FEXTRA field on the first stream must not
        // leak its XLEN into the second stream's header parsing. The xlen state has to be reset
        // between streams; otherwise the second stream (which has no extra field) would skip
        // xlen bytes that are actually deflate data and fail to decode.
        String firstText = "first stream";
        String secondText = "second stream";
        byte[] first = firstText.getBytes(CharsetUtil.UTF_8);
        byte[] second = secondText.getBytes(CharsetUtil.UTF_8);
        byte[] extra = { 0x42, 0x43, 0x02, 0x00, (byte) 0x99, 0x00 };

        byte[] firstGz = gzipWithExtraField(first, extra); // first stream HAS an extra field
        byte[] secondGz = gzip(second);                    // second stream has none
        byte[] both = new byte[firstGz.length + secondGz.length];
        System.arraycopy(firstGz, 0, both, 0, firstGz.length);
        System.arraycopy(secondGz, 0, both, firstGz.length, secondGz.length);

        EmbeddedChannel ch = new EmbeddedChannel(new JdkZlibDecoder(true, 0));
        try {
            assertTrue(ch.writeInbound(Unpooled.copiedBuffer(both)));
            ByteArrayOutputStream decoded = new ByteArrayOutputStream();
            ByteBuf msg;
            while ((msg = ch.readInbound()) != null) {
                msg.readBytes(decoded, msg.readableBytes());
                msg.release();
            }
            assertArrayEquals((firstText + secondText).getBytes(CharsetUtil.UTF_8),
                    decoded.toByteArray());
            decoded.close();
        } finally {
            assertFalse(ch.finish());
        }
    }

    @ParameterizedTest
    @MethodSource("highlyCompressibleStreams")
    public void testHighlyCompressibleStreamIsFullyDecoded(ZlibWrapper wrapper, int size, int chunkSize)
            throws Exception {
        // Every output buffer is sized from the number of remaining input bytes. That is generous at
        // ordinary compression ratios, but at a high ratio the inflater can pull the last input byte into
        // its internal state while it still holds decoded data, and then the proposed size is zero and
        // inflate(...) cannot make progress. The tail stayed inside the inflater and was dropped together
        // with the input, without any exception, so a peer simply saw a short body.
        assertFullyDecoded(wrapper, size, chunkSize);
    }

    private static Object[][] highlyCompressibleStreams() {
        // The two payload shapes reach the two places a buffer is taken. 65537 bytes written in one go
        // crosses the threshold at which the decoder forwards the buffer downstream and then allocates a
        // fresh one; 33333 bytes arriving in 7-byte reads stays under that threshold but starts every
        // decode(...) call with a fresh buffer. Only ZlibWrapper.NONE actually lost bytes before the fix,
        // because the ZLIB and GZIP trailers keep bytes in the input buffer while output is still pending.
        return new Object[][] {
                { ZlibWrapper.NONE, 65537, Integer.MAX_VALUE },
                { ZlibWrapper.NONE, 33333, 7 },
                { ZlibWrapper.ZLIB, 65537, Integer.MAX_VALUE },
                { ZlibWrapper.ZLIB, 33333, 7 },
                { ZlibWrapper.GZIP, 65537, Integer.MAX_VALUE },
                { ZlibWrapper.GZIP, 33333, 7 },
        };
    }

    private void assertFullyDecoded(ZlibWrapper wrapper, int size, int chunkSize) throws Exception {
        byte[] data = new byte[size];
        Arrays.fill(data, (byte) 'a');
        byte[] compressed = compress(wrapper, data);

        // Cross-check the fixture with the JDK itself, so a failure below cannot be blamed on it.
        assertArrayEquals(data, jdkInflate(wrapper, compressed));

        EmbeddedChannel ch = new EmbeddedChannel(createDecoder(wrapper));
        ByteArrayOutputStream decoded = new ByteArrayOutputStream();
        try {
            ByteBuf in = Unpooled.wrappedBuffer(compressed);
            try {
                while (in.isReadable()) {
                    ch.writeInbound(in.readRetainedSlice(Math.min(chunkSize, in.readableBytes())));
                }
            } finally {
                in.release();
            }
            ch.finish();

            ByteBuf msg;
            while ((msg = ch.readInbound()) != null) {
                msg.readBytes(decoded, msg.readableBytes());
                msg.release();
            }
            assertArrayEquals(data, decoded.toByteArray());
        } finally {
            decoded.close();
            ch.close();
        }
    }

    private static byte[] compress(ZlibWrapper wrapper, byte[] data) throws IOException {
        if (wrapper == ZlibWrapper.GZIP) {
            return gzip(data);
        }
        ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
        DeflaterOutputStream deflaterOut = new DeflaterOutputStream(bytesOut,
                new Deflater(Deflater.BEST_COMPRESSION, wrapper == ZlibWrapper.NONE));
        deflaterOut.write(data);
        deflaterOut.close();
        return bytesOut.toByteArray();
    }

    private static byte[] jdkInflate(ZlibWrapper wrapper, byte[] compressed) throws IOException {
        if (wrapper == ZlibWrapper.GZIP) {
            return jdkGunzip(compressed);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            InflaterInputStream in = new InflaterInputStream(new ByteArrayInputStream(compressed),
                    new Inflater(wrapper == ZlibWrapper.NONE));
            try {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
            } finally {
                in.close();
            }
            return out.toByteArray();
        } finally {
            out.close();
        }
    }

    private static byte[] gzip(byte[] data) throws IOException {
        ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
        GZIPOutputStream gzipOut = new GZIPOutputStream(bytesOut);
        gzipOut.write(data);
        gzipOut.close();
        return bytesOut.toByteArray();
    }

    private static byte[] gzipWithExtraField(byte[] data, byte[] extra) throws IOException {
        // GZIPOutputStream never emits an FEXTRA field, so build a standard gzip stream first ...
        byte[] standard = gzip(data);

        // ... then splice in an FEXTRA field by hand: set the FEXTRA flag in FLG and insert
        // XLEN (2 bytes, little-endian per RFC 1952) followed by the extra subfield, right after
        // the fixed 10-byte gzip header.
        ByteArrayOutputStream withExtra = new ByteArrayOutputStream();
        try {
            byte[] header = Arrays.copyOfRange(standard, 0, 10);
            header[3] |= 0x04; // FLG.FEXTRA
            withExtra.write(header);
            withExtra.write(extra.length & 0xff);          // XLEN low byte (little-endian)
            withExtra.write((extra.length >>> 8) & 0xff);  // XLEN high byte
            withExtra.write(extra);
            withExtra.write(standard, 10, standard.length - 10);
            return withExtra.toByteArray();
        } finally {
            withExtra.close();
        }
    }

    private static byte[] jdkGunzip(byte[] gz) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            java.util.zip.GZIPInputStream in =
                    new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(gz));
            try {
                byte[] buf = new byte[256];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
            } finally {
                in.close();
            }
            return out.toByteArray();
        } finally {
            out.close();
        }
    }

    @Test
    public void testDecodeWithHeaderFollowingFooter() throws Exception {
        byte[] bytes = new byte[1024];
        ThreadLocalRandom.current().nextBytes(bytes);
        ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
        GZIPOutputStream out = new GZIPOutputStream(bytesOut);
        out.write(bytes);
        out.close();

        byte[] compressed = bytesOut.toByteArray();
        ByteBuf buffer = Unpooled.buffer().writeBytes(compressed).writeBytes(compressed);
        EmbeddedChannel channel = new EmbeddedChannel(new JdkZlibDecoder(ZlibWrapper.GZIP, true, 0));
        // Write it into the Channel in a way that we were able to decompress the first data completely but not the
        // whole footer.
        assertTrue(channel.writeInbound(buffer.readRetainedSlice(compressed.length - 1)));
        assertTrue(channel.writeInbound(buffer));
        assertTrue(channel.finish());

        ByteBuf uncompressedBuffer = Unpooled.wrappedBuffer(bytes);
        ByteBuf read = channel.readInbound();
        assertEquals(uncompressedBuffer, read);
        read.release();

        read = channel.readInbound();
        assertEquals(uncompressedBuffer, read);
        read.release();

        assertNull(channel.readInbound());
        uncompressedBuffer.release();
    }

    @Test
    public void testLargeEncode() throws Exception {
        // construct a 128M buffer out of many times the same 1M buffer :)
        byte[] smallArray = new byte[1024 * 1024];
        byte[][] arrayOfArrays = new byte[128][];
        Arrays.fill(arrayOfArrays, smallArray);
        ByteBuf bigBuffer = Unpooled.wrappedBuffer(arrayOfArrays);

        EmbeddedChannel channel = new EmbeddedChannel(new JdkZlibEncoder(ZlibWrapper.NONE));
        channel.config().setAllocator(new LimitedByteBufAllocator(channel.alloc()));
        assertTrue(channel.writeOutbound(bigBuffer));
        assertTrue(channel.finish());
        channel.checkException();
        assertTrue(channel.releaseOutbound());
    }

    @Test
    void testAllowDefaultCompression() {
        assertDoesNotThrow(() -> new JdkZlibEncoder(Deflater.DEFAULT_COMPRESSION));
    }

    @Test
    public void testGzipFooterValidationSuccess() throws Exception {
        byte[] data = "hello gzip world".getBytes(CharsetUtil.UTF_8);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        GZIPOutputStream gzipOut = new GZIPOutputStream(baos);
        gzipOut.write(data);
        gzipOut.close();

        byte[] compressed = baos.toByteArray();
        EmbeddedChannel ch = new EmbeddedChannel(new JdkZlibDecoder(ZlibWrapper.GZIP, Integer.MAX_VALUE));
        assertTrue(ch.writeInbound(Unpooled.wrappedBuffer(compressed)));
        ByteBuf result = ch.readInbound();
        assertEquals(Unpooled.wrappedBuffer(data), result);
        result.release();
        assertFalse(ch.finish());
    }

    @Test
    public void testGzipFooterCrcMismatchThrows() throws Exception {
        byte[] data = "corrupted gzip".getBytes(CharsetUtil.UTF_8);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        GZIPOutputStream gzipOut = new GZIPOutputStream(baos);
        gzipOut.write(data);
        gzipOut.close();

        byte[] compressed = baos.toByteArray();
        // Corrupt the CRC
        compressed[compressed.length - 8] ^= 0xFF;

        EmbeddedChannel ch = new EmbeddedChannel(new JdkZlibDecoder(ZlibWrapper.GZIP, Integer.MAX_VALUE));
        assertThrows(DecompressionException.class, () -> {
            ch.writeInbound(Unpooled.wrappedBuffer(compressed));
        });
        ch.finishAndReleaseAll();
    }

    @Test
    public void testGzipFooterISizeMismatchThrows() throws Exception {
        byte[] data = "wrong size".getBytes(CharsetUtil.UTF_8);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        GZIPOutputStream gzipOut = new GZIPOutputStream(baos);
        gzipOut.write(data);
        gzipOut.close();

        byte[] compressed = baos.toByteArray();
        // Corrupt the ISIZE
        compressed[compressed.length - 4] ^= 0xFF;

        EmbeddedChannel ch = new EmbeddedChannel(new JdkZlibDecoder(ZlibWrapper.GZIP, Integer.MAX_VALUE));
        assertThrows(DecompressionException.class, () -> {
            ch.writeInbound(Unpooled.wrappedBuffer(compressed));
        });
        ch.finishAndReleaseAll();
    }

    @Test
    public void testRoundTripCompressionGzipContentMatch() throws Exception {
        byte[] input = "Hello, Netty gzip roundtrip!".getBytes(CharsetUtil.UTF_8);

        EmbeddedChannel encoder = new EmbeddedChannel(new JdkZlibEncoder(ZlibWrapper.GZIP));
        assertTrue(encoder.writeOutbound(Unpooled.wrappedBuffer(input)));
        assertTrue(encoder.finish());

        ByteBuf compressed = Unpooled.buffer();
        for (;;) {
            ByteBuf part = encoder.readOutbound();
            if (part == null) {
                break;
            }
            compressed.writeBytes(part);
            part.release();
        }

        EmbeddedChannel decoder = new EmbeddedChannel(new JdkZlibDecoder(ZlibWrapper.GZIP, Integer.MAX_VALUE));
        assertTrue(decoder.writeInbound(compressed));
        ByteBuf result = decoder.readInbound();
        assertEquals(Unpooled.wrappedBuffer(input), result);
        result.release();
        decoder.finish();
    }

    @Test
    public void testFragmentedGzipStreamStillYieldsCorrectContent() throws Exception {
        String text = "Fragmented input stream for GZIP!";
        byte[] input = text.getBytes(CharsetUtil.UTF_8);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        GZIPOutputStream gzipOut = new GZIPOutputStream(baos);
        gzipOut.write(input);
        gzipOut.close();
        byte[] compressed = baos.toByteArray();

        EmbeddedChannel decoder = new EmbeddedChannel(new JdkZlibDecoder(ZlibWrapper.GZIP, Integer.MAX_VALUE));

        for (byte b : compressed) {
            decoder.writeInbound(Unpooled.wrappedBuffer(new byte[]{ b }));
        }
        assertTrue(decoder.finish());

        ByteBuf result = Unpooled.buffer();
        ByteBuf chunk;
        while ((chunk = decoder.readInbound()) != null) {
            result.writeBytes(chunk);
            chunk.release();
        }

        assertEquals(text, result.toString(CharsetUtil.UTF_8));
        result.release();
    }

    @Test
    public void testMultipleConcatenatedGzipMessagesDecompressedIndividually() throws Exception {
        String first = "first message";
        String second = "second message";

        byte[] c1 = gzipCompress(first.getBytes(CharsetUtil.UTF_8));
        byte[] c2 = gzipCompress(second.getBytes(CharsetUtil.UTF_8));
        byte[] combined = new byte[c1.length + c2.length];
        System.arraycopy(c1, 0, combined, 0, c1.length);
        System.arraycopy(c2, 0, combined, c1.length, c2.length);

        EmbeddedChannel decoder = new EmbeddedChannel(new JdkZlibDecoder(true, 0));
        assertTrue(decoder.writeInbound(Unpooled.wrappedBuffer(combined)));

        ByteBuf m1 = decoder.readInbound();
        ByteBuf m2 = decoder.readInbound();

        assertEquals(first, m1.toString(CharsetUtil.UTF_8));
        assertEquals(second, m2.toString(CharsetUtil.UTF_8));

        m1.release();
        m2.release();
        decoder.finish();
    }

    private static byte[] gzipCompress(byte[] input) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        GZIPOutputStream gzipOut = new GZIPOutputStream(baos);
        gzipOut.write(input);
        gzipOut.close();
        return baos.toByteArray();
    }

    /**
     * Allocator that will limit buffer capacity to 1M.
     */
    private static final class LimitedByteBufAllocator extends AbstractByteBufAllocator {
        private static final int MAX = 1024 * 1024;

        private final ByteBufAllocator wrapped;

        LimitedByteBufAllocator(ByteBufAllocator wrapped) {
            this.wrapped = wrapped;
        }

        @Override
        public boolean isDirectBufferPooled() {
            return wrapped.isDirectBufferPooled();
        }

        @Override
        protected ByteBuf newHeapBuffer(int initialCapacity, int maxCapacity) {
            return wrapped.heapBuffer(initialCapacity, Math.min(maxCapacity, MAX));
        }

        @Override
        protected ByteBuf newDirectBuffer(int initialCapacity, int maxCapacity) {
            return wrapped.directBuffer(initialCapacity, Math.min(maxCapacity, MAX));
        }
    }
}
