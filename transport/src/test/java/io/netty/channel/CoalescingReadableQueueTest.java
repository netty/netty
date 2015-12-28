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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.ImmediateEventExecutor;

import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link CoalescingReadableQueue}.
 */
public class CoalescingReadableQueueTest {

    private ByteBuf cat;
    private FileRegion mouse;
    private ReadableCollection dog;

    private ChannelPromise catPromise, emptyPromise;
    private ChannelPromise voidPromise;
    private ChannelFutureListener mouseListener;

    private boolean mouseDone;
    private boolean mouseSuccess;

    private Channel channel = new EmbeddedChannel();

    private CoalescingReadableQueue writeQueue = new CoalescingReadableQueue(channel);

    private DefaultFileRegion createFileRegion(String str) throws IOException {
        File file = File.createTempFile("netty-", ".tmp");
        file.deleteOnExit();
        FileOutputStream out = new FileOutputStream(file);
        try {
            out.write(str.getBytes(CharsetUtil.US_ASCII));
        } finally {
            out.close();
        }
        return new DefaultFileRegion(file, 0, file.length());
    }

    @Before
    public void setup() throws IOException {
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

        cat = Unpooled.wrappedBuffer("cat".getBytes(CharsetUtil.US_ASCII));
        mouse = createFileRegion("mouse");
        dog = ReadableCollection.of(createFileRegion("dog"));
    }

    @Test
    public void testAggregateWithFullRead() throws IOException {
        writeQueue.add(cat, catPromise);
        assertQueueSize(3, false);
        writeQueue.add(mouse, mouseListener);
        assertQueueSize(8, false);
        writeQueue.add(dog);
        assertQueueSize(11, false);
        DefaultChannelPromise aggregatePromise = newPromise();
        assertEquals("catmouse", dequeue(8, aggregatePromise));
        assertQueueSize(3, false);
        assertFalse(catPromise.isSuccess());
        assertFalse(mouseDone);
        assertEquals("dog", dequeue(3, aggregatePromise));
        assertQueueSize(0, true);
        aggregatePromise.setSuccess();
        assertTrue(catPromise.isSuccess());
        assertTrue(mouseSuccess);
        assertEquals(0, cat.refCnt());
        assertEquals(0, mouse.refCnt());
        assertFalse(dog.isReadable());
    }

    @Test
    public void testWithVoidPromise() throws IOException {
        writeQueue.add(cat, voidPromise);
        writeQueue.add(mouse, voidPromise);
        writeQueue.add(dog, voidPromise);
        assertQueueSize(11, false);
        assertEquals("catm", dequeue(4, newPromise()));
        assertQueueSize(7, false);
        assertEquals("oused", dequeue(5, newPromise()));
        assertQueueSize(2, false);
        assertEquals("og", dequeue(2, newPromise()));
        assertQueueSize(0, true);
        assertEquals(0, cat.refCnt());
        assertEquals(0, mouse.refCnt());
        assertFalse(dog.isReadable());
    }

    @Test
    public void testAggregateWithPartialRead() throws IOException {
        writeQueue.add(cat, catPromise);
        writeQueue.add(mouse, mouseListener);
        DefaultChannelPromise aggregatePromise = newPromise();
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
    public void testReadExactAddedBufferSizeReturnsOriginal() throws IOException {
        writeQueue.add(cat, catPromise);
        writeQueue.add(mouse, mouseListener);

        DefaultChannelPromise aggregatePromise = newPromise();
        assertSame(cat, writeQueue.remove(3, aggregatePromise).unbox());
        assertFalse(catPromise.isSuccess());
        aggregatePromise.setSuccess();
        assertTrue(catPromise.isSuccess());
        assertEquals(1, cat.refCnt());
        cat.release();

        aggregatePromise = newPromise();
        assertEquals("mouse", dequeue(5, aggregatePromise));
        assertFalse(mouseDone);
        aggregatePromise.setSuccess();
        assertTrue(mouseSuccess);
    }

    @Test
    public void testReadEmptyQueueReturnsEmptyBuffer() throws IOException {
        // Not used in this test.
        cat.release();
        mouse.release();

        assertQueueSize(0, true);
        DefaultChannelPromise aggregatePromise = newPromise();
        assertEquals("", dequeue(Integer.MAX_VALUE, aggregatePromise));
        assertQueueSize(0, true);
    }

    @Test
    public void testReleaseAndFailAll() throws IOException {
        writeQueue.add(cat, catPromise);
        writeQueue.add(mouse, mouseListener);
        writeQueue.add(dog);
        RuntimeException cause = new RuntimeException("ooops");
        writeQueue.releaseAndFailAll(cause);
        DefaultChannelPromise aggregatePromise = newPromise();
        assertQueueSize(0, true);
        assertEquals(0, cat.refCnt());
        assertEquals(0, mouse.refCnt());
        assertFalse(dog.isReadable());
        assertSame(cause, catPromise.cause());
        assertEquals("", dequeue(Integer.MAX_VALUE, aggregatePromise));
        assertQueueSize(0, true);
    }

    @Test
    public void testEmptyBuffersAreCoalesced() throws IOException {
        ByteBuf empty = Unpooled.buffer(0, 1);
        assertQueueSize(0, true);
        writeQueue.add(cat, catPromise);
        writeQueue.add(empty, emptyPromise);
        assertQueueSize(3, false);
        DefaultChannelPromise aggregatePromise = newPromise();
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
    public void testMerge() throws IOException {
        writeQueue.add(cat, catPromise);
        CoalescingReadableQueue otherQueue = new CoalescingReadableQueue(channel);
        otherQueue.add(mouse, mouseListener);
        otherQueue.copyTo(writeQueue);
        assertQueueSize(8, false);
        DefaultChannelPromise aggregatePromise = newPromise();
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

    private byte[] toBytes(ReadableCollection rc) throws IOException {
        EmbeddedChannel channel = new EmbeddedChannel();
        rc.writeTo(channel, rc.readableBytes());
        channel.flush();
        return ReadableCollectionTest.toBytes(channel.outboundMessages());
    }

    private String dequeue(int numBytes, ChannelPromise aggregatePromise) throws IOException {
        ReadableCollection removed = writeQueue.remove(numBytes, aggregatePromise);
        String result = new String(toBytes(removed), CharsetUtil.US_ASCII);
        ReferenceCountUtil.safeRelease(removed);
        return result;
    }
}
