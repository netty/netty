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
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.junit.Test;

import java.io.ByteArrayInputStream;

import static io.netty.handler.codec.compression.Bzip2Constants.*;
import static org.junit.Assert.*;

public class Bzip2EncoderTest {

    private static final ThreadLocalRandom rand;

    private static final byte[] BYTES_SMALL = new byte[256];
    private static final byte[] BYTES_LARGE = new byte[MAX_BLOCK_SIZE * BASE_BLOCK_SIZE * 2];

    static {
        rand = ThreadLocalRandom.current();
        rand.nextBytes(BYTES_SMALL);
        rand.nextBytes(BYTES_LARGE);
    }

    @Test
    public void testStreamInitialization() throws Exception {
        final EmbeddedChannel channel = new EmbeddedChannel(new Bzip2Encoder());

        ByteBuf in = Unpooled.wrappedBuffer("test".getBytes());
        channel.writeOutbound(in);

        ByteBuf out = channel.readOutbound();

        assertEquals(MAGIC_NUMBER, out.readMedium());
        assertEquals(9 + '0', out.readByte());

        out.release();
        channel.finish();
    }

    private static void testCompression(final byte[] data) throws Exception {
        for (int blockSize = MIN_BLOCK_SIZE; blockSize <= MAX_BLOCK_SIZE; blockSize++) {
            final EmbeddedChannel channel = new EmbeddedChannel(new Bzip2Encoder(blockSize));

            ByteBuf in = Unpooled.wrappedBuffer(data);
            channel.writeOutbound(in);
            channel.finish();

            byte[] uncompressed = uncompress(channel, data.length);

            assertArrayEquals(data, uncompressed);
        }
    }

    @Test
    public void testCompressionOfSmallChunkOfData() throws Exception {
        testCompression(BYTES_SMALL);
    }

    @Test
    public void testCompressionOfLargeChunkOfData() throws Exception {
        testCompression(BYTES_LARGE);
    }

    @Test
    public void testCompressionOfBatchedFlowOfData() throws Exception {
        final EmbeddedChannel channel = new EmbeddedChannel(new Bzip2Encoder(
                                rand.nextInt(MIN_BLOCK_SIZE, MAX_BLOCK_SIZE + 1)));

        int written = 0, length = rand.nextInt(100);
        while (written + length < BYTES_LARGE.length) {
            ByteBuf in = Unpooled.wrappedBuffer(BYTES_LARGE, written, length);
            channel.writeOutbound(in);
            written += length;
            length = rand.nextInt(100);
        }
        ByteBuf in = Unpooled.wrappedBuffer(BYTES_LARGE, written, BYTES_LARGE.length - written);
        channel.writeOutbound(in);
        channel.finish();

        byte[] uncompressed = uncompress(channel, BYTES_LARGE.length);

        assertArrayEquals(BYTES_LARGE, uncompressed);
    }

    private static byte[] uncompress(EmbeddedChannel channel, int length) throws Exception {
        CompositeByteBuf out = Unpooled.compositeBuffer();
        ByteBuf msg;
        while ((msg = channel.readOutbound()) != null) {
            out.addComponent(msg);
            out.writerIndex(out.writerIndex() + msg.readableBytes());
        }

        byte[] compressed = new byte[out.readableBytes()];
        out.readBytes(compressed);
        out.release();

        ByteArrayInputStream is = new ByteArrayInputStream(compressed);
        BZip2CompressorInputStream bZip2Is = new BZip2CompressorInputStream(is);
        byte[] uncompressed = new byte[length];
        int remaining = length;
        while (remaining > 0) {
            int read = bZip2Is.read(uncompressed, length - remaining, remaining);
            if (read > 0) {
                remaining -= read;
            } else {
                break;
            }
        }

        assertEquals(-1, bZip2Is.read());

        return uncompressed;
    }
}
