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
package io.netty.handler.codec;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Test;

import java.util.List;


public class MessageToMessageEncoderTest {

    /**
     * Test-case for https://github.com/netty/netty/issues/1656
     */
    @Test(expected = EncoderException.class)
    public void testException() {
        EmbeddedChannel channel = new EmbeddedChannel(new MessageToMessageEncoder<Object>() {
            @Override
            protected void encode(ChannelHandlerContext ctx, Object msg, List<Object> out) throws Exception {
                throw new Exception();
            }
        });
        channel.writeOutbound(new Object());
    }
}
