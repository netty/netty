/*
 * Copyright 2025 The Netty Project
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
package io.netty.util.concurrent;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static io.netty.util.internal.ObjectUtil.checkPositiveOrZero;
import static java.util.Objects.requireNonNull;

/**
 * The default {@link MockTicker} implementation.
 */
final class DefaultMockTicker implements MockTicker {

    private final Lock lock = new ReentrantLock();
    private final Condition cond = lock.newCondition();
    private final AtomicLong nanoTime = new AtomicLong();

    @Override
    public long nanoTime() {
        return nanoTime.get();
    }

    @Override
    public void sleep(long delay, TimeUnit unit) throws InterruptedException {
        checkPositiveOrZero(delay, "delay");
        requireNonNull(unit, "unit");

        if (delay == 0) {
            return;
        }

        final long startTimeNanos = nanoTime();
        final long delayNanos = unit.toNanos(delay);
        lock.lockInterruptibly();
        try {
            do {
                cond.await();
            } while (nanoTime() - startTimeNanos < delayNanos);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void advance(long amount, TimeUnit unit) {
        checkPositiveOrZero(amount, "amount");
        requireNonNull(unit, "unit");

        if (amount == 0) {
            return;
        }

        final long amountNanos = unit.toNanos(amount);
        lock.lock();
        try {
            nanoTime.addAndGet(amountNanos);
            cond.signalAll();
        } finally {
            lock.unlock();
        }
    }
}
