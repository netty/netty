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
package io.netty.handler.timeout;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventExecutor;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timer;

import java.nio.channels.Channels;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Triggers an {@link IdleStateEvent} when a {@link Channel} has not performed
 * read, write, or both operation for a while.
 *
 * <h3>Supported idle states</h3>
 * <table border="1">
 * <tr>
 * <th>Property</th><th>Meaning</th>
 * </tr>
 * <tr>
 * <td>{@code readerIdleTime}</td>
 * <td>an {@link IdleStateEvent} whose state is {@link IdleState#READER_IDLE}
 *     will be triggered when no read was performed for the specified period of
 *     time.  Specify {@code 0} to disable.</td>
 * </tr>
 * <tr>
 * <td>{@code writerIdleTime}</td>
 * <td>an {@link IdleStateEvent} whose state is {@link IdleState#WRITER_IDLE}
 *     will be triggered when no write was performed for the specified period of
 *     time.  Specify {@code 0} to disable.</td>
 * </tr>
 * <tr>
 * <td>{@code allIdleTime}</td>
 * <td>an {@link IdleStateEvent} whose state is {@link IdleState#ALL_IDLE}
 *     will be triggered when neither read nor write was performed for the
 *     specified period of time.  Specify {@code 0} to disable.</td>
 * </tr>
 * </table>
 *
 * <pre>
 * // An example that sends a ping message when there is no outbound traffic
 * // for 30 seconds.  The connection is closed when there is no inbound traffic
 * // for 60 seconds.
 *
 * public class MyPipelineFactory implements {@link ChannelPipelineFactory} {
 *
 *     private final {@link Timer} timer;
 *     private final {@link ChannelHandler} idleStateHandler;
 *
 *     public MyPipelineFactory({@link Timer} timer) {
 *         this.timer = timer;
 *         this.idleStateHandler = <b>new {@link IdleStateHandler}(timer, 60, 30, 0), // timer must be shared.</b>
 *     }
 *
 *     public {@link ChannelPipeline} getPipeline() {
 *         return {@link Channels}.pipeline(
 *             idleStateHandler,
 *             new MyHandler());
 *     }
 * }
 *
 * // Handler should handle the {@link IdleStateEvent} triggered by {@link IdleStateHandler}.
 * public class MyHandler extends {@link IdleStateAwareChannelHandler} {
 *
 *     {@code @Override}
 *     public void channelIdle({@link ChannelHandlerContext} ctx, {@link IdleStateEvent} e) {
 *         if (e.getState() == {@link IdleState}.READER_IDLE) {
 *             e.getChannel().close();
 *         } else if (e.getState() == {@link IdleState}.WRITER_IDLE) {
 *             e.getChannel().write(new PingMessage());
 *         }
 *     }
 * }
 *
 * {@link ServerBootstrap} bootstrap = ...;
 * {@link Timer} timer = new {@link HashedWheelTimer}();
 * ...
 * bootstrap.setPipelineFactory(new MyPipelineFactory(timer));
 * ...
 * </pre>
 *
 * The {@link Timer} which was specified when the {@link IdleStateHandler} is
 * created should be stopped manually by calling {@link #releaseExternalResources()}
 * or {@link Timer#stop()} when your application shuts down.
 * @see ReadTimeoutHandler
 * @see WriteTimeoutHandler
 *
 * @apiviz.landmark
 * @apiviz.uses io.netty.util.HashedWheelTimer
 * @apiviz.has io.netty.handler.timeout.IdleStateEvent oneway - - triggers
 */
public class IdleStateHandler extends ChannelHandlerAdapter {

    private final long readerIdleTimeMillis;
    private final long writerIdleTimeMillis;
    private final long allIdleTimeMillis;

    volatile ScheduledFuture<?> readerIdleTimeout;
    volatile long lastReadTime;
    int readerIdleCount;

    volatile ScheduledFuture<?> writerIdleTimeout;
    volatile long lastWriteTime;
    int writerIdleCount;

    volatile ScheduledFuture<?> allIdleTimeout;
    int allIdleCount;

    volatile boolean destroyed;

    /**
     * Creates a new instance.
     *
     * @param readerIdleTimeSeconds
     *        an {@link IdleStateEvent} whose state is {@link IdleState#READER_IDLE}
     *        will be triggered when no read was performed for the specified
     *        period of time.  Specify {@code 0} to disable.
     * @param writerIdleTimeSeconds
     *        an {@link IdleStateEvent} whose state is {@link IdleState#WRITER_IDLE}
     *        will be triggered when no write was performed for the specified
     *        period of time.  Specify {@code 0} to disable.
     * @param allIdleTimeSeconds
     *        an {@link IdleStateEvent} whose state is {@link IdleState#ALL_IDLE}
     *        will be triggered when neither read nor write was performed for
     *        the specified period of time.  Specify {@code 0} to disable.
     */
    public IdleStateHandler(
            int readerIdleTimeSeconds,
            int writerIdleTimeSeconds,
            int allIdleTimeSeconds) {

        this(readerIdleTimeSeconds, writerIdleTimeSeconds, allIdleTimeSeconds,
             TimeUnit.SECONDS);
    }

    /**
     * Creates a new instance.
     *
     * @param readerIdleTime
     *        an {@link IdleStateEvent} whose state is {@link IdleState#READER_IDLE}
     *        will be triggered when no read was performed for the specified
     *        period of time.  Specify {@code 0} to disable.
     * @param writerIdleTime
     *        an {@link IdleStateEvent} whose state is {@link IdleState#WRITER_IDLE}
     *        will be triggered when no write was performed for the specified
     *        period of time.  Specify {@code 0} to disable.
     * @param allIdleTime
     *        an {@link IdleStateEvent} whose state is {@link IdleState#ALL_IDLE}
     *        will be triggered when neither read nor write was performed for
     *        the specified period of time.  Specify {@code 0} to disable.
     * @param unit
     *        the {@link TimeUnit} of {@code readerIdleTime},
     *        {@code writeIdleTime}, and {@code allIdleTime}
     */
    public IdleStateHandler(
            long readerIdleTime, long writerIdleTime, long allIdleTime,
            TimeUnit unit) {

        if (unit == null) {
            throw new NullPointerException("unit");
        }

        if (readerIdleTime <= 0) {
            readerIdleTimeMillis = 0;
        } else {
            readerIdleTimeMillis = Math.max(unit.toMillis(readerIdleTime), 1);
        }
        if (writerIdleTime <= 0) {
            writerIdleTimeMillis = 0;
        } else {
            writerIdleTimeMillis = Math.max(unit.toMillis(writerIdleTime), 1);
        }
        if (allIdleTime <= 0) {
            allIdleTimeMillis = 0;
        } else {
            allIdleTimeMillis = Math.max(unit.toMillis(allIdleTime), 1);
        }
    }

    @Override
    public void beforeAdd(ChannelHandlerContext ctx) throws Exception {
        if (ctx.channel().isActive()) {
            // channelActvie() event has been fired already, which means this.channelActive() will
            // not be invoked. We have to initialize here instead.
            initialize(ctx);
        } else {
            // channelActive() event has not been fired yet.  this.channelOpen() will be invoked
            // and initialization will occur there.
        }
    }

    @Override
    public void beforeRemove(ChannelHandlerContext ctx) throws Exception {
        destroy();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        // This method will be invoked only if this handler was added
        // before channelActive() event is fired.  If a user adds this handler
        // after the channelActive() event, initialize() will be called by beforeAdd().
        initialize(ctx);
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        destroy();
        super.channelInactive(ctx);
    }


    @Override
    public void inboundBufferUpdated(ChannelHandlerContext ctx) throws Exception {
        lastReadTime = System.currentTimeMillis();
        readerIdleCount = allIdleCount = 0;
        ctx.fireInboundBufferUpdated();
    }

    @Override
    public void flush(final ChannelHandlerContext ctx, ChannelFuture future) throws Exception {
        future.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                lastWriteTime = System.currentTimeMillis();
                writerIdleCount = allIdleCount = 0;
            }
        });

        super.flush(ctx, future);
    }

    private void initialize(ChannelHandlerContext ctx) {
        // Avoid the case where destroy() is called before scheduling timeouts.
        // See: https://github.com/netty/netty/issues/143
        if (destroyed) {
            return;
        }

        EventExecutor loop = ctx.executor();

        lastReadTime = lastWriteTime = System.currentTimeMillis();
        if (readerIdleTimeMillis > 0) {
            readerIdleTimeout = loop.schedule(
                    new ReaderIdleTimeoutTask(ctx),
                    readerIdleTimeMillis, TimeUnit.MILLISECONDS);
        }
        if (writerIdleTimeMillis > 0) {
            writerIdleTimeout = loop.schedule(
                    new WriterIdleTimeoutTask(ctx),
                    writerIdleTimeMillis, TimeUnit.MILLISECONDS);
        }
        if (allIdleTimeMillis > 0) {
            allIdleTimeout = loop.schedule(
                    new AllIdleTimeoutTask(ctx),
                    allIdleTimeMillis, TimeUnit.MILLISECONDS);
        }
    }

    private void destroy() {
        destroyed = true;

        if (readerIdleTimeout != null) {
            readerIdleTimeout.cancel(false);
            readerIdleTimeout = null;
        }
        if (writerIdleTimeout != null) {
            writerIdleTimeout.cancel(false);
            writerIdleTimeout = null;
        }
        if (allIdleTimeout != null) {
            allIdleTimeout.cancel(false);
            allIdleTimeout = null;
        }
    }

    protected void channelIdle(ChannelHandlerContext ctx, IdleStateEvent evt) throws Exception {
        ctx.fireUserEventTriggered(evt);
    }

    private final class ReaderIdleTimeoutTask implements Runnable {

        private final ChannelHandlerContext ctx;

        ReaderIdleTimeoutTask(ChannelHandlerContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void run() {
            if (!ctx.channel().isOpen()) {
                return;
            }

            long currentTime = System.currentTimeMillis();
            long lastReadTime = IdleStateHandler.this.lastReadTime;
            long nextDelay = readerIdleTimeMillis - (currentTime - lastReadTime);
            if (nextDelay <= 0) {
                // Reader is idle - set a new timeout and notify the callback.
                readerIdleTimeout =
                    ctx.executor().schedule(this, readerIdleTimeMillis, TimeUnit.MILLISECONDS);
                try {
                    channelIdle(ctx, new IdleStateEvent(
                            IdleState.READER_IDLE, readerIdleCount ++, currentTime - lastReadTime));
                } catch (Throwable t) {
                    ctx.fireExceptionCaught(t);
                }
            } else {
                // Read occurred before the timeout - set a new timeout with shorter delay.
                readerIdleTimeout = ctx.executor().schedule(this, nextDelay, TimeUnit.MILLISECONDS);
            }
        }

    }

    private final class WriterIdleTimeoutTask implements Runnable {

        private final ChannelHandlerContext ctx;

        WriterIdleTimeoutTask(ChannelHandlerContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void run() {
            if (!ctx.channel().isOpen()) {
                return;
            }

            long currentTime = System.currentTimeMillis();
            long lastWriteTime = IdleStateHandler.this.lastWriteTime;
            long nextDelay = writerIdleTimeMillis - (currentTime - lastWriteTime);
            if (nextDelay <= 0) {
                // Writer is idle - set a new timeout and notify the callback.
                writerIdleTimeout = ctx.executor().schedule(
                        this, writerIdleTimeMillis, TimeUnit.MILLISECONDS);
                try {
                    channelIdle(ctx, new IdleStateEvent(
                            IdleState.WRITER_IDLE, writerIdleCount ++, currentTime - lastWriteTime));
                } catch (Throwable t) {
                    ctx.fireExceptionCaught(t);
                }
            } else {
                // Write occurred before the timeout - set a new timeout with shorter delay.
                writerIdleTimeout = ctx.executor().schedule(this, nextDelay, TimeUnit.MILLISECONDS);
            }
        }
    }

    private final class AllIdleTimeoutTask implements Runnable {

        private final ChannelHandlerContext ctx;

        AllIdleTimeoutTask(ChannelHandlerContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void run() {
            if (!ctx.channel().isOpen()) {
                return;
            }

            long currentTime = System.currentTimeMillis();
            long lastIoTime = Math.max(lastReadTime, lastWriteTime);
            long nextDelay = allIdleTimeMillis - (currentTime - lastIoTime);
            if (nextDelay <= 0) {
                // Both reader and writer are idle - set a new timeout and
                // notify the callback.
                allIdleTimeout = ctx.executor().schedule(
                        this, allIdleTimeMillis, TimeUnit.MILLISECONDS);
                try {
                    channelIdle(ctx, new IdleStateEvent(
                            IdleState.ALL_IDLE, allIdleCount ++, currentTime - lastIoTime));
                } catch (Throwable t) {
                    ctx.fireExceptionCaught(t);
                }
            } else {
                // Either read or write occurred before the timeout - set a new
                // timeout with shorter delay.
                allIdleTimeout = ctx.executor().schedule(this, nextDelay, TimeUnit.MILLISECONDS);
            }
        }
    }
}
