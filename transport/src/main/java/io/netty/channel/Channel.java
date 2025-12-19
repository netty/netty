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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.util.AttributeMap;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

import java.net.InetSocketAddress;
import java.net.SocketAddress;


/**
 * A nexus to a network socket or a component which is capable of I/O
 * operations such as read, write, connect, and bind.
 * <p>
 * A channel provides a user:
 * <ul>
 * <li>the current state of the channel (e.g. is it open? is it connected?),</li>
 * <li>the {@linkplain ChannelConfig configuration parameters} of the channel (e.g. receive buffer size),</li>
 * <li>the I/O operations that the channel supports (e.g. read, write, connect, and bind), and</li>
 * <li>the {@link ChannelPipeline} which handles all I/O events and requests
 *     associated with the channel.</li>
 * </ul>
 *
 * <h3>All I/O operations are asynchronous.</h3>
 * <p>
 * All I/O operations in Netty are asynchronous.  It means any I/O calls will
 * return immediately with no guarantee that the requested I/O operation has
 * been completed at the end of the call.  Instead, you will be returned with
 * a {@link Future} instance which will notify you when the requested I/O
 * operation has succeeded, failed, or canceled.
 *
 * <h3>Channels are hierarchical</h3>
 * <p>
 * A {@link Channel} can have a {@linkplain #parent() parent} depending on
 * how it was created.  For instance, a {@link SocketChannel}, that was accepted
 * by {@link ServerSocketChannel}, will return the {@link ServerSocketChannel}
 * as its parent on {@link #parent()}.
 * <p>
 * The semantics of the hierarchical structure depends on the transport
 * implementation where the {@link Channel} belongs to.  For example, you could
 * write a new {@link Channel} implementation that creates the sub-channels that
 * share one socket connection, as <a href="http://beepcore.org/">BEEP</a> and
 * <a href="https://en.wikipedia.org/wiki/Secure_Shell">SSH</a> do.
 *
 * <h3>Downcast to access transport-specific operations</h3>
 * <p>
 * Some transports exposes additional operations that is specific to the
 * transport.  Down-cast the {@link Channel} to sub-type to invoke such
 * operations.  For example, with the old I/O datagram transport, multicast
 * join / leave operations are provided by {@link DatagramChannel}.
 *
 * <h3>Release resources</h3>
 * <p>
 * It is important to call {@link #close()} or {@link ChannelOutboundInvoker#close(Promise)} to release all
 * resources once you are done with the {@link Channel}. This ensures all resources are
 * released in a proper way, i.e. filehandles.
 */
public interface Channel extends AttributeMap, ChannelOutboundInvoker, Comparable<Channel> {

    /**
     * Returns the globally unique identifier of this {@link Channel}.
     */
    ChannelId id();

    /**
     * Return the {@link EventLoop} this {@link Channel} was registered to.
     */
    @Override
    EventLoop executor();

    /**
     * Returns the parent of this channel.
     *
     * @return the parent channel.
     *         {@code null} if this channel does not have a parent channel.
     */
    Channel parent();

    /**
     * Returns the configuration of this channel.
     */
    ChannelConfig config();

    /**
     * Returns {@code true} if the {@link Channel} is open and may get active later
     */
    boolean isOpen();

    /**
     * Returns {@code true} if the {@link Channel} is registered with an {@link EventLoop}.
     */
    boolean isRegistered();

    /**
     * Return {@code true} if the {@link Channel} is active and so connected.
     */
    boolean isActive();

    /**
     * Returns {@code true} if the given {@link ChannelShutdownDirection} is shutdown, {@code false} otherwise.
     *
     * @param direction     the {@link ChannelShutdownDirection}
     * @return              {@code true} if shutdown.
     */
    boolean isShutdown(ChannelShutdownDirection direction);

    /**
     * Returns the local address where this channel is bound to.  The returned
     * {@link SocketAddress} is supposed to be down-cast into more concrete
     * type such as {@link InetSocketAddress} to retrieve the detailed
     * information.
     *
     * @return the local address of this channel.
     *         {@code null} if this channel is not bound.
     */
    SocketAddress localAddress();

    /**
     * Returns the remote address where this channel is connected to.  The
     * returned {@link SocketAddress} is supposed to be down-cast into more
     * concrete type such as {@link InetSocketAddress} to retrieve the detailed
     * information.
     *
     * @return the remote address of this channel.
     *         {@code null} if this channel is not connected.
     *         If this channel is not connected but it can receive messages
     *         from arbitrary remote addresses (e.g. {@link DatagramChannel},
     *         use {@link DatagramPacket#recipient()} to determine
     *         the origination of the received message as this method will
     *         return {@code null}.
     */
    SocketAddress remoteAddress();

    /**
     * Returns the {@link Future} which will be notified when this
     * channel is closed.  This method always returns the same future instance.
     */
    Future<Void> closeFuture();

    /**
     * Returns {@code true} if this {@link Channel} has any pending bytes stored that were not written
     * to the underlying transport yet, {@code false} otherwise.
     *
     * @return  if there are any pending bytes.
     */
    boolean hasPendingBytes();

    /**
     * Returns {@code true} if and only if the I/O thread will perform the
     * requested write operation immediately.  Any write requests made when
     * this method returns {@code false} are queued until the I/O thread is
     * ready to process the queued write requests.
     *
     * {@link WriteBufferWaterMark} can be used to configure on which condition
     * the write buffer would cause this channel to change writability.
     */
    boolean isWritable();

    /**
     * Get how many bytes can be written until {@link #isWritable()} returns {@code false}.
     * This quantity will always be non-negative. If {@link #isWritable()} is {@code false} then 0.
     *
     * {@link WriteBufferWaterMark} can be used to define writability settings.
     */
    long bytesBeforeUnwritable();

    /**
     * Get how many bytes must be drained from underlying buffers until {@link #isWritable()} returns {@code true}.
     * This quantity will always be non-negative. If {@link #isWritable()} is {@code true} then 0.
     *
     * {@link WriteBufferWaterMark} can be used to define writability settings.
     */
    long bytesBeforeWritable();

    /**
     * Return the assigned {@link ChannelPipeline}.
     */
    ChannelPipeline pipeline();

    /**
     * Return the assigned {@link ByteBufAllocator} which will be used to allocate {@link ByteBuf}s.
     */
    default ByteBufAllocator alloc() {
        return config().getAllocator();
    }

    /**
     * Return the value of the given {@link ChannelOption}
     */
    default <T> T getOption(ChannelOption<T> option) {
        return config().getOption(option);
    }

    /**
     * Sets a configuration property with the specified name and value.
     * To override this method properly, you must call the super class:
     * <pre>
     * public boolean setOption(ChannelOption&lt;T&gt; option, T value) {
     *     if (super.setOption(option, value)) {
     *         return true;
     *     }
     *
     *     if (option.equals(additionalOption)) {
     *         ....
     *         return true;
     *     }
     *
     *     return false;
     * }
     * </pre>
     *
     * @return {@code true} if and only if the property has been set
     */
    default <T> boolean setOption(ChannelOption<T> option, T value) {
        return config().setOption(option, value);
    }

    @Override
    default Channel read() {
        pipeline().read();
        return this;
    }

    @Override
    default Channel flush() {
        pipeline().flush();
        return this;
    }

    @Override
    default Future<Void> writeAndFlush(Object msg) {
        return pipeline().writeAndFlush(msg);
    }

    @Override
    default Future<Void> writeAndFlush(Object msg, Promise<Void> promise) {
        return pipeline().writeAndFlush(msg, promise);
    }

    @Override
    default Future<Void> write(Object msg, Promise<Void> promise) {
        return pipeline().write(msg, promise);
    }

    @Override
    default Future<Void> write(Object msg) {
        return pipeline().write(msg);
    }

    @Override
    default Future<Void> deregister(Promise<Void> promise) {
        return pipeline().deregister(promise);
    }

    @Override
    default Future<Void> close(Promise<Void> promise) {
        return pipeline().close(promise);
    }

    @Override
    default Future<Void> disconnect(Promise<Void> promise) {
        return pipeline().disconnect(promise);
    }

    @Override
    default Future<Void> connect(SocketAddress remoteAddress, SocketAddress localAddress, Promise<Void> promise) {
        return pipeline().connect(remoteAddress, localAddress, promise);
    }

    @Override
    default Future<Void> connect(SocketAddress remoteAddress, Promise<Void> promise) {
        return pipeline().connect(remoteAddress, promise);
    }

    @Override
    default Future<Void> bind(SocketAddress localAddress, Promise<Void> promise) {
        return pipeline().bind(localAddress, promise);
    }

    @Override
    default Future<Void> deregister() {
        return pipeline().deregister();
    }

    @Override
    default Future<Void> close() {
        return pipeline().close();
    }

    @Override
    default Future<Void> disconnect() {
        return pipeline().disconnect();
    }

    @Override
    default Future<Void> connect(SocketAddress remoteAddress, SocketAddress localAddress) {
        return pipeline().connect(remoteAddress, localAddress);
    }

    @Override
    default Future<Void> connect(SocketAddress remoteAddress) {
        return pipeline().connect(remoteAddress);
    }

    @Override
    default Future<Void> bind(SocketAddress localAddress) {
        return pipeline().bind(localAddress);
    }

    @Override
    default <T> Promise<T> newPromise() {
        return pipeline().newPromise();
    }

    @Override
    default <T> Future<T> newSucceededFuture(T result) {
        return pipeline().newSucceededFuture(result);
    }

    @Override
    default <T> Future<T> newFailedFuture(Throwable cause) {
        return pipeline().newFailedFuture(cause);
    }

    @Override
    default Future<Void> register() {
        return pipeline().register();
    }

    @Override
    default Future<Void> register(Promise<Void> promise) {
        return pipeline().register(promise);
    }

    @Override
    default Future<Void> shutdown(ChannelShutdownType type, Promise<Void> promise) {
        return pipeline().shutdown(type, promise);
    }

    @Override
    default Future<Void> shutdown(ChannelShutdownType type) {
        return pipeline().shutdown(type);
    }
}
