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
package org.jboss.netty.channel.socket.nio;

import java.nio.channels.Selector;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.socket.ServerSocketChannel;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.jboss.netty.util.ExternalResourceReleasable;

/**
 * A {@link ServerSocketChannelFactory} which creates a server-side NIO-based
 * {@link ServerSocketChannel}.  It utilizes the non-blocking I/O mode which
 * was introduced with NIO to serve many number of concurrent connections
 * efficiently.
 *
 * <h3>How threads work</h3>
 * <p>
 * There are two types of threads in a {@link NioServerSocketChannelFactory};
 * one is boss thread and the other is worker thread.
 *
 * <h4>Boss threads</h4>
 * <p>
 * Each bound {@link ServerSocketChannel} has its own boss thread.
 * For example, if you opened two server ports such as 80 and 443, you will
 * have two boss threads.  A boss thread accepts incoming connections until
 * the port is unbound.  Once a connection is accepted successfully, the boss
 * thread passes the accepted {@link Channel} to one of the worker
 * threads that the {@link NioServerSocketChannelFactory} manages.
 *
 * <h4>Worker threads</h4>
 * <p>
 * One {@link NioServerSocketChannelFactory} can have one or more worker
 * threads.  A worker thread performs non-blocking read and write for one or
 * more {@link Channel}s in a non-blocking mode.
 *
 * <h3>Life cycle of threads and graceful shutdown</h3>
 * <p>
 * All threads are acquired from the {@link Executor}s which were specified
 * when a {@link NioServerSocketChannelFactory} was created.  Boss threads are
 * acquired from the {@code bossExecutor}, and worker threads are acquired from
 * the {@code workerExecutor}.  Therefore, you should make sure the specified
 * {@link Executor}s are able to lend the sufficient number of threads.
 * It is the best bet to specify {@linkplain Executors#newCachedThreadPool() a cached thread pool}.
 * <p>
 * Both boss and worker threads are acquired lazily, and then released when
 * there's nothing left to process.  All the related resources such as
 * {@link Selector} are also released when the boss and worker threads are
 * released.  Therefore, to shut down a service gracefully, you should do the
 * following:
 *
 * <ol>
 * <li>unbind all channels created by the factory,
 * <li>close all child channels accepted by the unbound channels, and
 *     (these two steps so far is usually done using {@link ChannelGroup#close()})</li>
 * <li>call {@link #releaseExternalResources()}.</li>
 * </ol>
 *
 * Please make sure not to shut down the executor until all channels are
 * closed.  Otherwise, you will end up with a {@link RejectedExecutionException}
 * and the related resources might not be released properly.
 *
 * @apiviz.landmark
 */
public class NioServerSocketChannelFactory implements ServerSocketChannelFactory {

    private final WorkerPool<NioWorker> workerPool;
    private final NioServerSocketPipelineSink sink;
    private final BossPool<NioServerBoss> bossPool;
    private boolean releasePools;

    /**
     * Create a new {@link NioServerSocketChannelFactory} using {@link Executors#newCachedThreadPool()}
     * for the boss and worker.
     *
     * See {@link #NioServerSocketChannelFactory(Executor, Executor)}
     */
    public NioServerSocketChannelFactory() {
        this(Executors.newCachedThreadPool(), Executors.newCachedThreadPool());
        releasePools = true;
    }

    /**
     * Creates a new instance.  Calling this constructor is same with calling
     * {@link #NioServerSocketChannelFactory(Executor, Executor, int)} with
     * the worker executor passed into {@link #getMaxThreads(Executor)}.
     *
     * @param bossExecutor
     *        the {@link Executor} which will execute the boss threads
     * @param workerExecutor
     *        the {@link Executor} which will execute the I/O worker threads
     */
    public NioServerSocketChannelFactory(
            Executor bossExecutor, Executor workerExecutor) {
        this(bossExecutor, workerExecutor, getMaxThreads(workerExecutor));
    }

    /**
     * Creates a new instance.
     *
     * @param bossExecutor
     *        the {@link Executor} which will execute the boss threads
     * @param workerExecutor
     *        the {@link Executor} which will execute the I/O worker threads
     * @param workerCount
     *        the maximum number of I/O worker threads
     */
    public NioServerSocketChannelFactory(
            Executor bossExecutor, Executor workerExecutor,
            int workerCount) {
        this(bossExecutor, 1, workerExecutor, workerCount);
    }

    /**
     * Create a new instance.
     *
     * @param bossExecutor
     *        the {@link Executor} which will execute the boss threads
     * @param bossCount
     *        the number of boss threads
     * @param workerExecutor
     *        the {@link Executor} which will execute the I/O worker threads
     * @param workerCount
     *        the maximum number of I/O worker threads
     */
    public NioServerSocketChannelFactory(
            Executor bossExecutor, int bossCount, Executor workerExecutor,
            int workerCount) {
        this(bossExecutor, bossCount, new NioWorkerPool(workerExecutor, workerCount));
    }

    /**
     * Creates a new instance.
     *
     * @param bossExecutor
     *        the {@link Executor} which will execute the boss threads
     * @param workerPool
     *        the {@link WorkerPool} which will be used to obtain the {@link NioWorker} that execute
     *        the I/O worker threads
     */
    public NioServerSocketChannelFactory(
            Executor bossExecutor, WorkerPool<NioWorker> workerPool) {
        this(bossExecutor, 1 , workerPool);
    }

    /**
     * Create a new instance.
     *
     * @param bossExecutor
     *        the {@link Executor} which will execute the boss threads
     * @param bossCount
     *        the number of boss threads
     * @param workerPool
     *        the {@link WorkerPool} which will be used to obtain the {@link NioWorker} that execute
     *        the I/O worker threads
     */
    public NioServerSocketChannelFactory(
            Executor bossExecutor, int bossCount, WorkerPool<NioWorker> workerPool) {
        this(new NioServerBossPool(bossExecutor, bossCount, null), workerPool);
    }

    /**
     * Create a new instance.
     *
     * @param bossPool
     *        the {@link BossPool} which will be used to obtain the {@link NioServerBoss} that execute
     *        the I/O for accept new connections
     * @param workerPool
     *        the {@link WorkerPool} which will be used to obtain the {@link NioWorker} that execute
     *        the I/O worker threads
     */
    public NioServerSocketChannelFactory(BossPool<NioServerBoss> bossPool, WorkerPool<NioWorker> workerPool) {
        if (bossPool == null) {
            throw new NullPointerException("bossExecutor");
        }
        if (workerPool == null) {
            throw new NullPointerException("workerPool");
        }
        this.bossPool = bossPool;
        this.workerPool = workerPool;
        sink = new NioServerSocketPipelineSink();
    }

    public ServerSocketChannel newChannel(ChannelPipeline pipeline) {
        return new NioServerSocketChannel(this, pipeline, sink, bossPool.nextBoss(), workerPool);
    }

    public void shutdown() {
        bossPool.shutdown();
        workerPool.shutdown();
        if (releasePools) {
            releasePools();
        }
    }

    public void releaseExternalResources() {
        bossPool.shutdown();
        workerPool.shutdown();
        releasePools();
    }

    private void releasePools() {
        if (bossPool instanceof ExternalResourceReleasable) {
            ((ExternalResourceReleasable) bossPool).releaseExternalResources();
        }
        if (workerPool instanceof ExternalResourceReleasable) {
            ((ExternalResourceReleasable) workerPool).releaseExternalResources();
        }
    }

    /**
     * Returns number of max threads for the {@link NioWorkerPool} to use. If
     * the * {@link Executor} is a {@link ThreadPoolExecutor}, check its
     * maximum * pool size and return either it's maximum or
     * {@link SelectorUtil#DEFAULT_IO_THREADS}, whichever is lower. Note that
     * {@link SelectorUtil#DEFAULT_IO_THREADS} is 2 * the number of available
     * processors in the machine.  The number of available processors is
     * obtained by {@link Runtime#availableProcessors()}.
     *
     * @param executor
     *        the {@link Executor} which will execute the I/O worker threads
     * @return
     *        number of maximum threads the NioWorkerPool should use
     */
    private static int getMaxThreads(Executor executor) {
        if (executor instanceof ThreadPoolExecutor) {
            final int maxThreads = ((ThreadPoolExecutor) executor).getMaximumPoolSize();
            return Math.min(maxThreads, SelectorUtil.DEFAULT_IO_THREADS);
        }
        return SelectorUtil.DEFAULT_IO_THREADS;
    }
}
