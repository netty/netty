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
package org.jboss.netty.buffer;

import java.nio.ByteOrder;

/**
 * A skeletal implementation of {@link ChannelBufferFactory}.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 */
public abstract class AbstractChannelBufferFactory implements ChannelBufferFactory {

    private final ByteOrder defaultOrder;

    /**
     * Creates a new factory whose default {@link ByteOrder} is
     * {@link ByteOrder#BIG_ENDIAN}.
     */
    protected AbstractChannelBufferFactory() {
        this(ByteOrder.BIG_ENDIAN);
    }

    /**
     * Creates a new factory with the specified default {@link ByteOrder}.
     *
     * @param defaultOrder the default {@link ByteOrder} of this factory
     */
    protected AbstractChannelBufferFactory(ByteOrder defaultOrder) {
        if (defaultOrder == null) {
            throw new NullPointerException("defaultOrder");
        }
        this.defaultOrder = defaultOrder;
    }

    public ChannelBuffer getBuffer(int capacity) {
        return getBuffer(getDefaultOrder(), capacity);
    }

    public ByteOrder getDefaultOrder() {
        return defaultOrder;
    }
}
