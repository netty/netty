/*
 * Copyright 2011 Red Hat, Inc.
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
package org.jboss.netty.util;

import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A {@link ThreadFactory} that creates a new {@link Thread} with the specified name and thread ID.
 * This class is useful when you want to customize the name of the I/O threads:
 * <pre>
 * {@link ChannelFactory} f = new {@link NioServerSocketChannelFactory}(
 *         {@link Executors}.{@link Executors#newCachedThreadPool(java.util.concurrent.ThreadFactory) newCachedThreadPool}(new {@link NamedThreadFactory}("myServerBoss-")),
 *         {@link Executors}.{@link Executors#newCachedThreadPool(java.util.concurrent.ThreadFactory) newCachedThreadPool}(new {@link NamedThreadFactory}("myServerWorker-")));
 * </pre>
 *
 * @author <a href="http://jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 */
public class NamedThreadFactory implements ThreadFactory {

    private final ThreadGroup group;
    private final AtomicInteger threadId = new AtomicInteger(1);
    private final String prefix;

    /**
     * Creates a new factory that creates a {@link Thread} with the specified name prefix.
     */
    public NamedThreadFactory(String prefix) {
        if (prefix == null) {
            throw new NullPointerException("prefix");
        }
        this.prefix = prefix;

        SecurityManager s = System.getSecurityManager();
        if (s != null) {
            group = s.getThreadGroup();
        } else {
            group = Thread.currentThread().getThreadGroup();
        }
    }

    /**
     * {@inheritDoc} The name of the thread is {@code "prefix + threadId"}. (e.g. {@code "ioThread-1"} if
     * {@code prefix} is {@code "ioThread-"}.
     */
    public Thread newThread(Runnable r) {
        Thread t = new Thread(group, r, prefix + threadId.getAndIncrement());
        if (t.isDaemon()) {
            t.setDaemon(false);
        }

        if (t.getPriority() != Thread.NORM_PRIORITY) {
            t.setPriority(Thread.NORM_PRIORITY);
        }
        return t;
    }
}
