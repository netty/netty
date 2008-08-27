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

import org.junit.Test;


/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 */
public class ChannelBufferIndexFinderTest {

    @Test
    public void testForward() {
        ChannelBuffer buf = ChannelBuffers.copiedBuffer(
                "abc\r\n\ndef\r\rghi\n\njkl\0\0mno  \t\tx", "ISO-8859-1");

        assertEquals(3, buf.indexOf(Integer.MIN_VALUE, buf.capacity(), ChannelBufferIndexFinder.CRLF));
        assertEquals(6, buf.indexOf(3, buf.capacity(), ChannelBufferIndexFinder.NOT_CRLF));
        assertEquals(9, buf.indexOf(6, buf.capacity(), ChannelBufferIndexFinder.CR));
        assertEquals(11, buf.indexOf(9, buf.capacity(), ChannelBufferIndexFinder.NOT_CR));
        assertEquals(14, buf.indexOf(11, buf.capacity(), ChannelBufferIndexFinder.LF));
        assertEquals(16, buf.indexOf(14, buf.capacity(), ChannelBufferIndexFinder.NOT_LF));
        assertEquals(19, buf.indexOf(16, buf.capacity(), ChannelBufferIndexFinder.NUL));
        assertEquals(21, buf.indexOf(19, buf.capacity(), ChannelBufferIndexFinder.NOT_NUL));
        assertEquals(24, buf.indexOf(21, buf.capacity(), ChannelBufferIndexFinder.LINEAR_WHITESPACE));
        assertEquals(28, buf.indexOf(24, buf.capacity(), ChannelBufferIndexFinder.NOT_LINEAR_WHITESPACE));
        assertEquals(-1, buf.indexOf(28, buf.capacity(), ChannelBufferIndexFinder.LINEAR_WHITESPACE));
    }

    @Test
    public void testBackward() {
        ChannelBuffer buf = ChannelBuffers.copiedBuffer(
                "abc\r\n\ndef\r\rghi\n\njkl\0\0mno  \t\tx", "ISO-8859-1");

        assertEquals(27, buf.indexOf(Integer.MAX_VALUE, 0, ChannelBufferIndexFinder.LINEAR_WHITESPACE));
        assertEquals(23, buf.indexOf(28, 0, ChannelBufferIndexFinder.NOT_LINEAR_WHITESPACE));
        assertEquals(20, buf.indexOf(24, 0, ChannelBufferIndexFinder.NUL));
        assertEquals(18, buf.indexOf(21, 0, ChannelBufferIndexFinder.NOT_NUL));
        assertEquals(15, buf.indexOf(19, 0, ChannelBufferIndexFinder.LF));
        assertEquals(13, buf.indexOf(16, 0, ChannelBufferIndexFinder.NOT_LF));
        assertEquals(10, buf.indexOf(14, 0, ChannelBufferIndexFinder.CR));
        assertEquals(8, buf.indexOf(11, 0, ChannelBufferIndexFinder.NOT_CR));
        assertEquals(5, buf.indexOf(9, 0, ChannelBufferIndexFinder.CRLF));
        assertEquals(2, buf.indexOf(6, 0, ChannelBufferIndexFinder.NOT_CRLF));
        assertEquals(-1, buf.indexOf(3, 0, ChannelBufferIndexFinder.CRLF));
    }
}
