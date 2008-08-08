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
package org.jboss.netty.handler.execution;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.jboss.netty.channel.Channel;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @author David M. Lloyd (david.lloyd@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 */
public class OrderedMemoryAwareThreadPoolExecutor extends
        MemoryAwareThreadPoolExecutor {

    private final ConcurrentMap<Channel, Executor> childExecutors =
        new ConcurrentHashMap<Channel, Executor>();

    public OrderedMemoryAwareThreadPoolExecutor(int corePoolSize,
            int maxChannelMemorySize, int maxTotalMemorySize,
            long keepAliveTime, TimeUnit unit,
            ObjectSizeEstimator objectSizeEstimator, ThreadFactory threadFactory) {
        super(corePoolSize, maxChannelMemorySize, maxTotalMemorySize,
                keepAliveTime, unit, objectSizeEstimator, threadFactory);
    }

    public OrderedMemoryAwareThreadPoolExecutor(int corePoolSize,
            int maxChannelMemorySize, int maxTotalMemorySize,
            long keepAliveTime, TimeUnit unit, ThreadFactory threadFactory) {
        super(corePoolSize, maxChannelMemorySize, maxTotalMemorySize,
                keepAliveTime, unit, threadFactory);
    }

    public OrderedMemoryAwareThreadPoolExecutor(int corePoolSize,
            int maxChannelMemorySize, int maxTotalMemorySize,
            long keepAliveTime, TimeUnit unit) {
        super(corePoolSize, maxChannelMemorySize, maxTotalMemorySize,
                keepAliveTime, unit);
    }

    public OrderedMemoryAwareThreadPoolExecutor(int corePoolSize,
            int maxChannelMemorySize, int maxTotalMemorySize) {
        super(corePoolSize, maxChannelMemorySize, maxTotalMemorySize);
    }

    @Override
    protected void doExecute(Runnable task) {
        if (!(task instanceof ChannelEventRunnable)) {
            doUnorderedExecute(task);
        } else {
            ChannelEventRunnable r = (ChannelEventRunnable) task;
            getOrderedExecutor(r.getEvent().getChannel()).execute(task);
        }
    }

    private Executor getOrderedExecutor(Channel channel) {
        Executor executor = childExecutors.get(channel);
        if (executor == null) {
            executor = new ChildExecutor();
            Executor oldExecutor = childExecutors.putIfAbsent(channel, executor);
            if (oldExecutor != null) {
                executor = oldExecutor;
            }
        }

        // Remove the entry when the channel closes.
        if (!channel.isOpen()) {
            childExecutors.remove(channel);
        }
        return executor;
    }

    private class ChildExecutor implements Executor, Runnable {
        private final Set<ChildExecutor> runningChildren = new HashSet<ChildExecutor>();
        private final Queue<Runnable> tasks = new LinkedList<Runnable>();

        ChildExecutor() {
            super();
        }

        public void execute(Runnable command) {
            synchronized (tasks) {
                tasks.add(command);
                if (tasks.size() == 1 && runningChildren.add(this)) {
                    doUnorderedExecute(this);
                }
            }
        }

        public void run() {
            for (;;) {
                final Runnable task;
                synchronized (tasks) {
                    task = tasks.poll();
                    if (task == null) {
                        runningChildren.remove(this);
                        return;
                    }
                }
                task.run();
            }
        }
    }
}
