/*
 * Copyright 2025 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.util.concurrent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A factory that creates {@link AutoScalingEventExecutorChooser} instances.
 * This chooser implements a dynamic, utilization-based auto-scaling strategy.
 * <p>
 * It enables the Event loop group to automatically scale the number of active {@code EventLoop}
 * threads between a minimum and maximum threshold. The scaling decision is based on the average
 * utilization of the active threads, measured over a configurable time window.
 * <p>
 * An {@code EventLoop} can be suspended if its utilization is consistently below the
 * {@code scaleDownThreshold}. Conversely, if the group's average utilization is consistently
 * above the {@code scaleUpThreshold}, a suspended thread will be automatically resumed to handle
 * the increased load.
 * <p>
 * To control the aggressiveness of scaling actions, the {@code maxRampUpStep} and {@code maxRampDownStep}
 * parameters limit the maximum number of threads that can be activated or suspended in a single scaling cycle.
 * Furthermore, to ensure decisions are based on sustained trends rather than transient spikes, the
 * {@code scalingPatienceCycles} defines how many consecutive monitoring windows a condition must be met
 * before a scaling action is triggered.
 */
public final class AutoScalingEventExecutorChooserFactory implements EventExecutorChooserFactory {

    private final int minChildren;
    private final int maxChildren;
    private final long utilizationCheckPeriodNanos;
    private final double scaleDownThreshold;
    private final double scaleUpThreshold;
    private final int maxRampUpStep;
    private final int maxRampDownStep;
    private final int scalingPatienceCycles;

    /**
     * Creates a new factory for a scaling-enabled {@link EventExecutorChooser}.
     *
     * @param minThreads               the minimum number of threads to keep active.
     * @param maxThreads               the maximum number of threads to scale up to.
     * @param utilizationWindow        the period at which to check group utilization.
     * @param windowUnit               the unit for {@code utilizationWindow}.
     * @param scaleDownThreshold       the average utilization below which a thread may be suspended.
     * @param scaleUpThreshold         the average utilization above which a thread may be resumed.
     * @param maxRampUpStep            the maximum number of threads to add in one cycle.
     * @param maxRampDownStep          the maximum number of threads to remove in one cycle.
     * @param scalingPatienceCycles    the number of consecutive cycles a condition must be met before scaling.
     */
    public AutoScalingEventExecutorChooserFactory(int minThreads, int maxThreads, long utilizationWindow,
                                                  TimeUnit windowUnit, double scaleDownThreshold,
                                                  double scaleUpThreshold, int maxRampUpStep, int maxRampDownStep,
                                                  int scalingPatienceCycles) {
        if (minThreads < 0 || minThreads > maxThreads || maxThreads == 0) {
            throw new IllegalArgumentException(String.format(
                    "minThreads: %d, maxThreads: %d (expected: 0 <= minThreads <= maxThreads, maxThreads > 0)",
                    minThreads, maxThreads));
        }
        if (utilizationWindow <= 0) {
            throw new IllegalArgumentException("utilizationWindow must be > 0: " + utilizationWindow);
        }
        if (windowUnit == null) {
            throw new NullPointerException("windowUnit");
        }
        if (scaleDownThreshold < 0.0 || scaleDownThreshold > 1.0) {
            throw new IllegalArgumentException(
                    "scaleDownThreshold must be between 0.0 and 1.0: " + scaleDownThreshold);
        }
        if (scaleUpThreshold < 0.0 || scaleUpThreshold > 1.0) {
            throw new IllegalArgumentException(
                    "scaleUpThreshold must be between 0.0 and 1.0: " + scaleUpThreshold);
        }
        if (scaleDownThreshold >= scaleUpThreshold) {
            throw new IllegalArgumentException(
                    "scaleDownThreshold must be less than scaleUpThreshold: " +
                    scaleDownThreshold + " >= " + scaleUpThreshold);
        }
        if (maxRampUpStep <= 0) {
            throw new IllegalArgumentException("maxRampUpStep must be > 0: " + maxRampUpStep);
        }
        if (maxRampDownStep <= 0) {
            throw new IllegalArgumentException("maxRampDownStep must be > 0: " + maxRampDownStep);
        }
        if (scalingPatienceCycles < 0) {
            throw new IllegalArgumentException("scalingPatienceCycles must be >= 0: " + scalingPatienceCycles);
        }

        this.minChildren = minThreads;
        this.maxChildren = maxThreads;
        this.utilizationCheckPeriodNanos = windowUnit.toNanos(utilizationWindow);
        this.scaleDownThreshold = scaleDownThreshold;
        this.scaleUpThreshold = scaleUpThreshold;
        this.maxRampUpStep = maxRampUpStep;
        this.maxRampDownStep = maxRampDownStep;
        this.scalingPatienceCycles = scalingPatienceCycles;
    }

    @Override
    public EventExecutorChooser newChooser(EventExecutor[] executors) {
        return new AutoScalingEventExecutorChooser(executors);
    }

    private final class AutoScalingEventExecutorChooser implements EventExecutorChooser {
        private final Runnable noOpTask = () -> { /* NOOP */ };
        private final EventExecutor[] executors;
        private final AtomicInteger activeChildrenCount;
        private final AtomicInteger nextWakeUpIndex = new AtomicInteger();
        private final EventExecutorChooser delegate;

        AutoScalingEventExecutorChooser(EventExecutor[] executors) {
            this.executors = executors;
            this.activeChildrenCount = new AtomicInteger(maxChildren);
            this.delegate = DefaultEventExecutorChooserFactory.INSTANCE.newChooser(executors);

            ScheduledFuture<?> utilizationMonitoringTask = GlobalEventExecutor.INSTANCE.scheduleAtFixedRate(
                    new UtilizationMonitor(), utilizationCheckPeriodNanos, utilizationCheckPeriodNanos,
                    TimeUnit.NANOSECONDS);

            if (executors.length > 0) {
                executors[0].terminationFuture().addListener(future -> utilizationMonitoringTask.cancel(false));
            }
        }

        @Override
        public EventExecutor next() {
            if (activeChildrenCount.get() < maxChildren) {
                // If we are operating below maximum capacity, try to scale up by one.
                // This handles on-demand scaling.
                tryScaleUpBy(1);
            }
            return delegate.next();
        }

        /**
         * Tries to scale up by a given amount, respecting the maxChildren limit.
         * This method is thread-safe and reliably wakes up suspended threads.
         *
         * @param amount The desired number of threads to add.
         * @return The number of threads that were actually activated.
         */
        private int tryScaleUpBy(int amount) {
            if (amount <= 0) {
                return 0;
            }

            int wokenUp = 0;
            for (;;) {
                int currentActive = activeChildrenCount.get();
                if (currentActive >= maxChildren) {
                    return 0;
                }

                int canAdd = Math.min(amount, maxChildren - currentActive);
                int newActive = currentActive + canAdd;

                if (activeChildrenCount.compareAndSet(currentActive, newActive)) {
                    final int startIndex = nextWakeUpIndex.getAndIncrement();

                    for (int i = 0; i < executors.length; i++) {
                        EventExecutor child = executors[(startIndex + i) % executors.length];

                        if (wokenUp >= canAdd) {
                            break; // We have woken up all the threads we reserved.
                        }
                        if (child instanceof SingleThreadEventExecutor) {
                            SingleThreadEventExecutor stee = (SingleThreadEventExecutor) child;
                            if (stee.isSuspended()) {
                                stee.execute(noOpTask);
                                wokenUp++;
                            }
                        }
                    }

                    // We reserved 'canAdd' slots but found fewer suspended threads
                    // to wake up (in a lost race condition). Release the unused slots.
                    if (wokenUp < canAdd) {
                        activeChildrenCount.addAndGet(-(canAdd - wokenUp));
                    }
                    return wokenUp;
                }
            }
        }

        private final class UtilizationMonitor implements Runnable {
            private final List<SingleThreadEventExecutor> consistentlyIdleChildren = new ArrayList<>(maxChildren);

            @Override
            public void run() {
                if (executors.length == 0 || executors[0].isShuttingDown()) {
                    // The group is shutting down, so no scaling decisions should be made.
                    // The lifecycle listener on the terminationFuture will handle the final cancellation.
                    return;
                }

                int consistentlyBusyChildren = 0;
                consistentlyIdleChildren.clear();

                for (EventExecutor child : executors) {
                    if (child.isSuspended() || !(child instanceof SingleThreadEventExecutor)) {
                        continue;
                    }

                    SingleThreadEventExecutor stee = (SingleThreadEventExecutor) child;

                    long activeTime = stee.getAndResetAccumulatedActiveTimeNanos();
                    final long totalTime = utilizationCheckPeriodNanos;

                    if (activeTime == 0) {
                        long lastActivity = stee.getLastActivityTimeNanos();
                        long idleTime = System.nanoTime() - lastActivity;

                        // If the event loop has been idle for less time than our utilization window,
                        // it means it was active for the remainder of that window.
                        if (idleTime < totalTime) {
                            activeTime = totalTime - idleTime;
                        }
                        // If idleTime >= totalTime, it was idle for the whole window, so activeTime remains 0.
                    }

                    double utilization = Math.min(1.0, (double) activeTime / totalTime);

                    if (utilization < scaleDownThreshold) {
                        // Utilization is low, increment idle counter and reset busy counter.
                        int idleCycles = stee.getAndIncrementIdleCycles();
                        stee.resetBusyCycles();
                        if (idleCycles >= scalingPatienceCycles) {
                            if (stee.getNumOfRegisteredChannels() <= 0) {
                                consistentlyIdleChildren.add(stee);
                            }
                        }
                    } else if (utilization > scaleUpThreshold) {
                        // Utilization is high, increment busy counter and reset idle counter.
                        int busyCycles = stee.getAndIncrementBusyCycles();
                        stee.resetIdleCycles();
                        if (busyCycles >= scalingPatienceCycles) {
                            consistentlyBusyChildren++;
                        }
                    } else {
                        // Utilization is in the normal range, reset counters.
                        stee.resetIdleCycles();
                        stee.resetBusyCycles();
                    }
                }

                // Make scaling decisions based on stable states.
                int currentActive = activeChildrenCount.get();

                if (consistentlyBusyChildren > 0 && currentActive < maxChildren) {
                    // Scale Up, we have children that have been busy for multiple cycles.
                    int threadsToAdd = Math.min(consistentlyBusyChildren, maxRampUpStep);
                    threadsToAdd = Math.min(threadsToAdd, maxChildren - currentActive);
                    if (threadsToAdd > 0) {
                        // tryScaleUpBy handles the atomic increment and wake-up.
                        tryScaleUpBy(threadsToAdd);
                    }
                } else if (!consistentlyIdleChildren.isEmpty() && currentActive > minChildren) {
                    // Scale down, we have children that have been idle for multiple cycles.

                    int threadsToRemove = Math.min(consistentlyIdleChildren.size(), maxRampDownStep);
                    threadsToRemove = Math.min(threadsToRemove, currentActive - minChildren);

                    int actuallyRemoved = 0;
                    for (int i = 0; i < threadsToRemove; i++) {
                        SingleThreadEventExecutor childToSuspend = consistentlyIdleChildren.get(i);
                        if (childToSuspend.trySuspend()) {
                            // Reset cycles upon suspension so it doesn't get immediately re-suspended on wake-up.
                            childToSuspend.resetBusyCycles();
                            childToSuspend.resetIdleCycles();
                            actuallyRemoved++;
                        }
                    }
                    if (actuallyRemoved > 0) {
                        //TODO: log # of suspended and current active threads?
                        activeChildrenCount.addAndGet(-actuallyRemoved);
                    }
                }
            }
        }
    }
}
