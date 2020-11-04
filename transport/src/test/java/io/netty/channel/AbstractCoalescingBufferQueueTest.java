/*
 * Copyright 2020 The Netty Project

 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:

 * https://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import org.junit.Test;

import java.nio.channels.ClosedChannelException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AbstractCoalescingBufferQueueTest {

    // See https://github.com/netty/netty/issues/10286
    @Test
    public void testDecrementAllWhenWriteAndRemoveAll() {
        testDecrementAll(true);
    }

    // See https://github.com/netty/netty/issues/10286
    @Test
    public void testDecrementAllWhenReleaseAndFailAll() {
        testDecrementAll(false);
    }

    private static void testDecrementAll(boolean write) {
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                ReferenceCountUtil.release(msg);
                promise.setSuccess();
            }
        }, new ChannelHandlerAdapter() { });
        final AbstractCoalescingBufferQueue queue = new AbstractCoalescingBufferQueue(channel, 128) {
            @Override
            protected ByteBuf compose(ByteBufAllocator alloc, ByteBuf cumulation, ByteBuf next) {
                return composeIntoComposite(alloc, cumulation, next);
            }

            @Override
            protected ByteBuf removeEmptyValue() {
                return Unpooled.EMPTY_BUFFER;
            }
        };

        final byte[] bytes = new byte[128];
        queue.add(Unpooled.wrappedBuffer(bytes), new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) {
                queue.add(Unpooled.wrappedBuffer(bytes));
                assertEquals(bytes.length, queue.readableBytes());
            }
        });

        assertEquals(bytes.length, queue.readableBytes());

        ChannelHandlerContext ctx = channel.pipeline().lastContext();
        if (write) {
            queue.writeAndRemoveAll(ctx);
        } else {
            queue.releaseAndFailAll(ctx, new ClosedChannelException());
        }
        ByteBuf buffer = queue.remove(channel.alloc(), 128, channel.newPromise());
        assertFalse(buffer.isReadable());
        buffer.release();

        assertTrue(queue.isEmpty());
        assertEquals(0, queue.readableBytes());

        assertFalse(channel.finish());
    }
}
