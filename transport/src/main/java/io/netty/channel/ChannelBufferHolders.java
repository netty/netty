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
package io.netty.channel;

import io.netty.buffer.AbstractChannelBuffer;
import io.netty.buffer.ChannelBuffer;
import io.netty.buffer.ChannelBufferFactory;
import io.netty.buffer.ChannelBuffers;
import io.netty.buffer.HeapChannelBufferFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.util.AbstractQueue;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Iterator;
import java.util.Queue;

public final class ChannelBufferHolders {

    private static final ChannelBufferHolder<Object> DISCARD_MESSAGE_BUFFER =
            new ChannelBufferHolder<Object>(new NoopQueue<Object>());
    private static final ChannelBufferHolder<Byte> DISCARD_BYTE_BUFFER =
            new ChannelBufferHolder<Byte>(new NoopByteBuf());

    public static <E> ChannelBufferHolder<E> messageBuffer() {
        return messageBuffer(new ArrayDeque<E>());
    }

    public static <E> ChannelBufferHolder<E> messageBuffer(Queue<E> buffer) {
        return new ChannelBufferHolder<E>(buffer);
    }

    public static ChannelBufferHolder<Byte> byteBuffer() {
        // TODO: Use more efficient implementation.
        return byteBuffer(ChannelBuffers.dynamicBuffer());
    }

    public static ChannelBufferHolder<Byte> byteBuffer(ChannelBuffer buffer) {
        return new ChannelBufferHolder<Byte>(buffer);
    }

    public static <E> ChannelBufferHolder<E> inboundBypassBuffer(ChannelHandlerContext ctx) {
        return new ChannelBufferHolder<E>(ctx, true);
    }

    public static <E> ChannelBufferHolder<E> outboundBypassBuffer(ChannelHandlerContext ctx) {
        return new ChannelBufferHolder<E>(ctx, false);
    }

    @SuppressWarnings("unchecked")
    public static <E> ChannelBufferHolder<E> discardMessageBuffer() {
        return (ChannelBufferHolder<E>) DISCARD_MESSAGE_BUFFER;
    }

    @SuppressWarnings("unchecked")
    public static <E> ChannelBufferHolder<E> discardByteBuffer() {
        return (ChannelBufferHolder<E>) DISCARD_BYTE_BUFFER;
    }

    private ChannelBufferHolders() {
        // Utility class
    }

    private static class NoopQueue<E> extends AbstractQueue<E> {
        @Override
        public boolean offer(Object e) {
            return false;
        }

        @Override
        public E poll() {
            return null;
        }

        @Override
        public E peek() {
            return null;
        }

        @Override
        public Iterator<E> iterator() {
            return (Iterator<E>) Collections.emptyList().iterator();
        }

        @Override
        public int size() {
            return 0;
        }
    }

    private static class NoopByteBuf extends AbstractChannelBuffer {

        @Override
        public ChannelBufferFactory factory() {
            return HeapChannelBufferFactory.getInstance();
        }

        @Override
        public int capacity() {
            return 0;
        }

        @Override
        public ByteOrder order() {
            return ByteOrder.BIG_ENDIAN;
        }

        @Override
        public boolean isDirect() {
            return false;
        }

        @Override
        public byte getByte(int index) {
            return 0;
        }

        @Override
        public short getShort(int index) {
            return 0;
        }

        @Override
        public int getUnsignedMedium(int index) {
            return 0;
        }

        @Override
        public int getInt(int index) {
            return 0;
        }

        @Override
        public long getLong(int index) {
            return 0;
        }

        @Override
        public void getBytes(int index, ChannelBuffer dst, int dstIndex, int length) {
            // NOOP
        }

        @Override
        public void getBytes(int index, byte[] dst, int dstIndex, int length) {
            // NOOP
        }

        @Override
        public void getBytes(int index, ByteBuffer dst) {
            // NOOP
        }

        @Override
        public void getBytes(int index, OutputStream out, int length)
                throws IOException {
            // NOOP
        }

        @Override
        public int getBytes(int index, GatheringByteChannel out, int length)
                throws IOException {
            return 0;
        }

        @Override
        public void setByte(int index, int value) {
            // NOOP
        }

        @Override
        public void setShort(int index, int value) {
            // NOOP
        }

        @Override
        public void setMedium(int index, int value) {
            // NOOP
        }

        @Override
        public void setInt(int index, int value) {
            // NOOP
        }

        @Override
        public void setLong(int index, long value) {
            // NOOP
        }

        @Override
        public void setBytes(int index, ChannelBuffer src, int srcIndex,
                int length) {
            // NOOP
        }

        @Override
        public void setBytes(int index, byte[] src, int srcIndex, int length) {
            // NOOP
        }

        @Override
        public void setBytes(int index, ByteBuffer src) {
            // NOOP
        }

        @Override
        public int setBytes(int index, InputStream in, int length)
                throws IOException {
            return 0;
        }

        @Override
        public int setBytes(int index, ScatteringByteChannel in, int length)
                throws IOException {
            return 0;
        }

        @Override
        public ChannelBuffer copy(int index, int length) {
            return this;
        }

        @Override
        public ChannelBuffer slice(int index, int length) {
            return this;
        }

        @Override
        public ChannelBuffer duplicate() {
            return this;
        }

        @Override
        public boolean hasNioBuffer() {
            return true;
        }

        @Override
        public ByteBuffer nioBuffer(int index, int length) {
            return ByteBuffer.allocate(0);
        }

        @Override
        public boolean hasArray() {
            return true;
        }

        @Override
        public byte[] array() {
            return new byte[0];
        }

        @Override
        public int arrayOffset() {
            return 0;
        }
    }
}
