/*
 * Copyright 2022 The Netty Project
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
package io.netty5.handler.codec.frame;

import io.netty5.buffer.api.Buffer;
import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.handler.codec.LengthFieldBasedFrameDecoderForBuffer;
import io.netty5.handler.codec.TooLongFrameException;
import io.netty5.util.CharsetUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LengthFieldBasedFrameDecoderForBufferTest {
    @Test
    public void testFailSlowTooLongFrameRecovery() throws Exception {
        final EmbeddedChannel channel = new EmbeddedChannel(
                new LengthFieldBasedFrameDecoderForBuffer(5, 0, 4, 0, 4, false));

        for (int i = 0; i < 2; i++) {
            assertFalse(channel.writeInbound(channel.bufferAllocator().copyOf(new byte[] { 0, 0, 0, 2 })));
            assertThrows(TooLongFrameException.class, () -> {
                channel.writeInbound(channel.bufferAllocator().copyOf(new byte[] { 0, 0 }));
            });

            assertTrue(channel.writeInbound(channel.bufferAllocator().copyOf(new byte[] { 0, 0, 0, 1, 'A' })));

            try (Buffer buffer = channel.readInbound()) {
                assertEquals("A", buffer.toString(CharsetUtil.ISO_8859_1));
            }
        }
    }

    @Test
    public void testFailFastTooLongFrameRecovery() throws Exception {
        final EmbeddedChannel channel = new EmbeddedChannel(
                new LengthFieldBasedFrameDecoderForBuffer(5, 0, 4, 0, 4));

        for (int i = 0; i < 2; i++) {
            assertThrows(TooLongFrameException.class, () -> {
                channel.writeInbound(channel.bufferAllocator().copyOf(new byte[] { 0, 0, 0, 2 }));
            });

            assertTrue(channel.writeInbound(channel.bufferAllocator().copyOf(new byte[] { 0, 0, 0, 0, 0, 1, 'A' })));

            try (Buffer buffer = channel.readInbound()) {
                assertEquals("A", buffer.toString(CharsetUtil.ISO_8859_1));
            }
        }
    }
}
