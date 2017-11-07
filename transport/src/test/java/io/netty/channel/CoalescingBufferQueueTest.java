/*
 * Copyright 2015 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.channel;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link CoalescingBufferQueue}.
 */
public class CoalescingBufferQueueTest {

    private ByteBuf cat;
    private ByteBuf mouse;

    private ChannelPromise catPromise, emptyPromise;
    private ChannelPromise voidPromise;
    private ChannelFutureListener mouseListener;

    private boolean mouseDone;
    private boolean mouseSuccess;

    private EmbeddedChannel channel;
    private CoalescingBufferQueue writeQueue;

    @Before
    public void setup() {
        mouseDone = false;
        mouseSuccess = false;
        channel = new EmbeddedChannel();
        writeQueue = new CoalescingBufferQueue(channel, 16, true);
        catPromise = newPromise();
        mouseListener = new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                mouseDone = true;
                mouseSuccess = future.isSuccess();
            }
        };
        emptyPromise = newPromise();
        voidPromise = channel.voidPromise();

        cat = Unpooled.wrappedBuffer("cat".getBytes(CharsetUtil.US_ASCII));
        mouse = Unpooled.wrappedBuffer("mouse".getBytes(CharsetUtil.US_ASCII));
    }

    @After
    public void finish() {
        assertFalse(channel.finish());
    }

    @Test
    public void testAddFirstPromiseRetained() {
        writeQueue.add(cat, catPromise);
        assertQueueSize(3, false);
        writeQueue.add(mouse, mouseListener);
        assertQueueSize(8, false);
        ChannelPromise aggregatePromise = newPromise();
        assertEquals("catmous", dequeue(7, aggregatePromise));
        ByteBuf remainder = Unpooled.wrappedBuffer("mous".getBytes(CharsetUtil.US_ASCII));
        writeQueue.addFirst(remainder, aggregatePromise);
        ChannelPromise aggregatePromise2 = newPromise();
        assertEquals("mouse", dequeue(5, aggregatePromise2));
        aggregatePromise2.setSuccess();
        assertTrue(catPromise.isSuccess());
        assertTrue(mouseSuccess);
        assertEquals(0, cat.refCnt());
        assertEquals(0, mouse.refCnt());
    }

    @Test
    public void testAddFirstVoidPromise() {
        writeQueue.add(cat, catPromise);
        assertQueueSize(3, false);
        writeQueue.add(mouse, mouseListener);
        assertQueueSize(8, false);
        ChannelPromise aggregatePromise = newPromise();
        assertEquals("catmous", dequeue(7, aggregatePromise));
        ByteBuf remainder = Unpooled.wrappedBuffer("mous".getBytes(CharsetUtil.US_ASCII));
        writeQueue.addFirst(remainder, voidPromise);
        ChannelPromise aggregatePromise2 = newPromise();
        assertEquals("mouse", dequeue(5, aggregatePromise2));
        aggregatePromise2.setSuccess();
        // Because we used a void promise above, we shouldn't complete catPromise until aggregatePromise is completed.
        assertFalse(catPromise.isSuccess());
        assertTrue(mouseSuccess);
        aggregatePromise.setSuccess();
        assertTrue(catPromise.isSuccess());
        assertTrue(mouseSuccess);
        assertEquals(0, cat.refCnt());
        assertEquals(0, mouse.refCnt());
    }

    @Test
    public void testAggregateWithFullRead() {
        writeQueue.add(cat, catPromise);
        assertQueueSize(3, false);
        writeQueue.add(mouse, mouseListener);
        assertQueueSize(8, false);
        ChannelPromise aggregatePromise = newPromise();
        assertEquals("catmouse", dequeue(8, aggregatePromise));
        assertQueueSize(0, true);
        assertFalse(catPromise.isSuccess());
        assertFalse(mouseDone);
        aggregatePromise.setSuccess();
        assertTrue(catPromise.isSuccess());
        assertTrue(mouseSuccess);
        assertEquals(0, cat.refCnt());
        assertEquals(0, mouse.refCnt());
    }

    @Test
    public void testWithVoidPromise() {
        writeQueue.add(cat, voidPromise);
        writeQueue.add(mouse, voidPromise);
        assertQueueSize(8, false);
        assertEquals("catm", dequeue(4, newPromise()));
        assertQueueSize(4, false);
        assertEquals("ouse", dequeue(4, newPromise()));
        assertQueueSize(0, true);
        assertEquals(0, cat.refCnt());
        assertEquals(0, mouse.refCnt());
    }

    @Test
    public void testAggregateWithPartialRead() {
        writeQueue.add(cat, catPromise);
        writeQueue.add(mouse, mouseListener);
        ChannelPromise aggregatePromise = newPromise();
        assertEquals("catm", dequeue(4, aggregatePromise));
        assertQueueSize(4, false);
        assertFalse(catPromise.isSuccess());
        assertFalse(mouseDone);
        aggregatePromise.setSuccess();
        assertTrue(catPromise.isSuccess());
        assertFalse(mouseDone);

        aggregatePromise = newPromise();
        assertEquals("ouse", dequeue(Integer.MAX_VALUE, aggregatePromise));
        assertQueueSize(0, true);
        assertFalse(mouseDone);
        aggregatePromise.setSuccess();
        assertTrue(mouseSuccess);
        assertEquals(0, cat.refCnt());
        assertEquals(0, mouse.refCnt());
    }

    @Test
    public void testReadExactAddedBufferSizeReturnsOriginal() {
        writeQueue.add(cat, catPromise);
        writeQueue.add(mouse, mouseListener);

        ChannelPromise aggregatePromise = newPromise();
        assertSame(cat, writeQueue.remove(3, aggregatePromise));
        assertFalse(catPromise.isSuccess());
        aggregatePromise.setSuccess();
        assertTrue(catPromise.isSuccess());
        assertEquals(1, cat.refCnt());
        cat.release();

        aggregatePromise = newPromise();
        assertSame(mouse, writeQueue.remove(5, aggregatePromise));
        assertFalse(mouseDone);
        aggregatePromise.setSuccess();
        assertTrue(mouseSuccess);
        assertEquals(1, mouse.refCnt());
        mouse.release();
    }

    @Test
    public void testReadEmptyQueueReturnsEmptyBuffer() {
        // Not used in this test.
        cat.release();
        mouse.release();

        assertQueueSize(0, true);
        ChannelPromise aggregatePromise = newPromise();
        assertEquals("", dequeue(Integer.MAX_VALUE, aggregatePromise));
        assertQueueSize(0, true);
    }

    @Test
    public void testReleaseAndFailAll() {
        writeQueue.add(cat, catPromise);
        writeQueue.add(mouse, mouseListener);
        RuntimeException cause = new RuntimeException("ooops");
        writeQueue.releaseAndFailAll(cause);
        ChannelPromise aggregatePromise = newPromise();
        assertQueueSize(0, true);
        assertEquals(0, cat.refCnt());
        assertEquals(0, mouse.refCnt());
        assertSame(cause, catPromise.cause());
        assertEquals("", dequeue(Integer.MAX_VALUE, aggregatePromise));
        assertQueueSize(0, true);
    }

    @Test
    public void testEmptyBuffersAreCoalesced() {
        ByteBuf empty = Unpooled.buffer(0, 1);
        assertQueueSize(0, true);
        writeQueue.add(cat, catPromise);
        writeQueue.add(empty, emptyPromise);
        assertQueueSize(3, false);
        ChannelPromise aggregatePromise = newPromise();
        assertEquals("cat", dequeue(3, aggregatePromise));
        assertQueueSize(0, true);
        assertFalse(catPromise.isSuccess());
        assertFalse(emptyPromise.isSuccess());
        aggregatePromise.setSuccess();
        assertTrue(catPromise.isSuccess());
        assertTrue(emptyPromise.isSuccess());
        assertEquals(0, cat.refCnt());
        assertEquals(0, empty.refCnt());
    }

    @Test
    public void testMerge() {
        writeQueue.add(cat, catPromise);
        CoalescingBufferQueue otherQueue = new CoalescingBufferQueue(channel);
        otherQueue.add(mouse, mouseListener);
        otherQueue.copyTo(writeQueue);
        assertQueueSize(8, false);
        ChannelPromise aggregatePromise = newPromise();
        assertEquals("catmouse", dequeue(8, aggregatePromise));
        assertQueueSize(0, true);
        assertFalse(catPromise.isSuccess());
        assertFalse(mouseDone);
        aggregatePromise.setSuccess();
        assertTrue(catPromise.isSuccess());
        assertTrue(mouseSuccess);
        assertEquals(0, cat.refCnt());
        assertEquals(0, mouse.refCnt());
    }

    @Test
    public void testWritabilityChanged() {
        testWritabilityChanged0(false);
    }

    @Test
    public void testWritabilityChangedFailAll() {
        testWritabilityChanged0(true);
    }

    private void testWritabilityChanged0(boolean fail) {
        channel.config().setWriteBufferWaterMark(new WriteBufferWaterMark(3, 4));
        assertTrue(channel.isWritable());
        writeQueue.add(Unpooled.wrappedBuffer(new byte[] {1 , 2, 3}));
        assertTrue(channel.isWritable());
        writeQueue.add(Unpooled.wrappedBuffer(new byte[] {4, 5}));
        assertFalse(channel.isWritable());
        assertEquals(5, writeQueue.readableBytes());

        if (fail) {
            writeQueue.releaseAndFailAll(new IllegalStateException());
        } else {
            ByteBuf buffer = writeQueue.removeFirst(voidPromise);
            assertEquals(1, buffer.readByte());
            assertEquals(2, buffer.readByte());
            assertEquals(3, buffer.readByte());
            assertFalse(buffer.isReadable());
            buffer.release();
            assertTrue(channel.isWritable());

            buffer = writeQueue.removeFirst(voidPromise);
            assertEquals(4, buffer.readByte());
            assertEquals(5, buffer.readByte());
            assertFalse(buffer.isReadable());
            buffer.release();
        }

        assertTrue(channel.isWritable());
        assertTrue(writeQueue.isEmpty());
    }

    private ChannelPromise newPromise() {
        return channel.newPromise();
    }

    private void assertQueueSize(int size, boolean isEmpty) {
        assertEquals(size, writeQueue.readableBytes());
        if (isEmpty) {
            assertTrue(writeQueue.isEmpty());
        } else {
            assertFalse(writeQueue.isEmpty());
        }
    }

    private String dequeue(int numBytes, ChannelPromise aggregatePromise) {
        ByteBuf removed = writeQueue.remove(numBytes, aggregatePromise);
        String result = removed.toString(CharsetUtil.US_ASCII);
        ReferenceCountUtil.safeRelease(removed);
        return result;
    }
}
