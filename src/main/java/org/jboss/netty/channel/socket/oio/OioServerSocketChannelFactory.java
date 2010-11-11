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
package org.jboss.netty.channel.socket.oio;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelSink;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.socket.ServerSocketChannel;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.jboss.netty.util.internal.ExecutorUtil;

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
 * when a {@link OioServerSocketChannelFactory} was created.  Boss threads are
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
 * <li>close all child channels accepted by the unbound channels,
 *     (these two steps so far is usually done using {@link ChannelGroup#close()})</li>
 * <li>call {@link #releaseExternalResources()}.</li>
 * </ol>
 *
 * Please make sure not to shut down the executor until all channels are
 * closed.  Otherwise, you will end up with a {@link RejectedExecutionException}
 * and the related resources might not be released properly.
 *
 * <h3>Limitation</h3>
 * <p>
 * A {@link ServerSocketChannel} created by this factory and its child channels
 * do not support asynchronous operations.  Any I/O requests such as
 * {@code "write"} will be performed in a blocking manner.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 *
 * @apiviz.landmark
 */
public class OioServerSocketChannelFactory implements ServerSocketChannelFactory {

    final Executor bossExecutor;
    private final Executor workerExecutor;
    private final ChannelSink sink;

    /**
     * Creates a new instance.
     *
     * @param bossExecutor
     *        the {@link Executor} which will execute the boss threads
     * @param workerExecutor
     *        the {@link Executor} which will execute the I/O worker threads
     */
    public OioServerSocketChannelFactory(
            Executor bossExecutor, Executor workerExecutor) {
        if (bossExecutor == null) {
            throw new NullPointerException("bossExecutor");
        }
        if (workerExecutor == null) {
            throw new NullPointerException("workerExecutor");
        }
        this.bossExecutor = bossExecutor;
        this.workerExecutor = workerExecutor;
        sink = new OioServerSocketPipelineSink(workerExecutor);
    }

    public ServerSocketChannel newChannel(ChannelPipeline pipeline) {
        return new OioServerSocketChannel(this, pipeline, sink);
    }

    public void releaseExternalResources() {
        ExecutorUtil.terminate(bossExecutor, workerExecutor);
    }
}
