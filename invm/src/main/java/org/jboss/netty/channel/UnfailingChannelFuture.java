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
 * The {@link ChannelFuture} which can not fail at all.  Any attempt to mark
 * this future as failure, by calling {@link #setFailure(Throwable)} will raise
 * an {@link IllegalStateException}.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 */
public class UnfailingChannelFuture extends DefaultChannelFuture {

    /**
     * Creates a new instance.
     *
     * @param channel
     *        the {@link Channel} associated with this future
     * @param cancellable
     *        {@code true} if and only if this future can be canceled
     */
    public UnfailingChannelFuture(Channel channel, boolean cancellable) {
        super(channel, cancellable);
    }

    @Override
    public boolean setFailure(Throwable cause) {
        throw new IllegalStateException("Can not fail");
    }
}
