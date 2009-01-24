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
package org.jboss.netty.channel.socket.servlet;

import java.util.concurrent.Executor;
import java.net.URL;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelSink;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.SocketChannel;
import org.jboss.netty.channel.socket.servlet.ServletClientSocketPipelineSink;
import org.jboss.netty.channel.socket.servlet.ServletClientSocketChannel;
import org.jboss.netty.util.ExecutorShutdownUtil;

/**
 * A {@link org.jboss.netty.channel.socket.ClientSocketChannelFactory} which creates a client-side NIO-based
 * {@link org.jboss.netty.channel.socket.SocketChannel}.  It utilizes the non-blocking I/O mode which was
 * introduced with NIO to serve many number of concurrent connections
 * efficiently.
 *
 * <h3>How threads work</h3>
 * <p>
 * There are two types of threads in a {@link ServletClientSocketChannelFactory};
 * one is boss thread and the other is worker thread.
 *
 * <h4>Boss thread</h4>
 * <p>
 * One {@link ServletClientSocketChannelFactory} has one boss thread.  It makes
 * a connection attempt on request.  Once a connection attempt succeeds,
 * the boss thread passes the connected {@link org.jboss.netty.channel.Channel} to one of the worker
 * threads that the {@link ServletClientSocketChannelFactory} manages.
 *
 * <h4>Worker threads</h4>
 * <p>
 * One {@link ServletClientSocketChannelFactory} can have one or more worker
 * threads.  A worker thread performs non-blocking read and write for one or
 * more {@link org.jboss.netty.channel.Channel}s in a non-blocking mode.
 *
 * <h3>Life cycle of threads and graceful shutdown</h3>
 * <p>
 * All threads are acquired from the {@link java.util.concurrent.Executor}s which were specified
 * when a {@link ServletClientSocketChannelFactory} was created.  A boss thread is
 * acquired from the {@code bossExecutor}, and worker threads are acquired from
 * the {@code workerExecutor}.  Therefore, you should make sure the specified
 * {@link java.util.concurrent.Executor}s are able to lend the sufficient number of threads.
 * It is the best bet to specify {@linkplain java.util.concurrent.Executors#newCachedThreadPool() a cached thread pool}.
 * <p>
 * Both boss and worker threads are acquired lazily, and then released when
 * there's nothing left to process.  All the related resources such as
 * {@link java.nio.channels.Selector} are also released when the boss and worker threads are
 * released.  Therefore, to shut down a service gracefully, you should do the
 * following:
 *
 * <ol>
 * <li>close all channels created by the factory, and</li>
 * <li>call {@link #releaseExternalResources()}.</li>
 * </ol>
 *
 * Please make sure not to shut down the executor until all channels are
 * closed.  Otherwise, you will end up with a {@link java.util.concurrent.RejectedExecutionException}
 * and the related resources might not be released properly.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 * @apiviz.landmark
 */
public class ServletClientSocketChannelFactory implements ClientSocketChannelFactory {

    private final Executor workerExecutor;
    private final ChannelSink sink;
    private final URL url;

    /**
     *
     * @param workerExecutor
     */
    public ServletClientSocketChannelFactory(Executor workerExecutor, URL url) {
        this(url, workerExecutor, Runtime.getRuntime().availableProcessors());
    }

    /**
     * Creates a new instance.
     *
     *        the {@link java.util.concurrent.Executor} which will execute the boss thread
     * @param url
     * @param workerExecutor
     *        the {@link java.util.concurrent.Executor} which will execute the I/O worker threads
     * @param workerCount
     */
    public ServletClientSocketChannelFactory(
          URL url, Executor workerExecutor,
          int workerCount) {
        if (url == null) {
            throw new NullPointerException("Url is null");
        }
        this.url = url;
        if (workerExecutor == null) {
            throw new NullPointerException("workerExecutor");
        }
        if (workerCount <= 0) {
            throw new IllegalArgumentException(
                    "workerCount (" + workerCount + ") " +
                    "must be a positive integer.");
        }

        this.workerExecutor = workerExecutor;
        sink = new ServletClientSocketPipelineSink(workerExecutor);
    }

    public SocketChannel newChannel(ChannelPipeline pipeline) {
        return new ServletClientSocketChannel(this, pipeline, sink, url);
    }

    public void releaseExternalResources() {
        ExecutorShutdownUtil.shutdown( workerExecutor);
    }
}