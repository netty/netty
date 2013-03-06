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
package io.netty.channel;

import io.netty.channel.ChannelFlushPromiseNotifier.FlushCheckpoint;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

/**
 * The default {@link ChannelPromise} implementation.  It is recommended to use {@link Channel#newPromise()} to create
 * a new {@link ChannelPromise} rather than calling the constructor explicitly.
 */
public class DefaultChannelPromise extends DefaultPromise implements ChannelPromise, FlushCheckpoint {

    private final Channel channel;

    /**
     * The first 24 bits of this field represents the number of waiters waiting for this promise with await*().
     * The other 40 bits of this field represents the flushCheckpoint used by ChannelFlushPromiseNotifier and
     * AbstractChannel.Unsafe.flush().
     */
    private long flushCheckpoint;

    /**
     * Creates a new instance.
     *
     * @param channel
     *        the {@link Channel} associated with this future
     */
    public DefaultChannelPromise(Channel channel) {
        this.channel = channel;
    }

    /**
     * Creates a new instance.
     *
     * @param channel
     *        the {@link Channel} associated with this future
     */
    public DefaultChannelPromise(Channel channel, EventExecutor executor) {
        super(executor);
        this.channel = channel;
    }

    @Override
    protected EventExecutor executor() {
        EventExecutor e = super.executor();
        if (e == null) {
            return channel().eventLoop();
        } else {
            return e;
        }
    }

    @Override
    public Channel channel() {
        return channel;
    }

    @Override
    public ChannelPromise setSuccess() {
        super.setSuccess();
        return this;
    }

    @Override
    public ChannelPromise setFailure(Throwable cause) {
        super.setFailure(cause);
        return this;
    }

    @Override
    public ChannelPromise addListener(GenericFutureListener<? extends Future> listener) {
        super.addListener(listener);
        return this;
    }

    @Override
    public ChannelPromise addListeners(GenericFutureListener<? extends Future>... listeners) {
        super.addListeners(listeners);
        return this;
    }

    @Override
    public ChannelPromise removeListener(GenericFutureListener<? extends Future> listener) {
        super.removeListener(listener);
        return this;
    }

    @Override
    public ChannelPromise removeListeners(GenericFutureListener<? extends Future>... listeners) {
        super.removeListeners(listeners);
        return this;
    }

    @Override
    public ChannelPromise sync() throws InterruptedException {
        await();
        rethrowIfFailed(this);
        return this;
    }

    @Override
    public ChannelPromise syncUninterruptibly() {
        awaitUninterruptibly();
        rethrowIfFailed(this);
        return this;
    }

    static void rethrowIfFailed(ChannelFuture future) {
        Throwable cause = future.cause();
        if (cause == null) {
            return;
        }

        if (cause instanceof RuntimeException) {
            throw (RuntimeException) cause;
        }

        if (cause instanceof Error) {
            throw (Error) cause;
        }

        throw new ChannelException(cause);
    }

    @Override
    public ChannelPromise await() throws InterruptedException {
        super.await();
        return this;
    }

    @Override
    public ChannelPromise awaitUninterruptibly() {
        super.awaitUninterruptibly();
        return this;
    }

    @Override
    public long flushCheckpoint() {
        return flushCheckpoint & 0x000000FFFFFFFFFFL;
    }

    @Override
    public void flushCheckpoint(long checkpoint) {
        if ((checkpoint & 0xFFFFFF0000000000L) != 0) {
            throw new IllegalStateException("flushCheckpoint overflow");
        }
        flushCheckpoint = flushCheckpoint & 0xFFFFFF0000000000L | checkpoint;
    }

    @Override
    protected boolean hasWaiters() {
        return (flushCheckpoint & 0xFFFFFF0000000000L) != 0;
    }

    @Override
    protected void incWaiters() {
        long waiters = waiters() + 1;
        if ((waiters & 0xFFFFFFFFFF000000L) != 0) {
            throw new IllegalStateException("too many waiters");
        }
        flushCheckpoint = flushCheckpoint() | waiters << 40L;
    }

    @Override
    protected void decWaiters() {
        flushCheckpoint = flushCheckpoint() | waiters() - 1L << 40L;
    }

    private long waiters() {
        return flushCheckpoint >>> 40;
    }

    @Override
    public ChannelPromise future() {
        return this;
    }
}
