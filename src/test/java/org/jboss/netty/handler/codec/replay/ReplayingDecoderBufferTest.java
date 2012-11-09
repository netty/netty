/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.handler.codec.replay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.util.CharsetUtil;
import org.junit.Test;

public class ReplayingDecoderBufferTest {
    
    /**
     * See https://github.com/netty/netty/issues/445
     */
    @Test
    public void testGetUnsignedByte() {
        ReplayingDecoderBuffer buffer = new ReplayingDecoderBuffer(new ReplayDecoderImpl());

        boolean error;
        int i = 0;
        try {
            for (;;) {
                buffer.getUnsignedByte(i);
                i++;
            }
        } catch (ReplayError e) {
            error = true;
        }

        assertTrue(error);
        assertEquals(10, i);
    }

    /**
     * See https://github.com/netty/netty/issues/445
     */
    @Test
    public void testGetByte() {
        ReplayingDecoderBuffer buffer = new ReplayingDecoderBuffer(new ReplayDecoderImpl());

        boolean error;
        int i = 0;
        try {
            for (;;) {
                buffer.getByte(i);
                i++;
            }
        } catch (ReplayError e) {
            error = true;
        }

        assertTrue(error);
        assertEquals(10, i);
    }
    
    private static class ReplayDecoderImpl extends ReplayingDecoder<VoidEnum> {
        private final ChannelBuffer internal = ChannelBuffers.copiedBuffer("TestBuffer", CharsetUtil.ISO_8859_1);
        
        @Override
        protected ChannelBuffer internalBuffer() {
            return internal;
        }

        @Override
        protected Object decode(ChannelHandlerContext ctx, Channel channel, ChannelBuffer buffer, VoidEnum state)
                throws Exception {
            throw new UnsupportedOperationException();
        }
    }
}
