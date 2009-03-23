/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.channel;

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
 * <li>the {@link ChannelPipeline} which handles all {@linkplain ChannelEvent I/O events and requests}
 *     associated with the channel.</li>
 * </ul>
 *
 * <h3>All I/O operations are asynchronous.</h3>
 *
 * All I/O operations in Netty are asynchronous.  It means any I/O calls will
 * return immediately with no guarantee that the requested I/O operation has
 * been completed at the end of the call.  Instead, you will be returned with
 * a {@link ChannelFuture} instance which tells you when the requested I/O
 * operation has succeeded, failed, or canceled.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 * @apiviz.landmark
 * @apiviz.composedOf org.jboss.netty.channel.ChannelConfig
 * @apiviz.composedOf org.jboss.netty.channel.ChannelPipeline
 */
public interface Channel extends Comparable<Channel>{

    /**
     * The {@link #getInterestOps() interestOps} value which tells that the
     * I/O thread will not read a message from the channel but will perform
     * the requested write operation immediately.
     */
    static int OP_NONE = 0;

    /**
     * The {@link #getInterestOps() interestOps} value which tells that the
     * I/O thread will read a message from the channel and will perform the
     * requested write operation immediately.
     */
    static int OP_READ = 1;

    /**
     * The {@link #getInterestOps() interestOps} value which tells that the
     * I/O thread will not read a message from the channel and will not perform
     * the requested write operation immediately.  Any write requests made when
     * {@link #OP_WRITE} flag is set are queued until the I/O thread is ready
     * to process the queued write requests.
     */
    static int OP_WRITE = 4;

    /**
     * The {@link #getInterestOps() interestOps} value which tells that the
     * I/O thread will read a message from the channel but will not perform
     * the requested write operation immediately.  Any write requests made when
     * {@link #OP_WRITE} flag is set are queued until the I/O thread is ready
     * to process the queued write requests.
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
     */
    SocketAddress getRemoteAddress();

    /**
     * Sends a message to this channel asynchronously.
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
     * message instead of this channel's current remote address.
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
     * return (getInterestOps() & OP_WRITE) != 0;
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
