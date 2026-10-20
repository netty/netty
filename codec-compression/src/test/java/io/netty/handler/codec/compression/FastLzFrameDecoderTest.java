/*
 * Copyright 2026 The Netty Project
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

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class FastLzFrameDecoderTest {

    @Test
    public void testCompressedBlockWithEmptyPayload() {
        assertDecompressionException(new byte[] {
                'F', 'L', 'Z',
                0x01,
                0x00, 0x00,
                0x00, 0x01
        });
    }

    @Test
    public void testCompressedBlockWithTruncatedMatch() {
        assertDecompressionException(new byte[] {
                'F', 'L', 'Z',
                0x01,
                0x00, 0x03,
                0x00, 0x04,
                0x00, 'A', 0x20
        });
    }

    private static void assertDecompressionException(byte[] input) {
        EmbeddedChannel channel = new EmbeddedChannel(new FastLzFrameDecoder());
        try {
            assertThrows(DecompressionException.class, () -> channel.writeInbound(Unpooled.wrappedBuffer(input)));
        } finally {
            channel.finishAndReleaseAll();
        }
    }
}
