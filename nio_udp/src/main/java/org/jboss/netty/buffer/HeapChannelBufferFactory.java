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
 * A {@link ChannelBufferFactory} which merely allocates a heap buffer with
 * the specified capacity.  {@link HeapChannelBufferFactory} should perform
 * very well in most situations because it relies on the JVM garbage collector,
 * which is highly optimized for heap allocation.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
public class HeapChannelBufferFactory extends AbstractChannelBufferFactory {

    private static final HeapChannelBufferFactory INSTANCE_BE =
        new HeapChannelBufferFactory(ByteOrder.BIG_ENDIAN);

    private static final HeapChannelBufferFactory INSTANCE_LE =
        new HeapChannelBufferFactory(ByteOrder.LITTLE_ENDIAN);

    public static ChannelBufferFactory getInstance() {
        return INSTANCE_BE;
    }

    public static ChannelBufferFactory getInstance(ByteOrder endianness) {
        if (endianness == ByteOrder.BIG_ENDIAN) {
            return INSTANCE_BE;
        } else if (endianness == ByteOrder.LITTLE_ENDIAN) {
            return INSTANCE_LE;
        } else if (endianness == null) {
            throw new NullPointerException("endianness");
        } else {
            throw new IllegalStateException("Should not reach here");
        }
    }

    /**
     * Creates a new factory whose default {@link ByteOrder} is
     * {@link ByteOrder#BIG_ENDIAN}.
     */
    public HeapChannelBufferFactory() {
        super();
    }

    /**
     * Creates a new factory with the specified default {@link ByteOrder}.
     *
     * @param defaultOrder the default {@link ByteOrder} of this factory
     */
    public HeapChannelBufferFactory(ByteOrder defaultOrder) {
        super(defaultOrder);
    }

    public ChannelBuffer getBuffer(ByteOrder order, int capacity) {
        return ChannelBuffers.buffer(order, capacity);
    }
}
