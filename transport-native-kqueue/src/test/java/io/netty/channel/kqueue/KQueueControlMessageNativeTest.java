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
package io.netty.channel.kqueue;

import io.netty.channel.socket.SocketProtocolFamily;
import io.netty.channel.unix.ControlMessage;
import io.netty.channel.unix.ControlMessageDecoder;
import io.netty.channel.unix.ControlMessageEncoder;
import io.netty.channel.unix.ControlMessages;
import io.netty.channel.unix.PacketInfo;
import io.netty.util.NetUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the {@link ControlMessage}s with the native constants of the platform, without the need to send or receive
 * datagrams.
 */
public class KQueueControlMessageNativeTest {

    @BeforeAll
    public static void loadNative() {
        KQueue.ensureAvailability();
    }

    @Test
    public void testIpv4RoundTrip() throws Exception {
        PacketInfo info = new PacketInfo(NetUtil.LOCALHOST4, 1);
        ControlMessages messages = ControlMessages.of(ControlMessage.IP_PKTINFO, info);
        ByteBuffer buffer = encode(messages);

        Set<ControlMessage<?>> types = new HashSet<ControlMessage<?>>(
                Arrays.asList(ControlMessage.IP_TOS, ControlMessage.IP_TTL, ControlMessage.IP_PKTINFO));
        BsdSocket socket = BsdSocket.newSocketDgram(SocketProtocolFamily.INET);
        try {
            ControlMessageDecoder decoder = ControlMessageDecoder.update(socket, null, types);
            assertEquals(types, ControlMessageDecoder.types(decoder));
            assertTrue(decoder.controlLength() > 0);

            // When sending ipi_spec_dst is used for the address while ipi_addr is used when receiving, as these are
            // different only the interface index survives the round trip.
            ControlMessages decoded = decoder.decode(buffer, 0, buffer.remaining());
            PacketInfo decodedInfo = decoded.get(ControlMessage.IP_PKTINFO);
            assertEquals(info.interfaceIndex(), decodedInfo.interfaceIndex());

            // Disable again.
            assertNull(ControlMessageDecoder.update(socket, decoder, Collections.<ControlMessage<?>>emptySet()));
        } finally {
            socket.close();
        }
    }

    @Test
    public void testIpv6RoundTrip() throws Exception {
        ControlMessages messages = ControlMessages.builder()
                .set(ControlMessage.IPV6_TCLASS, 0x02)
                .set(ControlMessage.IPV6_HOPLIMIT, 7)
                .set(ControlMessage.IPV6_PKTINFO, new PacketInfo(NetUtil.LOCALHOST6, 1))
                .build();
        ByteBuffer buffer = encode(messages);

        BsdSocket socket;
        try {
            socket = BsdSocket.newSocketDgram(SocketProtocolFamily.INET6);
        } catch (Exception e) {
            // IPv6 not supported.
            return;
        }
        try {
            // Also request the IPv4 control messages, which is done on a best-effort basis for IPv6 sockets.
            Set<ControlMessage<?>> types = new HashSet<ControlMessage<?>>(messages.types());
            types.add(ControlMessage.IP_TOS);
            types.add(ControlMessage.IP_PKTINFO);
            ControlMessageDecoder decoder = ControlMessageDecoder.update(socket, null, types);
            assertEquals(types, ControlMessageDecoder.types(decoder));
            assertEquals(messages, decoder.decode(buffer, 0, buffer.remaining()));
        } finally {
            socket.close();
        }
    }

    @Test
    public void testIpv4MappedAddress() throws Exception {
        ControlMessages messages = ControlMessages.of(ControlMessage.IPV6_PKTINFO,
                new PacketInfo(NetUtil.LOCALHOST4, 1));
        ByteBuffer buffer = encode(messages);
        BsdSocket socket;
        try {
            socket = BsdSocket.newSocketDgram(SocketProtocolFamily.INET6);
        } catch (Exception e) {
            // IPv6 not supported.
            return;
        }
        try {
            ControlMessageDecoder decoder = ControlMessageDecoder.update(
                    socket, null, Collections.<ControlMessage<?>>singleton(ControlMessage.IPV6_PKTINFO));
            // IPv4-mapped IPv6 addresses are decoded as Inet4Address.
            assertEquals(messages, decoder.decode(buffer, 0, buffer.remaining()));
        } finally {
            socket.close();
        }
    }

    @Test
    public void testUnsupported() throws Exception {
        // UDP_SEGMENT and sending IP_TTL are only supported on linux.
        assertThrows(UnsupportedOperationException.class,
                () -> ControlMessageEncoder.encodedLength(ControlMessages.of(ControlMessage.UDP_SEGMENT, 1200)));
        assertThrows(UnsupportedOperationException.class,
                () -> ControlMessageEncoder.encodedLength(ControlMessages.of(ControlMessage.IP_TTL, 7)));
        BsdSocket socket = BsdSocket.newSocketDgram(SocketProtocolFamily.INET);
        try {
            assertThrows(UnsupportedOperationException.class, () -> ControlMessageDecoder.update(socket, null,
                    Collections.<ControlMessage<?>>singleton(ControlMessage.UDP_SEGMENT)));
            // IPv6 control messages are ignored if they can't be enabled for an IPv4 socket, as it will never
            // receive IPv6 datagrams anyway.
            Set<ControlMessage<?>> types = new HashSet<ControlMessage<?>>(
                    Arrays.asList(ControlMessage.IPV6_TCLASS, ControlMessage.IP_TOS));
            assertEquals(types, ControlMessageDecoder.types(ControlMessageDecoder.update(socket, null, types)));
        } finally {
            socket.close();
        }
    }

    @Test
    public void testEncodedLength() {
        int intSpace = ControlMessageEncoder.space(4);
        assertTrue(intSpace >= 4);
        assertEquals(intSpace, ControlMessageEncoder.encodedLength(
                ControlMessages.of(ControlMessage.IPV6_TCLASS, 0)));
        assertEquals(intSpace * 2, ControlMessageEncoder.encodedLength(
                ControlMessages.of(ControlMessage.IPV6_TCLASS, 0, ControlMessage.IPV6_HOPLIMIT, 1)));
        // sizeof(struct in6_pktinfo) == 20
        assertEquals(ControlMessageEncoder.space(20), ControlMessageEncoder.encodedLength(
                ControlMessages.of(ControlMessage.IPV6_PKTINFO, new PacketInfo(null, 0))));
        assertEquals(0, ControlMessageEncoder.encodedLength(ControlMessages.EMPTY));
    }

    private static ByteBuffer encode(ControlMessages messages) {
        int length = ControlMessageEncoder.encodedLength(messages);
        ByteBuffer buffer = ByteBuffer.allocateDirect(length).order(ByteOrder.nativeOrder());
        assertEquals(length, ControlMessageEncoder.encode(messages, buffer, 0));
        return buffer;
    }
}
