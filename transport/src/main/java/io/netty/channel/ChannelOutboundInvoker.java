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

import io.netty.util.concurrent.CompletionHandler;
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
     * The given {@link CompletionHandler} will be notified.
     * <p>
     * This will result in having the
     * {@link ChannelOutboundHandler#register(ChannelHandlerContext, CompletionHandler)}
     * method called of the next {@link ChannelOutboundHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    void register(CompletionHandler<Void> handler);

    /**
     * Request to bind to the given {@link SocketAddress} and notify the {@link Future} once the operation
     * completes, either because the operation was successful or because of an error.
     * <p>
     * This will result in having the
     * {@link ChannelOutboundHandler#bind(ChannelHandlerContext, SocketAddress, CompletionHandler)} method
     * called of the next {@link ChannelOutboundHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    default Future<Void> bind(SocketAddress localAddress) {
        Promise<Void> promise = newPromise();
        bind(localAddress, promise.toCompletionHandler());
        return promise;
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
     * {@link ChannelOutboundHandler#connect(ChannelHandlerContext, SocketAddress, SocketAddress, CompletionHandler)}
     * method called of the next {@link ChannelOutboundHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    default Future<Void> connect(SocketAddress remoteAddress) {
        Promise<Void> promise = newPromise();
        connect(remoteAddress, promise.toCompletionHandler());
        return promise;
    }

    /**
     * Request to connect to the given {@link SocketAddress} while bind to the localAddress and notify the
     * {@link Future} once the operation completes, either because the operation was successful or because of
     * an error.
     * <p>
     * This will result in having the
     * {@link ChannelOutboundHandler#connect(ChannelHandlerContext, SocketAddress, SocketAddress, CompletionHandler)}
     * method called of the next {@link ChannelOutboundHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    default Future<Void> connect(SocketAddress remoteAddress, SocketAddress localAddress) {
        Promise<Void> promise = newPromise();
        connect(remoteAddress, localAddress, promise.toCompletionHandler());
        return promise;
    }

    /**
     * Request to disconnect from the remote peer and notify the {@link Future} once the operation completes,
     * either because the operation was successful or because of an error.
     * <p>
     * This will result in having the
     * {@link ChannelOutboundHandler#disconnect(ChannelHandlerContext, CompletionHandler)}
     * method called of the next {@link ChannelOutboundHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    default Future<Void> disconnect() {
        Promise<Void> promise = newPromise();
        disconnect(promise.toCompletionHandler());
        return promise;
    }

    /**
     * Request to close the {@link Channel} and notify the {@link Future} once the operation completes,
     * either because the operation was successful or because of
     * an error.
     * <p>
     * After it is closed it is not possible to reuse it again.
     * <p>
     * This will result in having the
     * {@link ChannelOutboundHandler#close(ChannelHandlerContext, CompletionHandler)}
     * method called of the next {@link ChannelOutboundHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    default Future<Void> close() {
        Promise<Void> promise = newPromise();
        close(promise.toCompletionHandler());
        return promise;
    }

    /**
     * Request to deregister from the previous assigned {@link EventExecutor} and notify the
     * {@link Future} once the operation completes, either because the operation was successful or because of
     * an error.
     * <p>
     * This will result in having the
     * {@link ChannelOutboundHandler#deregister(ChannelHandlerContext, CompletionHandler)}
     * method called of the next {@link ChannelOutboundHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     *
     */
    default Future<Void> deregister() {
        Promise<Void> promise = newPromise();
        deregister(promise.toCompletionHandler());
        return promise;
    }

    /**
     * Request to register to the {@link EventExecutor} and notify the
     * {@link Future} once the operation completes, either because the operation was successful or because of
     * an error.
     * <p>
     * The given {@link Promise} will be notified.
     * <p>
     * This will result in having the
     * {@link ChannelOutboundHandler#register(ChannelHandlerContext, CompletionHandler)}
     * method called of the next {@link ChannelOutboundHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    default Future<Void> register() {
        Promise<Void> promise = newPromise();
        register(promise.toCompletionHandler());
        return promise;
    }

    /**
     * Request to bind to the given {@link SocketAddress} and notify the {@link CompletionHandler} once the operation
     * completes, either because the operation was successful or because of an error.
     * <p>
     * The given {@link CompletionHandler} will be notified.
     * <p>
     * This will result in having the
     * {@link ChannelOutboundHandler#bind(ChannelHandlerContext, SocketAddress, CompletionHandler)} method
     * called of the next {@link ChannelOutboundHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    void bind(SocketAddress localAddress, CompletionHandler<Void> handler);

    /**
     * Request to connect to the given {@link SocketAddress} and notify the {@link CompletionHandler} once the operation
     * completes, either because the operation was successful or because of an error.
     * <p>
     * The given {@link CompletionHandler} will be notified.
     *
     * <p>
     * If the connection fails because of a connection timeout, the {@link CompletionHandler} will get failed with
     * a {@link ConnectTimeoutException}. If it fails because of connection refused a {@link ConnectException}
     * will be used.
     * <p>
     * This will result in having the
     * {@link ChannelOutboundHandler#connect(ChannelHandlerContext, SocketAddress, SocketAddress, CompletionHandler)}
     * method called of the next {@link ChannelOutboundHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    void connect(SocketAddress remoteAddress, CompletionHandler<Void> handler);

    /**
     * Request to connect to the given {@link SocketAddress} while bind to the localAddress and notify the
     * {@link CompletionHandler} once the operation completes, either because the operation was successful or because of
     * an error.
     * <p>
     * The given {@link CompletionHandler} will be notified and also returned.
     * <p>
     * This will result in having the
     * {@link ChannelOutboundHandler#connect(ChannelHandlerContext, SocketAddress, SocketAddress, CompletionHandler)}
     * method called of the next {@link ChannelOutboundHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    void connect(SocketAddress remoteAddress, SocketAddress localAddress, CompletionHandler<Void> handler);

    /**
     * Request to disconnect from the remote peer and notify the {@link CompletionHandler} once the operation completes,
     * either because the operation was successful or because of an error.
     * <p>
     * The given {@link CompletionHandler} will be notified.
     * <p>
     * This will result in having the
     * {@link ChannelOutboundHandler#disconnect(ChannelHandlerContext, CompletionHandler)}
     * method called of the next {@link ChannelOutboundHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    void disconnect(CompletionHandler<Void> handler);

    /**
     * Request to close the {@link Channel} and notify the {@link CompletionHandler} once the operation completes,
     * either because the operation was successful or because of
     * an error.
     * <p>
     * After it is closed it is not possible to reuse it again.
     * The given {@link CompletionHandler} will be notified.
     * <p>
     * This will result in having the
     * {@link ChannelOutboundHandler#close(ChannelHandlerContext, CompletionHandler)}
     * method called of the next {@link ChannelOutboundHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    void close(CompletionHandler<Void> handler);

    /**
     * Request to deregister from the previous assigned {@link EventExecutor} and notify the
     * {@link CompletionHandler} once the operation completes, either because the operation was successful or because of
     * an error.
     * <p>
     * The given {@link CompletionHandler} will be notified.
     * <p>
     * This will result in having the
     * {@link ChannelOutboundHandler#deregister(ChannelHandlerContext, CompletionHandler)}
     * method called of the next {@link ChannelOutboundHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    void deregister(CompletionHandler<Void> promise);

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
    void read();

    /**
     * Request to write a message via this {@link ChannelHandlerContext} through the {@link ChannelPipeline}.
     * This method will not request to actual flush, so be sure to call {@link #flush()}
     * once you want to request to flush all pending data to the actual transport.
     */
    default Future<Void> write(Object msg) {
        Promise<Void> promise = newPromise();
        write(msg, promise.toCompletionHandler());
        return promise;
    }

    /**
     * Request to write a message via this {@link ChannelHandlerContext} through the {@link ChannelPipeline}.
     * This method will not request to actual flush, so be sure to call {@link #flush()}
     * once you want to request to flush all pending data to the actual transport.
     */
    void write(Object msg, CompletionHandler<Void> handler);

    /**
     * Request to flush all pending messages via this ChannelOutboundInvoker.
     */
    void flush();

    /**
     * Shortcut for call {@link #write(Object, CompletionHandler)} and {@link #flush()}.
     */
    void writeAndFlush(Object msg, CompletionHandler<Void> promise);

    /**
     * Shortcut for call {@link #write(Object)} and {@link #flush()}.
     */
    default Future<Void> writeAndFlush(Object msg) {
        Promise<Void> promise = newPromise();
        writeAndFlush(msg, promise.toCompletionHandler());
        return promise;
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
     * {@link ChannelOutboundHandler#shutdown(ChannelHandlerContext, ChannelShutdownType, CompletionHandler)}.
     * method called of the next {@link ChannelHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    default Future<Void> shutdown(ChannelShutdownType type) {
        Promise<Void> promise = newPromise();
        shutdown(type, promise.toCompletionHandler());
        return promise;
    }

    /**
     * Request shutdown one direction of the {@link Channel} and notify the {@link CompletionHandler} once the operation
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
     * {@link ChannelOutboundHandler#shutdown(ChannelHandlerContext, ChannelShutdownType, CompletionHandler)}
     * method called of the next {@link ChannelOutboundHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    void shutdown(ChannelShutdownType type, CompletionHandler<Void> handler);

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
