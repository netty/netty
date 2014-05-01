/*
 * Copyright 2014 The Netty Project
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

package io.netty.util.metrics;

/**
 * Maintains the sum of the last N ticks.
 */
public class CumulativeHistory {
    private long tickValue;
    private volatile long totalValue;
    private long[] pastValues;
    private int idx;

    /**
     * @param pastTicks     number of ticks to keep track of
     */
    public CumulativeHistory(int pastTicks) {
        if (pastTicks < 1) {
            throw new IllegalArgumentException("pastTicks must not be smaller than one.");
        }

        pastValues = new long[pastTicks];
    }

    public void update(long by) {
        tickValue += by;
    }

    public void tick() {
        totalValue -= pastValues[idx];
        totalValue += tickValue;
        pastValues[idx] = tickValue;
        tickValue = 0;

        idx = ++idx < pastValues.length ? idx : 0;
    }

    public long value() {
        return totalValue;
    }
}
