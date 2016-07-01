/*
 * Copyright 2018 The Netty Project
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
package io.netty.handler.timeout;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * This handler supports trivial emulation of soTimeout for clients, measure time between
 * last {@code write()} method invocation and first proceed {@code channelRead()}
 *
 * Timeout measurement in the default implementation would be cancelled just after first potentially
 * partial {@code read()} in the channel, so, if you have a marker of a last event in the series -
 * it's worth to override {@link #timeoutShouldBeCancelled(ChannelHandlerContext, Object)} method,
 * otherwise - use this handler in pair with a reasonable {@link IdleStateHandler} to catch situations
 * of extremely slow client reads or dead writes from the server side.
 *
 * Although this handler doesn't catch situations with very low client writes, so use this handler in pair with
 * a reasonable {@link IdleStateHandler} to catch this situations.
 */
public class ReadAfterWriteTimeoutHandler extends ChannelDuplexHandler {
    private static final long MIN_TIMEOUT_NANOS = TimeUnit.MILLISECONDS.toNanos(1);

    private final long timeoutNanos;
    private final boolean closeOnTimeout;
    private final boolean suppressReadAfterTimeout;

    private ChannelFutureListener channelOnWriteListener;
    private ReadTimeoutTask readTimeoutTask;

    private boolean readInvokedAfterWrite;
    private ScheduledFuture<?> timeout;
    private boolean timedOut;

    private volatile boolean removed;

    /**
     * Creates a new instance.
     *
     * @param timeout read timeout after last write to the channel
     * @param unit the {@link TimeUnit} of {@code timeout}
     * @param closeOnTimeout if channel should be closed by this handler after firing {@link ReadTimeoutException}
     * @param suppressReadAfterTimeout channelRead got after timeout before new write would be ignored if {@code true}.
     * This address possible situation when we ran into timeout and get response in a very short time
     * and timeout job executing before channelRead event.
     * Also this addresses situation when we don't close connection after timeout ({@code closeOnTimeout == false}) and
     * should skip next read which was received after timeout.
     * User event {@link ReadSuppressed} will be thrown if case of read suppressing.
     * @throws IllegalArgumentException if {@code timeout <= 0}
     */
    public ReadAfterWriteTimeoutHandler(long timeout, TimeUnit unit, boolean closeOnTimeout,
                                        boolean suppressReadAfterTimeout) {
        if (timeout <= 0) {
            throw new IllegalArgumentException("timeout value must be positive");
        }
        this.timeoutNanos = Math.max(unit.toNanos(timeout), MIN_TIMEOUT_NANOS);
        this.closeOnTimeout = closeOnTimeout;
        this.suppressReadAfterTimeout = suppressReadAfterTimeout;
    }

    /**
     * Creates a new instance with {@code closeOnTimeout == true} and {@code ignoreReadAfterTimeout == true}
     *
     * @param timeout read timeout
     * @param unit the {@link TimeUnit} of {@code timeout}
     */
    public ReadAfterWriteTimeoutHandler(long timeout, TimeUnit unit) {
        this(timeout, unit, true, true);
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) throws Exception {
        removed = false;

        if (channelOnWriteListener == null) {
            channelOnWriteListener = new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    //ensure that only the last write listener in a batch would setup one final timeout task
                    //or nothing in a case of error
                    disable();
                    timedOut = false;
                    if (future.isSuccess()) {
                        if (removed) {
                            return;
                        }
                        readInvokedAfterWrite = false;
                        timeout = ctx.executor().schedule(readTimeoutTask, timeoutNanos, TimeUnit.NANOSECONDS);
                    }
                }
            };
        }

        if (readTimeoutTask == null) {
            readTimeoutTask = new ReadTimeoutTask(ctx);
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        destroy();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        destroy();
        super.channelInactive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (timeoutShouldBeCancelled(ctx, msg)) {
            readInvokedAfterWrite = true;
            disable();
        }
        if (suppressReadAfterTimeout && timedOut) {
            ctx.fireUserEventTriggered(ReadSuppressed.INSTANCE);
            ReferenceCountUtil.release(msg);
        } else {
            super.channelRead(ctx, msg);
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        ctx.write(msg, promise.addListener(channelOnWriteListener));
    }

    /**
     * This method is called after receiving each {@link #channelRead(ChannelHandlerContext, Object)}
     * and should define if this read should trigger
     * timeout cancelling. Default implementation returns {@code true}.
     * @param ctx {@link ChannelHandlerContext} of related {@link #channelRead(ChannelHandlerContext, Object)}
     * invocation
     * @param msg message of related {@link #channelRead} invocation
     * @return true if timeout should be cancelled by receiving this read, false otherwise
     */
    protected boolean timeoutShouldBeCancelled(@SuppressWarnings("unused") ChannelHandlerContext ctx,
                                               @SuppressWarnings("unused") Object msg) {
        return true;
    }

    private void destroy() {
        removed = true;
        disable();
    }

    private void disable() {
        if (timeout != null) {
            timeout.cancel(false);
            timeout = null;
        }
    }

    /**
     * Is called when a read timeout was detected.
     */
    protected void readTimedOut(ChannelHandlerContext ctx) throws Exception {
        timedOut = true;
        ctx.fireExceptionCaught(ReadTimeoutException.INSTANCE);
        if (closeOnTimeout) {
            ctx.close();
        }
    }

    public static final class ReadSuppressed {
        private static final ReadSuppressed INSTANCE = new ReadSuppressed();

        private ReadSuppressed() {
        }
    }

    private final class ReadTimeoutTask implements Runnable {
        private final ChannelHandlerContext ctx;

        ReadTimeoutTask(ChannelHandlerContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void run() {
            timeout = null; //cleanup to use this channel and handler further if we don't close
            if (!ctx.channel().isOpen()) {
                return;
            }
            if (!readInvokedAfterWrite && !timedOut) {
                try {
                    readTimedOut(ctx);
                } catch (Throwable t) {
                    ctx.fireExceptionCaught(t);
                }
            }
        }
    }
}
