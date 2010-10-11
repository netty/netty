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

import java.nio.ByteOrder;

import org.junit.Test;

/**
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev$, $Date$
 */
public class DynamicChannelBufferTest extends AbstractChannelBufferTest {

    private ChannelBuffer buffer;

    @Override
    protected ChannelBuffer newBuffer(int length) {
        buffer = ChannelBuffers.dynamicBuffer(length);

        assertEquals(0, buffer.readerIndex());
        assertEquals(0, buffer.writerIndex());
        assertEquals(length, buffer.capacity());

        return buffer;
    }

    @Override
    protected ChannelBuffer[] components() {
        return new ChannelBuffer[] { buffer };
    }

    @Test(expected = NullPointerException.class)
    public void shouldNotAllowNullInConstructor() {
        new DynamicChannelBuffer(null, 0);
    }

    @Test
    public void shouldNotFailOnInitialIndexUpdate() {
        new DynamicChannelBuffer(ByteOrder.BIG_ENDIAN, 10).setIndex(0, 10);
    }

    @Test
    public void shouldNotFailOnInitialIndexUpdate2() {
        new DynamicChannelBuffer(ByteOrder.BIG_ENDIAN, 10).writerIndex(10);
    }

    @Test
    public void shouldNotFailOnInitialIndexUpdate3() {
        ChannelBuffer buf = new DynamicChannelBuffer(ByteOrder.BIG_ENDIAN, 10);
        buf.writerIndex(10);
        buf.readerIndex(10);
    }
}
