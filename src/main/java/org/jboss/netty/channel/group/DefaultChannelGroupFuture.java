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
package org.jboss.netty.channel.group;

import static java.util.concurrent.TimeUnit.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.util.internal.DeadLockProofWorker;

/**
 * The default {@link ChannelGroupFuture} implementation.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2191 $, $Date: 2010-02-19 18:18:10 +0900 (Fri, 19 Feb 2010) $
 */
public class DefaultChannelGroupFuture implements ChannelGroupFuture {

    private static final InternalLogger logger =
        InternalLoggerFactory.getInstance(DefaultChannelGroupFuture.class);

    private final ChannelGroup group;
    final Map<Integer, ChannelFuture> futures;
    private ChannelGroupFutureListener firstListener;
    private List<ChannelGroupFutureListener> otherListeners;
    private boolean done;
    int successCount;
    int failureCount;
    private int waiters;

    private final ChannelFutureListener childListener = new ChannelFutureListener() {
        public void operationComplete(ChannelFuture future) throws Exception {
            boolean success = future.isSuccess();
            boolean callSetDone = false;
            synchronized (DefaultChannelGroupFuture.this) {
                if (success) {
                    successCount ++;
                } else {
                    failureCount ++;
                }

                callSetDone = successCount + failureCount == futures.size();
                assert successCount + failureCount <= futures.size();
            }

            if (callSetDone) {
                setDone();
            }
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

        Map<Integer, ChannelFuture> futureMap = new LinkedHashMap<Integer, ChannelFuture>();
        for (ChannelFuture f: futures) {
            futureMap.put(f.getChannel().getId(), f);
        }

        this.futures = Collections.unmodifiableMap(futureMap);

        for (ChannelFuture f: this.futures.values()) {
            f.addListener(childListener);
        }

        // Done on arrival?
        if (this.futures.isEmpty()) {
            setDone();
        }
    }

    DefaultChannelGroupFuture(ChannelGroup group, Map<Integer, ChannelFuture> futures) {
        this.group = group;
        this.futures = Collections.unmodifiableMap(futures);
        for (ChannelFuture f: this.futures.values()) {
            f.addListener(childListener);
        }

        // Done on arrival?
        if (this.futures.isEmpty()) {
            setDone();
        }
    }

    public ChannelGroup getGroup() {
        return group;
    }

    public ChannelFuture find(Integer channelId) {
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
        return !futures.isEmpty() && successCount != 0;
    }

    public synchronized boolean isPartialFailure() {
        return !futures.isEmpty() && failureCount != 0;
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
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }

        synchronized (this) {
            while (!done) {
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

    public boolean await(long timeout, TimeUnit unit)
            throws InterruptedException {
        return await0(unit.toNanos(timeout), true);
    }

    public boolean await(long timeoutMillis) throws InterruptedException {
        return await0(MILLISECONDS.toNanos(timeoutMillis), true);
    }

    public ChannelGroupFuture awaitUninterruptibly() {
        boolean interrupted = false;
        synchronized (this) {
            while (!done) {
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
                            this.wait(waitTime / 1000000, (int) (waitTime % 1000000));
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
        if (DeadLockProofWorker.PARENT.get() != null) {
            throw new IllegalStateException(
                    "await*() in I/O thread causes a dead lock or " +
                    "sudden performance drop. Use addListener() instead or " +
                    "call await*() from a different thread.");
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
        // This method doesn't need synchronization because:
        // 1) This method is always called after synchronized (this) block.
        //    Hence any listener list modification happens-before this method.
        // 2) This method is called only when 'done' is true.  Once 'done'
        //    becomes true, the listener list is never modified - see add/removeListener()
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
