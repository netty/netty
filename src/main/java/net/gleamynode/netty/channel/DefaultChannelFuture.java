/*
 * Copyright (C) 2008  Trustin Heuiseung Lee
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, 5th Floor, Boston, MA 02110-1301 USA
 */
package net.gleamynode.netty.channel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import net.gleamynode.netty.logging.Logger;


public class DefaultChannelFuture implements ChannelFuture {

    private static final Logger logger =
        Logger.getLogger(DefaultChannelFuture.class);
    private static final int DEAD_LOCK_CHECK_INTERVAL = 5000;
    private static final Throwable CANCELLED = new Throwable();

    private final Channel channel;
    private final boolean cancellable;

    private ChannelFutureListener firstListener;
    private List<ChannelFutureListener> otherListeners;
    private boolean done;
    private Throwable cause;
    private int waiters;

    public DefaultChannelFuture(Channel channel, boolean cancellable) {
        this.channel = channel;
        this.cancellable = cancellable;
    }

    public Channel getChannel() {
        return channel;
    }

    public synchronized boolean isDone() {
        return done;
    }

    public synchronized boolean isSuccess() {
        return cause == null;
    }

    public synchronized Throwable getCause() {
        if (cause != CANCELLED) {
            return cause;
        } else {
            return null;
        }
    }

    public synchronized boolean isCancelled() {
        return cause == CANCELLED;
    }

    public void addListener(ChannelFutureListener listener) {
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
            }
        }

        if (notifyNow) {
            notifyListener(listener);
        }
    }

    public void removeListener(ChannelFutureListener listener) {
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
            }
        }
    }

    public ChannelFuture await() throws InterruptedException {
        synchronized (this) {
            while (!done) {
                waiters++;
                try {
                    this.wait(DEAD_LOCK_CHECK_INTERVAL);
                    checkDeadLock();
                } finally {
                    waiters--;
                }
            }
        }
        return this;
    }

    public boolean await(long timeout, TimeUnit unit)
            throws InterruptedException {
        return await(unit.toMillis(timeout));
    }

    public boolean await(long timeoutMillis) throws InterruptedException {
        return await0(timeoutMillis, true);
    }

    public ChannelFuture awaitUninterruptibly() {
        synchronized (this) {
            while (!done) {
                waiters++;
                try {
                    this.wait(DEAD_LOCK_CHECK_INTERVAL);
                } catch (InterruptedException e) {
                    // Ignore.
                } finally {
                    waiters--;
                    if (!done) {
                        checkDeadLock();
                    }
                }
            }
        }

        return this;
    }

    public boolean awaitUninterruptibly(long timeout, TimeUnit unit) {
        return awaitUninterruptibly(unit.toMillis(timeout));
    }

    public boolean awaitUninterruptibly(long timeoutMillis) {
        try {
            return await0(timeoutMillis, false);
        } catch (InterruptedException e) {
            throw new InternalError();
        }
    }

    private boolean await0(long timeoutMillis, boolean interruptable) throws InterruptedException {
        long startTime = timeoutMillis <= 0 ? 0 : System.currentTimeMillis();
        long waitTime = timeoutMillis;

        synchronized (this) {
            if (done) {
                return done;
            } else if (waitTime <= 0) {
                return done;
            }

            waiters++;
            try {
                for (;;) {
                    try {
                        this.wait(Math.min(waitTime, DEAD_LOCK_CHECK_INTERVAL));
                    } catch (InterruptedException e) {
                        if (interruptable) {
                            throw e;
                        }
                    }

                    if (done) {
                        return true;
                    } else {
                        waitTime = timeoutMillis
                                - (System.currentTimeMillis() - startTime);
                        if (waitTime <= 0) {
                            return done;
                        }
                    }
                }
            } finally {
                waiters--;
                if (!done) {
                    checkDeadLock();
                }
            }
        }
    }

    private void checkDeadLock() {
//        IllegalStateException e = new IllegalStateException(
//                "DEAD LOCK: " + ChannelFuture.class.getSimpleName() +
//                ".await() was invoked from an I/O processor thread.  " +
//                "Please use " + ChannelFutureListener.class.getSimpleName() +
//                " or configure a proper thread model alternatively.");
//
//        StackTraceElement[] stackTrace = e.getStackTrace();
//
//        // Simple and quick check.
//        for (StackTraceElement s: stackTrace) {
//            if (AbstractPollingIoProcessor.class.getName().equals(s.getClassName())) {
//                throw e;
//            }
//        }
//
//        // And then more precisely.
//        for (StackTraceElement s: stackTrace) {
//            try {
//                Class<?> cls = DefaultChannelFuture.class.getClassLoader().loadClass(s.getClassName());
//                if (IoProcessor.class.isAssignableFrom(cls)) {
//                    throw e;
//                }
//            } catch (Exception cnfe) {
//                // Ignore
//            }
//        }
    }

    public void setSuccess() {
        synchronized (this) {
            // Allow only once.
            if (done) {
                return;
            }

            done = true;
            if (waiters > 0) {
                this.notifyAll();
            }
        }

        notifyListeners();
    }

    public void setFailure(Throwable cause) {
        synchronized (this) {
            // Allow only once.
            if (done) {
                return;
            }

            this.cause = cause;
            done = true;
            if (waiters > 0) {
                this.notifyAll();
            }
        }

        notifyListeners();
    }

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
                this.notifyAll();
            }
        }

        notifyListeners();
        return true;
    }

    private void notifyListeners() {
        // There won't be any visibility problem or concurrent modification
        // because 'ready' flag will be checked against both addListener and
        // removeListener calls.
        if (firstListener != null) {
            notifyListener(firstListener);
            firstListener = null;

            if (otherListeners != null) {
                for (ChannelFutureListener l: otherListeners) {
                    notifyListener(l);
                }
                otherListeners = null;
            }

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
}
