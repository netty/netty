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

import io.netty.channel.ChannelFlushFutureNotifier.FlushCheckpoint;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;

import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.util.concurrent.TimeUnit.*;

/**
 * The default {@link ChannelFuture} implementation.  It is recommended to
 * use {@link Channels#future(Channel)} and {@link Channels#future(Channel, boolean)}
 * to create a new {@link ChannelFuture} rather than calling the constructor
 * explicitly.
 */
public class DefaultChannelFuture extends FlushCheckpoint implements ChannelFuture {

    private static final InternalLogger logger =
        InternalLoggerFactory.getInstance(DefaultChannelFuture.class);

    private static final int MAX_LISTENER_STACK_DEPTH = 8;
    private static final ThreadLocal<Integer> LISTENER_STACK_DEPTH = new ThreadLocal<Integer>() {
        @Override
        protected Integer initialValue() {
            return 0;
        }
    };

    private static final Throwable CANCELLED = new Throwable();

    private final Channel channel;
    private final boolean cancellable;

    private ChannelFutureListener firstListener;
    private List<ChannelFutureListener> otherListeners;
    private List<ChannelFutureProgressListener> progressListeners;
    private boolean done;
    private Throwable cause;
    private int waiters;

    /**
     * Opportunistically extending FlushCheckpoint to reduce GC.
     * Only used for flush() operation. See AbstractChannel.DefaultUnsafe.flush() */
    private long flushCheckpoint;

    /**
     * Creates a new instance.
     *
     * @param channel
     *        the {@link Channel} associated with this future
     * @param cancellable
     *        {@code true} if and only if this future can be canceled
     */
    public DefaultChannelFuture(Channel channel, boolean cancellable) {
        this.channel = channel;
        this.cancellable = cancellable;
    }

    @Override
    public Channel channel() {
        return channel;
    }

    @Override
    public synchronized boolean isDone() {
        return done;
    }

    @Override
    public synchronized boolean isSuccess() {
        return done && cause == null;
    }

    @Override
    public synchronized Throwable cause() {
        if (cause != CANCELLED) {
            return cause;
        } else {
            return null;
        }
    }

    @Override
    public synchronized boolean isCancelled() {
        return cause == CANCELLED;
    }

    @Override
    public ChannelFuture addListener(final ChannelFutureListener listener) {
        if (listener == null) {
            throw new NullPointerException("listener");
        }

        boolean notifyNow = false;
        synchronized (this) {
            if (done) {
                notifyNow = true;
            } else {
                if (firstListener == null) {
                    firstListener = listener;
                } else {
                    if (otherListeners == null) {
                        otherListeners = new ArrayList<ChannelFutureListener>(1);
                    }
                    otherListeners.add(listener);
                }

                if (listener instanceof ChannelFutureProgressListener) {
                    if (progressListeners == null) {
                        progressListeners = new ArrayList<ChannelFutureProgressListener>(1);
                    }
                    progressListeners.add((ChannelFutureProgressListener) listener);
                }
            }
        }

        if (notifyNow) {
            notifyListener(this, listener);
        }

        return this;
    }

    @Override
    public ChannelFuture removeListener(ChannelFutureListener listener) {
        if (listener == null) {
            throw new NullPointerException("listener");
        }

        synchronized (this) {
            if (!done) {
                if (listener == firstListener) {
                    if (otherListeners != null && !otherListeners.isEmpty()) {
                        firstListener = otherListeners.remove(0);
                    } else {
                        firstListener = null;
                    }
                } else if (otherListeners != null) {
                    otherListeners.remove(listener);
                }

                if (listener instanceof ChannelFutureProgressListener) {
                    progressListeners.remove(listener);
                }
            }
        }

        return this;
    }

    @Override
    public ChannelFuture sync() throws InterruptedException {
        await();
        rethrowIfFailed();
        return this;
    }

    @Override
    public ChannelFuture syncUninterruptibly() {
        awaitUninterruptibly();
        rethrowIfFailed();
        return this;
    }

    @Override
    public Void get() throws InterruptedException, ExecutionException {
        await();
        Throwable cause = cause();
        if (cause == null) {
            return null;
        } else {
            throw new ExecutionException(cause);
        }
    }

    @Override
    public Void get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException,
            TimeoutException {
        if (!await(timeout, unit)) {
            throw new TimeoutException();
        }

        Throwable cause = cause();
        if (cause == null) {
            return null;
        } else {
            throw new ExecutionException(cause);
        }
    }

    private void rethrowIfFailed() {
        Throwable cause = cause();
        if (cause == null) {
            return;
        }

        if (cause instanceof RuntimeException) {
            throw (RuntimeException) cause;
        }

        if (cause instanceof Error) {
            throw (Error) cause;
        }

        throw new ChannelException(cause);
    }

    @Override
    public ChannelFuture await() throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }

        synchronized (this) {
            while (!done) {
                checkDeadLock();
                waiters++;
                try {
                    wait();
                } finally {
                    waiters--;
                }
            }
        }
        return this;
    }

    @Override
    public boolean await(long timeout, TimeUnit unit)
            throws InterruptedException {
        return await0(unit.toNanos(timeout), true);
    }

    @Override
    public boolean await(long timeoutMillis) throws InterruptedException {
        return await0(MILLISECONDS.toNanos(timeoutMillis), true);
    }

    @Override
    public ChannelFuture awaitUninterruptibly() {
        boolean interrupted = false;
        synchronized (this) {
            while (!done) {
                checkDeadLock();
                waiters++;
                try {
                    wait();
                } catch (InterruptedException e) {
                    interrupted = true;
                } finally {
                    waiters--;
                }
            }
        }

        if (interrupted) {
            Thread.currentThread().interrupt();
        }

        return this;
    }

    @Override
    public boolean awaitUninterruptibly(long timeout, TimeUnit unit) {
        try {
            return await0(unit.toNanos(timeout), false);
        } catch (InterruptedException e) {
            throw new InternalError();
        }
    }

    @Override
    public boolean awaitUninterruptibly(long timeoutMillis) {
        try {
            return await0(MILLISECONDS.toNanos(timeoutMillis), false);
        } catch (InterruptedException e) {
            throw new InternalError();
        }
    }

    private boolean await0(long timeoutNanos, boolean interruptable) throws InterruptedException {
        if (interruptable && Thread.interrupted()) {
            throw new InterruptedException();
        }

        long startTime = timeoutNanos <= 0 ? 0 : System.nanoTime();
        long waitTime = timeoutNanos;
        boolean interrupted = false;

        try {
            synchronized (this) {
                if (done) {
                    return done;
                } else if (waitTime <= 0) {
                    return done;
                }

                checkDeadLock();
                waiters++;
                try {
                    for (;;) {
                        try {
                            wait(waitTime / 1000000, (int) (waitTime % 1000000));
                        } catch (InterruptedException e) {
                            if (interruptable) {
                                throw e;
                            } else {
                                interrupted = true;
                            }
                        }

                        if (done) {
                            return true;
                        } else {
                            waitTime = timeoutNanos - (System.nanoTime() - startTime);
                            if (waitTime <= 0) {
                                return done;
                            }
                        }
                    }
                } finally {
                    waiters--;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void checkDeadLock() {
        if (channel().isRegistered() && channel().eventLoop().inEventLoop()) {
            throw new BlockingOperationException();
        }
    }

    @Override
    public boolean setSuccess() {
        synchronized (this) {
            // Allow only once.
            if (done) {
                return false;
            }

            done = true;
            if (waiters > 0) {
                notifyAll();
            }
        }

        notifyListeners();
        return true;
    }

    @Override
    public boolean setFailure(Throwable cause) {
        synchronized (this) {
            // Allow only once.
            if (done) {
                return false;
            }

            this.cause = cause;
            done = true;
            if (waiters > 0) {
                notifyAll();
            }
        }

        notifyListeners();
        return true;
    }

    @Override
    public boolean cancel() {
        if (!cancellable) {
            return false;
        }

        synchronized (this) {
            // Allow only once.
            if (done) {
                return false;
            }

            cause = CANCELLED;
            done = true;
            if (waiters > 0) {
                notifyAll();
            }
        }

        notifyListeners();
        return true;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return cancel();
    }

    private void notifyListeners() {
        // This method doesn't need synchronization because:
        // 1) This method is always called after synchronized (this) block.
        //    Hence any listener list modification happens-before this method.
        // 2) This method is called only when 'done' is true.  Once 'done'
        //    becomes true, the listener list is never modified - see add/removeListener()

        if (firstListener == null) {
            return;
        }

        if (channel().eventLoop().inEventLoop()) {
            notifyListener0(this, firstListener);
            firstListener = null;

            if (otherListeners != null) {
                for (ChannelFutureListener l: otherListeners) {
                    notifyListener0(this, l);
                }
                otherListeners = null;
            }
        } else {
            final ChannelFutureListener firstListener = this.firstListener;
            final List<ChannelFutureListener> otherListeners = this.otherListeners;
            this.firstListener = null;
            this.otherListeners = null;
            channel().eventLoop().execute(new Runnable() {
                @Override
                public void run() {
                    notifyListener0(DefaultChannelFuture.this, firstListener);
                    if (otherListeners != null) {
                        for (ChannelFutureListener l: otherListeners) {
                            notifyListener0(DefaultChannelFuture.this, l);
                        }
                    }
                }
            });
        }
    }

    static void notifyListener(final ChannelFuture f, final ChannelFutureListener l) {
        EventLoop loop = f.channel().eventLoop();
        if (loop.inEventLoop()) {
            final Integer stackDepth = LISTENER_STACK_DEPTH.get();
            if (stackDepth < MAX_LISTENER_STACK_DEPTH) {
                LISTENER_STACK_DEPTH.set(stackDepth + 1);
                try {
                    notifyListener0(f, l);
                } finally {
                    LISTENER_STACK_DEPTH.set(stackDepth);
                }
                return;
            }
        }

        loop.execute(new Runnable() {
            @Override
            public void run() {
                notifyListener(f, l);
            }

        });
    }

    private static void notifyListener0(ChannelFuture f, ChannelFutureListener l) {
        try {
            l.operationComplete(f);
        } catch (Throwable t) {
            if (logger.isWarnEnabled()) {
                logger.warn(
                        "An exception was thrown by " +
                        ChannelFutureListener.class.getSimpleName() + '.', t);
            }
        }
    }

    @Override
    public boolean setProgress(long amount, long current, long total) {
        ChannelFutureProgressListener[] plisteners;
        synchronized (this) {
            // Do not generate progress event after completion.
            if (done) {
                return false;
            }

            Collection<ChannelFutureProgressListener> progressListeners =
                this.progressListeners;
            if (progressListeners == null || progressListeners.isEmpty()) {
                // Nothing to notify - no need to create an empty array.
                return true;
            }

            plisteners = progressListeners.toArray(
                    new ChannelFutureProgressListener[progressListeners.size()]);
        }

        for (ChannelFutureProgressListener pl: plisteners) {
            notifyProgressListener(pl, amount, current, total);
        }

        return true;
    }

    private void notifyProgressListener(
            ChannelFutureProgressListener l,
            long amount, long current, long total) {

        try {
            l.operationProgressed(this, amount, current, total);
        } catch (Throwable t) {
            if (logger.isWarnEnabled()) {
                logger.warn(
                        "An exception was thrown by " +
                        ChannelFutureProgressListener.class.getSimpleName() + '.', t);
            }
        }
    }

    @Override
    long flushCheckpoint() {
        return flushCheckpoint;
    }

    @Override
    void flushCheckpoint(long checkpoint) {
        flushCheckpoint = checkpoint;
    }

    @Override
    ChannelFuture future() {
        return this;
    }
}
