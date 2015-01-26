/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class MessageToMessageDecoderTest {

    @Test
    public void testChannelReadCompleteSurpressed() {

        MessageToMessageDecoder<Object> decoder = new MessageToMessageDecoder<Object>() {
            @Override
            protected void decode(ChannelHandlerContext ctx, Object msg, List<Object> out) throws Exception { }
        };
        testChannelReadComplete(decoder, 0, "test1");
    }

    @Test
    public void testChannelReadCompleteTriggered() {
        MessageToMessageDecoder<Object> decoder = new MessageToMessageDecoder<Object>() {
            @Override
            protected void decode(ChannelHandlerContext ctx, Object msg, List<Object> out) throws Exception {
                out.add(msg);
            }
        };

        testChannelReadComplete(decoder, 1, "test1");
    }

    @Test
    public void testChannelReadCompleteTriggered2() {
        MessageToMessageDecoder<Object> decoder = new MessageToMessageDecoder<Object>() {
            private boolean added;

            @Override
            protected void decode(ChannelHandlerContext ctx, Object msg, List<Object> out) throws Exception {
                if (!added) {
                    added = true;
                    out.add(msg);
                }
            }
        };

        testChannelReadComplete(decoder, 1, "test1", "test2");
    }

    private static void testChannelReadComplete(MessageToMessageDecoder<?> decoder, int count, Object... msgs) {
        ChannelReadCompleteCountHandler handler = new ChannelReadCompleteCountHandler();
        EmbeddedChannel ch = new EmbeddedChannel(decoder, handler);
        for (Object msg: msgs) {
            boolean result = ch.writeInbound(msg);
            if (count > 0) {
                Assert.assertTrue(result);
            } else {
                Assert.assertFalse(result);
            }
        }
        boolean result = ch.finish();
        if (count > 0) {
            Assert.assertTrue(result);
        } else {
            Assert.assertFalse(result);
        }
        Assert.assertEquals(count, handler.counter.get());
    }

    private static final class ChannelReadCompleteCountHandler extends ChannelInboundHandlerAdapter {
        final AtomicInteger counter = new AtomicInteger();

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            counter.incrementAndGet();
        }
    }
}
