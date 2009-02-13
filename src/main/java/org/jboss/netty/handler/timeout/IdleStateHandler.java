/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.handler.timeout;

import static org.jboss.netty.channel.Channels.*;

import java.util.concurrent.TimeUnit;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.LifeCycleAwareChannelHandler;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.WriteCompletionEvent;
import org.jboss.netty.util.ExternalResourceReleasable;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
@ChannelPipelineCoverage("one")
public class IdleStateHandler extends SimpleChannelUpstreamHandler
                             implements LifeCycleAwareChannelHandler,
                                        ExternalResourceReleasable {

    final Timer timer;

    final long readerIdleTimeMillis;
    volatile Timeout readerIdleTimeout;
    private volatile ReaderIdleTimeoutTask readerIdleTimeoutTask;
    volatile long lastReadTime;

    final long writerIdleTimeMillis;
    volatile Timeout writerIdleTimeout;
    private volatile WriterIdleTimeoutTask writerIdleTimeoutTask;
    volatile long lastWriteTime;

    final long allIdleTimeMillis;
    volatile Timeout allIdleTimeout;
    private volatile AllIdleTimeoutTask allIdleTimeoutTask;

    public IdleStateHandler(
            Timer timer,
            int readerIdleTimeSeconds,
            int writerIdleTimeSeconds,
            int allIdleTimeSeconds) {

        this(timer,
             readerIdleTimeSeconds, writerIdleTimeSeconds, allIdleTimeSeconds,
             TimeUnit.SECONDS);
    }

    public IdleStateHandler(
            Timer timer,
            long readerIdleTime, long writerIdleTime, long allIdleTime,
            TimeUnit unit) {

        if (timer == null) {
            throw new NullPointerException("timer");
        }
        if (unit == null) {
            throw new NullPointerException("unit");
        }

        this.timer = timer;
        readerIdleTimeMillis = unit.toMillis(readerIdleTime);
        writerIdleTimeMillis = unit.toMillis(writerIdleTime);
        allIdleTimeMillis = unit.toMillis(allIdleTime);
    }

    public void releaseExternalResources() {
        timer.stop();
    }

    public void beforeAdd(ChannelHandlerContext ctx) throws Exception {
        initialize(ctx);
    }

    public void afterAdd(ChannelHandlerContext ctx) throws Exception {
        // NOOP
    }

    public void beforeRemove(ChannelHandlerContext ctx) throws Exception {
        destroy();
    }

    public void afterRemove(ChannelHandlerContext ctx) throws Exception {
        // NOOP
    }

    @Override
    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception {
        initialize(ctx);
        ctx.sendUpstream(e);
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception {
        destroy();
        ctx.sendUpstream(e);
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception {
        lastReadTime = System.currentTimeMillis();
        ctx.sendUpstream(e);
    }

    @Override
    public void writeComplete(ChannelHandlerContext ctx, WriteCompletionEvent e)
            throws Exception {
        if (e.getWrittenAmount() > 0) {
            lastWriteTime = System.currentTimeMillis();
        }
        ctx.sendUpstream(e);
    }

    private void initialize(ChannelHandlerContext ctx) {
        lastReadTime = lastWriteTime = System.currentTimeMillis();
        readerIdleTimeoutTask = new ReaderIdleTimeoutTask(ctx);
        writerIdleTimeoutTask = new WriterIdleTimeoutTask(ctx);
        allIdleTimeoutTask = new AllIdleTimeoutTask(ctx);
        if (readerIdleTimeMillis > 0) {
            readerIdleTimeout = timer.newTimeout(
                    readerIdleTimeoutTask, readerIdleTimeMillis, TimeUnit.MILLISECONDS);
        }
        if (writerIdleTimeMillis > 0) {
            writerIdleTimeout = timer.newTimeout(
                    writerIdleTimeoutTask, writerIdleTimeMillis, TimeUnit.MILLISECONDS);
        }
        if (allIdleTimeMillis > 0) {
            allIdleTimeout = timer.newTimeout(
                    allIdleTimeoutTask, allIdleTimeMillis, TimeUnit.MILLISECONDS);
        }
    }

    private void destroy() {
        if (readerIdleTimeout != null) {
            readerIdleTimeout.cancel();
        }
        if (writerIdleTimeout != null) {
            writerIdleTimeout.cancel();
        }
        if (allIdleTimeout != null) {
            allIdleTimeout.cancel();
        }
        readerIdleTimeout = null;
        readerIdleTimeoutTask = null;
        writerIdleTimeout = null;
        writerIdleTimeoutTask = null;
        allIdleTimeout = null;
        allIdleTimeoutTask = null;
    }

    protected void channelIdle(
            ChannelHandlerContext ctx, IdleState state, long lastActivityTimeMillis) throws Exception {
        ctx.sendUpstream(new DefaultIdleStateEvent(ctx.getChannel(), state, lastActivityTimeMillis));
    }

    private final class ReaderIdleTimeoutTask implements TimerTask {

        private final ChannelHandlerContext ctx;

        ReaderIdleTimeoutTask(ChannelHandlerContext ctx) {
            this.ctx = ctx;
        }

        public void run(Timeout timeout) throws Exception {
            if (timeout.isCancelled() || !ctx.getChannel().isOpen()) {
                return;
            }

            long currentTime = System.currentTimeMillis();
            long lastReadTime = IdleStateHandler.this.lastReadTime;
            long nextDelay = readerIdleTimeMillis - (currentTime - lastReadTime);
            if (nextDelay <= 0) {
                // Reader is idle - set a new timeout and notify the callback.
                readerIdleTimeout =
                    timer.newTimeout(this, readerIdleTimeMillis, TimeUnit.MILLISECONDS);
                try {
                    channelIdle(ctx, IdleState.READER_IDLE, lastReadTime);
                } catch (Throwable t) {
                    fireExceptionCaught(ctx, t);
                }
            } else {
                // Read occurred before the timeout - set a new timeout with shorter delay.
                readerIdleTimeout =
                    timer.newTimeout(this, nextDelay, TimeUnit.MILLISECONDS);
            }
        }

    }

    private final class WriterIdleTimeoutTask implements TimerTask {

        private final ChannelHandlerContext ctx;

        WriterIdleTimeoutTask(ChannelHandlerContext ctx) {
            this.ctx = ctx;
        }

        public void run(Timeout timeout) throws Exception {
            if (timeout.isCancelled() || !ctx.getChannel().isOpen()) {
                return;
            }

            long currentTime = System.currentTimeMillis();
            long lastWriteTime = IdleStateHandler.this.lastWriteTime;
            long nextDelay = writerIdleTimeMillis - (currentTime - lastWriteTime);
            if (nextDelay <= 0) {
                // Writer is idle - set a new timeout and notify the callback.
                writerIdleTimeout =
                    timer.newTimeout(this, writerIdleTimeMillis, TimeUnit.MILLISECONDS);
                try {
                    channelIdle(ctx, IdleState.WRITER_IDLE, lastReadTime);
                } catch (Throwable t) {
                    fireExceptionCaught(ctx, t);
                }
            } else {
                // Write occurred before the timeout - set a new timeout with shorter delay.
                writerIdleTimeout =
                    timer.newTimeout(this, nextDelay, TimeUnit.MILLISECONDS);
            }
        }
    }

    private final class AllIdleTimeoutTask implements TimerTask {

        private final ChannelHandlerContext ctx;

        AllIdleTimeoutTask(ChannelHandlerContext ctx) {
            this.ctx = ctx;
        }

        public void run(Timeout timeout) throws Exception {
            if (timeout.isCancelled() || !ctx.getChannel().isOpen()) {
                return;
            }

            long currentTime = System.currentTimeMillis();
            long lastIoTime = Math.max(lastReadTime, lastWriteTime);
            long nextDelay = allIdleTimeMillis - (currentTime - lastIoTime);
            if (nextDelay <= 0) {
                // Both reader and writer are idle - set a new timeout and
                // notify the callback.
                allIdleTimeout =
                    timer.newTimeout(this, allIdleTimeMillis, TimeUnit.MILLISECONDS);
                try {
                    channelIdle(ctx, IdleState.ALL_IDLE, lastReadTime);
                } catch (Throwable t) {
                    fireExceptionCaught(ctx, t);
                }
            } else {
                // Either read or write occurred before the timeout - set a new
                // timeout with shorter delay.
                allIdleTimeout =
                    timer.newTimeout(this, nextDelay, TimeUnit.MILLISECONDS);
            }
        }
    }
}
