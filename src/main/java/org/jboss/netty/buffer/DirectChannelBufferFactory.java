/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.buffer;

import java.lang.ref.ReferenceQueue;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * A {@link ChannelBufferFactory} which pre-allocates a large chunk of direct
 * buffer and returns its slice on demand.  Direct buffers are reclaimed via
 * {@link ReferenceQueue} in most JDK implementations, and therefore they are
 * deallocated less efficiently than an ordinary heap buffer.  Consequently,
 * a user will get {@link OutOfMemoryError} when one tries to allocate small
 * direct buffers more often than the GC throughput of direct buffers, which
 * is much lower than the GC throughput of heap buffers.  This factory avoids
 * this problem by allocating a large chunk of pre-allocated direct buffer and
 * reducing the number of the garbage collected internal direct buffer objects.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev: 2293 $, $Date: 2010-06-01 17:38:51 +0900 (Tue, 01 Jun 2010) $
 */
public class DirectChannelBufferFactory extends AbstractChannelBufferFactory {

    private static final DirectChannelBufferFactory INSTANCE_BE =
        new DirectChannelBufferFactory(ByteOrder.BIG_ENDIAN);

    private static final DirectChannelBufferFactory INSTANCE_LE =
        new DirectChannelBufferFactory(ByteOrder.LITTLE_ENDIAN);

    public static ChannelBufferFactory getInstance() {
        return INSTANCE_BE;
    }

    public static ChannelBufferFactory getInstance(ByteOrder defaultEndianness) {
        if (defaultEndianness == ByteOrder.BIG_ENDIAN) {
            return INSTANCE_BE;
        } else if (defaultEndianness == ByteOrder.LITTLE_ENDIAN) {
            return INSTANCE_LE;
        } else if (defaultEndianness == null) {
            throw new NullPointerException("defaultEndianness");
        } else {
            throw new IllegalStateException("Should not reach here");
        }
    }

    private final Object bigEndianLock = new Object();
    private final Object littleEndianLock = new Object();
    private final int preallocatedBufferCapacity;
    private ChannelBuffer preallocatedBigEndianBuffer = null;
    private int preallocatedBigEndianBufferPosition;
    private ChannelBuffer preallocatedLittleEndianBuffer = null;
    private int preallocatedLittleEndianBufferPosition;

    /**
     * Creates a new factory whose default {@link ByteOrder} is
     * {@link ByteOrder#BIG_ENDIAN}.
     */
    public DirectChannelBufferFactory() {
        this(ByteOrder.BIG_ENDIAN);
    }

    /**
     * Creates a new factory whose default {@link ByteOrder} is
     * {@link ByteOrder#BIG_ENDIAN}.
     */
    public DirectChannelBufferFactory(int preallocatedBufferCapacity) {
        this(ByteOrder.BIG_ENDIAN, preallocatedBufferCapacity);
    }

    /**
     * Creates a new factory with the specified default {@link ByteOrder}.
     *
     * @param defaultOrder the default {@link ByteOrder} of this factory
     */
    public DirectChannelBufferFactory(ByteOrder defaultOrder) {
        this(defaultOrder, 1048576);
    }

    /**
     * Creates a new factory with the specified default {@link ByteOrder}.
     *
     * @param defaultOrder the default {@link ByteOrder} of this factory
     */
    public DirectChannelBufferFactory(ByteOrder defaultOrder, int preallocatedBufferCapacity) {
        super(defaultOrder);
        if (preallocatedBufferCapacity <= 0) {
            throw new IllegalArgumentException(
                    "preallocatedBufferCapacity must be greater than 0: " + preallocatedBufferCapacity);
        }

        this.preallocatedBufferCapacity = preallocatedBufferCapacity;
    }

    public ChannelBuffer getBuffer(ByteOrder order, int capacity) {
        if (order == null) {
            throw new NullPointerException("order");
        }
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity: " + capacity);
        }
        if (capacity == 0) {
            return ChannelBuffers.EMPTY_BUFFER;
        }
        if (capacity >= preallocatedBufferCapacity) {
            return ChannelBuffers.directBuffer(order, capacity);
        }

        ChannelBuffer slice;
        if (order == ByteOrder.BIG_ENDIAN) {
            slice = allocateBigEndianBuffer(capacity);
        } else {
            slice = allocateLittleEndianBuffer(capacity);
        }
        slice.clear();
        return slice;
    }

    public ChannelBuffer getBuffer(ByteOrder order, byte[] array, int offset, int length) {
        if (array == null) {
            throw new NullPointerException("array");
        }
        if (offset < 0) {
            throw new IndexOutOfBoundsException("offset: " + offset);
        }
        if (length == 0) {
            return ChannelBuffers.EMPTY_BUFFER;
        }
        if (offset + length > array.length) {
            throw new IndexOutOfBoundsException("length: " + length);
        }

        ChannelBuffer buf = getBuffer(order, length);
        buf.writeBytes(array, offset, length);
        return buf;
    }

    public ChannelBuffer getBuffer(ByteBuffer nioBuffer) {
        if (!nioBuffer.isReadOnly() && nioBuffer.isDirect()) {
            return ChannelBuffers.wrappedBuffer(nioBuffer);
        }

        ChannelBuffer buf = getBuffer(nioBuffer.order(), nioBuffer.remaining());
        int pos = nioBuffer.position();
        buf.writeBytes(nioBuffer);
        nioBuffer.position(pos);
        return buf;
    }

    private ChannelBuffer allocateBigEndianBuffer(int capacity) {
        ChannelBuffer slice;
        synchronized (bigEndianLock) {
            if (preallocatedBigEndianBuffer == null) {
                preallocatedBigEndianBuffer = ChannelBuffers.directBuffer(ByteOrder.BIG_ENDIAN, preallocatedBufferCapacity);
                slice = preallocatedBigEndianBuffer.slice(0, capacity);
                preallocatedBigEndianBufferPosition = capacity;
            } else if (preallocatedBigEndianBuffer.capacity() - preallocatedBigEndianBufferPosition >= capacity) {
                slice = preallocatedBigEndianBuffer.slice(preallocatedBigEndianBufferPosition, capacity);
                preallocatedBigEndianBufferPosition += capacity;
            } else {
                preallocatedBigEndianBuffer = ChannelBuffers.directBuffer(ByteOrder.BIG_ENDIAN, preallocatedBufferCapacity);
                slice = preallocatedBigEndianBuffer.slice(0, capacity);
                preallocatedBigEndianBufferPosition = capacity;
            }
        }
        return slice;
    }

    private ChannelBuffer allocateLittleEndianBuffer(int capacity) {
        ChannelBuffer slice;
        synchronized (littleEndianLock) {
            if (preallocatedLittleEndianBuffer == null) {
                preallocatedLittleEndianBuffer = ChannelBuffers.directBuffer(ByteOrder.LITTLE_ENDIAN, preallocatedBufferCapacity);
                slice = preallocatedLittleEndianBuffer.slice(0, capacity);
                preallocatedLittleEndianBufferPosition = capacity;
            } else if (preallocatedLittleEndianBuffer.capacity() - preallocatedLittleEndianBufferPosition >= capacity) {
                slice = preallocatedLittleEndianBuffer.slice(preallocatedLittleEndianBufferPosition, capacity);
                preallocatedLittleEndianBufferPosition += capacity;
            } else {
                preallocatedLittleEndianBuffer = ChannelBuffers.directBuffer(ByteOrder.LITTLE_ENDIAN, preallocatedBufferCapacity);
                slice = preallocatedLittleEndianBuffer.slice(0, capacity);
                preallocatedLittleEndianBufferPosition = capacity;
            }
        }
        return slice;
    }
}
