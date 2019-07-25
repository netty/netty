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
package io.netty.handler.traffic;

import io.netty.handler.traffic.GlobalChannelTrafficShapingHandler.PerChannel;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Version for {@link GlobalChannelTrafficShapingHandler}.
 * This TrafficCounter is the Global one, and its special property is to directly handle
 * other channel's TrafficCounters. In particular, there are no scheduler for those
 * channel's TrafficCounters because it is managed by this one.
 */
public class GlobalChannelTrafficCounter extends TrafficCounter {
    /**
     * @param trafficShapingHandler the associated {@link GlobalChannelTrafficShapingHandler}.
     * @param executor the underlying executor service for scheduling checks (both Global and per Channel).
     * @param name the name given to this monitor.
     * @param checkInterval the checkInterval in millisecond between two computations.
     */
    public GlobalChannelTrafficCounter(GlobalChannelTrafficShapingHandler trafficShapingHandler,
            ScheduledExecutorService executor, String name, long checkInterval) {
        super(trafficShapingHandler, executor, name, checkInterval);
        if (executor == null) {
            throw new IllegalArgumentException("Executor must not be null");
        }
    }

    /**
     * Class to implement monitoring at fix delay.
     * This version is Mixed in the way it mixes Global and Channel counters.
     */
    private static class MixedTrafficMonitoringTask implements Runnable {
        /**
         * The associated TrafficShapingHandler
         */
        private final GlobalChannelTrafficShapingHandler trafficShapingHandler1;

        /**
         * The associated TrafficCounter
         */
        private final TrafficCounter counter;

        /**
         * @param trafficShapingHandler The parent handler to which this task needs to callback to for accounting.
         * @param counter The parent TrafficCounter that we need to reset the statistics for.
         */
        MixedTrafficMonitoringTask(
                GlobalChannelTrafficShapingHandler trafficShapingHandler,
                TrafficCounter counter) {
            trafficShapingHandler1 = trafficShapingHandler;
            this.counter = counter;
        }

        @Override
        public void run() {
            if (!counter.monitorActive) {
                return;
            }
            long newLastTime = milliSecondFromNano();
            counter.resetAccounting(newLastTime);
            for (PerChannel perChannel : trafficShapingHandler1.channelQueues.values()) {
                perChannel.channelTrafficCounter.resetAccounting(newLastTime);
            }
            trafficShapingHandler1.doAccounting(counter);
        }
    }

    /**
     * Start the monitoring process.
     */
    @Override
    public synchronized void start() {
        if (monitorActive) {
            return;
        }
        lastTime.set(milliSecondFromNano());
        long localCheckInterval = checkInterval.get();
        if (localCheckInterval > 0) {
            monitorActive = true;
            monitor = new MixedTrafficMonitoringTask((GlobalChannelTrafficShapingHandler) trafficShapingHandler, this);
            scheduledFuture =
                executor.scheduleAtFixedRate(monitor, 0, localCheckInterval, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Stop the monitoring process.
     */
    @Override
    public synchronized void stop() {
        if (!monitorActive) {
            return;
        }
        monitorActive = false;
        resetAccounting(milliSecondFromNano());
        trafficShapingHandler.doAccounting(this);
        if (scheduledFuture != null) {
            scheduledFuture.cancel(true);
        }
    }

    @Override
    public void resetCumulativeTime() {
        for (PerChannel perChannel :
            ((GlobalChannelTrafficShapingHandler) trafficShapingHandler).channelQueues.values()) {
            perChannel.channelTrafficCounter.resetCumulativeTime();
        }
        super.resetCumulativeTime();
    }

}
