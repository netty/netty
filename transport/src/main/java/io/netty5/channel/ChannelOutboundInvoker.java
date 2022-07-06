/*
 * Copyright 2016 The Netty Project
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
package io.netty5.channel;

import io.netty5.util.concurrent.EventExecutor;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.FuturePromiseFactory;
import io.netty5.util.concurrent.Promise;

import java.net.ConnectException;
import java.net.SocketAddress;

public interface ChannelOutboundInvoker extends FuturePromiseFactory {

    /**
     * Request to bind to the given {@link SocketAddress} and notify the {@link Future} once the operation
     * completes, either because the operation was successful or because of an error.
     * <p>
     * This will result in having the
     * {@link ChannelHandler#bind(ChannelHandlerContext, SocketAddress)} method
     * called of the next {@link ChannelHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    Future<Void> bind(SocketAddress localAddress);

    /**
     * Request to connect to the given {@link SocketAddress} and notify the {@link Future} once the operation
     * completes, either because the operation was successful or because of an error.
     * <p>
     * If the connection fails because of a connection timeout, the {@link Future} will get failed with
     * a {@link ConnectTimeoutException}. If it fails because of connection refused a {@link ConnectException}
     * will be used.
     * <p>
     * This will result in having the
     * {@link ChannelHandler#connect(ChannelHandlerContext, SocketAddress, SocketAddress)}
     * method called of the next {@link ChannelHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    Future<Void> connect(SocketAddress remoteAddress);

    /**
     * Request to connect to the given {@link SocketAddress} while bind to the localAddress and notify the
     * {@link Future} once the operation completes, either because the operation was successful or because of
     * an error.
     * <p>
     * This will result in having the
     * {@link ChannelHandler#connect(ChannelHandlerContext, SocketAddress, SocketAddress)}
     * method called of the next {@link ChannelHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    Future<Void> connect(SocketAddress remoteAddress, SocketAddress localAddress);

    /**
     * Request to disconnect from the remote peer and notify the {@link Future} once the operation completes,
     * either because the operation was successful or because of an error.
     * <p>
     * This will result in having the
     * {@link ChannelHandler#disconnect(ChannelHandlerContext)}
     * method called of the next {@link ChannelHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    Future<Void> disconnect();

    /**
     * Request to close the {@link Channel} and notify the {@link Future} once the operation completes,
     * either because the operation was successful or because of
     * an error.
     *
     * After it is closed it is not possible to reuse it again.
     * <p>
     * This will result in having the
     * {@link ChannelHandler#close(ChannelHandlerContext)}
     * method called of the next {@link ChannelHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    Future<Void> close();

    /**
     * Request shutdown one direction of the {@link Channel} and notify the {@link Future} once the operation completes,
     * either because the operation was successful or because of an error.
     * <p>
     * When completed, the channel will either not produce any inbound data anymore, or it will not be
     * possible to write data anymore, depending on the given {@link ChannelShutdownDirection}.
     * <p>
     * Depending on the transport implementation shutting down the {@link ChannelShutdownDirection#Outbound}
     * or {@link ChannelShutdownDirection#Inbound} might also result in data transferred over the network. Like for
     * example in case of TCP shutting down the {@link ChannelShutdownDirection#Outbound} will result in a {@code FIN}
     * that is transmitted to the remote peer that will as a result shutdown {@link ChannelShutdownDirection#Inbound}.
     * <p>
     * This will result in having the
     * {@link ChannelHandler#shutdown(ChannelHandlerContext, ChannelShutdownDirection)}.
     * method called of the next {@link ChannelHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    Future<Void> shutdown(ChannelShutdownDirection direction);

    /**
     * Request to register on the {@link EventExecutor} for I/O processing.
     * {@link Future} once the operation completes, either because the operation was successful or because of
     * an error.
     * <p>
     * This will result in having the
     * {@link ChannelHandler#register(ChannelHandlerContext)}
     * method called of the next {@link ChannelHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    Future<Void> register();

    /**
     * Request to deregister from the previous assigned {@link EventExecutor} and notify the
     * {@link Future} once the operation completes, either because the operation was successful or because of
     * an error.
     * <p>
     * This will result in having the
     * {@link ChannelHandler#deregister(ChannelHandlerContext)}
     * method called of the next {@link ChannelHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     *
     */
    Future<Void> deregister();

    /**
     * Request to Read data from the {@link Channel} into the first inbound buffer, triggers an
     * {@link ChannelHandler#channelRead(ChannelHandlerContext, Object)} event if data was
     * read, and triggers a
     * {@link ChannelHandler#channelReadComplete(ChannelHandlerContext) channelReadComplete} event so the
     * handler can decide to continue reading.  If there's a pending read operation already, this method does nothing.
     * <p>
     * This will result in having the
     * {@link ChannelHandler#read(ChannelHandlerContext)}
     * method called of the next {@link ChannelHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    ChannelOutboundInvoker read();

    /**
     * Request to write a message via this {@link ChannelHandlerContext} through the {@link ChannelPipeline}.
     * This method will not request to actual flush, so be sure to call {@link #flush()}
     * once you want to request to flush all pending data to the actual transport.
     */
    Future<Void> write(Object msg);

    /**
     * Request to flush all pending messages via this ChannelOutboundInvoker.
     */
    ChannelOutboundInvoker flush();

    /**
     * Shortcut for call {@link #write(Object)} and {@link #flush()}.
     */
    Future<Void> writeAndFlush(Object msg);

    /**
     * Send a custom outbound event via this {@link ChannelOutboundInvoker} through the
     * {@link ChannelPipeline}.
     * <p>
     * This will result in having the
     * {@link ChannelHandler#sendOutboundEvent(ChannelHandlerContext, Object)}
     * method called of the next {@link ChannelHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    Future<Void> sendOutboundEvent(Object event);

    @Override
    default <V> Promise<V> newPromise() {
        return executor().newPromise();
    }

    @Override
    default <V> Future<V> newSucceededFuture(V value) {
        return executor().newSucceededFuture(value);
    }

    @Override
    default <V> Future<V> newFailedFuture(Throwable cause) {
        return executor().newFailedFuture(cause);
    }

    @Override
    default Future<Void> newSucceededFuture() {
        return executor().newSucceededFuture();
    }

    /**
     * Returns the {@link EventExecutor} that is used to execute the operations of this {@link ChannelOutboundInvoker}.
     *
     * @return  the executor.
     */
    EventExecutor executor();
}
