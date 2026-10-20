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
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.codec.TooLongFrameException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        EmbeddedChannel channel = new EmbeddedChannel(new ProtobufVarint32FrameDecoder(3));
        byte[] b = { 4, 1, 1, 1, 1 };
        assertThrows(TooLongFrameException.class, () -> channel.writeInbound(wrappedBuffer(b)));
        assertNull(channel.readInbound());
        assertFalse(channel.finish());
    }

    @Test
    public void testOversizedFramePartialDiscard() {
        EmbeddedChannel channel = new EmbeddedChannel(new ProtobufVarint32FrameDecoder(3));

        // Frame with length=10, only send length byte + 5 data bytes
        byte[] partial = { 10, 1, 2, 3, 4, 5 };
        assertThrows(TooLongFrameException.class, () -> channel.writeInbound(wrappedBuffer(partial)));

        // Send remaining 5 bytes — should be silently discarded
        byte[] remaining = { 6, 7, 8, 9, 10 };
        assertFalse(channel.writeInbound(wrappedBuffer(remaining)));
        assertNull(channel.readInbound());
        assertFalse(channel.finish());
    }

    @Test
    public void testValidFrameAfterOversized() {
        EmbeddedChannel channel = new EmbeddedChannel(new ProtobufVarint32FrameDecoder(5));

        // Oversized frame: length=10, all data present
        byte[] oversized = new byte[11];
        oversized[0] = 10;
        for (int i = 1; i <= 10; i++) {
            oversized[i] = (byte) i;
        }
        assertThrows(TooLongFrameException.class, () -> channel.writeInbound(wrappedBuffer(oversized)));

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

    @Test
    public void testLengthPrefixSplitAtEveryPosition() {
        // Cover 1, 2, 3 and 4 byte prefixes, including lengths whose low 21 bits are all zero (multiples of 2 MiB).
        // For these the partial varint read of the first 3 prefix bytes evaluates to 0.
        int[] lengths = {
                1, 127,
                128, 16383,
                16384, 2097151,
                2097152, 2097152 + 5, 3 * 1024 * 1024, 4 * 1024 * 1024
        };
        for (int length : lengths) {
            ByteBuf wire = buffer();
            ProtobufVarint32LengthFieldPrepender.writeRawVarint32(wire, length);
            int prefixLength = wire.readableBytes();
            byte[] payload = new byte[length];
            // Mark the last byte so a frame that starts or ends at the wrong position is detected.
            payload[length - 1] = 1;
            wire.writeBytes(payload);
            ByteBuf expected = wire.slice(prefixLength, length);

            for (int split = 1; split <= prefixLength; split++) {
                EmbeddedChannel channel = new EmbeddedChannel(new ProtobufVarint32FrameDecoder());
                String message = "length: " + length + ", split after: " + split;
                assertFalse(channel.writeInbound(wire.retainedSlice(0, split)), message);
                assertTrue(channel.writeInbound(wire.retainedSlice(split, wire.readableBytes() - split)), message);

                ByteBuf actual = channel.readInbound();
                try {
                    assertEquals(length, actual.readableBytes(), message);
                    assertEquals(expected, actual, message);
                } finally {
                    actual.release();
                }
                assertNull(channel.readInbound(), message);
                assertFalse(channel.finish(), message);
            }
            wire.release();
        }
    }

    @Test
    public void testFiveByteLengthPrefixSplitAtEveryPosition() {
        // 0x10000000 needs a 5 byte prefix: 80 80 80 80 01. Use a small maxFrameLength so we do not need to
        // allocate the whole frame, the TooLongFrameException proves the complete prefix was decoded.
        byte[] prefix = { (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x01 };
        for (int split = 1; split < prefix.length; split++) {
            EmbeddedChannel channel = new EmbeddedChannel(new ProtobufVarint32FrameDecoder(1024));
            assertFalse(channel.writeInbound(wrappedBuffer(prefix, 0, split)), "split after: " + split);
            final ByteBuf remaining = wrappedBuffer(prefix, split, prefix.length - split);
            TooLongFrameException e = assertThrows(TooLongFrameException.class,
                    () -> channel.writeInbound(remaining), "split after: " + split);
            assertTrue(e.getMessage().contains(String.valueOf(0x10000000)), e.getMessage());
            assertFalse(channel.finish());
        }
    }

    @Test
    public void testReadRawVarint32WaitsForIncompleteVarint() {
        byte[][] incomplete = {
                { (byte) 0x80 },
                { (byte) 0x80, (byte) 0x80 },
                { (byte) 0x80, (byte) 0x80, (byte) 0x80 },
                { (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80 }
        };
        for (byte[] bytes : incomplete) {
            ByteBuf buf = wrappedBuffer(bytes);
            ProtobufVarint32FrameDecoder.readRawVarint32(buf);
            assertEquals(0, buf.readerIndex(), "readerIndex must not move for " + bytes.length + " incomplete bytes");
            buf.release();
        }
    }

    @Test
    public void testReadRawVarint32RejectsSixByteVarint() {
        ByteBuf buf = wrappedBuffer(new byte[] { (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80 });
        assertThrows(CorruptedFrameException.class, () -> ProtobufVarint32FrameDecoder.readRawVarint32(buf));
        buf.release();
    }
}
