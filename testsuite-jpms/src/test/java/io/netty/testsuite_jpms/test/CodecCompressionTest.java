/*
 * Copyright 2024 The Netty Project
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

package io.netty.testsuite_jpms.test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.compression.ZstdEncoder;
import org.junit.jupiter.api.Test;
import io.netty.handler.codec.compression.BrotliEncoder;
import io.netty.channel.embedded.EmbeddedChannel;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class CodecCompressionTest {

    @Test
    public void testBrotli4j() {
        testCompress(new BrotliEncoder());
    }

    @Test
    public void testZstd() {
        testCompress(new ZstdEncoder());
    }

    public void testCompress(MessageToByteEncoder<ByteBuf> encoder) {
        EmbeddedChannel channel = new EmbeddedChannel(encoder);
        ByteBuf data = Unpooled.copiedBuffer("some-string", StandardCharsets.UTF_8);
        assertTrue(channel.writeOutbound(data));
        assertTrue(channel.finish());
        assertEquals(0, data.readableBytes());
        int size = 0;
        for (ByteBuf chunk = channel.readOutbound(); chunk != null; chunk = channel.readOutbound()) {
            // Zstd can emit an empty buffer (flush)
            if (chunk != Unpooled.EMPTY_BUFFER) {
                size += chunk.readableBytes();
                assertTrue(chunk.release());
            }
        }
        assertTrue(size > 0);
    }
}
