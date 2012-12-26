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

package io.netty.util;

import io.netty.monitor.CounterMonitor;
import io.netty.monitor.EventRateMonitor;
import io.netty.monitor.MonitorName;
import io.netty.monitor.MonitorRegistry;
import io.netty.monitor.ValueDistributionMonitor;
import io.netty.monitor.ValueMonitor;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class HashedWheelTimerMonitorTest {

    @Test
    public final void shouldCallValueDistributionMonitorWhenTimeoutExpires() throws InterruptedException {
        final CountDownLatch eventDistributionCalled = new CountDownLatch(1);
        final ValueDistributionMonitor eventDistributionRecorder = new ValueDistributionMonitor() {
            @Override
            public void update(final long value) {
                eventDistributionCalled.countDown();
            }

            @Override
            public void reset() {
            }
        };

        final RecordingMonitorRegistry recordingMonitorRegistry = new RecordingMonitorRegistry(
                eventDistributionRecorder, EventRateMonitor.NOOP);

        final HashedWheelTimer objectUnderTest = new HashedWheelTimer(Executors.defaultThreadFactory(), 100,
                TimeUnit.MILLISECONDS, 512, recordingMonitorRegistry);
        objectUnderTest.newTimeout(new TimerTask() {
            @Override
            public void run(final Timeout timeout) throws Exception {
            }
        }, 1, TimeUnit.MILLISECONDS);

        assertTrue("HashedWheelTimer should have called ValueDistributionMonitor when Timeout expired",
                eventDistributionCalled.await(200, TimeUnit.MILLISECONDS));
    }

    private static class RecordingMonitorRegistry implements MonitorRegistry {

        private final ValueDistributionMonitor eventDistributionMonitor;

        private final EventRateMonitor eventRateMonitor;

        RecordingMonitorRegistry(final ValueDistributionMonitor eventDistributionMonitor,
                final EventRateMonitor eventRateMonitor) {
            this.eventDistributionMonitor = eventDistributionMonitor;
            this.eventRateMonitor = eventRateMonitor;
        }

        @Override
        public ValueDistributionMonitor newValueDistributionMonitor(final MonitorName monitorName) {
            return eventDistributionMonitor;
        }

        @Override
        public EventRateMonitor newEventRateMonitor(final MonitorName monitorName, final TimeUnit rateUnit) {
            return eventRateMonitor;
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

    @Test
    public final void shouldCallEventRateMonitorWhenTimeoutExpires() throws InterruptedException {
        final CountDownLatch eventRateCalled = new CountDownLatch(1);
        final EventRateMonitor eventRateRecorder = new EventRateMonitor() {
            @Override
            public void events(final long count) {
            }

            @Override
            public void event() {
                eventRateCalled.countDown();
            }
        };

        final RecordingMonitorRegistry recordingMonitorRegistry = new RecordingMonitorRegistry(
                ValueDistributionMonitor.NOOP, eventRateRecorder);

        final HashedWheelTimer objectUnderTest = new HashedWheelTimer(Executors.defaultThreadFactory(), 100,
                TimeUnit.MILLISECONDS, 512, recordingMonitorRegistry);
        objectUnderTest.newTimeout(new TimerTask() {
            @Override
            public void run(final Timeout timeout) throws Exception {
            }
        }, 1, TimeUnit.MILLISECONDS);

        assertTrue("HashedWheelTimer should have called EventRateMonitor when Timeout expired",
                eventRateCalled.await(200, TimeUnit.MILLISECONDS));
    }
}
