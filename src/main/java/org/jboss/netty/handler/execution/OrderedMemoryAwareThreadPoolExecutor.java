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
package org.jboss.netty.handler.execution;

import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelState;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.util.ObjectSizeEstimator;
import org.jboss.netty.util.internal.ConcurrentIdentityWeakKeyHashMap;

/**
 * A {@link MemoryAwareThreadPoolExecutor} which makes sure the events from the
 * same {@link Channel} are executed sequentially.
 * <p>
 * <b>NOTE</b>: This thread pool inherits most characteristics of its super
 * type, so please make sure to refer to {@link MemoryAwareThreadPoolExecutor}
 * to understand how it works basically.
 *
 * <h3>Event execution order</h3>
 *
 * For example, let's say there are two executor threads that handle the events
 * from the two channels:
 * <pre>
 *           -------------------------------------&gt; Timeline ------------------------------------&gt;
 *
 * Thread X: --- Channel A (Event A1) --.   .-- Channel B (Event B2) --- Channel B (Event B3) ---&gt;
 *                                      \ /
 *                                       X
 *                                      / \
 * Thread Y: --- Channel B (Event B1) --'   '-- Channel A (Event A2) --- Channel A (Event A3) ---&gt;
 * </pre>
 * As you see, the events from different channels are independent from each
 * other.  That is, an event of Channel B will not be blocked by an event of
 * Channel A and vice versa, unless the thread pool is exhausted.
 * <p>
 * Also, it is guaranteed that the invocation will be made sequentially for the
 * events from the same channel.  For example, the event A2 is never executed
 * before the event A1 is finished.  (Although not recommended, if you want the
 * events from the same channel to be executed simultaneously, please use
 * {@link MemoryAwareThreadPoolExecutor} instead.)
 * <p>
 * However, it is not guaranteed that the invocation will be made by the same
 * thread for the same channel.  The events from the same channel can be
 * executed by different threads.  For example, the Event A2 is executed by the
 * thread Y while the event A1 was executed by the thread X.
 *
 * <h3>Using a different key other than {@link Channel} to maintain event order</h3>
 * <p>
 * {@link OrderedMemoryAwareThreadPoolExecutor} uses a {@link Channel} as a key
 * that is used for maintaining the event execution order, as explained in the
 * previous section.  Alternatively, you can extend it to change its behavior.
 * For example, you can change the key to the remote IP of the peer:
 *
 * <pre>
 * public class RemoteAddressBasedOMATPE extends {@link OrderedMemoryAwareThreadPoolExecutor} {
 *
 *     ... Constructors ...
 *
 *     {@code @Override}
 *     protected ConcurrentMap&lt;Object, Executor&gt; newChildExecutorMap() {
 *         // The default implementation returns a special ConcurrentMap that
 *         // uses identity comparison only (see {@link IdentityHashMap}).
 *         // Because SocketAddress does not work with identity comparison,
 *         // we need to employ more generic implementation.
 *         return new ConcurrentHashMap&lt;Object, Executor&gt;
 *     }
 *
 *     protected Object getChildExecutorKey({@link ChannelEvent} e) {
 *         // Use the IP of the remote peer as a key.
 *         return ((InetSocketAddress) e.getChannel().getRemoteAddress()).getAddress();
 *     }
 *
 *     // Make public so that you can call from anywhere.
 *     public boolean removeChildExecutor(Object key) {
 *         super.removeChildExecutor(key);
 *     }
 * }
 * </pre>
 *
 * Please be very careful of memory leak of the child executor map.  You must
 * call {@link #removeChildExecutor(Object)} when the life cycle of the key
 * ends (e.g. all connections from the same IP were closed.)  Also, please
 * keep in mind that the key can appear again after calling {@link #removeChildExecutor(Object)}
 * (e.g. a new connection could come in from the same old IP after removal.)
 * If in doubt, prune the old unused or stall keys from the child executor map
 * periodically:
 *
 * <pre>
 * RemoteAddressBasedOMATPE executor = ...;
 *
 * on every 3 seconds:
 *
 *   for (Iterator&lt;Object&gt; i = executor.getChildExecutorKeySet().iterator; i.hasNext();) {
 *       InetAddress ip = (InetAddress) i.next();
 *       if (there is no active connection from 'ip' now &&
 *           there has been no incoming connection from 'ip' for last 10 minutes) {
 *           i.remove();
 *       }
 *   }
 * </pre>
 *
 * If the expected maximum number of keys is small and deterministic, you could
 * use a weak key map such as <a href="http://viewvc.jboss.org/cgi-bin/viewvc.cgi/jbosscache/experimental/jsr166/src/jsr166y/ConcurrentWeakHashMap.java?view=markup">ConcurrentWeakHashMap</a>
 * or synchronized {@link WeakHashMap} instead of managing the life cycle of the
 * keys by yourself.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @author David M. Lloyd (david.lloyd@redhat.com)
 *
 * @version $Rev: 2308 $, $Date: 2010-06-17 23:23:59 +0900 (Thu, 17 Jun 2010) $
 *
 * @apiviz.landmark
 */
public class OrderedMemoryAwareThreadPoolExecutor extends
        MemoryAwareThreadPoolExecutor {

    // TODO Make OMATPE focus on the case where Channel is the key.
    //      Add a new less-efficient TPE that allows custom key.

    private final ConcurrentMap<Object, Executor> childExecutors = newChildExecutorMap();

    /**
     * Creates a new instance.
     *
     * @param corePoolSize          the maximum number of active threads
     * @param maxChannelMemorySize  the maximum total size of the queued events per channel.
     *                              Specify {@code 0} to disable.
     * @param maxTotalMemorySize    the maximum total size of the queued events for this pool
     *                              Specify {@code 0} to disable.
     */
    public OrderedMemoryAwareThreadPoolExecutor(
            int corePoolSize, long maxChannelMemorySize, long maxTotalMemorySize) {
        super(corePoolSize, maxChannelMemorySize, maxTotalMemorySize);
    }

    /**
     * Creates a new instance.
     *
     * @param corePoolSize          the maximum number of active threads
     * @param maxChannelMemorySize  the maximum total size of the queued events per channel.
     *                              Specify {@code 0} to disable.
     * @param maxTotalMemorySize    the maximum total size of the queued events for this pool
     *                              Specify {@code 0} to disable.
     * @param keepAliveTime         the amount of time for an inactive thread to shut itself down
     * @param unit                  the {@link TimeUnit} of {@code keepAliveTime}
     */
    public OrderedMemoryAwareThreadPoolExecutor(
            int corePoolSize, long maxChannelMemorySize, long maxTotalMemorySize,
            long keepAliveTime, TimeUnit unit) {
        super(corePoolSize, maxChannelMemorySize, maxTotalMemorySize,
                keepAliveTime, unit);
    }

    /**
     * Creates a new instance.
     *
     * @param corePoolSize          the maximum number of active threads
     * @param maxChannelMemorySize  the maximum total size of the queued events per channel.
     *                              Specify {@code 0} to disable.
     * @param maxTotalMemorySize    the maximum total size of the queued events for this pool
     *                              Specify {@code 0} to disable.
     * @param keepAliveTime         the amount of time for an inactive thread to shut itself down
     * @param unit                  the {@link TimeUnit} of {@code keepAliveTime}
     * @param threadFactory         the {@link ThreadFactory} of this pool
     */
    public OrderedMemoryAwareThreadPoolExecutor(
            int corePoolSize, long maxChannelMemorySize, long maxTotalMemorySize,
            long keepAliveTime, TimeUnit unit, ThreadFactory threadFactory) {
        super(corePoolSize, maxChannelMemorySize, maxTotalMemorySize,
                keepAliveTime, unit, threadFactory);
    }

    /**
     * Creates a new instance.
     *
     * @param corePoolSize          the maximum number of active threads
     * @param maxChannelMemorySize  the maximum total size of the queued events per channel.
     *                              Specify {@code 0} to disable.
     * @param maxTotalMemorySize    the maximum total size of the queued events for this pool
     *                              Specify {@code 0} to disable.
     * @param keepAliveTime         the amount of time for an inactive thread to shut itself down
     * @param unit                  the {@link TimeUnit} of {@code keepAliveTime}
     * @param threadFactory         the {@link ThreadFactory} of this pool
     * @param objectSizeEstimator   the {@link ObjectSizeEstimator} of this pool
     */
    public OrderedMemoryAwareThreadPoolExecutor(
            int corePoolSize, long maxChannelMemorySize, long maxTotalMemorySize,
            long keepAliveTime, TimeUnit unit,
            ObjectSizeEstimator objectSizeEstimator, ThreadFactory threadFactory) {
        super(corePoolSize, maxChannelMemorySize, maxTotalMemorySize,
                keepAliveTime, unit, objectSizeEstimator, threadFactory);
    }

    protected ConcurrentMap<Object, Executor> newChildExecutorMap() {
        return new ConcurrentIdentityWeakKeyHashMap<Object, Executor>();
    }

    protected Object getChildExecutorKey(ChannelEvent e) {
        return e.getChannel();
    }

    protected Set<Object> getChildExecutorKeySet() {
        return childExecutors.keySet();
    }

    protected boolean removeChildExecutor(Object key) {
        // FIXME: Succeed only when there is no task in the ChildExecutor's queue.
        //        Note that it will need locking which might slow down task submission.
        return childExecutors.remove(key) != null;
    }

    /**
     * Executes the specified task concurrently while maintaining the event
     * order.
     */
    @Override
    protected void doExecute(Runnable task) {
        if (!(task instanceof ChannelEventRunnable)) {
            doUnorderedExecute(task);
        } else {
            ChannelEventRunnable r = (ChannelEventRunnable) task;
            getChildExecutor(r.getEvent()).execute(task);
        }
    }

    private Executor getChildExecutor(ChannelEvent e) {
        Object key = getChildExecutorKey(e);
        Executor executor = childExecutors.get(key);
        if (executor == null) {
            executor = new ChildExecutor();
            Executor oldExecutor = childExecutors.putIfAbsent(key, executor);
            if (oldExecutor != null) {
                executor = oldExecutor;
            }
        }

        // Remove the entry when the channel closes.
        if (e instanceof ChannelStateEvent) {
            Channel channel = e.getChannel();
            ChannelStateEvent se = (ChannelStateEvent) e;
            if (se.getState() == ChannelState.OPEN &&
                !channel.isOpen()) {
                childExecutors.remove(channel);
            }
        }
        return executor;
    }

    @Override
    protected boolean shouldCount(Runnable task) {
        if (task instanceof ChildExecutor) {
            return false;
        }

        return super.shouldCount(task);
    }

    void onAfterExecute(Runnable r, Throwable t) {
        afterExecute(r, t);
    }

    private final class ChildExecutor implements Executor, Runnable {
        private final LinkedList<Runnable> tasks = new LinkedList<Runnable>();

        ChildExecutor() {
            super();
        }

        public void execute(Runnable command) {
            boolean needsExecution;
            synchronized (tasks) {
                needsExecution = tasks.isEmpty();
                tasks.add(command);
            }

            if (needsExecution) {
                doUnorderedExecute(this);
            }
        }

        public void run() {
            Thread thread = Thread.currentThread();
            for (;;) {
                final Runnable task;
                synchronized (tasks) {
                    task = tasks.getFirst();
                }

                boolean ran = false;
                beforeExecute(thread, task);
                try {
                    task.run();
                    ran = true;
                    onAfterExecute(task, null);
                } catch (RuntimeException e) {
                    if (!ran) {
                        onAfterExecute(task, e);
                    }
                    throw e;
                } finally {
                    synchronized (tasks) {
                        tasks.removeFirst();
                        if (tasks.isEmpty()) {
                            break;
                        }
                    }
                }
            }
        }
    }
}
