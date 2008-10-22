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
 * An I/O event or I/O request associated with a {@link Channel}.
 * <p>
 * A {@link ChannelEvent} is supposed to be handled by the
 * {@link ChannelPipeline} which is owned by the {@link Channel} that
 * the event belongs to.  Once an event is sent to a {@link ChannelPipeline},
 * it is handled by a list of {@link ChannelHandler}s.
 *
 * <h3>Upstream events and downstream events, and their interpretation</h3>
 * <p>
 * Every event can be either a upstream event or a downstream event.
 * If an event flows from the first handler to the last handler in a
 * {@link ChannelPipeline}, we call it a upstream event and say <strong>"an
 * event goes upstream."</strong>  If an event flows from the last handler to
 * the first handler in a {@link ChannelPipeline}, we call it a downstream
 * event and say <strong>"an event goes downstream."</strong>  (Please refer
 * to the diagram in {@link ChannelPipeline} for more explanation.)
 * <p>
 * A {@link ChannelEvent} is interpreted differently by a {@link ChannelHandler}
 * depending on whether the event is a upstream event or a downstream event.
 * A upstream event represents the notification of what happened in the past.
 * By contrast, a downstream event represents the request of what should happen
 * in the future.  For example, a {@link MessageEvent} represents the
 * notification of a received message when it goes upstream, while it
 * represents the request of writing a message when it goes downstream.
 *
 * <h4>Additional resources worth reading</h4>
 * <p>
 * Please refer to the documentation of {@link ChannelHandler} and its sub-types
 * ({@link ChannelUpstreamHandler} for upstream events and
 *  {@link ChannelDownstreamHandler} for downstream events) to find out how
 * a {@link ChannelEvent} is interpreted depending on the type of the handler
 * more in detail.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 * @apiviz.landmark
 * @apiviz.composedOf org.jboss.netty.channel.ChannelFuture
 */
public interface ChannelEvent {

    /**
     * Returns the {@link Channel} which is associated with this event.
     */
    Channel getChannel();

    /**
     * Returns the {@link ChannelFuture} which is associated with this event.
     * If this event is a upstream event, this method will always return a
     * {@link SucceededChannelFuture} because the event has occurred already.
     * If this event is a downstream event (i.e. I/O request), the returned
     * future will be notified when the I/O request succeeds or fails.
     */
    ChannelFuture getFuture();
}
