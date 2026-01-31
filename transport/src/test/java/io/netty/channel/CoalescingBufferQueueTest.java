/*
 * Copyright 2015 The Netty Project
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
package io.netty.channel;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link CoalescingBufferQueue}.
 */
public class CoalescingBufferQueueTest {

    private ByteBuf cat;
    private ByteBuf mouse;

    private Promise<Void> catPromise, emptyPromise;
    private FutureListener<Void> mouseListener;

    private boolean mouseDone;
    private boolean mouseSuccess;

    private EmbeddedChannel channel;
    private CoalescingBufferQueue writeQueue;

    @BeforeEach
    public void setup() {
        mouseDone = false;
        mouseSuccess = false;
        channel = new EmbeddedChannel();
        writeQueue = new CoalescingBufferQueue(16);
        catPromise = newPromise();
        mouseListener = future -> {
            mouseDone = true;
            mouseSuccess = future.isSuccess();
        };
        emptyPromise = newPromise();

        cat = Unpooled.wrappedBuffer("cat".getBytes(CharsetUtil.US_ASCII));
        mouse = Unpooled.wrappedBuffer("mouse".getBytes(CharsetUtil.US_ASCII));
    }

    @AfterEach
    public void finish() throws Exception {
        assertFalse(channel.finish());
    }

    @Test
    public void testAddFirstPromiseRetained() {
        writeQueue.add(cat, catPromise);
        assertQueueSize(3, false);
        writeQueue.add(mouse, mouseListener);
        assertQueueSize(8, false);
        Promise<Void> aggregatePromise = newPromise();
        assertEquals("catmous", dequeue(7, aggregatePromise));
        ByteBuf remainder = Unpooled.wrappedBuffer("mous".getBytes(CharsetUtil.US_ASCII));
        writeQueue.addFirst(remainder, aggregatePromise);
        Promise<Void> aggregatePromise2 = newPromise();
        assertEquals("mouse", dequeue(5, aggregatePromise2));
        aggregatePromise2.setSuccess(null);
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
        Promise<Void> aggregatePromise = newPromise();
        assertEquals("catmouse", dequeue(8, aggregatePromise));
        assertQueueSize(0, true);
        assertFalse(catPromise.isSuccess());
        assertFalse(mouseDone);
        aggregatePromise.setSuccess(null);
        assertTrue(catPromise.isSuccess());
        assertTrue(mouseSuccess);
        assertEquals(0, cat.refCnt());
        assertEquals(0, mouse.refCnt());
    }

    @Test
    public void testAggregateWithPartialRead() {
        writeQueue.add(cat, catPromise);
        writeQueue.add(mouse, mouseListener);
        Promise<Void> aggregatePromise = newPromise();
        assertEquals("catm", dequeue(4, aggregatePromise));
        assertQueueSize(4, false);
        assertFalse(catPromise.isSuccess());
        assertFalse(mouseDone);
        aggregatePromise.setSuccess(null);
        assertTrue(catPromise.isSuccess());
        assertFalse(mouseDone);

        aggregatePromise = newPromise();
        assertEquals("ouse", dequeue(Integer.MAX_VALUE, aggregatePromise));
        assertQueueSize(0, true);
        assertFalse(mouseDone);
        aggregatePromise.setSuccess(null);
        assertTrue(mouseSuccess);
        assertEquals(0, cat.refCnt());
        assertEquals(0, mouse.refCnt());
    }

    @Test
    public void testReadExactAddedBufferSizeReturnsOriginal() {
        writeQueue.add(cat, catPromise);
        writeQueue.add(mouse, mouseListener);

        Promise<Void> aggregatePromise = newPromise();
        assertSame(cat, writeQueue.remove(channel.alloc(), 3, aggregatePromise));
        assertFalse(catPromise.isSuccess());
        aggregatePromise.setSuccess(null);
        assertTrue(catPromise.isSuccess());
        assertEquals(1, cat.refCnt());
        cat.release();

        aggregatePromise = newPromise();
        assertSame(mouse, writeQueue.remove(channel.alloc(), 5, aggregatePromise));
        assertFalse(mouseDone);
        aggregatePromise.setSuccess(null);
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
        Promise<Void> aggregatePromise = newPromise();
        assertEquals("", dequeue(Integer.MAX_VALUE, aggregatePromise));
        assertQueueSize(0, true);
    }

    @Test
    public void testReleaseAndFailAll() {
        writeQueue.add(cat, catPromise);
        writeQueue.add(mouse, mouseListener);
        RuntimeException cause = new RuntimeException("ooops");
        writeQueue.releaseAndFailAll(channel, cause);
        Promise<Void> aggregatePromise = newPromise();
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
        Promise<Void> aggregatePromise = newPromise();
        assertEquals("cat", dequeue(3, aggregatePromise));
        assertQueueSize(0, true);
        assertFalse(catPromise.isSuccess());
        assertFalse(emptyPromise.isSuccess());
        aggregatePromise.setSuccess(null);
        assertTrue(catPromise.isSuccess());
        assertTrue(emptyPromise.isSuccess());
        assertEquals(0, cat.refCnt());
        assertEquals(0, empty.refCnt());
    }

    @Test
    public void testMerge() {
        writeQueue.add(cat, catPromise);
        CoalescingBufferQueue otherQueue = new CoalescingBufferQueue();
        otherQueue.add(mouse, mouseListener);
        otherQueue.copyTo(writeQueue);
        assertQueueSize(8, false);
        Promise<Void> aggregatePromise = newPromise();
        assertEquals("catmouse", dequeue(8, aggregatePromise));
        assertQueueSize(0, true);
        assertFalse(catPromise.isSuccess());
        assertFalse(mouseDone);
        aggregatePromise.setSuccess(null);
        assertTrue(catPromise.isSuccess());
        assertTrue(mouseSuccess);
        assertEquals(0, cat.refCnt());
        assertEquals(0, mouse.refCnt());
    }

    private Promise<Void> newPromise() {
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

    private String dequeue(int numBytes, Promise<Void> aggregatePromise) {
        ByteBuf removed = writeQueue.remove(channel.alloc(), numBytes, aggregatePromise);
        String result = removed.toString(CharsetUtil.US_ASCII);
        ReferenceCountUtil.safeRelease(removed);
        return result;
    }
}
