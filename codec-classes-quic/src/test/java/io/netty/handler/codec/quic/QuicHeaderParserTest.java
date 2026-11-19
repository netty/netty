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
package io.netty.handler.codec.quic;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

public class QuicHeaderParserTest {

    private static final InetSocketAddress SENDER = new InetSocketAddress("127.0.0.1", 9999);
    private static final InetSocketAddress RECIPIENT = new InetSocketAddress("127.0.0.1", 8888);

    private static final QuicHeaderParser.QuicHeaderProcessor FAILING_PROCESSOR =
            (sender, recipient, packet, type, version, scid, dcid, token) ->
                    fail("Should not be able to parse the header out of a truncated packet");

    // Long header packet with header form (1) + fixed bit (1) + INITIAL packet type (00), version 1, but the
    // buffer stops right before the "Destination Connection ID Length" byte is present.
    //
    // The buffer capacity exactly matches what is written (no spare capacity), so that a read which is not
    // correctly bounds-checked reads past the backing array and fails with a raw IndexOutOfBoundsException
    // rather than the expected QuicException.
    private static ByteBuf truncatedBeforeDestinationConnectionIdLength() {
        ByteBuf buffer = Unpooled.buffer(5, 5);
        buffer.writeByte(0x80); // header form = 1, fixed bit = 0, type-specific bits = 0 (INITIAL)
        buffer.writeInt(1); // version
        return buffer;
    }

    // Same as above but includes a (zero-length) destination connection id, and stops right before the
    // "Source Connection ID Length" byte is present.
    private static ByteBuf truncatedBeforeSourceConnectionIdLength() {
        ByteBuf buffer = Unpooled.buffer(6, 6);
        buffer.writeByte(0x80);
        buffer.writeInt(1);
        buffer.writeByte(0); // destination connection id length == 0
        return buffer;
    }

    @Test
    public void testThrowsOnTruncatedDestinationConnectionIdLength() throws Exception {
        ByteBuf packet = truncatedBeforeDestinationConnectionIdLength();
        try (QuicHeaderParser parser = new QuicHeaderParser(20)) {
            assertThrows(QuicException.class, () ->
                    parser.parse(SENDER, RECIPIENT, packet, FAILING_PROCESSOR));
        } finally {
            packet.release();
        }
    }

    @Test
    public void testThrowsOnTruncatedSourceConnectionIdLength() throws Exception {
        ByteBuf packet = truncatedBeforeSourceConnectionIdLength();
        try (QuicHeaderParser parser = new QuicHeaderParser(20)) {
            assertThrows(QuicException.class, () ->
                    parser.parse(SENDER, RECIPIENT, packet, FAILING_PROCESSOR));
        } finally {
            packet.release();
        }
    }
}
