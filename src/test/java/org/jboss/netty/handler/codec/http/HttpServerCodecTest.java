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
package org.jboss.netty.handler.codec.http;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.embedder.DecoderEmbedder;
import org.jboss.netty.util.CharsetUtil;
import org.junit.Assert;
import org.junit.Test;

public class HttpServerCodecTest {
    
    /**
     * Testcase for https://github.com/netty/netty/issues/433
     */
    @Test
    public void testUnfinishedChunkedHttpRequestIsLastFlag() throws Exception {

        int maxChunkSize = 2000;
        HttpServerCodec httpServerCodec = new HttpServerCodec(1000, 1000, maxChunkSize);
        DecoderEmbedder<Object> decoderEmbedder = new DecoderEmbedder<Object>(httpServerCodec);

        int totalContentLength = maxChunkSize * 5;
        decoderEmbedder.offer(ChannelBuffers.copiedBuffer("PUT /test HTTP/1.1\r\n" +
                "Content-Length: " + totalContentLength + "\r\n" +
                "\r\n", CharsetUtil.UTF_8));

        int offeredContentLength = (int) (maxChunkSize * 2.5);
        decoderEmbedder.offer(prepareDataChunk(offeredContentLength));
        decoderEmbedder.finish();

        HttpMessage httpMessage = (HttpMessage) decoderEmbedder.poll();
        Assert.assertTrue(httpMessage.isChunked());

        Assert.assertNotNull(decoderEmbedder.peek());

        int totalBytesPolled = 0;
        while (decoderEmbedder.peek() != null) {
            HttpChunk httpChunk = (HttpChunk) decoderEmbedder.poll();
            totalBytesPolled += httpChunk.getContent().readableBytes();
            Assert.assertFalse(httpChunk.isLast());
        }

        Assert.assertEquals(offeredContentLength, totalBytesPolled);
    }

    private static ChannelBuffer prepareDataChunk(int size) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < size; ++i) {
            sb.append('a');
        }
        return ChannelBuffers.copiedBuffer(sb.toString(), CharsetUtil.UTF_8);
    }
}
