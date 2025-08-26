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
 * A fake {@link Ticker} that allows the caller control the flow of time.
 * This can be useful when you test time-sensitive logic without waiting for too long
 * or introducing flakiness due to non-deterministic nature of system clock.
 */
public interface MockTicker extends Ticker {

    @Override
    default long initialNanoTime() {
        return 0;
    }

    /**
     * Advances the current {@link #nanoTime()} by the given amount of time.
     *
     * @param amount the amount of time to advance this ticker by.
     * @param unit the {@link TimeUnit} of {@code amount}.
     */
    void advance(long amount, TimeUnit unit);

    /**
     * Advances the current {@link #nanoTime()} by the given amount of time.
     *
     * @param amountMillis the number of milliseconds to advance this ticker by.
     */
    default void advanceMillis(long amountMillis) {
        advance(amountMillis, TimeUnit.MILLISECONDS);
    }
}
