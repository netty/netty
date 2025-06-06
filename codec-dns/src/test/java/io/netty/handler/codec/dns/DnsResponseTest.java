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
package io.netty.handler.codec.dns;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.AddressedEnvelope;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.CorruptedFrameException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DnsResponseTest {

    private static final byte[][] packets = {
            {
                    0, 1, -127, -128, 0, 1, 0, 1, 0, 0, 0, 0, 3, 119, 119, 119, 7, 101, 120, 97, 109, 112, 108, 101, 3,
                    99, 111, 109, 0, 0, 1, 0, 1, -64, 12, 0, 1, 0, 1, 0, 0, 16, -113, 0, 4, -64, 0, 43, 10
            },
            {
                    0, 1, -127, -128, 0, 1, 0, 1, 0, 0, 0, 0, 3, 119, 119, 119, 7, 101, 120, 97, 109, 112, 108, 101, 3,
                    99, 111, 109, 0, 0, 28, 0, 1, -64, 12, 0, 28, 0, 1, 0, 0, 69, -8, 0, 16, 32, 1, 5, 0, 0, -120, 2,
                    0, 0, 0, 0, 0, 0, 0, 0, 16
            },
            {
                    0, 2, -127, -128, 0, 1, 0, 0, 0, 1, 0, 0, 3, 119, 119, 119, 7, 101, 120, 97, 109, 112, 108, 101, 3,
                    99, 111, 109, 0, 0, 15, 0, 1, -64, 16, 0, 6, 0, 1, 0, 0, 3, -43, 0, 45, 3, 115, 110, 115, 3, 100,
                    110, 115, 5, 105, 99, 97, 110, 110, 3, 111, 114, 103, 0, 3, 110, 111, 99, -64, 49, 119, -4, 39,
                    112, 0, 0, 28, 32, 0, 0, 14, 16, 0, 18, 117, 0, 0, 0, 14, 16
            },
            {
                    0, 3, -127, -128, 0, 1, 0, 1, 0, 0, 0, 0, 3, 119, 119, 119, 7, 101, 120, 97, 109, 112, 108, 101, 3,
                    99, 111, 109, 0, 0, 16, 0, 1, -64, 12, 0, 16, 0, 1, 0, 0, 84, 75, 0, 12, 11, 118, 61, 115, 112,
                    102, 49, 32, 45, 97, 108, 108
            },
            {
                    -105, 19, -127, 0, 0, 1, 0, 0, 0, 13, 0, 0, 2, 104, 112, 11, 116, 105, 109, 98, 111, 117, 100, 114,
                    101, 97, 117, 3, 111, 114, 103, 0, 0, 1, 0, 1, 0, 0, 2, 0, 1, 0, 7, -23, 0, 0, 20, 1, 68, 12, 82,
                    79, 79, 84, 45, 83, 69, 82, 86, 69, 82, 83, 3, 78, 69, 84, 0, 0, 0, 2, 0, 1, 0, 7, -23, 0, 0, 4, 1,
                    70, -64, 49, 0, 0, 2, 0, 1, 0, 7, -23, 0, 0, 4, 1, 69, -64, 49, 0, 0, 2, 0, 1, 0, 7, -23, 0, 0, 4,
                    1, 75, -64, 49, 0, 0, 2, 0, 1, 0, 7, -23, 0, 0, 4, 1, 67, -64, 49, 0, 0, 2, 0, 1, 0, 7, -23, 0, 0,
                    4, 1, 76, -64, 49, 0, 0, 2, 0, 1, 0, 7, -23, 0, 0, 4, 1, 71, -64, 49, 0, 0, 2, 0, 1, 0, 7, -23, 0,
                    0, 4, 1, 73, -64, 49, 0, 0, 2, 0, 1, 0, 7, -23, 0, 0, 4, 1, 66, -64, 49, 0, 0, 2, 0, 1, 0, 7, -23,
                    0, 0, 4, 1, 77, -64, 49, 0, 0, 2, 0, 1, 0, 7, -23, 0, 0, 4, 1, 65, -64, 49, 0, 0, 2, 0, 1, 0, 7,
                    -23, 0, 0, 4, 1, 72, -64, 49, 0, 0, 2, 0, 1, 0, 7, -23, 0, 0, 4, 1, 74, -64, 49
            }
    };

    private static final byte[] malformedLoopPacket = {
            0, 4, -127, -128, 0, 1, 0, 0, 0, 0, 0, 0, -64, 12, 0, 1, 0, 1
    };

    @Test
    public void readResponseTest() {
        EmbeddedChannel embedder = new EmbeddedChannel(new DatagramDnsResponseDecoder());
        for (byte[] p: packets) {
            ByteBuf packet = embedder.alloc().buffer(512).writeBytes(p);
            embedder.writeInbound(new DatagramPacket(packet, null, new InetSocketAddress(0)));
            AddressedEnvelope<DnsResponse, InetSocketAddress> envelope = embedder.readInbound();
            assertInstanceOf(DatagramDnsResponse.class, envelope);
            DnsResponse response = envelope.content();
            assertSame(envelope, response);

            ByteBuf raw = Unpooled.wrappedBuffer(p);
            assertEquals(raw.getUnsignedShort(0), response.id());
            assertEquals(raw.getUnsignedShort(4), response.count(DnsSection.QUESTION));
            assertEquals(raw.getUnsignedShort(6), response.count(DnsSection.ANSWER));
            assertEquals(raw.getUnsignedShort(8), response.count(DnsSection.AUTHORITY));
            assertEquals(raw.getUnsignedShort(10), response.count(DnsSection.ADDITIONAL));

            envelope.release();
        }
        assertFalse(embedder.finish());
    }

    @Test
    public void readMalformedResponseTest() {
        final EmbeddedChannel embedder = new EmbeddedChannel(new DatagramDnsResponseDecoder());
        final ByteBuf packet = embedder.alloc().buffer(512).writeBytes(malformedLoopPacket);
        try {
            assertThrows(CorruptedFrameException.class, new Executable() {
                @Override
                public void execute() {
                    embedder.writeInbound(new DatagramPacket(packet, null, new InetSocketAddress(0)));
                }
            });
        } finally {
            assertFalse(embedder.finish());
        }
    }

    @Test
    public void readIncompleteResponseTest() {
        final EmbeddedChannel embedder = new EmbeddedChannel(new DatagramDnsResponseDecoder());
        final ByteBuf packet = embedder.alloc().buffer(512);
        try {
            assertThrows(CorruptedFrameException.class, new Executable() {
                @Override
                public void execute() {
                    embedder.writeInbound(new DatagramPacket(packet, null, new InetSocketAddress(0)));
                }
            });
        } finally {
            assertFalse(embedder.finish());
        }
    }
}
