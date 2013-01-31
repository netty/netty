/*
 * Copyright 2013 The Netty Project
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
package io.netty.handler.codec.compression;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedByteChannel;
import io.netty.util.CharsetUtil;

import org.junit.Assert;
import org.junit.Test;

public class SnappyIntegrationTest {
    private final EmbeddedByteChannel channel = new EmbeddedByteChannel(new SnappyFramedEncoder(),
                                                                        new SnappyFramedDecoder());
    
    @Test
    public void testEncoderDecoderIdentity() throws Exception {
        ByteBuf in = Unpooled.copiedBuffer(
            "Netty has been designed carefully with the experiences " +
            "earned from the implementation of a lot of protocols " +
            "such as FTP, SMTP, HTTP, and various binary and " +
            "text-based legacy protocols", CharsetUtil.US_ASCII
        );
        channel.writeOutbound(in);
        
        channel.writeInbound(channel.readOutbound());
        
        Assert.assertEquals(in, channel.readInbound());
    }
}
