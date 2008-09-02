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
