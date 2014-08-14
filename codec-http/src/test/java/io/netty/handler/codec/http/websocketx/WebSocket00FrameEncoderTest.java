/*
 * Copyright 2014 The Netty Project
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
package io.netty.handler.codec.http.websocketx;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Assert;
import org.junit.Test;

public class WebSocket00FrameEncoderTest {

    // Test for https://github.com/netty/netty/issues/2768
    @Test
    public void testMultipleWebSocketCloseFrames() {
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocket00FrameEncoder());
        Assert.assertTrue(channel.writeOutbound(new CloseWebSocketFrame()));
        Assert.assertTrue(channel.writeOutbound(new CloseWebSocketFrame()));
        Assert.assertTrue(channel.finish());
        assertCloseWebSocketFrame(channel);
        assertCloseWebSocketFrame(channel);
        Assert.assertNull(channel.readOutbound());
    }

    private static void assertCloseWebSocketFrame(EmbeddedChannel channel) {
        ByteBuf buf = channel.readOutbound();
        Assert.assertEquals(2, buf.readableBytes());
        Assert.assertEquals((byte) 0xFF, buf.readByte());
        Assert.assertEquals((byte) 0x00, buf.readByte());
        buf.release();
    }
}
