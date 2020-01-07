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
package io.netty.handler.codec.compression;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

/**
 * Decompresses a {@link ByteBuf} using the deflate algorithm.
 */
public abstract class ZlibDecoder extends ByteToMessageDecoder {

    /**
     * Maximum allowed size of the decompression buffer.
     */
    protected int maxAllocation;

    /**
     * Same as {@link #ZlibDecoder(int)} with maxAllocation = 0.
     */
    public ZlibDecoder() {
        this(0);
    }

    /**
     * Construct a new ZlibDecoder.
     * @param maxAllocation
     *          Maximum size of the decompression buffer. Must be &gt;= 0.
     *          If zero, maximum size is decided by the {@link ByteBufAllocator}.
     */
    public ZlibDecoder(int maxAllocation) {
        if (maxAllocation < 0) {
            throw new IllegalArgumentException("maxAllocation must be >= 0");
        }
        this.maxAllocation = maxAllocation;
    }

    /**
     * Returns {@code true} if and only if the end of the compressed stream
     * has been reached.
     */
    public abstract boolean isClosed();

    /**
     * Allocate or expand the decompression buffer, without exceeding the maximum allocation.
     * Calls {@link #maxAllocationReached(ByteBuf)} if the buffer is full and cannot be expanded further.
     */
    protected ByteBuf prepareDecompressBuffer(ChannelHandlerContext ctx, ByteBuf buffer, int preferredSize) {
        if (buffer == null) {
            if (maxAllocation == 0) {
                return ctx.alloc().heapBuffer(preferredSize);
            } else {
                return ctx.alloc().heapBuffer(Math.min(preferredSize, maxAllocation), maxAllocation);
            }
        }

        if (buffer.ensureWritable(preferredSize, true) == 1) {
            return maxAllocationReached(buffer);
        }

        return buffer;
    }

    /**
     * Called when the decompression buffer cannot be expanded further.
     * Default implementation throws an exception, but subclasses can override to change behavior.
     */
    protected ByteBuf maxAllocationReached(ByteBuf buffer) {
        throw new DecompressionException("Decompression buffer has reached maximum size: " + buffer.maxCapacity());
    }

}
