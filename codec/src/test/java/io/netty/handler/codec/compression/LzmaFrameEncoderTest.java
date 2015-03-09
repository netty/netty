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
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.internal.ThreadLocalRandom;
import lzma.sdk.lzma.Decoder;
import lzma.streams.LzmaInputStream;
import org.junit.Before;
import org.junit.Test;

import java.io.InputStream;

import static org.junit.Assert.*;

public class LzmaFrameEncoderTest {

    private static final ThreadLocalRandom rand;

    private static final byte[] BYTES_SMALL = new byte[256];
    private static final byte[] BYTES_LARGE = new byte[256000];

    static {
        rand = ThreadLocalRandom.current();
        rand.nextBytes(BYTES_SMALL);
        rand.nextBytes(BYTES_LARGE);
    }

    private EmbeddedChannel channel;

    @Before
    public void initChannel() {
        channel = new EmbeddedChannel(new LzmaFrameEncoder());
    }

    private static void testCompression(final EmbeddedChannel channel, final byte[] data) throws Exception {
        ByteBuf in = Unpooled.wrappedBuffer(data);
        assertTrue(channel.writeOutbound(in));
        assertTrue(channel.finish());

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
        final byte[] data = BYTES_SMALL;

        int written = 0, length = rand.nextInt(50);
        while (written + length < data.length) {
            ByteBuf in = Unpooled.wrappedBuffer(data, written, length);
            assertTrue(channel.writeOutbound(in));
            written += length;
            length = rand.nextInt(50);
        }
        ByteBuf in = Unpooled.wrappedBuffer(data, written, data.length - written);
        assertTrue(channel.writeOutbound(in));
        assertTrue(channel.finish());

        byte[] uncompressed = new byte[data.length];
        int outOffset = 0;

        ByteBuf msg;
        while ((msg = channel.readOutbound()) != null) {
            InputStream is = new ByteBufInputStream(msg);
            LzmaInputStream lzmaIs = new LzmaInputStream(is, new Decoder());
            for (;;) {
                int read = lzmaIs.read(uncompressed, outOffset, data.length - outOffset);
                if (read > 0) {
                    outOffset += read;
                } else {
                    break;
                }
            }
            assertEquals(0, is.available());
            assertEquals(-1, is.read());

            is.close();
            lzmaIs.close();
            msg.release();
        }

        assertArrayEquals(data, uncompressed);
    }

    private static byte[] uncompress(EmbeddedChannel channel, int length) throws Exception {
        CompositeByteBuf out = Unpooled.compositeBuffer();
        ByteBuf msg;
        while ((msg = channel.readOutbound()) != null) {
            out.addComponent(msg);
            out.writerIndex(out.writerIndex() + msg.readableBytes());
        }

        InputStream is = new ByteBufInputStream(out);
        LzmaInputStream lzmaIs = new LzmaInputStream(is, new Decoder());
        byte[] uncompressed = new byte[length];
        int remaining = length;
        while (remaining > 0) {
            int read = lzmaIs.read(uncompressed, length - remaining, remaining);
            if (read > 0) {
                remaining -= read;
            } else {
                break;
            }
        }

        assertEquals(0, is.available());
        assertEquals(-1, is.read());
        assertEquals(-1, lzmaIs.read());

        is.close();
        lzmaIs.close();
        out.release();

        return uncompressed;
    }
}
