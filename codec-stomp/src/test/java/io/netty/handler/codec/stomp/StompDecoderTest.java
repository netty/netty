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
package io.netty.handler.codec.stomp;

import io.netty.util.CharsetUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Assert;

public class StompDecoderTest {

    private EmbeddedChannel channel;

    @Before
    public void setup() throws Exception {
        channel = new EmbeddedChannel(new StompDecoder());
    }

    @After
    public void teardown() throws Exception {
        Assert.assertFalse(channel.finish());
    }

    @Test
    public void testSingleFrameDecoding() {
        ByteBuf incoming = Unpooled.buffer();
        incoming.writeBytes(StompTestConstants.CONNECT_FRAME.getBytes());
        channel.writeInbound(incoming);
        StompFrame frame = channel.readInbound();
        Assert.assertNotNull(frame);
        Assert.assertEquals(StompCommand.CONNECT, frame.command());
        StompContent content = channel.readInbound();
        Assert.assertTrue(content == LastStompContent.EMPTY_LAST_CONTENT);
        Object o = channel.readInbound();
        Assert.assertNull(o);
    }

    @Test
    public void testSingleFrameWithBodyAndContentLength() {
        ByteBuf incoming = Unpooled.buffer();
        incoming.writeBytes(StompTestConstants.SEND_FRAME_2.getBytes());
        channel.writeInbound(incoming);
        StompFrame frame = channel.readInbound();
        Assert.assertNotNull(frame);
        Assert.assertEquals(StompCommand.SEND, frame.command());
        StompContent content = channel.readInbound();
        Assert.assertTrue(content instanceof LastStompContent);
        String s = content.content().toString(CharsetUtil.UTF_8);
        Assert.assertEquals("hello, queue a!!!", s);
        Assert.assertNull(channel.readInbound());
    }

    @Test
    public void testSingleFrameWithBodyWithoutContentLength() {
        ByteBuf incoming = Unpooled.buffer();
        incoming.writeBytes(StompTestConstants.SEND_FRAME_1.getBytes());
        channel.writeInbound(incoming);
        StompFrame frame = (StompFrame) channel.readInbound();
        Assert.assertNotNull(frame);
        Assert.assertEquals(StompCommand.SEND, frame.command());
        StompContent content = (StompContent) channel.readInbound();
        Assert.assertTrue(content instanceof LastStompContent);
        String s = content.content().toString(CharsetUtil.UTF_8);
        Assert.assertEquals("hello, queue a!", s);
        Assert.assertNull(channel.readInbound());
    }

    @Test
    public void testSingleFrameChunked() {
        EmbeddedChannel channel = new EmbeddedChannel(new StompDecoder(10000, 5));

        ByteBuf incoming = Unpooled.buffer();
        incoming.writeBytes(StompTestConstants.SEND_FRAME_2.getBytes());
        channel.writeInbound(incoming);
        StompFrame frame = channel.readInbound();
        Assert.assertNotNull(frame);
        Assert.assertEquals(StompCommand.SEND, frame.command());
        StompContent content = channel.readInbound();
        String s = content.content().toString(CharsetUtil.UTF_8);
        Assert.assertEquals("hello", s);

        content = channel.readInbound();
        s = content.content().toString(CharsetUtil.UTF_8);
        Assert.assertEquals(", que", s);

        content = channel.readInbound();
        s = content.content().toString(CharsetUtil.UTF_8);
        Assert.assertEquals("ue a!", s);

        content = channel.readInbound();
        s = content.content().toString(CharsetUtil.UTF_8);
        Assert.assertEquals("!!", s);

        Assert.assertNull(channel.readInbound());
    }

    @Test
    public void testMultipleFramesDecoding() {
        ByteBuf incoming = Unpooled.buffer();
        incoming.writeBytes(StompTestConstants.CONNECT_FRAME.getBytes());
        incoming.writeBytes(StompTestConstants.CONNECTED_FRAME.getBytes());
        channel.writeInbound(incoming);

        StompFrame frame = channel.readInbound();
        Assert.assertNotNull(frame);
        Assert.assertEquals(StompCommand.CONNECT, frame.command());
        StompContent content = channel.readInbound();
        Assert.assertTrue(content == LastStompContent.EMPTY_LAST_CONTENT);

        StompFrame frame2 = channel.readInbound();
        Assert.assertNotNull(frame2);
        Assert.assertEquals(StompCommand.CONNECTED, frame2.command());
        StompContent content2 = channel.readInbound();
        Assert.assertTrue(content2 == LastStompContent.EMPTY_LAST_CONTENT);
        Assert.assertNull(channel.readInbound());
    }
}
