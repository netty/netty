/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */package org.jboss.netty.channel.handler.stream;

import java.io.ByteArrayInputStream;

import junit.framework.Assert;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.handler.codec.embedder.EncoderEmbedder;
import org.jboss.netty.handler.stream.ChunkedStream;
import org.jboss.netty.handler.stream.ChunkedWriteHandler;
import org.junit.Test;

public class ChunkedWriteHandlerTest {
    private static final byte[] BYTES = new byte[1024 * 64];
    static {
        for (int i = 0; i < BYTES.length; i++) {
            BYTES[i] = (byte) i;
        }
    }

    // See #310
    @Test
    public void testChunkedStream() {
        EncoderEmbedder<ChannelBuffer> embedder = new EncoderEmbedder<ChannelBuffer>(new ChunkedWriteHandler());
        embedder.offer(new ChunkedStream(new ByteArrayInputStream(BYTES)));

        Assert.assertTrue(embedder.finish());

        int i = 0;
        for (;;) {
            ChannelBuffer buffer = embedder.poll();
            if (buffer == null) {
                break;
            }
            while (buffer.readable()) {
                Assert.assertEquals(BYTES[i++], buffer.readByte());
            }
        }

        Assert.assertEquals(BYTES.length, i);
    }
}
