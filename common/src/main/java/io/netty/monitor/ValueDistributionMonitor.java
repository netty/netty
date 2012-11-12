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
package io.netty.monitor;

/**
 * <p>
 * A monitor that tracks a value's distribution. The value to track is
 * represented by a {@code long}.
 * </p>
 * <p>
 * <strong>DISCLAIMER</strong> This interface is heavily based on <a
 * href="http://metrics.codahale.com/">Yammer's</a>
 * {@link com.yammer.metrics.core.Histogram Histogram}.
 * </p>
 */
public interface ValueDistributionMonitor {

    /**
     * Null object.
     */
    ValueDistributionMonitor NOOP = new ValueDistributionMonitor() {

        @Override
        public void update(final long value) {
        }

        @Override
        public void reset() {
        }
    };

    /**
     * Clear this monitor, resetting it to its base state.
     */
    void reset();

    /**
     * Record {@code value}.
     */
    void update(long value);
}
