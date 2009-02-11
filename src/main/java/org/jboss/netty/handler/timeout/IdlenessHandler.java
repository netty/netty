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
public class IdlenessHandler extends SimpleChannelUpstreamHandler implements LifeCycleAwareChannelHandler, ExternalResourceReleasable {

    static final ReadTimeoutException EXCEPTION = new ReadTimeoutException();

    final Timer timer;

    final long readerIdleTimeMillis;
    volatile Timeout readerIdleTimeout;
    private volatile ReaderIdleTimeoutTask readerIdleTimeoutTask;
    volatile long lastReadTime;

    final long writerIdleTimeMillis;
    volatile Timeout writerIdleTimeout;
    private volatile WriterIdleTimeoutTask writerIdleTimeoutTask;
    volatile long lastWriteTime;

    public IdlenessHandler(
            Timer timer, long readerIdleTimeMillis, long writerIdleTimeMillis) {
        this(timer, readerIdleTimeMillis, writerIdleTimeMillis, TimeUnit.MILLISECONDS);
    }

    public IdlenessHandler(
            Timer timer, long readerIdleTime, long writerIdleTime, TimeUnit unit) {
        if (timer == null) {
            throw new NullPointerException("timer");
        }
        if (unit == null) {
            throw new NullPointerException("unit");
        }
        if (readerIdleTime < 0) {
            throw new IllegalArgumentException(
                    "readerIdleTime must not be less than 0: " + readerIdleTime);
        }
        if (writerIdleTime < 0) {
            throw new IllegalArgumentException(
                    "writerIdleTime must not be less than 0: " + writerIdleTime);
        }

        this.timer = timer;
        readerIdleTimeMillis = unit.toMillis(readerIdleTime);
        writerIdleTimeMillis = unit.toMillis(writerIdleTime);
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
        readerIdleTimeout = timer.newTimeout(readerIdleTimeoutTask, readerIdleTimeMillis, TimeUnit.MILLISECONDS);
        writerIdleTimeout = timer.newTimeout(writerIdleTimeoutTask, writerIdleTimeMillis, TimeUnit.MILLISECONDS);
    }

    private void destroy() {
        if (readerIdleTimeout != null) {
            readerIdleTimeout.cancel();
        }
        if (writerIdleTimeout != null) {
            writerIdleTimeout.cancel();
        }
        readerIdleTimeout = null;
        readerIdleTimeoutTask = null;
        writerIdleTimeout = null;
        writerIdleTimeoutTask = null;
    }

    private final class ReaderIdleTimeoutTask implements TimerTask {

        private final ChannelHandlerContext ctx;

        ReaderIdleTimeoutTask(ChannelHandlerContext ctx) {
            this.ctx = ctx;
        }

        public void run(Timeout timeout) throws Exception {
            if (timeout.isCancelled()) {
                return;
            }

            if (!ctx.getChannel().isOpen()) {
                return;
            }

            long currentTime = System.currentTimeMillis();
            long lastReadTime = IdlenessHandler.this.lastReadTime;
            long nextDelay = readerIdleTimeMillis - (currentTime - lastReadTime);
            if (nextDelay <= 0) {
                // Reader is idle - set a new timeout and notify the callback.
                readerIdleTimeout =
                    timer.newTimeout(this, readerIdleTimeMillis, TimeUnit.MILLISECONDS);
                ctx.sendUpstream(new DefaultIdlenessEvent(
                        ctx.getChannel(), lastReadTime, lastWriteTime,
                        readerIdleTimeMillis, writerIdleTimeMillis));
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
            if (timeout.isCancelled()) {
                return;
            }

            if (!ctx.getChannel().isOpen()) {
                return;
            }

            long currentTime = System.currentTimeMillis();
            long lastWriteTime = IdlenessHandler.this.lastWriteTime;
            long nextDelay = writerIdleTimeMillis - (currentTime - lastWriteTime);
            if (nextDelay <= 0) {
                // Writer is idle - set a new timeout and notify the callback.
                writerIdleTimeout =
                    timer.newTimeout(this, writerIdleTimeMillis, TimeUnit.MILLISECONDS);
                ctx.sendUpstream(new DefaultIdlenessEvent(
                        ctx.getChannel(), lastReadTime, lastWriteTime,
                        readerIdleTimeMillis, writerIdleTimeMillis));
            } else {
                // Write occurred before the timeout - set a new timeout with shorter delay.
                writerIdleTimeout =
                    timer.newTimeout(this, nextDelay, TimeUnit.MILLISECONDS);
            }
        }
    }
}
