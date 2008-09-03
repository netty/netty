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

/**
 * The context type which enables the interaction
 * between a {@link ChannelHandler} and its {@link ChannelPipeline}
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 * @apiviz.owns org.jboss.netty.channel.ChannelHandler
 */
public interface ChannelHandlerContext {

    /**
     * Returns the {@link ChannelPipeline} that the {@link ChannelHandler}
     * belongs to.
     */
    ChannelPipeline getPipeline();

    /**
     * Returns the name of the {@link ChannelHandler} in the
     * {@link ChannelPipeline}.
     */
    String getName();

    /**
     * Returns the {@link ChannelHandler} that this context object is
     * serving.
     */
    ChannelHandler getHandler();

    /**
     * Returns {@code true} if and only if the {@link ChannelHandler} is an
     * instance of {@link ChannelUpstreamHandler}.
     */
    boolean canHandleUpstream();

    /**
     * Returns {@code true} if and only if the {@link ChannelHandler} is an
     * instance of {@link ChannelDownstreamHandler}.
     */
    boolean canHandleDownstream();

    /**
     * Sends the specified {@link ChannelEvent} to the next
     * {@link ChannelUpstreamHandler} in the {@link ChannelPipeline}.  It is
     * recommended to use the event generation methods in {@link Channels}
     * rather than calling this method directly.
     */
    void sendUpstream(ChannelEvent e);

    /**
     * Sends the specified {@link ChannelEvent} to the previous
     * {@link ChannelDownstreamHandler} in the {@link ChannelPipeline}.  It is
     * recommended to use the event generation methods in {@link Channels}
     * rather than calling this method directly.
     */
    void sendDownstream(ChannelEvent e);
}