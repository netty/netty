package io.netty.channel;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class MultithreadEventExecutor implements EventExecutor {

    private static final int DEFAULT_POOL_SIZE = Runtime.getRuntime().availableProcessors() * 2;
    private static final AtomicInteger poolId = new AtomicInteger();

    private final EventExecutor[] children;
    private final AtomicInteger childIndex = new AtomicInteger();
    private final Unsafe unsafe = new Unsafe() {
        @Override
        public EventExecutor nextChild() {
            return children[Math.abs(childIndex.getAndIncrement() % children.length)];
        }
    };

    protected MultithreadEventExecutor(Object... args) {
        this(DEFAULT_POOL_SIZE, args);
    }

    protected MultithreadEventExecutor(int nThreads, Object... args) {
        this(nThreads, null, args);
    }

    protected MultithreadEventExecutor(int nThreads, ThreadFactory threadFactory, Object... args) {
        if (nThreads <= 0) {
            throw new IllegalArgumentException(String.format(
                    "nThreads: %d (expected: > 0)", nThreads));
        }

        if (threadFactory == null) {
            threadFactory = new DefaultThreadFactory();
        }

        children = new SingleThreadEventExecutor[nThreads];
        for (int i = 0; i < nThreads; i ++) {
            boolean success = false;
            try {
                children[i] = newChild(threadFactory, args);
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

    protected abstract EventExecutor newChild(ThreadFactory threadFactory, Object... args) throws Exception;

    @Override
    public Unsafe unsafe() {
        return unsafe;
    }

    @Override
    public void shutdown() {
        for (EventExecutor l: children) {
            l.shutdown();
        }
    }

    @Override
    public List<Runnable> shutdownNow() {
        for (EventExecutor l: children) {
            l.shutdownNow();
        }
        return Collections.emptyList();
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

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return currentEventLoop().submit(task);
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return currentEventLoop().submit(task, result);
    }

    @Override
    public Future<?> submit(Runnable task) {
        return currentEventLoop().submit(task);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
            throws InterruptedException {
        return currentEventLoop().invokeAll(tasks);
    }

    @Override
    public <T> List<Future<T>> invokeAll(
            Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException {
        return currentEventLoop().invokeAll(tasks, timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
            throws InterruptedException, ExecutionException {
        return currentEventLoop().invokeAny(tasks);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks,
            long timeout, TimeUnit unit) throws InterruptedException,
            ExecutionException, TimeoutException {
        return currentEventLoop().invokeAny(tasks, timeout, unit);
    }

    @Override
    public void execute(Runnable command) {
        currentEventLoop().execute(command);
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay,
            TimeUnit unit) {
        return currentEventLoop().schedule(command, delay, unit);
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        return currentEventLoop().schedule(callable, delay, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        return currentEventLoop().scheduleAtFixedRate(command, initialDelay, period, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        return currentEventLoop().scheduleWithFixedDelay(command, initialDelay, delay, unit);
    }

    @Override
    public boolean inEventLoop() {
        return SingleThreadEventExecutor.currentEventLoop() != null;
    }

    private static EventExecutor currentEventLoop() {
        EventExecutor loop = SingleThreadEventExecutor.currentEventLoop();
        if (loop == null) {
            throw new IllegalStateException("not called from an event loop thread");
        }
        return loop;
    }

    private final class DefaultThreadFactory implements ThreadFactory {
        private final AtomicInteger nextId = new AtomicInteger();
        private final String prefix;

        DefaultThreadFactory() {
            String typeName = MultithreadEventExecutor.this.getClass().getSimpleName();
            typeName = "" + Character.toLowerCase(typeName.charAt(0)) + typeName.substring(1);
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
