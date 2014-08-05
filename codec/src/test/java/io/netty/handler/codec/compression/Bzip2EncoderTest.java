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
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;

import static io.netty.handler.codec.compression.Bzip2Constants.*;
import static org.junit.Assert.*;

public class Bzip2EncoderTest {

    private static final ThreadLocalRandom rand;

    private static final byte[] BYTES_SMALL = new byte[256];
    private static final byte[] BYTES_LARGE = new byte[MAX_BLOCK_SIZE * BASE_BLOCK_SIZE + 256];

    static {
        rand = ThreadLocalRandom.current();
        rand.nextBytes(BYTES_SMALL);
        rand.nextBytes(BYTES_LARGE);
    }

    private EmbeddedChannel channel;

    @Before
    public void initChannel() {
        channel = new EmbeddedChannel(new Bzip2Encoder(randomBlockSize()));
    }

    private static void testCompression(final EmbeddedChannel channel, final byte[] data) throws Exception {
        ByteBuf in = Unpooled.wrappedBuffer(data);
        channel.writeOutbound(in);
        channel.finish();

        byte[] uncompressed = uncompress(channel, data.length);

        assertArrayEquals(data, uncompressed);
    }

    @Test
    public void testCompressionOfSmallChunkOfData() throws Exception {
        testCompression(channel, BYTES_SMALL);
    }

    @Test
    public void testCompressionOfLargeChunkOfData() throws Exception {
        testCompression(channel, BYTES_LARGE);
    }

    @Test
    public void testCompressionOfBatchedFlowOfData() throws Exception {
        final byte[] data = BYTES_LARGE;

        int written = 0, length = rand.nextInt(100);
        while (written + length < data.length) {
            ByteBuf in = Unpooled.wrappedBuffer(data, written, length);
            channel.writeOutbound(in);
            written += length;
            length = rand.nextInt(100);
        }
        ByteBuf in = Unpooled.wrappedBuffer(data, written, data.length - written);
        channel.writeOutbound(in);
        channel.finish();

        byte[] uncompressed = uncompress(channel, data.length);

        assertArrayEquals(data, uncompressed);
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

    private static int randomBlockSize() {
        return rand.nextInt(MIN_BLOCK_SIZE, MAX_BLOCK_SIZE + 1);
    }
}
