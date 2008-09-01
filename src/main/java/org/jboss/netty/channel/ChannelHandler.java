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
 * Handles or intercepts a {@link ChannelEvent}, and fires a new, modified, or
 * existing {@link ChannelEvent} to the next handler in a {@link ChannelPipeline}.
 * <p>
 * This is a tag interface.  There are two sub-interfaces which processes
 * a received event actually, one for upstream events and the other for
 * downstream events:
 * <ul>
 * <li>{@link ChannelUpstreamHandler} handles and intercepts
 *     a {@link ChannelEvent} fired by an I/O thread.</li>
 * <li>{@link ChannelDownstreamHandler} handles and intercepts
 *     a {@link ChannelEvent} fired by a user via the methods in
 *     the {@link Channel} interface and the {@link Channels} utility class.</li>
 * </ul>
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 */
public interface ChannelHandler {
    // This is a tag interface.
}
