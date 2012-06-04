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
import io.netty.channel.ChannelBufferHolder;
import io.netty.channel.ChannelBufferHolders;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timer;

import java.nio.channels.Channels;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Raises a {@link WriteTimeoutException} when no data was written within a
 * certain period of time.
 *
 * <pre>
 * public class MyPipelineFactory implements {@link ChannelPipelineFactory} {
 *
 *     private final {@link Timer} timer;
 *
 *     public MyPipelineFactory({@link Timer} timer) {
 *         this.timer = timer;
 *     }
 *
 *     public {@link ChannelPipeline} getPipeline() {
 *         // An example configuration that implements 30-second write timeout:
 *         return {@link Channels}.pipeline(
 *             <b>new {@link WriteTimeoutHandler}(timer, 30), // timer must be shared.</b>
 *             new MyHandler());
 *     }
 * }
 *
 * {@link ServerBootstrap} bootstrap = ...;
 * {@link Timer} timer = new {@link HashedWheelTimer}();
 * ...
 * bootstrap.setPipelineFactory(new MyPipelineFactory(timer));
 * </pre>
 *
 * The {@link Timer} which was specified when the {@link ReadTimeoutHandler} is
 * created should be stopped manually by calling {@link #releaseExternalResources()}
 * or {@link Timer#stop()} when your application shuts down.
 * @see ReadTimeoutHandler
 * @see IdleStateHandler
 *
 * @apiviz.landmark
 * @apiviz.uses io.netty.util.HashedWheelTimer
 * @apiviz.has io.netty.handler.timeout.TimeoutException oneway - - raises
 */
public class WriteTimeoutHandler extends ChannelOutboundHandlerAdapter<Object> {

    private final long timeoutMillis;

    private boolean closed;

    /**
     * Creates a new instance.
     *
     * @param timeoutSeconds
     *        write timeout in seconds
     */
    public WriteTimeoutHandler(int timeoutSeconds) {
        this(timeoutSeconds, TimeUnit.SECONDS);
    }

    /**
     * Creates a new instance.
     *
     * @param timeout
     *        write timeout
     * @param unit
     *        the {@link TimeUnit} of {@code timeout}
     */
    public WriteTimeoutHandler(long timeout, TimeUnit unit) {
        if (unit == null) {
            throw new NullPointerException("unit");
        }

        if (timeout <= 0) {
            timeoutMillis = 0;
        } else {
            timeoutMillis = Math.max(unit.toMillis(timeout), 1);
        }
    }

    @Override
    public ChannelBufferHolder<Object> newOutboundBuffer(ChannelOutboundHandlerContext<Object> ctx) throws Exception {
        return ChannelBufferHolders.outboundBypassBuffer(ctx);
    }

    @Override
    public void flush(final ChannelOutboundHandlerContext<Object> ctx, final ChannelFuture future) throws Exception {
        if (timeoutMillis > 0) {
            // Schedule a timeout.
            final ScheduledFuture<?> sf = ctx.executor().schedule(new Runnable() {
                @Override
                public void run() {
                    if (future.setFailure(WriteTimeoutException.INSTANCE)) {
                        // If succeeded to mark as failure, notify the pipeline, too.
                        try {
                            writeTimedOut(ctx);
                        } catch (Throwable t) {
                            ctx.fireExceptionCaught(t);
                        }
                    }
                }

            }, timeoutMillis, TimeUnit.MILLISECONDS);

            // Cancel the scheduled timeout if the flush future is complete.
            future.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    sf.cancel(false);
                }
            });
        }

        super.flush(ctx, future);
    }

    protected void writeTimedOut(ChannelHandlerContext ctx) throws Exception {
        if (!closed) {
            ctx.fireExceptionCaught(WriteTimeoutException.INSTANCE);
            ctx.close();
            closed = true;
        }
    }
}
