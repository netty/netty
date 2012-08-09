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

import static org.junit.Assert.*;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.handler.codec.embedder.EncoderEmbedder;
import org.junit.Test;

public class HttpMessageEncoderTest {

    // Test for #493
    @Test
    public void testHttpChunkIsLast() {
        EncoderEmbedder<ChannelBuffer> encoder = new EncoderEmbedder<ChannelBuffer>(new HttpMessageEncoder() {

            @Override
            protected void encodeInitialLine(ChannelBuffer buf, HttpMessage message) throws Exception {
                // do nothing
            }
            
        });
        assertTrue(encoder.offer(HttpChunk.LAST_CHUNK));
        assertTrue(encoder.finish());
        assertNotNull(encoder.poll());
        assertNull(encoder.poll());
    }
}
