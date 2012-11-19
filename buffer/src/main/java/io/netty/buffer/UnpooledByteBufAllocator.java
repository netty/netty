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
public class UnpooledByteBufAllocator extends AbstractByteBufAllocator {

    private final Semaphore s = new Semaphore(0);

    public UnpooledByteBufAllocator() {
        super(false);
    }

    public UnpooledByteBufAllocator(boolean directByDefault) {
        super(directByDefault);
    }

    @Override
    protected ByteBuf newHeapBuffer(int initialCapacity, int maxCapacity) {
        return new UnpooledHeapByteBuf(this, initialCapacity, maxCapacity);
    }

    @Override
    protected ByteBuf newDirectBuffer(int initialCapacity, int maxCapacity) {
        return new UnpooledDirectByteBuf(this, initialCapacity, maxCapacity);
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
}
