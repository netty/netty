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
package io.netty.channel.socket.nio;

import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelTaskScheduler;
import io.netty.channel.SingleThreadEventLoop;
import io.netty.channel.socket.nio.AbstractNioChannel.NioUnsafe;
import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;

import java.io.IOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@link SingleThreadEventLoop} implementation which register the {@link Channel}'s to a
 * {@link Selector} and so does the multi-plexing of these in the event loop.
 *
 */
final class NioEventLoop extends SingleThreadEventLoop {

    /**
     * Internal Netty logger.
     */
    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(NioEventLoop.class);

    static final int CLEANUP_INTERVAL = 256; // XXX Hard-coded value, but won't need customization.

    /**
     * The NIO {@link Selector}.
     */
    Selector selector;

    private final SelectorProvider provider;

    /**
     * Boolean that controls determines if a blocked Selector.select should
     * break out of its selection process. In our case we use a timeone for
     * the select method and the select method will block for that time unless
     * waken up.
     */
    private final AtomicBoolean wakenUp = new AtomicBoolean();

    private int cancelledKeys;
    private boolean cleanedCancelledKeys;

    NioEventLoop(
            NioEventLoopGroup parent, ThreadFactory threadFactory,
            ChannelTaskScheduler scheduler, SelectorProvider selectorProvider) {
        super(parent, threadFactory, scheduler);
        if (selectorProvider == null) {
            throw new NullPointerException("selectorProvider");
        }
        provider = selectorProvider;
        selector = openSelector();
    }

    private Selector openSelector() {
        try {
            return provider.openSelector();
        } catch (IOException e) {
            throw new ChannelException("failed to open a new selector", e);
        }
    }

    @Override
    protected Queue<Runnable> newTaskQueue() {
        // This event loop never calls takeTask()
        return new ConcurrentLinkedQueue<Runnable>();
    }

    // Create a new selector and "transfer" all channels from the old
    // selector to the new one
    private Selector recreateSelector() {
        Selector newSelector = openSelector();
        Selector selector = this.selector;
        this.selector = newSelector;

        // loop over all the keys that are registered with the old Selector
        // and register them with the new one
        for (SelectionKey key: selector.keys()) {
            SelectableChannel ch = key.channel();
            int ops = key.interestOps();
            Object att = key.attachment();
            // cancel the old key
            cancel(key);

            try {
                // register the channel with the new selector now
                ch.register(newSelector, ops, att);
            } catch (ClosedChannelException e) {
                // close channel
                AbstractNioChannel channel = (AbstractNioChannel) att;
                channel.unsafe().close(channel.unsafe().voidFuture());
            }
        }
        try {
            // time to close the old selector as everything else is registered to the new one
            selector.close();
        } catch (Throwable t) {
            logger.warn("Failed to close a selector.", t);
        }
        logger.warn("Recreated Selector because of possible jdk epoll(..) bug");
        return newSelector;
    }

    @Override
    protected void run() {
        Selector selector = this.selector;
        int selectReturnsImmediately = 0;

        // use 80% of the timeout for measure
        long minSelectTimeout = SelectorUtil.SELECT_TIMEOUT_NANOS / 100 * 80;

        for (;;) {

            wakenUp.set(false);

            try {
                long beforeSelect = System.nanoTime();
                int selected = SelectorUtil.select(selector);
                if (SelectorUtil.EPOLL_BUG_WORKAROUND) {
                    if (selected == 0) {
                        long timeBlocked = System.nanoTime()  - beforeSelect;
                        if (timeBlocked < minSelectTimeout) {
                            // returned before the minSelectTimeout elapsed with nothing select.
                            // this may be the cause of the jdk epoll(..) bug, so increment the counter
                            // which we use later to see if its really the jdk bug.
                            selectReturnsImmediately ++;
                        } else {
                            selectReturnsImmediately = 0;
                        }
                        if (selectReturnsImmediately == 10) {
                            // The selector returned immediately for 10 times in a row,
                            // so recreate one selector as it seems like we hit the
                            // famous epoll(..) jdk bug.
                            selector = recreateSelector();
                            selectReturnsImmediately = 0;

                            // try to select again
                            continue;
                        }
                    } else {
                        // reset counter
                        selectReturnsImmediately = 0;
                    }
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
                    selector.wakeup();
                }

                cancelledKeys = 0;
                runAllTasks();
                processSelectedKeys();

                if (isShutdown()) {
                    closeAll();
                    if (peekTask() == null) {
                        break;
                    }
                }
            } catch (Throwable t) {
                logger.warn(
                        "Unexpected exception in the selector loop.", t);

                // Prevent possible consecutive immediate failures that lead to
                // excessive CPU consumption.
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // Ignore.
                }
            }
        }
    }

    @Override
    protected void cleanup() {
        try {
            selector.close();
        } catch (IOException e) {
            logger.warn(
                    "Failed to close a selector.", e);
        }
    }

    void cancel(SelectionKey key) {
        key.cancel();
        cancelledKeys ++;
        if (cancelledKeys >= CLEANUP_INTERVAL) {
            cancelledKeys = 0;
            cleanedCancelledKeys = true;
            SelectorUtil.cleanupKeys(selector);
        }
    }

    private void processSelectedKeys() {
        Set<SelectionKey> selectedKeys = selector.selectedKeys();
        // check if the set is empty and if so just return to not create garbage by
        // creating a new Iterator every time even if there is nothing to process.
        // See https://github.com/netty/netty/issues/597
        if (selectedKeys.isEmpty()) {
            return;
        }

        Iterator<SelectionKey> i;
        cleanedCancelledKeys = false;
        boolean clearSelectedKeys = true;
        try {
            for (i = selectedKeys.iterator(); i.hasNext();) {
                final SelectionKey k = i.next();
                final AbstractNioChannel ch = (AbstractNioChannel) k.attachment();
                final NioUnsafe unsafe = ch.unsafe();
                try {
                    int readyOps = k.readyOps();
                    if ((readyOps & (SelectionKey.OP_READ | SelectionKey.OP_ACCEPT)) != 0 || readyOps == 0) {
                        unsafe.read();
                        if (!ch.isOpen()) {
                            // Connection already closed - no need to handle write.
                            continue;
                        }
                    }
                    if ((readyOps & SelectionKey.OP_WRITE) != 0) {
                        unsafe.flushNow();
                    }
                    if ((readyOps & SelectionKey.OP_CONNECT) != 0) {
                        unsafe.finishConnect();
                    }
                } catch (CancelledKeyException ignored) {
                    unsafe.close(unsafe.voidFuture());
                }

                if (cleanedCancelledKeys) {
                    // Create the iterator again to avoid ConcurrentModificationException
                    if (selectedKeys.isEmpty()) {
                        clearSelectedKeys = false;
                        break;
                    } else {
                        i = selectedKeys.iterator();
                    }
                }
            }
        } finally {
            if (clearSelectedKeys) {
                selectedKeys.clear();
            }
        }
    }

    private void closeAll() {
        SelectorUtil.cleanupKeys(selector);
        Set<SelectionKey> keys = selector.keys();
        Collection<Channel> channels = new ArrayList<Channel>(keys.size());
        for (SelectionKey k: keys) {
            channels.add((Channel) k.attachment());
        }

        for (Channel ch: channels) {
            ch.unsafe().close(ch.unsafe().voidFuture());
        }
    }

    @Override
    protected void wakeup(boolean inEventLoop) {
        if (wakenUp.compareAndSet(false, true)) {
            selector.wakeup();
        }
    }
}
