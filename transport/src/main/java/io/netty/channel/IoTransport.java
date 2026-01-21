/*
 * Copyright 2025 The Netty Project
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

import io.netty.util.concurrent.CompletionHandler;

import java.net.SocketAddress;

/**
 * Transport which actually submit IO operations to the underlying OS.
 */
public interface IoTransport {

    /**
     * Register the transport and notify
     * the {@link CompletionHandler} once the registration was complete.
     */
    void register(CompletionHandler<Void> handler);

    /**
     * Bind the {@link SocketAddress} to the transport and notify the {@link CompletionHandler}
     * once it's done.
     */
    void bind(SocketAddress localAddress, CompletionHandler<Void> handler);

    /**
     * Connect the transport with the given remote {@link SocketAddress}.
     * If a specific local {@link SocketAddress} should be used it need to be given as argument. Otherwise just
     * pass {@code null} to it.
     * <p>
     * The {@link CompletionHandler} will get notified once the connect operation was complete.
     */
    void connect(SocketAddress remoteAddress, SocketAddress localAddress, CompletionHandler<Void> handler);

    /**
     * Disconnect the transport and notify the {@link CompletionHandler} once the
     * operation was complete.
     */
    void disconnect(CompletionHandler<Void> handler);

    /**
     * Close the transport and notify the {@link CompletionHandler} once the
     * operation was complete.
     */
    void close(CompletionHandler<Void> handler);

    /**
     * Deregister the transport from {@link EventLoop} and notify the
     * {@link CompletionHandler} once the operation was complete.
     */
    void deregister(CompletionHandler<Void> handler);

    /**
     * Schedules a read operation that fills the inbound buffer of the first {@link ChannelInboundHandler} in the
     * {@link ChannelPipeline}.  If there's already a pending read operation, this method does nothing.
     */
    void read();

    /**
     * Schedules a write operation.
     */
    void write(Object msg, CompletionHandler<Void> handler);

    /**
     * Flush out all write operations scheduled via {@link #write(Object, CompletionHandler)}.
     */
    void flush();

    /**
     * Shutdown the {@link ChannelShutdownType} if the transport and notify the {@link CompletionHandler}
     * once the operation was complete.
     */
    void shutdown(ChannelShutdownType type, CompletionHandler<Void> handler);
}
