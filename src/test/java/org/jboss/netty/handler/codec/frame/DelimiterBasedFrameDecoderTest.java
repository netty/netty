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
package org.jboss.netty.handler.codec.frame;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.embedder.CodecEmbedderException;
import org.jboss.netty.handler.codec.embedder.DecoderEmbedder;
import org.jboss.netty.util.CharsetUtil;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 */
public class DelimiterBasedFrameDecoderTest {
    @Test
    public void testTooLongFrameRecovery() throws Exception {
        DecoderEmbedder<ChannelBuffer> embedder = new DecoderEmbedder<ChannelBuffer>(
                new DelimiterBasedFrameDecoder(1, Delimiters.nulDelimiter()));

        for (int i = 0; i < 2; i ++) {
            try {
                embedder.offer(ChannelBuffers.wrappedBuffer(new byte[] { 1, 2, 0 }));
                Assert.fail(CodecEmbedderException.class.getSimpleName() + " must be raised.");
            } catch (CodecEmbedderException e) {
                Assert.assertTrue(e.getCause() instanceof TooLongFrameException);
                // Expected
            }

            embedder.offer(ChannelBuffers.wrappedBuffer(new byte[] { 'A', 0 }));
            ChannelBuffer buf = embedder.poll();
            Assert.assertEquals("A", buf.toString(CharsetUtil.ISO_8859_1));
        }
    }
}
