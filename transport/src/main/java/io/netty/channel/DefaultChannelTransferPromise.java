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
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.EventListener;

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
    public ChannelTransferPromise incrementTransferredBytes(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount must be >= 0");
        }
        long sum;
        synchronized (this) {
            this.amount += amount;
            sum = this.amount;
        }
        notifyTransferListeners(sum);
        return this;
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
    public ChannelTransferPromise addTransferListener(TransferFutureListener listener) {
        if (listener == null) {
            throw new NullPointerException("listener");
        }

        if (isDone()) {
            notifyTransferListener(executor(), amount, total, listener);
            return this;
        }
        synchronized (this) {
            if (!isDone()) {
                if (transferListeners == null) {
                    transferListeners = listener;
                } else {
                    if (transferListeners instanceof DefaultEventListeners) {
                        ((DefaultEventListeners) transferListeners).add(listener);
                    } else {
                        transferListeners = new DefaultEventListeners(
                                (EventListener) transferListeners, listener);
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
    public ChannelTransferPromise addTransferListeners(TransferFutureListener... listeners) {
        if (listeners == null) {
            throw new NullPointerException("listeners");
        }
        for (TransferFutureListener l : listeners) {
            if (l == null) {
                break;
            }
            addTransferListener(l);
        }
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ChannelTransferPromise removeTransferListener(TransferFutureListener listener) {
        if (listener == null) {
            throw new NullPointerException("listener");
        }
        if (isDone()) {
            return this;
        }
        if (transferListeners == null) {
            return this;
        }
        synchronized (this) {
            if (!isDone()) {
                if (transferListeners instanceof DefaultEventListeners) {
                    ((DefaultEventListeners) transferListeners).remove(listener);
                } else if (transferListeners == listener) {
                    transferListeners = null;
                }
            }
        }
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ChannelTransferPromise removeTransferListeners(TransferFutureListener... listeners) {
        if (listeners == null) {
            throw new NullPointerException("listeners");
        }
        for (TransferFutureListener l : listeners) {
            if (l == null) {
                break;
            }
            removeTransferListener(l);
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
            if (transferListeners instanceof DefaultEventListeners) {
                notifyTransferListeners0(amount, total, (DefaultEventListeners) transferListeners);
            } else {
                notifyTransferListener0(amount, total, (TransferFutureListener) transferListeners);
            }
        } else {
            try {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        if (transferListeners instanceof DefaultEventListeners) {
                            notifyTransferListeners0(amount, total,
                                    (DefaultEventListeners) transferListeners);
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
                                                 DefaultEventListeners listeners) {
        final EventListener[] allListeners = listeners.listeners();
        for (EventListener l : allListeners) {
            notifyTransferListener0(amount, total, (TransferFutureListener) l);
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
