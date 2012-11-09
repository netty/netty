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

import static io.netty.buffer.Unpooled.*;
import static org.junit.Assert.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/**
 * An abstract test class for composite channel buffers
 */
public abstract class AbstractCompositeChannelBufferTest extends
        AbstractChannelBufferTest {

    private final ByteOrder order;

    protected AbstractCompositeChannelBufferTest(ByteOrder order) {
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
        assertFalse(buffer.writable());
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
        CompositeByteBuf buf = (CompositeByteBuf) wrappedBuffer(new byte[]{1, 2, 3, 4, 5}, new byte[]{4, 5, 6, 7, 8, 9, 26});

        //Ensure that a random place will be fine
        assertEquals(buf.componentAtOffset(2).capacity(), 5);

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
        CompositeByteBuf buf = compositeBuffer(Integer.MAX_VALUE);
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
    }

    @Test
    public void testCompositeWrappedBuffer() {
        ByteBuf header = buffer(12).order(order);
        ByteBuf payload = buffer(512).order(order);

        header.writeBytes(new byte[12]);
        payload.writeBytes(new byte[512]);

        ByteBuf buffer = wrappedBuffer(header, payload);

        assertTrue(header.readableBytes() == 12);
        assertTrue(payload.readableBytes() == 512);

        assertEquals(12 + 512, buffer.readableBytes());
        assertFalse(buffer.hasNioBuffer());
    }

    @Test
    public void testSeveralBuffersEquals() {
        ByteBuf a, b;
        //XXX Same tests with several buffers in wrappedCheckedBuffer
        // Different length.
        a = wrappedBuffer(new byte[] { 1  }).order(order);
        b = wrappedBuffer(wrappedBuffer(new byte[] { 1 }).order(order),
                wrappedBuffer(new byte[] { 2 }).order(order));
        assertFalse(ByteBufUtil.equals(a, b));

        // Same content, same firstIndex, short length.
        a = wrappedBuffer(new byte[] { 1, 2, 3 }).order(order);
        b = wrappedBuffer(wrappedBuffer(new byte[] { 1 }).order(order),
                wrappedBuffer(new byte[] { 2 }).order(order),
                wrappedBuffer(new byte[] { 3 }).order(order));
        assertTrue(ByteBufUtil.equals(a, b));

        // Same content, different firstIndex, short length.
        a = wrappedBuffer(new byte[] { 1, 2, 3 }).order(order);
        b = wrappedBuffer(wrappedBuffer(new byte[] { 0, 1, 2, 3, 4 }, 1, 2).order(order),
                wrappedBuffer(new byte[] { 0, 1, 2, 3, 4 }, 3, 1).order(order));
        assertTrue(ByteBufUtil.equals(a, b));

        // Different content, same firstIndex, short length.
        a = wrappedBuffer(new byte[] { 1, 2, 3 }).order(order);
        b = wrappedBuffer(wrappedBuffer(new byte[] { 1, 2 }).order(order),
                wrappedBuffer(new byte[] { 4 }).order(order));
        assertFalse(ByteBufUtil.equals(a, b));

        // Different content, different firstIndex, short length.
        a = wrappedBuffer(new byte[] { 1, 2, 3 }).order(order);
        b = wrappedBuffer(wrappedBuffer(new byte[] { 0, 1, 2, 4, 5 }, 1, 2).order(order),
                wrappedBuffer(new byte[] { 0, 1, 2, 4, 5 }, 3, 1).order(order));
        assertFalse(ByteBufUtil.equals(a, b));

        // Same content, same firstIndex, long length.
        a = wrappedBuffer(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 }).order(order);
        b = wrappedBuffer(wrappedBuffer(new byte[] { 1, 2, 3 }).order(order),
                wrappedBuffer(new byte[] { 4, 5, 6 }).order(order),
                wrappedBuffer(new byte[] { 7, 8, 9, 10 }).order(order));
        assertTrue(ByteBufUtil.equals(a, b));

        // Same content, different firstIndex, long length.
        a = wrappedBuffer(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 }).order(order);
        b = wrappedBuffer(wrappedBuffer(new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11}, 1, 5).order(order),
                wrappedBuffer(new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11}, 6, 5).order(order));
        assertTrue(ByteBufUtil.equals(a, b));

        // Different content, same firstIndex, long length.
        a = wrappedBuffer(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 }).order(order);
        b = wrappedBuffer(wrappedBuffer(new byte[] { 1, 2, 3, 4, 6 }).order(order),
                wrappedBuffer(new byte[] { 7, 8, 5, 9, 10 }).order(order));
        assertFalse(ByteBufUtil.equals(a, b));

        // Different content, different firstIndex, long length.
        a = wrappedBuffer(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 }).order(order);
        b = wrappedBuffer(wrappedBuffer(new byte[] { 0, 1, 2, 3, 4, 6, 7, 8, 5, 9, 10, 11 }, 1, 5).order(order),
                wrappedBuffer(new byte[] { 0, 1, 2, 3, 4, 6, 7, 8, 5, 9, 10, 11 }, 6, 5).order(order));
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
                wrappedBuffer(wrappedBuffer(
                        new byte[] { 1 },
                        new byte[] { 2 },
                        new byte[] { 3 }).order(order)));

        assertEquals(
                wrappedBuffer(wrappedBuffer(new byte[] { 1, 2, 3 }).order(order)),
                wrappedBuffer(new ByteBuf[] {
                        wrappedBuffer(new byte[] { 1, 2, 3 }).order(order)
                }));

        assertEquals(
                wrappedBuffer(wrappedBuffer(new byte[] { 1, 2, 3 }).order(order)),
                wrappedBuffer(
                        wrappedBuffer(new byte[] { 1 }).order(order),
                        wrappedBuffer(new byte[] { 2 }).order(order),
                        wrappedBuffer(new byte[] { 3 }).order(order)));

        assertEquals(
                wrappedBuffer(wrappedBuffer(new byte[] { 1, 2, 3 }).order(order)),
                wrappedBuffer(wrappedBuffer(new ByteBuffer[] {
                        ByteBuffer.wrap(new byte[] { 1, 2, 3 })
                })));

        assertEquals(
                wrappedBuffer(wrappedBuffer(new byte[] { 1, 2, 3 }).order(order)),
                wrappedBuffer(wrappedBuffer(
                        ByteBuffer.wrap(new byte[] { 1 }),
                        ByteBuffer.wrap(new byte[] { 2 }),
                        ByteBuffer.wrap(new byte[] { 3 }))));
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
        b.writeBytes(
                wrappedBuffer(new byte[] { 2 }).order(order));
        assertFalse(ByteBufUtil.equals(a, b));

        // Same content, same firstIndex, short length.
        a = wrappedBuffer(new byte[] { 1, 2, 3 }).order(order);
        b = wrappedBuffer(wrappedBuffer(new byte[] { 1 }, new byte[2]).order(order));
        // to enable writeBytes
        b.writerIndex(b.writerIndex() - 2);
        b.writeBytes(
                wrappedBuffer(new byte[] { 2 }).order(order));
        b.writeBytes(wrappedBuffer(new byte[] { 3 }).order(order));
        assertTrue(ByteBufUtil.equals(a, b));

        // Same content, different firstIndex, short length.
        a = wrappedBuffer(new byte[] { 1, 2, 3 }).order(order);
        b = wrappedBuffer(wrappedBuffer(new byte[] { 0, 1, 2, 3, 4 }, 1, 3).order(order));
        // to enable writeBytes
        b.writerIndex(b.writerIndex() - 1);
        b.writeBytes(
                wrappedBuffer(new byte[] { 0, 1, 2, 3, 4 }, 3, 1).order(order));
        assertTrue(ByteBufUtil.equals(a, b));

        // Different content, same firstIndex, short length.
        a = wrappedBuffer(new byte[] { 1, 2, 3 }).order(order);
        b = wrappedBuffer(wrappedBuffer(new byte[] { 1, 2 }, new byte[1]).order(order));
        // to enable writeBytes
        b.writerIndex(b.writerIndex() - 1);
        b.writeBytes(
                wrappedBuffer(new byte[] { 4 }).order(order));
        assertFalse(ByteBufUtil.equals(a, b));

        // Different content, different firstIndex, short length.
        a = wrappedBuffer(new byte[] { 1, 2, 3 }).order(order);
        b = wrappedBuffer(wrappedBuffer(new byte[] { 0, 1, 2, 4, 5 }, 1, 3).order(order));
        // to enable writeBytes
        b.writerIndex(b.writerIndex() - 1);
        b.writeBytes(
                wrappedBuffer(new byte[] { 0, 1, 2, 4, 5 }, 3, 1).order(order));
        assertFalse(ByteBufUtil.equals(a, b));

        // Same content, same firstIndex, long length.
        a = wrappedBuffer(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 }).order(order);
        b = wrappedBuffer(wrappedBuffer(new byte[] { 1, 2, 3 }, new byte[7]).order(order));
        // to enable writeBytes
        b.writerIndex(b.writerIndex() - 7);
        b.writeBytes(
                wrappedBuffer(new byte[] { 4, 5, 6 }).order(order));
        b.writeBytes(
                wrappedBuffer(new byte[] { 7, 8, 9, 10 }).order(order));
        assertTrue(ByteBufUtil.equals(a, b));

        // Same content, different firstIndex, long length.
        a = wrappedBuffer(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 }).order(order);
        b = wrappedBuffer(wrappedBuffer(new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11}, 1, 10).order(order));
        // to enable writeBytes
        b.writerIndex(b.writerIndex() - 5);
        b.writeBytes(
                wrappedBuffer(new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11}, 6, 5).order(order));
        assertTrue(ByteBufUtil.equals(a, b));

        // Different content, same firstIndex, long length.
        a = wrappedBuffer(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 }).order(order);
        b = wrappedBuffer(wrappedBuffer(new byte[] { 1, 2, 3, 4, 6 }, new byte[5]).order(order));
        // to enable writeBytes
        b.writerIndex(b.writerIndex() - 5);
        b.writeBytes(
                wrappedBuffer(new byte[] { 7, 8, 5, 9, 10 }).order(order));
        assertFalse(ByteBufUtil.equals(a, b));

        // Different content, different firstIndex, long length.
        a = wrappedBuffer(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 }).order(order);
        b = wrappedBuffer(wrappedBuffer(new byte[] { 0, 1, 2, 3, 4, 6, 7, 8, 5, 9, 10, 11 }, 1, 10).order(order));
        // to enable writeBytes
        b.writerIndex(b.writerIndex() - 5);
        b.writeBytes(
                wrappedBuffer(new byte[] { 0, 1, 2, 3, 4, 6, 7, 8, 5, 9, 10, 11 }, 6, 5).order(order));
        assertFalse(ByteBufUtil.equals(a, b));
    }

    @Test 
    public void testEmptyBuffer() {
        ByteBuf b = wrappedBuffer(new byte[] {1, 2}, new byte[] {3, 4});
        b.readBytes(new byte[4]);
        b.readBytes(new byte[0]);
    }
}
