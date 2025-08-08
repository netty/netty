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
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A factory that creates auto-scaling {@link EventExecutorChooser} instances.
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

    /**
     * An immutable snapshot of the chooser's state. All state transitions
     * are managed by atomically swapping this object.
     */
    private static final class AutoScalingState {
        final int activeChildrenCount;
        final long nextWakeUpIndex;
        final EventExecutor[] activeExecutors;
        final EventExecutorChooser activeExecutorsChooser;

        AutoScalingState(int activeChildrenCount, long nextWakeUpIndex, EventExecutor[] activeExecutors) {
            this.activeChildrenCount = activeChildrenCount;
            this.nextWakeUpIndex = nextWakeUpIndex;
            this.activeExecutors = activeExecutors;
            this.activeExecutorsChooser = DefaultEventExecutorChooserFactory.INSTANCE.newChooser(activeExecutors);
        }
    }

    private final class AutoScalingEventExecutorChooser implements EventExecutorChooser {
        private final Runnable noOpTask = () -> { };
        private final EventExecutor[] executors;
        private final EventExecutorChooser allExecutorsChooser;
        private final AtomicReference<AutoScalingState> state;

        AutoScalingEventExecutorChooser(EventExecutor[] executors) {
            this.executors = executors;
            this.allExecutorsChooser = DefaultEventExecutorChooserFactory.INSTANCE.newChooser(executors);

            AutoScalingState initialState = new AutoScalingState(maxChildren, 0L, executors);
            this.state = new AtomicReference<>(initialState);

            ScheduledFuture<?> utilizationMonitoringTask = GlobalEventExecutor.INSTANCE.scheduleAtFixedRate(
                    new UtilizationMonitor(), utilizationCheckPeriodNanos, utilizationCheckPeriodNanos,
                    TimeUnit.NANOSECONDS);

            if (executors.length > 0) {
                executors[0].terminationFuture().addListener(future -> utilizationMonitoringTask.cancel(false));
            }
        }

        /**
         * This method is only responsible for picking from the active executors list.
         * The monitor handles all scaling decisions.
         */
        @Override
        public EventExecutor next() {
            // Get a snapshot of the current state.
            AutoScalingState currentState = this.state.get();

            if (currentState.activeExecutors.length == 0) {
                // This is only reachable if minChildren is 0 and the monitor has just suspended the last active thread.
                // To prevent an error and ensure the group can recover, we wake one up and use the
                // chooser that contains all executors as a safe temporary choice.
                tryScaleUpBy(1);
                return allExecutorsChooser.next();
            }
            return currentState.activeExecutorsChooser.next();
        }

        /**
         * Tries to increase the active thread count by waking up suspended executors.
         * This method is thread-safe and updates the state atomically.
         *
         * @param amount    The desired number of threads to add to the active count.
         */
        private void tryScaleUpBy(int amount) {
            if (amount <= 0) {
                return;
            }

            for (;;) {
                AutoScalingState oldState = state.get();
                if (oldState.activeChildrenCount >= maxChildren) {
                    return;
                }

                int canAdd = Math.min(amount, maxChildren - oldState.activeChildrenCount);
                List<EventExecutor> wokenUp = new ArrayList<>(canAdd);
                final long startIndex = oldState.nextWakeUpIndex;

                for (int i = 0; i < executors.length; i++) {
                    EventExecutor child = executors[(int) Math.abs((startIndex + i) % executors.length)];

                    if (wokenUp.size() >= canAdd) {
                        break; // We have woken up all the threads we reserved.
                    }
                    if (child instanceof SingleThreadEventExecutor) {
                        SingleThreadEventExecutor stee = (SingleThreadEventExecutor) child;
                        if (stee.isSuspended()) {
                            stee.execute(noOpTask);
                            wokenUp.add(stee);
                        }
                    }
                }

                if (wokenUp.isEmpty()) {
                    return;
                }

                // Create the new state.
                List<EventExecutor> newActiveList = new ArrayList<>(oldState.activeExecutors.length + wokenUp.size());
                newActiveList.addAll(Arrays.asList(oldState.activeExecutors));
                newActiveList.addAll(wokenUp);

                AutoScalingState newState = new AutoScalingState(
                        oldState.activeChildrenCount + wokenUp.size(),
                        startIndex + wokenUp.size(),
                        newActiveList.toArray(new EventExecutor[0]));

                if (state.compareAndSet(oldState, newState)) {
                    return;
                }
                // CAS failed, another thread changed the state. Loop again to retry.
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

                final AutoScalingState currentState = state.get();

                for (EventExecutor child : executors) {
                    if (child.isSuspended() || !(child instanceof SingleThreadEventExecutor)) {
                        continue;
                    }

                    SingleThreadEventExecutor stee = (SingleThreadEventExecutor) child;

                    long activeTime = stee.getAndResetAccumulatedActiveTimeNanos();
                    final long totalTime = utilizationCheckPeriodNanos;

                    if (activeTime == 0) {
                        long lastActivity = stee.getLastActivityTimeNanos();
                        long idleTime = stee.ticker().nanoTime() - lastActivity;

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
                        if (idleCycles >= scalingPatienceCycles && stee.getNumOfRegisteredChannels() <= 0) {
                            consistentlyIdleChildren.add(stee);
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

                boolean changed = false; // Flag to track if we need to rebuild the active executors list.
                int currentActive = currentState.activeChildrenCount;

                // Make scaling decisions based on stable states.
                if (consistentlyBusyChildren > 0 && currentActive < maxChildren) {
                    // Scale Up, we have children that have been busy for multiple cycles.
                    int threadsToAdd = Math.min(consistentlyBusyChildren, maxRampUpStep);
                    threadsToAdd = Math.min(threadsToAdd, maxChildren - currentActive);
                    if (threadsToAdd > 0) {
                        tryScaleUpBy(threadsToAdd);
                        // State change is handled by tryScaleUpBy, no need for rebuild here.
                        return; // Exit to avoid conflicting scale down logic in the same cycle.
                    }
                }

                if (!consistentlyIdleChildren.isEmpty() && currentActive > minChildren) {
                    // Scale down, we have children that have been idle for multiple cycles.

                    int threadsToRemove = Math.min(consistentlyIdleChildren.size(), maxRampDownStep);
                    threadsToRemove = Math.min(threadsToRemove, currentActive - minChildren);

                    for (int i = 0; i < threadsToRemove; i++) {
                        SingleThreadEventExecutor childToSuspend = consistentlyIdleChildren.get(i);
                        if (childToSuspend.trySuspend()) {
                            // Reset cycles upon suspension so it doesn't get immediately re-suspended on wake-up.
                            childToSuspend.resetBusyCycles();
                            childToSuspend.resetIdleCycles();
                            changed = true;
                        }
                    }
                }

                // If a scale-down occurred, or if the actual state differs from our view, rebuild.
                if (changed || currentActive != currentState.activeExecutors.length) {
                    rebuildActiveExecutors();
                }
            }

            /**
             * Atomically updates the state by creating a new snapshot with the current set of active executors.
             */
            private void rebuildActiveExecutors() {
                for (;;) {
                    AutoScalingState oldState = state.get();
                    List<EventExecutor> active = new ArrayList<>(oldState.activeChildrenCount);
                    for (EventExecutor executor : executors) {
                        if (!executor.isSuspended()) {
                            active.add(executor);
                        }
                    }
                    EventExecutor[] newActiveExecutors = active.toArray(new EventExecutor[0]);

                    // If the number of active executors in our scan differs from the count in the state,
                    // another thread likely changed it. We use the count from our fresh scan.
                    // The nextWakeUpIndex is preserved from the old state as this rebuild is not a scale-up action.
                    AutoScalingState newState = new AutoScalingState(
                            newActiveExecutors.length, oldState.nextWakeUpIndex, newActiveExecutors);

                    if (state.compareAndSet(oldState, newState)) {
                        break;
                    }
                }
            }
        }
    }
}
