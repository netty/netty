/*
 * Copyright 2024 The Netty Project
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


import io.netty.util.concurrent.Future;

/**
 * {@link EventLoop} that allows to register / deregister {@link IoHandle} instances.
 */
public interface IoEventLoop extends EventLoop, IoEventLoopGroup {

    // We only not return IoHandleEventLoopGroup here as this could break compat for people
    // that extend EventLoopGroup implementations that now implement IoHandleEventLoop (for example NioEventLoop).
    @Override
    EventLoopGroup parent();
    /**
     * @deprecated Use {@link #registerForIo(IoHandle)}.
     */
    @Deprecated
    @Override
    ChannelFuture register(Channel channel);

    /**
     * @deprecated Use {@link #registerForIo(IoHandle)}.
     */
    @Deprecated
    @Override
    ChannelFuture register(ChannelPromise promise);

    /**
     * Register the {@link IoHandle} to the {@link EventLoop} for I/O processing.
     *
     * @param handle   the {@link IoHandle} to register.
     * @return          the {@link Future} that is notified once the operations completes.
     */
    Future<Void> registerForIo(IoHandle handle);

    /**
     * Deregister the {@link Channel} from the {@link EventLoop} for I/O processing.
     *
     * @param handle   the {@link IoHandle} to deregister.
     * @return          the {@link Future} that is notified once the operations completes.
     */
    Future<Void> deregisterForIo(IoHandle handle);
}
