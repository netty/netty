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
package io.netty5.channel;

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.util.CharsetUtil;
import io.netty5.util.concurrent.FutureListener;
import io.netty5.util.concurrent.Promise;
import io.netty5.util.internal.SilentDispose;
import io.netty5.util.internal.logging.InternalLogger;
import io.netty5.util.internal.logging.InternalLoggerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link CoalescingBufferQueue}.
 */
public class CoalescingBufferQueueTest {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(CoalescingBufferQueueTest.class);

    private Buffer cat;
    private Buffer mouse;

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
        writeQueue = new CoalescingBufferQueue(channel, 16);
        catPromise = newPromise();
        mouseListener = future -> {
            mouseDone = true;
            mouseSuccess = future.isSuccess();
        };
        emptyPromise = newPromise();

        cat = BufferAllocator.offHeapUnpooled().copyOf("cat", CharsetUtil.US_ASCII);
        mouse = BufferAllocator.offHeapUnpooled().copyOf("mouse", CharsetUtil.US_ASCII);
    }

    @AfterEach
    public void finish() {
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
        Buffer remainder = BufferAllocator.offHeapUnpooled().copyOf("mous", CharsetUtil.US_ASCII);
        writeQueue.addFirst(remainder, aggregatePromise);
        Promise<Void> aggregatePromise2 = newPromise();
        assertEquals("mouse", dequeue(5, aggregatePromise2));
        aggregatePromise2.setSuccess(null);
        assertTrue(catPromise.isSuccess());
        assertTrue(mouseSuccess);
        assertFalse(cat.isAccessible());
        assertFalse(mouse.isAccessible());
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
        assertFalse(cat.isAccessible());
        assertFalse(mouse.isAccessible());
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
        assertFalse(cat.isAccessible());
        assertFalse(mouse.isAccessible());
    }

    @Test
    public void testReadExactAddedBufferSizeReturnsOriginal() {
        writeQueue.add(cat, catPromise);
        writeQueue.add(mouse, mouseListener);

        Promise<Void> aggregatePromise = newPromise();
        assertSame(cat, writeQueue.remove(3, aggregatePromise));
        assertFalse(catPromise.isSuccess());
        aggregatePromise.setSuccess(null);
        assertTrue(catPromise.isSuccess());
        assertTrue(cat.isAccessible());
        cat.close();

        aggregatePromise = newPromise();
        assertSame(mouse, writeQueue.remove(5, aggregatePromise));
        assertFalse(mouseDone);
        aggregatePromise.setSuccess(null);
        assertTrue(mouseSuccess);
        assertTrue(mouse.isAccessible());
        mouse.close();
    }

    @Test
    public void testReadEmptyQueueReturnsEmptyBuffer() {
        // Not used in this test.
        cat.close();
        mouse.close();

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
        writeQueue.releaseAndFailAll(cause);
        Promise<Void> aggregatePromise = newPromise();
        assertQueueSize(0, true);
        assertFalse(cat.isAccessible());
        assertFalse(mouse.isAccessible());
        assertSame(cause, catPromise.cause());
        assertEquals("", dequeue(Integer.MAX_VALUE, aggregatePromise));
        assertQueueSize(0, true);
    }

    @Test
    public void testEmptyBuffersAreCoalesced() {
        mouse.close(); // Not used
        Buffer empty = BufferAllocator.offHeapUnpooled().allocate(0);
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
        assertFalse(cat.isAccessible());
        assertFalse(empty.isAccessible());
    }

    @Test
    public void testMerge() {
        writeQueue.add(cat, catPromise);
        CoalescingBufferQueue otherQueue = new CoalescingBufferQueue(channel);
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
        assertFalse(cat.isAccessible());
        assertFalse(mouse.isAccessible());
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
        Buffer removed = writeQueue.remove(numBytes, aggregatePromise);
        String result = removed.toString(US_ASCII);
        SilentDispose.dispose(removed, logger);
        return result;
    }
}
