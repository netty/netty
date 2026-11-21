/*
 * Copyright 2026 The Netty Project
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

package io.netty.handler.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.netty.handler.codec.ByteToMessageDecoder.Cumulator;
import io.netty.util.internal.ObjectUtil;

/**
 * "Adaptive" cumulator: cumulate {@link ByteBuf}s by dynamically switching
 * between merge and compose strategies.
 */
public final class AdaptiveCumulator implements Cumulator {
    private final int composeMinSize;

    /**
     * @param composeMinSize Determines the minimal size of the buffer that should
     *                       be composed (added as a new component of the
     *                       {@link CompositeByteBuf}). If the total size of the
     *                       last component (tail) and the incoming buffer is
     *                       below this value, the incoming buffer is appended to
     *                       the tail, and the new component is not added.
     */
    public AdaptiveCumulator(int composeMinSize) {
        ObjectUtil.checkPositiveOrZero(composeMinSize, "composeMinSize");
        this.composeMinSize = composeMinSize;
    }

    /**
     * "Adaptive" cumulator: cumulate {@link ByteBuf}s by dynamically switching
     * between merge and compose strategies.
     *
     * <p>
     * This cumulator applies a heuristic to make a decision whether to track a
     * reference to the buffer with bytes received from the network stack in an
     * array ("zero-copy"), or to merge into the last component (the tail) by
     * performing a memory copy.
     *
     * <p>
     * It is necessary as a protection from a potential attack on the
     * {@link io.netty.handler.codec.ByteToMessageDecoder#COMPOSITE_CUMULATOR}.
     * Consider a pathological case when an attacker sends TCP packages containing a
     * single byte of data, and forcing the cumulator to track each one in a
     * separate buffer. The cost is memory overhead for each buffer, and extra
     * compute to read the cumulation.
     *
     * <p>
     * Implemented heuristic establishes a minimal threshold for the total size of
     * the tail and incoming buffer, below which they are merged. The sum of the
     * tail and the incoming buffer is used to avoid a case where attacker
     * alternates the size of data packets to trick the cumulator into always
     * selecting compose strategy.
     *
     * <p>
     * Merging strategy attempts to minimize unnecessary memory writes. When
     * possible, it expands the tail capacity and only copies the incoming buffer
     * into available memory.
     * Otherwise, when both tail and the buffer must be copied, the tail is
     * reallocated (or fully replaced) with a new buffer of exponentially increasing
     * capacity (bounded to {@link #composeMinSize}) to ensure runtime
     * {@code O(n^2)} is amortized to {@code O(n)}.
     */
    @Override
    @SuppressWarnings("ReferenceEquality")
    public ByteBuf cumulate(ByteBufAllocator alloc, ByteBuf cumulation, ByteBuf in) {
        if (cumulation == in) {
            in.release();
            return cumulation;
        }
        if (!cumulation.isReadable()) {
            cumulation.release();
            return in;
        }
        CompositeByteBuf composite = null;
        boolean cumulationTransferred = false;
        try {
            if (isOwnedCompositeBuf(cumulation)) {
                composite = (CompositeByteBuf) cumulation;
                cumulationTransferred = true;
                // Writer index must equal capacity if we are going to "write"
                // new components to the end
                if (composite.writerIndex() != composite.capacity()) {
                    composite.capacity(composite.writerIndex());
                }
            } else {
                composite = alloc.compositeBuffer(Integer.MAX_VALUE);
                composite.addFlattenedComponents(true, cumulation);
                cumulationTransferred = true;
            }
            ByteBuf b = in;
            in = null;
            addInput(alloc, composite, b);

            CompositeByteBuf result = composite;
            composite = null;
            return result;
        } catch (Throwable t) {
            // If an exception was thrown AFTER cumulation was successfully wrapped,
            // calling composite.release() in 'finally' will drop its refCount to 0.
            // We prevent this by calling retain() here on the exception path to keep it alive.
            if (cumulationTransferred && composite != null && composite != cumulation) {
                cumulation.retain();
            }
            throw t;
        } finally {
            if (in != null) {
                // We must release if the ownership was not transferred as otherwise it may
                // produce a leak
                in.release();
            }
            // Also release any new buffer allocated if we're not returning it
            if (composite != null && composite != cumulation) {
                composite.release();
            }
        }
    }

    private static boolean isOwnedCompositeBuf(ByteBuf buf) {
        return buf instanceof CompositeByteBuf && buf.refCnt() == 1;
    }

    private void addInput(ByteBufAllocator alloc, CompositeByteBuf composite, ByteBuf in) {
        if (shouldCompose(composite, in, composeMinSize)) {
            composite.addFlattenedComponents(true, in);
        } else {
            // The total size of the new data and the last component are below the
            // threshold. Merge them.
            mergeWithCompositeTail(alloc, composite, in);
        }
    }

    private static boolean shouldCompose(CompositeByteBuf composite, ByteBuf in, int composeMinSize) {
        int componentCount = composite.numComponents();
        if (componentCount == 0) {
            return true;
        }
        int inputSize = in.readableBytes();
        int tailStart = composite.toByteIndex(componentCount - 1);
        long tailSize = composite.writerIndex() - tailStart;
        return tailSize + inputSize >= composeMinSize;
    }

    /**
     * Append the given {@link ByteBuf} {@code in} to {@link CompositeByteBuf}
     * {@code composite} by expanding or replacing the tail component of the {@link
     * CompositeByteBuf}.
     *
     * <p>
     * The goal is to prevent {@code O(n^2)} runtime in a pathological case, that
     * forces copying the tail component into a new buffer, for each incoming
     * single-byte buffer.
     * We append the new bytes to the tail, when a write (or a fast write) is
     * possible.
     *
     * <p>
     * Otherwise, the tail is replaced with a new buffer, with the capacity
     * increased enough to achieve runtime amortization.
     *
     * <p>
     * We assume that implementations of
     * {@link ByteBufAllocator#calculateNewCapacity(int, int)},
     * are similar to
     * {@link io.netty.buffer.AbstractByteBufAllocator#calculateNewCapacity(int, int)},
     * which doubles buffer capacity by normalizing it to the closest power of two.
     * This assumption is verified in unit tests for this method.
     */
    private static void mergeWithCompositeTail(
            ByteBufAllocator alloc, CompositeByteBuf composite, ByteBuf in) {
        int inputSize = in.readableBytes();
        int tailComponentIndex = composite.numComponents() - 1;
        int tailStart = composite.toByteIndex(tailComponentIndex);
        int tailSize = composite.writerIndex() - tailStart;
        int newTailSize = inputSize + tailSize;

        ByteBuf tail = composite.component(tailComponentIndex);
        ByteBuf newTail = null;
        // Use componentSlice() to get the correct view of the indices.
        ByteBuf componentView = composite.componentSlice(tailComponentIndex);
        try {
            // Ideal case: The tail is not shared and can be expanded in-place.
            // In-place expansion should happen only if the component represents the full
            // capacity of the underlying buffer because if tail.capacity() >
            // componentView.capacity(), it indicates the component is a partial slice
            // containing hidden "discarded" bytes. Expanding such a slice in-place would
            // "resurrect" those discarded bytes leading to silent data corruption.
            if (tail.refCnt() == 1 && !tail.isReadOnly() && tail.capacity() == componentView.capacity()
                    && newTailSize <= tail.maxCapacity()) {
                // Take ownership of the tail.
                newTail = tail.retain();

                // Synchronize indices based on the component's view in the composite buffer.
                newTail.setIndex(componentView.readerIndex(), componentView.writerIndex());

                /*
                 * The tail is a readable non-composite buffer, so writeBytes() handles
                 * everything for us.
                 *
                 * - ensureWritable() performs a fast resize when possible (f.e. PooledByteBuf
                 * simply updates its boundary to the end of consecutive memory run assigned to
                 * this buffer)
                 * - when the required size doesn't fit into writableBytes(), a new buffer is
                 * allocated, and the capacity is calculated with alloc.calculateNewCapacity()
                 * - note that maxFastWritableBytes() would normally allow a fast expansion of
                 * PooledByteBuf is not called because CompositeByteBuf.component() returns a
                 * duplicate, wrapped buffer.
                 * Unwrapping buffers is unsafe, and potential benefit of fast writes may not be
                 * as pronounced because the capacity is doubled with each reallocation.
                 */
                newTail.writeBytes(in);
            } else {
                // Fallback strategy: Reallocate a new buffer to merge the tail and input.
                // This ensures absolute index consistency and prevents data corruption
                // from hidden offsets in sliced or derived buffers.
                newTail = alloc.buffer(alloc.calculateNewCapacity(newTailSize, Integer.MAX_VALUE));
                newTail.setBytes(0, composite, tailStart, tailSize)
                        .setBytes(tailSize, in, in.readerIndex(), inputSize)
                        .writerIndex(newTailSize);
                in.readerIndex(in.writerIndex());
            }

            // Store readerIndex to avoid out-of-bounds writerIndex during replacement.
            int prevReader = composite.readerIndex();

            // Remove the old tail and add the new one.
            composite.removeComponent(tailComponentIndex).setIndex(0, tailStart);

            // newTail ownership successfully transferred to the composite buffer.
            // We null out newTail before adding it to avoid a double-release if addFlattenedComponents throws.
            ByteBuf b = newTail;
            newTail = null;
            composite.addFlattenedComponents(true, b);

            // Restore the reader. We do this before releasing 'in' so that if it fails,
            // the caller's finally block will handle releasing 'in' without a double-free.
            composite.readerIndex(prevReader);
        } finally {
            in.release();
            // If new tail's ownership isn't transferred to the composite buf.
            // Release it to prevent a leak.
            if (newTail != null) {
                newTail.release();
            }
        }
    }
}
