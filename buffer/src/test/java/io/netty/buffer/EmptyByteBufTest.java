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

import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

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
        assertTrue(empty.hasArray());
        assertEquals(0, empty.array().length);
        assertEquals(0, empty.arrayOffset());
    }

    @Test
    public void testNioBuffer() {
        EmptyByteBuf empty = new EmptyByteBuf(UnpooledByteBufAllocator.DEFAULT);
        assertEquals(1, empty.nioBufferCount());
        assertEquals(0, empty.nioBuffer().position());
        assertEquals(0, empty.nioBuffer().limit());
        assertSame(empty.nioBuffer(), empty.internalNioBuffer(empty.readerIndex(), 0));
    }

    @Test
    public void testMemoryAddress() {
        EmptyByteBuf empty = new EmptyByteBuf(UnpooledByteBufAllocator.DEFAULT);
        if (empty.hasMemoryAddress()) {
            assertNotEquals(0L, empty.memoryAddress());
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

    @Test
    public void testReadString() {
        EmptyByteBuf empty = new EmptyByteBuf(UnpooledByteBufAllocator.DEFAULT);
        assertEquals("", empty.readString(0, CharsetUtil.US_ASCII));
    }

    @Test
    public void testReadStringThrowsIndexOutOfBoundsException() {
        EmptyByteBuf empty = new EmptyByteBuf(UnpooledByteBufAllocator.DEFAULT);
        assertThrows(IndexOutOfBoundsException.class, () -> empty.readString(1, CharsetUtil.US_ASCII));
    }

}
