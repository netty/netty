/*
 * Copyright 2021 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty5.handler.codec;

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.buffer.api.CompositeBuffer;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.ChannelPipeline;
import io.netty5.channel.ReadBufferAllocator;
import io.netty5.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MessageAggregatorTest {
    private static final class ReadCounter implements ChannelHandler {
        int value;

        @Override
        public void read(ChannelHandlerContext ctx, ReadBufferAllocator readBufferAllocator) {
            value++;
            ctx.read(readBufferAllocator);
        }
    }

    static class MockMessageAggregator extends MessageAggregator<Buffer, Buffer, Buffer, CompositeBuffer> {

        private final Buffer first;
        private final Buffer last;

        protected MockMessageAggregator(Buffer first, Buffer last) {
            super(1024);
            this.first = first;
            this.last = last;
        }

        @Override
        protected Buffer tryStartMessage(Object msg) {
            return msg.equals(first) ? first : null;
        }

        @Override
        protected Buffer tryContentMessage(Object msg) {
            return msg instanceof Buffer ? (Buffer) msg : null;
        }

        @Override
        protected boolean isLastContentMessage(Buffer msg) {
            return msg.equals(last);
        }

        @Override
        protected boolean isAggregated(Object msg) {
            return msg instanceof CompositeBuffer;
        }

        @Override
        protected int lengthForContent(Buffer msg) {
            return msg.readableBytes();
        }

        @Override
        protected int lengthForAggregation(CompositeBuffer msg) {
            return msg.readableBytes();
        }

        @Override
        protected boolean isContentLengthInvalid(Buffer start, int maxContentLength) {
            return start.readableBytes() > maxContentLength;
        }

        @Override
        protected Object newContinueResponse(Buffer start, int maxContentLength, ChannelPipeline pipeline) {
            return null;
        }

        @Override
        protected boolean closeAfterContinueResponse(Object msg) {
            return true;
        }

        @Override
        protected boolean ignoreContentAfterContinueResponse(Object msg) {
            return true;
        }

        @Override
        protected CompositeBuffer beginAggregation(BufferAllocator allocator, Buffer start) {
            return allocator.compose(start.copy().send());
        }

        @Override
        protected void aggregate(BufferAllocator allocator, CompositeBuffer aggregated, Buffer content) {
            aggregated.extendWith(content.copy().send());
        }
    }

    private static Buffer message(BufferAllocator allocator, String string) {
        final byte[] bytes = string.getBytes(StandardCharsets.US_ASCII);
        return allocator.allocate(bytes.length).writeBytes(bytes);
    }

    @Test
    public void testReadFlowManagement() {
        try (BufferAllocator allocator = BufferAllocator.offHeapPooled();
             Buffer first = message(allocator, "first");
             Buffer chunk = message(allocator, "chunk");
             Buffer last = message(allocator, "last")) {
            ReadCounter counter = new ReadCounter();
            MockMessageAggregator agg = new MockMessageAggregator(first.copy(), last.copy());
            EmbeddedChannel embedded = new EmbeddedChannel(counter, agg);
            embedded.setOption(ChannelOption.AUTO_READ, false);

            assertFalse(embedded.writeInbound(first.copy()));
            assertFalse(embedded.writeInbound(chunk.copy()));
            assertTrue(embedded.writeInbound(last.copy()));

            assertEquals(3, counter.value); // 2 reads issued from MockMessageAggregator
            // 1 read issued from EmbeddedChannel constructor

            try (CompositeBuffer all = allocator.compose(asList(first.copy().send(), chunk.copy().send(),
                    last.copy().send()));
                 CompositeBuffer out = embedded.readInbound()) {
                assertEquals(all, out);
                assertTrue(all.isAccessible());
                assertTrue(out.isAccessible());
            }
            assertFalse(embedded.finish());
        }
    }
}
