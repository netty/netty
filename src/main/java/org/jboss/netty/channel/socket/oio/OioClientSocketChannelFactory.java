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
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.SocketChannel;

/**
 * A {@link ClientSocketChannelFactory} which creates a client-side blocking
 * I/O based {@link SocketChannel}.  It utilizes the good old blocking I/O API
 * which is known to yield better throughput and latency when there are
 * relatively small number of connections to serve.
 *
 * <h3>How threads work</h3>
 * <p>
 * There is only one type of threads in {@link OioClientSocketChannelFactory};
 * worker threads.
 *
 * <h4>Worker threads</h4>
 * <p>
 * Each connected {@link Channel} has a dedicated worker thread, just like a
 * traditional blocking I/O thread model.
 *
 * <h3>Life cycle of threads and graceful shutdown</h3>
 * <p>
 * Worker threads are acquired from the {@link Executor} which was specified
 * when a {@link OioClientSocketChannelFactory} is created (i.e. {@code workerExecutor}.)
 * Therefore, you should make sure the specified {@link Executor} is able to
 * lend the sufficient number of threads.
 * <p>
 * Worker threads are acquired lazily, and then released when there's nothing
 * left to process.  All the related resources are also released when the
 * worker threads are released.  Therefore, to shut down a service gracefully,
 * you should do the following:
 *
 * <ol>
 * <li>close all channels created by the factory,</li>
 * <li>call {@link ExecutorService#shutdownNow()} for the executor which was
 *     specified to create the factory, and</li>
 * <li>call {@link ExecutorService#awaitTermination(long, TimeUnit)}
 *     until it returns {@code true}.</li>
 * </ol>
 *
 * Please make sure not to shut down the executor until all channels are
 * closed.  Otherwise, you will end up with a {@link RejectedExecutionException}
 * and the related resources might not be released properly.
 *
 * <h3>Limitation</h3>
 * <p>
 * A {@link SocketChannel} created by this factory doesn't support asynchronous
 * operations.  Any I/O requests such as {@code "connect"} and {@code "write"}
 * will be performed in a blocking manner.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 * @apiviz.landmark
 */
public class OioClientSocketChannelFactory implements ClientSocketChannelFactory {

    final OioClientSocketPipelineSink sink;

    /**
     * Creates a new instance.
     *
     * @param workerExecutor
     *        the {@link Executor} which will execute the I/O worker threads
     */
    public OioClientSocketChannelFactory(Executor workerExecutor) {
        if (workerExecutor == null) {
            throw new NullPointerException("workerExecutor");
        }
        sink = new OioClientSocketPipelineSink(workerExecutor);
    }

    public SocketChannel newChannel(ChannelPipeline pipeline) {
        return new OioClientSocketChannel(this, pipeline, sink);
    }
}
