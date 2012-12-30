/*
 * Copyright 2012 The Netty Project
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

import java.net.SocketAddress;

/**
 * Interface which is shared by others which need to execute outbound logic.
 */
public interface ChannelOutboundInvoker {

    /**
     * Bind to the given {@link SocketAddress} and notify the {@link ChannelFuture} once the operation completes,
     * either because the operation was successful or because of an error.
     */
    ChannelFuture bind(SocketAddress localAddress);

    /**
     * Connect to the given {@link SocketAddress} and notify the {@link ChannelFuture} once the operation completes,
     * either because the operation was successful or because of
     * an error.
     */
    ChannelFuture connect(SocketAddress remoteAddress);

    /**
     * Connect to the given {@link SocketAddress} while bind to the localAddress and notify the {@link ChannelFuture}
     * once the operation completes, either because the operation was successful or because of
     * an error.
     */
    ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress);

    /**
     * Discconect from the remote peer and notify the {@link ChannelFuture} once the operation completes,
     * either because the operation was successful or because of
     * an error.
     */
    ChannelFuture disconnect();

    /**
     * Close this ChannelOutboundInvoker and notify the {@link ChannelFuture} once the operation completes,
     * either because the operation was successful or because of
     * an error.
     *
     * After it is closed it is not possible to reuse it again.
     */
    ChannelFuture close();

    /**
     * Deregister this ChannelOutboundInvoker from the previous assigned {@link EventExecutor} and notify the
     * {@link ChannelFuture} once the operation completes, either because the operation was successful or because of
     * an error.
     *
     */
    ChannelFuture deregister();

    /**
     * Flush all pending data which belongs to this ChannelOutboundInvoker and notify the {@link ChannelFuture}
     * once the operation completes, either because the operation was successful or because of an error.
     */
    ChannelFuture flush();

    /**
     * Write a message via this ChannelOutboundInvoker and notify the {@link ChannelFuture}
     * once the operation completes, either because the operation was successful or because of an error.
     *
     * If you want to write a {@link FileRegion} use {@link #sendFile(FileRegion)}
     */
    ChannelFuture write(Object message);

    /**
     * Send a {@link FileRegion} via this ChannelOutboundInvoker and notify the {@link ChannelFuture}
     * once the operation completes, either because the operation was successful or because of an error.
     */
    ChannelFuture sendFile(FileRegion region);

    /**
     * Bind to the given {@link SocketAddress} and notify the {@link ChannelPromise} once the operation completes,
     * either because the operation was successful or because of an error.
     *
     * The given {@link ChannelPromise} will be notified.
     */
    ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise);

    /**
     * Connect to the given {@link SocketAddress} and notify the {@link ChannelPromise} once the operation completes,
     * either because the operation was successful or because of
     * an error.
     *
     * The given {@link ChannelFuture} will be notified.
     */
    ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise);

    /**
     * Connect to the given {@link SocketAddress} while bind to the localAddress and notify the {@link ChannelPromise}
     * once the operation completes, either because the operation was successful or because of
     * an error.
     *
     * The given {@link ChannelPromise} will be notified and also returned.
     */
    ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise);

    /**
     * Discconect from the remote peer and notify the {@link ChannelPromise} once the operation completes,
     * either because the operation was successful or because of
     * an error.
     *
     * The given {@link ChannelPromise} will be notified.
     */
    ChannelFuture disconnect(ChannelPromise promise);

    /**
     * Close this ChannelOutboundInvoker and notify the {@link ChannelPromise} once the operation completes,
     * either because the operation was successful or because of
     * an error.
     *
     * After it is closed it is not possible to reuse it again.
     * The given {@link ChannelPromise} will be notified.
     */
    ChannelFuture close(ChannelPromise promise);

    /**
     * Deregister this ChannelOutboundInvoker from the previous assigned {@link EventExecutor} and notify the
     * {@link ChannelFuture} once the operation completes, either because the operation was successful or because of
     * an error.
     *
     * The given {@link ChannelPromise} will be notified.
     */
    ChannelFuture deregister(ChannelPromise promise);

    /**
     * Reads data from the {@link Channel} into the first inbound buffer, triggers an
     * {@link ChannelStateHandler#inboundBufferUpdated(ChannelHandlerContext) inboundBufferUpdated} event if data was
     * read, and triggers an
     * {@link ChannelStateHandler#inboundBufferSuspended(ChannelHandlerContext) inboundBufferSuspended} event so the
     * handler can decide to continue reading.  If there's a pending read operation already, this method does nothing.
     */
    void read();

    /**
     * Flush all pending data which belongs to this ChannelOutboundInvoker and notify the {@link ChannelPromise}
     * once the operation completes, either because the operation was successful or because of an error.
     *
     * The given {@link ChannelPromise} will be notified.
     */
    ChannelFuture flush(ChannelPromise promise);

    /**
     * Write a message via this ChannelOutboundInvoker and notify the {@link ChannelPromise}
     * once the operation completes, either because the operation was successful or because of an error.
     *
     * If you want to write a {@link FileRegion} use {@link #sendFile(FileRegion)}
     * The given {@link ChannelPromise} will be notified and also returned.
     */
    ChannelFuture write(Object message, ChannelPromise promise);

    /**
     * Send a {@link FileRegion} via this ChannelOutboundInvoker and notify the {@link ChannelPromise}
     * once the operation completes, either because the operation was successful or because of an error.
     *
     * The given {@link ChannelPromise} will be notified.
     */
    ChannelFuture sendFile(FileRegion region, ChannelPromise promise);
}
