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
package io.netty5.handler.codec;

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class LengthFieldBasedFrameDecoderForBufferTest {

    @Test
    public void testDiscardTooLongFrame1() {
        final EmbeddedChannel channel = new EmbeddedChannel(new LengthFieldBasedFrameDecoderForBuffer(16, 0, 4));
        final Buffer buffer = prepareTestBuffer(channel.bufferAllocator());

        try {
            channel.writeInbound(buffer);
            fail();
        } catch (TooLongFrameException e) {
            // expected
        }
        assertTrue(channel.finish());

        try (final Buffer readBuffer = channel.readInbound()) {
            assertEquals(5, readBuffer.readableBytes());
            assertEquals(1, readBuffer.readInt());
            assertEquals(50, readBuffer.readByte());
        }

        assertNull(channel.readInbound());
        assertFalse(channel.finish());
    }

    @Test
    public void testDiscardTooLongFrame2() {
        final EmbeddedChannel channel = new EmbeddedChannel(new LengthFieldBasedFrameDecoderForBuffer(16, 0, 4));
        final Buffer buffer = prepareTestBuffer(channel.bufferAllocator());

        try {
            channel.writeInbound(buffer.readSplit(14));
            fail();
        } catch (TooLongFrameException e) {
            // expected
        }
        assertTrue(channel.writeInbound(buffer));
        assertTrue(channel.finish());

        try (final Buffer readBuffer = channel.readInbound()) {
            assertEquals(5, readBuffer.readableBytes());
            assertEquals(1, readBuffer.readInt());
            assertEquals(50, readBuffer.readByte());
        }

        assertNull(channel.readInbound());
        assertFalse(channel.finish());
    }

    private static Buffer prepareTestBuffer(BufferAllocator allocator) {
        final Buffer buffer = allocator.allocate(41);
        buffer.writeInt(32);
        for (byte i = 0; i < 32; i++) {
            buffer.writeByte(i);
        }
        buffer.writeInt(1);
        buffer.writeByte((byte) 50);
        return buffer;
    }

}
