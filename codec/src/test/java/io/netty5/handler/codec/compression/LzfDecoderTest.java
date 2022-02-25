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
package io.netty5.handler.codec.compression;

import com.ning.compress.lzf.LZFEncoder;
import io.netty5.buffer.ByteBuf;
import io.netty5.buffer.Unpooled;
import io.netty5.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import static com.ning.compress.lzf.LZFChunk.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class LzfDecoderTest extends AbstractDecoderTest {

    public LzfDecoderTest() throws Exception {
    }

    @Override
    protected EmbeddedChannel createChannel() {
        return new EmbeddedChannel(new DecompressionHandler(LzfDecompressor.newFactory()));
    }

    @Test
    public void testUnexpectedBlockIdentifier() {
        ByteBuf in = Unpooled.buffer();
        in.writeShort(0x1234);  //random value
        in.writeByte(BLOCK_TYPE_NON_COMPRESSED);
        in.writeShort(0);

        assertThrows(DecompressionException.class, () -> channel.writeInbound(in), "unexpected block identifier");
    }

    @Test
    public void testUnknownTypeOfChunk() {
        ByteBuf in = Unpooled.buffer();
        in.writeByte(BYTE_Z);
        in.writeByte(BYTE_V);
        in.writeByte(0xFF);   //random value
        in.writeInt(0);

        assertThrows(DecompressionException.class, () -> channel.writeInbound(in), "unknown type of chunk");
    }

    @Override
    protected byte[] compress(byte[] data) throws Exception {
        return LZFEncoder.encode(data);
    }
}
