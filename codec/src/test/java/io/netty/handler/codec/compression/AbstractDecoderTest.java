/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.compression;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.FromDataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(Theories.class)
public abstract class AbstractDecoderTest extends AbstractCompressionTest {

    protected static final ByteBuf WRAPPED_BYTES_SMALL;
    protected static final ByteBuf WRAPPED_BYTES_LARGE;

    static {
        WRAPPED_BYTES_SMALL = Unpooled.wrappedBuffer(BYTES_SMALL);
        WRAPPED_BYTES_LARGE = Unpooled.wrappedBuffer(BYTES_LARGE);
    }

    @Rule
    public final ExpectedException expected = ExpectedException.none();

    protected EmbeddedChannel channel;

    protected static byte[] compressedBytesSmall;
    protected static byte[] compressedBytesLarge;

    protected AbstractDecoderTest() throws Exception {
        compressedBytesSmall = compress(BYTES_SMALL);
        compressedBytesLarge = compress(BYTES_LARGE);
    }

    /**
     * Compresses data with some external library.
     */
    protected abstract byte[] compress(byte[] data) throws Exception;

    @Before
    public abstract void initChannel();

    @After
    public void destroyChannel() {
        if (channel != null) {
            channel.finishAndReleaseAll();
            channel = null;
        }
    }

    @DataPoints("smallData")
    public static ByteBuf[] smallData() {
        ByteBuf heap = Unpooled.wrappedBuffer(compressedBytesSmall);
        ByteBuf direct = Unpooled.directBuffer(compressedBytesSmall.length);
        direct.writeBytes(compressedBytesSmall);
        return new ByteBuf[] {heap, direct};
    }

    @DataPoints("largeData")
    public static ByteBuf[] largeData() {
        ByteBuf heap = Unpooled.wrappedBuffer(compressedBytesLarge);
        ByteBuf direct = Unpooled.directBuffer(compressedBytesLarge.length);
        direct.writeBytes(compressedBytesLarge);
        return new ByteBuf[] {heap, direct};
    }

    @Theory
    public void testDecompressionOfSmallChunkOfData(@FromDataPoints("smallData") ByteBuf data) throws Exception {
        testDecompression(WRAPPED_BYTES_SMALL, data);
    }

    @Theory
    public void testDecompressionOfLargeChunkOfData(@FromDataPoints("largeData") ByteBuf data) throws Exception {
        testDecompression(WRAPPED_BYTES_LARGE, data);
    }

    @Theory
    public void testDecompressionOfBatchedFlowOfData(@FromDataPoints("largeData") ByteBuf data) throws Exception {
        testDecompressionOfBatchedFlow(WRAPPED_BYTES_LARGE, data);
    }

    protected void testDecompression(final ByteBuf expected, final ByteBuf data) throws Exception {
        assertTrue(channel.writeInbound(data));

        ByteBuf decompressed = readDecompressed(channel);
        assertEquals(expected, decompressed);

        decompressed.release();
    }

    protected void testDecompressionOfBatchedFlow(final ByteBuf expected, final ByteBuf data) throws Exception {
        final int compressedLength = data.readableBytes();
        int written = 0, length = rand.nextInt(100);
        while (written + length < compressedLength) {
            ByteBuf compressedBuf = data.retainedSlice(written, length);
            channel.writeInbound(compressedBuf);
            written += length;
            length = rand.nextInt(100);
        }
        ByteBuf compressedBuf = data.slice(written, compressedLength - written);
        assertTrue(channel.writeInbound(compressedBuf.retain()));

        ByteBuf decompressedBuf = readDecompressed(channel);
        assertEquals(expected, decompressedBuf);

        decompressedBuf.release();
        data.release();
    }

    protected static ByteBuf readDecompressed(final EmbeddedChannel channel) {
        CompositeByteBuf decompressed = Unpooled.compositeBuffer();
        ByteBuf msg;
        while ((msg = channel.readInbound()) != null) {
            decompressed.addComponent(true, msg);
        }
        return decompressed;
    }

    protected static void tryDecodeAndCatchBufLeaks(final EmbeddedChannel channel, final ByteBuf data) {
        try {
            channel.writeInbound(data);
        } finally {
            for (;;) {
                ByteBuf inflated = channel.readInbound();
                if (inflated == null) {
                    break;
                }
                inflated.release();
            }
            channel.finish();
        }
    }
}
