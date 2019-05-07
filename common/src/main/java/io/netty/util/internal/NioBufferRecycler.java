/*
 * Copyright 2019 The Netty Project
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
package io.netty.util.internal;

import static io.netty.util.internal.ObjectUtil.checkPositiveOrZero;
import static io.netty.util.internal.PlatformDependent0.directBufferAddress;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;

import io.netty.util.concurrent.FastThreadLocal;

public final class NioBufferRecycler {

    private static final int MAX_CACHE_SIZE = 128;

    private static final FastThreadLocal<ArrayDeque<ByteBuffer>> TL_NIO_BUFFER_CACHE;

    static {
        TL_NIO_BUFFER_CACHE = MAX_CACHE_SIZE > 0 && PlatformDependent0.hasUnsafeDirectBufferFields()
                ? new FastThreadLocal<ArrayDeque<ByteBuffer>>() {
            @Override
            protected ArrayDeque<ByteBuffer> initialValue() throws Exception {
                return new ArrayDeque<ByteBuffer>(4);
            }
        } : null;
    }

    private NioBufferRecycler() { }

    public static ByteBuffer duplicate(ByteBuffer buffer) {
        return duplicateDirectBuffer0(buffer, pollFromCacheForDerived(buffer));
    }

    public static ByteBuffer slice(ByteBuffer buffer) {
        ByteBuffer toReuse = pollFromCacheForDerived(buffer);
        return toReuse == null ? buffer.slice()
                : sliceDirectBuffer0(buffer, buffer.position(), buffer.remaining(), toReuse);
    }

    public static ByteBuffer slice(ByteBuffer buffer, int index, int length) {
        if (!PlatformDependent0.isReusableType(buffer)) {
            ByteBuffer duplicate = buffer.duplicate();
            duplicate.limit(index + length).position(index);
            return duplicate.slice();
        }
        if (checkPositiveOrZero(index, "index") + checkPositiveOrZero(length, "length") > buffer.limit()) {
            throw new IndexOutOfBoundsException();
        }
        return sliceDirectBuffer0(buffer, index, length, pollFromCacheForDerived(buffer));
    }

    static ByteBuffer pollFromCacheForNew() {
        return TL_NIO_BUFFER_CACHE != null ? pollFromCache0() : null;
    }

    private static ByteBuffer pollFromCacheForDerived(ByteBuffer source) {
        return TL_NIO_BUFFER_CACHE != null && PlatformDependent0.isReusableType(source) ? pollFromCache0() : null;
    }

    private static ByteBuffer pollFromCache0() {
        ArrayDeque<ByteBuffer> cache = TL_NIO_BUFFER_CACHE.getIfExists();
        return cache != null ? cache.poll() : null;
    }

    public static void recycle(ByteBuffer buffer) {
        recycle(buffer, false);
    }

    public static void recycle(ByteBuffer... buffers) {
        for (ByteBuffer buffer : buffers) {
            recycle(buffer, false);
        }
    }

    // must only be used on derived buffers or after explicit deallocation
    static void recycle(ByteBuffer buffer, boolean afterDeallocate) {
        if (TL_NIO_BUFFER_CACHE != null && (afterDeallocate ? PlatformDependent0.isReusableType(buffer)
                : PlatformDependent0.isDerivedDirectBuffer(buffer))) {
            ArrayDeque<ByteBuffer> cache = TL_NIO_BUFFER_CACHE.get();
            if (cache.size() < MAX_CACHE_SIZE) {
                cache.push(PlatformDependent0.resetDirectBuffer(buffer));
            }
        }
    }

    private static ByteBuffer sliceDirectBuffer0(ByteBuffer parent, int index, int length, ByteBuffer toRecycle) {
        final long newAddress = directBufferAddress(parent) + index;
        return toRecycle == null ? PlatformDependent0.newDirectBufferUnsafe(newAddress, length, parent)
                : PlatformDependent0.initDirectBuffer(toRecycle, newAddress, parent, length, length);
    }

    private static ByteBuffer duplicateDirectBuffer0(ByteBuffer parent, ByteBuffer toRecycle) {
        if (toRecycle == null) {
            return parent.duplicate();
        }
        PlatformDependent0.initDirectBuffer(toRecycle,
                directBufferAddress(parent), parent, parent.capacity(), parent.limit());
        // We don't bother to set the mark like ByteBuffer.duplicate() does
        toRecycle.position(parent.position());
        return toRecycle;
    }
}
