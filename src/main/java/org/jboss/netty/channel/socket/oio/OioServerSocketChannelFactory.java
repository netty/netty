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
package org.jboss.netty.channel.socket.oio;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelSink;
import org.jboss.netty.channel.socket.ServerSocketChannel;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;

/**
 * A {@link ServerSocketChannelFactory} which creates a server-side blocking
 * I/O based {@link ServerSocketChannel}.  It utilizes the good old blocking
 * I/O API which is known to yield better throughput and latency when there
 * are relatively small number of connections to serve.
 *
 * <h3>How threads work</h3>
 * <p>
 * There are two types of threads in a {@link OioServerSocketChannelFactory};
 * one is boss thread and the other is worker thread.
 *
 * <h4>Boss threads</h4>
 * <p>
 * Each bound {@link ServerSocketChannel} has its own boss thread.
 * For example, if you opened two server ports such as 80 and 443, you will
 * have two boss threads.  A boss thread accepts incoming connections until
 * the port is unbound.  Once a connection is accepted successfully, the boss
 * thread passes the accepted {@link Channel} to one of the worker
 * threads that the {@link OioServerSocketChannelFactory} manages.
 *
 * <h4>Worker threads</h4>
 * <p>
 * Each connected {@link Channel} has a dedicated worker thread, just like a
 * traditional blocking I/O thread model.
 *
 * <h3>Life cycle of threads and graceful shutdown</h3>
 * <p>
 * All threads are acquired from the {@link Executor}s which were specified
 * when a {@link OioServerSocketChannelFactory} is created.  Boss threads are
 * acquired from the {@code bossExecutor}, and worker threads are acquired from
 * the {@code workerExecutor}.  Therefore, you should make sure the specified
 * {@link Executor}s are able to lend the sufficient number of threads.
 * <p>
 * Both boss and worker threads are acquired lazily, and then released when
 * there's nothing left to process.  All the related resources are also
 * released when the boss and worker threads are released.  Therefore, to shut
 * down a service gracefully, you should do the following:
 *
 * <ol>
 * <li>unbind all channels created by the factory,
 * <li>close all child channels accepted by the unbound channels,</li>
 * <li>call {@link ExecutorService#shutdown()} for all executors which were
 *     specified to create the factory, and</li>
 * <li>call {@link ExecutorService#awaitTermination(long, TimeUnit)}
 *     until it returns {@code true}.</li>
 * </ol>
 *
 * Please make sure not to shut down the executor until all channels are
 * closed.  Otherwise, you will end up with a {@link RejectedExecutionException}
 * and the related resources might not be released properly.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 */
public class OioServerSocketChannelFactory implements ServerSocketChannelFactory {

    final Executor bossExecutor;
    private final ChannelSink sink;

    public OioServerSocketChannelFactory(
            Executor bossExecutor, Executor workerExecutor) {
        if (bossExecutor == null) {
            throw new NullPointerException("bossExecutor");
        }
        if (workerExecutor == null) {
            throw new NullPointerException("workerExecutor");
        }
        this.bossExecutor = bossExecutor;
        sink = new OioServerSocketPipelineSink(workerExecutor);
    }

    public ServerSocketChannel newChannel(ChannelPipeline pipeline) {
        return new OioServerSocketChannel(this, pipeline, sink);
    }
}
