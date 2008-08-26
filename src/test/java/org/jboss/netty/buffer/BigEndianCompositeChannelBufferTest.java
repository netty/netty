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

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 */
public class BigEndianCompositeChannelBufferTest extends AbstractChannelBufferTest {
    private List<ChannelBuffer> buffers;
    private ChannelBuffer buffer;

    @Override
    protected ChannelBuffer newBuffer(int length) {
        buffers = new ArrayList<ChannelBuffer>();
        for (int i = 0; i < length; i += 10) {
            buffers.add(ChannelBuffers.wrappedBuffer(new byte[1]));
            buffers.add(ChannelBuffers.wrappedBuffer(new byte[2]));
            buffers.add(ChannelBuffers.wrappedBuffer(new byte[3]));
            buffers.add(ChannelBuffers.wrappedBuffer(new byte[4]));
            buffers.add(ChannelBuffers.wrappedBuffer(new byte[5]));
            buffers.add(ChannelBuffers.wrappedBuffer(new byte[6]));
            buffers.add(ChannelBuffers.wrappedBuffer(new byte[7]));
            buffers.add(ChannelBuffers.wrappedBuffer(new byte[8]));
            buffers.add(ChannelBuffers.wrappedBuffer(new byte[9]));
        }

        buffer = ChannelBuffers.wrappedBuffer(buffers.toArray(new ChannelBuffer[buffers.size()]));
        buffer.writerIndex(length);
        buffer = ChannelBuffers.wrappedBuffer(buffer);
        assertEquals(length, buffer.capacity());
        assertEquals(length, buffer.readableBytes());
        assertFalse(buffer.writable());
        buffer.writerIndex(0);
        return buffer;
    }

    @Override
    protected ChannelBuffer[] components() {
        return buffers.toArray(new ChannelBuffer[buffers.size()]);
    }
}
