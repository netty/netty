package io.netty.channel;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

/**
 * Created with IntelliJ IDEA.
 * User: kerr
 * Date: 13-4-12
 * Time: 下午11:50
 * To change this template use File | Settings | File Templates.
 */
public interface ChannelTransferPromise extends ChannelPromise, ChannelTransferSensor{

    void setTransferedAmount(long amount);

    @Override
    ChannelTransferPromise removeListeners(GenericFutureListener<? extends Future<Void>>... listeners);

    @Override
    ChannelTransferPromise addListener(GenericFutureListener<? extends Future<Void>> listener);

    @Override
    ChannelTransferPromise addListeners(GenericFutureListener<? extends Future<Void>>... listeners);

    @Override
    ChannelTransferPromise removeListener(GenericFutureListener<? extends Future<Void>> listener);

    @Override
    ChannelTransferPromise sync() throws InterruptedException;

    @Override
    ChannelTransferPromise syncUninterruptibly();

    @Override
    ChannelPromise await() throws InterruptedException;

    @Override
    ChannelPromise awaitUninterruptibly();
}
