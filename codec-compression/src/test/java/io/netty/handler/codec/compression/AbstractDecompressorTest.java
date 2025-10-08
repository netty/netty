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
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class AbstractDecompressorTest {
    protected abstract ChannelHandler createCompressor();

    protected abstract Decompressor.AbstractDecompressorBuilder createDecompressor();

    private ByteBuf compress(byte[] data) {
        EmbeddedChannel ch = new EmbeddedChannel(createCompressor());
        ch.writeOutbound(Unpooled.wrappedBuffer(data));
        assertTrue(ch.close().isSuccess());
        assertTrue(ch.finish());

        CompositeByteBuf composite = ch.alloc().compositeBuffer();
        while (true) {
            ByteBuf b = ch.readOutbound();
            if (b == null) {
                break;
            }
            composite.addComponent(true, b);
        }
        return composite;
    }

    @Test
    public void completeAfterEndOfInput() {
        ByteBuf compressed = compress("foo".getBytes(StandardCharsets.UTF_8));

        try (Decompressor decompressor = createDecompressor().build(ByteBufAllocator.DEFAULT)) {
            assertEquals(Decompressor.Status.NEED_INPUT, decompressor.status());
            decompressor.addInput(compressed);

            CompositeByteBuf decompressed = ByteBufAllocator.DEFAULT.compositeBuffer();
            boolean sentEof = false;

            loop:
            while (true) {
                switch (decompressor.status()) {
                    case NEED_INPUT:
                        // once endOfInput is signalled, the decompressor should return COMPLETE.
                        assertFalse(sentEof);
                        sentEof = true;
                        decompressor.endOfInput();
                        break;
                    case NEED_OUTPUT:
                        decompressed.addComponent(true, decompressor.takeOutput());
                        break;
                    case COMPLETE:
                        break loop;
                }
            }

            assertEquals("foo", decompressed.toString(StandardCharsets.UTF_8));
            decompressed.release();
        }
    }
}
