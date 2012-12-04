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

import org.jboss.netty.channel.ChannelException;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelSink;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.util.ThreadNameDeterminer;
import org.jboss.netty.util.ThreadRenamingRunnable;
import org.jboss.netty.util.internal.DeadLockProofWorker;

import java.io.IOException;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.jboss.netty.channel.Channels.*;

/**
 * Boss implementation which handles accepting of new connections
 */
public final class NioServerBoss implements Boss {

    private static final AtomicInteger nextId = new AtomicInteger();

    static final InternalLogger logger =
            InternalLoggerFactory.getInstance(NioServerBoss.class);

    private final int id = nextId.incrementAndGet();

    private volatile Selector selector;
    private volatile Thread thread;

    private final Executor bossExecutor;
    /**
     * Queue of channel registration tasks.
     */
    private final Queue<Runnable> taskQueue = new ConcurrentLinkedQueue<Runnable>();

    /**
     * Monitor object used to synchronize selector open/close.
     */
    private final Object startStopLock = new Object();

    /**
     * Boolean that controls determines if a blocked Selector.select should
     * break out of its selection process. In our case we use a timeone for
     * the select method and the select method will block for that time unless
     * waken up.
     */
    private final AtomicBoolean wakenUp = new AtomicBoolean();

    private volatile int cancelledKeys; // should use AtomicInteger but we just need approximation
    static final int CLEANUP_INTERVAL = 256; // XXX Hard-coded value, but won't need customization.

    NioServerBoss(Executor bossExecutor) {
        this(bossExecutor, null);
    }

    NioServerBoss(Executor bossExecutor, ThreadNameDeterminer determiner) {
        this.bossExecutor = bossExecutor;
        openSelector(determiner);
    }

    void bind(final NioServerSocketChannel channel, final ChannelFuture future,
              final SocketAddress localAddress) {
        synchronized (startStopLock) {
            if (selector == null) {
                // the selector was null this means the Worker has already been shutdown.
                throw new RejectedExecutionException("Worker has already been shutdown");
            }

            boolean offered = taskQueue.offer(new Runnable() {
                public void run() {
                    boolean bound = false;
                    boolean registered = false;
                    try {
                        channel.socket.socket().bind(localAddress, channel.getConfig().getBacklog());
                        bound = true;

                        future.setSuccess();
                        fireChannelBound(channel, channel.getLocalAddress());
                        channel.socket.register(selector, SelectionKey.OP_ACCEPT, channel);

                        registered = true;
                    } catch (Throwable t) {
                        future.setFailure(t);
                        fireExceptionCaught(channel, t);
                    } finally {
                        if (!registered && bound) {
                            close(channel, future);
                        }
                    }
                }
            });
            assert offered;

            if (wakenUp.compareAndSet(false, true)) {
                selector.wakeup();
            }
        }
    }

    void close(NioServerSocketChannel channel, ChannelFuture future) {
        boolean bound = channel.isBound();

        try {
            channel.socket.close();
            cancelledKeys ++;

            if (channel.setClosed()) {
                future.setSuccess();

                if (bound) {
                    fireChannelUnbound(channel);
                }
                fireChannelClosed(channel);
            } else {
                future.setSuccess();
            }
        } catch (Throwable t) {
            future.setFailure(t);
            fireExceptionCaught(channel, t);
        }
    }

    private void openSelector(ThreadNameDeterminer determiner) {
        try {
            selector = Selector.open();
        } catch (Throwable t) {
            throw new ChannelException("Failed to create a selector.", t);
        }

        // Start the worker thread with the new Selector.
        boolean success = false;
        try {
            DeadLockProofWorker.start(bossExecutor, new ThreadRenamingRunnable(this,
                    "New I/O server boss #" + id, determiner));
            success = true;
        } finally {
            if (!success) {
                // Release the Selector if the execution fails.
                try {
                    selector.close();
                } catch (Throwable t) {
                    logger.warn("Failed to close a selector.", t);
                }
                selector = null;
                // The method will return to the caller at this point.
            }
        }
        assert selector != null && selector.isOpen();
    }

    public void run() {
        thread = Thread.currentThread();
        boolean shutdown = false;
        for (;;) {
            wakenUp.set(false);

            try {
                // Just do a blocking select without any timeout
                // as this thread does not execute anything else.
                selector.select();

                // 'wakenUp.compareAndSet(false, true)' is always evaluated
                // before calling 'selector.wakeup()' to reduce the wake-up
                // overhead. (Selector.wakeup() is an expensive operation.)
                //
                // However, there is a race condition in this approach.
                // The race condition is triggered when 'wakenUp' is set to
                // true too early.
                //
                // 'wakenUp' is set to true too early if:
                // 1) Selector is waken up between 'wakenUp.set(false)' and
                //    'selector.select(...)'. (BAD)
                // 2) Selector is waken up between 'selector.select(...)' and
                //    'if (wakenUp.get()) { ... }'. (OK)
                //
                // In the first case, 'wakenUp' is set to true and the
                // following 'selector.select(...)' will wake up immediately.
                // Until 'wakenUp' is set to false again in the next round,
                // 'wakenUp.compareAndSet(false, true)' will fail, and therefore
                // any attempt to wake up the Selector will fail, too, causing
                // the following 'selector.select(...)' call to block
                // unnecessarily.
                //
                // To fix this problem, we wake up the selector again if wakenUp
                // is true immediately after selector.select(...).
                // It is inefficient in that it wakes up the selector for both
                // the first case (BAD - wake-up required) and the second case
                // (OK - no wake-up required).
                if (wakenUp.get()) {
                    selector.wakeup();
                }
                processTaskQueue();
                processSelectedKeys(selector.selectedKeys());

                // Exit the loop when there's nothing to handle.
                // The shutdown flag is used to delay the shutdown of this
                // loop to avoid excessive Selector creation when
                // connections are registered in a one-by-one manner instead of
                // concurrent manner.
                if (selector.keys().isEmpty()) {
                    if (shutdown || bossExecutor instanceof ExecutorService &&
                            ((ExecutorService) bossExecutor).isShutdown()) {

                        synchronized (startStopLock) {
                            if (selector.keys().isEmpty()) {
                                try {
                                    selector.close();
                                } catch (IOException e) {
                                    logger.warn(
                                            "Failed to close a selector.", e);
                                } finally {
                                    selector = null;
                                }
                                break;
                            } else {
                                shutdown = false;
                            }
                        }
                    }
                } else {
                    shutdown = false;
                }
            } catch (Throwable e) {
                if (logger.isWarnEnabled()) {
                    logger.warn(
                            "Failed to accept a connection.", e);
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e1) {
                    // Ignore
                }
            }
        }
    }

    private void processTaskQueue() throws IOException {
        for (;;) {
            final Runnable task = taskQueue.poll();
            if (task == null) {
                break;
            }

            task.run();
            cleanUpCancelledKeys();
        }
    }
    private boolean cleanUpCancelledKeys() throws IOException {
        if (cancelledKeys >= CLEANUP_INTERVAL) {
            cancelledKeys = 0;
            selector.selectNow();
            return true;
        }
        return false;
    }

    private void processSelectedKeys(Set<SelectionKey> selectedKeys) {
        if (selectedKeys.isEmpty()) {
            return;
        }
        for (Iterator<SelectionKey> i = selectedKeys.iterator(); i.hasNext();) {
            SelectionKey k = i.next();
            i.remove();
            NioServerSocketChannel channel = (NioServerSocketChannel) k.attachment();

            try {
                // accept connections in a for loop until no new connection is ready
                for (;;) {
                    SocketChannel acceptedSocket = channel.socket.accept();
                    if (acceptedSocket == null) {
                        break;
                    }
                    registerAcceptedChannel(channel, acceptedSocket, thread);
                }
            } catch (CancelledKeyException e) {
                // Raised by accept() when the server socket was closed.
                k.cancel();
                channel.close();
            } catch (SocketTimeoutException e) {
                // Thrown every second to get ClosedChannelException
                // raised.
            } catch (ClosedChannelException e) {
                // Closed as requested.
            } catch (Throwable t) {
                if (logger.isWarnEnabled()) {
                    logger.warn(
                            "Failed to accept a connection.", t);
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e1) {
                    // Ignore
                }
            }
        }
    }

    private static void registerAcceptedChannel(NioServerSocketChannel parent, SocketChannel acceptedSocket,
                                         Thread currentThread) {
        try {
            ChannelSink sink = parent.getPipeline().getSink();
            ChannelPipeline pipeline =
                    parent.getConfig().getPipelineFactory().getPipeline();
            NioWorker worker = parent.workerPool.nextWorker();
            worker.register(new NioAcceptedSocketChannel(
                    parent.getFactory(), pipeline, parent, sink
                    , acceptedSocket,
                    worker, currentThread), null);
        } catch (Exception e) {
            if (logger.isWarnEnabled()) {
                logger.warn(
                        "Failed to initialize an accepted socket.", e);
            }

            try {
                acceptedSocket.close();
            } catch (IOException e2) {
                if (logger.isWarnEnabled()) {
                    logger.warn(
                            "Failed to close a partially accepted socket.",
                            e2);
                }
            }
        }
    }

    public void rebuildSelector() {
        if (Thread.currentThread() != thread) {
            Selector selector = this.selector;
            if (selector == null) {
                return;
            }

            taskQueue.add(new Runnable() {
                public void run() {
                    rebuildSelector();
                }
            });
            selector.wakeup();
        }

        final Selector oldSelector = selector;
        final Selector newSelector;

        if (oldSelector == null) {
            return;
        }

        try {
            newSelector = Selector.open();
        } catch (Exception e) {
            logger.warn("Failed to create a new Selector.", e);
            return;
        }

        // Register all channels to the new Selector.
        int nChannels = 0;
        for (;;) {
            try {
                for (SelectionKey key: oldSelector.keys()) {
                    try {
                        if (key.channel().keyFor(newSelector) != null) {
                            continue;
                        }

                        key.cancel();
                        key.channel().register(newSelector, key.interestOps(), key.attachment());
                        nChannels ++;
                    } catch (Exception e) {
                        logger.warn("Failed to re-register a Channel to the new Selector,", e);
                        NioServerSocketChannel ch = (NioServerSocketChannel) key.attachment();
                        close(ch, succeededFuture(ch));
                    }
                }
            } catch (ConcurrentModificationException e) {
                // Probably due to concurrent modification of the key set.
                continue;
            }

            break;
        }

        selector = newSelector;

        try {
            // time to close the old selector as everything else is registered to the new one
            oldSelector.close();
        } catch (Throwable t) {
            if (logger.isWarnEnabled()) {
                logger.warn("Failed to close the old Selector.", t);
            }
        }

        logger.info("Migrated " + nChannels + " channel(s) to the new Selector,");
    }
}
