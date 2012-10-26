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

import static org.jboss.netty.channel.Channels.*;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketAddress;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SocketChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelException;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelState;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.Timer;
import org.jboss.netty.util.TimerTask;
import org.jboss.netty.util.ThreadRenamingRunnable;
import org.jboss.netty.util.internal.DeadLockProofWorker;

class NioClientSocketPipelineSink extends AbstractNioChannelSink {

    private static final AtomicInteger nextId = new AtomicInteger();

    static final InternalLogger logger =
        InternalLoggerFactory.getInstance(NioClientSocketPipelineSink.class);

    final Executor bossExecutor;

    final int id = nextId.incrementAndGet();
    private final Boss[] bosses;

    private final AtomicInteger bossIndex = new AtomicInteger();

    private final WorkerPool<NioWorker> workerPool;

    private final Timer timer;

    NioClientSocketPipelineSink(
            Executor bossExecutor, int bossCount, WorkerPool<NioWorker> workerPool, Timer timer) {

        this.bossExecutor = bossExecutor;
        this.timer = timer;
        bosses = new Boss[bossCount];
        for (int i = 0; i < bosses.length; i ++) {
            bosses[i] = new Boss(i);
        }

        this.workerPool = workerPool;
    }

    public void eventSunk(
            ChannelPipeline pipeline, ChannelEvent e) throws Exception {
        if (e instanceof ChannelStateEvent) {
            ChannelStateEvent event = (ChannelStateEvent) e;
            NioClientSocketChannel channel =
                (NioClientSocketChannel) event.getChannel();
            ChannelFuture future = event.getFuture();
            ChannelState state = event.getState();
            Object value = event.getValue();

            switch (state) {
            case OPEN:
                if (Boolean.FALSE.equals(value)) {
                    channel.worker.close(channel, future);
                }
                break;
            case BOUND:
                if (value != null) {
                    bind(channel, future, (SocketAddress) value);
                } else {
                    channel.worker.close(channel, future);
                }
                break;
            case CONNECTED:
                if (value != null) {
                    connect(channel, future, (SocketAddress) value);
                } else {
                    channel.worker.close(channel, future);
                }
                break;
            case INTEREST_OPS:
                channel.worker.setInterestOps(channel, future, ((Integer) value).intValue());
                break;
            }
        } else if (e instanceof MessageEvent) {
            MessageEvent event = (MessageEvent) e;
            NioSocketChannel channel = (NioSocketChannel) event.getChannel();
            boolean offered = channel.writeBufferQueue.offer(event);
            assert offered;
            channel.worker.writeFromUserCode(channel);
        }
    }

    private static void bind(
            NioClientSocketChannel channel, ChannelFuture future,
            SocketAddress localAddress) {
        try {
            channel.channel.socket().bind(localAddress);
            channel.boundManually = true;
            channel.setBound();
            future.setSuccess();
            fireChannelBound(channel, channel.getLocalAddress());
        } catch (Throwable t) {
            future.setFailure(t);
            fireExceptionCaught(channel, t);
        }
    }

    private void connect(
            final NioClientSocketChannel channel, final ChannelFuture cf,
            SocketAddress remoteAddress) {
        try {
            if (channel.channel.connect(remoteAddress)) {
                channel.worker.register(channel, cf);
            } else {
                channel.getCloseFuture().addListener(new ChannelFutureListener() {
                    public void operationComplete(ChannelFuture f)
                            throws Exception {
                        if (!cf.isDone()) {
                            cf.setFailure(new ClosedChannelException());
                        }
                    }
                });
                cf.addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
                channel.connectFuture = cf;
                nextBoss().register(channel);
            }

        } catch (Throwable t) {
            cf.setFailure(t);
            fireExceptionCaught(channel, t);
            channel.worker.close(channel, succeededFuture(channel));
        }
    }

    Boss nextBoss() {
        return bosses[Math.abs(
                bossIndex.getAndIncrement() % bosses.length)];
    }

    NioWorker nextWorker() {
        return workerPool.nextWorker();
    }

    private final class Boss implements Runnable {

        volatile Selector selector;
        private boolean started;
        private final AtomicBoolean wakenUp = new AtomicBoolean();
        private final Object startStopLock = new Object();
        private final Queue<Runnable> registerTaskQueue = new ConcurrentLinkedQueue<Runnable>();
        private final int subId;
        private final TimerTask wakeupTask = new TimerTask() {
            public void run(Timeout timeout) throws Exception {
                // This is needed to prevent a possible race that can lead to a NPE
                // when the selector is closed before this is run
                //
                // See https://github.com/netty/netty/issues/685
                Selector selector = NioClientSocketPipelineSink.Boss.this.selector;

                if (selector != null) {
                    if (wakenUp.compareAndSet(false, true)) {
                        selector.wakeup();
                    }
                }
            }
        };

        Boss(int subId) {
            this.subId = subId;
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
                                        "New I/O client boss #" + id + '-' + subId));

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
                boolean offered = registerTaskQueue.offer(registerTask);
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
                            selector = recreateSelector();
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
                    processRegisterTaskQueue();
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
                                if (registerTaskQueue.isEmpty() && selector.keys().isEmpty()) {
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

        private void processRegisterTaskQueue() {
            for (;;) {
                final Runnable task = registerTaskQueue.poll();
                if (task == null) {
                    break;
                }

                task.run();
            }
        }

        private void processSelectedKeys(Set<SelectionKey> selectedKeys) {
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

        private void processConnectTimeout(Set<SelectionKey> keys, long currentTimeNanos) {
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

        private void connect(SelectionKey k) throws IOException {
            NioClientSocketChannel ch = (NioClientSocketChannel) k.attachment();
            if (ch.channel.finishConnect()) {
                k.cancel();
                if (ch.timoutTimer != null) {
                    ch.timoutTimer.cancel();
                }
                ch.worker.register(ch, ch.connectFuture);
            }
        }

        private void close(SelectionKey k) {
            NioClientSocketChannel ch = (NioClientSocketChannel) k.attachment();
            ch.worker.close(ch, succeededFuture(ch));
        }

        // Create a new selector and "transfer" all channels from the old
        // selector to the new one
        private Selector recreateSelector() throws IOException {
            Selector newSelector = Selector.open();
            Selector selector = this.selector;
            this.selector = newSelector;

            // loop over all the keys that are registered with the old Selector
            // and register them with the new one
            for (SelectionKey key: selector.keys()) {
                SelectableChannel ch = key.channel();
                int ops = key.interestOps();
                Object att = key.attachment();
                // cancel the old key
                key.cancel();

                try {
                    // register the channel with the new selector now
                    ch.register(newSelector, ops, att);
                } catch (ClosedChannelException e) {
                    // close the Channel if we can't register it
                    close(key);
                }
            }

            try {
                // time to close the old selector as everything else is registered to the new one
                selector.close();
            } catch (Throwable t) {
                if (logger.isWarnEnabled()) {
                    logger.warn("Failed to close a selector.", t);
                }
            }
            if (logger.isWarnEnabled()) {
                logger.warn("Recreated Selector because of possible jdk epoll(..) bug");
            }
            return newSelector;
        }

    }

    private static final class RegisterTask implements Runnable {
        private final Boss boss;
        private final NioClientSocketChannel channel;

        RegisterTask(Boss boss, NioClientSocketChannel channel) {
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
