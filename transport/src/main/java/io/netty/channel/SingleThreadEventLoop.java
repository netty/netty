package io.netty.channel;

import io.netty.util.internal.QueueFactory;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public abstract class SingleThreadEventLoop extends AbstractExecutorService implements EventLoop {

    static final ThreadLocal<SingleThreadEventLoop> CURRENT_EVENT_LOOP = new ThreadLocal<SingleThreadEventLoop>();

    private final BlockingQueue<Runnable> taskQueue = QueueFactory.createQueue(Runnable.class);
    private final Thread thread;
    private final Object stateLock = new Object();
    private final Semaphore threadLock = new Semaphore(0);
    /** 0 - not started, 1 - started, 2 - shut down, 3 - terminated */
    private volatile int state;

    protected SingleThreadEventLoop() {
        this(Executors.defaultThreadFactory());
    }

    protected SingleThreadEventLoop(ThreadFactory threadFactory) {
        thread = threadFactory.newThread(new Runnable() {
            @Override
            public void run() {
                CURRENT_EVENT_LOOP.set(SingleThreadEventLoop.this);
                try {
                    SingleThreadEventLoop.this.run();
                } finally {
                    synchronized (stateLock) {
                        state = 3;
                    }
                    try {
                        cleanup();
                    } finally {
                        threadLock.release();
                        assert taskQueue.isEmpty();
                    }
                }
            }
        });
    }

    @Override
    public ChannelFuture register(Channel channel) {
        ChannelFuture future = channel.newFuture();
        register(channel, future);
        return future;
    }

    @Override
    public ChannelFuture register(final Channel channel, final ChannelFuture future) {
        if (inEventLoop()) {
            channel.unsafe().register(this, future);
        } else {
            execute(new Runnable() {
                @Override
                public void run() {
                    channel.unsafe().register(SingleThreadEventLoop.this, future);
                }
            });
        }
        return future;
    }

    protected void interruptThread() {
        thread.interrupt();
    }

    protected Runnable pollTask() {
        assert inEventLoop();
        return taskQueue.poll();
    }

    protected Runnable takeTask() throws InterruptedException {
        assert inEventLoop();
        return taskQueue.take();
    }

    protected Runnable peekTask() {
        assert inEventLoop();
        return taskQueue.peek();
    }

    protected boolean hasTasks() {
        assert inEventLoop();
        return !taskQueue.isEmpty();
    }

    protected void addTask(Runnable task) {
        if (task == null) {
            throw new NullPointerException("task");
        }
        if (isShutdown()) {
            reject();
        }
        taskQueue.add(task);
    }

    protected boolean removeTask(Runnable task) {
        if (task == null) {
            throw new NullPointerException("task");
        }
        return taskQueue.remove(task);
    }

    protected abstract void run();

    protected void cleanup() {
        // Do nothing. Subclasses will override.
    }

    protected abstract void wakeup(boolean inEventLoop);

    @Override
    public boolean inEventLoop() {
        return Thread.currentThread() == thread;
    }

    @Override
    public void shutdown() {
        boolean inEventLoop = inEventLoop();
        boolean wakeup = false;
        if (inEventLoop) {
            synchronized (stateLock) {
                assert state == 1;
                state = 2;
                wakeup = true;
            }
        } else {
            synchronized (stateLock) {
                switch (state) {
                case 0:
                    state = 3;
                    try {
                        cleanup();
                    } finally {
                        threadLock.release();
                    }
                    break;
                case 1:
                    state = 2;
                    wakeup = true;
                    break;
                }
            }
        }

        if (wakeup) {
            wakeup(inEventLoop);
        }
    }

    @Override
    public List<Runnable> shutdownNow() {
        shutdown();
        return Collections.emptyList();
    }

    @Override
    public boolean isShutdown() {
        return state >= 2;
    }

    @Override
    public boolean isTerminated() {
        return state == 3;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        if (unit == null) {
            throw new NullPointerException("unit");
        }

        if (inEventLoop()) {
            throw new IllegalStateException("cannot await termination of the current thread");
        }

        if (threadLock.tryAcquire(timeout, unit)) {
            threadLock.release();
        }

        return isTerminated();
    }

    @Override
    public void execute(Runnable task) {
        if (task == null) {
            throw new NullPointerException("task");
        }

        if (inEventLoop()) {
            if (isShutdown()) {
                reject();
            }
            addTask(task);
            wakeup(true);
        } else {
            synchronized (stateLock) {
                if (state == 0) {
                    state = 1;
                    thread.start();
                }
            }
            addTask(task);
            if (isShutdown() && removeTask(task)) {
                reject();
            }
            wakeup(false);
        }
    }

    private static void reject() {
        throw new RejectedExecutionException("event loop shut down");
    }
}
