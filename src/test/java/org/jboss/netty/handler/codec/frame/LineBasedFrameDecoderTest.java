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
package org.jboss.netty.handler.codec.frame;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.embedder.CodecEmbedderException;
import org.jboss.netty.handler.codec.embedder.DecoderEmbedder;
import org.jboss.netty.util.CharsetUtil;
import org.junit.Assert;
import org.junit.Test;

public class LineBasedFrameDecoderTest {
    @Test
    public void testDecodeWithStrip() throws Exception {
        DecoderEmbedder<ChannelBuffer> embedder = new DecoderEmbedder<ChannelBuffer>(
                new LineBasedFrameDecoder(8192, true, false));

        Assert.assertTrue(embedder.offer(ChannelBuffers.copiedBuffer("first\r\nsecond\nthird", CharsetUtil.US_ASCII)));
        Assert.assertTrue(embedder.finish());
        Assert.assertEquals("first", embedder.poll().toString(CharsetUtil.US_ASCII));
        Assert.assertEquals("second", embedder.poll().toString(CharsetUtil.US_ASCII));
        Assert.assertNull(embedder.poll());

    }
    @Test
    public void testDecodeWithoutStrip() throws Exception {
        DecoderEmbedder<ChannelBuffer> embedder = new DecoderEmbedder<ChannelBuffer>(
                new LineBasedFrameDecoder(8192, false, false));

        Assert.assertTrue(embedder.offer(ChannelBuffers.copiedBuffer("first\r\nsecond\nthird", CharsetUtil.US_ASCII)));
        Assert.assertTrue(embedder.finish());
        Assert.assertEquals("first\r\n", embedder.poll().toString(CharsetUtil.US_ASCII));
        Assert.assertEquals("second\n", embedder.poll().toString(CharsetUtil.US_ASCII));
        Assert.assertNull(embedder.poll());

    }

}
