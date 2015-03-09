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
import net.jpountz.lz4.LZ4BlockInputStream;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;

import static org.junit.Assert.*;

public class Lz4FrameEncoderTest {

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

    private EmbeddedChannel channel;

    @Before
    public void initChannel() {
        channel = new EmbeddedChannel(new Lz4FrameEncoder());
    }

    private static void testCompression(final EmbeddedChannel channel, final byte[] data) throws Exception {
        ByteBuf in = Unpooled.wrappedBuffer(data);
        channel.writeOutbound(in);
        channel.finish();

        final byte[] uncompressed = uncompress(channel, data.length);

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

        int written = 0, length = rand.nextInt(1, 100);
        while (written + length < data.length) {
            ByteBuf in = Unpooled.wrappedBuffer(data, written, length);
            channel.writeOutbound(in);
            written += length;
            length = rand.nextInt(1, 100);
        }
        ByteBuf in = Unpooled.wrappedBuffer(data, written, data.length - written);
        channel.writeOutbound(in);
        channel.finish();

        final byte[] uncompressed = uncompress(channel, data.length);

        assertArrayEquals(data, uncompressed);
    }

    private static byte[] uncompress(EmbeddedChannel channel, int originalLength) throws Exception {
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
        LZ4BlockInputStream lz4Is = new LZ4BlockInputStream(is);
        byte[] uncompressed = new byte[originalLength];
        int remaining = originalLength;
        while (remaining > 0) {
            int read = lz4Is.read(uncompressed, originalLength - remaining, remaining);
            if (read > 0) {
                remaining -= read;
            } else {
                break;
            }
        }

        return uncompressed;
    }
}
