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
 * Handles or intercepts a {@link ChannelEvent}, and sends a
 * {@link ChannelEvent} to the next or previous handler in a
 * {@link ChannelPipeline}.
 *
 * <h3>Sub-types</h3>
 * <p>
 * {@link ChannelHandler} itself does not provide any method.  To handle a
 * {@link ChannelEvent} you need to implement its sub-interfaces.  There are
 * two sub-interfaces which handles a received event, one for upstream events
 * and the other for downstream events:
 * <ul>
 * <li>{@link ChannelUpstreamHandler} handles and intercepts an upstream {@link ChannelEvent}.</li>
 * <li>{@link ChannelDownstreamHandler} handles and intercepts a downstream {@link ChannelEvent}.</li>
 * </ul>
 *
 * You will also find more detailed explanation from the documentation of
 * each sub-interface on how an event is interpreted when it goes upstream and
 * downstream respectively.
 *
 * <h3>The context object</h3>
 * <p>
 * A {@link ChannelHandler} is provided with a {@link ChannelHandlerContext}
 * object.  A {@link ChannelHandler} is supposed to interact with the
 * {@link ChannelPipeline} it belongs to via a context object.  Using the
 * context object, the {@link ChannelHandler} can pass events to the next
 * or the previous handler or modify the behavior of the pipeline, or store the
 * information (attachment) which is specific to the handler.
 *
 * <h3>Additional resources worth reading</h3>
 * <p>
 * Please refer to the {@link ChannelEvent} and {@link ChannelPipeline} to find
 * out what a upstream event and a downstream event are, what fundamental
 * differences they have, and how they flow in a pipeline.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 * @apiviz.landmark
 * @apiviz.exclude ^org\.jboss\.netty\.handler\..*$
 */
public interface ChannelHandler {
    // This is a tag interface.
}
