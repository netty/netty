/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.channel.socket.oio;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.socket.DatagramChannel;
import org.jboss.netty.channel.socket.DatagramChannelFactory;
import org.jboss.netty.util.ThreadNameDeterminer;
import org.jboss.netty.util.internal.ExecutorUtil;

/**
 * A {@link DatagramChannelFactory} which creates a blocking I/O based
 * {@link DatagramChannel}.  It utilizes the good old blocking I/O API which
 * has support for multicast.
 *
 * <h3>How threads work</h3>
 * <p>
 * There is only one type of threads in {@link OioDatagramChannelFactory};
 * worker threads.
 *
 * <h4>Worker threads</h4>
 * <p>
 * Each {@link Channel} has a dedicated worker thread, just like a
 * traditional blocking I/O thread model.
 *
 * <h3>Life cycle of threads and graceful shutdown</h3>
 * <p>
 * Worker threads are acquired from the {@link Executor} which was specified
 * when a {@link OioDatagramChannelFactory} was created (i.e. {@code workerExecutor}.)
 * Therefore, you should make sure the specified {@link Executor} is able to
 * lend the sufficient number of threads.
 * <p>
 * Worker threads are acquired lazily, and then released when there's nothing
 * left to process.  All the related resources are also released when the
 * worker threads are released.  Therefore, to shut down a service gracefully,
 * you should do the following:
 *
 * <ol>
 * <li>close all channels created by the factory usually using
 *     {@link ChannelGroup#close()}, and</li>
 * <li>call {@link #releaseExternalResources()}.</li>
 * </ol>
 *
 * Please make sure not to shut down the executor until all channels are
 * closed.  Otherwise, you will end up with a {@link RejectedExecutionException}
 * and the related resources might not be released properly.
 *
 * <h3>Limitation</h3>
 * <p>
 * A {@link DatagramChannel} created by this factory does not support asynchronous
 * operations.  Any I/O requests such as {@code "write"} will be performed in a
 * blocking manner.
 *
 * @apiviz.landmark
 */
public class OioDatagramChannelFactory implements DatagramChannelFactory {

    private final Executor workerExecutor;
    final OioDatagramPipelineSink sink;
    private boolean shutdownExecutor;

    /**
     * Creates a new instance with a {@link Executors#newCachedThreadPool()}
     *
     * See {@link #OioDatagramChannelFactory(Executor)}
     */
    public OioDatagramChannelFactory() {
        this(Executors.newCachedThreadPool());
        shutdownExecutor = true;
    }

    /**
     * Creates a new instance.
     *
     * @param workerExecutor
     *        the {@link Executor} which will execute the I/O worker threads
     */
    public OioDatagramChannelFactory(Executor workerExecutor) {
        this(workerExecutor, null);
    }

    /**
     * Creates a new instance.
     *
     * @param workerExecutor
     *        the {@link Executor} which will execute the I/O worker threads
     * @param determiner
     *        the {@link ThreadNameDeterminer} to set the thread names.
     */
    public OioDatagramChannelFactory(Executor workerExecutor,
                                     ThreadNameDeterminer determiner) {
        if (workerExecutor == null) {
            throw new NullPointerException("workerExecutor");
        }
        this.workerExecutor = workerExecutor;
        sink = new OioDatagramPipelineSink(workerExecutor, determiner);
    }

    public DatagramChannel newChannel(ChannelPipeline pipeline) {
        return new OioDatagramChannel(this, pipeline, sink);
    }

    public void shutdown() {
        if (shutdownExecutor) {
            ExecutorUtil.shutdownNow(workerExecutor);
        }
    }

    public void releaseExternalResources() {
        ExecutorUtil.shutdownNow(workerExecutor);
    }
}
