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
 * A {@link ChannelEvent} which represents the change of the {@link Channel}
 * state.  It can mean the notification of a change or the request for a
 * change, depending on whether it is a upstream event or a downstream event
 * respectively.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 * @apiviz.has org.jboss.netty.channel.ChannelState
 */
public interface ChannelStateEvent extends ChannelEvent {

    /**
     * Returns the changed property of the {@link Channel}.
     */
    ChannelState getState();

    /**
     * Returns the value of the changed property of the {@link Channel}.
     * Please refer to {@link ChannelState} documentation to find out the
     * allowed values for each property.
     */
    Object getValue();
}
