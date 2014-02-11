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

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.SocketChannel;
import org.jboss.netty.util.ExternalResourceReleasable;
import org.jboss.netty.util.Timer;

/**
 * A {@link ClientSocketChannelFactory} which creates a client-side NIO-based
 * {@link SocketChannel}.  It utilizes the non-blocking I/O mode which was
 * introduced with NIO to serve many number of concurrent connections
 * efficiently.
 *
 * <h3>How threads work</h3>
 * <p>
 * There are two types of threads in a {@link NioClientSocketChannelFactory};
 * one is boss thread and the other is worker thread.
 *
 * <h4>Boss thread</h4>
 * <p>
 * One {@link NioClientSocketChannelFactory} has one boss thread.  It makes
 * a connection attempt on request.  Once a connection attempt succeeds,
 * the boss thread passes the connected {@link Channel} to one of the worker
 * threads that the {@link NioClientSocketChannelFactory} manages.
 *
 * <h4>Worker threads</h4>
 * <p>
 * One {@link NioClientSocketChannelFactory} can have one or more worker
 * threads.  A worker thread performs non-blocking read and write for one or
 * more {@link Channel}s in a non-blocking mode.
 *
 * <h3>Life cycle of threads and graceful shutdown</h3>
 * <p>
 * All threads are acquired from the {@link Executor}s which were specified
 * when a {@link NioClientSocketChannelFactory} was created.  A boss thread is
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
 * <li>close all channels created by the factory usually using
 *     {@link ChannelGroup#close()}, and</li>
 * <li>call {@link #releaseExternalResources()}.</li>
 * </ol>
 *
 * Please make sure not to shut down the executor until all channels are
 * closed.  Otherwise, you will end up with a {@link RejectedExecutionException}
 * and the related resources might not be released properly.
 *
 * @apiviz.landmark
 */
public class NioClientSocketChannelFactory implements ClientSocketChannelFactory {

    private static final int DEFAULT_BOSS_COUNT = 1;

    private final BossPool<NioClientBoss> bossPool;
    private final WorkerPool<NioWorker> workerPool;
    private final NioClientSocketPipelineSink sink;
    private boolean releasePools;

    /**
     * Creates a new {@link NioClientSocketChannelFactory} which uses {@link Executors#newCachedThreadPool()}
     * for the worker and boss executors.
     *
     * See {@link #NioClientSocketChannelFactory(Executor, Executor)}
     */
    public NioClientSocketChannelFactory() {
        this(Executors.newCachedThreadPool(), Executors.newCachedThreadPool());
        releasePools = true;
    }

    /**
     * Creates a new instance.  Calling this constructor is same with calling
     * {@link #NioClientSocketChannelFactory(Executor, Executor, int, int)} with
     * 1 and (2 * the number of available processors in the machine) for
     * <tt>bossCount</tt> and <tt>workerCount</tt> respectively.  The number of
     * available processors is obtained by {@link Runtime#availableProcessors()}.
     *
     * @param bossExecutor
     *        the {@link Executor} which will execute the boss thread
     * @param workerExecutor
     *        the {@link Executor} which will execute the worker threads
     */
    public NioClientSocketChannelFactory(
            Executor bossExecutor, Executor workerExecutor) {
        this(bossExecutor, workerExecutor, DEFAULT_BOSS_COUNT, SelectorUtil.DEFAULT_IO_THREADS);
    }

    /**
     * Creates a new instance.  Calling this constructor is same with calling
     * {@link #NioClientSocketChannelFactory(Executor, Executor, int, int)} with
     * 1 as <tt>bossCount</tt>.
     *
     * @param bossExecutor
     *        the {@link Executor} which will execute the boss thread
     * @param workerExecutor
     *        the {@link Executor} which will execute the worker threads
     * @param workerCount
     *        the maximum number of I/O worker threads
     */
    public NioClientSocketChannelFactory(
            Executor bossExecutor, Executor workerExecutor, int workerCount) {
        this(bossExecutor, workerExecutor, DEFAULT_BOSS_COUNT, workerCount);
    }

    /**
     * Creates a new instance.
     *
     * @param bossExecutor
     *        the {@link Executor} which will execute the boss thread
     * @param workerExecutor
     *        the {@link Executor} which will execute the worker threads
     * @param bossCount
     *        the maximum number of boss threads
     * @param workerCount
     *        the maximum number of I/O worker threads
     */
    public NioClientSocketChannelFactory(
            Executor bossExecutor, Executor workerExecutor,
            int bossCount, int workerCount) {
        this(bossExecutor, bossCount, new NioWorkerPool(workerExecutor, workerCount));
    }

    /**
     * Creates a new instance.
     *
     * @param bossExecutor
     *        the {@link Executor} which will execute the boss thread
     * @param bossCount
     *        the maximum number of boss threads
     * @param workerPool
     *        the {@link WorkerPool} to use to do the IO
     */
    public NioClientSocketChannelFactory(
            Executor bossExecutor, int bossCount,
            WorkerPool<NioWorker> workerPool) {
        this(new NioClientBossPool(bossExecutor, bossCount), workerPool);
    }

    /**
     * Creates a new instance.
     *
     * @param bossExecutor
     *        the {@link Executor} which will execute the boss thread
     * @param bossCount
     *        the maximum number of boss threads
     * @param workerPool
     *        the {@link WorkerPool} to use to do the IO
     * @param timer
     *        the {@link Timer} to use to handle the connection timeouts
     */
    public NioClientSocketChannelFactory(
            Executor bossExecutor, int bossCount,
            WorkerPool<NioWorker> workerPool, Timer timer) {
        this(new NioClientBossPool(bossExecutor, bossCount, timer, null), workerPool);
    }

    /**
     * Creates a new instance.
     *
     * @param bossPool
     *        the {@link BossPool} to use to handle the connects
     * @param workerPool
     *        the {@link WorkerPool} to use to do the IO
     */
    public NioClientSocketChannelFactory(
            BossPool<NioClientBoss> bossPool,
            WorkerPool<NioWorker> workerPool) {

        if (bossPool == null) {
            throw new NullPointerException("bossPool");
        }
        if (workerPool == null) {
            throw new NullPointerException("workerPool");
        }
        this.bossPool = bossPool;
        this.workerPool = workerPool;
        sink = new NioClientSocketPipelineSink(bossPool);
    }

    public SocketChannel newChannel(ChannelPipeline pipeline) {
        return new NioClientSocketChannel(this, pipeline, sink, workerPool.nextWorker());
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
}
