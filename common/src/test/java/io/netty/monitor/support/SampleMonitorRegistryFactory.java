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

public class SampleMonitorRegistryFactory implements MonitorRegistryFactory {

    public static final MonitorProvider PROVIDER = MonitorProvider.named("SAMPLE");

    @Override
    public MonitorProvider provider() {
        return PROVIDER;
    }

    @Override
    public MonitorRegistry newMonitorRegistry() {
        return new SampleMonitorRegistry();
    }

    public static final class SampleMonitorRegistry implements MonitorRegistry {

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
                public void inc(long delta) {
                }

                @Override
                public void inc() {
                }

                @Override
                public void decr(long delta) {
                }

                @Override
                public void decr() {
                }
            };
        }
    }
}
