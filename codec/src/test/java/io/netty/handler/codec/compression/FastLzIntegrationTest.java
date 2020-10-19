/*
 * Copyright 2014 The Netty Project
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
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class FastLzIntegrationTest extends AbstractIntegrationTest {

    public static class TestWithChecksum extends AbstractIntegrationTest {

        @Override
        protected EmbeddedChannel createEncoder() {
            return new EmbeddedChannel(new FastLzFrameEncoder(true));
        }

        @Override
        protected EmbeddedChannel createDecoder() {
            return new EmbeddedChannel(new FastLzFrameDecoder(true));
        }
    }

    public static class TestRandomChecksum extends AbstractIntegrationTest {

        @Override
        protected EmbeddedChannel createEncoder() {
            return new EmbeddedChannel(new FastLzFrameEncoder(rand.nextBoolean()));
        }

        @Override
        protected EmbeddedChannel createDecoder() {
            return new EmbeddedChannel(new FastLzFrameDecoder(rand.nextBoolean()));
        }
    }

    @Override
    protected EmbeddedChannel createEncoder() {
        return new EmbeddedChannel(new FastLzFrameEncoder(rand.nextBoolean()));
    }

    @Override
    protected EmbeddedChannel createDecoder() {
        return new EmbeddedChannel(new FastLzFrameDecoder(rand.nextBoolean()));
    }

    @Override   // test batched flow of data
    protected void testIdentity(final byte[] data) {
        final ByteBuf original = Unpooled.wrappedBuffer(data);

        int written = 0, length = rand.nextInt(100);
        while (written + length < data.length) {
            ByteBuf in = Unpooled.wrappedBuffer(data, written, length);
            encoder.writeOutbound(in);
            written += length;
            length = rand.nextInt(100);
        }
        ByteBuf in = Unpooled.wrappedBuffer(data, written, data.length - written);
        encoder.writeOutbound(in);
        encoder.finish();

        ByteBuf msg;
        final CompositeByteBuf compressed = Unpooled.compositeBuffer();
        while ((msg = encoder.readOutbound()) != null) {
            compressed.addComponent(true, msg);
        }
        assertThat(compressed, is(notNullValue()));

        final byte[] compressedArray = new byte[compressed.readableBytes()];
        compressed.readBytes(compressedArray);
        written = 0;
        length = rand.nextInt(100);
        while (written + length < compressedArray.length) {
            in = Unpooled.wrappedBuffer(compressedArray, written, length);
            decoder.writeInbound(in);
            written += length;
            length = rand.nextInt(100);
        }
        in = Unpooled.wrappedBuffer(compressedArray, written, compressedArray.length - written);
        decoder.writeInbound(in);

        assertFalse(compressed.isReadable());
        final CompositeByteBuf decompressed = Unpooled.compositeBuffer();
        while ((msg = decoder.readInbound()) != null) {
            decompressed.addComponent(true, msg);
        }
        assertEquals(original, decompressed);

        compressed.release();
        decompressed.release();
        original.release();
    }
}
