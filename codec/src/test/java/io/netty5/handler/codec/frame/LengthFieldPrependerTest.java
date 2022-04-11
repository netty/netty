/*
 * Copyright 2013 The Netty Project
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
import io.netty5.buffer.api.DefaultBufferAllocators;
import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.handler.codec.EncoderException;
import io.netty5.handler.codec.LengthFieldPrepender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LengthFieldPrependerTest {

    private Buffer message;

    @BeforeEach
    public void setUp() throws Exception {
        message = DefaultBufferAllocators.onHeapAllocator().copyOf(new byte[] {50});
    }

    @Test
    public void testPrependLength() throws Exception {
        final EmbeddedChannel channel = new EmbeddedChannel(new LengthFieldPrepender(4));
        assertTrue(channel.writeOutbound(message.copy()));

        try (Buffer buffer = channel.readOutbound()) {
            assertEquals(4, buffer.readableBytes());
            assertEquals(message.readableBytes(), buffer.readInt());
        }

        try (Buffer buffer = channel.readOutbound()) {
            assertEquals(message, buffer);
        }
    }

    @Test
    public void testPrependLengthIncludesLengthFieldLength() throws Exception {
        final EmbeddedChannel channel = new EmbeddedChannel(new LengthFieldPrepender(4, true));
        assertTrue(channel.writeOutbound(message.copy()));

        try (Buffer buffer = channel.readOutbound()) {
            assertEquals(4, buffer.readableBytes());
            assertEquals(message.readableBytes() + 4, buffer.readInt());
        }

        try (Buffer buffer = channel.readOutbound()) {
            assertEquals(message, buffer);
        }
    }

    @Test
    public void testPrependAdjustedLength() throws Exception {
        final EmbeddedChannel channel = new EmbeddedChannel(new LengthFieldPrepender(4, -1));
        assertTrue(channel.writeOutbound(message.copy()));

        try (Buffer buffer = channel.readOutbound()) {
            assertEquals(4, buffer.readableBytes());
            assertEquals(message.readableBytes() - 1, buffer.readInt());
        }

        try (Buffer buffer = channel.readOutbound()) {
            assertEquals(message, buffer);
        }
    }

    @Test
    public void testAdjustedLengthLessThanZero() throws Exception {
        final EmbeddedChannel channel = new EmbeddedChannel(new LengthFieldPrepender(4, -2));
        assertThrows(EncoderException.class, () -> channel.writeOutbound(message.copy()));
    }

    @Test
    public void testPrependLengthInLittleEndian() throws Exception {
        final EmbeddedChannel channel = new EmbeddedChannel(
                new LengthFieldPrepender(ByteOrder.LITTLE_ENDIAN, 4, 0, false));
        assertTrue(channel.writeOutbound(message.copy()));

        try (Buffer buffer = channel.readOutbound()) {
            assertEquals(4, buffer.readableBytes());
            assertEquals(1, buffer.readByte());
            assertEquals(0, buffer.readByte());
            assertEquals(0, buffer.readByte());
            assertEquals(0, buffer.readByte());
        }

        try (Buffer buffer = channel.readOutbound()) {
            assertEquals(message, buffer);
        }

        assertFalse(channel.finish(), "The channel must have been completely read");
    }

}
