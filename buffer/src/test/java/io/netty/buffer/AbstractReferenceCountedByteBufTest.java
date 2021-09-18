/*
 * Copyright 2016 The Netty Project
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

import io.netty.util.IllegalReferenceCountException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class AbstractReferenceCountedByteBufTest {

    @Test
    public void testRetainOverflow() {
        final AbstractReferenceCountedByteBuf referenceCounted = newReferenceCounted();
        referenceCounted.setRefCnt(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, referenceCounted.refCnt());
        assertThrows(IllegalReferenceCountException.class, new Executable() {
            @Override
            public void execute()  {
                referenceCounted.retain();
            }
        });
    }

    @Test
    public void testRetainOverflow2() {
        final AbstractReferenceCountedByteBuf referenceCounted = newReferenceCounted();
        assertEquals(1, referenceCounted.refCnt());
        assertThrows(IllegalReferenceCountException.class, new Executable() {
            @Override
            public void execute() {
                referenceCounted.retain(Integer.MAX_VALUE);
            }
        });
    }

    @Test
    public void testReleaseOverflow() {
        final AbstractReferenceCountedByteBuf referenceCounted = newReferenceCounted();
        referenceCounted.setRefCnt(0);
        assertEquals(0, referenceCounted.refCnt());
        assertThrows(IllegalReferenceCountException.class, new Executable() {
            @Override
            public void execute() {
                referenceCounted.release(Integer.MAX_VALUE);
            }
        });
    }

    @Test
    public void testReleaseErrorMessage() {
        AbstractReferenceCountedByteBuf referenceCounted = newReferenceCounted();
        assertTrue(referenceCounted.release());
        try {
            referenceCounted.release(1);
            fail("IllegalReferenceCountException didn't occur");
        } catch (IllegalReferenceCountException e) {
            assertEquals("refCnt: 0, decrement: 1", e.getMessage());
        }
    }

    @Test
    public void testRetainResurrect() {
        final AbstractReferenceCountedByteBuf referenceCounted = newReferenceCounted();
        assertTrue(referenceCounted.release());
        assertEquals(0, referenceCounted.refCnt());
        assertThrows(IllegalReferenceCountException.class, new Executable() {
            @Override
            public void execute() {
                referenceCounted.retain();
            }
        });
    }

    @Test
    public void testRetainResurrect2() {
        final AbstractReferenceCountedByteBuf referenceCounted = newReferenceCounted();
        assertTrue(referenceCounted.release());
        assertEquals(0, referenceCounted.refCnt());
        assertThrows(IllegalReferenceCountException.class, new Executable() {
            @Override
            public void execute() {
                referenceCounted.retain(2);
            }
        });
    }

    private static AbstractReferenceCountedByteBuf newReferenceCounted() {
        return new AbstractReferenceCountedByteBuf(Integer.MAX_VALUE) {

            @Override
            protected byte _getByte(int index) {
                throw new UnsupportedOperationException();
            }

            @Override
            protected short _getShort(int index) {
                throw new UnsupportedOperationException();
            }

            @Override
            protected short _getShortLE(int index) {
                throw new UnsupportedOperationException();
            }

            @Override
            protected int _getUnsignedMedium(int index) {
                throw new UnsupportedOperationException();
            }

            @Override
            protected int _getUnsignedMediumLE(int index) {
                throw new UnsupportedOperationException();
            }

            @Override
            protected int _getInt(int index) {
                throw new UnsupportedOperationException();
            }

            @Override
            protected int _getIntLE(int index) {
                throw new UnsupportedOperationException();
            }

            @Override
            protected long _getLong(int index) {
                throw new UnsupportedOperationException();
            }

            @Override
            protected long _getLongLE(int index) {
                throw new UnsupportedOperationException();
            }

            @Override
            protected void _setByte(int index, int value) {
                throw new UnsupportedOperationException();
            }

            @Override
            protected void _setShort(int index, int value) {
                throw new UnsupportedOperationException();
            }

            @Override
            protected void _setShortLE(int index, int value) {
                throw new UnsupportedOperationException();
            }

            @Override
            protected void _setMedium(int index, int value) {
                throw new UnsupportedOperationException();
            }

            @Override
            protected void _setMediumLE(int index, int value) {
                throw new UnsupportedOperationException();
            }

            @Override
            protected void _setInt(int index, int value) {
                throw new UnsupportedOperationException();
            }

            @Override
            protected void _setIntLE(int index, int value) {
                throw new UnsupportedOperationException();
            }

            @Override
            protected void _setLong(int index, long value) {
                throw new UnsupportedOperationException();
            }

            @Override
            protected void _setLongLE(int index, long value) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int capacity() {
                throw new UnsupportedOperationException();
            }

            @Override
            public ByteBuf capacity(int newCapacity) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ByteBufAllocator alloc() {
                throw new UnsupportedOperationException();
            }

            @Override
            public ByteOrder order() {
                throw new UnsupportedOperationException();
            }

            @Override
            public ByteBuf unwrap() {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isDirect() {
                throw new UnsupportedOperationException();
            }

            @Override
            public ByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ByteBuf getBytes(int index, byte[] dst, int dstIndex, int length) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ByteBuf getBytes(int index, ByteBuffer dst) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ByteBuf getBytes(int index, OutputStream out, int length) throws IOException {
                throw new UnsupportedOperationException();
            }

            @Override
            public int getBytes(int index, GatheringByteChannel out, int length) throws IOException {
                throw new UnsupportedOperationException();
            }

            @Override
            public int getBytes(int index, FileChannel out, long position, int length) throws IOException {
                throw new UnsupportedOperationException();
            }

            @Override
            public ByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ByteBuf setBytes(int index, byte[] src, int srcIndex, int length) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ByteBuf setBytes(int index, ByteBuffer src) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int setBytes(int index, InputStream in, int length) throws IOException {
                throw new UnsupportedOperationException();
            }

            @Override
            public int setBytes(int index, ScatteringByteChannel in, int length) throws IOException {
                throw new UnsupportedOperationException();
            }

            @Override
            public int setBytes(int index, FileChannel in, long position, int length) throws IOException {
                throw new UnsupportedOperationException();
            }

            @Override
            public ByteBuf copy(int index, int length) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int nioBufferCount() {
                throw new UnsupportedOperationException();
            }

            @Override
            public ByteBuffer nioBuffer(int index, int length) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ByteBuffer internalNioBuffer(int index, int length) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ByteBuffer[] nioBuffers(int index, int length) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean hasArray() {
                throw new UnsupportedOperationException();
            }

            @Override
            public byte[] array() {
                throw new UnsupportedOperationException();
            }

            @Override
            public int arrayOffset() {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean hasMemoryAddress() {
                throw new UnsupportedOperationException();
            }

            @Override
            public long memoryAddress() {
                throw new UnsupportedOperationException();
            }

            @Override
            protected void deallocate() {
                // NOOP
            }

            @Override
            public AbstractReferenceCountedByteBuf touch(Object hint) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
