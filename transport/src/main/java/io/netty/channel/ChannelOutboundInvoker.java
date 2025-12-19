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
package io.netty.channel;

import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;

import java.net.ConnectException;
import java.net.SocketAddress;

public interface ChannelOutboundInvoker {

    /**
     * Request to register to the {@link EventExecutor} and notify the
     * {@link Future} once the operation completes, either because the operation was successful or because of
     * an error.
     * <p>
     * The given {@link Promise} will be notified.
     * <p>
     * This will result in having the
     * {@link ChannelOutboundHandler#register(ChannelHandlerContext, Promise)}
     * method called of the next {@link ChannelOutboundHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    Future<Void> register(Promise<Void> promise);

    /**
     * Request to bind to the given {@link SocketAddress} and notify the {@link Future} once the operation
     * completes, either because the operation was successful or because of an error.
     * <p>
     * This will result in having the
     * {@link ChannelOutboundHandler#bind(ChannelHandlerContext, SocketAddress, Promise)} method
     * called of the next {@link ChannelOutboundHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    default Future<Void> bind(SocketAddress localAddress) {
        return bind(localAddress, newPromise());
    }

    /**
     * Request to connect to the given {@link SocketAddress} and notify the {@link Future} once the operation
     * completes, either because the operation was successful or because of an error.
     * <p>
     * If the connection fails because of a connection timeout, the {@link Future} will get failed with
     * a {@link ConnectTimeoutException}. If it fails because of connection refused a {@link ConnectException}
     * will be used.
     * <p>
     * This will result in having the
     * {@link ChannelOutboundHandler#connect(ChannelHandlerContext, SocketAddress, SocketAddress, Promise)}
     * method called of the next {@link ChannelOutboundHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    default Future<Void> connect(SocketAddress remoteAddress) {
        return connect(remoteAddress, newPromise());
    }

    /**
     * Request to connect to the given {@link SocketAddress} while bind to the localAddress and notify the
     * {@link Future} once the operation completes, either because the operation was successful or because of
     * an error.
     * <p>
     * This will result in having the
     * {@link ChannelOutboundHandler#connect(ChannelHandlerContext, SocketAddress, SocketAddress, Promise)}
     * method called of the next {@link ChannelOutboundHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    default Future<Void> connect(SocketAddress remoteAddress, SocketAddress localAddress) {
        return connect(remoteAddress, localAddress, newPromise());
    }

    /**
     * Request to disconnect from the remote peer and notify the {@link Future} once the operation completes,
     * either because the operation was successful or because of an error.
     * <p>
     * This will result in having the
     * {@link ChannelOutboundHandler#disconnect(ChannelHandlerContext, Promise)}
     * method called of the next {@link ChannelOutboundHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    default Future<Void> disconnect() {
        return disconnect(newPromise());
    }

    /**
     * Request to close the {@link Channel} and notify the {@link Future} once the operation completes,
     * either because the operation was successful or because of
     * an error.
     * <p>
     * After it is closed it is not possible to reuse it again.
     * <p>
     * This will result in having the
     * {@link ChannelOutboundHandler#close(ChannelHandlerContext, Promise)}
     * method called of the next {@link ChannelOutboundHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    default Future<Void> close() {
        return close(newPromise());
    }

    /**
     * Request to deregister from the previous assigned {@link EventExecutor} and notify the
     * {@link Future} once the operation completes, either because the operation was successful or because of
     * an error.
     * <p>
     * This will result in having the
     * {@link ChannelOutboundHandler#deregister(ChannelHandlerContext, Promise)}
     * method called of the next {@link ChannelOutboundHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     *
     */
    default Future<Void> deregister() {
        return deregister(newPromise());
    }

    /**
     * Request to register to the {@link EventExecutor} and notify the
     * {@link Future} once the operation completes, either because the operation was successful or because of
     * an error.
     * <p>
     * The given {@link Promise} will be notified.
     * <p>
     * This will result in having the
     * {@link ChannelOutboundHandler#register(ChannelHandlerContext, Promise)}
     * method called of the next {@link ChannelOutboundHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    default Future<Void> register() {
        return register(newPromise());
    }

    /**
     * Request to bind to the given {@link SocketAddress} and notify the {@link Future} once the operation
     * completes, either because the operation was successful or because of an error.
     * <p>
     * The given {@link Promise} will be notified.
     * <p>
     * This will result in having the
     * {@link ChannelOutboundHandler#bind(ChannelHandlerContext, SocketAddress, Promise)} method
     * called of the next {@link ChannelOutboundHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    Future<Void> bind(SocketAddress localAddress, Promise<Void> promise);

    /**
     * Request to connect to the given {@link SocketAddress} and notify the {@link Future} once the operation
     * completes, either because the operation was successful or because of an error.
     * <p>
     * The given {@link Future} will be notified.
     *
     * <p>
     * If the connection fails because of a connection timeout, the {@link Future} will get failed with
     * a {@link ConnectTimeoutException}. If it fails because of connection refused a {@link ConnectException}
     * will be used.
     * <p>
     * This will result in having the
     * {@link ChannelOutboundHandler#connect(ChannelHandlerContext, SocketAddress, SocketAddress, Promise)}
     * method called of the next {@link ChannelOutboundHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    Future<Void> connect(SocketAddress remoteAddress, Promise<Void> promise);

    /**
     * Request to connect to the given {@link SocketAddress} while bind to the localAddress and notify the
     * {@link Future} once the operation completes, either because the operation was successful or because of
     * an error.
     * <p>
     * The given {@link Promise} will be notified and also returned.
     * <p>
     * This will result in having the
     * {@link ChannelOutboundHandler#connect(ChannelHandlerContext, SocketAddress, SocketAddress, Promise)}
     * method called of the next {@link ChannelOutboundHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    Future<Void> connect(SocketAddress remoteAddress, SocketAddress localAddress, Promise<Void> promise);

    /**
     * Request to disconnect from the remote peer and notify the {@link Future} once the operation completes,
     * either because the operation was successful or because of an error.
     * <p>
     * The given {@link Promise} will be notified.
     * <p>
     * This will result in having the
     * {@link ChannelOutboundHandler#disconnect(ChannelHandlerContext, Promise)}
     * method called of the next {@link ChannelOutboundHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    Future<Void> disconnect(Promise<Void> promise);

    /**
     * Request to close the {@link Channel} and notify the {@link Future} once the operation completes,
     * either because the operation was successful or because of
     * an error.
     * <p>
     * After it is closed it is not possible to reuse it again.
     * The given {@link Promise} will be notified.
     * <p>
     * This will result in having the
     * {@link ChannelOutboundHandler#close(ChannelHandlerContext, Promise)}
     * method called of the next {@link ChannelOutboundHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    Future<Void> close(Promise<Void> promise);

    /**
     * Request to deregister from the previous assigned {@link EventExecutor} and notify the
     * {@link Future} once the operation completes, either because the operation was successful or because of
     * an error.
     * <p>
     * The given {@link Promise} will be notified.
     * <p>
     * This will result in having the
     * {@link ChannelOutboundHandler#deregister(ChannelHandlerContext, Promise)}
     * method called of the next {@link ChannelOutboundHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    Future<Void> deregister(Promise<Void> promise);

    /**
     * Request to Read data from the {@link Channel} into the first inbound buffer, triggers an
     * {@link ChannelInboundHandler#channelRead(ChannelHandlerContext, Object)} event if data was
     * read, and triggers a
     * {@link ChannelInboundHandler#channelReadComplete(ChannelHandlerContext) channelReadComplete} event so the
     * handler can decide to continue reading.  If there's a pending read operation already, this method does nothing.
     * <p>
     * This will result in having the
     * {@link ChannelOutboundHandler#read(ChannelHandlerContext)}
     * method called of the next {@link ChannelOutboundHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    ChannelOutboundInvoker read();

    /**
     * Request to write a message via this {@link ChannelHandlerContext} through the {@link ChannelPipeline}.
     * This method will not request to actual flush, so be sure to call {@link #flush()}
     * once you want to request to flush all pending data to the actual transport.
     */
    default Future<Void> write(Object msg) {
        return write(msg, newPromise());
    }

    /**
     * Request to write a message via this {@link ChannelHandlerContext} through the {@link ChannelPipeline}.
     * This method will not request to actual flush, so be sure to call {@link #flush()}
     * once you want to request to flush all pending data to the actual transport.
     */
    Future<Void> write(Object msg, Promise<Void> promise);

    /**
     * Request to flush all pending messages via this ChannelOutboundInvoker.
     */
    ChannelOutboundInvoker flush();

    /**
     * Shortcut for call {@link #write(Object, Promise)} and {@link #flush()}.
     */
    Future<Void> writeAndFlush(Object msg, Promise<Void> promise);

    /**
     * Shortcut for call {@link #write(Object)} and {@link #flush()}.
     */
    default Future<Void> writeAndFlush(Object msg) {
        return writeAndFlush(msg, newPromise());
    }

    /**
     * Request shutdown one direction of the {@link Channel} and notify the {@link Future} once the operation
     * completes, either because the operation was successful or because of an error.
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
     * {@link ChannelOutboundHandler#shutdown(ChannelHandlerContext, ChannelShutdownType, Promise)}.
     * method called of the next {@link ChannelHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    default Future<Void> shutdown(ChannelShutdownType type) {
        return shutdown(type, newPromise());
    }

    /**
     * Request shutdown one direction of the {@link Channel} and notify the {@link Future} once the operation
     * completes, either because the operation was successful or because of an error.
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
     * {@link ChannelOutboundHandler#shutdown(ChannelHandlerContext, ChannelShutdownType, Promise)}
     * method called of the next {@link ChannelOutboundHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    Future<Void> shutdown(ChannelShutdownType type, Promise<Void> promise);

    /**
     * Return a new {@link Promise}.
     */
    default <T> Promise<T> newPromise() {
        return executor().newPromise();
    }

    /**
     * Create a new {@link Future} which is marked as succeeded already. So {@link Future#isSuccess()}
     * will return {@code true}. All {@link FutureListener} added to it will be notified directly. Also
     * every call of blocking methods will just return without blocking.
     */
    default <T> Future<T> newSucceededFuture(T result) {
        return executor().newSucceededFuture(null);
    }

    /**
     * Create a new {@link Future} which is marked as failed already. So {@link Future#isSuccess()}
     * will return {@code false}. All {@link FutureListener} added to it will be notified directly. Also
     * every call of blocking methods will just return without blocking.
     */
    default <T> Future<T> newFailedFuture(Throwable cause) {
        return executor().newFailedFuture(cause);
    }

    /**
     * Returns the {@link EventExecutor} that is used to execute the operations of this {@link ChannelOutboundInvoker}.
     *
     * @return  the executor.
     */
    EventExecutor executor();
}
