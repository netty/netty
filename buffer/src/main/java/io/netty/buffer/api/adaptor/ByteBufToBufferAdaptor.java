/*
 * Copyright 2021 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.buffer.api.adaptor;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.api.Buffer;
import io.netty.buffer.api.BufferAllocator;
import io.netty.buffer.api.BufferStub;

/**
 * An adapter to convert {@link ByteBuf} to {@link Buffer} by copying data.
 */
public final class ByteBufToBufferAdaptor extends BufferStub {
    /**
     * Allocates a new {@link Buffer} using the provided {@link BufferAllocator} and copies all data from the passed
     * {@link ByteBuf}.
     *
     * @param allocator to allocate a new {@link Buffer}.
     * @param byteBuf source of the data to copy into this buffer.
     */
    public ByteBufToBufferAdaptor(BufferAllocator allocator, ByteBuf byteBuf) {
        super(copyToBuffer(allocator, byteBuf));
    }

    private static Buffer copyToBuffer(BufferAllocator allocator, ByteBuf src) {
        return allocator.allocate(src.capacity()).writeBytes(ByteBufUtil.getBytes(src));
    }
}
