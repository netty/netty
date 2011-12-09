/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel;

import java.util.concurrent.TimeUnit;

import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;

/**
 * A skeletal {@link ChannelFuture} implementation which represents a
 * {@link ChannelFuture} which has been completed already.
 *
 * @author <a href="http://netty.io/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 */
public abstract class CompleteChannelFuture implements ChannelFuture {

    private static final InternalLogger logger =
        InternalLoggerFactory.getInstance(CompleteChannelFuture.class);

    private final Channel channel;

    /**
     * Creates a new instance.
     *
     * @param channel the {@link Channel} associated with this future
     */
    protected CompleteChannelFuture(Channel channel) {
        if (channel == null) {
            throw new NullPointerException("channel");
        }
        this.channel = channel;
    }

    @Override
    public void addListener(ChannelFutureListener listener) {
        try {
            listener.operationComplete(this);
        } catch (Throwable t) {
            logger.warn(
                    "An exception was thrown by " +
                    ChannelFutureListener.class.getSimpleName() + ".", t);
        }
    }

    @Override
    public void removeListener(ChannelFutureListener listener) {
        // NOOP
    }

    @Override
    public ChannelFuture await() throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        return this;
    }

    @Override
    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        return true;
    }

    @Override
    public boolean await(long timeoutMillis) throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        return true;
    }

    @Override
    public ChannelFuture awaitUninterruptibly() {
        return this;
    }

    @Override
    public boolean awaitUninterruptibly(long timeout, TimeUnit unit) {
        return true;
    }

    @Override
    public boolean awaitUninterruptibly(long timeoutMillis) {
        return true;
    }

    @Override
    public Channel getChannel() {
        return channel;
    }

    @Override
    public boolean isDone() {
        return true;
    }

    @Override
    public boolean setProgress(long amount, long current, long total) {
        return false;
    }

    @Override
    public boolean setFailure(Throwable cause) {
        return false;
    }

    @Override
    public boolean setSuccess() {
        return false;
    }

    @Override
    public boolean cancel() {
        return false;
    }

    @Override
    public boolean isCancelled() {
        return false;
    }
}
