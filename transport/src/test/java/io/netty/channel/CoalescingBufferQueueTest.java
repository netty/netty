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
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.ImmediateEventExecutor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

import java.nio.charset.Charset;

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
    private ByteBuf empty;

    private ChannelPromise catPromise, emptyPromise;
    private ChannelPromise voidPromise;
    private ChannelFutureListener mouseListener;

    private boolean mouseDone;
    private boolean mouseSuccess;

    private Channel channel = new EmbeddedChannel();

    private CoalescingBufferQueue writeQueue = new CoalescingBufferQueue(channel);

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
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

        cat = Unpooled.wrappedBuffer("cat".getBytes(Charset.defaultCharset()));
        mouse = Unpooled.wrappedBuffer("mouse".getBytes(Charset.defaultCharset()));
        empty = Unpooled.buffer(0, 1);
    }

    @After
    public void tearDown() {
        ReferenceCountUtil.safeRelease(cat);
        ReferenceCountUtil.safeRelease(mouse);
        ReferenceCountUtil.safeRelease(empty);
    }

    @Test
    public void testAggregateWithFullRead() {
        writeQueue.add(cat, catPromise);
        assertQueueSize(3, false);
        writeQueue.add(mouse, mouseListener);
        assertQueueSize(8, false);
        DefaultChannelPromise aggregatePromise = newPromise();
        assertEquals("catmouse", writeQueue.remove(8, aggregatePromise).toString(Charset.defaultCharset()));
        assertQueueSize(0, true);
        assertFalse(catPromise.isSuccess());
        assertFalse(mouseDone);
        aggregatePromise.trySuccess();
        assertTrue(catPromise.isSuccess());
        assertTrue(mouseSuccess);
    }

    @Test
    public void testWithVoidPromise() {
        writeQueue.add(cat, voidPromise);
        writeQueue.add(mouse, voidPromise);
        writeQueue.add(empty, voidPromise);
        assertQueueSize(8, false);
        assertEquals("catm", writeQueue.remove(4, newPromise()).toString(Charset.defaultCharset()));
        assertQueueSize(4, false);
        assertEquals("ouse", writeQueue.remove(4, newPromise()).toString(Charset.defaultCharset()));
        assertQueueSize(0, true);
    }

    @Test
    public void testAggregateWithPartialRead() {
        writeQueue.add(cat, catPromise);
        writeQueue.add(mouse, mouseListener);
        DefaultChannelPromise aggregatePromise = newPromise();
        assertEquals("catm", writeQueue.remove(4, aggregatePromise).toString(Charset.defaultCharset()));
        assertQueueSize(4, false);
        assertFalse(catPromise.isSuccess());
        assertFalse(mouseDone);
        aggregatePromise.trySuccess();
        assertTrue(catPromise.isSuccess());
        assertFalse(mouseDone);

        aggregatePromise = newPromise();
        assertEquals("ouse", writeQueue.remove(Integer.MAX_VALUE, aggregatePromise).toString(Charset.defaultCharset()));
        assertQueueSize(0, true);
        assertFalse(mouseDone);
        aggregatePromise.trySuccess();
        assertTrue(mouseSuccess);
    }

    @Test
    public void testReadExactAddedBufferSizeReturnsOriginal() {
        writeQueue.add(cat, catPromise);
        writeQueue.add(mouse, mouseListener);
        DefaultChannelPromise aggregatePromise = newPromise();
        assertSame(cat, writeQueue.remove(3, aggregatePromise));
        assertFalse(catPromise.isSuccess());
        aggregatePromise.trySuccess();
        assertTrue(catPromise.isSuccess());

        aggregatePromise = newPromise();
        assertSame(mouse, writeQueue.remove(5, aggregatePromise));
        assertFalse(mouseDone);
        aggregatePromise.trySuccess();
        assertTrue(mouseSuccess);
    }

    @Test
    public void testReadEmptyQueueReturnsEmptyBuffer() {
        assertQueueSize(0, true);
        DefaultChannelPromise aggregatePromise = newPromise();
        assertEquals(0, writeQueue.remove(Integer.MAX_VALUE, aggregatePromise).readableBytes());
        assertQueueSize(0, true);
    }

    @Test
    public void testReleaseAndFailAll() {
        writeQueue.add(cat, catPromise);
        writeQueue.add(mouse, mouseListener);
        RuntimeException cause = new RuntimeException("ooops");
        writeQueue.releaseAndFailAll(cause);
        DefaultChannelPromise aggregatePromise = newPromise();
        assertQueueSize(0, true);
        assertEquals(0, cat.refCnt());
        assertEquals(0, mouse.refCnt());
        assertSame(cause, catPromise.cause());
        assertEquals(0, writeQueue.remove(Integer.MAX_VALUE, aggregatePromise).readableBytes());
        assertQueueSize(0, true);
    }

    @Test
    public void testEmptyBuffersAreCoalesced() {
        assertQueueSize(0, true);
        writeQueue.add(cat, catPromise);
        writeQueue.add(empty, emptyPromise);
        assertQueueSize(3, false);
        DefaultChannelPromise aggregatePromise = newPromise();
        ByteBuf removed = writeQueue.remove(3, aggregatePromise);
        assertQueueSize(0, true);
        assertEquals("cat", removed.toString(Charset.defaultCharset()));
        assertFalse(catPromise.isSuccess());
        assertFalse(emptyPromise.isSuccess());
        aggregatePromise.trySuccess();
        assertTrue(catPromise.isSuccess());
        assertTrue(emptyPromise.isSuccess());
        removed.release();
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
        DefaultChannelPromise aggregatePromise = newPromise();
        assertEquals("catmouse", writeQueue.remove(8, aggregatePromise).toString(Charset.defaultCharset()));
        assertQueueSize(0, true);
        assertFalse(catPromise.isSuccess());
        assertFalse(mouseDone);
        aggregatePromise.trySuccess();
        assertTrue(catPromise.isSuccess());
        assertTrue(mouseSuccess);
    }

    private DefaultChannelPromise newPromise() {
        return new DefaultChannelPromise(channel, ImmediateEventExecutor.INSTANCE);
    }

    private void assertQueueSize(int size, boolean isEmpty) {
        assertEquals(size, writeQueue.readableBytes());
        if (isEmpty) {
            assertTrue(writeQueue.isEmpty());
        } else {
            assertFalse(writeQueue.isEmpty());
        }
    }
}
