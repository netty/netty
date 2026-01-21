/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel;

import io.netty.util.concurrent.Promise;

import java.net.SocketAddress;

/**
 * A skeletal server-side {@link Channel} implementation.  A server-side
 * {@link Channel} does not allow the following operations:
 * <ul>
 * <li>{@link ChannelOutboundInvoker#connect(SocketAddress, io.netty.util.concurrent.CompletionHandler)}</li>
 * <li>{@link ChannelOutboundInvoker#disconnect(io.netty.util.concurrent.CompletionHandler)}</li>
 * <li>{@link ChannelOutboundInvoker#shutdown(ChannelShutdownType, io.netty.util.concurrent.CompletionHandler)}</li>
 * <li>{@link ChannelOutboundInvoker#write(Object, io.netty.util.concurrent.CompletionHandler)}</li>
 * <li>{@link #flush()}</li>
 * <li>and the shortcut methods which calls the methods mentioned above
 * </ul>
 */
public abstract class AbstractServerChannel extends AbstractChannel implements ServerChannel {

    private final EventLoopGroup childEventLoopGroup;

    /**
     * Creates a new instance.
     */
    protected AbstractServerChannel(EventLoop eventLoop, EventLoopGroup childEventLoopGroup,
                                    Class<? extends IoHandle> handleType) {
        super(eventLoop, handleType, null);
        this.childEventLoopGroup = validateEventLoopGroup(childEventLoopGroup, "childEventLoopGroup", handleType);
    }

    @Override
    public EventLoopGroup childEventExecutorGroup() {
        return childEventLoopGroup;
    }

    @Override
    public SocketAddress remoteAddress() {
        return null;
    }

    @Override
    protected SocketAddress remoteAddress0() {
        return null;
    }

    @Override
    protected void doShutdown(ChannelShutdownType type, Promise<Void> promise) {
        promise.setFailure(new UnsupportedOperationException());
    }

    @Override
    protected void doDisconnect(Promise<Void> promise)  {
        promise.setFailure(new UnsupportedOperationException());
    }

    @Override
    protected void doConnect(SocketAddress remoteAddress, SocketAddress localAddress, Promise<Void> promise) {
        promise.setFailure(new UnsupportedOperationException());
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in)  {
        throw new UnsupportedOperationException();
    }

    @Override
    protected final Object filterOutboundMessage(Object msg) {
        throw new UnsupportedOperationException();
    }
}
