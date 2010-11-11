/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.channel;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.SelectionKey;

import org.jboss.netty.channel.socket.DatagramChannel;
import org.jboss.netty.channel.socket.ServerSocketChannel;
import org.jboss.netty.channel.socket.SocketChannel;
import org.jboss.netty.channel.socket.nio.NioSocketChannelConfig;


/**
 * A nexus to a network socket or a component which is capable of I/O
 * operations such as read, write, connect, and bind.
 * <p>
 * A channel provides a user:
 * <ul>
 * <li>the current state of the channel (e.g. is it open? is it connected?),</li>
 * <li>the {@linkplain ChannelConfig configuration parameters} of the channel (e.g. receive buffer size),</li>
 * <li>the I/O operations that the channel supports (e.g. read, write, connect, and bind), and</li>
 * <li>the {@link ChannelPipeline} which handles all {@linkplain ChannelEvent I/O events and requests}
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
 * A {@link Channel} can have a {@linkplain #getParent() parent} depending on
 * how it was created.  For instance, a {@link SocketChannel}, that was accepted
 * by {@link ServerSocketChannel}, will return the {@link ServerSocketChannel}
 * as its parent on {@link #getParent()}.
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
 * <h3>InterestOps</h3>
 * <p>
 * A {@link Channel} has a property called {@link #getInterestOps() interestOps}
 * which is similar to that of {@link SelectionKey#interestOps() NIO SelectionKey}.
 * It is represented as a <a href="http://en.wikipedia.org/wiki/Bit_field">bit
 * field</a> which is composed of the two flags.
 * <ul>
 * <li>{@link #OP_READ} - If set, a message sent by a remote peer will be read
 *     immediately.  If unset, the message from the remote peer will not be read
 *     until the {@link #OP_READ} flag is set again (i.e. read suspension).</li>
 * <li>{@link #OP_WRITE} - If set, a write request will not be sent to a remote
 *     peer until the {@link #OP_WRITE} flag is cleared and the write request
 *     will be pending in a queue.  If unset, the write request will be flushed
 *     out as soon as possible from the queue.</li>
 * <li>{@link #OP_READ_WRITE} - This is a combination of {@link #OP_READ} and
 *     {@link #OP_WRITE}, which means only write requests are suspended.</li>
 * <li>{@link #OP_NONE} - This is a combination of (NOT {@link #OP_READ}) and
 *     (NOT {@link #OP_WRITE}), which means only read operation is suspended.</li>
 * </ul>
 * </p><p>
 * You can set or clear the {@link #OP_READ} flag to suspend and resume read
 * operation via {@link #setReadable(boolean)}.
 * </p><p>
 * Please note that you cannot suspend or resume write operation just like you
 * can set or clear {@link #OP_READ}. The {@link #OP_WRITE} flag is read only
 * and provided simply as a mean to tell you if the size of pending write
 * requests exceeded a certain threshold or not so that you don't issue too many
 * pending writes that lead to an {@link OutOfMemoryError}.  For example, the
 * NIO socket transport uses the {@code writeBufferLowWaterMark} and
 * {@code writeBufferHighWaterMark} properties in {@link NioSocketChannelConfig}
 * to determine when to set or clear the {@link #OP_WRITE} flag.
 * </p>
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2244 $, $Date: 2010-04-16 14:07:37 +0900 (Fri, 16 Apr 2010) $
 *
 * @apiviz.landmark
 * @apiviz.composedOf org.jboss.netty.channel.ChannelConfig
 * @apiviz.composedOf org.jboss.netty.channel.ChannelPipeline
 *
 * @apiviz.exclude ^org\.jboss\.netty\.channel\.([a-z]+\.)+[^\.]+Channel$
 */
public interface Channel extends Comparable<Channel> {

    /**
     * The {@link #getInterestOps() interestOps} value which tells that only
     * read operation has been suspended.
     */
    static int OP_NONE = 0;

    /**
     * The {@link #getInterestOps() interestOps} value which tells that neither
     * read nor write operation has been suspended.
     */
    static int OP_READ = 1;

    /**
     * The {@link #getInterestOps() interestOps} value which tells that both
     * read and write operation has been suspended.
     */
    static int OP_WRITE = 4;

    /**
     * The {@link #getInterestOps() interestOps} value which tells that only
     * write operation has been suspended.
     */
    static int OP_READ_WRITE = OP_READ | OP_WRITE;

    /**
     * Returns the unique integer ID of this channel.
     */
    Integer getId();

    /**
     * Returns the {@link ChannelFactory} which created this channel.
     */
    ChannelFactory getFactory();

    /**
     * Returns the parent of this channel.
     *
     * @return the parent channel.
     *         {@code null} if this channel does not have a parent channel.
     */
    Channel getParent();

    /**
     * Returns the configuration of this channel.
     */
    ChannelConfig getConfig();

    /**
     * Returns the {@link ChannelPipeline} which handles {@link ChannelEvent}s
     * associated with this channel.
     */
    ChannelPipeline getPipeline();

    /**
     * Returns {@code true} if and only if this channel is open.
     */
    boolean isOpen();

    /**
     * Returns {@code true} if and only if this channel is bound to a
     * {@linkplain #getLocalAddress() local address}.
     */
    boolean isBound();

    /**
     * Returns {@code true} if and only if this channel is connected to a
     * {@linkplain #getRemoteAddress() remote address}.
     */
    boolean isConnected();

    /**
     * Returns the local address where this channel is bound to.  The returned
     * {@link SocketAddress} is supposed to be down-cast into more concrete
     * type such as {@link InetSocketAddress} to retrieve the detailed
     * information.
     *
     * @return the local address of this channel.
     *         {@code null} if this channel is not bound.
     */
    SocketAddress getLocalAddress();

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
     *         use {@link MessageEvent#getRemoteAddress()} to determine
     *         the origination of the received message as this method will
     *         return {@code null}.
     */
    SocketAddress getRemoteAddress();

    /**
     * Sends a message to this channel asynchronously.    If this channel was
     * created by a connectionless transport (e.g. {@link DatagramChannel})
     * and is not connected yet, you have to call {@link #write(Object, SocketAddress)}
     * instead.  Otherwise, the write request will fail with
     * {@link NotYetConnectedException} and an {@code 'exceptionCaught'} event
     * will be triggered.
     *
     * @param message the message to write
     *
     * @return the {@link ChannelFuture} which will be notified when the
     *         write request succeeds or fails
     *
     * @throws NullPointerException if the specified message is {@code null}
     */
    ChannelFuture write(Object message);

    /**
     * Sends a message to this channel asynchronously.  It has an additional
     * parameter that allows a user to specify where to send the specified
     * message instead of this channel's current remote address.  If this
     * channel was created by a connectionless transport (e.g. {@link DatagramChannel})
     * and is not connected yet, you must specify non-null address.  Otherwise,
     * the write request will fail with {@link NotYetConnectedException} and
     * an {@code 'exceptionCaught'} event will be triggered.
     *
     * @param message       the message to write
     * @param remoteAddress where to send the specified message.
     *                      This method is identical to {@link #write(Object)}
     *                      if {@code null} is specified here.
     *
     * @return the {@link ChannelFuture} which will be notified when the
     *         write request succeeds or fails
     *
     * @throws NullPointerException if the specified message is {@code null}
     */
    ChannelFuture write(Object message, SocketAddress remoteAddress);

    /**
     * Binds this channel to the specified local address asynchronously.
     *
     * @param localAddress where to bind
     *
     * @return the {@link ChannelFuture} which will be notified when the
     *         bind request succeeds or fails
     *
     * @throws NullPointerException if the specified address is {@code null}
     */
    ChannelFuture bind(SocketAddress localAddress);

    /**
     * Connects this channel to the specified remote address asynchronously.
     *
     * @param remoteAddress where to connect
     *
     * @return the {@link ChannelFuture} which will be notified when the
     *         connection request succeeds or fails
     *
     * @throws NullPointerException if the specified address is {@code null}
     */
    ChannelFuture connect(SocketAddress remoteAddress);

    /**
     * Disconnects this channel from the current remote address asynchronously.
     *
     * @return the {@link ChannelFuture} which will be notified when the
     *         disconnection request succeeds or fails
     */
    ChannelFuture disconnect();

    /**
     * Unbinds this channel from the current local address asynchronously.
     *
     * @return the {@link ChannelFuture} which will be notified when the
     *         unbind request succeeds or fails
     */
    ChannelFuture unbind();

    /**
     * Closes this channel asynchronously.  If this channel is bound or
     * connected, it will be disconnected and unbound first.  Once a channel
     * is closed, it can not be open again.  Calling this method on a closed
     * channel has no effect.  Please note that this method always returns the
     * same future instance.
     *
     * @return the {@link ChannelFuture} which will be notified when the
     *         close request succeeds or fails
     */
    ChannelFuture close();

    /**
     * Returns the {@link ChannelFuture} which will be notified when this
     * channel is closed.  This method always returns the same future instance.
     */
    ChannelFuture getCloseFuture();

    /**
     * Returns the current {@code interestOps} of this channel.
     *
     * @return {@link #OP_NONE}, {@link #OP_READ}, {@link #OP_WRITE}, or
     *         {@link #OP_READ_WRITE}
     */
    int getInterestOps();

    /**
     * Returns {@code true} if and only if the I/O thread will read a message
     * from this channel.  This method is a shortcut to the following code:
     * <pre>
     * return (getInterestOps() & OP_READ) != 0;
     * </pre>
     */
    boolean isReadable();

    /**
     * Returns {@code true} if and only if the I/O thread will perform the
     * requested write operation immediately.  Any write requests made when
     * this method returns {@code false} are queued until the I/O thread is
     * ready to process the queued write requests.  This method is a shortcut
     * to the following code:
     * <pre>
     * return (getInterestOps() & OP_WRITE) == 0;
     * </pre>
     */
    boolean isWritable();

    /**
     * Changes the {@code interestOps} of this channel asynchronously.
     *
     * @param interestOps the new {@code interestOps}
     *
     * @return the {@link ChannelFuture} which will be notified when the
     *         {@code interestOps} change request succeeds or fails
     */
    ChannelFuture setInterestOps(int interestOps);

    /**
     * Suspends or resumes the read operation of the I/O thread asynchronously.
     * This method is a shortcut to the following code:
     * <pre>
     * int interestOps = getInterestOps();
     * if (readable) {
     *     setInterestOps(interestOps | OP_READ);
     * } else {
     *     setInterestOps(interestOps & ~OP_READ);
     * }
     * </pre>
     *
     * @param readable {@code true} to resume the read operation and
     *                 {@code false} to suspend the read operation
     *
     * @return the {@link ChannelFuture} which will be notified when the
     *         {@code interestOps} change request succeeds or fails
     */
    ChannelFuture setReadable(boolean readable);
}
