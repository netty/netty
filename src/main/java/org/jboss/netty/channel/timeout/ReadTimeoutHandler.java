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
package org.jboss.netty.channel.timeout;

import java.util.concurrent.TimeUnit;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.LifeCycleAwareChannelHandler;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.util.ExternalResourceReleasable;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
@ChannelPipelineCoverage("one")
public class ReadTimeoutHandler extends SimpleChannelUpstreamHandler implements LifeCycleAwareChannelHandler, ExternalResourceReleasable {

    static final ChannelReadTimeoutException EXCEPTION = new ChannelReadTimeoutException();

    final Timer timer;
    final long timeoutNanos;
    private volatile Timeout timeout;
    private volatile ReadTimeoutTask task;
    volatile long lastReadTime;

    public ReadTimeoutHandler(
            Timer timer, long timeout, TimeUnit unit) {
        if (timer == null) {
            throw new NullPointerException("timer");
        }
        if (unit == null) {
            throw new NullPointerException("unit");
        }
        if (timeout <= 0) {
            throw new IllegalArgumentException(
                    "timeout must be greater than 0: " + timeout);
        }

        this.timer = timer;
        timeoutNanos = unit.toNanos(timeout);
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
        super.channelOpen(ctx, e);
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
            throws Exception {
        destroy();
        super.channelOpen(ctx, e);
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
            throws Exception {
        lastReadTime = System.nanoTime();
        super.messageReceived(ctx, e);
    }

    private void initialize(ChannelHandlerContext ctx) {
        lastReadTime = System.nanoTime();
        task = new ReadTimeoutTask(ctx);
        timeout = timer.newTimeout(task, timeoutNanos, TimeUnit.NANOSECONDS);
    }

    private void destroy() {
        if (timeout != null) {
            timeout.cancel();
        }
        timeout = null;
        task = null;
    }

    private class ReadTimeoutTask implements TimerTask {

        private final ChannelHandlerContext ctx;

        ReadTimeoutTask(ChannelHandlerContext ctx) {
            this.ctx = ctx;
        }

        public void run(Timeout timeout) throws Exception {
            if (timeout.isCancelled()) {
                return;
            }

            long currentTime = System.nanoTime();
            long nextDelay = timeoutNanos - (currentTime - lastReadTime);
            if (nextDelay <= 0) {
                // Read timed out - set a new timeout and notify the callback.
                timeout = timer.newTimeout(this, timeoutNanos, TimeUnit.NANOSECONDS);
                Channels.fireExceptionCaught(ctx, EXCEPTION);
            } else {
                // Read occurred before the timer - set a new timeout with shorter delay.
                timeout = timer.newTimeout(this, nextDelay, TimeUnit.NANOSECONDS);
            }
        }
    }
}
