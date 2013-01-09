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
 * A simple monitor that increments and/or decrements a {@code long} value.
 * </p>
 * <p>
 * <strong>DISCLAIMER</strong> This interface is heavily based on <a
 * href="http://metrics.codahale.com/">Yammer's</a>
 * {@link com.yammer.metrics.core.Counter Counter}.
 * </p>
 */
public interface CounterMonitor {

    /**
     * Null object.
     */
    CounterMonitor NOOP = new CounterMonitor() {

        @Override
        public void reset() {
        }

        @Override
        public void increment(long delta) {
        }

        @Override
        public void increment() {
        }

        @Override
        public void decrement(long delta) {
        }

        @Override
        public void decrement() {
        }
    };

    /**
     * Increment this counter by 1.
     */
    void increment();

    /**
     * Increment this counter by the supplied {@code delta}.
     * @param delta The delta to apply
     */
    void increment(long delta);

    /**
     * Decrement this counter by 1.
     */
    void decrement();

    /**
     * Decrement this counter by the supplied {@code delta}.
     * @param delta The delta to apply
     */
    void decrement(long delta);

    /**
     * Reset this counter to its initial state.
     */
    void reset();
}
