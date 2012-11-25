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
package io.netty.handler.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedByteChannel;
import io.netty.util.CharsetUtil;
import org.junit.Assert;
import org.junit.Test;

public class LineBasedFrameDecoderTest {
    @Test
    public void testDecodeWithStrip() throws Exception {
        EmbeddedByteChannel ch = new EmbeddedByteChannel(new LineBasedFrameDecoder(8192, true, false));

        ch.writeInbound(Unpooled.copiedBuffer("first\r\nsecond\nthird", CharsetUtil.US_ASCII));
        Assert.assertEquals("first", ((ByteBuf)ch.readInbound()).toString(CharsetUtil.US_ASCII));
        Assert.assertEquals("second", ((ByteBuf)ch.readInbound()).toString(CharsetUtil.US_ASCII));
        Assert.assertNull(ch.readInbound());

    }
    @Test
    public void testDecodeWithoutStrip() throws Exception {
        EmbeddedByteChannel ch = new EmbeddedByteChannel(new LineBasedFrameDecoder(8192, false, false));

        ch.writeInbound(Unpooled.copiedBuffer("first\r\nsecond\nthird", CharsetUtil.US_ASCII));
        Assert.assertEquals("first\r\n", ((ByteBuf)ch.readInbound()).toString(CharsetUtil.US_ASCII));
        Assert.assertEquals("second\n", ((ByteBuf)ch.readInbound()).toString(CharsetUtil.US_ASCII));
        Assert.assertNull(ch.readInbound());
    }
}
