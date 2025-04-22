/*
 * Copyright 2021 The Netty Project
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

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.socket.DatagramPacket;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class QuicheQuicCodecTest<B extends QuicCodecBuilder<B>> extends AbstractQuicTest {

    protected abstract B newCodecBuilder();

    @Test
    public void testDefaultVersionIsV1() {
        B builder = newCodecBuilder();
        assertEquals(0x0000_0001, builder.version);
    }

    @Test
    public void testFlushStrategyUsedWithBytes() {
        testFlushStrategy(true);
    }

    @Test
    public void testFlushStrategyUsedWithPackets() {
        testFlushStrategy(false);
    }

    private void testFlushStrategy(boolean useBytes) {
        final int bytes = 8;
        final AtomicInteger numBytesTracker = new AtomicInteger();
        final AtomicInteger numPacketsTracker = new AtomicInteger();
        final AtomicInteger flushCount = new AtomicInteger();
        B builder = newCodecBuilder();
        builder.flushStrategy((numPackets, numBytes) -> {
            numPacketsTracker.set(numPackets);
            numBytesTracker.set(numBytes);
            if (useBytes) {
                return numBytes > 8;
            }
            if (numPackets == 2) {
                return true;
            }
            return false;
        });

        EmbeddedChannel channel = new EmbeddedChannel(new ChannelOutboundHandlerAdapter() {
            @Override
            public void flush(ChannelHandlerContext ctx) throws Exception {
                flushCount.incrementAndGet();
                super.flush(ctx);
            }
        }, builder.build());
        assertEquals(0, numPacketsTracker.get());
        assertEquals(0, numBytesTracker.get());
        assertEquals(0, flushCount.get());

        channel.write(new DatagramPacket(Unpooled.buffer().writeZero(bytes), new InetSocketAddress(0)));
        assertEquals(1, numPacketsTracker.get());
        assertEquals(8, numBytesTracker.get());
        assertEquals(0, flushCount.get());

        channel.write(new DatagramPacket(Unpooled.buffer().writeZero(bytes), new InetSocketAddress(0)));
        assertEquals(2, numPacketsTracker.get());
        assertEquals(16, numBytesTracker.get());
        assertEquals(1, flushCount.get());

        // As a flush did happen we should see two packets in the outbound queue.
        for (int i = 0; i < 2; i++) {
            DatagramPacket packet = channel.readOutbound();
            assertNotNull(packet);
            packet.release();
        }

        ChannelFuture future = channel.write(new DatagramPacket(Unpooled.buffer().writeZero(bytes),
                new InetSocketAddress(0)));
        assertEquals(1, numPacketsTracker.get());
        assertEquals(8, numBytesTracker.get());
        assertEquals(1, flushCount.get());

        // We never flushed the last datagram packet so it should be failed.
        assertFalse(channel.finish());
        assertTrue(future.isDone());
        assertFalse(future.isSuccess());
    }
}
