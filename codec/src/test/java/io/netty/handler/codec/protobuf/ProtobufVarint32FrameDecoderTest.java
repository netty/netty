/*
 * Copyright 2015 The Netty Project
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
package io.netty.handler.codec.protobuf;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.TooLongFrameException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static io.netty.buffer.Unpooled.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProtobufVarint32FrameDecoderTest {

    private EmbeddedChannel ch;

    @BeforeEach
    public void setUp() {
        ch = new EmbeddedChannel(new ProtobufVarint32FrameDecoder());
    }

    @Test
    public void testTinyDecode() {
        byte[] b = { 4, 1, 1, 1, 1 };
        assertFalse(ch.writeInbound(wrappedBuffer(b, 0, 1)));
        assertNull(ch.readInbound());
        assertFalse(ch.writeInbound(wrappedBuffer(b, 1, 2)));
        assertNull(ch.readInbound());
        assertTrue(ch.writeInbound(wrappedBuffer(b, 3, b.length - 3)));

        ByteBuf expected = wrappedBuffer(new byte[] { 1, 1, 1, 1 });
        ByteBuf actual = ch.readInbound();

        assertEquals(expected, actual);
        assertFalse(ch.finish());

        expected.release();
        actual.release();
    }

    @Test
    public void testRegularDecode() {
        byte[] b = new byte[2048];
        for (int i = 2; i < 2048; i ++) {
            b[i] = 1;
        }
        b[0] = -2;
        b[1] = 15;
        assertFalse(ch.writeInbound(wrappedBuffer(b, 0, 1)));
        assertNull(ch.readInbound());
        assertFalse(ch.writeInbound(wrappedBuffer(b, 1, 127)));
        assertNull(ch.readInbound());
        assertFalse(ch.writeInbound(wrappedBuffer(b, 127, 600)));
        assertNull(ch.readInbound());
        assertTrue(ch.writeInbound(wrappedBuffer(b, 727, b.length - 727)));

        ByteBuf expected = wrappedBuffer(b, 2, b.length - 2);
        ByteBuf actual = ch.readInbound();
        assertEquals(expected, actual);
        assertFalse(ch.finish());

        expected.release();
        actual.release();
    }

    @Test
    public void testFrameWithinMaxFrameLength() {
        EmbeddedChannel channel = new EmbeddedChannel(new ProtobufVarint32FrameDecoder(10));
        byte[] b = { 4, 1, 1, 1, 1 };
        assertTrue(channel.writeInbound(wrappedBuffer(b)));

        ByteBuf expected = wrappedBuffer(new byte[] { 1, 1, 1, 1 });
        ByteBuf actual = channel.readInbound();
        assertEquals(expected, actual);
        assertFalse(channel.finish());

        expected.release();
        actual.release();
    }

    @Test
    public void testFrameExceedingMaxFrameLength() {
        final EmbeddedChannel channel = new EmbeddedChannel(new ProtobufVarint32FrameDecoder(3));
        final byte[] b = { 4, 1, 1, 1, 1 };
        assertThrows(TooLongFrameException.class, new Executable() {
            @Override
            public void execute() {
                channel.writeInbound(wrappedBuffer(b));
            }
        });
        assertNull(channel.readInbound());
        assertFalse(channel.finish());
    }

    @Test
    public void testOversizedFramePartialDiscard() {
        final EmbeddedChannel channel = new EmbeddedChannel(new ProtobufVarint32FrameDecoder(3));

        // Frame with length=10, only send length byte + 5 data bytes
        final byte[] partial = { 10, 1, 2, 3, 4, 5 };
        assertThrows(TooLongFrameException.class, new Executable() {
            @Override
            public void execute() {
                channel.writeInbound(wrappedBuffer(partial));
            }
        });

        // Send remaining 5 bytes — should be silently discarded
        byte[] remaining = { 6, 7, 8, 9, 10 };
        assertFalse(channel.writeInbound(wrappedBuffer(remaining)));
        assertNull(channel.readInbound());
        assertFalse(channel.finish());
    }

    @Test
    public void testValidFrameAfterOversized() {
        final EmbeddedChannel channel = new EmbeddedChannel(new ProtobufVarint32FrameDecoder(5));

        // Oversized frame: length=10, all data present
        final byte[] oversized = new byte[11];
        oversized[0] = 10;
        for (int i = 1; i <= 10; i++) {
            oversized[i] = (byte) i;
        }
        assertThrows(TooLongFrameException.class, new Executable() {
            @Override
            public void execute() {
                channel.writeInbound(wrappedBuffer(oversized));
            }
        });

        // Valid frame after recovery
        byte[] valid = { 3, 10, 20, 30 };
        assertTrue(channel.writeInbound(wrappedBuffer(valid)));
        ByteBuf expected = wrappedBuffer(new byte[] { 10, 20, 30 });
        ByteBuf actual = channel.readInbound();
        assertEquals(expected, actual);
        assertFalse(channel.finish());

        expected.release();
        actual.release();
    }
}
