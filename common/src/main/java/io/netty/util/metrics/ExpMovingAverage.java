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

import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import static java.lang.Math.exp;

/*
 * Exponentially Weighted Moving Average.
 *
 * For more details have a look at the
 * <a href="http://en.wikipedia.org/wiki/Exponential_smoothing#The_exponential_moving_average">Wikipedia article</a>
 * and Neil J. Gunther's <a href="http://www.teamquest.com/pdfs/whitepaper/ldavg1.pdf">discussion</a> of the
 * UNIX load average.
 */
public class ExpMovingAverage {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ExpMovingAverage.class);

    private volatile double value;
    private long uncounted;

    private double alpha;
    private int interval;

    private long lastTickTime;
    private boolean initialized;

    /**
     * @param alpha factor at which to devalue the average per tick
     * @param interval  interval in seconds at which {@link #tick()} will be called.
     */
    public ExpMovingAverage(double alpha, int interval) {
        if (alpha < 0 || alpha > 1) {
            throw new IllegalArgumentException("alpha must be between 0 and 1");
        }

        if (interval < 1) {
            throw new IllegalArgumentException("interval must be bigger than 1");
        }

        this.alpha = alpha;
        this.interval = interval;
    }

    public static ExpMovingAverage loadAverageOneMinute() {
        return new ExpMovingAverage(exp(-5.0 / 60), 5);
    }

    public static ExpMovingAverage loadAverageFiveMinutes() {
        return new ExpMovingAverage(exp(-5.0 / 60 / 5), 5);
    }

    public static ExpMovingAverage loadAverageFifteenMinutes() {
        return new ExpMovingAverage(exp(-5.0 / 60 / 15), 5);
    }

    /**
     * Adds the value to the previous values passed to {@link #update(int)}. This will only
     * be represented in the exponential moving average after {@link #tick()} was called.
     */
    public void update(int by) {
        uncounted += by;
    }

    /**
     * This method should be called every {@link #interval} seconds to update
     * the exponential moving average {@link #value}. This method will reset {@link #value}
     * to zero.
     */
    public void tick() {
        final double newValue = normalizeAndResetUncounted();
        if (initialized) {
            value = value * (1 - alpha) + newValue * alpha;
        } else {
            value = newValue;
            initialized = true;
        }
    }

    /**
     * @return  The exponential moving average of all data points collected before
     *          the last call to {@link #tick()}.
     */
    public double value() {
        return value;
    }

    private double normalizeAndResetUncounted() {
        double newValue;
        final long now = System.nanoTime();
        if (initialized) {
            final double deltaSeconds = (now - lastTickTime) / 1000.0 / 1000 / 1000;
            final double inaccRatio = deltaSeconds / interval;
            if (inaccRatio > 0.1 && inaccRatio < 1.1) {
                newValue = uncounted;
            } else {
                newValue = uncounted * (interval / deltaSeconds);

                if (logger.isDebugEnabled()) {
                    logger.debug("The actual tick value interval was " + deltaSeconds + " seconds. " +
                                 "Should have been " + interval + " seconds.");
                }
            }
        } else {
            newValue = uncounted;
        }

        lastTickTime = now;
        uncounted = 0;

        return newValue;
    }
}
