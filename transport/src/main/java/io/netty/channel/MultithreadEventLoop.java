package io.netty.channel;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public class MultithreadEventLoop implements EventLoop {

    private final EventLoop[] children;
    private final AtomicInteger childIndex = new AtomicInteger();

    public MultithreadEventLoop(EventLoopFactory<? extends SingleThreadEventLoop> loopFactory) {
        this(loopFactory, Runtime.getRuntime().availableProcessors() * 2);
    }

    public MultithreadEventLoop(EventLoopFactory<? extends SingleThreadEventLoop> loopFactory, int nThreads) {
        this(loopFactory, nThreads, Executors.defaultThreadFactory());
    }

    public MultithreadEventLoop(EventLoopFactory<? extends SingleThreadEventLoop> loopFactory, int nThreads, ThreadFactory threadFactory) {
        if (loopFactory == null) {
            throw new NullPointerException("loopFactory");
        }
        if (nThreads <= 0) {
            throw new IllegalArgumentException(String.format(
                    "nThreads: %d (expected: > 0)", nThreads));
        }
        if (threadFactory == null) {
            throw new NullPointerException("threadFactory");
        }

        children = new EventLoop[nThreads];
        for (int i = 0; i < nThreads; i ++) {
            boolean success = false;
            try {
                children[i] = loopFactory.newEventLoop(threadFactory);
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
    public void shutdown() {
        for (EventLoop l: children) {
            l.shutdown();
        }
    }

    @Override
    public List<Runnable> shutdownNow() {
        for (EventLoop l: children) {
            l.shutdownNow();
        }
        return Collections.emptyList();
    }

    @Override
    public boolean isShutdown() {
        for (EventLoop l: children) {
            if (!l.isShutdown()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isTerminated() {
        for (EventLoop l: children) {
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
        loop: for (EventLoop l: children) {
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
    public ChannelFuture register(Channel channel) {
        return nextEventLoop().register(channel);
    }

    @Override
    public ChannelFuture register(Channel channel, ChannelFuture future) {
        return nextEventLoop().register(channel, future);
    }

    @Override
    public boolean inEventLoop() {
        return SingleThreadEventLoop.CURRENT_EVENT_LOOP.get() != null;
    }

    private EventLoop nextEventLoop() {
        return children[Math.abs(childIndex.getAndIncrement() % children.length)];
    }

    private static SingleThreadEventLoop currentEventLoop() {
        SingleThreadEventLoop loop = SingleThreadEventLoop.CURRENT_EVENT_LOOP.get();
        if (loop == null) {
            throw new IllegalStateException("not called from an event loop thread");
        }
        return loop;
    }
}
