/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.handler.timeout;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.jboss.netty.util.ExecutorUtil;
import org.jboss.netty.util.MapBackedSet;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
public class HashedWheelTimer implements Timer {

    private final Executor executor;
    private final Worker worker = new Worker();

    final long tickDuration;
    final long wheelDuration;
    final Set<HashedWheelTimeout>[] wheel;
    final int mask;
    final ReadWriteLock lock = new ReentrantReadWriteLock();
    volatile int wheelCursor;

    public HashedWheelTimer(Executor executor) {
        this(executor, 1, TimeUnit.SECONDS, 64);
    }

    public HashedWheelTimer(
            Executor executor,
            long tickDuration, TimeUnit unit, int ticksPerWheel) {

        if (executor == null) {
            throw new NullPointerException("executor");
        }
        if (unit == null) {
            throw new NullPointerException("unit");
        }
        if (tickDuration <= 0) {
            throw new IllegalArgumentException(
                    "tickDuration must be greater than 0: " + tickDuration);
        }

        this.executor = executor;

        // Normalize ticksPerWheel to power of two and initialize the wheel.
        wheel = createWheel(ticksPerWheel);
        mask = wheel.length - 1;

        // Convert checkInterval to nanoseconds.
        this.tickDuration = tickDuration = unit.toNanos(tickDuration);

        // Prevent overflow.
        if (tickDuration == Long.MAX_VALUE ||
                tickDuration >= Long.MAX_VALUE / wheel.length) {
            throw new IllegalArgumentException(
                    "tickDuration is too long: " +
                    tickDuration +  ' ' + unit);
        }

        wheelDuration = tickDuration * wheel.length;
        executor.execute(worker);
    }

    @SuppressWarnings("unchecked")
    private static Set<HashedWheelTimeout>[] createWheel(int ticksPerWheel) {
        if (ticksPerWheel <= 0) {
            throw new IllegalArgumentException(
                    "ticksPerWheel must be greater than 0: " + ticksPerWheel);
        }
        if (ticksPerWheel > 1073741824) {
            throw new IllegalArgumentException(
                    "ticksPerWheel may not be greater than 2^30: " + ticksPerWheel);
        }

        ticksPerWheel = normalizeTicksPerWheel(ticksPerWheel);
        Set<HashedWheelTimeout>[] buckets = new Set[ticksPerWheel];
        for (int i = 0; i < buckets.length; i ++) {
            buckets[i] = new MapBackedSet<HashedWheelTimeout>(new ConcurrentHashMap<HashedWheelTimeout, Boolean>());
        }
        return buckets;
    }

    private static int normalizeTicksPerWheel(int ticksPerWheel) {
        int normalizedTicksPerWheel = 1;
        while (normalizedTicksPerWheel < ticksPerWheel) {
            normalizedTicksPerWheel <<= 1;
        }
        return normalizedTicksPerWheel;
    }

    public void releaseExternalResources() {
        ExecutorUtil.terminate(executor);
    }

    public Timeout newTimeout(TimerTask task, long initialDelay, TimeUnit unit) {
        if (task == null) {
            throw new NullPointerException("task");
        }
        if (unit == null) {
            throw new NullPointerException("unit");
        }

        initialDelay = unit.toNanos(initialDelay);
        checkDelay(initialDelay);

        HashedWheelTimeout timeout;

        lock.readLock().lock();
        try {
            timeout = new HashedWheelTimeout(
                    task, wheelCursor, System.nanoTime(), initialDelay);

            wheel[schedule(timeout)].add(timeout);
        } finally {
            lock.readLock().unlock();
        }

        return timeout;
    }

    private int schedule(HashedWheelTimeout timeout) {
        return schedule(timeout, timeout.initialDelay);
    }

    int schedule(HashedWheelTimeout timeout, final long additionalDelay) {
        synchronized (timeout) {
            final long oldCumulativeDelay = timeout.cumulativeDelay;
            final long newCumulativeDelay = oldCumulativeDelay + additionalDelay;

            final long lastWheelDelay = newCumulativeDelay % wheelDuration;
            final long lastTickDelay = newCumulativeDelay % tickDuration;
            final int relativeIndex =
                (int) (lastWheelDelay / tickDuration) + (lastTickDelay != 0? 1 : 0);

            timeout.deadline = timeout.startTime + newCumulativeDelay;
            timeout.cumulativeDelay = newCumulativeDelay;
            timeout.remainingRounds =
                additionalDelay / wheelDuration -
                (additionalDelay % wheelDuration == 0? 1:0) - timeout.slippedRounds;
            timeout.slippedRounds = 0;

            return timeout.stopIndex = timeout.startIndex + relativeIndex & mask;
        }
    }

    void checkDelay(long delay) {
        if (delay < tickDuration) {
            throw new IllegalArgumentException(
                    "delay must be greater than " +
                    tickDuration + " nanoseconds");
        }
    }

    private final class Worker implements Runnable {

        private long startTime;
        private long tick;

        Worker() {
            super();
        }

        public void run() {
            List<HashedWheelTimeout> expiredTimeouts =
                new ArrayList<HashedWheelTimeout>();

            startTime = System.nanoTime();
            tick = 1;

            for (;;) {
                waitForNextTick();
                fetchExpiredTimeouts(expiredTimeouts);
                notifyExpiredTimeouts(expiredTimeouts);
            }
        }

        private void fetchExpiredTimeouts(
                List<HashedWheelTimeout> expiredTimeouts) {

            // Find the expired timeouts and decrease the round counter
            // if necessary.  Note that we don't send the notification
            // immediately to make sure the listeners are called without
            // an exclusive lock.
            lock.writeLock().lock();
            try {
                long currentTime = System.nanoTime();
                int newBucketHead = wheelCursor + 1 & mask;
                Set<HashedWheelTimeout> bucket = wheel[wheelCursor];
                wheelCursor = newBucketHead;
                for (Iterator<HashedWheelTimeout> i = bucket.iterator(); i.hasNext();) {
                    HashedWheelTimeout timeout = i.next();
                    synchronized (timeout) {
                        if (timeout.remainingRounds <= 0) {
                            if (timeout.deadline <= currentTime) {
                                i.remove();
                                expiredTimeouts.add(timeout);
                            } else {
                                // A rare case where a timeout is put for the next
                                // round: just wait for the next round.
                                timeout.slippedRounds ++;
                            }
                        } else {
                            timeout.remainingRounds --;
                        }
                    }
                }
            } finally {
                lock.writeLock().unlock();
            }
        }

        private void notifyExpiredTimeouts(
                List<HashedWheelTimeout> expiredTimeouts) {
            // Notify the expired timeouts.
            for (int i = expiredTimeouts.size() - 1; i >= 0; i --) {
                expiredTimeouts.get(i).expire();
            }

            // Clean up the temporary list.
            expiredTimeouts.clear();
        }

        private void waitForNextTick() {
            for (;;) {
                final long currentTime = System.nanoTime();
                final long sleepTime = tickDuration * tick - (currentTime - startTime);

                if (sleepTime <= 0) {
                    break;
                }

                try {
                    Thread.sleep(sleepTime / 1000000, (int) (sleepTime % 1000000));
                } catch (InterruptedException e) {
                    // FIXME: must exit the loop if necessary
                }
            }

            // Reset the tick if overflow is expected.
            if (tickDuration * tick > Long.MAX_VALUE - tickDuration) {
                startTime = System.nanoTime();
                tick = 1;
            } else {
                // Increase the tick if overflow is not likely to happen.
                tick ++;
            }
        }
    }

    private final class HashedWheelTimeout implements Timeout {

        private final TimerTask task;

        final int startIndex;
        int stopIndex;

        final long startTime;
        volatile long deadline;

        final long initialDelay;
        long cumulativeDelay;

        long remainingRounds;
        long slippedRounds;

        private volatile int extensionCount;
        private volatile boolean cancelled;

        HashedWheelTimeout(TimerTask task, int startIndex, long startTime, long initialDelay) {
            this.task = task;
            this.startIndex = startIndex;
            this.startTime = startTime;
            this.initialDelay = initialDelay;
        }

        public TimerTask getTask() {
            return task;
        }

        public void cancel() {
            if (cancelled) {
                return;
            }

            synchronized (this) {
                if (!wheel[stopIndex].remove(this)) {
                    return;
                }
            }
        }

        public void extend() {
            extend(initialDelay);
        }

        public void extend(long additionalDelay, TimeUnit unit) {
            extend(unit.toNanos(additionalDelay));
        }

        private void extend(long additionalDelay) {
            checkDelay(additionalDelay);
            if (cancelled) {
                throw new IllegalStateException("cancelled");
            }

            lock.readLock().lock();
            try {
                int newStopIndex;
                synchronized (this) {
                    newStopIndex = stopIndex = schedule(this, additionalDelay);
                }
                wheel[newStopIndex].add(this);
            } finally {
                extensionCount ++;
                lock.readLock().unlock();
            }
        }

        public int getExtensionCount() {
            return extensionCount;
        }

        public boolean isCancelled() {
            return cancelled;
        }

        public boolean isExpired() {
            return cancelled || System.nanoTime() > deadline;
        }

        public void expire() {
            if (cancelled) {
                return;
            }

            try {
                task.run(this);
            } catch (Throwable t) {
                // FIXME log the exception
            }
        }

        @Override
        public String toString() {
            long currentTime = System.nanoTime();
            long age = currentTime - startTime;
            long remaining = deadline - currentTime;

            StringBuilder buf = new StringBuilder(192);
            buf.append(getClass().getSimpleName());
            buf.append('(');

            buf.append("initialDelay: ");
            buf.append(initialDelay / 1000000);
            buf.append(" ms, ");

            buf.append("cumulativeDelay: ");
            buf.append(cumulativeDelay / 1000000);
            buf.append(" ms, ");

            buf.append("started: ");
            buf.append(age / 1000000);
            buf.append(" ms ago, ");

            buf.append("deadline: ");
            if (remaining > 0) {
                buf.append(remaining / 1000000);
                buf.append(" ms later, ");
            } else if (remaining < 0) {
                buf.append(-remaining / 1000000);
                buf.append(" ms ago, ");
            } else {
                buf.append("now, ");
            }

            buf.append("extended: ");
            switch (getExtensionCount()) {
            case 0:
                buf.append("never");
                break;
            case 1:
                buf.append("once");
                break;
            default:
                buf.append(getExtensionCount());
                buf.append(" times");
                break;
            }

            if (isCancelled()) {
                buf.append (", cancelled");
            }

            return buf.append(')').toString();
        }
    }
}
