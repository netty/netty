/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.sockjs.util;

import io.netty.channel.Channel.Unsafe;
import io.netty.channel.ChannelHandlerInvoker;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.channel.RecvByteBufAllocator.Handle;
import io.netty.handler.codec.sockjs.util.StubEmbeddedEventLoop.SchedulerExecutor;

import java.net.SocketAddress;

/**
 * A Unsafe implementation that is intended to be used by tests.
 */
public abstract class AbstractTestUnsafe implements Unsafe {

    protected final Unsafe delegate;

    protected AbstractTestUnsafe(final Unsafe delegate) {
        this.delegate = delegate;
    }

    /**
     * Will be called by {@link #register(EventLoop, ChannelPromise)} and the returned
     * {@link SchedulerExecutor} instance will be passed into {@link StubEmbeddedEventLoop}
     * and this instance will handle the various schedule methods (scheduleAtFixedRate, etc)
     *
     * @return {@link SchedulerExecutor}
     */
    public abstract SchedulerExecutor createSchedulerExecutor();

    @Override
    public void register(EventLoop eventLoop, ChannelPromise promise) {
        delegate.register(new StubEmbeddedEventLoop(eventLoop, createSchedulerExecutor()), promise);
    }

    @Override
    public Handle recvBufAllocHandle() {
        return delegate.recvBufAllocHandle();
    }

    @Override
    public ChannelHandlerInvoker invoker() {
        return delegate.invoker();
    }

    @Override
    public SocketAddress localAddress() {
        return delegate.localAddress();
    }

    @Override
    public SocketAddress remoteAddress() {
        return delegate.remoteAddress();
    }

    @Override
    public void bind(SocketAddress localAddress, ChannelPromise promise) {
        delegate.bind(localAddress, promise);
    }

    @Override
    public void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
        delegate.connect(remoteAddress, localAddress, promise);
    }

    @Override
    public void disconnect(ChannelPromise promise) {
        delegate.disconnect(promise);
    }

    @Override
    public void close(ChannelPromise promise) {
        delegate.close(promise);
    }

    @Override
    public void closeForcibly() {
        delegate.closeForcibly();
    }

    @Override
    public void deregister(ChannelPromise promise) {
        delegate.deregister(promise);
    }

    @Override
    public void beginRead() {
        delegate.beginRead();
    }

    @Override
    public void write(Object msg, ChannelPromise promise) {
        delegate.write(msg, promise);
    }

    @Override
    public void flush() {
        delegate.flush();
    }

    @Override
    public ChannelPromise voidPromise() {
        return delegate.voidPromise();
    }

    @Override
    public ChannelOutboundBuffer outboundBuffer() {
        return delegate.outboundBuffer();
    }
}
