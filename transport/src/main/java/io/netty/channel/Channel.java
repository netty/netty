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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.MessageBuf;
import io.netty.channel.socket.DatagramChannel;
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
 * <a href="http://en.wikipedia.org/wiki/Secure_Shell">SSH</a> do.
 *
 * <h3>Downcast to access transport-specific operations</h3>
 * <p>
 * Some transports exposes additional operations that is specific to the
 * transport.  Down-cast the {@link Channel} to sub-type to invoke such
 * operations.  For example, with the old I/O datagram transport, multicast
 * join / leave operations are provided by {@link DatagramChannel}.
 *
 * @apiviz.landmark
 * @apiviz.composedOf io.netty.channel.ChannelConfig
 * @apiviz.composedOf io.netty.channel.ChannelPipeline
 *
 * @apiviz.exclude ^io\.netty\.channel\.([a-z]+\.)+[^\.]+Channel$
 */
public interface Channel extends AttributeMap, ChannelOutboundInvoker, ChannelPropertyAccess, Comparable<Channel> {

    /**
     * Returns the unique integer ID of this channel.
     */
    Integer id();

    /**
     * Return the {@link EventLoop} this {@link Channel} was registered too.
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
     * Returns {@code true} if the {@link Channel} is open an may get active later
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

    ByteBuf outboundByteBuffer();
    <T> MessageBuf<T> outboundMessageBuffer();

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
     *         use {@link io.netty.channel.socket.DatagramPacket#remoteAddress()} to determine
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
     * <strong>Caution</strong> for transport implementations use only!
     */
    Unsafe unsafe();

    /**
     * <strong>Unsafe</strong> operations that should <strong>never</strong> be called
     * from user-code. These methods are only provided to implement the actual transport.
     */
    interface Unsafe {
        /**
         * Return the {@link ChannelHandlerContext} which is directly connected to the outbound of the
         * underlying transport.
         */
        ChannelHandlerContext directOutboundContext();

        /**
         * Return a {@link VoidChannelFuture}. This method always return the same instance.
         */
        ChannelFuture voidFuture();

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
         * Register the {@link Channel} of the {@link ChannelFuture} with the {@link EventLoop} and notify
         * the {@link ChannelFuture} once the registration was complete.
         */
        void register(EventLoop eventLoop, ChannelFuture future);

        /**
         * Bind the {@link SocketAddress} to the {@link Channel} of the {@link ChannelFuture} and notify
         * it once its done.
         */
        void bind(SocketAddress localAddress, ChannelFuture future);

        /**
         * Connect the {@link Channel} of the given {@link ChannelFuture} with the given remote {@link SocketAddress}.
         * If a specific local {@link SocketAddress} should be used it need to be given as argument. Otherwise just
         * pass {@code null} to it.
         *
         * The {@link ChannelFuture} will get notified once the connect operation was complete.
         */
        void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelFuture future);

        /**
         * Disconnect the {@link Channel} of the {@link ChannelFuture} and notify the {@link ChannelFuture} once the
         * operation was complete.
         */
        void disconnect(ChannelFuture future);

        /**
         * Close the {@link Channel} of the {@link ChannelFuture} and notify the {@link ChannelFuture} once the
         * operation was complete.
         */
        void close(ChannelFuture future);

        /**
         * Closes the {@link Channel} immediately without firing any events.  Probably only useful
         * when registration attempt failed.
         */
        void closeForcibly();

        /**
         * Deregister the {@link Channel} of the {@link ChannelFuture} from {@link EventLoop} and notify the
         * {@link ChannelFuture} once the operation was complete.
         */
        void deregister(ChannelFuture future);

        /**
         * Flush out all data that was buffered in the buffer of the {@link #directOutboundContext()} and was not
         * flushed out yet. After that is done the {@link ChannelFuture} will get notified
         */
        void flush(ChannelFuture future);

        /**
         * Flush out all data now.
         */
        void flushNow();

        /**
         * Suspend reads from the underlying transport, which basicly has the effect of no new data that will
         * get dispatched.
         */
        void suspendRead();

        /**
         * Resume reads from the underlying transport. If {@link #suspendRead()} was not called before, this
         * has no effect.
         */
        void resumeRead();

        /**
         * Send a {@link FileRegion} to the remote peer and notify the {@link ChannelFuture} once it completes
         * or an error was detected. Once the {@link FileRegion} was transfered or an error was thrown it will
         * automaticly closed via {@link io.netty.channel.FileRegion#close()}.
         */
        void sendFile(FileRegion region, ChannelFuture future);
    }
}
