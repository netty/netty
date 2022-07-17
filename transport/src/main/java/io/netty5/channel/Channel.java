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
package io.netty5.channel;

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.channel.socket.DatagramChannel;
import io.netty5.channel.socket.DatagramPacket;
import io.netty5.channel.socket.ServerSocketChannel;
import io.netty5.channel.socket.SocketChannel;
import io.netty5.util.AttributeKey;
import io.netty5.util.AttributeMap;
import io.netty5.util.concurrent.Future;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * A nexus to a network socket or a component which is capable of I/O
 * operations such as read, write, connect, and bind.
 * <p>
 * A channel provides a user:
 * <ul>
 * <li>the current state of the channel (e.g. is it open? is it connected?),</li>
 * <li>the configuration parameters of the channel (e.g. receive buffer size),</li>
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
 *
 * <h3>Storing stateful information</h3>
 *
 * {@link #attr(AttributeKey)} allow you to
 * store and access stateful information that is related with a handler and its
 * context.  Please refer to {@link ChannelHandler} to learn various recommended
 * ways to manage stateful information.
 *
 * <h3>Release resources</h3>
 * <p>
 * It is important to call {@link #close()} to release all
 * resources once you are done with the {@link Channel}. This ensures all resources are
 * released in a proper way, i.e. filehandles.
 *
 * <h3>Configuration / Option map</h3>
 *
 * An option map property is a dynamic write-only property which allows
 * the configuration of a {@link Channel} without down-casting.
 * To update an option map, please call {@link #setOption(ChannelOption, Object)}.
 * <p>
 * All {@link Channel} types have the following options:
 *
 * <table border="1" cellspacing="0" cellpadding="6">
 * <tr>
 * <th>Name</th>
 * </tr><tr>
 * <td>{@link ChannelOption#CONNECT_TIMEOUT_MILLIS}</td>
 * </tr><tr>
 * <td>{@link ChannelOption#WRITE_SPIN_COUNT}</td>
 * </tr><tr>
 * <td>{@link ChannelOption#WRITE_BUFFER_WATER_MARK}</td>
 * </tr><tr>
 * <td>{@link ChannelOption#BUFFER_ALLOCATOR}</td>
 * </tr><tr>
 * <td>{@link ChannelOption#AUTO_READ}</td>
 * </tr><tr>
 * <td>{@link ChannelOption#ALLOW_HALF_CLOSURE}</td>
 * </tr><tr>
 * <td>{@link ChannelOption#MAX_MESSAGES_PER_WRITE}</td>
 * </tr><tr>
 * <td>{@link ChannelOption#ALLOW_HALF_CLOSURE}</td>
 * </tr>
 * </table>
 * <p>
 * More options are available in the sub-types of {@link Channel}. For
 * example, you can configure the parameters which are specific to a TCP/IP
 * socket as explained in {@link SocketChannel}.
 */
public interface Channel extends AttributeMap, ChannelOutboundInvoker, Comparable<Channel>, IoHandle {

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
     * Return the value of the given {@link ChannelOption}
     *
     * @param option                            the {@link ChannelOption}.
     * @return                                  the value for the {@link ChannelOption}
     * @param <T>                               the type of the value.
     * @throws ChannelException                 thrown on error.
     * @throws UnsupportedOperationException    if the {@link ChannelOption} is not supported.
     */
    <T> T getOption(ChannelOption<T> option);

    /**
     * Sets a configuration property with the specified name and value.
     *
     * @param option                            the {@link ChannelOption}.
     * @param value                             the value for the {@link ChannelOption}
     * @return                                  itself.
     * @param <T>                               the type of the value.
     * @throws ChannelException                 thrown on error.
     * @throws UnsupportedOperationException    if the {@link ChannelOption} is not supported.
     */
    <T> Channel setOption(ChannelOption<T> option, T value);

    /**
     * Returns {@code true} if the given {@link ChannelOption} is supported by this {@link Channel} implementation.
     * If this methods returns {@code false}, calls to {@link #setOption(ChannelOption, Object)}
     * and {@link #getOption(ChannelOption)} with the {@link ChannelOption} will throw an
     * {@link UnsupportedOperationException}.
     *
     * @param option    the option.
     * @return          {@code} true if supported, {@code false} otherwise.
     */
    boolean isOptionSupported(ChannelOption<?> option);

    /**
     * Returns {@code true} if the {@link Channel} is open and may get active later
     */
    boolean isOpen();

    /**
     * Return {@code true} if the {@link Channel} is active and so connected.
     */
    boolean isActive();

    /**
     * Returns {@code true} if the {@link ChannelShutdownDirection} of the {@link Channel} was shutdown before.
     */
    boolean isShutdown(ChannelShutdownDirection direction);

    /**
     * Return the {@link ChannelMetadata} of the {@link Channel} which describe the nature of the {@link Channel}.
     */
    ChannelMetadata metadata();

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
     * Returns {@code true} if and only if the I/O thread will perform the
     * requested flush operation immediately. Any write requests made when
     * this method returns {@code false} are queued until the I/O thread is
     * ready to process the queued write requests.
     */
    default boolean isWritable() {
        return writableBytes() > 0;
    }

    /**
     * Returns how many bytes can be written before the {@link Channel} becomes 'unwritable'.
     * Once a {@link Channel} becomes unwritable, all messages will be queued until the I/O thread is
     * ready to process the queued write requests.
     *
     * @return the number of bytes that can be written before the {@link Channel} becomes unwritable.
     */
    long writableBytes();

    /**
     * Return the assigned {@link ChannelPipeline}.
     */
    ChannelPipeline pipeline();

    /**
     * Return the assigned {@link BufferAllocator} which will be used to allocate {@link Buffer}s.
     */
    BufferAllocator bufferAllocator();

    @Override
    default Channel read() {
        pipeline().read();
        return this;
    }

    @Override
    default Future<Void> bind(SocketAddress localAddress) {
        return pipeline().bind(localAddress);
    }

    @Override
    default Future<Void> connect(SocketAddress remoteAddress) {
        return pipeline().connect(remoteAddress);
    }

    @Override
    default Future<Void> connect(SocketAddress remoteAddress, SocketAddress localAddress) {
        return pipeline().connect(remoteAddress, localAddress);
    }

    @Override
    default Future<Void> disconnect() {
        return pipeline().disconnect();
    }

    @Override
    default Future<Void> close() {
        return pipeline().close();
    }

    @Override
    default Future<Void> shutdown(ChannelShutdownDirection direction) {
        return pipeline().shutdown(direction);
    }

    @Override
    default Future<Void> register() {
        return pipeline().register();
    }

    @Override
    default Future<Void> deregister() {
        return pipeline().deregister();
    }

    @Override
    default Future<Void> write(Object msg) {
        return pipeline().write(msg);
    }

    @Override
    default Future<Void> writeAndFlush(Object msg) {
        return pipeline().writeAndFlush(msg);
    }

    @Override
    default Channel flush() {
        pipeline().flush();
        return this;
    }

    @Override
    default Future<Void> sendOutboundEvent(Object event) {
        return pipeline().sendOutboundEvent(event);
    }
}
