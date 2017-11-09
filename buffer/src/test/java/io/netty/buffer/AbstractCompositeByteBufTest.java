/*
 * Copyright 2012 The Netty Project
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
package io.netty.buffer;

import io.netty.util.ReferenceCountUtil;
import org.junit.Assume;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import static io.netty.buffer.Unpooled.EMPTY_BUFFER;
import static io.netty.buffer.Unpooled.buffer;
import static io.netty.buffer.Unpooled.compositeBuffer;
import static io.netty.buffer.Unpooled.directBuffer;
import static io.netty.buffer.Unpooled.wrappedBuffer;
import static io.netty.util.internal.EmptyArrays.EMPTY_BYTES;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * An abstract test class for composite channel buffers
 */
public abstract class AbstractCompositeByteBufTest extends AbstractByteBufTest {

    private final ByteOrder order;

    protected AbstractCompositeByteBufTest(ByteOrder order) {
        if (order == null) {
            throw new NullPointerException("order");
        }
        this.order = order;
    }

    @Override
    protected ByteBuf newBuffer(int length, int maxCapacity) {
        Assume.assumeTrue(maxCapacity == Integer.MAX_VALUE);

        List<ByteBuf> buffers = new ArrayList<ByteBuf>();
        for (int i = 0; i < length + 45; i += 45) {
            buffers.add(EMPTY_BUFFER);
            buffers.add(wrappedBuffer(new byte[1]));
            buffers.add(EMPTY_BUFFER);
            buffers.add(wrappedBuffer(new byte[2]));
            buffers.add(EMPTY_BUFFER);
            buffers.add(wrappedBuffer(new byte[3]));
            buffers.add(EMPTY_BUFFER);
            buffers.add(wrappedBuffer(new byte[4]));
            buffers.add(EMPTY_BUFFER);
            buffers.add(wrappedBuffer(new byte[5]));
            buffers.add(EMPTY_BUFFER);
            buffers.add(wrappedBuffer(new byte[6]));
            buffers.add(EMPTY_BUFFER);
            buffers.add(wrappedBuffer(new byte[7]));
            buffers.add(EMPTY_BUFFER);
            buffers.add(wrappedBuffer(new byte[8]));
            buffers.add(EMPTY_BUFFER);
            buffers.add(wrappedBuffer(new byte[9]));
            buffers.add(EMPTY_BUFFER);
        }

        ByteBuf buffer = wrappedBuffer(Integer.MAX_VALUE, buffers.toArray(new ByteBuf[buffers.size()])).order(order);

        // Truncate to the requested capacity.
        buffer.capacity(length);

        assertEquals(length, buffer.capacity());
        assertEquals(length, buffer.readableBytes());
        assertFalse(buffer.isWritable());
        buffer.writerIndex(0);
        return buffer;
    }

    // Composite buffer does not waste bandwidth on discardReadBytes, but
    // the test will fail in strict mode.
    @Override
    protected boolean discardReadBytesDoesNotMoveWritableBytes() {
        return false;
    }

    /**
     * Tests the "getBufferFor" method
     */
    @Test
    public void testComponentAtOffset() {
        CompositeByteBuf buf = (CompositeByteBuf) wrappedBuffer(new byte[]{1, 2, 3, 4, 5},
                new byte[]{4, 5, 6, 7, 8, 9, 26});

        //Ensure that a random place will be fine
        assertEquals(5, buf.componentAtOffset(2).capacity());

        //Loop through each byte

        byte index = 0;

        while (index < buf.capacity()) {
            ByteBuf _buf = buf.componentAtOffset(index++);
            assertNotNull(_buf);
            assertTrue(_buf.capacity() > 0);
            assertNotNull(_buf.getByte(0));
            assertNotNull(_buf.getByte(_buf.readableBytes() - 1));
        }

        buf.release();
    }

    @Test
    public void testDiscardReadBytes3() {
        ByteBuf a, b;
        a = wrappedBuffer(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 }).order(order);
        b = wrappedBuffer(
                wrappedBuffer(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 }, 0, 5).order(order),
                wrappedBuffer(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 }, 5, 5).order(order));
        a.skipBytes(6);
        a.markReaderIndex();
        b.skipBytes(6);
        b.markReaderIndex();
        assertEquals(a.readerIndex(), b.readerIndex());
        a.readerIndex(a.readerIndex() - 1);
        b.readerIndex(b.readerIndex() - 1);
        assertEquals(a.readerIndex(), b.readerIndex());
        a.writerIndex(a.writerIndex() - 1);
        a.markWriterIndex();
        b.writerIndex(b.writerIndex() - 1);
        b.markWriterIndex();
        assertEquals(a.writerIndex(), b.writerIndex());
        a.writerIndex(a.writerIndex() + 1);
        b.writerIndex(b.writerIndex() + 1);
        assertEquals(a.writerIndex(), b.writerIndex());
        assertTrue(ByteBufUtil.equals(a, b));
        // now discard
        a.discardReadBytes();
        b.discardReadBytes();
        assertEquals(a.readerIndex(), b.readerIndex());
        assertEquals(a.writerIndex(), b.writerIndex());
        assertTrue(ByteBufUtil.equals(a, b));
        a.resetReaderIndex();
        b.resetReaderIndex();
        assertEquals(a.readerIndex(), b.readerIndex());
        a.resetWriterIndex();
        b.resetWriterIndex();
        assertEquals(a.writerIndex(), b.writerIndex());
        assertTrue(ByteBufUtil.equals(a, b));

        a.release();
        b.release();
    }

    @Test
    public void testAutoConsolidation() {
        CompositeByteBuf buf = compositeBuffer(2);

        buf.addComponent(wrappedBuffer(new byte[] { 1 }));
        assertEquals(1, buf.numComponents());

        buf.addComponent(wrappedBuffer(new byte[] { 2, 3 }));
        assertEquals(2, buf.numComponents());

        buf.addComponent(wrappedBuffer(new byte[] { 4, 5, 6 }));

        assertEquals(1, buf.numComponents());
        assertTrue(buf.hasArray());
        assertNotNull(buf.array());
        assertEquals(0, buf.arrayOffset());

        buf.release();
    }

    @Test
    public void testCompositeToSingleBuffer() {
        CompositeByteBuf buf = compositeBuffer(3);

        buf.addComponent(wrappedBuffer(new byte[] {1, 2, 3}));
        assertEquals(1, buf.numComponents());

        buf.addComponent(wrappedBuffer(new byte[] {4}));
        assertEquals(2, buf.numComponents());

        buf.addComponent(wrappedBuffer(new byte[] {5, 6}));
        assertEquals(3, buf.numComponents());

        // NOTE: hard-coding 6 here, since it seems like addComponent doesn't bump the writer index.
        // I'm unsure as to whether or not this is correct behavior
        ByteBuffer nioBuffer = buf.nioBuffer(0, 6);
        byte[] bytes = nioBuffer.array();
        assertEquals(6, bytes.length);
        assertArrayEquals(new byte[] {1, 2, 3, 4, 5, 6}, bytes);

        buf.release();
    }

    @Test
    public void testFullConsolidation() {
        CompositeByteBuf buf = compositeBuffer(Integer.MAX_VALUE);
        buf.addComponent(wrappedBuffer(new byte[] { 1 }));
        buf.addComponent(wrappedBuffer(new byte[] { 2, 3 }));
        buf.addComponent(wrappedBuffer(new byte[] { 4, 5, 6 }));
        buf.consolidate();

        assertEquals(1, buf.numComponents());
        assertTrue(buf.hasArray());
        assertNotNull(buf.array());
        assertEquals(0, buf.arrayOffset());

        buf.release();
    }

    @Test
    public void testRangedConsolidation() {
        CompositeByteBuf buf = compositeBuffer(Integer.MAX_VALUE);
        buf.addComponent(wrappedBuffer(new byte[] { 1 }));
        buf.addComponent(wrappedBuffer(new byte[] { 2, 3 }));
        buf.addComponent(wrappedBuffer(new byte[] { 4, 5, 6 }));
        buf.addComponent(wrappedBuffer(new byte[] { 7, 8, 9, 10 }));
        buf.consolidate(1, 2);

        assertEquals(3, buf.numComponents());
        assertEquals(wrappedBuffer(new byte[] { 1 }), buf.component(0));
        assertEquals(wrappedBuffer(new byte[] { 2, 3, 4, 5, 6 }), buf.component(1));
        assertEquals(wrappedBuffer(new byte[] { 7, 8, 9, 10 }), buf.component(2));

        buf.release();
    }

    @Test
    public void testCompositeWrappedBuffer() {
        ByteBuf header = buffer(12).order(order);
        ByteBuf payload = buffer(512).order(order);

        header.writeBytes(new byte[12]);
        payload.writeBytes(new byte[512]);

        ByteBuf buffer = wrappedBuffer(header, payload);

        assertEquals(12, header.readableBytes());
        assertEquals(512, payload.readableBytes());

        assertEquals(12 + 512, buffer.readableBytes());
        assertEquals(2, buffer.nioBufferCount());

        buffer.release();
    }

    @Test
    public void testSeveralBuffersEquals() {
        ByteBuf a, b;
        // XXX Same tests with several buffers in wrappedCheckedBuffer
        // Different length.
        a = wrappedBuffer(new byte[] { 1 }).order(order);
        b = wrappedBuffer(
                wrappedBuffer(new byte[] { 1 }).order(order),
                wrappedBuffer(new byte[] { 2 }).order(order));
        assertFalse(ByteBufUtil.equals(a, b));

        a.release();
        b.release();

        // Same content, same firstIndex, short length.
        a = wrappedBuffer(new byte[] { 1, 2, 3 }).order(order);
        b = wrappedBuffer(
                wrappedBuffer(new byte[]{1}).order(order),
                wrappedBuffer(new byte[]{2}).order(order),
                wrappedBuffer(new byte[]{3}).order(order));
        assertTrue(ByteBufUtil.equals(a, b));

        a.release();
        b.release();

        // Same content, different firstIndex, short length.
        a = wrappedBuffer(new byte[] { 1, 2, 3 }).order(order);
        b = wrappedBuffer(
                wrappedBuffer(new byte[] { 0, 1, 2, 3, 4 }, 1, 2).order(order),
                wrappedBuffer(new byte[] { 0, 1, 2, 3, 4 }, 3, 1).order(order));
        assertTrue(ByteBufUtil.equals(a, b));

        a.release();
        b.release();

        // Different content, same firstIndex, short length.
        a = wrappedBuffer(new byte[] { 1, 2, 3 }).order(order);
        b = wrappedBuffer(
                wrappedBuffer(new byte[] { 1, 2 }).order(order),
                wrappedBuffer(new byte[] { 4 }).order(order));
        assertFalse(ByteBufUtil.equals(a, b));

        a.release();
        b.release();

        // Different content, different firstIndex, short length.
        a = wrappedBuffer(new byte[] { 1, 2, 3 }).order(order);
        b = wrappedBuffer(
                wrappedBuffer(new byte[] { 0, 1, 2, 4, 5 }, 1, 2).order(order),
                wrappedBuffer(new byte[] { 0, 1, 2, 4, 5 }, 3, 1).order(order));
        assertFalse(ByteBufUtil.equals(a, b));

        a.release();
        b.release();

        // Same content, same firstIndex, long length.
        a = wrappedBuffer(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 }).order(order);
        b = wrappedBuffer(
                wrappedBuffer(new byte[] { 1, 2, 3 }).order(order),
                wrappedBuffer(new byte[] { 4, 5, 6 }).order(order),
                wrappedBuffer(new byte[] { 7, 8, 9, 10 }).order(order));
        assertTrue(ByteBufUtil.equals(a, b));

        a.release();
        b.release();

        // Same content, different firstIndex, long length.
        a = wrappedBuffer(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 }).order(order);
        b = wrappedBuffer(
                wrappedBuffer(new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11}, 1, 5).order(order),
                wrappedBuffer(new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11}, 6, 5).order(order));
        assertTrue(ByteBufUtil.equals(a, b));

        a.release();
        b.release();

        // Different content, same firstIndex, long length.
        a = wrappedBuffer(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 }).order(order);
        b = wrappedBuffer(
                wrappedBuffer(new byte[] { 1, 2, 3, 4, 6 }).order(order),
                wrappedBuffer(new byte[] { 7, 8, 5, 9, 10 }).order(order));
        assertFalse(ByteBufUtil.equals(a, b));

        a.release();
        b.release();

        // Different content, different firstIndex, long length.
        a = wrappedBuffer(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 }).order(order);
        b = wrappedBuffer(
                wrappedBuffer(new byte[] { 0, 1, 2, 3, 4, 6, 7, 8, 5, 9, 10, 11 }, 1, 5).order(order),
                wrappedBuffer(new byte[] { 0, 1, 2, 3, 4, 6, 7, 8, 5, 9, 10, 11 }, 6, 5).order(order));
        assertFalse(ByteBufUtil.equals(a, b));

        a.release();
        b.release();
    }

    @Test
    public void testWrappedBuffer() {

        ByteBuf a = wrappedBuffer(wrappedBuffer(ByteBuffer.allocateDirect(16)));
        assertEquals(16, a.capacity());
        a.release();

        a = wrappedBuffer(wrappedBuffer(new byte[] { 1, 2, 3 }).order(order));
        ByteBuf b = wrappedBuffer(wrappedBuffer(new byte[][] { new byte[] { 1, 2, 3 } }).order(order));
        assertEquals(a, b);

        a.release();
        b.release();

        a = wrappedBuffer(wrappedBuffer(new byte[] { 1, 2, 3 }).order(order));
        b = wrappedBuffer(wrappedBuffer(
                new byte[] { 1 },
                new byte[] { 2 },
                new byte[] { 3 }).order(order));
        assertEquals(a, b);

        a.release();
        b.release();

        a = wrappedBuffer(wrappedBuffer(new byte[] { 1, 2, 3 }).order(order));
        b = wrappedBuffer(new ByteBuf[] {
                wrappedBuffer(new byte[] { 1, 2, 3 }).order(order)
        });
        assertEquals(a, b);

        a.release();
        b.release();

        a = wrappedBuffer(wrappedBuffer(new byte[] { 1, 2, 3 }).order(order));
        b = wrappedBuffer(
                wrappedBuffer(new byte[] { 1 }).order(order),
                wrappedBuffer(new byte[] { 2 }).order(order),
                wrappedBuffer(new byte[] { 3 }).order(order));
        assertEquals(a, b);

        a.release();
        b.release();

        a = wrappedBuffer(wrappedBuffer(new byte[] { 1, 2, 3 })).order(order);
        b = wrappedBuffer(wrappedBuffer(new ByteBuffer[] {
                ByteBuffer.wrap(new byte[] { 1, 2, 3 })
        }));
        assertEquals(a, b);

        a.release();
        b.release();

        a = wrappedBuffer(wrappedBuffer(new byte[] { 1, 2, 3 }).order(order));
        b = wrappedBuffer(wrappedBuffer(
                ByteBuffer.wrap(new byte[] { 1 }),
                ByteBuffer.wrap(new byte[] { 2 }),
                ByteBuffer.wrap(new byte[] { 3 })));
        assertEquals(a, b);

        a.release();
        b.release();
    }

    @Test
    public void testWrittenBuffersEquals() {
        //XXX Same tests than testEquals with written AggregateChannelBuffers
        ByteBuf a, b, c;
        // Different length.
        a = wrappedBuffer(new byte[] { 1  }).order(order);
        b = wrappedBuffer(wrappedBuffer(new byte[] { 1 }, new byte[1])).order(order);
        c = wrappedBuffer(new byte[] { 2 }).order(order);

        // to enable writeBytes
        b.writerIndex(b.writerIndex() - 1);
        b.writeBytes(c);
        assertFalse(ByteBufUtil.equals(a, b));

        a.release();
        b.release();
        c.release();

        // Same content, same firstIndex, short length.
        a = wrappedBuffer(new byte[] { 1, 2, 3 }).order(order);
        b = wrappedBuffer(wrappedBuffer(new byte[] { 1 }, new byte[2])).order(order);
        c = wrappedBuffer(new byte[] { 2 }).order(order);

        // to enable writeBytes
        b.writerIndex(b.writerIndex() - 2);
        b.writeBytes(c);
        c.release();
        c = wrappedBuffer(new byte[] { 3 }).order(order);

        b.writeBytes(c);
        assertTrue(ByteBufUtil.equals(a, b));

        a.release();
        b.release();
        c.release();

        // Same content, different firstIndex, short length.
        a = wrappedBuffer(new byte[] { 1, 2, 3 }).order(order);
        b = wrappedBuffer(wrappedBuffer(new byte[] { 0, 1, 2, 3, 4 }, 1, 3)).order(order);
        c = wrappedBuffer(new byte[] { 0, 1, 2, 3, 4 }, 3, 1).order(order);
        // to enable writeBytes
        b.writerIndex(b.writerIndex() - 1);
        b.writeBytes(c);
        assertTrue(ByteBufUtil.equals(a, b));

        a.release();
        b.release();
        c.release();

        // Different content, same firstIndex, short length.
        a = wrappedBuffer(new byte[] { 1, 2, 3 }).order(order);
        b = wrappedBuffer(wrappedBuffer(new byte[] { 1, 2 }, new byte[1])).order(order);
        c = wrappedBuffer(new byte[] { 4 }).order(order);
        // to enable writeBytes
        b.writerIndex(b.writerIndex() - 1);
        b.writeBytes(c);
        assertFalse(ByteBufUtil.equals(a, b));

        a.release();
        b.release();
        c.release();

        // Different content, different firstIndex, short length.
        a = wrappedBuffer(new byte[] { 1, 2, 3 }).order(order);
        b = wrappedBuffer(wrappedBuffer(new byte[] { 0, 1, 2, 4, 5 }, 1, 3)).order(order);
        c = wrappedBuffer(new byte[] { 0, 1, 2, 4, 5 }, 3, 1).order(order);
        // to enable writeBytes
        b.writerIndex(b.writerIndex() - 1);
        b.writeBytes(c);
        assertFalse(ByteBufUtil.equals(a, b));

        a.release();
        b.release();
        c.release();

        // Same content, same firstIndex, long length.
        a = wrappedBuffer(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 }).order(order);
        b = wrappedBuffer(wrappedBuffer(new byte[] { 1, 2, 3 }, new byte[7])).order(order);
        c = wrappedBuffer(new byte[] { 4, 5, 6 }).order(order);

        // to enable writeBytes
        b.writerIndex(b.writerIndex() - 7);
        b.writeBytes(c);
        c.release();
        c = wrappedBuffer(new byte[] { 7, 8, 9, 10 }).order(order);
        b.writeBytes(c);
        assertTrue(ByteBufUtil.equals(a, b));

        a.release();
        b.release();
        c.release();

        // Same content, different firstIndex, long length.
        a = wrappedBuffer(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 }).order(order);
        b = wrappedBuffer(
                wrappedBuffer(new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11}, 1, 10)).order(order);
        c = wrappedBuffer(new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11}, 6, 5).order(order);
        // to enable writeBytes
        b.writerIndex(b.writerIndex() - 5);
        b.writeBytes(c);
        assertTrue(ByteBufUtil.equals(a, b));

        a.release();
        b.release();
        c.release();

        // Different content, same firstIndex, long length.
        a = wrappedBuffer(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 }).order(order);
        b = wrappedBuffer(wrappedBuffer(new byte[] { 1, 2, 3, 4, 6 }, new byte[5])).order(order);
        c = wrappedBuffer(new byte[] { 7, 8, 5, 9, 10 }).order(order);
        // to enable writeBytes
        b.writerIndex(b.writerIndex() - 5);
        b.writeBytes(c);
        assertFalse(ByteBufUtil.equals(a, b));

        a.release();
        b.release();
        c.release();

        // Different content, different firstIndex, long length.
        a = wrappedBuffer(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 }).order(order);
        b = wrappedBuffer(
                wrappedBuffer(new byte[] { 0, 1, 2, 3, 4, 6, 7, 8, 5, 9, 10, 11 }, 1, 10)).order(order);
        c = wrappedBuffer(new byte[] { 0, 1, 2, 3, 4, 6, 7, 8, 5, 9, 10, 11 }, 6, 5).order(order);
        // to enable writeBytes
        b.writerIndex(b.writerIndex() - 5);
        b.writeBytes(c);
        assertFalse(ByteBufUtil.equals(a, b));

        a.release();
        b.release();
        c.release();
    }

    @Test
    public void testEmptyBuffer() {
        ByteBuf b = wrappedBuffer(new byte[]{1, 2}, new byte[]{3, 4});
        b.readBytes(new byte[4]);
        b.readBytes(EMPTY_BYTES);
        b.release();
    }

    // Test for https://github.com/netty/netty/issues/1060
    @Test
    public void testReadWithEmptyCompositeBuffer() {
        ByteBuf buf = compositeBuffer();
        int n = 65;
        for (int i = 0; i < n; i ++) {
            buf.writeByte(1);
            assertEquals(1, buf.readByte());
        }
        buf.release();
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testComponentMustBeDuplicate() {
        CompositeByteBuf buf = compositeBuffer();
        buf.addComponent(buffer(4, 6).setIndex(1, 3));
        assertThat(buf.component(0), is(instanceOf(AbstractDerivedByteBuf.class)));
        assertThat(buf.component(0).capacity(), is(4));
        assertThat(buf.component(0).maxCapacity(), is(6));
        assertThat(buf.component(0).readableBytes(), is(2));
        buf.release();
    }

    @Test
    public void testReferenceCounts1() {
        ByteBuf c1 = buffer().writeByte(1);
        ByteBuf c2 = buffer().writeByte(2).retain();
        ByteBuf c3 = buffer().writeByte(3).retain(2);

        CompositeByteBuf buf = compositeBuffer();
        assertThat(buf.refCnt(), is(1));
        buf.addComponents(c1, c2, c3);

        assertThat(buf.refCnt(), is(1));

        // Ensure that c[123]'s refCount did not change.
        assertThat(c1.refCnt(), is(1));
        assertThat(c2.refCnt(), is(2));
        assertThat(c3.refCnt(), is(3));

        assertThat(buf.component(0).refCnt(), is(1));
        assertThat(buf.component(1).refCnt(), is(2));
        assertThat(buf.component(2).refCnt(), is(3));

        c3.release(2);
        c2.release();
        buf.release();
    }

    @Test
    public void testReferenceCounts2() {
        ByteBuf c1 = buffer().writeByte(1);
        ByteBuf c2 = buffer().writeByte(2).retain();
        ByteBuf c3 = buffer().writeByte(3).retain(2);

        CompositeByteBuf bufA = compositeBuffer();
        bufA.addComponents(c1, c2, c3).writerIndex(3);

        CompositeByteBuf bufB = compositeBuffer();
        bufB.addComponents(bufA);

        // Ensure that bufA.refCnt() did not change.
        assertThat(bufA.refCnt(), is(1));

        // Ensure that c[123]'s refCnt did not change.
        assertThat(c1.refCnt(), is(1));
        assertThat(c2.refCnt(), is(2));
        assertThat(c3.refCnt(), is(3));

        // This should decrease bufA.refCnt().
        bufB.release();
        assertThat(bufB.refCnt(), is(0));

        // Ensure bufA.refCnt() changed.
        assertThat(bufA.refCnt(), is(0));

        // Ensure that c[123]'s refCnt also changed due to the deallocation of bufA.
        assertThat(c1.refCnt(), is(0));
        assertThat(c2.refCnt(), is(1));
        assertThat(c3.refCnt(), is(2));

        c3.release(2);
        c2.release();
    }

    @Test
    public void testReferenceCounts3() {
        ByteBuf c1 = buffer().writeByte(1);
        ByteBuf c2 = buffer().writeByte(2).retain();
        ByteBuf c3 = buffer().writeByte(3).retain(2);

        CompositeByteBuf buf = compositeBuffer();
        assertThat(buf.refCnt(), is(1));

        List<ByteBuf> components = new ArrayList<ByteBuf>();
        Collections.addAll(components, c1, c2, c3);
        buf.addComponents(components);

        // Ensure that c[123]'s refCount did not change.
        assertThat(c1.refCnt(), is(1));
        assertThat(c2.refCnt(), is(2));
        assertThat(c3.refCnt(), is(3));

        assertThat(buf.component(0).refCnt(), is(1));
        assertThat(buf.component(1).refCnt(), is(2));
        assertThat(buf.component(2).refCnt(), is(3));

        c3.release(2);
        c2.release();
        buf.release();
    }

    @Test
    public void testNestedLayout() {
        CompositeByteBuf buf = compositeBuffer();
        buf.addComponent(
                compositeBuffer()
                        .addComponent(wrappedBuffer(new byte[]{1, 2}))
                        .addComponent(wrappedBuffer(new byte[]{3, 4})).slice(1, 2));

        ByteBuffer[] nioBuffers = buf.nioBuffers(0, 2);
        assertThat(nioBuffers.length, is(2));
        assertThat(nioBuffers[0].remaining(), is(1));
        assertThat(nioBuffers[0].get(), is((byte) 2));
        assertThat(nioBuffers[1].remaining(), is(1));
        assertThat(nioBuffers[1].get(), is((byte) 3));

        buf.release();
    }

    @Test
    public void testRemoveLastComponent() {
        CompositeByteBuf buf = compositeBuffer();
        buf.addComponent(wrappedBuffer(new byte[]{1, 2}));
        assertEquals(1, buf.numComponents());
        buf.removeComponent(0);
        assertEquals(0, buf.numComponents());
        buf.release();
    }

    @Test
    public void testCopyEmpty() {
        CompositeByteBuf buf = compositeBuffer();
        assertEquals(0, buf.numComponents());

        ByteBuf copy = buf.copy();
        assertEquals(0, copy.readableBytes());

        buf.release();
        copy.release();
    }

    @Test
    public void testDuplicateEmpty() {
        CompositeByteBuf buf = compositeBuffer();
        assertEquals(0, buf.numComponents());
        assertEquals(0, buf.duplicate().readableBytes());

        buf.release();
    }

    @Test
    public void testRemoveLastComponentWithOthersLeft() {
        CompositeByteBuf buf = compositeBuffer();
        buf.addComponent(wrappedBuffer(new byte[]{1, 2}));
        buf.addComponent(wrappedBuffer(new byte[]{1, 2}));
        assertEquals(2, buf.numComponents());
        buf.removeComponent(1);
        assertEquals(1, buf.numComponents());
        buf.release();
    }

    @Test
    public void testGatheringWritesHeap() throws Exception {
        testGatheringWrites(buffer().order(order), buffer().order(order));
    }

    @Test
    public void testGatheringWritesDirect() throws Exception {
        testGatheringWrites(directBuffer().order(order), directBuffer().order(order));
    }

    @Test
    public void testGatheringWritesMixes() throws Exception {
        testGatheringWrites(buffer().order(order), directBuffer().order(order));
    }

    @Test
    public void testGatheringWritesHeapPooled() throws Exception {
        testGatheringWrites(PooledByteBufAllocator.DEFAULT.heapBuffer().order(order),
                PooledByteBufAllocator.DEFAULT.heapBuffer().order(order));
    }

    @Test
    public void testGatheringWritesDirectPooled() throws Exception {
        testGatheringWrites(PooledByteBufAllocator.DEFAULT.directBuffer().order(order),
                PooledByteBufAllocator.DEFAULT.directBuffer().order(order));
    }

    @Test
    public void testGatheringWritesMixesPooled() throws Exception {
        testGatheringWrites(PooledByteBufAllocator.DEFAULT.heapBuffer().order(order),
                PooledByteBufAllocator.DEFAULT.directBuffer().order(order));
    }

    private static void testGatheringWrites(ByteBuf buf1, ByteBuf buf2) throws Exception {
        CompositeByteBuf buf = compositeBuffer();
        buf.addComponent(buf1.writeBytes(new byte[]{1, 2}));
        buf.addComponent(buf2.writeBytes(new byte[]{1, 2}));
        buf.writerIndex(3);
        buf.readerIndex(1);

        TestGatheringByteChannel channel = new TestGatheringByteChannel();

        buf.readBytes(channel, 2);

        byte[] data = new byte[2];
        buf.getBytes(1, data);
        assertArrayEquals(data, channel.writtenBytes());

        buf.release();
    }

    @Test
    public void testGatheringWritesPartialHeap() throws Exception {
        testGatheringWritesPartial(buffer().order(order), buffer().order(order), false);
    }

    @Test
    public void testGatheringWritesPartialDirect() throws Exception {
        testGatheringWritesPartial(directBuffer().order(order), directBuffer().order(order), false);
    }

    @Test
    public void testGatheringWritesPartialMixes() throws Exception {
        testGatheringWritesPartial(buffer().order(order), directBuffer().order(order), false);
    }

    @Test
    public void testGatheringWritesPartialHeapSlice() throws Exception {
        testGatheringWritesPartial(buffer().order(order), buffer().order(order), true);
    }

    @Test
    public void testGatheringWritesPartialDirectSlice() throws Exception {
        testGatheringWritesPartial(directBuffer().order(order), directBuffer().order(order), true);
    }

    @Test
    public void testGatheringWritesPartialMixesSlice() throws Exception {
        testGatheringWritesPartial(buffer().order(order), directBuffer().order(order), true);
    }

    @Test
    public void testGatheringWritesPartialHeapPooled() throws Exception {
        testGatheringWritesPartial(PooledByteBufAllocator.DEFAULT.heapBuffer().order(order),
                PooledByteBufAllocator.DEFAULT.heapBuffer().order(order), false);
    }

    @Test
    public void testGatheringWritesPartialDirectPooled() throws Exception {
        testGatheringWritesPartial(PooledByteBufAllocator.DEFAULT.directBuffer().order(order),
                PooledByteBufAllocator.DEFAULT.directBuffer().order(order), false);
    }

    @Test
    public void testGatheringWritesPartialMixesPooled() throws Exception {
        testGatheringWritesPartial(PooledByteBufAllocator.DEFAULT.heapBuffer().order(order),
                PooledByteBufAllocator.DEFAULT.directBuffer().order(order), false);
    }

    @Test
    public void testGatheringWritesPartialHeapPooledSliced() throws Exception {
        testGatheringWritesPartial(PooledByteBufAllocator.DEFAULT.heapBuffer().order(order),
                PooledByteBufAllocator.DEFAULT.heapBuffer().order(order), true);
    }

    @Test
    public void testGatheringWritesPartialDirectPooledSliced() throws Exception {
        testGatheringWritesPartial(PooledByteBufAllocator.DEFAULT.directBuffer().order(order),
                PooledByteBufAllocator.DEFAULT.directBuffer().order(order), true);
    }

    @Test
    public void testGatheringWritesPartialMixesPooledSliced() throws Exception {
        testGatheringWritesPartial(PooledByteBufAllocator.DEFAULT.heapBuffer().order(order),
                PooledByteBufAllocator.DEFAULT.directBuffer().order(order), true);
    }

    private static void testGatheringWritesPartial(ByteBuf buf1, ByteBuf buf2, boolean slice) throws Exception {
        CompositeByteBuf buf = compositeBuffer();
        buf1.writeBytes(new byte[]{1, 2, 3, 4});
        buf2.writeBytes(new byte[]{1, 2, 3, 4});
        if (slice) {
            buf1 = buf1.readerIndex(1).slice();
            buf2 = buf2.writerIndex(3).slice();
            buf.addComponent(buf1);
            buf.addComponent(buf2);
            buf.writerIndex(6);
        } else {
            buf.addComponent(buf1);
            buf.addComponent(buf2);
            buf.writerIndex(7);
            buf.readerIndex(1);
        }

        TestGatheringByteChannel channel = new TestGatheringByteChannel(1);

        while (buf.isReadable()) {
            buf.readBytes(channel, buf.readableBytes());
        }

        byte[] data = new byte[6];

        if (slice) {
            buf.getBytes(0, data);
        } else {
            buf.getBytes(1, data);
        }
        assertArrayEquals(data, channel.writtenBytes());

        buf.release();
    }

    @Test
    public void testGatheringWritesSingleHeap() throws Exception {
        testGatheringWritesSingleBuf(buffer().order(order));
    }

    @Test
    public void testGatheringWritesSingleDirect() throws Exception {
        testGatheringWritesSingleBuf(directBuffer().order(order));
    }

    private static void testGatheringWritesSingleBuf(ByteBuf buf1) throws Exception {
        CompositeByteBuf buf = compositeBuffer();
        buf.addComponent(buf1.writeBytes(new byte[]{1, 2, 3, 4}));
        buf.writerIndex(3);
        buf.readerIndex(1);

        TestGatheringByteChannel channel = new TestGatheringByteChannel();
        buf.readBytes(channel, 2);

        byte[] data = new byte[2];
        buf.getBytes(1, data);
        assertArrayEquals(data, channel.writtenBytes());

        buf.release();
    }

    @Override
    @Test
    public void testInternalNioBuffer() {
        // ignore
    }

    @Test
    public void testisDirectMultipleBufs() {
        CompositeByteBuf buf = compositeBuffer();
        assertFalse(buf.isDirect());

        buf.addComponent(directBuffer().writeByte(1));

        assertTrue(buf.isDirect());
        buf.addComponent(directBuffer().writeByte(1));
        assertTrue(buf.isDirect());

        buf.addComponent(buffer().writeByte(1));
        assertFalse(buf.isDirect());

        buf.release();
    }

    // See https://github.com/netty/netty/issues/1976
    @Test
    public void testDiscardSomeReadBytes() {
        CompositeByteBuf cbuf = compositeBuffer();
        int len = 8 * 4;
        for (int i = 0; i < len; i += 4) {
            ByteBuf buf = buffer().writeInt(i);
            cbuf.capacity(cbuf.writerIndex()).addComponent(buf).writerIndex(i + 4);
        }
        cbuf.writeByte(1);

        byte[] me = new byte[len];
        cbuf.readBytes(me);
        cbuf.readByte();

        cbuf.discardSomeReadBytes();

        cbuf.release();
    }

    @Test
    public void testAddEmptyBufferRelease() {
        CompositeByteBuf cbuf = compositeBuffer();
        ByteBuf buf = buffer();
        assertEquals(1, buf.refCnt());
        cbuf.addComponent(buf);
        assertEquals(1, buf.refCnt());

        cbuf.release();
        assertEquals(0, buf.refCnt());
    }

    @Test
    public void testAddEmptyBuffersRelease() {
        CompositeByteBuf cbuf = compositeBuffer();
        ByteBuf buf = buffer();
        ByteBuf buf2 = buffer().writeInt(1);
        ByteBuf buf3 = buffer();

        assertEquals(1, buf.refCnt());
        assertEquals(1, buf2.refCnt());
        assertEquals(1, buf3.refCnt());

        cbuf.addComponents(buf, buf2, buf3);
        assertEquals(1, buf.refCnt());
        assertEquals(1, buf2.refCnt());
        assertEquals(1, buf3.refCnt());

        cbuf.release();
        assertEquals(0, buf.refCnt());
        assertEquals(0, buf2.refCnt());
        assertEquals(0, buf3.refCnt());
    }

    @Test
    public void testAddEmptyBufferInMiddle() {
        CompositeByteBuf cbuf = compositeBuffer();
        ByteBuf buf1 = buffer().writeByte((byte) 1);
        cbuf.addComponent(true, buf1);
        cbuf.addComponent(true, EMPTY_BUFFER);
        ByteBuf buf3 = buffer().writeByte((byte) 2);
        cbuf.addComponent(true, buf3);

        assertEquals(2, cbuf.readableBytes());
        assertEquals((byte) 1, cbuf.readByte());
        assertEquals((byte) 2, cbuf.readByte());

        assertSame(EMPTY_BUFFER, cbuf.internalComponent(1));
        assertNotSame(EMPTY_BUFFER, cbuf.internalComponentAtOffset(1));
        cbuf.release();
    }

    @Test
    public void testIterator() {
        CompositeByteBuf cbuf = compositeBuffer();
        cbuf.addComponent(EMPTY_BUFFER);
        cbuf.addComponent(EMPTY_BUFFER);

        Iterator<ByteBuf> it = cbuf.iterator();
        assertTrue(it.hasNext());
        assertSame(EMPTY_BUFFER, it.next());
        assertTrue(it.hasNext());
        assertSame(EMPTY_BUFFER, it.next());
        assertFalse(it.hasNext());

        try {
            it.next();
            fail();
        } catch (NoSuchElementException e) {
            //Expected
        }
        cbuf.release();
    }

    @Test
    public void testEmptyIterator() {
        CompositeByteBuf cbuf = compositeBuffer();

        Iterator<ByteBuf> it = cbuf.iterator();
        assertFalse(it.hasNext());

        try {
            it.next();
            fail();
        } catch (NoSuchElementException e) {
            //Expected
        }
        cbuf.release();
    }

    @Test(expected = ConcurrentModificationException.class)
    public void testIteratorConcurrentModificationAdd() {
        CompositeByteBuf cbuf = compositeBuffer();
        cbuf.addComponent(EMPTY_BUFFER);

        Iterator<ByteBuf> it = cbuf.iterator();
        cbuf.addComponent(EMPTY_BUFFER);

        assertTrue(it.hasNext());
        try {
            it.next();
        } finally {
            cbuf.release();
        }
    }

    @Test(expected = ConcurrentModificationException.class)
    public void testIteratorConcurrentModificationRemove() {
        CompositeByteBuf cbuf = compositeBuffer();
        cbuf.addComponent(EMPTY_BUFFER);

        Iterator<ByteBuf> it = cbuf.iterator();
        cbuf.removeComponent(0);

        assertTrue(it.hasNext());
        try {
            it.next();
        } finally {
            cbuf.release();
        }
    }

    @Test
    public void testReleasesItsComponents() {
        ByteBuf buffer = PooledByteBufAllocator.DEFAULT.buffer(); // 1

        buffer.writeBytes(new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10});

        ByteBuf s1 = buffer.readSlice(2).retain(); // 2
        ByteBuf s2 = s1.readSlice(2).retain(); // 3
        ByteBuf s3 = s2.readSlice(2).retain(); // 4
        ByteBuf s4 = s3.readSlice(2).retain(); // 5

        ByteBuf composite = PooledByteBufAllocator.DEFAULT.compositeBuffer()
            .addComponent(s1)
            .addComponents(s2, s3, s4)
            .order(ByteOrder.LITTLE_ENDIAN);

        assertEquals(1, composite.refCnt());
        assertEquals(5, buffer.refCnt());

        // releasing composite should release the 4 components
        ReferenceCountUtil.release(composite);
        assertEquals(0, composite.refCnt());
        assertEquals(1, buffer.refCnt());

        // last remaining ref to buffer
        ReferenceCountUtil.release(buffer);
        assertEquals(0, buffer.refCnt());
    }

    @Test
    public void testAllocatorIsSameWhenCopy() {
        testAllocatorIsSameWhenCopy(false);
    }

    @Test
    public void testAllocatorIsSameWhenCopyUsingIndexAndLength() {
        testAllocatorIsSameWhenCopy(true);
    }

    private void testAllocatorIsSameWhenCopy(boolean withIndexAndLength) {
        ByteBuf buffer = newBuffer(8);
        buffer.writeZero(4);
        ByteBuf copy = withIndexAndLength ? buffer.copy(0, 4) : buffer.copy();
        assertEquals(buffer, copy);
        assertEquals(buffer.isDirect(), copy.isDirect());
        assertSame(buffer.alloc(), copy.alloc());
        buffer.release();
        copy.release();
    }
}
