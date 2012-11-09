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
package io.netty.monitor.yammer;

import io.netty.monitor.CounterMonitor;
import io.netty.monitor.EventRateMonitor;
import io.netty.monitor.MonitorName;
import io.netty.monitor.MonitorRegistry;
import io.netty.monitor.ValueDistributionMonitor;
import io.netty.monitor.ValueMonitor;

import java.util.concurrent.TimeUnit;

import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.Counter;
import com.yammer.metrics.core.Gauge;
import com.yammer.metrics.core.Histogram;
import com.yammer.metrics.core.Meter;
import com.yammer.metrics.core.MetricsRegistry;

/**
 * <p>
 * A {@link MonitorRegistry} that delegates to a <a
 * href="http://metrics.codahale.com/">Yammer</a> {@link MetricsRegistry}.
 * </p>
 */
public final class YammerMonitorRegistry implements MonitorRegistry {

    private final MetricsRegistry delegate;

    /**
     * Constructs a {@code YammerMonitorRegistry} that delegates to
     * {@code Yammer}'s {@link Metrics#defaultRegistry() default registry}.
     */
    public YammerMonitorRegistry() {
        this(Metrics.defaultRegistry());
    }

    /**
     * Constructs a {@code YammerMonitorRegistry} that delegates to the supplied
     * {@code Yammer} {@link MetricsRegistry delegate}.
     * @param delegate The {@code Yammer} {@link MetricsRegistry} to delegate to
     */
    public YammerMonitorRegistry(final MetricsRegistry delegate) {
        if (delegate == null) {
            throw new NullPointerException("delegate");
        }
        this.delegate = delegate;
    }

    /**
     * Create a new {@link ValueDistributionMonitor} that is backed by a
     * {@code Yammer} {@link Histogram}.
     * @see io.netty.monitor.MonitorRegistry#newValueDistributionMonitor(io.netty.monitor.MonitorName)
     */
    @Override
    public ValueDistributionMonitor newValueDistributionMonitor(final MonitorName monitorName) {
        final Histogram histogram = delegate.newHistogram(Utils.toMetricName(monitorName), true);
        return new YammerValueDistributionMonitor(histogram);
    }

    /**
     * Create a new {@link EventRateMonitor} that is backed by a {@code Yammer}
     * {@link Meter}.
     * @see io.netty.monitor.MonitorRegistry#newEventRateMonitor(io.netty.monitor.MonitorName,
     *      java.util.concurrent.TimeUnit)
     */
    @Override
    public EventRateMonitor newEventRateMonitor(final MonitorName monitorName, final TimeUnit rateUnit) {
        final Meter meter = delegate.newMeter(Utils.toMetricName(monitorName), monitorName.getName(), rateUnit);
        return new YammerEventRateMonitor(meter);
    }

    /**
     * Register the supplied {@link ValueMonitor valueMonitor}, using it
     * internally to create a {@code Yammer} {@link Gauge}.
     * @see io.netty.monitor.MonitorRegistry#registerValueMonitor(io.netty.monitor.MonitorName,
     *      io.netty.monitor.ValueMonitor)
     */
    @Override
    public <T> ValueMonitor<T> registerValueMonitor(final MonitorName monitorName, final ValueMonitor<T> valueMonitor) {
        delegate.newGauge(Utils.toMetricName(monitorName), new Gauge<T>() {
            @Override
            public T value() {
                return valueMonitor.currentValue();
            }
        });
        return valueMonitor;
    }

    /**
     * Create a new {@link CounterMonitor} that is backed by a {@code Yammer}
     * {@link Counter}.
     * @see io.netty.monitor.MonitorRegistry#newCounterMonitor(io.netty.monitor.MonitorName)
     */
    @Override
    public CounterMonitor newCounterMonitor(MonitorName monitorName) {
        final Counter counter = delegate.newCounter(Utils.toMetricName(monitorName));
        return new YammerCounterMonitor(counter);
    }

    /**
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "YammerMonitorRegistry(delegate=" + delegate + ')';
    }
}
