/*
 * Copyright 2016 The Netty Project
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

import io.netty.util.IllegalReferenceCountException;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AbstractReferenceCountedByteBufTest {

    @Test(expected = IllegalReferenceCountException.class)
    public void testRetainOverflow() {
        AbstractReferenceCountedByteBuf referenceCounted = newReferenceCounted();
        referenceCounted.setRefCnt(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, referenceCounted.refCnt());
        referenceCounted.retain();
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testRetainOverflow2() {
        AbstractReferenceCountedByteBuf referenceCounted = newReferenceCounted();
        assertEquals(1, referenceCounted.refCnt());
        referenceCounted.retain(Integer.MAX_VALUE);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testReleaseOverflow() {
        AbstractReferenceCountedByteBuf referenceCounted = newReferenceCounted();
        referenceCounted.setRefCnt(0);
        assertEquals(0, referenceCounted.refCnt());
        referenceCounted.release(Integer.MAX_VALUE);
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testRetainResurrect() {
        AbstractReferenceCountedByteBuf referenceCounted = newReferenceCounted();
        assertTrue(referenceCounted.release());
        assertEquals(0, referenceCounted.refCnt());
        referenceCounted.retain();
    }

    @Test(expected = IllegalReferenceCountException.class)
    public void testRetainResurrect2() {
        AbstractReferenceCountedByteBuf referenceCounted = newReferenceCounted();
        assertTrue(referenceCounted.release());
        assertEquals(0, referenceCounted.refCnt());
        referenceCounted.retain(2);
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
            protected int _getUnsignedMedium(int index) {
                throw new UnsupportedOperationException();
            }

            @Override
            protected int _getInt(int index) {
                throw new UnsupportedOperationException();
            }

            @Override
            protected long _getLong(int index) {
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
            protected void _setMedium(int index, int value) {
                throw new UnsupportedOperationException();
            }

            @Override
            protected void _setInt(int index, int value) {
                throw new UnsupportedOperationException();
            }

            @Override
            protected void _setLong(int index, long value) {
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
        };
    }
}
