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

import io.netty.channel.ChannelFlushPromiseNotifier.FlushCheckpoint;
import io.netty.util.Signal;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.*;

/**
 * The default {@link ChannelPromise} implementation.  It is recommended to use {@link Channel#newPromise()} to create
 * a new {@link ChannelPromise} rather than calling the constructor explicitly.
 */
public class DefaultChannelPromise extends FlushCheckpoint implements ChannelPromise {

    private static final InternalLogger logger =
        InternalLoggerFactory.getInstance(DefaultChannelPromise.class);

    private static final int MAX_LISTENER_STACK_DEPTH = 8;
    private static final ThreadLocal<Integer> LISTENER_STACK_DEPTH = new ThreadLocal<Integer>() {
        @Override
        protected Integer initialValue() {
            return 0;
        }
    };

    private static final Signal SUCCESS = new Signal(DefaultChannelPromise.class.getName() + ".SUCCESS");

    private final Channel channel;
    private volatile Throwable cause;
    private Object listeners; // Can be ChannelFutureListener or DefaultChannelPromiseListeners

    /**
     * The first 24 bits of this field represents the number of waiters waiting for this promise with await*().
     * The other 40 bits of this field represents the flushCheckpoint used by ChannelFlushPromiseNotifier and
     * AbstractChannel.Unsafe.flush().
     */
    private long flushCheckpoint;

    /**
     * Creates a new instance.
     *
     * @param channel
     *        the {@link Channel} associated with this future
     */
    public DefaultChannelPromise(Channel channel) {
        this.channel = channel;
    }

    @Override
    public Channel channel() {
        return channel;
    }

    @Override
    public boolean isDone() {
        return cause != null;
    }

    @Override
    public boolean isSuccess() {
        return cause == SUCCESS;
    }

    @Override
    public Throwable cause() {
        Throwable cause = this.cause;
        return cause == SUCCESS? null : cause;
    }

    @Override
    public ChannelPromise addListener(final ChannelFutureListener listener) {
        if (listener == null) {
            throw new NullPointerException("listener");
        }

        if (isDone()) {
            notifyListener(this, listener);
            return this;
        }

        synchronized (this) {
            if (!isDone()) {
                if (listeners == null) {
                    listeners = listener;
                } else {
                    if (listeners instanceof DefaultChannelPromiseListeners) {
                        ((DefaultChannelPromiseListeners) listeners).add(listener);
                    } else {
                        listeners = new DefaultChannelPromiseListeners((ChannelFutureListener) listeners, listener);
                    }
                }
                return this;
            }
        }

        notifyListener(this, listener);
        return this;
    }

    @Override
    public ChannelPromise addListeners(ChannelFutureListener... listeners) {
        if (listeners == null) {
            throw new NullPointerException("listeners");
        }

        for (ChannelFutureListener l: listeners) {
            if (l == null) {
                break;
            }
            addListener(l);
        }
        return this;
    }

    @Override
    public ChannelPromise removeListener(ChannelFutureListener listener) {
        if (listener == null) {
            throw new NullPointerException("listener");
        }

        if (isDone()) {
            return this;
        }

        synchronized (this) {
            if (!isDone()) {
                if (listeners instanceof DefaultChannelPromiseListeners) {
                    ((DefaultChannelPromiseListeners) listeners).remove(listener);
                } else if (listeners == listener) {
                    listeners = null;
                }
            }
        }

        return this;
    }

    @Override
    public ChannelPromise removeListeners(ChannelFutureListener... listeners) {
        if (listeners == null) {
            throw new NullPointerException("listeners");
        }

        for (ChannelFutureListener l: listeners) {
            if (l == null) {
                break;
            }
            removeListener(l);
        }
        return this;
    }

    @Override
    public ChannelPromise sync() throws InterruptedException {
        await();
        rethrowIfFailed();
        return this;
    }

    @Override
    public ChannelPromise syncUninterruptibly() {
        awaitUninterruptibly();
        rethrowIfFailed();
        return this;
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
    public ChannelPromise await() throws InterruptedException {
        if (isDone()) {
            return this;
        }

        if (Thread.interrupted()) {
            throw new InterruptedException();
        }

        synchronized (this) {
            while (!isDone()) {
                checkDeadLock();
                incWaiters();
                try {
                    wait();
                } finally {
                    decWaiters();
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
    public ChannelPromise awaitUninterruptibly() {
        if (isDone()) {
            return this;
        }

        boolean interrupted = false;
        synchronized (this) {
            while (!isDone()) {
                checkDeadLock();
                incWaiters();
                try {
                    wait();
                } catch (InterruptedException e) {
                    interrupted = true;
                } finally {
                    decWaiters();
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
        if (isDone()) {
            return true;
        }

        if (timeoutNanos <= 0) {
            return isDone();
        }

        if (interruptable && Thread.interrupted()) {
            throw new InterruptedException();
        }

        long startTime = timeoutNanos <= 0 ? 0 : System.nanoTime();
        long waitTime = timeoutNanos;
        boolean interrupted = false;

        try {
            synchronized (this) {
                if (isDone()) {
                    return true;
                }

                if (waitTime <= 0) {
                    return isDone();
                }

                checkDeadLock();
                incWaiters();
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

                        if (isDone()) {
                            return true;
                        } else {
                            waitTime = timeoutNanos - (System.nanoTime() - startTime);
                            if (waitTime <= 0) {
                                return isDone();
                            }
                        }
                    }
                } finally {
                    decWaiters();
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
    public ChannelPromise setSuccess() {
        if (set(SUCCESS)) {
            notifyListeners();
            return this;
        }
        throw new IllegalStateException();
    }

    @Override
    public boolean trySuccess() {
        if (set(SUCCESS)) {
            notifyListeners();
            return true;
        }
        return false;
    }

    @Override
    public ChannelPromise setFailure(Throwable cause) {
        if (set(cause)) {
            notifyListeners();
            return this;
        }
        throw new IllegalStateException();
    }

    @Override
    public boolean tryFailure(Throwable cause) {
        if (set(cause)) {
            notifyListeners();
            return true;
        }
        return false;
    }

    private boolean set(Throwable cause) {
        if (isDone()) {
            return false;
        }

        synchronized (this) {
            // Allow only once.
            if (isDone()) {
                return false;
            }

            this.cause = cause;
            if (hasWaiters()) {
                notifyAll();
            }
        }
        return true;
    }

    private void notifyListeners() {
        // This method doesn't need synchronization because:
        // 1) This method is always called after synchronized (this) block.
        //    Hence any listener list modification happens-before this method.
        // 2) This method is called only when 'done' is true.  Once 'done'
        //    becomes true, the listener list is never modified - see add/removeListener()

        if (listeners == null) {
            return;
        }

        if (channel().eventLoop().inEventLoop()) {
            if (listeners instanceof DefaultChannelPromiseListeners) {
                notifyListeners0(this, (DefaultChannelPromiseListeners) listeners);
            } else {
                notifyListener0(this, (ChannelFutureListener) listeners);
            }
            listeners = null;
        } else {
            final Object listeners = this.listeners;
            this.listeners = null;
            channel().eventLoop().execute(new Runnable() {
                @Override
                public void run() {
                    if (listeners instanceof DefaultChannelPromiseListeners) {
                        notifyListeners0(DefaultChannelPromise.this, (DefaultChannelPromiseListeners) listeners);
                    } else {
                        notifyListener0(DefaultChannelPromise.this, (ChannelFutureListener) listeners);
                    }
                }
            });
        }
    }

    private static void notifyListeners0(ChannelFuture f, DefaultChannelPromiseListeners listeners) {
        final ChannelFutureListener[] a = listeners.listeners();
        final int size = listeners.size();
        for (int i = 0; i < size; i ++) {
            notifyListener0(f, a[i]);
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
    long flushCheckpoint() {
        return flushCheckpoint & 0x000000FFFFFFFFFFL;
    }

    @Override
    void flushCheckpoint(long checkpoint) {
        if ((checkpoint & 0xFFFFFF0000000000L) != 0) {
            throw new IllegalStateException("flushCheckpoint overflow");
        }
        flushCheckpoint = flushCheckpoint & 0xFFFFFF0000000000L | checkpoint;
    }

    private boolean hasWaiters() {
        return (flushCheckpoint & 0xFFFFFF0000000000L) != 0;
    }

    private void incWaiters() {
        long waiters = waiters() + 1;
        if ((waiters & 0xFFFFFFFFFF000000L) != 0) {
            throw new IllegalStateException("too many waiters");
        }
        flushCheckpoint = flushCheckpoint() | waiters << 40L;
    }

    private void decWaiters() {
        flushCheckpoint = flushCheckpoint() | waiters() - 1L << 40L;
    }

    private long waiters() {
        return flushCheckpoint >>> 40;
    }

    @Override
    ChannelPromise future() {
        return this;
    }

    private static final class DefaultChannelPromiseListeners {
        private ChannelFutureListener[] listeners;
        private int size;

        DefaultChannelPromiseListeners(ChannelFutureListener firstListener, ChannelFutureListener secondListener) {
            listeners = new ChannelFutureListener[] { firstListener, secondListener };
            size = 2;
        }

        void add(ChannelFutureListener l) {
            ChannelFutureListener[] listeners = this.listeners;
            final int size = this.size;
            if (size == listeners.length) {
                this.listeners = listeners = Arrays.copyOf(listeners, size << 1);
            }
            listeners[size] = l;
            this.size = size + 1;
        }

        void remove(ChannelFutureListener l) {
            final ChannelFutureListener[] listeners = this.listeners;
            int size = this.size;
            for (int i = 0; i < size; i ++) {
                if (listeners[i] == l) {
                    int listenersToMove = size - i - 1;
                    if (listenersToMove > 0) {
                        System.arraycopy(listeners, i + 1, listeners, i, listenersToMove);
                    }
                    listeners[-- size] = null;
                    this.size = size;
                    return;
                }
            }
        }

        ChannelFutureListener[] listeners() {
            return listeners;
        }

        int size() {
            return size;
        }
    }
}
