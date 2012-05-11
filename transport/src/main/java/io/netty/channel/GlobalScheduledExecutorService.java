package io.netty.channel;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A global single-threaded {@link ScheduledExecutorService} which is purposed
 * to trigger scheduled events in {@link SingleThreadEventLoop}.
 */
public final class GlobalScheduledExecutorService implements ScheduledExecutorService {

    private static final GlobalScheduledExecutorService INSTANCE = new GlobalScheduledExecutorService();

    public static final GlobalScheduledExecutorService instance() {
        return INSTANCE;
    }

    private final ThreadFactory threadFactory = Executors.defaultThreadFactory();
    private final ScheduledThreadPoolExecutor timer;

    private GlobalScheduledExecutorService() {
        timer = (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(1, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = threadFactory.newThread(r);
                t.setDaemon(true);
                t.setName(String.format("EventLoopTimer-%08x", GlobalScheduledExecutorService.this.hashCode()));
                return t;
            }
        });

        // Avoid unnecessary memory consumption on a burst of cancellation.
        timer.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                timer.purge();
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    @Override
    protected void finalize() throws Throwable {
        shutdownNow();
        super.finalize();
    }

    @Override
    public void shutdown() {
        timer.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return timer.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return timer.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return timer.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        if (unit == null) {
            throw new NullPointerException("unit");
        }
        return timer.awaitTermination(timeout, unit);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        if (task == null) {
            throw new NullPointerException("task");
        }
        return timer.submit(task);
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        if (task == null) {
            throw new NullPointerException("task");
        }
        return timer.submit(task, result);
    }

    @Override
    public Future<?> submit(Runnable task) {
        if (task == null) {
            throw new NullPointerException("task");
        }

        return timer.submit(task);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        if (tasks == null) {
            throw new NullPointerException("tasks");
        }

        return timer.invokeAll(tasks);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
        if (tasks == null) {
            throw new NullPointerException("tasks");
        }
        if (unit == null) {
            throw new NullPointerException("unit");
        }
        return timer.invokeAll(tasks, timeout, unit);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        if (tasks == null) {
            throw new NullPointerException("tasks");
        }
        return timer.invokeAny(tasks);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        if (tasks == null) {
            throw new NullPointerException("tasks");
        }
        if (unit == null) {
            throw new NullPointerException("unit");
        }
        return timer.invokeAny(tasks, timeout, unit);
    }

    @Override
    public void execute(Runnable command) {
        if (command == null) {
            throw new NullPointerException("command");
        }
        timer.execute(command);
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        if (command == null) {
            throw new NullPointerException("command");
        }
        if (unit == null) {
            throw new NullPointerException("unit");
        }
        return timer.schedule(command, delay, unit);
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        if (callable == null) {
            throw new NullPointerException("callable");
        }
        if (unit == null) {
            throw new NullPointerException("unit");
        }
        return timer.schedule(callable, delay, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        if (command == null) {
            throw new NullPointerException("command");
        }
        if (unit == null) {
            throw new NullPointerException("unit");
        }
        return timer.scheduleAtFixedRate(command, initialDelay, period, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        if (command == null) {
            throw new NullPointerException("command");
        }
        if (unit == null) {
            throw new NullPointerException("unit");
        }
        return timer.scheduleWithFixedDelay(command, initialDelay, delay, unit);
    }
}
