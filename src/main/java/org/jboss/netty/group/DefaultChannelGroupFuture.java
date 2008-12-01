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
package org.jboss.netty.group;

import static java.util.concurrent.TimeUnit.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;

/**
 * The default {@link ChannelGroupFuture} implementation.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 */
public class DefaultChannelGroupFuture implements ChannelGroupFuture {

    private static final InternalLogger logger =
        InternalLoggerFactory.getInstance(DefaultChannelGroupFuture.class);

    private final ChannelGroup group;
    final Map<UUID, ChannelFuture> futures;
    private volatile ChannelGroupFutureListener firstListener;
    private volatile List<ChannelGroupFutureListener> otherListeners;
    private boolean done;
    int successCount;
    int failureCount;
    private int waiters;

    private final ChannelFutureListener childListener = new ChannelFutureListener() {
        public void operationComplete(ChannelFuture future) throws Exception {
            boolean success = future.isSuccess();
            synchronized (DefaultChannelGroupFuture.this) {
                if (success) {
                    successCount ++;
                } else {
                    failureCount ++;
                }
            }

            if (successCount + failureCount == futures.size()) {
                setDone();
            }

            assert successCount + failureCount <= futures.size();
        }
    };

    /**
     * Creates a new instance.
     */
    public DefaultChannelGroupFuture(ChannelGroup group, Collection<ChannelFuture> futures) {
        if (group == null) {
            throw new NullPointerException("group");
        }
        if (futures == null) {
            throw new NullPointerException("futures");
        }

        this.group = group;

        Map<UUID, ChannelFuture> futureMap = new HashMap<UUID, ChannelFuture>();
        for (ChannelFuture f: futures) {
            futureMap.put(f.getChannel().getId(), f);
        }

        this.futures = Collections.unmodifiableMap(futureMap);
        for (ChannelFuture f: this.futures.values()) {
            f.addListener(childListener);
        }
    }

    DefaultChannelGroupFuture(ChannelGroup group, Map<UUID, ChannelFuture> futures) {
        this.group = group;
        this.futures = Collections.unmodifiableMap(futures);
        for (ChannelFuture f: this.futures.values()) {
            f.addListener(childListener);
        }
    }

    public ChannelGroup getGroup() {
        return group;
    }

    public ChannelFuture find(UUID channelId) {
        return futures.get(channelId);
    }

    public ChannelFuture find(Channel channel) {
        return futures.get(channel.getId());
    }

    public Iterator<ChannelFuture> iterator() {
        return futures.values().iterator();
    }

    public synchronized boolean isDone() {
        return done;
    }

    public synchronized boolean isCompleteSuccess() {
        return successCount == futures.size();
    }

    public synchronized boolean isPartialSuccess() {
        return successCount != 0;
    }

    public synchronized boolean isPartialFailure() {
        return failureCount != 0;
    }

    public synchronized boolean isCompleteFailure() {
        return failureCount == futures.size();
    }

    public void addListener(ChannelGroupFutureListener listener) {
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
                        otherListeners = new ArrayList<ChannelGroupFutureListener>(1);
                    }
                    otherListeners.add(listener);
                }
            }
        }

        if (notifyNow) {
            notifyListener(listener);
        }
    }

    public void removeListener(ChannelGroupFutureListener listener) {
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

    public ChannelGroupFuture await() throws InterruptedException {
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
        return await0(unit.toNanos(timeout), true);
    }

    public boolean await(long timeoutMillis) throws InterruptedException {
        return await0(MILLISECONDS.toNanos(timeoutMillis), true);
    }

    public ChannelGroupFuture awaitUninterruptibly() {
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
        try {
            return await0(unit.toNanos(timeout), false);
        } catch (InterruptedException e) {
            throw new InternalError();
        }
    }

    public boolean awaitUninterruptibly(long timeoutMillis) {
        try {
            return await0(MILLISECONDS.toNanos(timeoutMillis), false);
        } catch (InterruptedException e) {
            throw new InternalError();
        }
    }

    private boolean await0(long timeoutNanos, boolean interruptable) throws InterruptedException {
        long startTime = timeoutNanos <= 0 ? 0 : System.nanoTime();
        long waitTime = timeoutNanos;

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
                        this.wait(waitTime / 1000000, (int) (waitTime % 1000000));
                    } catch (InterruptedException e) {
                        if (interruptable) {
                            throw e;
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
    }

    boolean setDone() {
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

    private void notifyListeners() {
        // There won't be any visibility problem or concurrent modification
        // because 'ready' flag will be checked against both addListener and
        // removeListener calls.
        if (firstListener != null) {
            notifyListener(firstListener);
            firstListener = null;

            if (otherListeners != null) {
                for (ChannelGroupFutureListener l: otherListeners) {
                    notifyListener(l);
                }
                otherListeners = null;
            }
        }
    }

    private void notifyListener(ChannelGroupFutureListener l) {
        try {
            l.operationComplete(this);
        } catch (Throwable t) {
            logger.warn(
                    "An exception was thrown by " +
                    ChannelFutureListener.class.getSimpleName() + ".", t);
        }
    }
}
