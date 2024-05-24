/*
 * Copyright 2024 The Netty Project
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
package io.netty.handler.ssl;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.AbstractCoalescingBufferQueue;
import io.netty.channel.Channel;
import io.netty.util.internal.PlatformDependent;

import static io.netty.buffer.ByteBufUtil.ensureWritableSuccess;

/**
 * Each call to SSL_write will introduce about ~100 bytes of overhead. This coalescing queue attempts to increase
 * goodput by aggregating the plaintext in chunks of {@link #wrapDataSize}. If many small chunks are written
 * this can increase goodput, decrease the amount of calls to SSL_write, and decrease overall encryption operations.
 */
abstract class SslHandlerCoalescingBufferQueue extends AbstractCoalescingBufferQueue {

    private final boolean wantsDirectBuffer;

    SslHandlerCoalescingBufferQueue(Channel channel, int initSize, boolean wantsDirectBuffer) {
        super(channel, initSize);
        this.wantsDirectBuffer = wantsDirectBuffer;
    }

    protected abstract int wrapDataSize();

    @Override
    protected ByteBuf compose(ByteBufAllocator alloc, ByteBuf cumulation, ByteBuf next) {
        final int wrapDataSize = wrapDataSize();
        if (cumulation instanceof CompositeByteBuf) {
            CompositeByteBuf composite = (CompositeByteBuf) cumulation;
            int numComponents = composite.numComponents();
            if (numComponents == 0 ||
                    !attemptCopyToCumulation(composite.internalComponent(numComponents - 1), next, wrapDataSize)) {
                composite.addComponent(true, next);
            }
            return composite;
        }
        return attemptCopyToCumulation(cumulation, next, wrapDataSize) ? cumulation :
                copyAndCompose(alloc, cumulation, next);
    }

    @Override
    protected ByteBuf composeFirst(ByteBufAllocator allocator, ByteBuf first) {
        if (first instanceof CompositeByteBuf) {
            CompositeByteBuf composite = (CompositeByteBuf) first;
            if (wantsDirectBuffer) {
                first = allocator.directBuffer(composite.readableBytes());
            } else {
                first = allocator.heapBuffer(composite.readableBytes());
            }
            try {
                first.writeBytes(composite);
            } catch (Throwable cause) {
                first.release();
                PlatformDependent.throwException(cause);
            }
            composite.release();
        }
        return first;
    }

    @Override
    protected ByteBuf removeEmptyValue() {
        return null;
    }

    private static boolean attemptCopyToCumulation(ByteBuf cumulation, ByteBuf next, int wrapDataSize) {
        final int inReadableBytes = next.readableBytes();
        // Nothing to copy so just release the buffer.
        if (inReadableBytes == 0) {
            next.release();
            return true;
        }
        final int cumulationCapacity = cumulation.capacity();
        if (wrapDataSize - cumulation.readableBytes() >= inReadableBytes &&
                // Avoid using the same buffer if next's data would make cumulation exceed the wrapDataSize.
                // Only copy if there is enough space available and the capacity is large enough, and attempt to
                // resize if the capacity is small.
                (cumulation.isWritable(inReadableBytes) && cumulationCapacity >= wrapDataSize ||
                        cumulationCapacity < wrapDataSize &&
                                ensureWritableSuccess(cumulation.ensureWritable(inReadableBytes, false)))) {
            cumulation.writeBytes(next);
            next.release();
            return true;
        }
        return false;
    }
}
