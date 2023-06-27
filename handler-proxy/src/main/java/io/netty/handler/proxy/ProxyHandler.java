/*
 * Copyright 2014 The Netty Project
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

package io.netty.handler.proxy;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.PendingWriteQueue;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.SocketAddress;
import java.nio.channels.ConnectionPendingException;
import java.util.concurrent.TimeUnit;

public abstract class ProxyHandler extends ChannelDuplexHandler {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ProxyHandler.class);

    /**
     * The default connect timeout: 10 seconds.
     */
    private static final long DEFAULT_CONNECT_TIMEOUT_MILLIS = 10000;

    /**
     * A string that signifies 'no authentication' or 'anonymous'.
     */
    static final String AUTH_NONE = "none";

    private final SocketAddress proxyAddress;
    private volatile SocketAddress destinationAddress;
    private volatile long connectTimeoutMillis = DEFAULT_CONNECT_TIMEOUT_MILLIS;

    private volatile ChannelHandlerContext ctx;
    private PendingWriteQueue pendingWrites;
    private boolean finished;
    private boolean suppressChannelReadComplete;
    private boolean flushedPrematurely;
    private final LazyChannelPromise connectPromise = new LazyChannelPromise();
    private Future<?> connectTimeoutFuture;
    private final ChannelFutureListener writeListener = new ChannelFutureListener() {
        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            if (!future.isSuccess()) {
                setConnectFailure(future.cause());
            }
        }
    };

    protected ProxyHandler(SocketAddress proxyAddress) {
        this.proxyAddress = ObjectUtil.checkNotNull(proxyAddress, "proxyAddress");
    }

    /**
     * Returns the name of the proxy protocol in use.
     */
    public abstract String protocol();

    /**
     * Returns the name of the authentication scheme in use.
     */
    public abstract String authScheme();

    /**
     * Returns the address of the proxy server.
     */
    @SuppressWarnings("unchecked")
    public final <T extends SocketAddress> T proxyAddress() {
        return (T) proxyAddress;
    }

    /**
     * Returns the address of the destination to connect to via the proxy server.
     */
    @SuppressWarnings("unchecked")
    public final <T extends SocketAddress> T destinationAddress() {
        return (T) destinationAddress;
    }

    /**
     * Returns {@code true} if and only if the connection to the destination has been established successfully.
     */
    public final boolean isConnected() {
        return connectPromise.isSuccess();
    }

    /**
     * Returns a {@link Future} that is notified when the connection to the destination has been established
     * or the connection attempt has failed.
     */
    public final Future<Channel> connectFuture() {
        return connectPromise;
    }

    /**
     * Returns the connect timeout in millis.  If the connection attempt to the destination does not finish within
     * the timeout, the connection attempt will be failed.
     */
    public final long connectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    /**
     * Sets the connect timeout in millis.  If the connection attempt to the destination does not finish within
     * the timeout, the connection attempt will be failed.
     */
    public final void setConnectTimeoutMillis(long connectTimeoutMillis) {
        if (connectTimeoutMillis <= 0) {
            connectTimeoutMillis = 0;
        }

        this.connectTimeoutMillis = connectTimeoutMillis;
    }

    @Override
    public final void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        this.ctx = ctx;
        addCodec(ctx);

        if (ctx.channel().isActive()) {
            // channelActive() event has been fired already, which means this.channelActive() will
            // not be invoked. We have to initialize here instead.
            sendInitialMessage(ctx);
        } else {
            // channelActive() event has not been fired yet.  this.channelOpen() will be invoked
            // and initialization will occur there.
        }
    }

    /**
     * Adds the codec handlers required to communicate with the proxy server.
     */
    protected abstract void addCodec(ChannelHandlerContext ctx) throws Exception;

    /**
     * Removes the encoders added in {@link #addCodec(ChannelHandlerContext)}.
     */
    protected abstract void removeEncoder(ChannelHandlerContext ctx) throws Exception;

    /**
     * Removes the decoders added in {@link #addCodec(ChannelHandlerContext)}.
     */
    protected abstract void removeDecoder(ChannelHandlerContext ctx) throws Exception;

    @Override
    public final void connect(
            ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress,
            ChannelPromise promise) throws Exception {

        if (destinationAddress != null) {
            promise.setFailure(new ConnectionPendingException());
            return;
        }

        destinationAddress = remoteAddress;
        ctx.connect(proxyAddress, localAddress, promise);
    }

    @Override
    public final void channelActive(ChannelHandlerContext ctx) throws Exception {
        sendInitialMessage(ctx);
        ctx.fireChannelActive();
    }

    /**
     * Sends the initial message to be sent to the proxy server. This method also starts a timeout task which marks
     * the {@link #connectPromise} as failure if the connection attempt does not success within the timeout.
     */
    private void sendInitialMessage(final ChannelHandlerContext ctx) throws Exception {
        final long connectTimeoutMillis = this.connectTimeoutMillis;
        if (connectTimeoutMillis > 0) {
            connectTimeoutFuture = ctx.executor().schedule(new Runnable() {
                @Override
                public void run() {
                    if (!connectPromise.isDone()) {
                        setConnectFailure(new ProxyConnectException(exceptionMessage("timeout")));
                    }
                }
            }, connectTimeoutMillis, TimeUnit.MILLISECONDS);
        }

        final Object initialMessage = newInitialMessage(ctx);
        if (initialMessage != null) {
            sendToProxyServer(initialMessage);
        }

        readIfNeeded(ctx);
    }

    /**
     * Returns a new message that is sent at first time when the connection to the proxy server has been established.
     *
     * @return the initial message, or {@code null} if the proxy server is expected to send the first message instead
     */
    protected abstract Object newInitialMessage(ChannelHandlerContext ctx) throws Exception;

    /**
     * Sends the specified message to the proxy server.  Use this method to send a response to the proxy server in
     * {@link #handleResponse(ChannelHandlerContext, Object)}.
     */
    protected final void sendToProxyServer(Object msg) {
        ctx.writeAndFlush(msg).addListener(writeListener);
    }

    @Override
    public final void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (finished) {
            ctx.fireChannelInactive();
        } else {
            // Disconnected before connected to the destination.
            setConnectFailure(new ProxyConnectException(exceptionMessage("disconnected")));
        }
    }

    @Override
    public final void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (finished) {
            ctx.fireExceptionCaught(cause);
        } else {
            // Exception was raised before the connection attempt is finished.
            setConnectFailure(cause);
        }
    }

    @Override
    public final void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (finished) {
            // Received a message after the connection has been established; pass through.
            suppressChannelReadComplete = false;
            ctx.fireChannelRead(msg);
        } else {
            suppressChannelReadComplete = true;
            Throwable cause = null;
            try {
                boolean done = handleResponse(ctx, msg);
                if (done) {
                    setConnectSuccess();
                }
            } catch (Throwable t) {
                cause = t;
            } finally {
                ReferenceCountUtil.release(msg);
                if (cause != null) {
                    setConnectFailure(cause);
                }
            }
        }
    }

    /**
     * Handles the message received from the proxy server.
     *
     * @return {@code true} if the connection to the destination has been established,
     *         {@code false} if the connection to the destination has not been established and more messages are
     *         expected from the proxy server
     */
    protected abstract boolean handleResponse(ChannelHandlerContext ctx, Object response) throws Exception;

    private void setConnectSuccess() {
        finished = true;
        cancelConnectTimeoutFuture();

        if (!connectPromise.isDone()) {
            boolean removedCodec = true;

            removedCodec &= safeRemoveEncoder();

            ctx.fireUserEventTriggered(
                    new ProxyConnectionEvent(protocol(), authScheme(), proxyAddress, destinationAddress));

            removedCodec &= safeRemoveDecoder();

            if (removedCodec) {
                writePendingWrites();

                if (flushedPrematurely) {
                    ctx.flush();
                }
                connectPromise.trySuccess(ctx.channel());
            } else {
                // We are at inconsistent state because we failed to remove all codec handlers.
                Exception cause = new ProxyConnectException(
                        "failed to remove all codec handlers added by the proxy handler; bug?");
                failPendingWritesAndClose(cause);
            }
        }
    }

    private boolean safeRemoveDecoder() {
        try {
            removeDecoder(ctx);
            return true;
        } catch (Exception e) {
            logger.warn("Failed to remove proxy decoders:", e);
        }

        return false;
    }

    private boolean safeRemoveEncoder() {
        try {
            removeEncoder(ctx);
            return true;
        } catch (Exception e) {
            logger.warn("Failed to remove proxy encoders:", e);
        }

        return false;
    }

    private void setConnectFailure(Throwable cause) {
        finished = true;
        cancelConnectTimeoutFuture();

        if (!connectPromise.isDone()) {

            if (!(cause instanceof ProxyConnectException)) {
                cause = new ProxyConnectException(
                        exceptionMessage(cause.toString()), cause);
            }

            safeRemoveDecoder();
            safeRemoveEncoder();
            failPendingWritesAndClose(cause);
        }
    }

    private void failPendingWritesAndClose(Throwable cause) {
        failPendingWrites(cause);
        connectPromise.tryFailure(cause);
        ctx.fireExceptionCaught(cause);
        ctx.close();
    }

    private void cancelConnectTimeoutFuture() {
        if (connectTimeoutFuture != null) {
            connectTimeoutFuture.cancel(false);
            connectTimeoutFuture = null;
        }
    }

    /**
     * Decorates the specified exception message with the common information such as the current protocol,
     * authentication scheme, proxy address, and destination address.
     */
    protected final String exceptionMessage(String msg) {
        if (msg == null) {
            msg = "";
        }

        StringBuilder buf = new StringBuilder(128 + msg.length())
            .append(protocol())
            .append(", ")
            .append(authScheme())
            .append(", ")
            .append(proxyAddress)
            .append(" => ")
            .append(destinationAddress);
        if (!msg.isEmpty()) {
            buf.append(", ").append(msg);
        }

        return buf.toString();
    }

    @Override
    public final void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        if (suppressChannelReadComplete) {
            suppressChannelReadComplete = false;

            readIfNeeded(ctx);
        } else {
            ctx.fireChannelReadComplete();
        }
    }

    @Override
    public final void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (finished) {
            writePendingWrites();
            ctx.write(msg, promise);
        } else {
            addPendingWrite(ctx, msg, promise);
        }
    }

    @Override
    public final void flush(ChannelHandlerContext ctx) throws Exception {
        if (finished) {
            writePendingWrites();
            ctx.flush();
        } else {
            flushedPrematurely = true;
        }
    }

    private static void readIfNeeded(ChannelHandlerContext ctx) {
        if (!ctx.channel().config().isAutoRead()) {
            ctx.read();
        }
    }

    private void writePendingWrites() {
        if (pendingWrites != null) {
            pendingWrites.removeAndWriteAll();
            pendingWrites = null;
        }
    }

    private void failPendingWrites(Throwable cause) {
        if (pendingWrites != null) {
            pendingWrites.removeAndFailAll(cause);
            pendingWrites = null;
        }
    }

    private void addPendingWrite(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        PendingWriteQueue pendingWrites = this.pendingWrites;
        if (pendingWrites == null) {
            this.pendingWrites = pendingWrites = new PendingWriteQueue(ctx);
        }
        pendingWrites.add(msg, promise);
    }

    private final class LazyChannelPromise extends DefaultPromise<Channel> {
        @Override
        protected EventExecutor executor() {
            if (ctx == null) {
                throw new IllegalStateException();
            }
            return ctx.executor();
        }
    }
}
