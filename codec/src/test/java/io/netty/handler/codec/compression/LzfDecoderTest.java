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

import com.ning.compress.lzf.LZFEncoder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.internal.ThreadLocalRandom;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static com.ning.compress.lzf.LZFChunk.BYTE_Z;
import static com.ning.compress.lzf.LZFChunk.BYTE_V;
import static com.ning.compress.lzf.LZFChunk.BLOCK_TYPE_NON_COMPRESSED;
import static org.junit.Assert.*;

public class LzfDecoderTest {

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
        channel = new EmbeddedChannel(new LzfDecoder());
    }

    @Test
    public void testUnexpectedSignatureOfChunk() throws Exception {
        expected.expect(DecompressionException.class);
        expected.expectMessage("Unexpected signature of chunk");

        ByteBuf in = Unpooled.buffer();
        in.writeShort(0x1234);  //random value
        in.writeByte(BLOCK_TYPE_NON_COMPRESSED);
        in.writeShort(0);

        channel.writeInbound(in);
    }

    @Test
    public void testUnknownTypeOfChunk() throws Exception {
        expected.expect(DecompressionException.class);
        expected.expectMessage("Unknown type of chunk");

        ByteBuf in = Unpooled.buffer();
        in.writeByte(BYTE_Z);
        in.writeByte(BYTE_V);
        in.writeByte(0xFF);   //random value
        in.writeInt(0);

        channel.writeInbound(in);
    }

    private static void testDecompression(final EmbeddedChannel channel, final byte[] data) throws Exception {
        byte[] compressedArray = LZFEncoder.encode(data);
        ByteBuf compressed = Unpooled.wrappedBuffer(compressedArray);

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

        byte[] compressedArray = LZFEncoder.encode(data);
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
}
