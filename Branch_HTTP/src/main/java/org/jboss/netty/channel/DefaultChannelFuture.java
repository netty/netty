/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.channel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;

/**
 * The default {@link ChannelFuture} implementation.  It is recommended to
 * use {@link Channels#future(Channel)} and {@link Channels#future(Channel, boolean)}
 * to create a new {@link ChannelFuture} rather than calling the constructor
 * explicitly.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 */
public class DefaultChannelFuture implements ChannelFuture {

    private static final InternalLogger logger =
        InternalLoggerFactory.getInstance(DefaultChannelFuture.class);

    private static final Throwable CANCELLED = new Throwable();

    private final Channel channel;
    private final boolean cancellable;

    private ChannelFutureListener firstListener;
    private List<ChannelFutureListener> otherListeners;
    private boolean done;
    private Throwable cause;
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
                    this.wait();
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
                    this.wait();
                } catch (InterruptedException e) {
                    // Ignore.
                } finally {
                    waiters--;
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
                        this.wait(waitTime);
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
            }
        }
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
