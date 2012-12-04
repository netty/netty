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
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.util.ThreadNameDeterminer;
import org.jboss.netty.util.ThreadRenamingRunnable;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.Timer;
import org.jboss.netty.util.TimerTask;
import org.jboss.netty.util.internal.DeadLockProofWorker;

import java.io.IOException;
import java.net.ConnectException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.jboss.netty.channel.Channels.*;

/**
 * {@link Boss} implementation that handles the  connection attempts of clients
 */
public final class NioClientBoss implements Boss {

    private static final AtomicInteger nextId = new AtomicInteger();

    private final int id = nextId.incrementAndGet();

    static final InternalLogger logger =
            InternalLoggerFactory.getInstance(NioClientBoss.class);

    private volatile Selector selector;
    private volatile Thread thread;
    private boolean started;
    private final AtomicBoolean wakenUp = new AtomicBoolean();
    private final Object startStopLock = new Object();
    private final Queue<Runnable> taskQueue = new ConcurrentLinkedQueue<Runnable>();
    private final TimerTask wakeupTask = new TimerTask() {
        public void run(Timeout timeout) throws Exception {
            // This is needed to prevent a possible race that can lead to a NPE
            // when the selector is closed before this is run
            //
            // See https://github.com/netty/netty/issues/685
            Selector selector = NioClientBoss.this.selector;

            if (selector != null) {
                if (wakenUp.compareAndSet(false, true)) {
                    selector.wakeup();
                }
            }
        }
    };
    private final Executor bossExecutor;
    private final ThreadNameDeterminer determiner;
    private final Timer timer;

    NioClientBoss(Executor bossExecutor, Timer timer, ThreadNameDeterminer determiner) {
        this.bossExecutor = bossExecutor;
        this.determiner = determiner;
        this.timer = timer;
    }

    void register(NioClientSocketChannel channel) {
        Runnable registerTask = new RegisterTask(this, channel);
        Selector selector;

        synchronized (startStopLock) {
            if (!started) {
                // Open a selector if this worker didn't start yet.
                try {
                    this.selector = selector =  Selector.open();
                } catch (Throwable t) {
                    throw new ChannelException(
                            "Failed to create a selector.", t);
                }

                // Start the worker thread with the new Selector.
                boolean success = false;
                try {
                    DeadLockProofWorker.start(bossExecutor,
                            new ThreadRenamingRunnable(this,
                                    "New I/O client boss #" + id , determiner));

                    success = true;
                } finally {
                    if (!success) {
                        // Release the Selector if the execution fails.
                        try {
                            selector.close();
                        } catch (Throwable t) {
                            if (logger.isWarnEnabled()) {
                                logger.warn("Failed to close a selector.", t);
                            }
                        }
                        this.selector = selector = null;
                        // The method will return to the caller at this point.
                    }
                }
            } else {
                // Use the existing selector if this worker has been started.
                selector = this.selector;
            }

            assert selector != null && selector.isOpen();

            started = true;
            boolean offered = taskQueue.offer(registerTask);
            assert offered;
        }
        int timeout = channel.getConfig().getConnectTimeoutMillis();
        if (timeout > 0) {
            if (!channel.isConnected()) {
                channel.timoutTimer = timer.newTimeout(wakeupTask,
                        timeout, TimeUnit.MILLISECONDS);
            }
        }
        if (wakenUp.compareAndSet(false, true)) {
            selector.wakeup();
        }
    }

    public void run() {
        thread = Thread.currentThread();

        boolean shutdown = false;
        int selectReturnsImmediately = 0;

        Selector selector = this.selector;

        // use 80% of the timeout for measure
        final long minSelectTimeout = SelectorUtil.SELECT_TIMEOUT_NANOS * 80 / 100;
        boolean wakenupFromLoop = false;
        for (;;) {
            wakenUp.set(false);

            try {
                long beforeSelect = System.nanoTime();
                int selected = SelectorUtil.select(selector);
                if (SelectorUtil.EPOLL_BUG_WORKAROUND && selected == 0 && !wakenupFromLoop && !wakenUp.get()) {
                    long timeBlocked = System.nanoTime() - beforeSelect;

                    if (timeBlocked < minSelectTimeout) {
                        boolean notConnected = false;
                        // loop over all keys as the selector may was unblocked because of a closed channel
                        for (SelectionKey key: selector.keys()) {
                            SelectableChannel ch = key.channel();
                            try {
                                if (ch instanceof SocketChannel && !((SocketChannel) ch).isConnected()) {
                                    notConnected = true;
                                    // cancel the key just to be on the safe side
                                    key.cancel();
                                }
                            } catch (CancelledKeyException e) {
                                // ignore
                            }
                        }
                        if (notConnected) {
                            selectReturnsImmediately = 0;
                        } else {
                            // returned before the minSelectTimeout elapsed with nothing select.
                            // this may be the cause of the jdk epoll(..) bug, so increment the counter
                            // which we use later to see if its really the jdk bug.
                            selectReturnsImmediately ++;
                        }
                    } else {
                        selectReturnsImmediately = 0;
                    }

                    if (selectReturnsImmediately == 1024) {
                        // The selector returned immediately for 10 times in a row,
                        // so recreate one selector as it seems like we hit the
                        // famous epoll(..) jdk bug.
                        rebuildSelector();
                        selector = this.selector;
                        selectReturnsImmediately = 0;
                        wakenupFromLoop = false;
                        // try to select again
                        continue;
                    }
                } else {
                    // reset counter
                    selectReturnsImmediately = 0;
                }

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
                    wakenupFromLoop = true;
                    selector.wakeup();
                } else {
                    wakenupFromLoop = false;
                }
                processTaskQueue();
                selector = this.selector; // processTaskQueue() can call rebuildSelector()
                processSelectedKeys(selector.selectedKeys());

                // Handle connection timeout every 10 milliseconds approximately.
                long currentTimeNanos = System.nanoTime();
                processConnectTimeout(selector.keys(), currentTimeNanos);

                // Exit the loop when there's nothing to handle.
                // The shutdown flag is used to delay the shutdown of this
                // loop to avoid excessive Selector creation when
                // connection attempts are made in a one-by-one manner
                // instead of concurrent manner.
                if (selector.keys().isEmpty()) {
                    if (shutdown ||
                            bossExecutor instanceof ExecutorService && ((ExecutorService) bossExecutor).isShutdown()) {

                        synchronized (startStopLock) {
                            if (taskQueue.isEmpty() && selector.keys().isEmpty()) {
                                started = false;
                                try {
                                    selector.close();
                                } catch (IOException e) {
                                    if (logger.isWarnEnabled()) {
                                        logger.warn(
                                                "Failed to close a selector.", e);
                                    }
                                } finally {
                                    this.selector = null;
                                }
                                break;
                            } else {
                                shutdown = false;
                            }
                        }
                    } else {
                        // Give one more second.
                        shutdown = true;
                    }
                } else {
                    shutdown = false;
                }
            } catch (Throwable t) {
                if (logger.isWarnEnabled()) {
                    logger.warn(
                            "Unexpected exception in the selector loop.", t);
                }

                // Prevent possible consecutive immediate failures.
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // Ignore.
                }
            }
        }
    }

    private void processTaskQueue() {
        for (;;) {
            final Runnable task = taskQueue.poll();
            if (task == null) {
                break;
            }

            task.run();
        }
    }

    private static void processSelectedKeys(Set<SelectionKey> selectedKeys) {
        // check if the set is empty and if so just return to not create garbage by
        // creating a new Iterator every time even if there is nothing to process.
        // See https://github.com/netty/netty/issues/597
        if (selectedKeys.isEmpty()) {
            return;
        }
        for (Iterator<SelectionKey> i = selectedKeys.iterator(); i.hasNext();) {
            SelectionKey k = i.next();
            i.remove();

            if (!k.isValid()) {
                close(k);
                continue;
            }

            try {
                if (k.isConnectable()) {
                    connect(k);
                }
            } catch (Throwable t) {
                NioClientSocketChannel ch = (NioClientSocketChannel) k.attachment();
                ch.connectFuture.setFailure(t);
                fireExceptionCaught(ch, t);
                k.cancel(); // Some JDK implementations run into an infinite loop without this.
                ch.worker.close(ch, succeededFuture(ch));
            }
        }
    }

    private static void processConnectTimeout(Set<SelectionKey> keys, long currentTimeNanos) {
        ConnectException cause = null;
        for (SelectionKey k: keys) {
            if (!k.isValid()) {
                // Comment the close call again as it gave us major problems
                // with ClosedChannelExceptions.
                //
                // See:
                // * https://github.com/netty/netty/issues/142
                // * https://github.com/netty/netty/issues/138
                //
                // close(k);
                continue;
            }

            NioClientSocketChannel ch = (NioClientSocketChannel) k.attachment();
            if (ch.connectDeadlineNanos > 0 &&
                    currentTimeNanos >= ch.connectDeadlineNanos) {

                if (cause == null) {
                    cause = new ConnectException("connection timed out");
                }

                ch.connectFuture.setFailure(cause);
                fireExceptionCaught(ch, cause);
                ch.worker.close(ch, succeededFuture(ch));
            }
        }
    }

    private static void connect(SelectionKey k) throws IOException {
        NioClientSocketChannel ch = (NioClientSocketChannel) k.attachment();
        if (ch.channel.finishConnect()) {
            k.cancel();
            if (ch.timoutTimer != null) {
                ch.timoutTimer.cancel();
            }
            ch.worker.register(ch, ch.connectFuture);
        }
    }

    private static void close(SelectionKey k) {
        NioClientSocketChannel ch = (NioClientSocketChannel) k.attachment();
        ch.worker.close(ch, succeededFuture(ch));
    }

    public void rebuildSelector() {
        if (Thread.currentThread() != thread) {
            Selector selector = this.selector;
            if (selector != null) {
                taskQueue.add(new Runnable() {
                    public void run() {
                        rebuildSelector();
                    }
                });
                selector.wakeup();
            }
            return;
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

                        int interestOps = key.interestOps();
                        key.cancel();
                        key.channel().register(newSelector, interestOps, key.attachment());
                        nChannels ++;
                    } catch (Exception e) {
                        logger.warn("Failed to re-register a Channel to the new Selector,", e);
                        NioClientSocketChannel ch = (NioClientSocketChannel) key.attachment();
                        ch.worker.close(ch, succeededFuture(ch));
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

    private static final class RegisterTask implements Runnable {
        private final NioClientBoss boss;
        private final NioClientSocketChannel channel;

        RegisterTask(NioClientBoss boss, NioClientSocketChannel channel) {
            this.boss = boss;
            this.channel = channel;
        }

        public void run() {
            try {
                channel.channel.register(
                        boss.selector, SelectionKey.OP_CONNECT, channel);
            } catch (ClosedChannelException e) {
                channel.worker.close(channel, succeededFuture(channel));
            }

            int connectTimeout = channel.getConfig().getConnectTimeoutMillis();
            if (connectTimeout > 0) {
                channel.connectDeadlineNanos = System.nanoTime() + connectTimeout * 1000000L;
            }
        }
    }
}
