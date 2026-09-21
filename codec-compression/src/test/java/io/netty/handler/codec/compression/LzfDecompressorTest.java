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
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import org.junit.jupiter.api.Test;

import static com.ning.compress.lzf.LZFChunk.BLOCK_TYPE_NON_COMPRESSED;
import static com.ning.compress.lzf.LZFChunk.BYTE_V;
import static com.ning.compress.lzf.LZFChunk.BYTE_Z;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class LzfDecompressorTest extends AbstractDecompressorTest {
    @Override
    protected ChannelHandler createCompressor() {
        return new LzfEncoder();
    }

    @Override
    protected Decompressor.AbstractDecompressorBuilder createDecompressor() {
        return LzfDecompressor.builder();
    }

    @Test
    public void testUnexpectedBlockIdentifier() throws DecompressionException {
        ByteBuf in = Unpooled.buffer();
        in.writeShort(0x1234);  // random value
        in.writeByte(BLOCK_TYPE_NON_COMPRESSED);
        in.writeShort(0);

        try (Decompressor decompressor = createDecompressor().build(ByteBufAllocator.DEFAULT)) {
            assertEquals(Decompressor.Status.NEED_INPUT, decompressor.status());
            assertThrows(DecompressionException.class, () -> decompressor.addInput(in));
        }
    }

    @Test
    public void testUnknownTypeOfChunk() throws DecompressionException {
        ByteBuf in = Unpooled.buffer();
        in.writeByte(BYTE_Z);
        in.writeByte(BYTE_V);
        in.writeByte(0xFF);   // random value
        in.writeInt(0);

        try (Decompressor decompressor = createDecompressor().build(ByteBufAllocator.DEFAULT)) {
            assertEquals(Decompressor.Status.NEED_INPUT, decompressor.status());
            assertThrows(DecompressionException.class, () -> decompressor.addInput(in));
        }
    }

    @Test
    public void testIncompleteHeader() throws DecompressionException {
        ByteBuf in = Unpooled.buffer();
        in.writeByte(BYTE_Z);
        in.writeByte(BYTE_V);

        try (Decompressor decompressor = createDecompressor().build(ByteBufAllocator.DEFAULT)) {
            assertEquals(Decompressor.Status.NEED_INPUT, decompressor.status());
            decompressor.addInput(in);
            assertEquals(Decompressor.Status.NEED_INPUT, decompressor.status());
            assertThrows(DecompressionException.class, decompressor::endOfInput);
        }
    }
}
