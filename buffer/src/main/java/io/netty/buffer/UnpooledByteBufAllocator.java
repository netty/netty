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

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Simplistic {@link ByteBufAllocator} implementation that does not pool anything.
 */
public class UnpooledByteBufAllocator implements ByteBufAllocator {

    private final Semaphore s = new Semaphore(0);

    @Override
    public ByteBuf buffer() {
        return heapBuffer();
    }

    @Override
    public ByteBuf buffer(int initialCapacity) {
        return heapBuffer(initialCapacity);
    }

    @Override
    public ByteBuf buffer(int initialCapacity, int maxCapacity) {
        return heapBuffer(initialCapacity, maxCapacity);
    }

    @Override
    public ByteBuf heapBuffer() {
        ensureNotShutdown();
        return Unpooled.buffer();
    }

    @Override
    public ByteBuf heapBuffer(int initialCapacity) {
        ensureNotShutdown();
        return Unpooled.buffer(initialCapacity);
    }

    @Override
    public ByteBuf heapBuffer(int initialCapacity, int maxCapacity) {
        ensureNotShutdown();
        return Unpooled.buffer(initialCapacity, maxCapacity);
    }

    @Override
    public ByteBuf directBuffer() {
        ensureNotShutdown();
        return Unpooled.directBuffer();
    }

    @Override
    public ByteBuf directBuffer(int initialCapacity) {
        ensureNotShutdown();
        return Unpooled.directBuffer(initialCapacity);
    }

    @Override
    public ByteBuf directBuffer(int initialCapacity, int maxCapacity) {
        ensureNotShutdown();
        return Unpooled.directBuffer(initialCapacity, maxCapacity);
    }

    @Override
    public synchronized void shutdown() {
        if (isShutdown()) {
            return;
        }

        s.release();
    }

    @Override
    public boolean isShutdown() {
        return s.availablePermits() > 0;
    }

    @Override
    public boolean isTerminated() {
        return isShutdown();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        if (s.tryAcquire(timeout, unit)) {
            s.release();
        }
        return isTerminated();
    }

    private void ensureNotShutdown() {
        if (isShutdown()) {
            throw new IllegalStateException("shut down already");
        }
    }
}
