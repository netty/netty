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
package org.jboss.netty.channel.group;

import java.net.SocketAddress;
import java.util.Set;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ServerChannel;

/**
 * A thread-safe {@link Set} that contains open {@link Channel}s and provides
 * various bulk operations on them.  Using {@link ChannelGroup}, you can
 * categorize {@link Channel}s into a meaningful group (e.g. on a per-service
 * or per-state basis.)  A closed {@link Channel} is automatically removed from
 * the collection, so that you don't need to worry about the life cycle of the
 * added {@link Channel}.  A {@link Channel} can belong to more than one
 * {@link ChannelGroup}.
 *
 * <h3>Simplify shutdown process with {@link ChannelGroup}</h3>
 * <p>
 * If both {@link ServerChannel}s and non-{@link ServerChannel}s exist in the
 * same {@link ChannelGroup}, any requested I/O operations on the group are
 * performed for the {@link ServerChannel}s first and then for the others.
 * <p>
 * This rule is very useful when you shut down a server in one shot:
 *
 * <pre>
 * ChannelGroup allChannels = new DefaultChannelGroup();
 *
 * public static void main(String[] args) throws Exception {
 *     ServerBootstrap b = new ServerBootstrap(..);
 *     ...
 *
 *     // Start the server
 *     b.getPipeline().addLast("handler", new MyHandler());
 *     Channel serverChannel = b.bind(..);
 *
 *     ... Wait until the shutdown signal reception ...
 *
 *     // Close the serverChannel and then all accepted connections.
 *     allChannels.close().awaitUninterruptibly();
 *     b.releaseExternalResources();
 * }
 *
 * public class MyHandler extends SimpleChannelUpstreamHandler {
 *     public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e) {
 *         // Add all open channels to the global group so that they are
 *         // closed on shutdown.
 *         allChannels.add(e.getChannel());
 *     }
 * }
 * </pre>
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 *
 * @apiviz.landmark
 * @apiviz.has org.jboss.netty.channel.group.ChannelGroupFuture oneway - - returns
 */
public interface ChannelGroup extends Set<Channel>, Comparable<ChannelGroup> {

    /**
     * Returns the name of this group.  A group name is purely for helping
     * you to distinguish one group from others.
     */
    String getName();

    /**
     * Returns the {@link Channel} whose ID matches the specified integer.
     *
     * @return the matching {@link Channel} if found. {@code null} otherwise.
     */
    Channel find(Integer id);

    /**
     * Calls {@link Channel#setInterestOps(int)} for all {@link Channel}s in
     * this group with the specified {@code interestOps}. Please note that
     * this operation is asynchronous as {@link Channel#setInterestOps(int)} is.
     *
     * @return the {@link ChannelGroupFuture} instance that notifies when
     *         the operation is done for all channels
     */
    ChannelGroupFuture setInterestOps(int interestOps);

    /**
     * Calls {@link Channel#setReadable(boolean)} for all {@link Channel}s in
     * this group with the specified boolean flag. Please note that this
     * operation is asynchronous as {@link Channel#setReadable(boolean)} is.
     *
     * @return the {@link ChannelGroupFuture} instance that notifies when
     *         the operation is done for all channels
     */
    ChannelGroupFuture setReadable(boolean readable);

    /**
     * Writes the specified {@code message} to all {@link Channel}s in this
     * group. If the specified {@code message} is an instance of
     * {@link ChannelBuffer}, it is automatically
     * {@linkplain ChannelBuffer#duplicate() duplicated} to avoid a race
     * condition. Please note that this operation is asynchronous as
     * {@link Channel#write(Object)} is.
     *
     * @return the {@link ChannelGroupFuture} instance that notifies when
     *         the operation is done for all channels
     */
    ChannelGroupFuture write(Object message);

    /**
     * Writes the specified {@code message} with the specified
     * {@code remoteAddress} to all {@link Channel}s in this group.  If the
     * specified {@code message} is an instance of {@link ChannelBuffer}, it is
     * automatically {@linkplain ChannelBuffer#duplicate() duplicated} to avoid
     * a race condition. Please note that this operation is asynchronous as
     * {@link Channel#write(Object, SocketAddress)} is.
     *
     * @return the {@link ChannelGroupFuture} instance that notifies when
     *         the operation is done for all channels
     */
    ChannelGroupFuture write(Object message, SocketAddress remoteAddress);

    /**
     * Disconnects all {@link Channel}s in this group from their remote peers.
     *
     * @return the {@link ChannelGroupFuture} instance that notifies when
     *         the operation is done for all channels
     */
    ChannelGroupFuture disconnect();

    /**
     * Unbinds all {@link Channel}s in this group from their local address.
     *
     * @return the {@link ChannelGroupFuture} instance that notifies when
     *         the operation is done for all channels
     */
    ChannelGroupFuture unbind();

    /**
     * Closes all {@link Channel}s in this group.  If the {@link Channel} is
     * connected to a remote peer or bound to a local address, it is
     * automatically disconnected and unbound.
     *
     * @return the {@link ChannelGroupFuture} instance that notifies when
     *         the operation is done for all channels
     */
    ChannelGroupFuture close();
}
