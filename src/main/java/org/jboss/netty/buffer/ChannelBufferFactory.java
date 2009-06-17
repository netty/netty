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
 * A factory that creates or pools {@link ChannelBuffer}s.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
public interface ChannelBufferFactory {

    /**
     * Returns a {@link ChannelBuffer} with the specified {@code capacity}.
     * This method is identical to {@code getBuffer(getDefaultOrder(), capacity)}.
     *
     * @param capacity the capacity of the returned {@link ChannelBuffer}
     * @return a {@link ChannelBuffer} with the specified {@code capacity},
     *         whose {@code readerIndex} and {@code writerIndex} are {@code 0}
     */
    ChannelBuffer getBuffer(int capacity);

    /**
     * Returns a {@link ChannelBuffer} with the specified {@code endianness}
     * and {@code capacity}.
     *
     * @param endianness the endianness of the returned {@link ChannelBuffer}
     * @param capacity   the capacity of the returned {@link ChannelBuffer}
     * @return a {@link ChannelBuffer} with the specified {@code endianness} and
     *         {@code capacity}, whose {@code readerIndex} and {@code writerIndex}
     *         are {@code 0}
     */
    ChannelBuffer getBuffer(ByteOrder endianness, int capacity);

    /**
     * Returns the default endianness of the {@link ChannelBuffer} which is
     * returned by {@link #getBuffer(int)}.
     *
     * @return the default endianness of the {@link ChannelBuffer} which is
     *         returned by {@link #getBuffer(int)}
     */
    ByteOrder getDefaultOrder();
}
