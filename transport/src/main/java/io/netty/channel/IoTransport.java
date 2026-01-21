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

import io.netty.util.concurrent.Promise;

import java.net.SocketAddress;

/**
 * Transport which actually submit IO operations to the underlying OS.
 */
public interface IoTransport {

    /**
     * Register the {@link Channel} of the {@link Promise} and notify
     * the {@link Promise} once the registration was complete.
     */
    void register(Promise<Void> promise);

    /**
     * Bind the {@link SocketAddress} to the {@link Channel} of the {@link Promise} and notify
     * it once its done.
     */
    void bind(SocketAddress localAddress, Promise<Void> promise);

    /**
     * Connect the {@link Channel} with the given remote {@link SocketAddress}.
     * If a specific local {@link SocketAddress} should be used it need to be given as argument. Otherwise just
     * pass {@code null} to it.
     * <p>
     * The {@link Promise} will get notified once the connect operation was complete.
     */
    void connect(SocketAddress remoteAddress, SocketAddress localAddress, Promise<Void> promise);

    /**
     * Disconnect the {@link Channel} and notify the {@link Promise} once the
     * operation was complete.
     */
    void disconnect(Promise<Void> promise);

    /**
     * Close the {@link Channel} and notify the {@link Promise} once the
     * operation was complete.
     */
    void close(Promise<Void> promise);

    /**
     * Deregister the {@link Channel} from {@link EventLoop} and notify the
     * {@link Promise} once the operation was complete.
     */
    void deregister(Promise<Void> promise);

    /**
     * Schedules a read operation that fills the inbound buffer of the first {@link ChannelInboundHandler} in the
     * {@link ChannelPipeline}.  If there's already a pending read operation, this method does nothing.
     */
    void read();

    /**
     * Schedules a write operation.
     */
    void write(Object msg, Promise<Void> promise);

    /**
     * Flush out all write operations scheduled via {@link #write(Object, Promise)}.
     */
    void flush();

    /**
     * Shutdown the {@link ChannelShutdownType} if the {@link Channel} and notify the {@link Promise}
     * once the operation was complete.
     */
    void shutdown(ChannelShutdownType type, Promise<Void> promise);
}
