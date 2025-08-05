/*
 * Copyright 2012 The Netty Project
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
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Abstract base class for {@link EventExecutorGroup} implementations that handles their tasks with multiple threads at
 * the same time.
 */
public abstract class MultithreadEventExecutorGroup extends AbstractEventExecutorGroup {

    private static final Runnable NOOP_TASK = () -> { /* NOOP */ };
    private final EventExecutor[] children;
    private final AtomicInteger nextWakeUpIndex = new AtomicInteger();
    private final Set<EventExecutor> readonlyChildren;
    private final AtomicInteger terminatedChildren = new AtomicInteger();
    private final Promise<?> terminationFuture = new DefaultPromise(GlobalEventExecutor.INSTANCE);
    private final EventExecutorChooserFactory.EventExecutorChooser chooser;

    // Fields for dynamic utilization-based auto-scaling
    private final int minChildren;
    private final int maxChildren;
    private final long utilizationCheckPeriodNanos;
    private final double scaleDownThreshold;
    private final double scaleUpThreshold;
    private final int maxRampUpStep;
    private final int maxRampDownStep;
    private final int scalingPatienceCycles;
    private final AtomicInteger activeChildrenCount;
    private final ScheduledFuture<?> utilizationMonitoringTask;

    /**
     * Create a new instance.
     *
     * @param nThreads          the number of threads that will be used by this instance.
     * @param threadFactory     the ThreadFactory to use, or {@code null} if the default should be used.
     * @param args              arguments which will passed to each {@link #newChild(Executor, Object...)} call
     */
    protected MultithreadEventExecutorGroup(int nThreads, ThreadFactory threadFactory, Object... args) {
        this(nThreads, threadFactory == null ? null : new ThreadPerTaskExecutor(threadFactory), args);
    }

    /**
     * Create a new instance.
     *
     * @param nThreads          the number of threads that will be used by this instance.
     * @param executor          the Executor to use, or {@code null} if the default should be used.
     * @param args              arguments which will passed to each {@link #newChild(Executor, Object...)} call
     */
    protected MultithreadEventExecutorGroup(int nThreads, Executor executor, Object... args) {
        this(nThreads, executor, DefaultEventExecutorChooserFactory.INSTANCE, args);
    }

    /**
     * Create a new instance.
     *
     * @param nThreads          the number of threads that will be used by this instance.
     * @param executor          the Executor to use, or {@code null} if the default should be used.
     * @param chooserFactory    the {@link EventExecutorChooserFactory} to use.
     * @param args              arguments which will passed to each {@link #newChild(Executor, Object...)} call
     */
    protected MultithreadEventExecutorGroup(int nThreads, Executor executor,
                                            EventExecutorChooserFactory chooserFactory, Object... args) {
        this(nThreads, nThreads, 0, TimeUnit.SECONDS, 0, 0, 0, 0, 0, executor, chooserFactory, args);
    }

    /**
     * Create a new instance with optional dynamic utilization-based scaling capabilities.
     *
     * @param minThreads                  the minimum number of threads to keep active.
     * @param maxThreads                  the maximum number of threads to scale up to.
     * @param utilizationWindow           the period at which to check group utilization. A value of 0 disables scaling.
     * @param windowUnit                  the unit for {@code utilizationWindow}.
     * @param scaleDownThreshold          the average utilization below which a thread may be suspended (0.0 to 1.0).
     * @param scaleUpThreshold            the average utilization above which a thread may be resumed (0.0 to 1.0).
     * @param maxRampUpStep               the maximum number of threads to add in one cycle.
     * @param maxRampDownStep             the maximum number of threads to remove in one cycle.
     * @param scalingPatienceCycles       the number of consecutive cycles a condition must be met before scaling.
     * @param executor                    the Executor to use, or {@code null} if the default should be used.
     * @param chooserFactory              the {@link EventExecutorChooserFactory} to use.
     * @param args                        arguments which will be passed to each {@link #newChild(Executor, Object...)}
     *                                    call
     */
    protected MultithreadEventExecutorGroup(int minThreads, int maxThreads, long utilizationWindow, TimeUnit windowUnit,
                                            double scaleDownThreshold, double scaleUpThreshold, int maxRampUpStep,
                                            int maxRampDownStep, int scalingPatienceCycles, Executor executor,
                                            EventExecutorChooserFactory chooserFactory, Object... args) {
        if (minThreads < 0 || minThreads > maxThreads || maxThreads == 0) {
            throw new IllegalArgumentException(String.format(
                    "minThreads: %d, maxThreads: %d (expected: 0 <= minThreads <= maxThreads, maxThreads > 0)",
                    minThreads, maxThreads));
        }
        if (utilizationWindow < 0) {
            throw new IllegalArgumentException("utilizationWindow must be >= 0: " + utilizationWindow);
        }
        if (windowUnit == null && utilizationWindow > 0) {
            throw new NullPointerException("windowUnit");
        }
        boolean scalingEnabled = utilizationWindow > 0 && minThreads < maxThreads;
        if (scalingEnabled) {
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
                        "scaleDownThreshold must be less than scaleUpThreshold when scaling is enabled: " +
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
        }

        if (executor == null) {
            executor = new ThreadPerTaskExecutor(newDefaultThreadFactory());
        }

        this.minChildren = minThreads;
        this.maxChildren = maxThreads;
        this.utilizationCheckPeriodNanos = windowUnit == null ? 0 : windowUnit.toNanos(utilizationWindow);
        this.scaleDownThreshold = scaleDownThreshold;
        this.scaleUpThreshold = scaleUpThreshold;
        this.maxRampUpStep = maxRampUpStep;
        this.maxRampDownStep = maxRampDownStep;
        this.scalingPatienceCycles = scalingPatienceCycles;

        children = new EventExecutor[maxThreads];

        for (int i = 0; i < maxThreads; i ++) {
            boolean success = false;
            try {
                children[i] = newChild(executor, args);
                success = true;
            } catch (Exception e) {
                // TODO: Think about if this is a good exception type
                throw new IllegalStateException("failed to create a child event loop", e);
            } finally {
                if (!success) {
                    for (int j = 0; j < i; j ++) {
                        children[j].shutdownGracefully();
                    }

                    for (int j = 0; j < i; j ++) {
                        EventExecutor e = children[j];
                        try {
                            while (!e.isTerminated()) {
                                e.awaitTermination(Integer.MAX_VALUE, TimeUnit.SECONDS);
                            }
                        } catch (InterruptedException interrupted) {
                            // Let the caller handle the interruption.
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
        }

        if (utilizationCheckPeriodNanos > 0 && minChildren < maxChildren) {
            // Autoscaling is enabled, schedule the monitor.
            this.activeChildrenCount = new AtomicInteger(maxThreads);
            utilizationMonitoringTask = GlobalEventExecutor.INSTANCE.scheduleAtFixedRate(
                    new UtilizationMonitor(), this.utilizationCheckPeriodNanos, this.utilizationCheckPeriodNanos,
                    TimeUnit.NANOSECONDS);
            // The monitor will handle the initial suspension
        } else {
            // Scaling is disabled.
            this.activeChildrenCount = null;
            this.utilizationMonitoringTask = null;
        }

        chooser = chooserFactory.newChooser(children);

        final FutureListener<Object> terminationListener = new FutureListener<Object>() {
            @Override
            public void operationComplete(Future<Object> future) throws Exception {
                if (terminatedChildren.incrementAndGet() == children.length) {
                    terminationFuture.setSuccess(null);
                }
            }
        };

        for (EventExecutor e: children) {
            e.terminationFuture().addListener(terminationListener);
        }

        Set<EventExecutor> childrenSet = new LinkedHashSet<EventExecutor>(children.length);
        Collections.addAll(childrenSet, children);
        readonlyChildren = Collections.unmodifiableSet(childrenSet);
    }

    protected ThreadFactory newDefaultThreadFactory() {
        return new DefaultThreadFactory(getClass());
    }

    @Override
    public EventExecutor next() {
        if (activeChildrenCount != null) {
            // If we are operating below maximum capacity, try to scale up by one.
            // This handles on-demand scaling.
            if (activeChildrenCount.get() < maxChildren) {
                tryScaleUpBy(1);
            }
        }
        return chooser.next();
    }

    @Override
    public Iterator<EventExecutor> iterator() {
        return readonlyChildren.iterator();
    }

    /**
     * Return the number of {@link EventExecutor} this implementation uses. This number is the maps
     * 1:1 to the threads it use.
     */
    public final int executorCount() {
        return children.length;
    }

    /**
     * Create a new EventExecutor which will later then accessible via the {@link #next()}  method. This method will be
     * called for each thread that will serve this {@link MultithreadEventExecutorGroup}.
     *
     */
    protected abstract EventExecutor newChild(Executor executor, Object... args) throws Exception;

    @Override
    public Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
        if (utilizationMonitoringTask != null) {
            utilizationMonitoringTask.cancel(false);
        }
        for (EventExecutor l: children) {
            l.shutdownGracefully(quietPeriod, timeout, unit);
        }
        return terminationFuture();
    }

    @Override
    public Future<?> terminationFuture() {
        return terminationFuture;
    }

    @Override
    @Deprecated
    public void shutdown() {
        for (EventExecutor l: children) {
            l.shutdown();
        }
    }

    @Override
    public boolean isShuttingDown() {
        for (EventExecutor l: children) {
            if (!l.isShuttingDown()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isShutdown() {
        for (EventExecutor l: children) {
            if (!l.isShutdown()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isTerminated() {
        for (EventExecutor l: children) {
            if (!l.isTerminated()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit)
            throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        loop: for (EventExecutor l: children) {
            for (;;) {
                long timeLeft = deadline - System.nanoTime();
                if (timeLeft <= 0) {
                    break loop;
                }
                if (l.awaitTermination(timeLeft, TimeUnit.NANOSECONDS)) {
                    break;
                }
            }
        }
        return isTerminated();
    }

    /**
     * Tries to scale up by a given amount, respecting the maxChildren limit.
     * This method is thread-safe and reliably wakes up suspended threads.
     *
     * @param amount The desired number of threads to add.
     * @return The number of threads that were actually activated.
     */
    private int tryScaleUpBy(int amount) {
        if (amount <= 0 || activeChildrenCount == null) {
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

                for (int i = 0; i < children.length; i++) {
                    EventExecutor child = children[(startIndex + i) % children.length];

                    if (wokenUp >= canAdd) {
                        break; // We have woken up all the threads we reserved.
                    }
                    if (child instanceof SingleThreadEventExecutor) {
                        SingleThreadEventExecutor stee = (SingleThreadEventExecutor) child;
                        if (stee.isSuspended()) {
                            stee.execute(NOOP_TASK);
                            wokenUp++;
                        }
                    }
                }

                // We reserved 'canAdd' slots but found fewer suspended threads to wake up (in a lost race condition).
                // Release the unused slots.
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
            if (isShuttingDown() || isShutdown() || isTerminated()) {
                if (utilizationMonitoringTask != null) {
                    utilizationMonitoringTask.cancel(false);
                }
                return;
            }

            int consistentlyBusyChildren = 0;
            consistentlyIdleChildren.clear();

            for (EventExecutor child : children) {
                if (child.isSuspended() || !(child instanceof SingleThreadEventExecutor)) {
                    continue;
                }

                SingleThreadEventExecutor stee = (SingleThreadEventExecutor) child;

                long activeTime = stee.getAndResetAccumulatedActiveTimeNanos();
                final long totalTime = utilizationCheckPeriodNanos;

                if (activeTime == 0) {
                    long lastActivity = stee.getLastActivityTimeNanos();
                    long idleTime = ticker().nanoTime() - lastActivity;

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

