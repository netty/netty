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

import org.jboss.netty.util.CharsetUtil;
import org.junit.Test;


/**
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 *
 */
public class ChannelBufferIndexFinderTest {

    @Test
    public void testForward() {
        ChannelBuffer buf = ChannelBuffers.copiedBuffer(
                "abc\r\n\ndef\r\rghi\n\njkl\0\0mno  \t\tx",
                CharsetUtil.ISO_8859_1);

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
                "abc\r\n\ndef\r\rghi\n\njkl\0\0mno  \t\tx",
                CharsetUtil.ISO_8859_1);

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
