/*
 * Copyright 2013 The Netty Project
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
package io.netty.channel;

import io.netty.util.concurrent.DefaultEventListeners;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.EventListeners;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

/**
 * The default {@link ChannelTransferPromise} implementation.  It is recommended to use
 * {@link Channel#newTransferPromise(long)} to create a new {@link ChannelTransferPromise} rather than calling the
 * constructor explicitly.
 */
public class DefaultChannelTransferPromise extends DefaultChannelPromise implements ChannelTransferPromise {
    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(DefaultChannelTransferPromise.class);
    private static final int MAX_LISTENER_STACK_DEPTH = 8;
    private static final ThreadLocal<Integer> TRANSFER_LISTENER_STACK_DEPTH = new ThreadLocal<Integer>();
    private final long total;
    private long amount;
    private Object transferListeners; //can be TransferFutureListener or DefaultTransferFutureListeners;
    public DefaultChannelTransferPromise(Channel channel, long total) {
        super(channel);
        this.total = total;
    }

    public DefaultChannelTransferPromise(Channel channel, EventExecutor executor, long total) {
        super(channel, executor);
        this.total = total;
    }

    @Override
    public void incrementTransferredBytes(long amount) {
        this.amount = amount;
        notifyTransferListeners(amount);
    }

    @Override
    public ChannelTransferPromise removeListeners(GenericFutureListener<? extends Future<Void>>... listeners) {
        super.removeListeners(listeners);
        return this;
    }

    @Override
    public ChannelTransferPromise addListener(GenericFutureListener<? extends Future<Void>> listener) {
        super.addListener(listener);
        return this;
    }

    @Override
    public ChannelTransferPromise addListeners(GenericFutureListener<? extends Future<Void>>... listeners) {
        super.addListeners(listeners);
        return this;
    }

    @Override
    public ChannelTransferPromise removeListener(GenericFutureListener<? extends Future<Void>> listener) {
        super.removeListener(listener);
        return this;
    }

    @Override
    public ChannelTransferPromise await() throws InterruptedException {
        super.await();
        return this;
    }

    @Override
    public ChannelTransferPromise awaitUninterruptibly() {
        super.awaitUninterruptibly();
        return this;
    }

    @Override
    public ChannelTransferPromise sync() throws InterruptedException {
        super.sync();
        return this;
    }

    @Override
    public ChannelTransferPromise syncUninterruptibly() {
        super.syncUninterruptibly();
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ChannelTransferPromise addTransferFutureListener(TransferFutureListener listener) {
        if (listener == null) {
            throw new NullPointerException("listener can not be null");
        }

        if (isDone()) {
            notifyTransferListener(executor(), amount, total, listener);
        }
        synchronized (this) {
            if (!isDone()) {
                if (transferListeners == null) {
                    transferListeners = listener;
                } else {
                    if (transferListeners instanceof EventListeners) {
                        ((EventListeners) transferListeners).add(listener);
                    } else {
                        transferListeners = new DefaultEventListeners<TransferFutureListener>(
                                TransferFutureListener.class,
                                (TransferFutureListener) transferListeners, listener);
                    }
                }
                return this;
            }
        }

        notifyTransferListener(executor(), amount, total, listener);
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ChannelTransferPromise addTransferFutureListeners(TransferFutureListener... listeners) {
        if (listeners == null) {
            throw new NullPointerException("listeners can not be null");
        }
        if (isDone()) {
            for (TransferFutureListener l : listeners) {
                if (l == null) {
                    break;
                }
                notifyTransferListener(executor(), amount, total, l);
            }
        }
        synchronized (this) {
            Object localTransferListeners = this.transferListeners;
            if (localTransferListeners == null) {
                localTransferListeners =
                        new DefaultEventListeners<TransferFutureListener>(TransferFutureListener.class);
            }
            EventListeners<TransferFutureListener> transferListeners =
                    (EventListeners<TransferFutureListener>) localTransferListeners;
            for (TransferFutureListener l : listeners) {
                if (l == null) {
                    break;
                }
                if (!isDone()) {
                    transferListeners.add(l);
                    notifyTransferListener(executor(), amount, total, l);
                }
            }
        }
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ChannelTransferPromise removeTransferFutureListener(TransferFutureListener listener) {
        if (listener == null) {
            throw new NullPointerException("listener can not be null");
        }
        if (isDone()) {
            return this;
        }
        if (transferListeners == null) {
            return this;
        }
        synchronized (this) {
            if (!isDone()) {
                if (transferListeners instanceof EventListeners) {
                    ((EventListeners) transferListeners).remove(listener);
                } else if (transferListeners == listener) {
                    transferListeners = null;
                }
            }
        }
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ChannelTransferPromise removeTransferFutureListeners(TransferFutureListener... listeners) {
        if (listeners == null) {
            throw new NullPointerException("listeners can not be null");
        }
        if (isDone()) {
            return this;
        }
        if (transferListeners == null) {
            return this;
        }
        synchronized (this) {
            for (TransferFutureListener l : listeners) {
                ((EventListeners) transferListeners).remove(l);
            }
        }
        return this;
    }

    protected static void notifyTransferListener(final EventExecutor eventExecutor, final long amount,
                                                final long total, final TransferFutureListener l) {
        if (eventExecutor.inEventLoop()) {
            Integer stackDepth = TRANSFER_LISTENER_STACK_DEPTH.get();
            if (stackDepth == null) {
                stackDepth = 0;
            }
            if (stackDepth < MAX_LISTENER_STACK_DEPTH) {
                TRANSFER_LISTENER_STACK_DEPTH.set(stackDepth + 1);
                try {
                    notifyTransferListener0(amount, total, l);
                } finally {
                    TRANSFER_LISTENER_STACK_DEPTH.set(stackDepth);
                }
                return;
            }
        }
        try {
            eventExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    notifyTransferListener(eventExecutor, amount, total, l);
                }
            });
        } catch (Throwable t) {
            logger.error("Failed to notify a listener. Event loop terminated?", t);
        }
    }

    @SuppressWarnings("unchecked")
    private void notifyTransferListeners(final long amount) {
        if (transferListeners == null) {
            return;
        }

        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            if (transferListeners instanceof EventListeners) {
                notifyTransferListeners0(amount, total, (EventListeners<TransferFutureListener>) transferListeners);
            } else {
                notifyTransferListener0(amount, total, (TransferFutureListener) transferListeners);
            }
        } else {
            try {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        if (transferListeners instanceof EventListeners) {
                            notifyTransferListeners0(amount, total,
                                    (EventListeners<TransferFutureListener>) transferListeners);
                        } else {
                            notifyTransferListener0(amount, total, (TransferFutureListener) transferListeners);
                        }
                    }
                });
            } catch (Throwable t) {
                logger.error("Failed to notify the transfer listener(s). Event loop terminated?", t);
            }
        }
    }

    private static void notifyTransferListeners0(long amount, long total,
                                                 EventListeners<TransferFutureListener> listeners) {
        final TransferFutureListener[] allListeners = listeners.listeners();
        for (TransferFutureListener l : allListeners) {
            notifyTransferListener0(amount, total, l);
        }
    }

    private static void notifyTransferListener0(long amount, long total, TransferFutureListener l) {
        try {
            l.onTransferred(amount, total);
        } catch (Throwable t) {
            if (logger.isWarnEnabled()) {
                logger.warn("an exception is throw by {}:",
                        DefaultChannelTransferPromise.class.getSimpleName());
            }
        }
    }
}
