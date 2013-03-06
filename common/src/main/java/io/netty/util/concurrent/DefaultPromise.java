/*
 * Copyright 2013 The Netty Project
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
package io.netty.util.concurrent;

import io.netty.util.Signal;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.Arrays;
import java.util.EventListener;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.MILLISECONDS;


public class DefaultPromise implements Promise {

    private static final InternalLogger logger =
        InternalLoggerFactory.getInstance(DefaultPromise.class);

    private static final int MAX_LISTENER_STACK_DEPTH = 8;
    private static final ThreadLocal<Integer> LISTENER_STACK_DEPTH = new ThreadLocal<Integer>() {
        @Override
        protected Integer initialValue() {
            return 0;
        }
    };

    private static final Signal SUCCESS = new Signal(DefaultPromise.class.getName() + ".SUCCESS");

    private volatile Throwable cause;
    private Object listeners; // Can be ChannelFutureListener or DefaultChannelPromiseListeners
    private final EventExecutor executor;
    private int waiters;

    /**
     * Creates a new instance.
     *
     * @param executor
     *        the {@link EventExecutor} which is used to notify the promise once it is complete
     */
    public DefaultPromise(EventExecutor executor) {
        if (executor == null) {
            throw new NullPointerException("executor");
        }
        this.executor = executor;
    }

    protected DefaultPromise() {
        // only for subclasses
        executor = null;
    }

    protected EventExecutor executor() {
        if (executor == null) {
            throw new IllegalStateException();
        }
        return executor;
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
    public Promise addListener(GenericFutureListener<? extends Future> listener) {
        if (listener == null) {
            throw new NullPointerException("listener");
        }

        if (isDone()) {
            notifyListener(executor(), this, listener);
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
                        listeners = new DefaultChannelPromiseListeners(
                                (GenericFutureListener<? extends Future>) listeners, listener);
                    }
                }
                return this;
            }
        }

        notifyListener(executor(), this, listener);
        return this;
    }

    @Override
    public Promise addListeners(GenericFutureListener<? extends Future>... listeners) {
        if (listeners == null) {
            throw new NullPointerException("listeners");
        }

        for (GenericFutureListener<? extends Future> l: listeners) {
            if (l == null) {
                break;
            }
            addListener(l);
        }
        return this;
    }

    @Override
    public Promise removeListener(GenericFutureListener<? extends Future> listener) {
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
    public Promise removeListeners(GenericFutureListener<? extends Future>... listeners) {
        if (listeners == null) {
            throw new NullPointerException("listeners");
        }

        for (GenericFutureListener<? extends Future> l: listeners) {
            if (l == null) {
                break;
            }
            removeListener(l);
        }
        return this;
    }

    @Override
    public Promise await() throws InterruptedException {
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
    public Promise awaitUninterruptibly() {
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

    /**
     * Do deadlock checks
     */
    protected void checkDeadLock() {
        // NOOP
    }

    @Override
    public Promise setSuccess() {
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
    public Promise setFailure(Throwable cause) {
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

        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            if (listeners instanceof DefaultChannelPromiseListeners) {
                notifyListeners0(this, (DefaultChannelPromiseListeners) listeners);
            } else {
                notifyListener0(this, (GenericFutureListener<? extends Future>) listeners);
            }
            listeners = null;
        } else {
            final Object listeners = this.listeners;
            this.listeners = null;
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    if (listeners instanceof DefaultChannelPromiseListeners) {
                        notifyListeners0(DefaultPromise.this, (DefaultChannelPromiseListeners) listeners);
                    } else {
                        notifyListener0(DefaultPromise.this, (GenericFutureListener<? extends Future>) listeners);
                    }
                }
            });
        }
    }

    private static void notifyListeners0(final Future future,
                                         DefaultChannelPromiseListeners listeners) {
        final GenericFutureListener<? extends Future>[] a = listeners.listeners();
        final int size = listeners.size();
        for (int i = 0; i < size; i ++) {
            notifyListener0(future, a[i]);
        }
    }

   public static void notifyListener(final EventExecutor eventExecutor, final Future future,
                               final GenericFutureListener<? extends Future> l) {
        if (eventExecutor.inEventLoop()) {
            final Integer stackDepth = LISTENER_STACK_DEPTH.get();
            if (stackDepth < MAX_LISTENER_STACK_DEPTH) {
                LISTENER_STACK_DEPTH.set(stackDepth + 1);
                try {
                    notifyListener0(future, l);
                } finally {
                    LISTENER_STACK_DEPTH.set(stackDepth);
                }
                return;
            }
        }

        eventExecutor.execute(new Runnable() {
            @Override
            public void run() {
                notifyListener(eventExecutor, future, l);
            }
        });
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void notifyListener0(Future future, GenericFutureListener l) {
        try {
            l.operationComplete(future);
        } catch (Throwable t) {
            if (logger.isWarnEnabled()) {
                logger.warn(
                        "An exception was thrown by " +
                                GenericFutureListener.class.getSimpleName() + '.', t);
            }
        }
    }

    protected boolean hasWaiters() {
        return waiters > 0;
    }

    protected void incWaiters() {
        waiters++;
    }

    protected void decWaiters() {
        waiters--;
    }

    private static final class DefaultChannelPromiseListeners {
        private GenericFutureListener<? extends Future>[] listeners;
        private int size;

        @SuppressWarnings("unchecked")
        DefaultChannelPromiseListeners(GenericFutureListener<? extends Future> firstListener,
                                       GenericFutureListener<? extends Future> secondListener) {

            listeners = new GenericFutureListener[] { firstListener, secondListener };
            size = 2;
        }

        void add(GenericFutureListener<? extends Future> l) {
            GenericFutureListener<? extends Future>[] listeners = this.listeners;
            final int size = this.size;
            if (size == listeners.length) {
                this.listeners = listeners = Arrays.copyOf(listeners, size << 1);
            }
            listeners[size] = l;
            this.size = size + 1;
        }

        void remove(EventListener l) {
            final EventListener[] listeners = this.listeners;
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

        GenericFutureListener<? extends Future>[] listeners() {
            return listeners;
        }

        int size() {
            return size;
        }
    }
}
