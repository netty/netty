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

package io.netty.monitor.support;

import io.netty.monitor.CounterMonitor;
import io.netty.monitor.EventRateMonitor;
import io.netty.monitor.MonitorName;
import io.netty.monitor.MonitorRegistry;
import io.netty.monitor.ValueDistributionMonitor;
import io.netty.monitor.ValueMonitor;
import io.netty.monitor.spi.MonitorProvider;
import io.netty.monitor.spi.MonitorRegistryFactory;

import java.util.concurrent.TimeUnit;

public class AnotherSampleMonitorRegistryFactory implements MonitorRegistryFactory {

    public static final MonitorProvider PROVIDER = MonitorProvider.named("ANOTHER_SAMPLE");

    @Override
    public MonitorProvider provider() {
        return PROVIDER;
    }

    @Override
    public MonitorRegistry newMonitorRegistry() {
        return new AnotherSampleMonitorRegistry();
    }

    public static final class AnotherSampleMonitorRegistry implements MonitorRegistry {

        @Override
        public ValueDistributionMonitor newValueDistributionMonitor(final MonitorName monitorName) {
            return new ValueDistributionMonitor() {
                @Override
                public void update(final long value) {
                }

                @Override
                public void reset() {
                }
            };
        }

        @Override
        public EventRateMonitor newEventRateMonitor(final MonitorName monitorName, final TimeUnit rateUnit) {
            return new EventRateMonitor() {
                @Override
                public void events(final long count) {
                }

                @Override
                public void event() {
                }
            };
        }

        @Override
        public <T> ValueMonitor<T> registerValueMonitor(MonitorName monitorName, ValueMonitor<T> valueMonitor) {
            return valueMonitor;
        }

        @Override
        public CounterMonitor newCounterMonitor(MonitorName monitorName) {
            return new CounterMonitor() {
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
        }
    }
}
