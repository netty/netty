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
import io.netty.util.internal.ThreadLocalRandom;
import net.jpountz.lz4.LZ4BlockOutputStream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import static io.netty.handler.codec.compression.Lz4Constants.*;
import static org.junit.Assert.*;

public class Lz4FramedDecoderTest {

    private static final byte[] DATA = { 0x4C, 0x5A, 0x34, 0x42, 0x6C, 0x6F, 0x63, 0x6B,  // magic bytes
                                         0x16,                                            // token
                                         0x05, 0x00, 0x00, 0x00, 0x05, 0x00, 0x00, 0x00,  // compr. and decompr. length
                                         (byte) 0x86, (byte) 0xE4, 0x79, 0x0F,            // checksum
                                         0x4E, 0x65, 0x74, 0x74, 0x79,                    // data
                                         0x4C, 0x5A, 0x34, 0x42, 0x6C, 0x6F, 0x63, 0x6B,  // magic bytes
                                         0x16,                                            // token
                                         0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,  // last empty block
                                         0x00, 0x00, 0x00, 0x00 };

    private static final ThreadLocalRandom rand;

    private static final byte[] BYTES_SMALL = new byte[256];
    private static final byte[] BYTES_LARGE = new byte[256000];

    static {
        rand = ThreadLocalRandom.current();
        //fill arrays with compressible data
        for (int i = 0; i < BYTES_SMALL.length; i++) {
            BYTES_SMALL[i] = i % 4 != 0 ? 0 : (byte) rand.nextInt();
        }
        for (int i = 0; i < BYTES_LARGE.length; i++) {
            BYTES_LARGE[i] = i % 4 != 0 ? 0 : (byte) rand.nextInt();
        }
    }

    @Rule
    public ExpectedException expected = ExpectedException.none();

    private EmbeddedChannel channel;

    @Before
    public void initChannel() {
        channel = new EmbeddedChannel(new Lz4FramedDecoder(true));
    }

    @Test
    public void testUnexpectedBlockIdentifier() throws Exception {
        expected.expect(DecompressionException.class);
        expected.expectMessage("Unexpected block identifier");

        final byte[] data = Arrays.copyOf(DATA, DATA.length);
        data[1] = 0x00;

        ByteBuf in = Unpooled.wrappedBuffer(data);
        channel.writeInbound(in);
    }

    @Test
    public void testUnexpectedCompressedLength() throws Exception {
        expected.expect(DecompressionException.class);
        expected.expectMessage("Unexpected compressedLength");

        final byte[] data = Arrays.copyOf(DATA, DATA.length);
        data[12] = (byte) 0xFF;

        ByteBuf in = Unpooled.wrappedBuffer(data);
        channel.writeInbound(in);
    }

    @Test
    public void testUnexpectedDecompressedLength() throws Exception {
        expected.expect(DecompressionException.class);
        expected.expectMessage("Unexpected decompressedLength");

        final byte[] data = Arrays.copyOf(DATA, DATA.length);
        data[16] = (byte) 0xFF;

        ByteBuf in = Unpooled.wrappedBuffer(data);
        channel.writeInbound(in);
    }

    @Test
    public void testDecompressedAndCompressedLengthDoNotMatch() throws Exception {
        expected.expect(DecompressionException.class);
        expected.expectMessage("decompressedLength and compressedLength do not match");

        final byte[] data = Arrays.copyOf(DATA, DATA.length);
        data[13] = 0x01;

        ByteBuf in = Unpooled.wrappedBuffer(data);
        channel.writeInbound(in);
    }

    @Test
    public void testUnexpectedBlockType() throws Exception {
        expected.expect(DecompressionException.class);
        expected.expectMessage("Unexpected blockType");

        final byte[] data = Arrays.copyOf(DATA, DATA.length);
        data[8] = 0x36;

        ByteBuf in = Unpooled.wrappedBuffer(data);
        channel.writeInbound(in);
    }

    @Test
    public void testChecksumError() throws Exception {
        expected.expect(DecompressionException.class);
        expected.expectMessage("checksum error");

        final byte[] data = Arrays.copyOf(DATA, DATA.length);
        data[17] = 0x01;

        ByteBuf in = Unpooled.wrappedBuffer(data);
        channel.writeInbound(in);
    }

    @Test
    public void testChecksumErrorOfLastBlock() throws Exception {
        expected.expect(DecompressionException.class);
        expected.expectMessage("checksum error");

        final byte[] data = Arrays.copyOf(DATA, DATA.length);
        data[44] = 0x01;

        ByteBuf in = Unpooled.wrappedBuffer(data);
        try {
            channel.writeInbound(in);
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

    private static void testDecompression(final EmbeddedChannel channel, final byte[] data) throws Exception {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        LZ4BlockOutputStream lz4Os = new LZ4BlockOutputStream(os, randomBlockSize());
        lz4Os.write(data);
        lz4Os.close();

        ByteBuf compressed = Unpooled.wrappedBuffer(os.toByteArray());
        channel.writeInbound(compressed);

        ByteBuf uncompressed = readUncompressed(channel);
        ByteBuf dataBuf = Unpooled.wrappedBuffer(data);

        assertEquals(dataBuf, uncompressed);

        uncompressed.release();
        dataBuf.release();
    }

    @Test
    public void testDecompressionOfSmallChunkOfData() throws Exception {
        testDecompression(channel, BYTES_SMALL);
    }

    @Test
    public void testDecompressionOfLargeChunkOfData() throws Exception {
        testDecompression(channel, BYTES_LARGE);
    }

    @Test
    public void testDecompressionOfBatchedFlowOfData() throws Exception {
        final byte[] data = BYTES_LARGE;

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        LZ4BlockOutputStream lz4Os = new LZ4BlockOutputStream(os, randomBlockSize());
        lz4Os.write(data);
        lz4Os.close();

        final byte[] compressedArray = os.toByteArray();
        int written = 0, length = rand.nextInt(100);
        while (written + length < compressedArray.length) {
            ByteBuf compressed = Unpooled.wrappedBuffer(compressedArray, written, length);
            channel.writeInbound(compressed);
            written += length;
            length = rand.nextInt(100);
        }
        ByteBuf compressed = Unpooled.wrappedBuffer(compressedArray, written, compressedArray.length - written);
        channel.writeInbound(compressed);

        ByteBuf uncompressed = readUncompressed(channel);
        ByteBuf dataBuf = Unpooled.wrappedBuffer(data);

        assertEquals(dataBuf, uncompressed);

        uncompressed.release();
        dataBuf.release();
    }

    private static ByteBuf readUncompressed(EmbeddedChannel channel) throws Exception {
        CompositeByteBuf uncompressed = Unpooled.compositeBuffer();
        ByteBuf msg;
        while ((msg = channel.readInbound()) != null) {
            uncompressed.addComponent(msg);
            uncompressed.writerIndex(uncompressed.writerIndex() + msg.readableBytes());
        }

        return uncompressed;
    }

    private static int randomBlockSize() {
        return rand.nextInt(MIN_BLOCK_SIZE, MAX_BLOCK_SIZE + 1);
    }
}
