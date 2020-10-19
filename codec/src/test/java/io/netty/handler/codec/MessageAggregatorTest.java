/*
 * Copyright 2017 The Netty Project
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

package io.netty.handler.codec;

import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.DefaultByteBufHolder;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.CharsetUtil;

public class MessageAggregatorTest {
    private static final class ReadCounter extends ChannelOutboundHandlerAdapter {
        int value;

        @Override
        public void read(ChannelHandlerContext ctx) throws Exception {
            value++;
            ctx.read();
        }
    }

    abstract static class MockMessageAggregator
        extends MessageAggregator<ByteBufHolder, ByteBufHolder, ByteBufHolder, ByteBufHolder> {

        protected MockMessageAggregator() {
            super(1024);
        }

        @Override
        protected ByteBufHolder beginAggregation(ByteBufHolder start, ByteBuf content) throws Exception {
            return start.replace(content);
        }
    }

    private static ByteBufHolder message(String string) {
        return new DefaultByteBufHolder(
            Unpooled.copiedBuffer(string, CharsetUtil.US_ASCII));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testReadFlowManagement() throws Exception {
        ReadCounter counter = new ReadCounter();
        ByteBufHolder first = message("first");
        ByteBufHolder chunk = message("chunk");
        ByteBufHolder last = message("last");

        MockMessageAggregator agg = spy(MockMessageAggregator.class);
        when(agg.isStartMessage(first)).thenReturn(true);
        when(agg.isContentMessage(chunk)).thenReturn(true);
        when(agg.isContentMessage(last)).thenReturn(true);
        when(agg.isLastContentMessage(last)).thenReturn(true);

        EmbeddedChannel embedded = new EmbeddedChannel(counter, agg);
        embedded.config().setAutoRead(false);

        assertFalse(embedded.writeInbound(first));
        assertFalse(embedded.writeInbound(chunk));
        assertTrue(embedded.writeInbound(last));

        assertEquals(3, counter.value); // 2 reads issued from MockMessageAggregator
                                        // 1 read issued from EmbeddedChannel constructor

        ByteBufHolder all = new DefaultByteBufHolder(Unpooled.wrappedBuffer(
            first.content().retain(), chunk.content().retain(), last.content().retain()));
        ByteBufHolder out = embedded.readInbound();

        assertEquals(all, out);
        assertTrue(all.release() && out.release());
        assertFalse(embedded.finish());
    }
}
