/*
 * Copyright 2024 The Netty Project
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
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.NetUtil;
import io.netty.util.internal.PlatformDependent;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class QuicCodecDispatcherTest {

    @Test
    public void testPacketsAreDispatchedToCorrectChannel() throws QuicException {
        short localConnectionIdLength = 16;

        AtomicInteger initChannelCalled = new AtomicInteger();
        QuicCodecDispatcher dispatcher = new QuicCodecDispatcher(localConnectionIdLength) {
            @Override
            protected void initChannel(Channel channel, int localConnectionIdLength,
                                       QuicConnectionIdGenerator idGenerator) {
                initChannelCalled.incrementAndGet();
            }
        };

        EmbeddedChannel[] channels = new EmbeddedChannel[8];
        for (int i = 0; i < channels.length; i++) {
            channels[i] = new EmbeddedChannel(dispatcher);
        }

        int numPackets = 0;
        for (int i = 0; i < 100; i++) {
            writePacket(channels, false, localConnectionIdLength);
            numPackets++;
            writePacket(channels, true, localConnectionIdLength);
            numPackets++;
        }

        for (int idx = 0; idx < channels.length; idx++) {
            EmbeddedChannel channel = channels[idx];
            for (;;) {
                DatagramPacket packet = channel.readInbound();
                    if (packet == null) {
                        break;
                    }
                try {
                    boolean hasShortHeader = QuicCodecDispatcher.hasShortHeader(packet.content());
                    ByteBuf id = QuicCodecDispatcher.getDestinationConnectionId(
                            packet.content(), localConnectionIdLength);
                    if (hasShortHeader) {
                        assertNotNull(id);
                        assertEquals(idx, QuicCodecDispatcher.decodeIdx(id));
                    } else {
                        assertNull(id);
                    }
                    numPackets--;
                } finally {
                    packet.release();
                }
            }
            assertFalse(channel.finishAndReleaseAll());
        }
        assertEquals(0, numPackets);
        assertEquals(channels.length, initChannelCalled.get());
    }

    private static void writePacket(EmbeddedChannel[] channels, boolean shortHeader, short localConnectionIdLength) {
        DatagramPacket packet = createQuicPacket(
                PlatformDependent.threadLocalRandom().nextInt(channels.length),
                shortHeader, localConnectionIdLength);
        channels[PlatformDependent.threadLocalRandom().nextInt(channels.length)].writeInbound(packet);
    }

    // See https://www.rfc-editor.org/rfc/rfc9000.html#section-17
    private static DatagramPacket createQuicPacket(int idx, boolean shortHeader, short localConnectionIdLength) {
        ByteBuf content = Unpooled.buffer();
        byte[] random = new byte[localConnectionIdLength];
        PlatformDependent.threadLocalRandom().nextBytes(random);

        if (shortHeader) {
            content.writeByte(0);
            int writerIndex = content.writerIndex();
            content.writeBytes(random);
            content.setShort(writerIndex, (short) idx);
        } else {
            content.writeByte(1);
            content.writeInt(7);
            content.writeByte((byte) localConnectionIdLength);
            int writerIndex = content.writerIndex();
            content.writeBytes(random);
            content.setShort(writerIndex, (short) idx);
        }
        // Add some more data.
        content.writeZero(PlatformDependent.threadLocalRandom().nextInt(32));
        return new DatagramPacket(content, new InetSocketAddress(NetUtil.LOCALHOST, 0));
    }
}
