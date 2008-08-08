/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.netty.channel;

import java.util.concurrent.TimeUnit;

import org.jboss.netty.logging.Logger;

public abstract class CompleteChannelFuture implements ChannelFuture {

    private static final Logger logger =
        Logger.getLogger(CompleteChannelFuture.class);

    private final Channel channel;

    protected CompleteChannelFuture(Channel channel) {
        if (channel == null) {
            throw new NullPointerException("channel");
        }
        this.channel = channel;
    }

    public void addListener(ChannelFutureListener listener) {
        try {
            listener.operationComplete(this);
        } catch (Throwable t) {
            logger.warn(
                    "An exception was thrown by " +
                    ChannelFutureListener.class.getSimpleName() + ".", t);
        }
    }

    public void removeListener(ChannelFutureListener listener) {
        // NOOP
    }

    public ChannelFuture await() throws InterruptedException {
        return this;
    }

    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        return true;
    }

    public boolean await(long timeoutMillis) throws InterruptedException {
        return true;
    }

    public ChannelFuture awaitUninterruptibly() {
        return this;
    }

    public boolean awaitUninterruptibly(long timeout, TimeUnit unit) {
        return true;
    }

    public boolean awaitUninterruptibly(long timeoutMillis) {
        return true;
    }

    public Channel getChannel() {
        return channel;
    }

    public boolean isDone() {
        return true;
    }

    public void setFailure(Throwable cause) {
        // Unused
    }

    public void setSuccess() {
        // Unused
    }

    public boolean cancel() {
        return false;
    }

    public boolean isCancelled() {
        return false;
    }
}
