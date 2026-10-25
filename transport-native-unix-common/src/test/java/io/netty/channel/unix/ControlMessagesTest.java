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

import io.netty.buffer.Unpooled;
import io.netty.util.NetUtil;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ControlMessagesTest {

    private static final IntegerControlMessage INT_1 = new IntegerControlMessage("INT_1", 1, 1);
    private static final IntegerControlMessage INT_2 = new IntegerControlMessage("INT_2", 1, 2);
    private static final RawControlMessage RAW = new RawControlMessage("RAW", 2, 1, 8);

    @Test
    public void testEmpty() {
        assertTrue(ControlMessages.EMPTY.isEmpty());
        assertEquals(0, ControlMessages.EMPTY.size());
        assertTrue(ControlMessages.EMPTY.types().isEmpty());
        assertNull(ControlMessages.EMPTY.get(INT_1));
        assertFalse(ControlMessages.EMPTY.contains(INT_1));
        assertSame(ControlMessages.EMPTY, ControlMessages.builder().build());
        assertEquals("[]", ControlMessages.EMPTY.toString());
    }

    @Test
    public void testOf() {
        ControlMessages messages = ControlMessages.of(INT_1, 10);
        assertEquals(1, messages.size());
        assertEquals(10, messages.get(INT_1));
        assertTrue(messages.contains(INT_1));
        assertFalse(messages.contains(INT_2));
        assertNull(messages.get(INT_2));

        messages = ControlMessages.of(INT_1, 10, INT_2, 20);
        assertEquals(2, messages.size());
        assertEquals(10, messages.get(INT_1));
        assertEquals(20, messages.get(INT_2));
        assertEquals("[INT_1=10, INT_2=20]", messages.toString());
    }

    @Test
    public void testBuilder() {
        ControlMessages messages = ControlMessages.builder()
                .set(INT_1, 1)
                .set(INT_2, 2)
                .set(INT_1, 3)
                .build();
        assertEquals(2, messages.size());
        assertEquals(3, messages.get(INT_1));
        assertEquals(2, messages.get(INT_2));

        // The order is preserved.
        Iterator<ControlMessage<?>> types = messages.types().iterator();
        assertSame(INT_1, types.next());
        assertSame(INT_2, types.next());
        assertFalse(types.hasNext());

        ControlMessages removed = messages.toBuilder().remove(INT_1).build();
        assertEquals(1, removed.size());
        assertNull(removed.get(INT_1));
        assertEquals(2, removed.get(INT_2));

        // The original must not be modified.
        assertEquals(3, messages.get(INT_1));

        assertSame(ControlMessages.EMPTY, removed.toBuilder().remove(INT_2).build());
    }

    @Test
    public void testBuilderGrows() {
        ControlMessages.Builder builder = ControlMessages.builder();
        IntegerControlMessage[] types = new IntegerControlMessage[16];
        for (int i = 0; i < types.length; i++) {
            types[i] = new IntegerControlMessage("INT_" + i, 3, i);
            builder.set(types[i], i);
        }
        ControlMessages messages = builder.build();
        assertEquals(types.length, messages.size());
        for (int i = 0; i < types.length; i++) {
            assertEquals(i, messages.get(types[i]));
        }
        assertEquals(Arrays.asList(types), Arrays.asList(messages.types().toArray()));
    }

    @Test
    public void testEqualsAndHashCode() {
        ControlMessages messages1 = ControlMessages.of(INT_1, 1, INT_2, 2);
        ControlMessages messages2 = ControlMessages.of(INT_2, 2, INT_1, 1);
        assertEquals(messages1, messages2);
        assertEquals(messages1.hashCode(), messages2.hashCode());
        assertNotEquals(messages1, ControlMessages.of(INT_1, 1, INT_2, 3));
        assertNotEquals(messages1, ControlMessages.of(INT_1, 1));

        assertEquals(ControlMessages.of(RAW, ByteBuffer.wrap(new byte[] { 1, 2 })),
                ControlMessages.of(RAW, ByteBuffer.wrap(new byte[] { 1, 2 })));
    }

    @Test
    public void testValidate() {
        assertThrows(NullPointerException.class, () -> ControlMessages.of(INT_1, null));
        assertThrows(NullPointerException.class, () -> ControlMessages.of(null, 1));
        assertThrows(NullPointerException.class, () -> ControlMessages.builder().set(INT_1, null));
        assertThrows(IllegalArgumentException.class, () -> ControlMessages.of(RAW, ByteBuffer.allocate(9)));
        ControlMessages.of(RAW, ByteBuffer.allocate(8));

        assertThrows(IllegalArgumentException.class, () -> ControlMessages.of(ControlMessage.IP_TOS, 256));
        assertThrows(IllegalArgumentException.class, () -> ControlMessages.of(ControlMessage.IP_TOS, -1));
        assertThrows(IllegalArgumentException.class, () -> ControlMessages.of(ControlMessage.UDP_SEGMENT, 0));
        assertThrows(IllegalArgumentException.class, () -> ControlMessages.of(ControlMessage.UDP_SEGMENT, 65536));
        assertThrows(IllegalArgumentException.class, () -> ControlMessages.of(ControlMessage.IP_PKTINFO,
                new PacketInfo(NetUtil.LOCALHOST6, 0)));
        ControlMessages.of(ControlMessage.IP_PKTINFO, new PacketInfo(NetUtil.LOCALHOST4, 0));
        ControlMessages.of(ControlMessage.IP_PKTINFO, new PacketInfo(null, 1));
        ControlMessages.of(ControlMessage.IPV6_PKTINFO, new PacketInfo(NetUtil.LOCALHOST4, 0));
        ControlMessages.of(ControlMessage.IPV6_PKTINFO, new PacketInfo(NetUtil.LOCALHOST6, 0));
        assertThrows(IllegalArgumentException.class, () -> new PacketInfo(null, -1));
    }

    @Test
    public void testDatagramPacket() {
        ControlMessages messages = ControlMessages.of(INT_1, 1);
        InetSocketAddress recipient = new InetSocketAddress(NetUtil.LOCALHOST4, 1);
        InetSocketAddress sender = new InetSocketAddress(NetUtil.LOCALHOST4, 2);
        ControlMessageDatagramPacket packet = new ControlMessageDatagramPacket(
                Unpooled.wrappedBuffer(new byte[] { 1, 2, 3 }), recipient, sender, messages);
        try {
            assertSame(messages, packet.controlMessages());
            assertEquals(1, packet.controlMessage(INT_1));
            assertNull(packet.controlMessage(INT_2));

            ControlMessageDatagramPacket copy = packet.copy();
            try {
                assertSame(messages, copy.controlMessages());
                assertEquals(recipient, copy.recipient());
                assertEquals(sender, copy.sender());
                assertEquals(packet.content(), copy.content());
            } finally {
                copy.release();
            }

            ControlMessageDatagramPacket duplicate = packet.retainedDuplicate();
            try {
                assertSame(messages, duplicate.controlMessages());
                assertEquals(2, packet.refCnt());
            } finally {
                duplicate.release();
            }
            assertTrue(packet.toString().contains(messages.toString()));
        } finally {
            packet.release();
        }
        assertThrows(NullPointerException.class, () -> new ControlMessageDatagramPacket(
                Unpooled.EMPTY_BUFFER, recipient, null));
    }
}
