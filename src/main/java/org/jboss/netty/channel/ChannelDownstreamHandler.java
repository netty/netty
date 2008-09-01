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
 * Handles or intercepts a downstream {@link ChannelEvent}, and fires a
 * {@link ChannelEvent} to the next handler in a {@link ChannelPipeline}.
 * <p>
 * A downstream event is an event which is supposed to be processed from the
 * last handler to the first handler in the {@link ChannelPipeline}.
 * For example, all I/O requests made by a user application are downstream
 * events.
 * <p>
 * In most common use case of this interface is to intercept an I/O request
 * such as {@link Channel#write(Object)} and {@link Channel#close()}.
 *
 * <h3>Thread safety</h3>
 * <p>
 * {@link #handleDownstream(ChannelHandlerContext, ChannelEvent) handleDownstream}
 * may be invoked by more than one thread simultaneously.  If the handler
 * accesses a shared resource or stores stateful information, you might need
 * proper synchronization in the handler implementation.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 * @apiviz.landmark
 */
public interface ChannelDownstreamHandler extends ChannelHandler {

    /**
     * Handles the specified downstream event.
     *
     * @param ctx  the context object for this handler
     * @param e    the downstream event to process or intercept
     */
    void handleDownstream(ChannelHandlerContext ctx, ChannelEvent e) throws Exception;
}
