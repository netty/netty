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
import io.netty.handler.codec.TooLongFrameException;
import org.junit.Assert;

public class StompAggregatorTest {

    private EmbeddedChannel channel;

    @Before
    public void setup() throws Exception {
        channel = new EmbeddedChannel(new StompDecoder(), new StompAggregator(100000));
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
        Assert.assertTrue(frame instanceof FullStompFrame);
        Assert.assertNull(channel.readInbound());
    }

    @Test
    public void testSingleFrameWithBodyAndContentLength() {
        ByteBuf incoming = Unpooled.buffer();
        incoming.writeBytes(StompTestConstants.SEND_FRAME_2.getBytes());
        channel.writeInbound(incoming);
        FullStompFrame frame = channel.readInbound();
        Assert.assertNotNull(frame);
        Assert.assertEquals(StompCommand.SEND, frame.command());
        Assert.assertEquals("hello, queue a!!!", frame.content().toString(CharsetUtil.UTF_8));
        Assert.assertNull(channel.readInbound());
    }

    @Test
    public void testSingleFrameChunked() {
        EmbeddedChannel channel = new EmbeddedChannel(new StompDecoder(10000, 5), new StompAggregator(100000));
        ByteBuf incoming = Unpooled.buffer();
        incoming.writeBytes(StompTestConstants.SEND_FRAME_2.getBytes());
        channel.writeInbound(incoming);
        FullStompFrame frame = channel.readInbound();
        Assert.assertNotNull(frame);
        Assert.assertEquals(StompCommand.SEND, frame.command());
        Assert.assertNull(channel.readInbound());
    }

    @Test
    public void testMultipleFramesDecoding() {
        ByteBuf incoming = Unpooled.buffer();
        incoming.writeBytes(StompTestConstants.CONNECT_FRAME.getBytes());
        incoming.writeBytes(StompTestConstants.CONNECTED_FRAME.getBytes());
        channel.writeInbound(incoming);
        channel.writeInbound(Unpooled.wrappedBuffer(StompTestConstants.SEND_FRAME_1.getBytes()));
        FullStompFrame frame = channel.readInbound();
        Assert.assertEquals(StompCommand.CONNECT, frame.command());
        frame = channel.readInbound();
        Assert.assertEquals(StompCommand.CONNECTED, frame.command());
        frame = channel.readInbound();
        Assert.assertEquals(StompCommand.SEND, frame.command());
        Assert.assertNull(channel.readInbound());
    }

    @Test(expected = TooLongFrameException.class)
    public void testTooLongFrameException() {
        EmbeddedChannel channel = new EmbeddedChannel(new StompDecoder(), new StompAggregator(10));
        channel.writeInbound(Unpooled.wrappedBuffer(StompTestConstants.SEND_FRAME_1.getBytes()));
    }

}
