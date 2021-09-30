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
package io.netty.buffer.api.internal;

import io.netty.buffer.api.AllocationType;
import io.netty.buffer.api.Buffer;
import io.netty.buffer.api.BufferAllocator;
import io.netty.util.internal.UnstableApi;

import java.util.function.Supplier;

import static io.netty.util.internal.ObjectUtil.checkNotNullWithIAE;
import static java.lang.Runtime.getRuntime;

/**
 * Internal wrapper for making allocators uncloseable.
 *
 * @implNote Beware that each uncloseable allocator adds a shut-down hook for closing themselves when the JVM shuts
 * down. These shut-down hooks cannot be removed, so uncloseable allocators should only be created in a very small
 * number, and reused throughout the lifetime of the JVM.
 */
@UnstableApi
public class UncloseableBufferAllocator implements BufferAllocator {
    private final BufferAllocator delegate;
    private final String uncloseableMessage;

    protected UncloseableBufferAllocator(BufferAllocator delegate, String uncloseableMessage) {
        this.delegate = checkNotNullWithIAE(delegate, "delegate");
        this.uncloseableMessage = uncloseableMessage;
        getRuntime().addShutdownHook(new Thread(delegate::close));
    }

    @Override
    public final boolean isPooling() {
        return delegate.isPooling();
    }

    @Override
    public final AllocationType getAllocationType() {
        return delegate.getAllocationType();
    }

    @Override
    public final Buffer allocate(int size) {
        return delegate.allocate(size);
    }

    @Override
    public final Supplier<Buffer> constBufferSupplier(byte[] bytes) {
        return delegate.constBufferSupplier(bytes);
    }

    /**
     * @throws UnsupportedOperationException Close is not supported on this allocator.
     */
    @Override
    public final void close() {
        throw new UnsupportedOperationException(uncloseableMessage);
    }
}
