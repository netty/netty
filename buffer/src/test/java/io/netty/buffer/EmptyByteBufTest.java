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
 */package io.netty.buffer;

import io.netty.util.CharsetUtil;
import org.junit.Test;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class EmptyByteBufTest {

    @Test
    public void testIsContiguous() {
        EmptyByteBuf empty = new EmptyByteBuf(UnpooledByteBufAllocator.DEFAULT);
        assertTrue(empty.isContiguous());
    }

    @Test
    public void testIsWritable() {
        EmptyByteBuf empty = new EmptyByteBuf(UnpooledByteBufAllocator.DEFAULT);
        assertFalse(empty.isWritable());
        assertFalse(empty.isWritable(1));
    }

    @Test
    public void testWriteEmptyByteBuf() {
        EmptyByteBuf empty = new EmptyByteBuf(UnpooledByteBufAllocator.DEFAULT);
        empty.writeBytes(Unpooled.EMPTY_BUFFER); // Ok
        ByteBuf nonEmpty = UnpooledByteBufAllocator.DEFAULT.buffer().writeBoolean(false);
        try {
            empty.writeBytes(nonEmpty);
            fail();
        } catch (IndexOutOfBoundsException ignored) {
            // Ignore.
        } finally {
            nonEmpty.release();
        }
    }

    @Test
    public void testIsReadable() {
        EmptyByteBuf empty = new EmptyByteBuf(UnpooledByteBufAllocator.DEFAULT);
        assertFalse(empty.isReadable());
        assertFalse(empty.isReadable(1));
    }

    @Test
    public void testArray() {
        EmptyByteBuf empty = new EmptyByteBuf(UnpooledByteBufAllocator.DEFAULT);
        assertThat(empty.hasArray(), is(true));
        assertThat(empty.array().length, is(0));
        assertThat(empty.arrayOffset(), is(0));
    }

    @Test
    public void testNioBuffer() {
        EmptyByteBuf empty = new EmptyByteBuf(UnpooledByteBufAllocator.DEFAULT);
        assertThat(empty.nioBufferCount(), is(1));
        assertThat(empty.nioBuffer().position(), is(0));
        assertThat(empty.nioBuffer().limit(), is(0));
        assertThat(empty.nioBuffer(), is(sameInstance(empty.nioBuffer())));
        assertThat(empty.nioBuffer(), is(sameInstance(empty.internalNioBuffer(empty.readerIndex(), 0))));
    }

    @Test
    public void testMemoryAddress() {
        EmptyByteBuf empty = new EmptyByteBuf(UnpooledByteBufAllocator.DEFAULT);
        if (empty.hasMemoryAddress()) {
            assertThat(empty.memoryAddress(), is(not(0L)));
        } else {
            try {
                empty.memoryAddress();
                fail();
            } catch (UnsupportedOperationException ignored) {
                // Ignore.
            }
        }
    }

    @Test
    public void consistentEqualsAndHashCodeWithAbstractBytebuf() {
        ByteBuf empty = new EmptyByteBuf(UnpooledByteBufAllocator.DEFAULT);
        ByteBuf emptyAbstract = new UnpooledHeapByteBuf(UnpooledByteBufAllocator.DEFAULT, 0, 0);
        assertEquals(emptyAbstract, empty);
        assertEquals(emptyAbstract.hashCode(), empty.hashCode());
        assertEquals(EmptyByteBuf.EMPTY_BYTE_BUF_HASH_CODE, empty.hashCode());
        assertTrue(emptyAbstract.release());
        assertFalse(empty.release());
    }

    @Test
    public void testGetCharSequence() {
        EmptyByteBuf empty = new EmptyByteBuf(UnpooledByteBufAllocator.DEFAULT);
        assertEquals("", empty.readCharSequence(0, CharsetUtil.US_ASCII));
    }

}
