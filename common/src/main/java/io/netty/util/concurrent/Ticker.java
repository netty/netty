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

/**
 * A nanosecond-based time source, e.g. {@link System#nanoTime()}.
 */
public interface Ticker {
    /**
     * Returns the singleton {@link Ticker} that returns the values from the real system clock source.
     * However, note that this is not the same as {@link System#nanoTime()} because we apply a fixed offset
     * to the {@link System#nanoTime() nanoTime}.
     */
    static Ticker systemTicker() {
        return SystemTicker.INSTANCE;
    }

    /**
     * Returns a newly created mock {@link Ticker} that allows the caller control the flow of time.
     * This can be useful when you test time-sensitive logic without waiting for too long or introducing
     * flakiness due to non-deterministic nature of system clock.
     */
    static MockTicker newMockTicker() {
        return new DefaultMockTicker();
    }

    /**
     * The initial value used for delay and computations based upon a monotonic time source.
     * @return initial value used for delay and computations based upon a monotonic time source.
     */
    long initialNanoTime();

    /**
     * The time elapsed since initialization of this class in nanoseconds. This may return a negative number just like
     * {@link System#nanoTime()}.
     */
    long nanoTime();

    /**
     * Waits until the given amount of time goes by.
     *
     * @param delay the amount of delay.
     * @param unit the {@link TimeUnit} of {@code delay}.
     *
     * @see Thread#sleep(long)
     */
    void sleep(long delay, TimeUnit unit) throws InterruptedException;

    /**
     * Waits until the given amount of time goes by.
     *
     * @param delayMillis the number of milliseconds.
     *
     * @see Thread#sleep(long)
     */
    default void sleepMillis(long delayMillis) throws InterruptedException {
        sleep(delayMillis, TimeUnit.MILLISECONDS);
    }
}
