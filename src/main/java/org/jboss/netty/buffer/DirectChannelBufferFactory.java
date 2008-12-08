/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.buffer;

import java.lang.ref.ReferenceQueue;
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
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
public class DirectChannelBufferFactory extends AbstractChannelBufferFactory {

    private static final DirectChannelBufferFactory INSTANCE_BE =
        new DirectChannelBufferFactory(ByteOrder.BIG_ENDIAN);

    private static final DirectChannelBufferFactory INSTANCE_LE =
        new DirectChannelBufferFactory(ByteOrder.LITTLE_ENDIAN);

    public static ChannelBufferFactory getInstance() {
        return INSTANCE_BE;
    }

    public static ChannelBufferFactory getInstance(ByteOrder endianness) {
        if (endianness == ByteOrder.BIG_ENDIAN) {
            return INSTANCE_BE;
        } else if (endianness == ByteOrder.LITTLE_ENDIAN) {
            return INSTANCE_LE;
        } else if (endianness == null) {
            throw new NullPointerException("endianness");
        } else {
            throw new IllegalStateException("Should not reach here");
        }
    }

    private final Object bigEndianLock = new Object();
    private final Object littleEndianLock = new Object();
    private final int preallocatedBufferCapacity = 1048576;
    private ChannelBuffer preallocatedBigEndianBuffer = null;
    private int preallocatedBigEndianBufferPosition;
    private ChannelBuffer preallocatedLittleEndianBuffer = null;
    private int preallocatedLittleEndianBufferPosition;

    /**
     * Creates a new factory whose default {@link ByteOrder} is
     * {@link ByteOrder#BIG_ENDIAN}.
     */
    public DirectChannelBufferFactory() {
        super();
    }

    /**
     * Creates a new factory with the specified default {@link ByteOrder}.
     *
     * @param defaultOrder the default {@link ByteOrder} of this factory
     */
    public DirectChannelBufferFactory(ByteOrder defaultOrder) {
        super(defaultOrder);
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
        return slice;
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

    private synchronized ChannelBuffer allocateLittleEndianBuffer(int capacity) {
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
