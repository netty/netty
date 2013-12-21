/*
 * Copyright 2013 The Netty Project
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

package io.netty.channel;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.internal.StringUtil;

import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;

/**
 * A {@link Channel} that represents a non-existing {@link Channel} which could not be instantiated successfully.
 */
public final class VoidChannel extends AbstractChannel {

    public static final VoidChannel INSTANCE = new VoidChannel();

    private VoidChannel() {
        super(null, new AbstractEventLoop(null) {
            private final ChannelHandlerInvoker invoker =
                    new DefaultChannelHandlerInvoker(GlobalEventExecutor.INSTANCE);

            @Override
            @Deprecated
            public void shutdown() {
                GlobalEventExecutor.INSTANCE.shutdown();
            }

            @Override
            public ChannelHandlerInvoker asInvoker() {
                return invoker;
            }

            @Override
            public boolean inEventLoop(Thread thread) {
                return GlobalEventExecutor.INSTANCE.inEventLoop(thread);
            }

            @Override
            public boolean isShuttingDown() {
                return GlobalEventExecutor.INSTANCE.isShuttingDown();
            }

            @Override
            public Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
                return GlobalEventExecutor.INSTANCE.shutdownGracefully(quietPeriod, timeout, unit);
            }

            @Override
            public Future<?> terminationFuture() {
                return GlobalEventExecutor.INSTANCE.terminationFuture();
            }

            @Override
            public boolean isShutdown() {
                return GlobalEventExecutor.INSTANCE.isShutdown();
            }

            @Override
            public boolean isTerminated() {
                return GlobalEventExecutor.INSTANCE.isTerminated();
            }

            @Override
            public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
                return GlobalEventExecutor.INSTANCE.awaitTermination(timeout, unit);
            }

            @Override
            public void execute(Runnable command) {
                GlobalEventExecutor.INSTANCE.execute(command);
            }
        });
    }

    @Override
    protected AbstractUnsafe newUnsafe() {
        return new AbstractUnsafe() {
            @Override
            public void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
                reject();
            }
        };
    }

    @Override
    protected boolean isCompatible(EventLoop loop) {
        return true;
    }

    @Override
    protected SocketAddress localAddress0() {
        return reject();
    }

    @Override
    protected SocketAddress remoteAddress0() {
        return reject();
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        reject();
    }

    @Override
    protected void doDisconnect() throws Exception {
        reject();
    }

    @Override
    protected void doClose() throws Exception {
        reject();
    }

    @Override
    protected void doBeginRead() throws Exception {
        reject();
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        reject();
    }

    @Override
    public ChannelConfig config() {
        return reject();
    }

    @Override
    public boolean isOpen() {
        return reject();
    }

    @Override
    public boolean isActive() {
        return reject();
    }

    @Override
    public ChannelMetadata metadata() {
        return reject();
    }

    @Override
    public String toString() {
        return StringUtil.simpleClassName(this);
    }

    private static <T> T reject() {
        throw new UnsupportedOperationException(
                StringUtil.simpleClassName(VoidChannel.class) +
                " is only for the representation of a non-existing " +
                StringUtil.simpleClassName(Channel.class) + '.');
    }
}
