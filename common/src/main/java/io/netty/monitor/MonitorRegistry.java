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

import java.util.concurrent.TimeUnit;

/**
 * <p>
 * A factory for {@code Monitors}. Implementations are expected to keep a
 * reference to each created {@code Monitor} in order to provide additional
 * services, e.g. publish metrics fed by those {@code Monitors} via {@code JMX},
 * {@code HTTP}.
 * </p>
 */
public interface MonitorRegistry {

    /**
     * Null object.
     */
    MonitorRegistry NOOP = NoopMonitorRegistry.INSTANCE;

    /**
     * Create a new {@link ValueDistributionMonitor} having the supplied
     * {@link MonitorName monitorName}.
     * @param monitorName The new {@link ValueDistributionMonitor}'s
     *            {@link MonitorName}
     * @return A new {@link ValueDistributionMonitor} having the supplied
     *         {@link MonitorName monitorName}
     */
    ValueDistributionMonitor newValueDistributionMonitor(MonitorName monitorName);

    /**
     * Create a new {@link EventRateMonitor} having the supplied
     * {@link MonitorName monitorName}.
     * @param monitorName The new {@link EventRateMonitor}'s {@link MonitorName}
     * @param rateUnit The {@link TimeUnit resolution} to measure our event rate
     *            at
     * @return A new {@link EventRateMonitor} having the supplied
     *         {@link MonitorName monitorName} and {@link TimeUnit rateUnit}
     */
    EventRateMonitor newEventRateMonitor(MonitorName monitorName, TimeUnit rateUnit);

    /**
     * Register a new {@link ValueMonitor} for a datum of type {@code T}, having
     * the supplied {@link MonitorName monitorName}.
     * @param monitorName The new {@link ValueMonitor}'s {@link MonitorName}
     * @param valueMonitor The {@link ValueMonitor} to register
     * @return The {@link ValueMonitor} passed in
     */
    <T> ValueMonitor<T> registerValueMonitor(MonitorName monitorName, ValueMonitor<T> valueMonitor);

    /**
     * Create a new {@link CounterMonitor} having the supplied
     * {@link MonitorName monitorName}.
     * @param monitorName The new {@link CounterMonitor}'s {@link MonitorName}
     * @return A new {@link CounterMonitor} having the supplied
     *         {@link MonitorName monitorName}
     */
    CounterMonitor newCounterMonitor(MonitorName monitorName);
}
