/*
 * Copyright 2013 The Netty Project
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

package io.netty.buffer;

import org.junit.jupiter.api.Test;

import java.nio.ByteOrder;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Tests wrapping a wrapped buffer does not go way too deep chaining.
 */
public class ByteBufDerivationTest {

    @Test
    public void testSlice() throws Exception {
        ByteBuf buf = Unpooled.buffer(8).setIndex(1, 7);
        ByteBuf slice = buf.slice(1, 7);

        assertInstanceOf(AbstractUnpooledSlicedByteBuf.class, slice);
        assertSame(buf, slice.unwrap());
        assertEquals(0, slice.readerIndex());
        assertEquals(7, slice.writerIndex());
        assertEquals(7, slice.capacity());
        assertEquals(7, slice.maxCapacity());

        slice.setIndex(1, 6);
        assertEquals(1, buf.readerIndex());
        assertEquals(7, buf.writerIndex());
    }

    @Test
    public void testSliceOfSlice() throws Exception {
        ByteBuf buf = Unpooled.buffer(8);
        ByteBuf slice = buf.slice(1, 7);
        ByteBuf slice2 = slice.slice(0, 6);

        assertNotSame(slice, slice2);
        assertInstanceOf(AbstractUnpooledSlicedByteBuf.class, slice2);
        assertSame(buf, slice2.unwrap());
        assertEquals(6, slice2.writerIndex());
        assertEquals(6, slice2.capacity());
    }

    @Test
    public void testDuplicate() throws Exception {
        ByteBuf buf = Unpooled.buffer(8).setIndex(1, 7);
        ByteBuf dup = buf.duplicate();

        assertInstanceOf(DuplicatedByteBuf.class, dup);
        assertSame(buf, dup.unwrap());
        assertEquals(buf.readerIndex(), dup.readerIndex());
        assertEquals(buf.writerIndex(), dup.writerIndex());
        assertEquals(buf.capacity(), dup.capacity());
        assertEquals(buf.maxCapacity(), dup.maxCapacity());

        dup.setIndex(2, 6);
        assertEquals(1, buf.readerIndex());
        assertEquals(7, buf.writerIndex());
    }

    @Test
    public void testDuplicateOfDuplicate() throws Exception {
        ByteBuf buf = Unpooled.buffer(8).setIndex(1, 7);
        ByteBuf dup = buf.duplicate().setIndex(2, 6);
        ByteBuf dup2 = dup.duplicate();

        assertNotSame(dup, dup2);
        assertInstanceOf(DuplicatedByteBuf.class, dup2);
        assertSame(buf, dup2.unwrap());
        assertEquals(dup.readerIndex(), dup2.readerIndex());
        assertEquals(dup.writerIndex(), dup2.writerIndex());
        assertEquals(dup.capacity(), dup2.capacity());
        assertEquals(dup.maxCapacity(), dup2.maxCapacity());
    }

    @Test
    public void testReadOnly() throws Exception {
        ByteBuf buf = Unpooled.buffer(8).setIndex(1, 7);
        ByteBuf ro = Unpooled.unmodifiableBuffer(buf);

        assertInstanceOf(ReadOnlyByteBuf.class, ro);
        assertSame(buf, ro.unwrap());
        assertEquals(buf.readerIndex(), ro.readerIndex());
        assertEquals(buf.writerIndex(), ro.writerIndex());
        assertEquals(buf.capacity(), ro.capacity());
        assertEquals(buf.maxCapacity(), ro.maxCapacity());

        ro.setIndex(2, 6);
        assertEquals(1, buf.readerIndex());
    }

    @Test
    public void testReadOnlyOfReadOnly() throws Exception {
        ByteBuf buf = Unpooled.buffer(8).setIndex(1, 7);
        ByteBuf ro = Unpooled.unmodifiableBuffer(buf).setIndex(2, 6);
        ByteBuf ro2 = Unpooled.unmodifiableBuffer(ro);

        assertNotSame(ro, ro2);
        assertInstanceOf(ReadOnlyByteBuf.class, ro2);
        assertSame(buf, ro2.unwrap());
        assertEquals(ro.readerIndex(), ro2.readerIndex());
        assertEquals(ro.writerIndex(), ro2.writerIndex());
        assertEquals(ro.capacity(), ro2.capacity());
        assertEquals(ro.maxCapacity(), ro2.maxCapacity());
    }

    @Test
    public void testReadOnlyOfDuplicate() throws Exception {
        ByteBuf buf = Unpooled.buffer(8).setIndex(1, 7);
        ByteBuf dup = buf.duplicate().setIndex(2, 6);
        ByteBuf ro = Unpooled.unmodifiableBuffer(dup);

        assertInstanceOf(ReadOnlyByteBuf.class, ro);
        assertSame(buf, ro.unwrap());
        assertEquals(dup.readerIndex(), ro.readerIndex());
        assertEquals(dup.writerIndex(), ro.writerIndex());
        assertEquals(dup.capacity(), ro.capacity());
        assertEquals(dup.maxCapacity(), ro.maxCapacity());
    }

    @Test
    public void testDuplicateOfReadOnly() throws Exception {
        ByteBuf buf = Unpooled.buffer(8).setIndex(1, 7);
        ByteBuf ro = Unpooled.unmodifiableBuffer(buf).setIndex(2, 6);
        ByteBuf dup = ro.duplicate();

        assertInstanceOf(ReadOnlyByteBuf.class, ro);
        assertSame(buf, ro.unwrap());
        assertEquals(dup.readerIndex(), ro.readerIndex());
        assertEquals(dup.writerIndex(), ro.writerIndex());
        assertEquals(dup.capacity(), ro.capacity());
        assertEquals(dup.maxCapacity(), ro.maxCapacity());
    }

    @Test
    public void testSwap() throws Exception {
        ByteBuf buf = Unpooled.buffer(8).setIndex(1, 7);
        ByteBuf swapped = buf.order(ByteOrder.LITTLE_ENDIAN);

        assertInstanceOf(SwappedByteBuf.class, swapped);
        assertSame(buf, swapped.unwrap());
        assertSame(swapped, swapped.order(ByteOrder.LITTLE_ENDIAN));
        assertSame(buf, swapped.order(ByteOrder.BIG_ENDIAN));

        buf.setIndex(2, 6);
        assertEquals(2, swapped.readerIndex());
        assertEquals(6, swapped.writerIndex());
    }

    @Test
    public void testMixture() throws Exception {
        ByteBuf buf = Unpooled.buffer(10000);
        ByteBuf derived = buf;
        Random rnd = new Random();
        for (int i = 0; i < buf.capacity(); i ++) {
            ByteBuf newDerived;
            switch (rnd.nextInt(4)) {
            case 0:
                newDerived = derived.slice(1, derived.capacity() - 1);
                break;
            case 1:
                newDerived = derived.duplicate();
                break;
            case 2:
                newDerived = derived.order(
                        derived.order() == ByteOrder.BIG_ENDIAN ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
                break;
            case 3:
                newDerived = Unpooled.unmodifiableBuffer(derived);
                break;
            default:
                throw new Error();
            }

            assertThat(nestLevel(newDerived)).isLessThanOrEqualTo(3);
            assertThat(nestLevel(newDerived.order(ByteOrder.BIG_ENDIAN))).isLessThanOrEqualTo(2);

            derived = newDerived;
        }
    }

    private static int nestLevel(ByteBuf buf) {
        int depth = 0;
        for (ByteBuf b = buf.order(ByteOrder.BIG_ENDIAN);;) {
            if (b.unwrap() == null && !(b instanceof SwappedByteBuf)) {
                break;
            }
            depth ++;
            if (b instanceof SwappedByteBuf) {
                b = b.order(ByteOrder.BIG_ENDIAN);
            } else {
                b = b.unwrap();
            }
        }
        return depth;
    }
}
