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
package io.netty.buffer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * A {@link ByteBufFactory} which merely allocates a heap buffer with
 * the specified capacity.  {@link HeapByteBufFactory} should perform
 * very well in most situations because it relies on the JVM garbage collector,
 * which is highly optimized for heap allocation.
 */
public class HeapByteBufFactory extends AbstractByteBufFactory {

    private static final HeapByteBufFactory INSTANCE_BE =
        new HeapByteBufFactory(ByteOrder.BIG_ENDIAN);

    private static final HeapByteBufFactory INSTANCE_LE =
        new HeapByteBufFactory(ByteOrder.LITTLE_ENDIAN);

    public static ByteBufFactory getInstance() {
        return INSTANCE_BE;
    }

    public static ByteBufFactory getInstance(ByteOrder endianness) {
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

    /**
     * Creates a new factory whose default {@link ByteOrder} is
     * {@link ByteOrder#BIG_ENDIAN}.
     */
    public HeapByteBufFactory() {
    }

    /**
     * Creates a new factory with the specified default {@link ByteOrder}.
     *
     * @param defaultOrder the default {@link ByteOrder} of this factory
     */
    public HeapByteBufFactory(ByteOrder defaultOrder) {
        super(defaultOrder);
    }

    @Override
    public ByteBuf getBuffer(ByteOrder order, int capacity) {
        return Unpooled.buffer(order, capacity);
    }

    @Override
    public ByteBuf getBuffer(ByteOrder order, byte[] array, int offset, int length) {
        return Unpooled.wrappedBuffer(order, array, offset, length);
    }

    @Override
    public ByteBuf getBuffer(ByteBuffer nioBuffer) {
        if (nioBuffer.hasArray()) {
            return Unpooled.wrappedBuffer(nioBuffer);
        }

        ByteBuf buf = getBuffer(nioBuffer.order(), nioBuffer.remaining());
        int pos = nioBuffer.position();
        buf.writeBytes(nioBuffer);
        nioBuffer.position(pos);
        return buf;
    }
}
