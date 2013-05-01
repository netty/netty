/*
 * Copyright 2013 The Netty Project
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

import java.nio.ByteOrder;
import java.util.Random;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

/**
 * Tests wrapping a wrapped buffer does not go way too deep chaining.
 */
public class ByteBufDerivationTest {

    @Test
    public void testSlice() throws Exception {
        ByteBuf buf = Unpooled.buffer(8).setIndex(1, 7);
        ByteBuf slice = buf.slice(1, 7);

        assertThat(slice, instanceOf(SlicedByteBuf.class));
        assertThat(slice.unwrap(), sameInstance(buf));
        assertThat(slice.readerIndex(), is(0));
        assertThat(slice.writerIndex(), is(7));
        assertThat(slice.capacity(), is(7));
        assertThat(slice.maxCapacity(), is(7));

        slice.setIndex(1, 6);
        assertThat(buf.readerIndex(), is(1));
        assertThat(buf.writerIndex(), is(7));
    }

    @Test
    public void testSliceOfSlice() throws Exception {
        ByteBuf buf = Unpooled.buffer(8);
        ByteBuf slice = buf.slice(1, 7);
        ByteBuf slice2 = slice.slice(0, 6);

        assertThat(slice2, not(sameInstance(slice)));
        assertThat(slice2, instanceOf(SlicedByteBuf.class));
        assertThat(slice2.unwrap(), sameInstance(buf));
        assertThat(slice2.writerIndex(), is(6));
        assertThat(slice2.capacity(), is(6));
    }

    @Test
    public void testDuplicate() throws Exception {
        ByteBuf buf = Unpooled.buffer(8).setIndex(1, 7);
        ByteBuf dup = buf.duplicate();

        assertThat(dup, instanceOf(DuplicatedByteBuf.class));
        assertThat(dup.unwrap(), sameInstance(buf));
        assertThat(dup.readerIndex(), is(buf.readerIndex()));
        assertThat(dup.writerIndex(), is(buf.writerIndex()));
        assertThat(dup.capacity(), is(buf.capacity()));
        assertThat(dup.maxCapacity(), is(buf.maxCapacity()));

        dup.setIndex(2, 6);
        assertThat(buf.readerIndex(), is(1));
        assertThat(buf.writerIndex(), is(7));
    }

    @Test
    public void testDuplicateOfDuplicate() throws Exception {
        ByteBuf buf = Unpooled.buffer(8).setIndex(1, 7);
        ByteBuf dup = buf.duplicate().setIndex(2, 6);
        ByteBuf dup2 = dup.duplicate();

        assertThat(dup2, not(sameInstance(dup)));
        assertThat(dup2, instanceOf(DuplicatedByteBuf.class));
        assertThat(dup2.unwrap(), sameInstance(buf));
        assertThat(dup2.readerIndex(), is(dup.readerIndex()));
        assertThat(dup2.writerIndex(), is(dup.writerIndex()));
        assertThat(dup2.capacity(), is(dup.capacity()));
        assertThat(dup2.maxCapacity(), is(dup.maxCapacity()));
    }

    @Test
    public void testReadOnly() throws Exception {
        ByteBuf buf = Unpooled.buffer(8).setIndex(1, 7);
        ByteBuf ro = Unpooled.unmodifiableBuffer(buf);

        assertThat(ro, instanceOf(ReadOnlyByteBuf.class));
        assertThat(ro.unwrap(), sameInstance(buf));
        assertThat(ro.readerIndex(), is(buf.readerIndex()));
        assertThat(ro.writerIndex(), is(buf.writerIndex()));
        assertThat(ro.capacity(), is(buf.capacity()));
        assertThat(ro.maxCapacity(), is(buf.maxCapacity()));

        ro.setIndex(2, 6);
        assertThat(buf.readerIndex(), is(1));
    }

    @Test
    public void testReadOnlyOfReadOnly() throws Exception {
        ByteBuf buf = Unpooled.buffer(8).setIndex(1, 7);
        ByteBuf ro = Unpooled.unmodifiableBuffer(buf).setIndex(2, 6);
        ByteBuf ro2 = Unpooled.unmodifiableBuffer(ro);

        assertThat(ro2, not(sameInstance(ro)));
        assertThat(ro2, instanceOf(ReadOnlyByteBuf.class));
        assertThat(ro2.unwrap(), sameInstance(buf));
        assertThat(ro2.readerIndex(), is(ro.readerIndex()));
        assertThat(ro2.writerIndex(), is(ro.writerIndex()));
        assertThat(ro2.capacity(), is(ro.capacity()));
        assertThat(ro2.maxCapacity(), is(ro.maxCapacity()));
    }

    @Test
    public void testReadOnlyOfDuplicate() throws Exception {
        ByteBuf buf = Unpooled.buffer(8).setIndex(1, 7);
        ByteBuf dup = buf.duplicate().setIndex(2, 6);
        ByteBuf ro = Unpooled.unmodifiableBuffer(dup);

        assertThat(ro, instanceOf(ReadOnlyByteBuf.class));
        assertThat(ro.unwrap(), sameInstance(buf));
        assertThat(ro.readerIndex(), is(dup.readerIndex()));
        assertThat(ro.writerIndex(), is(dup.writerIndex()));
        assertThat(ro.capacity(), is(dup.capacity()));
        assertThat(ro.maxCapacity(), is(dup.maxCapacity()));
    }

    @Test
    public void testDuplicateOfReadOnly() throws Exception {
        ByteBuf buf = Unpooled.buffer(8).setIndex(1, 7);
        ByteBuf ro = Unpooled.unmodifiableBuffer(buf).setIndex(2, 6);
        ByteBuf dup = ro.duplicate();

        assertThat(dup, instanceOf(ReadOnlyByteBuf.class));
        assertThat(dup.unwrap(), sameInstance(buf));
        assertThat(dup.readerIndex(), is(ro.readerIndex()));
        assertThat(dup.writerIndex(), is(ro.writerIndex()));
        assertThat(dup.capacity(), is(ro.capacity()));
        assertThat(dup.maxCapacity(), is(ro.maxCapacity()));
    }

    @Test
    public void testSwap() throws Exception {
        ByteBuf buf = Unpooled.buffer(8).setIndex(1, 7);
        ByteBuf swapped = buf.order(ByteOrder.LITTLE_ENDIAN);

        assertThat(swapped, instanceOf(SwappedByteBuf.class));
        assertThat(swapped.unwrap(), is((ByteBuf) null));
        assertThat(swapped.order(ByteOrder.LITTLE_ENDIAN), sameInstance(swapped));
        assertThat(swapped.order(ByteOrder.BIG_ENDIAN), sameInstance(buf));

        buf.setIndex(2, 6);
        assertThat(swapped.readerIndex(), is(2));
        assertThat(swapped.writerIndex(), is(6));
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

            assertThat("nest level of " + newDerived, nestLevel(newDerived), is(lessThanOrEqualTo(3)));
            assertThat(
                    "nest level of " + newDerived.order(ByteOrder.BIG_ENDIAN),
                    nestLevel(newDerived.order(ByteOrder.BIG_ENDIAN)), is(lessThanOrEqualTo(2)));

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
