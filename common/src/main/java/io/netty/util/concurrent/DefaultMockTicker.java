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

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import static io.netty.util.internal.ObjectUtil.checkPositiveOrZero;
import static java.util.Objects.requireNonNull;

/**
 * The default {@link MockTicker} implementation.
 */
final class DefaultMockTicker implements MockTicker {

    // The lock is fair, so waiters get to process condition signals in the order they (the waiters) queued up.
    private final ReentrantLock lock = new ReentrantLock(true);
    private final Condition tickCondition = lock.newCondition();
    private final Condition sleeperCondition = lock.newCondition();
    private final AtomicLong nanoTime = new AtomicLong();
    private final Set<Thread> sleepers = Collections.newSetFromMap(new IdentityHashMap<>());

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

        final long delayNanos = unit.toNanos(delay);
        lock.lockInterruptibly();
        try {
            final long startTimeNanos = nanoTime();
            sleepers.add(Thread.currentThread());
            sleeperCondition.signalAll();
            do {
                tickCondition.await();
            } while (nanoTime() - startTimeNanos < delayNanos);
        } finally {
            sleepers.remove(Thread.currentThread());
            lock.unlock();
        }
    }

    /**
     * Wait for the given thread to enter the {@link #sleep(long, TimeUnit)} method, and block.
     */
    public void awaitSleepingThread(Thread thread) throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (!sleepers.contains(thread)) {
                sleeperCondition.await();
            }
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
            tickCondition.signalAll();
        } finally {
            lock.unlock();
        }
    }
}

