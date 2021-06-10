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
 * a {@link ChannelFuture} instance which will notify you when the requested I/O
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
 * It is important to call {@link #close()} or {@link ChannelOutboundInvoker#close(ChannelOutboundInvokerCallback)}
 * to release all resources once you are done with the {@link Channel}. This ensures all resources are
 * released in a proper way, i.e. filehandles.
 */
public interface Channel extends AttributeMap, ChannelOutboundInvoker<Channel>, Comparable<Channel> {

    /**
     * Returns the globally unique identifier of this {@link Channel}.
     */
    ChannelId id();

    /**
     * Return the {@link EventLoop} this {@link Channel} was registered to.
     */
    EventLoop eventLoop();

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
     * Returns the {@link ChannelFuture} which will be notified when this
     * channel is closed.  This method always returns the same future instance.
     */
    ChannelFuture closeFuture();

    /**
     * Returns {@code true} if and only if the I/O thread will perform the
     * requested write operation immediately.  Any write requests made when
     * this method returns {@code false} are queued until the I/O thread is
     * ready to process the queued write requests.
     */
    boolean isWritable();

    /**
     * Get how many bytes can be written until {@link #isWritable()} returns {@code false}.
     * This quantity will always be non-negative. If {@link #isWritable()} is {@code false} then 0.
     */
    long bytesBeforeUnwritable();

    /**
     * Get how many bytes must be drained from underlying buffers until {@link #isWritable()} returns {@code true}.
     * This quantity will always be non-negative. If {@link #isWritable()} is {@code true} then 0.
     */
    long bytesBeforeWritable();

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
     */
    ByteBufAllocator alloc();

    @Override
    default ChannelFuture bind(SocketAddress localAddress) {
        return pipeline().bind(localAddress);
    }

    @Override
    default ChannelFuture connect(SocketAddress remoteAddress) {
        return pipeline().connect(remoteAddress);
    }

    @Override
    default ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
        return pipeline().connect(remoteAddress, localAddress);
    }

    @Override
    default ChannelFuture disconnect() {
        return pipeline().disconnect();
    }

    @Override
    default ChannelFuture close() {
        return pipeline().close();
    }

    @Override
    default ChannelFuture register() {
        return pipeline().register();
    }

    @Override
    default ChannelFuture deregister() {
        return pipeline().deregister();
    }

    @Override
    default Channel read() {
        pipeline().read();
        return this;
    }

    @Override
    default Channel bind(SocketAddress localAddress, ChannelOutboundInvokerCallback callback) {
        pipeline().bind(localAddress, callback);
        return this;
    }

    @Override
    default Channel connect(SocketAddress remoteAddress, ChannelOutboundInvokerCallback callback) {
        pipeline().connect(remoteAddress, callback);
        return this;
    }

    @Override
    default Channel connect(SocketAddress remoteAddress, SocketAddress localAddress,
                            ChannelOutboundInvokerCallback callback) {
        pipeline().connect(remoteAddress, localAddress, callback);
        return this;
    }

    @Override
    default Channel disconnect(ChannelOutboundInvokerCallback callback) {
        pipeline().disconnect(callback);
        return this;
    }

    @Override
    default Channel close(ChannelOutboundInvokerCallback callback) {
        pipeline().close(callback);
        return this;
    }

    @Override
    default Channel register(ChannelOutboundInvokerCallback callback) {
        pipeline().register(callback);
        return this;
    }

    @Override
    default Channel deregister(ChannelOutboundInvokerCallback callback) {
        pipeline().deregister(callback);
        return this;
    }

    @Override
    default ChannelFuture write(Object msg) {
        return pipeline().write(msg);
    }

    @Override
    default Channel write(Object msg, ChannelOutboundInvokerCallback callback) {
        pipeline().write(msg, callback);
        return this;
    }

    @Override
    default Channel flush() {
        pipeline().flush();
        return this;
    }

    @Override
    default Channel writeAndFlush(Object msg, ChannelOutboundInvokerCallback callback) {
        pipeline().writeAndFlush(msg, callback);
        return this;
    }

    @Override
    default ChannelFuture writeAndFlush(Object msg) {
        return pipeline().writeAndFlush(msg);
    }

    @Override
    default ChannelPromise newPromise() {
        return pipeline().newPromise();
    }

    @Override
    default ChannelFuture newSucceededFuture() {
        return pipeline().newSucceededFuture();
    }

    @Override
    default ChannelFuture newFailedFuture(Throwable cause) {
        return pipeline().newFailedFuture(cause);
    }

    @Override
    default ChannelOutboundInvokerCallback voidCallback() {
        return pipeline().voidCallback();
    }

    /**
     * <em>Unsafe</em> operations that should <em>never</em> be called from user-code. These methods
     * are only provided to implement the actual transport, and must be invoked from an I/O thread except for the
     * following methods:
     * <ul>
     *   <li>{@link #localAddress()}</li>
     *   <li>{@link #remoteAddress()}</li>
     *   <li>{@link #closeForcibly()}</li>
     *   <li>{@link #register(ChannelOutboundInvokerCallback)}</li>
     *   <li>{@link #deregister(ChannelOutboundInvokerCallback)}</li>
     * </ul>
     */
    interface Unsafe {

        /**
         * Return the assigned {@link RecvByteBufAllocator.Handle} which will be used to allocate {@link ByteBuf}'s when
         * receiving data.
         */
        RecvByteBufAllocator.Handle recvBufAllocHandle();

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
         * Register the {@link Channel} and notify
         * the {@link ChannelOutboundInvokerCallback} once the operations is complete.
         *
         * @param callback  the {@link ChannelOutboundInvokerCallback} that is notified once the operation completes.
         */
        void register(ChannelOutboundInvokerCallback callback);

        /**
         * Bind the {@link SocketAddress} to the {@link Channel} and notify the {@link ChannelOutboundInvokerCallback}
         * once the operations is complete.
         *
         * @param localAddress      the local {@link SocketAddress}.
         * @param callback  the {@link ChannelOutboundInvokerCallback} that is notified once the operation completes.
         */
        void bind(SocketAddress localAddress, ChannelOutboundInvokerCallback callback);

        /**
         * Connect the {@link Channel} with the given remote {@link SocketAddress}.
         * If a specific local {@link SocketAddress} should be used it need to be given as argument. Otherwise just
         * pass {@code null} to it.
         *
         * The {@link ChannelOutboundInvokerCallback} will get notified once the connect operation was complete.
         *
         * @param remoteAddress     the remote {@link SocketAddress}.
         * @param localAddress      the local {@link SocketAddress} or {@link null}.
         * @param callback  the {@link ChannelOutboundInvokerCallback} that is notified once the operation completes.
         */
        void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelOutboundInvokerCallback callback);

        /**
         * Disconnect the {@link Channel} and notify the {@link ChannelOutboundInvokerCallback} once the
         * operation was complete
         *
         * @param callback  the {@link ChannelOutboundInvokerCallback} that is notified once the operation completes.
         */
        void disconnect(ChannelOutboundInvokerCallback callback);

        /**
         * Close the {@link Channel} and notify the {@link ChannelOutboundInvokerCallback} once the
         * operation was complete.
         *
         * @param callback  the {@link ChannelOutboundInvokerCallback} that is notified once the operation completes.
         */
        void close(ChannelOutboundInvokerCallback callback);

        /**
         * Closes the {@link Channel} immediately without firing any events.  Probably only useful
         * when registration attempt failed.
         */
        void closeForcibly();

        /**
         * Deregister the {@link Channel} from {@link EventLoop} and notify the
         * {@link ChannelOutboundInvokerCallback} once the operation was complete.
         *
         * @param callback  the {@link ChannelOutboundInvokerCallback} that is notified once the operation completes.
         */
        void deregister(ChannelOutboundInvokerCallback callback);

        /**
         * Schedules a read operation that fills the inbound buffer of the first {@link ChannelHandler} in the
         * {@link ChannelPipeline}. If there's already a pending read operation, this method does nothing.
         */
        void beginRead();

        /**
         * Schedules a write operation and notifies the {@link ChannelOutboundInvokerCallback} once the operation
         * completes.
         *
         * @param msg               the msg to write.
         * @param callback  the {@link ChannelOutboundInvokerCallback} that is notified once the operation completes.
         */
        void write(Object msg, ChannelOutboundInvokerCallback callback);

        /**
         * Flush out all write operations scheduled via {@link #write(Object, ChannelOutboundInvokerCallback)}
         *  and notifies the {@link ChannelOutboundInvokerCallback} once the operation completes.
         */
        void flush();

        /**
         * Returns the {@link ChannelOutboundBuffer} of the {@link Channel} where the pending write requests are stored.
         */
        ChannelOutboundBuffer outboundBuffer();
    }
}
