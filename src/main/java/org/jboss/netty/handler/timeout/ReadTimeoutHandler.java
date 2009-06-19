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
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.LifeCycleAwareChannelHandler;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.util.ExternalResourceReleasable;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.Timer;
import org.jboss.netty.util.TimerTask;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
@ChannelPipelineCoverage("one")
public class ReadTimeoutHandler extends SimpleChannelUpstreamHandler
                                implements LifeCycleAwareChannelHandler,
                                           ExternalResourceReleasable {

    static final ReadTimeoutException EXCEPTION = new ReadTimeoutException();

    final Timer timer;
    final long timeoutMillis;
    volatile Timeout timeout;
    private volatile ReadTimeoutTask task;
    volatile long lastReadTime;

    public ReadTimeoutHandler(Timer timer, int timeoutSeconds) {
        this(timer, timeoutSeconds, TimeUnit.SECONDS);
    }

    public ReadTimeoutHandler(Timer timer, long timeout, TimeUnit unit) {
        if (timer == null) {
            throw new NullPointerException("timer");
        }
        if (unit == null) {
            throw new NullPointerException("unit");
        }

        this.timer = timer;
        timeoutMillis = unit.toMillis(timeout);
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

    private void initialize(ChannelHandlerContext ctx) {
        lastReadTime = System.currentTimeMillis();
        task = new ReadTimeoutTask(ctx);
        if (timeoutMillis > 0) {
            timeout = timer.newTimeout(task, timeoutMillis, TimeUnit.MILLISECONDS);
        }
    }

    private void destroy() {
        if (timeout != null) {
            timeout.cancel();
        }
        timeout = null;
        task = null;
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

            long currentTime = System.currentTimeMillis();
            long nextDelay = timeoutMillis - (currentTime - lastReadTime);
            if (nextDelay <= 0) {
                // Read timed out - set a new timeout and notify the callback.
                ReadTimeoutHandler.this.timeout =
                    timer.newTimeout(this, timeoutMillis, TimeUnit.MILLISECONDS);
                try {
                    readTimedOut(ctx);
                } catch (Throwable t) {
                    fireExceptionCaught(ctx, t);
                }
            } else {
                // Read occurred before the timeout - set a new timeout with shorter delay.
                ReadTimeoutHandler.this.timeout =
                    timer.newTimeout(this, nextDelay, TimeUnit.MILLISECONDS);
            }
        }
    }
}
