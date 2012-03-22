/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.handler.timeout;

import static org.jboss.netty.channel.Channels.*;

import java.util.concurrent.TimeUnit;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandler.Sharable;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.LifeCycleAwareChannelHandler;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.util.ExternalResourceReleasable;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.Timer;
import org.jboss.netty.util.TimerTask;

/**
 * Raises a {@link ReadTimeoutException} when no data was read within a certain
 * period of time.
 *
 * <pre>
 * public class MyPipelineFactory implements {@link ChannelPipelineFactory} {
 *
 *     private final {@link Timer} timer;
 *     private final {@link ChannelHandler} timeoutHandler;
 *
 *     public MyPipelineFactory({@link Timer} timer) {
 *         this.timer = timer;
 *         this.timeoutHandler = <b>new {@link ReadTimeoutHandler}(timer, 30), // timer must be shared.</b>
 *     }
 *
 *     public {@link ChannelPipeline} getPipeline() {
 *         // An example configuration that implements 30-second read timeout:
 *         return {@link Channels}.pipeline(
 *             timeoutHandler,
 *             new MyHandler());
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
 * The {@link Timer} which was specified when the {@link ReadTimeoutHandler} is
 * created should be stopped manually by calling {@link #releaseExternalResources()}
 * or {@link Timer#stop()} when your application shuts down.
 * @see WriteTimeoutHandler
 * @see IdleStateHandler
 *
 * @apiviz.landmark
 * @apiviz.uses org.jboss.netty.util.HashedWheelTimer
 * @apiviz.has org.jboss.netty.handler.timeout.TimeoutException oneway - - raises
 */
@Sharable
public class ReadTimeoutHandler extends SimpleChannelUpstreamHandler
                                implements LifeCycleAwareChannelHandler,
                                           ExternalResourceReleasable {

    static final ReadTimeoutException EXCEPTION = new ReadTimeoutException();

    final Timer timer;
    final long timeoutMillis;

    /**
     * Creates a new instance.
     *
     * @param timer
     *        the {@link Timer} that is used to trigger the scheduled event.
     *        The recommended {@link Timer} implementation is {@link HashedWheelTimer}.
     * @param timeoutSeconds
     *        read timeout in seconds
     */
    public ReadTimeoutHandler(Timer timer, int timeoutSeconds) {
        this(timer, timeoutSeconds, TimeUnit.SECONDS);
    }

    /**
     * Creates a new instance.
     *
     * @param timer
     *        the {@link Timer} that is used to trigger the scheduled event.
     *        The recommended {@link Timer} implementation is {@link HashedWheelTimer}.
     * @param timeout
     *        read timeout
     * @param unit
     *        the {@link TimeUnit} of {@code timeout}
     */
    public ReadTimeoutHandler(Timer timer, long timeout, TimeUnit unit) {
        if (timer == null) {
            throw new NullPointerException("timer");
        }
        if (unit == null) {
            throw new NullPointerException("unit");
        }

        this.timer = timer;
        if (timeout <= 0) {
            timeoutMillis = 0;
        } else {
            timeoutMillis = Math.max(unit.toMillis(timeout), 1);
        }
    }

    /**
     * Stops the {@link Timer} which was specified in the constructor of this
     * handler.  You should not call this method if the {@link Timer} is in use
     * by other objects.
     */
    public void releaseExternalResources() {
        timer.stop();
    }

    public void beforeAdd(ChannelHandlerContext ctx) throws Exception {
        if (ctx.getPipeline().isAttached()) {
            // channelOpen event has been fired already, which means
            // this.channelOpen() will not be invoked.
            // We have to initialize here instead.
            initialize(ctx);
        } else {
            // channelOpen event has not been fired yet.
            // this.channelOpen() will be invoked and initialization will occur there.
        }
    }

    public void afterAdd(ChannelHandlerContext ctx) throws Exception {
        // NOOP
    }

    public void beforeRemove(ChannelHandlerContext ctx) throws Exception {
        destroy(ctx);
    }

    public void afterRemove(ChannelHandlerContext ctx) throws Exception {
        // NOOP
    }

    @Override
    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception {
        // This method will be invoked only if this handler was added
        // before channelOpen event is fired.  If a user adds this handler
        // after the channelOpen event, initialize() will be called by beforeAdd().
        initialize(ctx);
        ctx.sendUpstream(e);
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception {
        destroy(ctx);
        ctx.sendUpstream(e);
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception {
        State state = (State) ctx.getAttachment();
        state.lastReadTime = System.currentTimeMillis();
        ctx.sendUpstream(e);
    }

    private void initialize(ChannelHandlerContext ctx) {
        State state = state(ctx);

        // Avoid the case where destroy() is called before scheduling timeouts.
        // See: https://github.com/netty/netty/issues/143
        if (state.destroyed) {
            return;
        }

        if (timeoutMillis > 0) {
            state.timeout = timer.newTimeout(new ReadTimeoutTask(ctx), timeoutMillis, TimeUnit.MILLISECONDS);
        }
    }

    private void destroy(ChannelHandlerContext ctx) {
        State state;
        synchronized (ctx) {
            state = state(ctx);
            state.destroyed = true;
        }

        if (state.timeout != null) {
            state.timeout.cancel();
            state.timeout = null;
        }
    }

    private State state(ChannelHandlerContext ctx) {
        State state;
        synchronized (ctx) {
            // FIXME: It could have been better if there is setAttachmentIfAbsent().
            state = (State) ctx.getAttachment();
            if (state != null) {
                return state;
            }
            state = new State();
            ctx.setAttachment(state);
        }
        return state;
    }

    protected void readTimedOut(ChannelHandlerContext ctx) throws Exception {
        Channels.fireExceptionCaught(ctx, EXCEPTION);
    }

    private final class ReadTimeoutTask implements TimerTask {

        private final ChannelHandlerContext ctx;

        ReadTimeoutTask(ChannelHandlerContext ctx) {
            this.ctx = ctx;
        }

        public void run(Timeout timeout) throws Exception {
            if (timeout.isCancelled()) {
                return;
            }

            if (!ctx.getChannel().isOpen()) {
                return;
            }

            State state = (State) ctx.getAttachment();
            long currentTime = System.currentTimeMillis();
            long nextDelay = timeoutMillis - (currentTime - state.lastReadTime);
            if (nextDelay <= 0) {
                // Read timed out - set a new timeout and notify the callback.
                state.timeout =
                    timer.newTimeout(this, timeoutMillis, TimeUnit.MILLISECONDS);
                try {
                    // FIXME This should be called from an I/O thread.
                    //       To be fixed in Netty 4.
                    readTimedOut(ctx);
                } catch (Throwable t) {
                    fireExceptionCaught(ctx, t);
                }
            } else {
                // Read occurred before the timeout - set a new timeout with shorter delay.
                state.timeout =
                    timer.newTimeout(this, nextDelay, TimeUnit.MILLISECONDS);
            }
        }
    }

    private static final class State {
        volatile Timeout timeout;
        volatile long lastReadTime = System.currentTimeMillis();
        volatile boolean destroyed;

        State() {
            super();
        }
    }
}
