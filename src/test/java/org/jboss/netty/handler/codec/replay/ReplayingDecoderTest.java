/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.netty.handler.codec.replay;

import static org.junit.Assert.*;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferIndexFinder;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.embedder.DecoderEmbedder;
import org.junit.Test;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
public class ReplayingDecoderTest {

    @Test
    public void testLineProtocol() {
        DecoderEmbedder<ChannelBuffer> e = new DecoderEmbedder<ChannelBuffer>(
                new LineDecoder());

        // Ordinary input
        e.offer(ChannelBuffers.wrappedBuffer(new byte[] { 'A' }));
        assertNull(e.poll());
        e.offer(ChannelBuffers.wrappedBuffer(new byte[] { 'B' }));
        assertNull(e.poll());
        e.offer(ChannelBuffers.wrappedBuffer(new byte[] { 'C' }));
        assertNull(e.poll());
        e.offer(ChannelBuffers.wrappedBuffer(new byte[] { '\n' }));
        assertEquals(ChannelBuffers.wrappedBuffer(new byte[] { 'A', 'B', 'C' }), e.poll());

        // Truncated input
        e.offer(ChannelBuffers.wrappedBuffer(new byte[] { 'A' }));
        assertNull(e.poll());
        e.finish();
        assertNull(e.poll());
    }

    private static final class LineDecoder extends ReplayingDecoder<VoidEnum> {

        LineDecoder() {
            super();
        }

        @Override
        protected Object decode(ChannelHandlerContext ctx, Channel channel,
                ChannelBuffer buffer, VoidEnum state) throws Exception {
            ChannelBuffer msg = buffer.readBytes(ChannelBufferIndexFinder.LF);
            buffer.skipBytes(1);
            return msg;
        }
    }
}
