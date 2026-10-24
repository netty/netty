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
package io.netty.channel.unix;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ControlMessageCodecTest {

    private static final IntegerControlMessage INT = new IntegerControlMessage("INT", 41, 67);
    private static final IntegerControlMessage OTHER_INT = new IntegerControlMessage("OTHER_INT", 41, 68);
    private static final RawControlMessage RAW = new RawControlMessage("RAW", 0, 8, 12);

    static List<ControlMessageLayout> layouts() {
        return Arrays.asList(
                // Linux (64 bit): size_t cmsg_len and 8 byte alignment.
                new ControlMessageLayout(16, 0, 8, 8, 12, 16, 8),
                // Linux (32 bit): size_t cmsg_len and 4 byte alignment.
                new ControlMessageLayout(12, 0, 4, 4, 8, 12, 4),
                // macOS: socklen_t cmsg_len and 4 byte alignment.
                new ControlMessageLayout(12, 0, 4, 4, 8, 12, 4),
                // FreeBSD (64 bit): socklen_t cmsg_len and 8 byte alignment of the data.
                new ControlMessageLayout(12, 0, 4, 4, 8, 16, 8));
    }

    private static ByteBuffer newBuffer(int capacity) {
        return ByteBuffer.allocateDirect(capacity).order(ByteOrder.nativeOrder());
    }

    @ParameterizedTest
    @MethodSource("layouts")
    public void testEncode(ControlMessageLayout layout) {
        ByteBuffer data = ByteBuffer.wrap(new byte[] { 1, 2, 3, 4, 5 });
        ControlMessages messages = ControlMessages.of(INT, 0x02, RAW, data);
        int length = ControlMessageEncoder.encodedLength(layout, messages);
        assertEquals(layout.space(4) + layout.space(5), length);

        ByteBuffer buffer = newBuffer(length + 16);
        // Fill with garbage so we can check that the padding was zeroed out.
        for (int i = 0; i < buffer.capacity(); i++) {
            buffer.put(i, (byte) 0xFF);
        }
        int offset = 8;
        assertEquals(length, ControlMessageEncoder.encode(layout, messages, buffer, offset));
        // The position of the value must not be changed.
        assertEquals(0, data.position());

        // First control message
        assertEquals(layout.length(4), layout.cmsgLen(buffer, offset));
        assertEquals(41, layout.cmsgLevel(buffer, offset));
        assertEquals(67, layout.cmsgType(buffer, offset));
        assertEquals(0x02, buffer.getInt(offset + layout.dataOffset()));
        assertZero(buffer, offset + layout.dataOffset() + 4, offset + layout.space(4));

        // Second control message
        int second = offset + layout.space(4);
        assertEquals(layout.length(5), layout.cmsgLen(buffer, second));
        assertEquals(0, layout.cmsgLevel(buffer, second));
        assertEquals(8, layout.cmsgType(buffer, second));
        for (int i = 0; i < 5; i++) {
            assertEquals(i + 1, buffer.get(second + layout.dataOffset() + i));
        }
        assertZero(buffer, second + layout.dataOffset() + 5, second + layout.space(5));

        // Nothing must be written after the control messages.
        assertEquals((byte) 0xFF, buffer.get(offset + length));
    }

    @ParameterizedTest
    @MethodSource("layouts")
    public void testDecode(ControlMessageLayout layout) {
        ByteBuffer data = ByteBuffer.wrap(new byte[] { 1, 2, 3, 4, 5 });
        ControlMessages messages = ControlMessages.of(INT, 0x02, RAW, data);
        ByteBuffer buffer = newBuffer(256);
        int length = ControlMessageEncoder.encode(layout, messages, buffer, 0);

        ControlMessageDecoder decoder = new ControlMessageDecoder(layout, Arrays.asList(RAW, INT, OTHER_INT));
        assertEquals(layout.space(4) * 2 + layout.space(12), decoder.controlLength());

        ControlMessages decoded = decoder.decode(buffer, 0, length);
        assertEquals(messages, decoded);
        assertEquals(2, decoded.size());
        ByteBuffer raw = decoded.get(RAW);
        assertTrue(raw.isReadOnly());
        assertEquals(ByteOrder.nativeOrder(), raw.order());
        assertNull(decoded.get(OTHER_INT));

        // Only decode what was requested.
        decoder = new ControlMessageDecoder(layout, Collections.singletonList(INT));
        decoded = decoder.decode(buffer, 0, length);
        assertEquals(ControlMessages.of(INT, 0x02), decoded);

        decoder = new ControlMessageDecoder(layout, Collections.singletonList(OTHER_INT));
        assertSame(ControlMessages.EMPTY, decoder.decode(buffer, 0, length));
        assertSame(ControlMessages.EMPTY, decoder.decode(buffer, 0, 0));
    }

    @ParameterizedTest
    @MethodSource("layouts")
    public void testDecodeIntegerOfDifferentSizes(ControlMessageLayout layout) {
        ByteBuffer buffer = newBuffer(256);
        RawControlMessage byteRaw = new RawControlMessage("BYTE", 41, 67, 1);
        int length = ControlMessageEncoder.encode(layout,
                ControlMessages.of(byteRaw, ByteBuffer.wrap(new byte[] { (byte) 0xFE })), buffer, 0);
        ControlMessageDecoder decoder = new ControlMessageDecoder(layout, Collections.singletonList(INT));
        assertEquals(ControlMessages.of(INT, 0xFE), decoder.decode(buffer, 0, length));

        RawControlMessage shortRaw = new RawControlMessage("SHORT", 41, 67, 2);
        ByteBuffer shortValue = ByteBuffer.allocate(2).order(ByteOrder.nativeOrder());
        shortValue.putShort(0, (short) 0xFFFE);
        length = ControlMessageEncoder.encode(layout, ControlMessages.of(shortRaw, shortValue), buffer, 0);
        assertEquals(ControlMessages.of(INT, 0xFFFE), decoder.decode(buffer, 0, length));

        RawControlMessage invalidRaw = new RawControlMessage("INVALID", 41, 67, 3);
        length = ControlMessageEncoder.encode(layout,
                ControlMessages.of(invalidRaw, ByteBuffer.allocate(3)), buffer, 0);
        assertSame(ControlMessages.EMPTY, decoder.decode(buffer, 0, length));
    }

    @ParameterizedTest
    @MethodSource("layouts")
    public void testDecodeTruncated(ControlMessageLayout layout) {
        ControlMessages messages = ControlMessages.of(INT, 1, OTHER_INT, 2);
        ByteBuffer buffer = newBuffer(256);
        int length = ControlMessageEncoder.encode(layout, messages, buffer, 0);
        ControlMessageDecoder decoder = new ControlMessageDecoder(layout, Arrays.asList(INT, OTHER_INT));

        // The padding of the last control message is not needed.
        assertEquals(messages, decoder.decode(buffer, 0, layout.space(4) + layout.length(4)));
        // Only the first control message is complete.
        assertEquals(ControlMessages.of(INT, 1), decoder.decode(buffer, 0, layout.space(4) + layout.length(4) - 1));
        assertEquals(ControlMessages.of(INT, 1), decoder.decode(buffer, 0, layout.space(4)));
        // Not even the first is complete.
        assertSame(ControlMessages.EMPTY, decoder.decode(buffer, 0, layout.length(4) - 1));
        assertSame(ControlMessages.EMPTY, decoder.decode(buffer, 0, layout.headerSize() - 1));
    }

    @ParameterizedTest
    @MethodSource("layouts")
    public void testDecodeMalformed(ControlMessageLayout layout) {
        ControlMessages messages = ControlMessages.of(INT, 1, OTHER_INT, 2);
        ByteBuffer buffer = newBuffer(256);
        int length = ControlMessageEncoder.encode(layout, messages, buffer, 0);
        ControlMessageDecoder decoder = new ControlMessageDecoder(layout, Arrays.asList(INT, OTHER_INT));

        // Use a cmsg_len which is too small for the second control message. This must not result in an endless loop.
        int second = layout.space(4);
        ByteBuffer copy = copy(buffer, length);
        writeCmsgLen(layout, copy, second, 0);
        assertEquals(ControlMessages.of(INT, 1), decoder.decode(copy, 0, length));

        // Use a cmsg_len which is too big.
        copy = copy(buffer, length);
        writeCmsgLen(layout, copy, second, 1024);
        assertEquals(ControlMessages.of(INT, 1), decoder.decode(copy, 0, length));
    }

    private static ByteBuffer copy(ByteBuffer buffer, int length) {
        ByteBuffer copy = newBuffer(length);
        for (int i = 0; i < length; i++) {
            copy.put(i, buffer.get(i));
        }
        return copy;
    }

    private static void writeCmsgLen(ControlMessageLayout layout, ByteBuffer buffer, int offset, int cmsgLen) {
        // Re-use writeHeader(...) to find out where cmsg_len is stored.
        int level = layout.cmsgLevel(buffer, offset);
        int type = layout.cmsgType(buffer, offset);
        layout.writeHeader(buffer, offset, level, type, 0);
        if (layout.headerSize() == 16) {
            buffer.putLong(offset, cmsgLen);
        } else {
            buffer.putInt(offset, cmsgLen);
        }
    }

    private static void assertZero(ByteBuffer buffer, int from, int to) {
        for (int i = from; i < to; i++) {
            assertEquals(0, buffer.get(i), "index: " + i);
        }
    }
}
