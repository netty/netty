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
package io.netty.channel.socket;

import com.google.common.primitives.Ints;
import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static io.netty.buffer.Unpooled.wrappedBuffer;
import static io.netty.channel.socket.InternetProtocolFamily.IPv4;
import static io.netty.channel.socket.Tun4Packet.INET4_FLAGS_DONT_FRAGMENT_MASK;
import static io.netty.channel.socket.Tun4Packet.INET4_FLAGS_MORE_FRAGMENTS_MASK;
import static io.netty.channel.socket.Tun4Packet.INET4_TYPE_OF_SERVICE_DELAY_MASK;
import static io.netty.channel.socket.Tun4Packet.INET4_TYPE_OF_SERVICE_PRECEDENCE_ROUTINE;
import static io.netty.channel.socket.Tun4Packet.INET4_TYPE_OF_SERVICE_RELIBILITY_MASK;
import static io.netty.channel.socket.Tun4Packet.INET4_TYPE_OF_SERVICE_THROUGHPUT_MASK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Tun4PacketTest {
    private ByteBuf data;
    private Tun4Packet packet;

    @BeforeEach
    void setUp() {
        data = wrappedBuffer(new byte[]{
                // IPv4
                (byte) 0x45, // version,
                (byte) 0x00, // internet header length
                (byte) 0x00, (byte) 0x3e, // header length
                (byte) 0xf0, (byte) 0x7d, // identification
                (byte) 0x40, (byte) 0x00, // flags (dont fragment)
                (byte) 0x01, // time to live
                (byte) 0x11, // protocol (UDP)
                (byte) 0x06, (byte) 0x01, // header checksum
                (byte) 0x0a, (byte) 0xe1, (byte) 0xd7, (byte) 0x54, // source address
                (byte) 0xe0, (byte) 0x00, (byte) 0x00, (byte) 0xfb, // destination address
                // UDP
                (byte) 0x14, (byte) 0xe9, // source port
                (byte) 0x14, (byte) 0xe9, // destination port
                (byte) 0x00, (byte) 0x2a, // length
                (byte) 0x95, (byte) 0x7a, // checksum
                // Multicast DNS
                (byte) 0x00, (byte) 0x00, // transaction id
                (byte) 0x00, (byte) 0x00, // flags
                (byte) 0x00, (byte) 0x01, // questions
                (byte) 0x00, (byte) 0x00, // answer resource records
                (byte) 0x00, (byte) 0x00, // authority resource records
                (byte) 0x00, (byte) 0x00, // additional resource records
                // additional resource records
                (byte) 0x0a, (byte) 0x72, (byte) 0x6d, (byte) 0x77, (byte) 0x79, (byte) 0x7a,
                (byte) 0x64, (byte) 0x69, (byte) 0x76, (byte) 0x75, (byte) 0x75, (byte) 0x05,
                (byte) 0x6c, (byte) 0x6f, (byte) 0x63, (byte) 0x61, (byte) 0x6c, (byte) 0x00,
                (byte) 0x00, (byte) 0x01, // type (A)
                (byte) 0x00, (byte) 0x01, // class (IN)
        });
        packet = new Tun4Packet(data);
    }

    @Test
    void testConstructor() {
        // create too short byte buf
        final ByteBuf buf = packet.content().readBytes(19);
        try {
            assertThrows(IllegalArgumentException.class, new Executable() {
                @Override
                public void execute() {
                    new Tun4Packet(buf);
                }
            });
        } finally {
            if (buf != null) {
                buf.release();
            }
        }
    }

    @Test
    void testVersion() {
        assertEquals(IPv4, packet.version());
    }

    @Test
    void testInternetHeaderLength() {
        assertEquals(5, packet.internetHeaderLength());
    }

    @Test
    void testTypeOfService() {
        assertEquals(0, packet.typeOfService());
        assertEquals(INET4_TYPE_OF_SERVICE_PRECEDENCE_ROUTINE, packet.typeOfService() >> 5);
        assertFalse((packet.typeOfService() & INET4_TYPE_OF_SERVICE_DELAY_MASK) > 0);
        assertFalse((packet.typeOfService() & INET4_TYPE_OF_SERVICE_THROUGHPUT_MASK) > 0);
        assertFalse((packet.typeOfService() & INET4_TYPE_OF_SERVICE_RELIBILITY_MASK) > 0);
    }

    @Test
    void testTotalLength() {
        assertEquals(62, packet.totalLength());
    }

    @Test
    void testIdentification() {
        assertEquals(61565, packet.identification());
    }

    @Test
    void testFlags() {
        assertEquals(2, packet.flags());
        assertTrue((packet.flags() & INET4_FLAGS_DONT_FRAGMENT_MASK) > 0);
        assertFalse((packet.flags() & INET4_FLAGS_MORE_FRAGMENTS_MASK) > 0);
    }

    @Test
    void testFragmentOffset() {
        assertEquals(0, packet.fragmentOffset());
    }

    @Test
    void testTimeToLive() {
        assertEquals(1, packet.timeToLive());
    }

    @Test
    void testProtocol() {
        assertEquals(17, packet.protocol());
    }

    @Test
    void testHeaderChecksum() {
        assertEquals(1537, packet.headerChecksum());
    }

    @Test
    void testSourceAddress() throws UnknownHostException {
        assertEquals(InetAddress.getByName("10.225.215.84"), packet.sourceAddress());
    }

    @Test
    void testDestinationAddress() throws UnknownHostException {
        assertEquals(InetAddress.getByName("224.0.0.251"), packet.destinationAddress());
    }

    @Test
    void testData() {
        assertEquals(data.slice(20, 42), packet.data());
    }

    @Test
    void testToString() {
        assertEquals("Tun4Packet[id=61565, len=62, src=10.225.215.84, dst=224.0.0.251]", packet.toString());
    }

    @Test
    void verifyChecksum() {
        // from https://en.wikipedia.org/w/index.php?title=Internet_checksum&oldid=1096765534
        ByteBuf buf = wrappedBuffer(new byte[]{
                (byte) 0x45, (byte) 0x00, (byte) 0x00, (byte) 0x73, (byte) 0x00, (byte) 0x00,
                (byte) 0x40, (byte) 0x00, (byte) 0x40, (byte) 0x11, (byte) 0xb8, (byte) 0x61,
                (byte) 0xc0, (byte) 0xa8, (byte) 0x00, (byte) 0x01, (byte) 0xc0, (byte) 0xa8,
                (byte) 0x00, (byte) 0xc7
        });

        Tun4Packet tun4Packet = new Tun4Packet(buf);
        assertTrue(tun4Packet.verifyChecksum());
    }

    @Test
    void calculateChecksum() {
        // from https://en.wikipedia.org/w/index.php?title=Internet_checksum&oldid=1096765534
        ByteBuf buf = wrappedBuffer(new byte[]{
                (byte) 0x45, (byte) 0x00, (byte) 0x00, (byte) 0x73, (byte) 0x00, (byte) 0x00,
                (byte) 0x40, (byte) 0x00, (byte) 0x40, (byte) 0x11, (byte) 0x00, (byte) 0x00,
                (byte) 0xc0, (byte) 0xa8, (byte) 0x00, (byte) 0x01, (byte) 0xc0, (byte) 0xa8,
                (byte) 0x00, (byte) 0xc7
        });

        assertEquals(Ints.fromByteArray(new byte[]{
                (byte) 0x00, (byte) 0x00, (byte) 0xb8, (byte) 0x61
        }), Tun4Packet.calculateChecksum(buf));
    }
}
