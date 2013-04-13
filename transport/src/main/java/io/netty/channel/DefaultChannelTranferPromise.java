package io.netty.channel;

import io.netty.util.concurrent.*;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Created with IntelliJ IDEA.
 * User: kerr
 * Date: 13-4-12
 * Time: 下午11:46
 * To change this template use File | Settings | File Templates.
 */
public class DefaultChannelTranferPromise extends DefaultChannelPromise implements ChannelTransferPromise{
    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(DefaultChannelTranferPromise.class);
    private static final int MAX_LISTENER_STACK_DEPTH = 8;
    private static final ThreadLocal<Integer> TRANSFER_LISTENER_STACK_DEPTH = new ThreadLocal<Integer>();
    private final long total;
    private long amount = 0;
    //now just for simple
    private List<TransferFutureListener> transferListeners = new CopyOnWriteArrayList<TransferFutureListener>();
    public DefaultChannelTranferPromise(Channel channel, long total) {
        super(channel);
        this.total = total;
    }

    public DefaultChannelTranferPromise(Channel channel, EventExecutor executor, long total) {
        super(channel, executor);
        this.total = total;
    }

    @Override
    public void setTransferedAmount(long amount) {
        this.amount = amount;
        logger.info("update amount :{}",amount);
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
    public ChannelTransferSensor addTransferFutureListner(TransferFutureListener listener) {
        if (listener == null) {
            throw new NullPointerException("listener is null");
        }
        System.out.println("add");

        if (isDone()) {
            System.out.println("Done");
            notifyTranferListener(executor(), amount, total, listener);
        }
        transferListeners.add(listener);
        return this;
    }

    @Override
    public ChannelTransferSensor addTransferFutureListeners(TransferFutureListener... listeners) {
        if (listeners == null) {
            return this;
        }
        for (TransferFutureListener l : listeners) {
            transferListeners.add(l);
        }
        return this;
    }

    @Override
    public ChannelTransferSensor removeTransferFutureListener(TransferFutureListener listener) {
        if (listener == null) {
            return this;
        }
        transferListeners.remove(listener);
        return this;
    }

    @Override
    public ChannelTransferSensor removeTransferFutureListeners(TransferFutureListener... listeners) {
        if (listeners == null) {
            return this;
        }
        for (TransferFutureListener l : listeners) {
            transferListeners.remove(l);
        }
        return this;
    }

    protected static void notifyTranferListener(final EventExecutor eventExecutor, final long amount,
                                                final long total, final TransferFutureListener l) {
        if (eventExecutor.inEventLoop()) {
            final Integer stackDepth = TRANSFER_LISTENER_STACK_DEPTH.get();
            if (stackDepth < MAX_LISTENER_STACK_DEPTH) {
                TRANSFER_LISTENER_STACK_DEPTH.set(stackDepth + 1);
                try {
                    notifyTranferListener0(amount,total,l);
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
                    notifyTranferListener(eventExecutor, amount, total, l);
                }
            });
        } catch (Throwable t) {
            logger.error("Failed to notify a listener. Event loop terminated?", t);
        }
    }

    private void notifyTransferListeners(final long amount) {
        if (transferListeners == null || transferListeners.isEmpty()) {
            return;
        }

        EventExecutor executor = executor();
        if (executor.inEventLoop()) {
            notifyTransferListeners0(amount,total,transferListeners);
        } else {
            try {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        notifyTransferListeners0(amount,total,transferListeners);
                    }
                });
            } catch (Throwable t) {
                logger.error("Failed to notify the transfer listener(s). Event loop terminated?", t);
            }
        }
    }

    private static void notifyTransferListeners0(long amount, long total, Iterable<TransferFutureListener> listeners) {
        for (TransferFutureListener l : listeners){
            notifyTranferListener0(amount,total,l);
        }
    }

    private static void notifyTranferListener0(long amount, long total, TransferFutureListener l) {
        try {
            l.onTransfered(amount,total);
        } catch (Throwable t) {
            if (logger.isWarnEnabled()) {
                logger.warn("an exception is throw by {}:",
                        DefaultChannelTranferPromise.class.getSimpleName());
            }

        }
    }


}
