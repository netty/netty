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

import io.netty.util.internal.ObjectUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A factory that creates auto-scaling {@link EventExecutorChooser} instances.
 * This chooser implements a dynamic, utilization-based auto-scaling strategy.
 * <p>
 * It enables the {@link io.netty.channel.EventLoopGroup} to automatically scale the number of active
 * {@link io.netty.channel.EventLoop} threads between a minimum and maximum threshold.
 * The scaling decision is based on the average utilization of the active threads, measured over a
 * configurable time window.
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

    /**
     * A container for the utilization metric of a single EventExecutor.
     * This object is intended to be created once and have its {@code utilization}
     * field updated periodically.
     */
    public static final class AutoScalingUtilizationMetric {
        private final EventExecutor executor;
        private final AtomicLong utilizationBits = new AtomicLong();

        AutoScalingUtilizationMetric(EventExecutor executor) {
            this.executor = executor;
        }

        /**
         * Returns the most recently calculated utilization for the associated executor.
         * @return a value from 0.0 to 1.0.
         */
        public double utilization() {
            return Double.longBitsToDouble(utilizationBits.get());
        }

        /**
         * Returns the {@link EventExecutor} this metric belongs too.
         * @return the executor.
         */
        public EventExecutor executor() {
            return executor;
        }

        void setUtilization(double utilization) {
            long bits = Double.doubleToRawLongBits(utilization);
            utilizationBits.lazySet(bits);
        }
    }

    private static final Runnable NO_OOP_TASK = () -> { };
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
        minChildren = ObjectUtil.checkPositiveOrZero(minThreads, "minThreads");
        maxChildren = ObjectUtil.checkPositive(maxThreads, "maxThreads");
        if (minThreads > maxThreads) {
            throw new IllegalArgumentException(String.format(
                    "minThreads: %d must not be greater than maxThreads: %d", minThreads, maxThreads));
        }
        utilizationCheckPeriodNanos = ObjectUtil.checkNotNull(windowUnit, "windowUnit")
                                                     .toNanos(ObjectUtil.checkPositive(utilizationWindow,
                                                                                       "utilizationWindow"));
        this.scaleDownThreshold = ObjectUtil.checkInRange(scaleDownThreshold, 0.0, 1.0, "scaleDownThreshold");
        this.scaleUpThreshold = ObjectUtil.checkInRange(scaleUpThreshold, 0.0, 1.0, "scaleUpThreshold");
        if (scaleDownThreshold >= scaleUpThreshold) {
            throw new IllegalArgumentException(
                    "scaleDownThreshold must be less than scaleUpThreshold: " +
                    scaleDownThreshold + " >= " + scaleUpThreshold);
        }
        this.maxRampUpStep = ObjectUtil.checkPositive(maxRampUpStep, "maxRampUpStep");
        this.maxRampDownStep = ObjectUtil.checkPositive(maxRampDownStep, "maxRampDownStep");
        this.scalingPatienceCycles = ObjectUtil.checkPositiveOrZero(scalingPatienceCycles, "scalingPatienceCycles");
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
        Set<SingleThreadEventExecutor> pausedExecutors;

        AutoScalingState(int activeChildrenCount, long nextWakeUpIndex, EventExecutor[] activeExecutors) {
            this(activeChildrenCount, nextWakeUpIndex, activeExecutors, ConcurrentHashMap.newKeySet());
        }

        AutoScalingState(int activeChildrenCount, long nextWakeUpIndex, EventExecutor[] activeExecutors,
                Set<SingleThreadEventExecutor> pausedExecutors) {
            this.activeChildrenCount = activeChildrenCount;
            this.nextWakeUpIndex = nextWakeUpIndex;
            this.activeExecutors = activeExecutors;
            activeExecutorsChooser = DefaultEventExecutorChooserFactory.INSTANCE.newChooser(activeExecutors);
            this.pausedExecutors = pausedExecutors;
        }
    }

    private final class AutoScalingEventExecutorChooser implements ObservableEventExecutorChooser {
        private final EventExecutor[] executors;
        private final EventExecutorChooser allExecutorsChooser;
        private final AtomicReference<AutoScalingState> state;
        private final List<AutoScalingUtilizationMetric> utilizationMetrics;

        AutoScalingEventExecutorChooser(EventExecutor[] executors) {
            this.executors = executors;
            List<AutoScalingUtilizationMetric> metrics = new ArrayList<>(executors.length);
            for (EventExecutor executor : executors) {
                metrics.add(new AutoScalingUtilizationMetric(executor));
            }
            utilizationMetrics = Collections.unmodifiableList(metrics);
            allExecutorsChooser = DefaultEventExecutorChooserFactory.INSTANCE.newChooser(executors);

            AutoScalingState initialState = new AutoScalingState(maxChildren, 0L, executors);
            state = new AtomicReference<>(initialState);

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
                EventExecutor executor = allExecutorsChooser.next();
                this.state.get().pausedExecutors.remove(executor);
                if (executor instanceof SingleThreadEventExecutor) {
                    SingleThreadEventExecutor stee = (SingleThreadEventExecutor) executor;
                    stee.resetIdleCycles();
                    stee.resetBusyCycles();
                }
                return executor;
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
                            stee.execute(NO_OOP_TASK);
                            wokenUp.add(stee);
                        } else if (oldState.pausedExecutors.remove(stee)) {
                            // Remove from the paused list so that it can accept connections once again
                            wokenUp.add(stee);
                        }
                    }
                }

                if (wokenUp.isEmpty()) {
                    return;
                }

                // Create the new state.
                List<EventExecutor> newActiveList = new ArrayList<>(oldState.activeExecutors.length + wokenUp.size());
                Collections.addAll(newActiveList, oldState.activeExecutors);
                newActiveList.addAll(wokenUp);

                AutoScalingState newState = new AutoScalingState(
                        oldState.activeChildrenCount + wokenUp.size(),
                        startIndex + wokenUp.size(),
                        newActiveList.toArray(new EventExecutor[0]),
                        oldState.pausedExecutors);

                if (state.compareAndSet(oldState, newState)) {
                    return;
                }
                // CAS failed, another thread changed the state. Loop again to retry.
            }
        }

        @Override
        public int activeExecutorCount() {
            return state.get().activeChildrenCount;
        }

        @Override
        public List<AutoScalingUtilizationMetric> executorUtilizations() {
            return utilizationMetrics;
        }

        private final class UtilizationMonitor implements Runnable {
            private final List<SingleThreadEventExecutor> consistentlyIdleChildren = new ArrayList<>(maxChildren);
            private final List<SingleThreadEventExecutor> drainingIdleChildren = new ArrayList<>(maxChildren);
            private long lastCheckTimeNanos;

            @Override
            public void run() {
                if (executors.length == 0 || executors[0].isShuttingDown()) {
                    // The group is shutting down, so no scaling decisions should be made.
                    // The lifecycle listener on the terminationFuture will handle the final cancellation.
                    return;
                }

                // Calculate the actual elapsed time since the last run.
                final long now = executors[0].ticker().nanoTime();
                long totalTime;

                if (lastCheckTimeNanos == 0) {
                    // On the first run, use the configured period as a baseline to avoid skipping the cycle.
                    totalTime = utilizationCheckPeriodNanos;
                } else {
                    // On subsequent runs, calculate the actual elapsed time.
                    totalTime = now - lastCheckTimeNanos;
                }

                // Always update the timestamp for the next cycle.
                lastCheckTimeNanos = now;

                if (totalTime <= 0) {
                    // Skip this cycle if the clock has issues or the interval is invalid.
                    return;
                }

                int consistentlyBusyChildren = 0;
                consistentlyIdleChildren.clear();
                drainingIdleChildren.clear();

                final AutoScalingState currentState = state.get();
                // Attempt to suspend current paused children if possible.
                for (SingleThreadEventExecutor pausedChild : currentState.pausedExecutors) {
                    if (pausedChild.getNumOfRegisteredChannels() <= 0 && pausedChild.trySuspend()) {
                        currentState.pausedExecutors.remove(pausedChild);
                    }
                }

                for (int i = 0; i < executors.length; i++) {
                    EventExecutor child = executors[i];
                    if (!(child instanceof SingleThreadEventExecutor)) {
                        continue;
                    }

                    SingleThreadEventExecutor eventExecutor = (SingleThreadEventExecutor) child;

                    double utilization = 0.0;
                    if (!eventExecutor.isSuspended()) {
                        long activeTime = eventExecutor.getAndResetAccumulatedActiveTimeNanos();

                        if (activeTime == 0) {
                            long lastActivity = eventExecutor.getLastActivityTimeNanos();
                            long idleTime = now - lastActivity;

                            // If the event loop has been idle for less time than our utilization window,
                            // it means it was active for the remainder of that window.
                            if (idleTime < totalTime) {
                                activeTime = totalTime - idleTime;
                            }
                            // If idleTime >= totalTime, it was idle for the whole window, so activeTime remains 0.
                        }

                        utilization = Math.min(1.0, (double) activeTime / totalTime);

                        if (utilization < scaleDownThreshold) {
                            // Utilization is low, increment idle counter and reset busy counter.
                            int idleCycles = eventExecutor.getAndIncrementIdleCycles();
                            eventExecutor.resetBusyCycles();
                            if (idleCycles >= scalingPatienceCycles &&
                                    !currentState.pausedExecutors.contains(eventExecutor)) {
                                if (eventExecutor.getNumOfRegisteredChannels() <= 0) {
                                    consistentlyIdleChildren.add(eventExecutor);
                                } else {
                                    // Stop accepting new connections to this executor while it finishes for suspension.
                                    drainingIdleChildren.add(eventExecutor);
                                }
                            }
                        } else if (utilization > scaleUpThreshold) {
                            // Utilization is high, increment busy counter and reset idle counter.
                            int busyCycles = eventExecutor.getAndIncrementBusyCycles();
                            eventExecutor.resetIdleCycles();
                            if (busyCycles >= scalingPatienceCycles) {
                                consistentlyBusyChildren++;
                            }
                        } else {
                            // Utilization is in the normal range, reset counters.
                            eventExecutor.resetIdleCycles();
                            eventExecutor.resetBusyCycles();
                        }
                    }

                    utilizationMetrics.get(i).setUtilization(utilization);
                }

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

                boolean changed = false; // Flag to track if we need to rebuild the active executors list.
                if (currentActive > minChildren &&
                        (!consistentlyIdleChildren.isEmpty() || !drainingIdleChildren.isEmpty())) {
                    int discontinuedThreads = 0;
                    // First attempt to suspend consistently idle children if any which takes priority
                    if (!consistentlyIdleChildren.isEmpty()) {
                        // Scale down, we have children that have been idle for multiple cycles.
                        int threadsToSuspend = Math.min(consistentlyIdleChildren.size(), maxRampDownStep);
                        threadsToSuspend = Math.min(threadsToSuspend, currentActive - minChildren);

                        for (; discontinuedThreads < threadsToSuspend; discontinuedThreads++) {
                            SingleThreadEventExecutor idleChild = consistentlyIdleChildren.get(discontinuedThreads);
                            if (idleChild.trySuspend()) {
                                // Reset cycles upon suspension so it doesn't get immediately re-suspended on wake-up.
                                idleChild.resetBusyCycles();
                                idleChild.resetIdleCycles();
                            } else {
                                // If failed to suspend, we add it to the paused children to later be able to suspend it
                                currentState.pausedExecutors.add(idleChild);
                            }
                            changed = true;
                        }
                    }
                    // If we have extra threads to remove then we can pause the executors that are idle
                    // but still working from the drainingIdleChildren set
                    if (!drainingIdleChildren.isEmpty() && discontinuedThreads < maxRampDownStep) {
                        // Pause when we have children that have been idle but still managing connections
                        int threadsToPause = Math.min(drainingIdleChildren.size(),
                                maxRampDownStep - discontinuedThreads);
                        threadsToPause = Math.min(threadsToPause, currentActive - minChildren - discontinuedThreads);
                        if (threadsToPause > 0) {
                            int threadsPaused = 0;
                            // Iterate children to drain and add it to the list of paused children
                            while (threadsPaused < threadsToPause) {
                                currentState.pausedExecutors.add(drainingIdleChildren.get(threadsPaused++));
                            }
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
                        if (!executor.isSuspended() && !oldState.pausedExecutors.contains(executor)) {
                            active.add(executor);
                        }
                    }
                    EventExecutor[] newActiveExecutors = active.toArray(new EventExecutor[0]);

                    // If the number of active executors in our scan differs from the count in the state,
                    // another thread likely changed it. We use the count from our fresh scan.
                    // The nextWakeUpIndex is preserved from the old state as this rebuild is not a scale-up action.
                    AutoScalingState newState = new AutoScalingState(newActiveExecutors.length,
                            oldState.nextWakeUpIndex, newActiveExecutors, oldState.pausedExecutors);

                    if (state.compareAndSet(oldState, newState)) {
                        break;
                    }
                }
            }
        }
    }
}
