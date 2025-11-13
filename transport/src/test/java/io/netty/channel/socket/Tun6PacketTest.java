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

import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static io.netty.buffer.Unpooled.wrappedBuffer;
import static io.netty.channel.socket.InternetProtocolFamily.IPv6;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class Tun6PacketTest {
    private Tun6Packet packet;
    private ByteBuf data;

    @BeforeEach
    void setUp() {
        data = wrappedBuffer(new byte[]{
                // IPv6
                (byte) 0x60, // version and traffic class
                (byte) 0x26, (byte) 0x0c, (byte) 0x00, // traffic class and flow label
                (byte) 0x00, (byte) 0x75, // payload length
                (byte) 0x06, // next header
                (byte) 0x40, // hop limit
                // source address
                (byte) 0xfe, (byte) 0x80, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x1c, (byte) 0xdf, (byte) 0x17, (byte) 0x4b,
                (byte) 0x91, (byte) 0xdf, (byte) 0x64, (byte) 0x07,
                // destination address
                (byte) 0xfe, (byte) 0x80, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x00, (byte) 0x66, (byte) 0x44, (byte) 0x5e,
                (byte) 0xbe, (byte) 0xdf, (byte) 0xf8, (byte) 0x43,
                // TCP
                (byte) 0xc3, (byte) 0x82, // source port
                (byte) 0x1b, (byte) 0x58, // destination port
                (byte) 0xe4, (byte) 0x02, (byte) 0x37, (byte) 0x5a, // sequence number
                (byte) 0x8f, (byte) 0xdb, (byte) 0x71, (byte) 0xbf, // acknowledgement number
                (byte) 0xc3, // data offset and reserved
                (byte) 0x18, // control bits (congestion window reduced)
                (byte) 0x08, (byte) 0x00, // window
                (byte) 0xbe, (byte) 0xdb, // checksum
                (byte) 0x00, (byte) 0x00, // urgent pointer
                (byte) 0x01, (byte) 0x01, // 2x option
                (byte) 0x08, // kind
                (byte) 0x10, // length
                (byte) 0x17, (byte) 0x8b, (byte) 0x2a, (byte) 0x50, // timestamp value
                (byte) 0xde, (byte) 0x25, (byte) 0x08, (byte) 0xf3, // timestamp echo reply
        });
        packet = new Tun6Packet(data);
    }

    @Test
    void testConstructor() {
        // create too short byte buf
        final ByteBuf buf = packet.content().readBytes(39);
        try {
            assertThrows(IllegalArgumentException.class, new Executable() {
                @Override
                public void execute() {
                    new Tun6Packet(buf);
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
        assertEquals(IPv6, packet.version());
    }

    @Test
    void testTrafficClass() {
        assertEquals(2, packet.trafficClass());
    }

    @Test
    void testFlowLabel() {
        assertEquals(396288, packet.flowLabel());
    }

    @Test
    void testPayloadLength() {
        assertEquals(117, packet.payloadLength());
    }

    @Test
    void testNextHeader() {
        assertEquals(6, packet.nextHeader());
    }

    @Test
    void testHopLimit() {
        assertEquals(64, packet.hopLimit());
    }

    @Test
    void testSourceAddress() throws UnknownHostException {
        assertEquals(InetAddress.getByName("fe80:0:0:0:1cdf:174b:91df:6407"), packet.sourceAddress());
    }

    @Test
    void testDestinationAddress() throws UnknownHostException {
        assertEquals(InetAddress.getByName("fe80:0:0:0:66:445e:bedf:f843"), packet.destinationAddress());
    }

    @Test
    void testData() {
        assertEquals(data.slice(40, 32), packet.data());
    }

    @Test
    void testToString() {
        assertEquals(
                "Tun6Packet[len=117, src=fe80:0:0:0:1cdf:174b:91df:6407, dst=fe80:0:0:0:66:445e:bedf:f843]",
                packet.toString()
        );
    }
}
