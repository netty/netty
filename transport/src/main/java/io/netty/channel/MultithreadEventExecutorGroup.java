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
package io.netty.channel;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class MultithreadEventExecutorGroup implements EventExecutorGroup {

    private static final int DEFAULT_POOL_SIZE = Runtime.getRuntime().availableProcessors() * 2;
    private static final AtomicInteger poolId = new AtomicInteger();

    final ChannelTaskScheduler scheduler;
    private final EventExecutor[] children;
    private final AtomicInteger childIndex = new AtomicInteger();

    protected MultithreadEventExecutorGroup(int nThreads, ThreadFactory threadFactory, Object... args) {
        if (nThreads < 0) {
            throw new IllegalArgumentException(String.format(
                    "nThreads: %d (expected: >= 0)", nThreads));
        }

        if (nThreads == 0) {
            nThreads = DEFAULT_POOL_SIZE;
        }
        if (threadFactory == null) {
            threadFactory = new DefaultThreadFactory();
        }

        scheduler = new ChannelTaskScheduler(threadFactory);

        children = new SingleThreadEventExecutor[nThreads];
        for (int i = 0; i < nThreads; i ++) {
            boolean success = false;
            try {
                children[i] = newChild(threadFactory, scheduler, args);
                success = true;
            } catch (Exception e) {
                throw new EventLoopException("failed to create a child event loop", e);
            } finally {
                if (!success) {
                    for (int j = 0; j < i; j ++) {
                        children[j].shutdown();
                    }
                }
            }
        }
    }

    @Override
    public EventExecutor next() {
        return children[Math.abs(childIndex.getAndIncrement() % children.length)];
    }

    protected abstract EventExecutor newChild(
            ThreadFactory threadFactory, ChannelTaskScheduler scheduler, Object... args) throws Exception;

    @Override
    public void shutdown() {
        scheduler.shutdown();
        for (EventExecutor l: children) {
            l.shutdown();
        }
    }

    @Override
    public boolean isShutdown() {
        if (!scheduler.isShutdown()) {
            return false;
        }
        for (EventExecutor l: children) {
            if (!l.isShutdown()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isTerminated() {
        if (!scheduler.isTerminated()) {
            return false;
        }
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
        for (;;) {
            long timeLeft = deadline - System.nanoTime();
            if (timeLeft <= 0) {
                return isTerminated();
            }
            if (scheduler.awaitTermination(timeLeft, TimeUnit.NANOSECONDS)) {
                break;
            }
        }
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

    private final class DefaultThreadFactory implements ThreadFactory {
        private final AtomicInteger nextId = new AtomicInteger();
        private final String prefix;

        DefaultThreadFactory() {
            String typeName = MultithreadEventExecutorGroup.this.getClass().getSimpleName();
            typeName = Character.toLowerCase(typeName.charAt(0)) + typeName.substring(1);
            prefix = typeName + '-' + poolId.incrementAndGet() + '-';
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, prefix + nextId.incrementAndGet());
            try {
                if (t.isDaemon()) {
                    t.setDaemon(false);
                }
                if (t.getPriority() != Thread.MAX_PRIORITY) {
                    t.setPriority(Thread.MAX_PRIORITY);
                }
            } catch (Exception ignored) {
                // Doesn't matter even if failed to set.
            }
            return t;
        }
    }
}
