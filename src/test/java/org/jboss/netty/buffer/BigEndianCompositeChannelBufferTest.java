/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
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
            buffers.add(ChannelBuffers.EMPTY_BUFFER);
            buffers.add(ChannelBuffers.wrappedBuffer(new byte[1]));
            buffers.add(ChannelBuffers.EMPTY_BUFFER);
            buffers.add(ChannelBuffers.wrappedBuffer(new byte[2]));
            buffers.add(ChannelBuffers.EMPTY_BUFFER);
            buffers.add(ChannelBuffers.wrappedBuffer(new byte[3]));
            buffers.add(ChannelBuffers.EMPTY_BUFFER);
            buffers.add(ChannelBuffers.wrappedBuffer(new byte[4]));
            buffers.add(ChannelBuffers.EMPTY_BUFFER);
            buffers.add(ChannelBuffers.wrappedBuffer(new byte[5]));
            buffers.add(ChannelBuffers.EMPTY_BUFFER);
            buffers.add(ChannelBuffers.wrappedBuffer(new byte[6]));
            buffers.add(ChannelBuffers.EMPTY_BUFFER);
            buffers.add(ChannelBuffers.wrappedBuffer(new byte[7]));
            buffers.add(ChannelBuffers.EMPTY_BUFFER);
            buffers.add(ChannelBuffers.wrappedBuffer(new byte[8]));
            buffers.add(ChannelBuffers.EMPTY_BUFFER);
            buffers.add(ChannelBuffers.wrappedBuffer(new byte[9]));
            buffers.add(ChannelBuffers.EMPTY_BUFFER);
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
