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

import io.netty5.buffer.ByteBuf;
import io.netty5.buffer.ByteBufAllocator;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.channel.socket.DatagramChannel;
import io.netty5.channel.socket.DatagramPacket;
import io.netty5.channel.socket.ServerSocketChannel;
import io.netty5.channel.socket.SocketChannel;
import io.netty5.util.AttributeMap;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.Promise;

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
 * It is important to call {@link #close()} to release all
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
     * requested write operation immediately.  Any write requests made when
     * this method returns {@code false} are queued until the I/O thread is
     * ready to process the queued write requests.
     */
    default boolean isWritable() {
        ChannelOutboundBuffer buf = unsafe().outboundBuffer();
        return buf != null && buf.isWritable();
    }

    /**
     * Get how many bytes can be written until {@link #isWritable()} returns {@code false}.
     * This quantity will always be non-negative. If {@link #isWritable()} is {@code false} then 0.
     */
    default long bytesBeforeUnwritable() {
        ChannelOutboundBuffer buf = unsafe().outboundBuffer();
        // isWritable() is currently assuming if there is no outboundBuffer then the channel is not writable.
        // We should be consistent with that here.
        return buf != null ? buf.bytesBeforeUnwritable() : 0;
    }

    /**
     * Get how many bytes must be drained from underlying buffers until {@link #isWritable()} returns {@code true}.
     * This quantity will always be non-negative. If {@link #isWritable()} is {@code true} then 0.
     */
    default long bytesBeforeWritable() {
        ChannelOutboundBuffer buf = unsafe().outboundBuffer();
        // isWritable() is currently assuming if there is no outboundBuffer then the channel is not writable.
        // We should be consistent with that here.
        return buf != null ? buf.bytesBeforeWritable() : Long.MAX_VALUE;
    }

    /**
     * Returns an <em>internal-use-only</em> object that provides unsafe operations.
     */
    Unsafe unsafe();

    /**
     * Return the assigned {@link ChannelPipeline}.
     */
    ChannelPipeline pipeline();

    /**
     * Return the assigned {@link ByteBufAllocator} which will be used to allocate {@link ByteBuf}s.
     * @deprecated Use {@link #bufferAllocator()}
     */
    @Deprecated
    default ByteBufAllocator alloc() {
        return config().getAllocator();
    }

    /**
     * Return the assigned {@link BufferAllocator} which will be used to allocate {@link io.netty5.buffer.api.Buffer}s.
     */
    default BufferAllocator bufferAllocator() {
        return config().getBufferAllocator();
    }

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

    /**
     * <em>Unsafe</em> operations that should <em>never</em> be called from user-code. These methods
     * are only provided to implement the actual transport, and must be invoked from an I/O thread except for the
     * following methods:
     * <ul>
     *   <li>{@link #localAddress()}</li>
     *   <li>{@link #remoteAddress()}</li>
     *   <li>{@link #closeForcibly()}</li>
     *   <li>{@link #register(Promise)}</li>
     *   <li>{@link #deregister(Promise)}</li>
     * </ul>
     */
    interface Unsafe {

        /**
         * Return the assigned {@link RecvBufferAllocator.Handle} which will be used to allocate {@link ByteBuf}'s when
         * receiving data.
         */
        RecvBufferAllocator.Handle recvBufAllocHandle();

        /**
         * Return the {@link SocketAddress} to which is bound local or
         * {@code null} if none.
         */
        SocketAddress localAddress();

        /**
         * Return the {@link SocketAddress} to which is bound remote or
         * {@code null} if none is bound yet.
         */
        SocketAddress remoteAddress();

        /**
         * Register the {@link Channel} of the {@link Promise} and notify
         * the {@link Future} once the registration was complete.
         */
        void register(Promise<Void> promise);

        /**
         * Bind the {@link SocketAddress} to the {@link Channel} of the {@link Promise} and notify
         * it once its done.
         */
        void bind(SocketAddress localAddress, Promise<Void> promise);

        /**
         * Connect the {@link Channel} of the given {@link Future} with the given remote {@link SocketAddress}.
         * If a specific local {@link SocketAddress} should be used it need to be given as argument. Otherwise just
         * pass {@code null} to it.
         *
         * The {@link Promise} will get notified once the connect operation was complete.
         */
        void connect(SocketAddress remoteAddress, SocketAddress localAddress, Promise<Void> promise);

        /**
         * Disconnect the {@link Channel} of the {@link Future} and notify the {@link Promise} once the
         * operation was complete.
         */
        void disconnect(Promise<Void> promise);

        /**
         * Close the {@link Channel} of the {@link Promise} and notify the {@link Promise} once the
         * operation was complete.
         */
        void close(Promise<Void> promise);

        /**
         * Closes the {@link Channel} immediately without firing any events.  Probably only useful
         * when registration attempt failed.
         */
        void closeForcibly();

        /**
         * Deregister the {@link Channel} of the {@link Promise} from {@link EventLoop} and notify the
         * {@link Promise} once the operation was complete.
         */
        void deregister(Promise<Void> promise);

        /**
         * Schedules a read operation that fills the inbound buffer of the first {@link ChannelHandler} in the
         * {@link ChannelPipeline}.  If there's already a pending read operation, this method does nothing.
         */
        void beginRead();

        /**
         * Schedules a write operation.
         */
        void write(Object msg, Promise<Void> promise);

        /**
         * Flush out all write operations scheduled via {@link #write(Object, Promise)}.
         */
        void flush();

        /**
         * Returns the {@link ChannelOutboundBuffer} of the {@link Channel} where the pending write requests are stored.
         */
        ChannelOutboundBuffer outboundBuffer();
    }
}
