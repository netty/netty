/*
 * Copyright 2024 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.nio;

import io.netty.channel.Channel;
import io.netty.channel.EventLoopTaskQueueFactory;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.IoRegistration;
import io.netty.channel.SingleThreadIoEventLoop;
import io.netty.util.concurrent.RejectedExecutionHandler;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * {@link SingleThreadIoEventLoop} implementation which register the {@link Channel}'s to a
 * {@link Selector} and so does the multi-plexing of these in the event loop.
 *
 * @deprecated Use {@link SingleThreadIoEventLoop} with {@link NioIoHandler}
 */
@Deprecated
public final class NioEventLoop extends SingleThreadIoEventLoop {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(NioEventLoop.class);

    NioEventLoop(NioEventLoopGroup parent, Executor executor, IoHandlerFactory ioHandlerFactory,
                 EventLoopTaskQueueFactory taskQueueFactory,
                 EventLoopTaskQueueFactory tailTaskQueueFactory, RejectedExecutionHandler rejectedExecutionHandler) {
        super(parent, executor, ioHandlerFactory, newTaskQueue(taskQueueFactory), newTaskQueue(tailTaskQueueFactory),
                rejectedExecutionHandler);
    }

    private static Queue<Runnable> newTaskQueue(
            EventLoopTaskQueueFactory queueFactory) {
        if (queueFactory == null) {
            return newTaskQueue0(DEFAULT_MAX_PENDING_TASKS);
        }
        return queueFactory.newTaskQueue(DEFAULT_MAX_PENDING_TASKS);
    }

    /**
     * Returns the {@link SelectorProvider} used by this {@link NioEventLoop} to obtain the {@link Selector}.
     */
    public SelectorProvider selectorProvider() {
        return ((NioIoHandler) ioHandler()).selectorProvider();
    }

    @Override
    protected Queue<Runnable> newTaskQueue(int maxPendingTasks) {
        return newTaskQueue0(maxPendingTasks);
    }

    private static Queue<Runnable> newTaskQueue0(int maxPendingTasks) {
        // This event loop never calls takeTask()
        return maxPendingTasks == Integer.MAX_VALUE ? PlatformDependent.<Runnable>newMpscQueue()
                : PlatformDependent.<Runnable>newMpscQueue(maxPendingTasks);
    }

    /**
     * Registers an arbitrary {@link SelectableChannel}, not necessarily created by Netty, to the {@link Selector}
     * of this event loop.  Once the specified {@link SelectableChannel} is registered, the specified {@code task} will
     * be executed by this event loop when the {@link SelectableChannel} is ready.
     */
    public void register(final SelectableChannel ch, final int interestOps, final NioTask<?> task) {
        ObjectUtil.checkNotNull(ch, "ch");
        if (interestOps == 0) {
            throw new IllegalArgumentException("interestOps must be non-zero.");
        }
        if ((interestOps & ~ch.validOps()) != 0) {
            throw new IllegalArgumentException(
                    "invalid interestOps: " + interestOps + "(validOps: " + ch.validOps() + ')');
        }
        ObjectUtil.checkNotNull(task, "task");

        if (isShutdown()) {
            throw new IllegalStateException("event loop shut down");
        }

        @SuppressWarnings("unchecked")
        final NioTask<SelectableChannel> nioTask = (NioTask<SelectableChannel>) task;
        if (inEventLoop()) {
            register0(ch, interestOps, nioTask);
        } else {
            try {
                // Offload to the EventLoop as otherwise java.nio.channels.spi.AbstractSelectableChannel.register
                // may block for a long time while trying to obtain an internal lock that may be hold while selecting.
                submit(new Runnable() {
                    @Override
                    public void run() {
                        register0(ch, interestOps, nioTask);
                    }
                }).sync();
            } catch (InterruptedException ignore) {
                // Even if interrupted we did schedule it so just mark the Thread as interrupted.
                Thread.currentThread().interrupt();
            }
        }
    }

    private void register0(final SelectableChannel ch, int interestOps, final NioTask<SelectableChannel> task) {
        try {
            IoRegistration registration = register(
                    new NioSelectableChannelIoHandle<SelectableChannel>(ch) {
                        @Override
                        protected void handle(SelectableChannel channel, SelectionKey key) {
                            try {
                                task.channelReady(channel, key);
                            } catch (Exception e) {
                                logger.warn("Unexpected exception while running NioTask.channelReady(...)", e);
                            }
                        }

                        @Override
                        protected void deregister(SelectableChannel channel) {
                            try {
                                task.channelUnregistered(channel, null);
                            } catch (Exception e) {
                                logger.warn("Unexpected exception while running NioTask.channelUnregistered(...)", e);
                            }
                        }
                    }).get();
            registration.submit(NioIoOps.valueOf(interestOps));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Always return 0.
     */
    public int getIoRatio() {
        return 0;
    }

    /**
     * This method is a no-op.
     *
     * @deprecated
     */
    @Deprecated
    public void setIoRatio(int ioRatio) {
        logger.debug("NioEventLoop.setIoRatio(int) logic was removed, this is a no-op");
    }

    /**
     * Replaces the current {@link Selector} of this event loop with newly created {@link Selector}s to work
     * around the infamous epoll 100% CPU bug.
     */
    public void rebuildSelector() {
        if (!inEventLoop()) {
            execute(new Runnable() {
                @Override
                public void run() {
                    ((NioIoHandler) ioHandler()).rebuildSelector0();
                }
            });
            return;
        }
        ((NioIoHandler) ioHandler()).rebuildSelector0();
    }

    @Override
    public int registeredChannels() {
        return ((NioIoHandler) ioHandler()).numRegistered();
    }

    @Override
    public Iterator<Channel> registeredChannelsIterator() {
        assert inEventLoop();
        final Set<SelectionKey> keys =  ((NioIoHandler) ioHandler()).registeredSet();
        if (keys.isEmpty()) {
            return ChannelsReadOnlyIterator.empty();
        }
        return new Iterator<Channel>() {
            final Iterator<SelectionKey> selectionKeyIterator =
                    ObjectUtil.checkNotNull(keys, "selectionKeys")
                            .iterator();
            Channel next;
            boolean isDone;

            @Override
            public boolean hasNext() {
                if (isDone) {
                    return false;
                }
                Channel cur = next;
                if (cur == null) {
                    cur = next = nextOrDone();
                    return cur != null;
                }
                return true;
            }

            @Override
            public Channel next() {
                if (isDone) {
                    throw new NoSuchElementException();
                }
                Channel cur = next;
                if (cur == null) {
                    cur = nextOrDone();
                    if (cur == null) {
                        throw new NoSuchElementException();
                    }
                }
                next = nextOrDone();
                return cur;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException("remove");
            }

            private Channel nextOrDone() {
                Iterator<SelectionKey> it = selectionKeyIterator;
                while (it.hasNext()) {
                    SelectionKey key = it.next();
                    if (key.isValid()) {
                        Object attachment = key.attachment();
                        if (attachment instanceof NioIoHandler.DefaultNioRegistration) {
                            NioIoHandle handle =  ((NioIoHandler.DefaultNioRegistration) attachment).handle();
                            if (handle instanceof AbstractNioChannel.AbstractNioUnsafe) {
                                return ((AbstractNioChannel.AbstractNioUnsafe) handle).channel();
                            }
                        }
                    }
                }
                isDone = true;
                return null;
            }
        };
    }

    Selector unwrappedSelector() {
        return ((NioIoHandler) ioHandler()).unwrappedSelector();
    }
}
