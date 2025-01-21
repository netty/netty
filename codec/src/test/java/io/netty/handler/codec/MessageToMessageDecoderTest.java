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
package io.netty.handler.codec;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MessageToMessageDecoderTest {

    @Test
    void testReadIfNotAutoReadWhenNotSharable() {
        ReadCountHandler readCountHandler = new ReadCountHandler();
        EmbeddedChannel channel = new EmbeddedChannel(new MessageToMessageDecoder<String>() {
            private int count;
            @Override
            protected void decode(ChannelHandlerContext ctx, String msg, List<Object> out)  {
                if (count++ == 0) {
                    return;
                }
                out.add(msg);
            }
        });
        channel.config().setAutoRead(false);
        channel.pipeline().addFirst(readCountHandler);
        assertFalse(channel.writeInbound("something"));
        assertEquals(1, readCountHandler.readCount.get());
        assertTrue(channel.writeInbound("something"));
        // As we produced a message in the MessageToMessageDecoder we don't expect that there will be any extra
        // read happen.
        assertEquals(1, readCountHandler.readCount.get());
        assertTrue(channel.finishAndReleaseAll());
    }

    private static final class ReadCountHandler extends ChannelOutboundHandlerAdapter {
        final AtomicInteger readCount = new AtomicInteger();
        @Override
        public void read(ChannelHandlerContext ctx) throws Exception {
            readCount.incrementAndGet();
            super.read(ctx);
        }
    }
}
