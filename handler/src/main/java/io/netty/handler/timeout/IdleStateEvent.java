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
package io.netty.handler.timeout;

import io.netty.channel.Channel;

/**
 * A user event triggered by {@link IdleStateHandler} when a {@link Channel} is idle.
 *
 * @apiviz.landmark
 * @apiviz.has io.netty.handler.timeout.IdleState oneway - -
 */
public final class IdleStateEvent {

    private final IdleState state;
    private final int count;
    private final long durationMillis;

    /**
     * Create a new instance
     *
     * @param state             the detailed idle state.
     * @param count             the count how often this kind of {@link IdleStateEvent} was fired before
     * @param durationMillis    the duration which caused the {@link IdleStateEvent} to get fired in milliseconds
     */
    public IdleStateEvent(IdleState state, int count, long durationMillis) {
        if (state == null) {
            throw new NullPointerException("state");
        }
        if (count < 0) {
            throw new IllegalStateException(String.format("count: %d (expected: >= 0)", count));
        }
        if (durationMillis < 0) {
            throw new IllegalStateException(String.format(
                    "durationMillis: %d (expected: >= 0)", durationMillis));
        }

        this.state = state;
        this.count = count;
        this.durationMillis = durationMillis;
    }

    /**
     * Returns the detailed idle state.
     */
    public IdleState state() {
        return state;
    }

    /**
     * Return the count how often this kind of {@link IdleStateEvent} was fired before.
     */
    public int count() {
        return count;
    }

    /**
     * Return the duration which caused the {@link IdleStateEvent} to get fired in milliseconds.
     */
    public long durationMillis() {
        return durationMillis;
    }

    @Override
    public String toString() {
        return state + "(" + count + ", " + durationMillis + "ms)";
    }
}
