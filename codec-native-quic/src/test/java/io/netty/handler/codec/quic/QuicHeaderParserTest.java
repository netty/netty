/*
 * Copyright 2025 The Netty Project
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
package io.netty.handler.codec.quic;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class QuicHeaderParserTest {

    private static final InetSocketAddress ADDRESS = new InetSocketAddress("127.0.0.1", 9999);

    // Initial packet with version 1, a 2 byte dcid, a 2 byte scid and the given encoded token length.
    private static ByteBuf initialPacket(long tokenLengthVarint, int tokenLengthBytes, int tokenBytes) {
        ByteBuf packet = Unpooled.buffer();
        packet.writeByte(0xc0);
        packet.writeInt(1);
        packet.writeByte(2).writeShort(0x0102);
        packet.writeByte(2).writeShort(0x0304);
        switch (tokenLengthBytes) {
            case 1:
                packet.writeByte((int) tokenLengthVarint);
                break;
            case 4:
                packet.writeInt((int) (tokenLengthVarint | 0x80000000L));
                break;
            case 8:
                packet.writeLong(tokenLengthVarint | 0xc000000000000000L);
                break;
            default:
                throw new IllegalArgumentException();
        }
        packet.writeZero(tokenBytes);
        return packet;
    }

    private static int parseTokenLength(ByteBuf packet) throws Exception {
        int[] tokenLength = { -1 };
        try (QuicHeaderParser parser = new QuicHeaderParser(2)) {
            parser.parse(ADDRESS, ADDRESS, packet, (sender, recipient, p, type, version, scid, dcid, token) ->
                    tokenLength[0] = token.readableBytes());
        } finally {
            packet.release();
        }
        return tokenLength[0];
    }

    @Test
    public void testTokenLength() throws Exception {
        assertEquals(5, parseTokenLength(initialPacket(5, 1, 5)));
        assertEquals(5, parseTokenLength(initialPacket(5, 4, 5)));
        assertEquals(5, parseTokenLength(initialPacket(5, 8, 5)));
    }

    @Test
    public void testTokenLengthThatDoesNotFitIntoIntIsRejected() {
        // 2^32 + 5 would be truncated to 5 when narrowed to an int, which is available in the packet.
        assertEquals(QuicTransportError.PROTOCOL_VIOLATION, assertThrows(QuicException.class, () ->
                parseTokenLength(initialPacket((1L << 32) + 5, 8, 5))).error());
        // 2^31 would become negative when narrowed to an int.
        assertEquals(QuicTransportError.PROTOCOL_VIOLATION, assertThrows(QuicException.class, () ->
                parseTokenLength(initialPacket(1L << 31, 8, 5))).error());
        assertEquals(QuicTransportError.PROTOCOL_VIOLATION, assertThrows(QuicException.class, () ->
                parseTokenLength(initialPacket((1L << 62) - 1, 8, 5))).error());
    }

    @Test
    public void testTokenLengthLargerThanPacketIsRejected() {
        assertEquals(QuicTransportError.PROTOCOL_VIOLATION, assertThrows(QuicException.class, () ->
                parseTokenLength(initialPacket(6, 1, 5))).error());
    }
}
