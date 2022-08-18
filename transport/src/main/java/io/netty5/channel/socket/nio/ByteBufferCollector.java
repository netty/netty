/*
 * Copyright 2022 The Netty Project
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
package io.netty5.channel.socket.nio;

import io.netty5.buffer.api.Buffer;
import io.netty5.util.concurrent.FastThreadLocal;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.function.Predicate;

final class ByteBufferCollector implements Predicate<Object> {

    private static final FastThreadLocal<BufferCache> NIO_BUFFERS = new FastThreadLocal<>() {
        @Override
        protected BufferCache initialValue() {
            BufferCache cache = new BufferCache();
            cache.buffers = new ByteBuffer[1024];
            return cache;
        }
    };

    private int maxCount;
    private long maxBytes;
    private BufferCache cache;

    /**
     * Returns the number of {@link ByteBuffer} that can be written out of the {@link ByteBuffer} array that was
     * obtained via {@link #test(Object)}. This method <strong>MUST</strong> be
     * called after {@link #test(Object)} was called.
     */
    int nioBufferCount() {
        return cache.bufferCount;
    }

    /**
     * Returns the number of bytes that can be written out of the {@link ByteBuffer} array that was
     * obtained via {@link #test(Object)}. This method <strong>MUST</strong> be called
     * after {@link #test(Object)} was called.
     */
    long nioBufferSize() {
        return cache.totalSize;
    }

    ByteBuffer[] nioBuffers() {
        return cache.buffers;
    }

    /**
     * Thread-local cache of {@link ByteBuffer} array, and processing meta-data.
     */
    private static final class BufferCache {
        ByteBuffer[] buffers;
        long totalSize;
        int bufferCount;
    }

    // Clear all ByteBuffer from the array so these can be GC'ed.
    // See https://github.com/netty/netty/issues/3837
    void reset() {
        if (cache == null) {
            cache = NIO_BUFFERS.get();
        }
        int count = cache.bufferCount;
        if (count > 0) {
            Arrays.fill(cache.buffers, 0, count, null);
        }
        cache.bufferCount = 0;
        cache.totalSize = 0;
        cache = null;
    }

    /**
     * Returns an array of direct NIO buffers if the currently pending messages are made of {@link Buffer} only.
     * {@link #nioBufferCount()} and {@link #nioBufferSize()} will return the number of NIO buffers in the returned
     * array and the total number of readable bytes of the NIO buffers respectively.
     *
     * @param maxCount The maximum amount of buffers that will be added to the return value.
     * @param maxBytes A hint toward the maximum number of bytes to include as part of the return value. Note that
     *                 this value maybe exceeded because we make a best effort to include at least 1
     *                 {@link ByteBuffer} in the return value to ensure write progress is made.
     */
    void prepare(int maxCount, long maxBytes) {
        assert maxCount > 0;
        assert maxBytes > 0;
        this.maxCount = maxCount;
        this.maxBytes = maxBytes;
        this.cache = NIO_BUFFERS.get();
        this.cache.bufferCount = 0;
        this.cache.totalSize = 0;
    }

    @Override
    public boolean test(Object msg) throws RuntimeException {
        if (!(msg instanceof Buffer)) {
            return false;
        }
        Buffer buf = (Buffer) msg;
        if (buf.readableBytes() == 0) {
            return true;
        }
        try (var iterator = buf.forEachComponent()) {
            for (var c = iterator.firstReadable(); c != null; c = c.nextReadable()) {
                ByteBuffer byteBuffer = c.readableBuffer();
                if (cache.bufferCount > 0 && cache.totalSize + byteBuffer.remaining() > maxBytes) {
                    // If the nioBufferSize + readableBytes will overflow maxBytes, and there is at least
                    // one entry we stop populate the ByteBuffer array. This is done for 2 reasons:
                    // 1. bsd/osx don't allow to write more bytes then Integer.MAX_VALUE with one
                    // writev(...) call and so will return 'EINVAL', which will raise an IOException.
                    // On Linux it may work depending on the architecture and kernel but to be safe we also
                    // enforce the limit here.
                    // 2. There is no sense in putting more data in the array than is likely to be accepted
                    // by the OS.
                    //
                    // See also:
                    // - https://www.freebsd.org/cgi/man.cgi?query=write&sektion=2
                    // - https://linux.die.net//man/2/writev
                    return false;
                }
                cache.totalSize += byteBuffer.remaining();
                ByteBuffer[] buffers = cache.buffers;
                int bufferCount = cache.bufferCount;
                if (buffers.length == bufferCount && bufferCount < maxCount) {
                    buffers = cache.buffers = expandNioBufferArray(buffers, bufferCount + 1, bufferCount);
                }
                buffers[cache.bufferCount] = byteBuffer;
                bufferCount++;
                cache.bufferCount = bufferCount;
                if (maxCount <= bufferCount) {
                    return false;
                }
            }
        }

        return true;
    }

    private static ByteBuffer[] expandNioBufferArray(ByteBuffer[] array, int neededSpace, int size) {
        int newCapacity = array.length;
        do {
            // double capacity until it is big enough
            // See https://github.com/netty/netty/issues/1890
            newCapacity <<= 1;

            if (newCapacity < 0) {
                throw new IllegalStateException();
            }

        } while (neededSpace > newCapacity);

        ByteBuffer[] newArray = new ByteBuffer[newCapacity];
        System.arraycopy(array, 0, newArray, 0, size);

        return newArray;
    }
}
