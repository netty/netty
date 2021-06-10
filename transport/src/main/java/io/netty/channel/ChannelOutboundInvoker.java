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
import io.netty.util.concurrent.FutureListener;

import java.net.ConnectException;
import java.net.SocketAddress;

public interface ChannelOutboundInvoker<I extends ChannelOutboundInvoker<I>> {

    /**
     * Request to bind to the given {@link SocketAddress} and notify the {@link ChannelFuture} once the operation
     * completes, either because the operation was successful or because of an error.
     * <p>
     * This will result in having the
     * {@link ChannelHandler#bind(ChannelHandlerContext, SocketAddress, ChannelOutboundInvokerCallback)} method
     * called of the next {@link ChannelHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    default ChannelFuture bind(SocketAddress localAddress) {
        ChannelPromise promise = newPromise();
        bind(localAddress, promise);
        return promise;
    }

    /**
     * Request to connect to the given {@link SocketAddress} and notify the {@link ChannelFuture} once the operation
     * completes, either because the operation was successful or because of an error.
     * <p>
     * If the connection fails because of a connection timeout, the {@link ChannelFuture} will get failed with
     * a {@link ConnectTimeoutException}. If it fails because of connection refused a {@link ConnectException}
     * will be used.
     * <p>
     * This will result in having the
     * {@link ChannelHandler#connect(ChannelHandlerContext, SocketAddress, SocketAddress,
     * ChannelOutboundInvokerCallback)}
     * method called of the next {@link ChannelHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    default ChannelFuture connect(SocketAddress remoteAddress) {
        ChannelPromise promise = newPromise();
        connect(remoteAddress, promise);
        return promise;
    }

    /**
     * Request to connect to the given {@link SocketAddress} while bind to the localAddress and notify the
     * {@link ChannelFuture} once the operation completes, either because the operation was successful or because of
     * an error.
     * <p>
     * This will result in having the
     * {@link ChannelHandler#connect(ChannelHandlerContext, SocketAddress, SocketAddress,
     * ChannelOutboundInvokerCallback)}
     * method called of the next {@link ChannelHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    default ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
        ChannelPromise promise = newPromise();
        connect(remoteAddress, localAddress, promise);
        return promise;
    }

    /**
     * Request to disconnect from the remote peer and notify the {@link ChannelFuture} once the operation completes,
     * either because the operation was successful or because of an error.
     * <p>
     * This will result in having the
     * {@link ChannelHandler#disconnect(ChannelHandlerContext, ChannelOutboundInvokerCallback)}
     * method called of the next {@link ChannelHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    default ChannelFuture disconnect() {
        ChannelPromise promise = newPromise();
        disconnect(promise);
        return promise;
    }

    /**
     * Request to close the {@link Channel} and notify the {@link ChannelFuture} once the operation completes,
     * either because the operation was successful or because of
     * an error.
     *
     * After it is closed it is not possible to reuse it again.
     * <p>
     * This will result in having the
     * {@link ChannelHandler#close(ChannelHandlerContext, ChannelOutboundInvokerCallback)}
     * method called of the next {@link ChannelHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     */
    default ChannelFuture close() {
        ChannelPromise promise = newPromise();
        close(promise);
        return promise;
    }

    /**
     * Request to register on the {@link EventExecutor} for I/O processing.
     * {@link ChannelFuture} once the operation completes, either because the operation was successful or because of
     * an error.
     * <p>
     * This will result in having the
     * {@link ChannelHandler#register(ChannelHandlerContext, ChannelOutboundInvokerCallback)}
     * method called of the next {@link ChannelHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     *
     */
    default ChannelFuture register() {
        ChannelPromise promise = newPromise();
        register(promise);
        return promise;
    }

    /**
     * Request to deregister from the previous assigned {@link EventExecutor} and notify the
     * {@link ChannelFuture} once the operation completes, either because the operation was successful or because of
     * an error.
     * <p>
     * This will result in having the
     * {@link ChannelHandler#deregister(ChannelHandlerContext, ChannelOutboundInvokerCallback)}
     * method called of the next {@link ChannelHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     *
     */
    default ChannelFuture deregister() {
        ChannelPromise promise = newPromise();
        deregister(promise);
        return promise;
    }

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
     *
     * @return itself.
     */
    I read();

    /**
     * Request to bind to the given {@link SocketAddress} and notify the {@link ChannelFuture} once the operation
     * completes, either because the operation was successful or because of an error.
     *
     * The given {@link ChannelOutboundInvokerCallback} will be notified.
     * <p>
     * This will result in having the
     * {@link ChannelHandler#bind(ChannelHandlerContext, SocketAddress, ChannelOutboundInvokerCallback)} method
     * called of the next {@link ChannelHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     *
     * <strong>Note:</strong> Usually user almost never want to implement their own
     * {@link ChannelOutboundInvokerCallback}. If the user wants to be notified about the result of the
     * operation {@link #bind(SocketAddress)} should be used and a {@link ChannelFutureListener} should be
     * attached to the returned {@link ChannelFuture}.
     *
     * @param localAddress      the {@link SocketAddress} to which it should bound
     * @param callback  the {@link ChannelOutboundInvokerCallback} to notify once the operation completes
     * @return itself.
     */
    I bind(SocketAddress localAddress, ChannelOutboundInvokerCallback callback);

    /**
     * Request to connect to the given {@link SocketAddress} and notify the {@link ChannelFuture} once the operation
     * completes, either because the operation was successful or because of an error.
     *
     * The given {@link ChannelOutboundInvokerCallback} will be notified.
     *
     * <p>
     * If the connection fails because of a connection timeout, the {@link ChannelFuture} will get failed with
     * a {@link ConnectTimeoutException}. If it fails because of connection refused a {@link ConnectException}
     * will be used.
     * <p>
     * This will result in having the
     * {@link ChannelHandler#connect(ChannelHandlerContext, SocketAddress, SocketAddress,
     * ChannelOutboundInvokerCallback)}
     * method called of the next {@link ChannelHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     *
     * <strong>Note:</strong> Usually user almost never want to implement their own
     * {@link ChannelOutboundInvokerCallback}. If the user wants to be notified about the result of the
     * operation {@link #connect(SocketAddress)} should be used and a {@link ChannelFutureListener} should be
     * attached to the returned {@link ChannelFuture}.
     *
     * @param remoteAddress     the {@link SocketAddress} to which it should connect
     * @param callback          the {@link ChannelOutboundInvokerCallback} to notify once the operation completes
     * @return itself.
     */
    default I connect(SocketAddress remoteAddress, ChannelOutboundInvokerCallback callback) {
        return connect(remoteAddress, null, callback);
    }

    /**
     * Request to connect to the given {@link SocketAddress} while bind to the localAddress and notify the
     * {@link ChannelFuture} once the operation completes, either because the operation was successful or because of
     * an error.
     *
     * The given {@link ChannelOutboundInvokerCallback} will be notified and also returned.
     * <p>
     * This will result in having the
     * {@link ChannelHandler#connect(ChannelHandlerContext, SocketAddress, SocketAddress,
     * ChannelOutboundInvokerCallback)}
     * method called of the next {@link ChannelHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     *
     * <strong>Note:</strong> Usually user almost never want to implement their own
     * {@link ChannelOutboundInvokerCallback}. If the user wants to be notified about the result of the
     * operation {@link #connect(SocketAddress, SocketAddress)} should be used and a {@link ChannelFutureListener}
     * should be attached to the returned {@link ChannelFuture}.
     *
     * @param remoteAddress     the {@link SocketAddress} to which it should connect
     * @param localAddress      the {@link SocketAddress} which is used as source on connect
     * @param callback          the {@link ChannelOutboundInvokerCallback} to notify once the operation completes
     * @return itself.
     */
    I connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelOutboundInvokerCallback callback);

    /**
     * Request to disconnect from the remote peer and notify the {@link ChannelFuture} once the operation completes,
     * either because the operation was successful or because of an error.
     *
     * The given {@link ChannelOutboundInvokerCallback} will be notified.
     * <p>
     * This will result in having the
     * {@link ChannelHandler#disconnect(ChannelHandlerContext, ChannelOutboundInvokerCallback)}
     * method called of the next {@link ChannelHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     *
     * <strong>Note:</strong> Usually user almost never want to implement their own
     * {@link ChannelOutboundInvokerCallback}. If the user wants to be notified about the result of the
     * operation {@link #disconnect()} should be used and a {@link ChannelFutureListener}
     * should be attached to the returned {@link ChannelFuture}.
     *
     * @param callback  the {@link ChannelOutboundInvokerCallback} to notify once the operation completes
     * @return itself.
     */
    I disconnect(ChannelOutboundInvokerCallback callback);

    /**
     * Request to close the {@link Channel} and notify the {@link ChannelFuture} once the operation completes,
     * either because the operation was successful or because of
     * an error.
     *
     * After it is closed it is not possible to reuse it again.
     * The given {@link ChannelOutboundInvokerCallback} will be notified.
     * <p>
     * This will result in having the
     * {@link ChannelHandler#close(ChannelHandlerContext, ChannelOutboundInvokerCallback)}
     * method called of the next {@link ChannelHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     *
     * <strong>Note:</strong> Usually user almost never want to implement their own
     * {@link ChannelOutboundInvokerCallback}. If the user wants to be notified about the result of the
     * operation {@link #close()} should be used and a {@link ChannelFutureListener}
     * should be attached to the returned {@link ChannelFuture}.
     *
     * @param callback  the {@link ChannelOutboundInvokerCallback} to notify once the operation completes
     * @return itself.
     */
    I close(ChannelOutboundInvokerCallback callback);

    /**
     * Request to register on the {@link EventExecutor} for I/O processing.
     * {@link ChannelFuture} once the operation completes, either because the operation was successful or because of
     * an error.
     *
     * The given {@link ChannelOutboundInvokerCallback} will be notified.
     * <p>
     * This will result in having the
     * {@link ChannelHandler#register(ChannelHandlerContext, ChannelOutboundInvokerCallback)}
     * method called of the next {@link ChannelHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     *
     * <strong>Note:</strong> Usually user almost never want to implement their own
     * {@link ChannelOutboundInvokerCallback}. If the user wants to be notified about the result of the
     * operation {@link #register()} should be used and a {@link ChannelFutureListener}
     * should be attached to the returned {@link ChannelFuture}.
     *
     * @param callback  the {@link ChannelOutboundInvokerCallback} to notify once the operation completes
     * @return itself.
     */
    I register(ChannelOutboundInvokerCallback callback);

    /**
     * Request to deregister from the previous assigned {@link EventExecutor} and notify the
     * {@link ChannelFuture} once the operation completes, either because the operation was successful or because of
     * an error.
     *
     * The given {@link ChannelOutboundInvokerCallback} will be notified.
     * <p>
     * This will result in having the
     * {@link ChannelHandler#deregister(ChannelHandlerContext, ChannelOutboundInvokerCallback)}
     * method called of the next {@link ChannelHandler} contained in the {@link ChannelPipeline} of the
     * {@link Channel}.
     *
     * <strong>Note:</strong> Usually user almost never want to implement their own
     * {@link ChannelOutboundInvokerCallback}. If the user wants to be notified about the result of the
     * operation {@link #deregister()} should be used and a {@link ChannelFutureListener}
     * should be attached to the returned {@link ChannelFuture}.
     *
     * @param callback  the {@link ChannelOutboundInvokerCallback} to notify once the operation completes
     * @return itself.
     */
    I deregister(ChannelOutboundInvokerCallback callback);

    /**
     * Request to write a message via this {@link ChannelHandlerContext} through the {@link ChannelPipeline}.
     * This method will not request to actual flush, so be sure to call {@link #flush()}
     * once you want to request to flush all pending data to the actual transport.
     *
     * @param msg   the message to write.
     * @return      the {@link ChannelFuture} that will be notified once the operation completes.
     */
    default ChannelFuture write(Object msg) {
        ChannelPromise promise = newPromise();
        write(msg, promise);
        return promise;
    }

    /**
     * Request to write a message via this {@link ChannelHandlerContext} through the {@link ChannelPipeline}.
     * This method will not request to actual flush, so be sure to call {@link #flush()}
     * once you want to request to flush all pending data to the actual transport.
     *
     * <strong>Note:</strong> Usually user almost never want to implement their own
     * {@link ChannelOutboundInvokerCallback}. If the user wants to be notified about the result of the
     * operation {@link #write(Object)} should be used and a {@link ChannelFutureListener}
     * should be attached to the returned {@link ChannelFuture}.
     *
     * @param msg       the message to write.
     * @param callback  the {@link ChannelOutboundInvokerCallback} to notify once the operation completes
     * @return itself.
     */
    I write(Object msg, ChannelOutboundInvokerCallback callback);

    /**
     * Request to flush all pending messages via this {@link ChannelOutboundInvoker}.
     *
     * @return itself.
     */
    I flush();

    /**
     * Shortcut for call {@link #write(Object, ChannelOutboundInvokerCallback)} and
     * {@link #flush()}
     *
     * <strong>Note:</strong> Usually user almost never want to implement their own
     * {@link ChannelOutboundInvokerCallback}. If the user wants to be notified about the result of the
     * operation {@link #writeAndFlush(Object)} should be used and a {@link ChannelFutureListener}
     * should be attached to the returned {@link ChannelFuture}.
     *
     * @param msg       the message to write.
     * @param callback  the {@link ChannelOutboundInvokerCallback} to notify once the operation completes
     */
    I writeAndFlush(Object msg, ChannelOutboundInvokerCallback callback);

    /**
     * Shortcut for call {@link #write(Object)} and {@link #flush()}.
     *
     * @param msg               the message to write.
     * @return      the {@link ChannelFuture} that will be notified once the operation completes.
     */
    default ChannelFuture writeAndFlush(Object msg) {
        ChannelPromise promise = newPromise();
        writeAndFlush(msg, promise);
        return promise;
    }

    /**
     * Return a new {@link ChannelPromise}.
     */
    ChannelPromise newPromise();

    /**
     * Create a new {@link ChannelFuture} which is marked as succeeded already. So {@link ChannelFuture#isSuccess()}
     * will return {@code true}. All {@link FutureListener} added to it will be notified directly. Also
     * every call of blocking methods will just return without blocking.
     */
    ChannelFuture newSucceededFuture();

    /**
     * Create a new {@link ChannelFuture} which is marked as failed already. So {@link ChannelFuture#isSuccess()}
     * will return {@code false}. All {@link FutureListener} added to it will be notified directly. Also
     * every call of blocking methods will just return without blocking.
     */
    ChannelFuture newFailedFuture(Throwable cause);

    /**
     * Return a special {@link ChannelOutboundInvokerCallback} that will notify the {@link ChannelPipeline} about an
     * error by calling {@link ChannelInboundInvoker#fireExceptionCaught(Throwable).}
     *
     * @return the callback.
     */
    ChannelOutboundInvokerCallback voidCallback();
}
