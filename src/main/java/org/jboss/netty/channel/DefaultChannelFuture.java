/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.channel;

import static java.util.concurrent.TimeUnit.*;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.util.internal.DeadLockProofWorker;

/**
 * The default {@link ChannelFuture} implementation.  It is recommended to
 * use {@link Channels#future(Channel)} and {@link Channels#future(Channel, boolean)}
 * to create a new {@link ChannelFuture} rather than calling the constructor
 * explicitly.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev$, $Date$
 */
public class DefaultChannelFuture implements ChannelFuture {

    private static final InternalLogger logger =
        InternalLoggerFactory.getInstance(DefaultChannelFuture.class);

    private static final Throwable CANCELLED = new Throwable();
    private static final Result CANCELLED_RESULT = new Result(CANCELLED);
    private static final Result SUCCESS_RESULT = new Result();

    private static volatile boolean useDeadLockChecker = true;
    private static boolean disabledDeadLockCheckerOnce;

    /**
     * Returns {@code true} if and only if the dead lock checker is enabled.
     */
    public static boolean isUseDeadLockChecker() {
        return useDeadLockChecker;
    }

    /**
     * Enables or disables the dead lock checker.  It is not recommended to
     * disable the dead lock checker.  Disable it at your own risk!
     */
    public static void setUseDeadLockChecker(boolean useDeadLockChecker) {
        if (!useDeadLockChecker && !disabledDeadLockCheckerOnce) {
            disabledDeadLockCheckerOnce = true;
            logger.debug(
                    "The dead lock checker in " +
                    DefaultChannelFuture.class.getSimpleName() +
                    " has been disabled as requested at your own risk.");
        }
        DefaultChannelFuture.useDeadLockChecker = useDeadLockChecker;
    }

    private final Channel channel;
    private final boolean cancellable;

    private final List<ChannelFutureListener> listeners = new CopyOnWriteArrayList<ChannelFutureListener>();
    private final List<ChannelFutureProgressListener> progressListeners = new CopyOnWriteArrayList<ChannelFutureProgressListener>();
    private final AtomicReference<Result> result = new AtomicReference<Result>();
    private int waiters;

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
    public Channel getChannel() {
        return channel;
    }

    @Override
    public boolean isDone() {
        return result.get() != null;
    }

    @Override
    public boolean isSuccess() {
        Result r = result.get();
        return r != null && r.isSuccess();
    }

    @Override
    public Throwable getCause() {
        Result r = result.get();
        
        if (r != null && r.getCause() != CANCELLED) {
            return r.getCause();
        } else {
            return null;
        }
    }

    @Override
    public boolean isCancelled() {
        Result r = result.get();
        return r != null && r.isCancelled();
    }

    @Override
    public void addListener(ChannelFutureListener listener) {
        if (listener == null) {
            throw new NullPointerException("listener");
        }
        if (isDone()) {
            notifyListener(listener);
        } else {
            listeners.add(listener);
            if (listener instanceof ChannelFutureProgressListener) {
                progressListeners.add((ChannelFutureProgressListener) listener);
            }     
        }
    }

    @Override
    public void removeListener(ChannelFutureListener listener) {
        if (listener == null) {
            throw new NullPointerException("listener");
        }

        if (!isDone()) {
            listeners.remove(listener);
            if (listener instanceof ChannelFutureProgressListener) {
                progressListeners.remove(listener);
            }
        }

    }

    @Override
    public ChannelFuture await() throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }

        synchronized (this) {
            while (!isDone()) {
                checkDeadLock();
                waiters++;
                try {
                    this.wait();
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
            while (!isDone()) {
                checkDeadLock();
                waiters++;
                try {
                    this.wait();
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
                if (isDone()) {
                    return true;
                } else if (waitTime <= 0) {
                    return isDone();
                }

                checkDeadLock();
                waiters++;
                try {
                    for (;;) {
                        try {
                            this.wait(waitTime / 1000000, (int) (waitTime % 1000000));
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
        if (isUseDeadLockChecker() && DeadLockProofWorker.PARENT.get() != null) {
            throw new IllegalStateException(
                    "await*() in I/O thread causes a dead lock or " +
                    "sudden performance drop. Use addListener() instead or " +
                    "call await*() from a different thread.");
        }
    }

    @Override
    public boolean setSuccess() {
        return setResult(SUCCESS_RESULT);
    }

    @Override
    public boolean setFailure(Throwable cause) {
        return setResult(new Result(cause));
    }
    
    private boolean setResult(Result r) {
        // Allow only once.
        if (isDone()) {
            return false;
        }
        boolean set = result.compareAndSet(null, r);

        if (set) {
            synchronized (this) {
                if (waiters > 0) {
                    notifyAll();
                }
            }
            notifyListeners();
        }

        return set;
    }

    @Override
    public boolean cancel() {
        if (!cancellable) {
            return false;
        }
        return setResult(CANCELLED_RESULT);
    }

    private void notifyListeners() {
        // thats safe as CopyOnWriteArrayList does make sure its safe to iterate over the results
        Iterator<ChannelFutureListener> clisteners = listeners.iterator();
        while (clisteners.hasNext()) {
            notifyListener(clisteners.next());
        }
    }

    private void notifyListener(ChannelFutureListener l) {
        try {
            l.operationComplete(this);
        } catch (Throwable t) {
            logger.warn(
                    "An exception was thrown by " +
                    ChannelFutureListener.class.getSimpleName() + ".", t);
        }
    }

    @Override
    public boolean setProgress(long amount, long current, long total) {
        // Do not generate progress event after completion.
        if (isDone()) {
            return false;
        }
        
        // thats safe as CopyOnWriteArrayList does make sure its safe to iterate over the results
        Iterator<ChannelFutureProgressListener> pListeners = progressListeners.iterator();
        while (pListeners.hasNext()) {
            notifyProgressListener(pListeners.next(), amount, current, total);

        }
        return true;
    }

    private void notifyProgressListener(
            ChannelFutureProgressListener l,
            long amount, long current, long total) {

        try {
            l.operationProgressed(this, amount, current, total);
        } catch (Throwable t) {
            logger.warn(
                    "An exception was thrown by " +
                    ChannelFutureProgressListener.class.getSimpleName() + ".", t);
        }
    }
    
    private static final class Result {
        
        private final Throwable cause;

        public Result() {
            this(null);
        }
        
        public Result(Throwable cause) {
            this.cause = cause;
        }
        
        public Throwable getCause() {
            return cause;
        }
        
        public boolean isSuccess() {
            return cause == null;
        }
        
        public boolean isCancelled() {
            return cause != null && cause == CANCELLED;
        }
        
    }
}
