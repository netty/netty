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
package org.jboss.netty.channel.socket.nio;

import java.util.concurrent.Executor;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ServerChannelFactory;
import org.jboss.netty.util.internal.ExecutorUtil;

/**
 * A {@link NioDatagramChannelFactory} creates a server-side NIO-based
 * {@link  NioDatagramChannel}. It utilizes the non-blocking I/O mode which
 * was introduced with NIO to serve many number of concurrent connections
 * efficiently.
 *
 * <h3>How threads work</h3>
 * <p>
 * There is only one type of thread in a {@link NioDatagramChannelFactory},
 * as opposed to the {@link NioServerSocketChannelFactory} where there is one boss
 * thread and a worker thread.
 * The boss thread in {@link NioServerSocketChannelFactory} performs the accept of
 * incoming connection and then passes the accepted Channel to on of the worker
 * threads. As DatagramChannels can act as both server (listener) and client (sender)
 * hence there is no concept of ServerSocketChannel and SocketChannel whic means that
 * there nothing to accept accept. This is the reason that there is only worker theads.
 *
 * <h4>Worker threads</h4>
 * <p>
 * One {@link NioDatagramChannelFactory} can have one or more worker
 * threads.  A worker thread performs non-blocking read and write for one or
 * more {@link Channel}s in a non-blocking mode.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @author Daniel Bevenius (dbevenius@jboss.com)
 *
 * @version $Rev$, $Date$
 */
public class NioDatagramChannelFactory implements ChannelFactory,
        ServerChannelFactory {
    /**
     *
     */
    private final Executor workerExecutor;

    /**
     *
     */
    private final NioDatagramPipelineSink sink;

    /**
     *
     * @param workerExecutor the {@link Executor} which will execute the I/O worker threads
     */
    public NioDatagramChannelFactory(final Executor workerExecutor) {
        this(workerExecutor, Runtime.getRuntime().availableProcessors());
    }

    /**
     * Creates a new instance.
     *
     * @param workerExecutor the {@link Executor} which will execute the I/O worker threads
     * @param workerCount the maximum number of I/O worker threads
     */
    public NioDatagramChannelFactory(final Executor workerExecutor,
            final int workerCount) {
        if (workerCount <= 0) {
            throw new IllegalArgumentException(String
                    .format("workerCount (%s) must be a positive integer.",
                            workerCount));
        }

        if (workerExecutor == null) {
            throw new NullPointerException(
                    "workerExecutor argument must not be null");
        }
        this.workerExecutor = workerExecutor;

        sink = new NioDatagramPipelineSink(workerExecutor, workerCount);
    }

    public NioDatagramChannel newChannel(final ChannelPipeline pipeline) {
        return new NioDatagramChannel(this, pipeline, sink, sink.nextWorker());
    }

    public void releaseExternalResources() {
        ExecutorUtil.terminate(workerExecutor);
    }
}
