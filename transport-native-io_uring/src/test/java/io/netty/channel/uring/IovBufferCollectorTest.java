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
package io.netty.channel.uring;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.unix.IovArray;
import io.netty.util.ReferenceCounted;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class IovBufferCollectorTest {

    @BeforeAll
    public static void loadJNI() {
        assumeTrue(IoUring.isAvailable());
    }

    @Test
    public void retainsOnlyBuffersAddedToIovArray() throws Exception {
        IovArray iovArray = new IovArray(3);
        iovArray.maxCount(2);
        assertRetainedBuffers(iovArray, 2,
                Unpooled.directBuffer(1).writeByte(1),
                Unpooled.directBuffer(1).writeByte(2),
                Unpooled.directBuffer(1).writeByte(3));
    }

    @Test
    public void retainsPartiallyAddedCompositeBuffer() throws Exception {
        CompositeByteBuf composite = Unpooled.compositeBuffer(2);
        composite.addComponents(true,
                Unpooled.directBuffer(1).writeByte(2), Unpooled.directBuffer(1).writeByte(3));
        // The first message leaves room for only one component of the composite.
        assertRetainedBuffers(new IovArray(2), 2,
                Unpooled.directBuffer(1).writeByte(1), composite, Unpooled.directBuffer(1).writeByte(4));
    }

    @Test
    public void retainsFirstBufferExceedingMaxBytes() throws Exception {
        IovArray iovArray = new IovArray(2);
        iovArray.maxBytes(1);
        assertRetainedBuffers(iovArray, 1,
                Unpooled.directBuffer(2).writeShort(1), Unpooled.directBuffer(1).writeByte(2));
    }

    private static void assertRetainedBuffers(IovArray iovArray, int expectedCount, ByteBuf... messages)
            throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel();
        List<ReferenceCounted> retained = Collections.emptyList();
        try {
            ChannelOutboundBuffer outbound = channel.unsafe().outboundBuffer();
            for (ByteBuf message : messages) {
                outbound.addMessage(message, message.readableBytes(), channel.newPromise());
            }
            outbound.addFlush();
            outbound.forEachFlushedMessage(iovArray);
            retained = new AbstractIoUringStreamChannel.IovBufferCollector().collect(outbound, iovArray.count());
            assertEquals(expectedCount, retained.size());
            for (int i = 0; i < expectedCount; i++) {
                assertSame(messages[i], retained.get(i));
            }

            // Discard the outbound queue; only the submitted messages should remain alive.
            channel.close().syncUninterruptibly();
            for (int i = 0; i < messages.length; i++) {
                assertEquals(i < expectedCount ? 1 : 0, messages[i].refCnt());
            }
        } finally {
            channel.finishAndReleaseAll();
            for (ReferenceCounted buffer : retained) {
                buffer.release();
            }
            iovArray.release();
        }
    }
}
