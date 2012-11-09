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
package io.netty.monitor.yammer.spi;

import io.netty.monitor.MonitorRegistry;
import io.netty.monitor.spi.MonitorProvider;
import io.netty.monitor.spi.MonitorRegistryFactory;
import io.netty.monitor.yammer.YammerMonitorRegistry;
import io.netty.monitor.yammer.YammerProvider;

import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.MetricsRegistry;

/**
 * <p>
 * A {@link MonitorRegistryFactory} that produces {@link YammerMonitorRegistry
 * YammerMonitorRegistries}.
 * </p>
 */
public class YammerMonitorRegistryFactory implements MonitorRegistryFactory {

    private final MetricsRegistry metricsRegistry;

    /**
     */
    public YammerMonitorRegistryFactory() {
        this(Metrics.defaultRegistry());
    }

    /**
     * @param metricsRegistry
     */
    public YammerMonitorRegistryFactory(final MetricsRegistry metricsRegistry) {
        if (metricsRegistry == null) {
            throw new NullPointerException("metricsRegistry");
        }
        this.metricsRegistry = metricsRegistry;
    }

    /**
     * @see io.netty.monitor.spi.MonitorRegistryFactory#provider()
     */
    @Override
    public MonitorProvider provider() {
        return YammerProvider.PROVIDER;
    }

    /**
     * @see io.netty.monitor.spi.MonitorRegistryFactory#newMonitorRegistry()
     */
    @Override
    public MonitorRegistry newMonitorRegistry() {
        return new YammerMonitorRegistry(metricsRegistry);
    }

    /**
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "YammerMonitorRegistryFactory(metricsRegistry=" + metricsRegistry + ')';
    }
}
