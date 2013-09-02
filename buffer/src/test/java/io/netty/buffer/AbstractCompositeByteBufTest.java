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

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.netty.buffer.Unpooled.*;
import static io.netty.util.internal.EmptyArrays.*;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

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

    private List<ByteBuf> buffers;
    private ByteBuf buffer;

    @Override
    protected ByteBuf newBuffer(int length) {
        buffers = new ArrayList<ByteBuf>();
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

        buffer = Unpooled.wrappedBuffer(
                Integer.MAX_VALUE, buffers.toArray(new ByteBuf[buffers.size()])).order(order);

        // Truncate to the requested capacity.
        buffer.capacity(length);

        assertEquals(length, buffer.capacity());
        assertEquals(length, buffer.readableBytes());
        assertFalse(buffer.isWritable());
        buffer.writerIndex(0);
        return buffer;
    }

    @Override
    protected ByteBuf[] components() {
        return buffers.toArray(new ByteBuf[buffers.size()]);
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
    }

    @Test
    public void testDiscardReadBytes3() {
        ByteBuf a, b;
        a = wrappedBuffer(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 }).order(order);
        b = freeLater(wrappedBuffer(
                wrappedBuffer(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 }, 0, 5).order(order),
                wrappedBuffer(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 }, 5, 5).order(order)));
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
    }

    @Test
    public void testFullConsolidation() {
        CompositeByteBuf buf = freeLater(compositeBuffer(Integer.MAX_VALUE));
        buf.addComponent(wrappedBuffer(new byte[] { 1 }));
        buf.addComponent(wrappedBuffer(new byte[] { 2, 3 }));
        buf.addComponent(wrappedBuffer(new byte[] { 4, 5, 6 }));
        buf.consolidate();

        assertEquals(1, buf.numComponents());
        assertTrue(buf.hasArray());
        assertNotNull(buf.array());
        assertEquals(0, buf.arrayOffset());
    }

    @Test
    public void testRangedConsolidation() {
        CompositeByteBuf buf = freeLater(compositeBuffer(Integer.MAX_VALUE));
        buf.addComponent(wrappedBuffer(new byte[] { 1 }));
        buf.addComponent(wrappedBuffer(new byte[] { 2, 3 }));
        buf.addComponent(wrappedBuffer(new byte[] { 4, 5, 6 }));
        buf.addComponent(wrappedBuffer(new byte[] { 7, 8, 9, 10 }));
        buf.consolidate(1, 2);

        assertEquals(3, buf.numComponents());
        assertEquals(wrappedBuffer(new byte[] { 1 }), buf.component(0));
        assertEquals(wrappedBuffer(new byte[] { 2, 3, 4, 5, 6 }), buf.component(1));
        assertEquals(wrappedBuffer(new byte[] { 7, 8, 9, 10 }), buf.component(2));
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
    }

    @Test
    public void testSeveralBuffersEquals() {
        ByteBuf a, b;
        // XXX Same tests with several buffers in wrappedCheckedBuffer
        // Different length.
        a = freeLater(wrappedBuffer(new byte[] { 1 }).order(order));
        b = freeLater(wrappedBuffer(
                wrappedBuffer(new byte[] { 1 }).order(order),
                wrappedBuffer(new byte[] { 2 }).order(order)));
        assertFalse(ByteBufUtil.equals(a, b));

        // Same content, same firstIndex, short length.
        a = freeLater(wrappedBuffer(new byte[] { 1, 2, 3 }).order(order));
        b = freeLater(wrappedBuffer(
                wrappedBuffer(new byte[]{1}).order(order),
                wrappedBuffer(new byte[]{2}).order(order),
                wrappedBuffer(new byte[]{3}).order(order)));
        assertTrue(ByteBufUtil.equals(a, b));

        // Same content, different firstIndex, short length.
        a = freeLater(wrappedBuffer(new byte[] { 1, 2, 3 }).order(order));
        b = freeLater(wrappedBuffer(
                wrappedBuffer(new byte[] { 0, 1, 2, 3, 4 }, 1, 2).order(order),
                wrappedBuffer(new byte[] { 0, 1, 2, 3, 4 }, 3, 1).order(order)));
        assertTrue(ByteBufUtil.equals(a, b));

        // Different content, same firstIndex, short length.
        a = freeLater(wrappedBuffer(new byte[] { 1, 2, 3 }).order(order));
        b = freeLater(wrappedBuffer(
                wrappedBuffer(new byte[] { 1, 2 }).order(order),
                wrappedBuffer(new byte[] { 4 }).order(order)));
        assertFalse(ByteBufUtil.equals(a, b));

        // Different content, different firstIndex, short length.
        a = freeLater(wrappedBuffer(new byte[] { 1, 2, 3 }).order(order));
        b = freeLater(wrappedBuffer(
                wrappedBuffer(new byte[] { 0, 1, 2, 4, 5 }, 1, 2).order(order),
                wrappedBuffer(new byte[] { 0, 1, 2, 4, 5 }, 3, 1).order(order)));
        assertFalse(ByteBufUtil.equals(a, b));

        // Same content, same firstIndex, long length.
        a = freeLater(wrappedBuffer(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 }).order(order));
        b = freeLater(wrappedBuffer(
                wrappedBuffer(new byte[] { 1, 2, 3 }).order(order),
                wrappedBuffer(new byte[] { 4, 5, 6 }).order(order),
                wrappedBuffer(new byte[] { 7, 8, 9, 10 }).order(order)));
        assertTrue(ByteBufUtil.equals(a, b));

        // Same content, different firstIndex, long length.
        a = freeLater(wrappedBuffer(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 }).order(order));
        b = freeLater(wrappedBuffer(
                wrappedBuffer(new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11}, 1, 5).order(order),
                wrappedBuffer(new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11}, 6, 5).order(order)));
        assertTrue(ByteBufUtil.equals(a, b));

        // Different content, same firstIndex, long length.
        a = freeLater(wrappedBuffer(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 }).order(order));
        b = freeLater(wrappedBuffer(
                wrappedBuffer(new byte[] { 1, 2, 3, 4, 6 }).order(order),
                wrappedBuffer(new byte[] { 7, 8, 5, 9, 10 }).order(order)));
        assertFalse(ByteBufUtil.equals(a, b));

        // Different content, different firstIndex, long length.
        a = freeLater(wrappedBuffer(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 }).order(order));
        b = freeLater(wrappedBuffer(
                wrappedBuffer(new byte[] { 0, 1, 2, 3, 4, 6, 7, 8, 5, 9, 10, 11 }, 1, 5).order(order),
                wrappedBuffer(new byte[] { 0, 1, 2, 3, 4, 6, 7, 8, 5, 9, 10, 11 }, 6, 5).order(order)));
        assertFalse(ByteBufUtil.equals(a, b));
    }

    @Test
    public void testWrappedBuffer() {

        assertEquals(16, wrappedBuffer(wrappedBuffer(ByteBuffer.allocateDirect(16))).capacity());

        assertEquals(
                wrappedBuffer(wrappedBuffer(new byte[] { 1, 2, 3 }).order(order)),
                wrappedBuffer(wrappedBuffer(new byte[][] { new byte[] { 1, 2, 3 } }).order(order)));

        assertEquals(
                wrappedBuffer(wrappedBuffer(new byte[] { 1, 2, 3 }).order(order)),
                freeLater(wrappedBuffer(wrappedBuffer(
                        new byte[] { 1 },
                        new byte[] { 2 },
                        new byte[] { 3 }).order(order))));

        assertEquals(
                wrappedBuffer(wrappedBuffer(new byte[] { 1, 2, 3 }).order(order)),
                wrappedBuffer(new ByteBuf[] {
                        wrappedBuffer(new byte[] { 1, 2, 3 }).order(order)
                }));

        assertEquals(
                wrappedBuffer(wrappedBuffer(new byte[] { 1, 2, 3 }).order(order)),
                freeLater(wrappedBuffer(
                        wrappedBuffer(new byte[] { 1 }).order(order),
                        wrappedBuffer(new byte[] { 2 }).order(order),
                        wrappedBuffer(new byte[] { 3 }).order(order))));

        assertEquals(
                wrappedBuffer(wrappedBuffer(new byte[] { 1, 2, 3 }).order(order)),
                wrappedBuffer(wrappedBuffer(new ByteBuffer[] {
                        ByteBuffer.wrap(new byte[] { 1, 2, 3 })
                })));

        assertEquals(
                wrappedBuffer(wrappedBuffer(new byte[] { 1, 2, 3 }).order(order)),
                freeLater(wrappedBuffer(wrappedBuffer(
                        ByteBuffer.wrap(new byte[] { 1 }),
                        ByteBuffer.wrap(new byte[] { 2 }),
                        ByteBuffer.wrap(new byte[] { 3 })))));
    }

    @Test
    public void testWrittenBuffersEquals() {
        //XXX Same tests than testEquals with written AggregateChannelBuffers
        ByteBuf a, b;
        // Different length.
        a = wrappedBuffer(new byte[] { 1  }).order(order);
        b = wrappedBuffer(wrappedBuffer(new byte[] { 1 }, new byte[1]).order(order));
        // to enable writeBytes
        b.writerIndex(b.writerIndex() - 1);
        b.writeBytes(wrappedBuffer(new byte[] { 2 }).order(order));
        assertFalse(ByteBufUtil.equals(a, b));

        // Same content, same firstIndex, short length.
        a = wrappedBuffer(new byte[] { 1, 2, 3 }).order(order);
        b = wrappedBuffer(wrappedBuffer(new byte[] { 1 }, new byte[2]).order(order));
        // to enable writeBytes
        b.writerIndex(b.writerIndex() - 2);
        b.writeBytes(wrappedBuffer(new byte[] { 2 }).order(order));
        b.writeBytes(wrappedBuffer(new byte[] { 3 }).order(order));
        assertTrue(ByteBufUtil.equals(a, b));

        // Same content, different firstIndex, short length.
        a = wrappedBuffer(new byte[] { 1, 2, 3 }).order(order);
        b = wrappedBuffer(wrappedBuffer(new byte[] { 0, 1, 2, 3, 4 }, 1, 3).order(order));
        // to enable writeBytes
        b.writerIndex(b.writerIndex() - 1);
        b.writeBytes(wrappedBuffer(new byte[] { 0, 1, 2, 3, 4 }, 3, 1).order(order));
        assertTrue(ByteBufUtil.equals(a, b));

        // Different content, same firstIndex, short length.
        a = wrappedBuffer(new byte[] { 1, 2, 3 }).order(order);
        b = freeLater(wrappedBuffer(wrappedBuffer(new byte[] { 1, 2 }, new byte[1]).order(order)));
        // to enable writeBytes
        b.writerIndex(b.writerIndex() - 1);
        b.writeBytes(wrappedBuffer(new byte[] { 4 }).order(order));
        assertFalse(ByteBufUtil.equals(a, b));

        // Different content, different firstIndex, short length.
        a = wrappedBuffer(new byte[] { 1, 2, 3 }).order(order);
        b = wrappedBuffer(wrappedBuffer(new byte[] { 0, 1, 2, 4, 5 }, 1, 3).order(order));
        // to enable writeBytes
        b.writerIndex(b.writerIndex() - 1);
        b.writeBytes(wrappedBuffer(new byte[] { 0, 1, 2, 4, 5 }, 3, 1).order(order));
        assertFalse(ByteBufUtil.equals(a, b));

        // Same content, same firstIndex, long length.
        a = wrappedBuffer(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 }).order(order);
        b = freeLater(wrappedBuffer(wrappedBuffer(new byte[] { 1, 2, 3 }, new byte[7])).order(order));
        // to enable writeBytes
        b.writerIndex(b.writerIndex() - 7);
        b.writeBytes(wrappedBuffer(new byte[] { 4, 5, 6 }).order(order));
        b.writeBytes(wrappedBuffer(new byte[] { 7, 8, 9, 10 }).order(order));
        assertTrue(ByteBufUtil.equals(a, b));

        // Same content, different firstIndex, long length.
        a = wrappedBuffer(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 }).order(order);
        b = wrappedBuffer(wrappedBuffer(new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11}, 1, 10).order(order));
        // to enable writeBytes
        b.writerIndex(b.writerIndex() - 5);
        b.writeBytes(wrappedBuffer(new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11}, 6, 5).order(order));
        assertTrue(ByteBufUtil.equals(a, b));

        // Different content, same firstIndex, long length.
        a = wrappedBuffer(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 }).order(order);
        b = freeLater(wrappedBuffer(wrappedBuffer(new byte[] { 1, 2, 3, 4, 6 }, new byte[5])).order(order));
        // to enable writeBytes
        b.writerIndex(b.writerIndex() - 5);
        b.writeBytes(wrappedBuffer(new byte[] { 7, 8, 5, 9, 10 }).order(order));
        assertFalse(ByteBufUtil.equals(a, b));

        // Different content, different firstIndex, long length.
        a = wrappedBuffer(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 }).order(order);
        b = wrappedBuffer(wrappedBuffer(new byte[] { 0, 1, 2, 3, 4, 6, 7, 8, 5, 9, 10, 11 }, 1, 10).order(order));
        // to enable writeBytes
        b.writerIndex(b.writerIndex() - 5);
        b.writeBytes(wrappedBuffer(new byte[] { 0, 1, 2, 3, 4, 6, 7, 8, 5, 9, 10, 11 }, 6, 5).order(order));
        assertFalse(ByteBufUtil.equals(a, b));
    }

    @Test
    public void testEmptyBuffer() {
        ByteBuf b = freeLater(wrappedBuffer(new byte[]{1, 2}, new byte[]{3, 4}));
        b.readBytes(new byte[4]);
        b.readBytes(EMPTY_BYTES);
    }

    // Test for https://github.com/netty/netty/issues/1060
    @Test
    public void testReadWithEmptyCompositeBuffer() {
        ByteBuf buf = freeLater(compositeBuffer());
        int n = 65;
        for (int i = 0; i < n; i ++) {
            buf.writeByte(1);
            assertEquals(1, buf.readByte());
        }
    }

    @Test
    public void testComponentMustBeSlice() {
        CompositeByteBuf buf = freeLater(compositeBuffer());
        buf.addComponent(buffer(4).setIndex(1, 3));
        assertThat(buf.component(0), is(instanceOf(SlicedByteBuf.class)));
        assertThat(buf.component(0).capacity(), is(2));
        assertThat(buf.component(0).maxCapacity(), is(2));
    }

    @Test
    public void testReferenceCounts1() {
        ByteBuf c1 = buffer().writeByte(1);
        ByteBuf c2 = buffer().writeByte(2).retain();
        ByteBuf c3 = buffer().writeByte(3).retain(2);

        CompositeByteBuf buf = freeLater(compositeBuffer());
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

        CompositeByteBuf buf = freeLater(compositeBuffer());
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
    }

    @Test
    public void testNestedLayout() {
        CompositeByteBuf buf = freeLater(compositeBuffer());
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
    }

    @Test
    public void testRemoveLastComponent() {
        CompositeByteBuf buf = freeLater(compositeBuffer());
        buf.addComponent(wrappedBuffer(new byte[]{1, 2}));
        assertEquals(1, buf.numComponents());
        buf.removeComponent(0);
        assertEquals(0, buf.numComponents());
    }

    @Test
    public void testCopyEmpty() {
        CompositeByteBuf buf = freeLater(compositeBuffer());
        assertEquals(0, buf.numComponents());
        assertEquals(0, freeLater(buf.copy()).readableBytes());
    }

    @Test
    public void testDuplicateEmpty() {
        CompositeByteBuf buf = freeLater(compositeBuffer());
        assertEquals(0, buf.numComponents());
        assertEquals(0, freeLater(buf.duplicate()).readableBytes());
    }

    @Override
    @Test
    public void testInternalNioBuffer() {
        // ignore
    }
}
