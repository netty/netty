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
package io.netty.handler.codec.http.websocketx;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.util.CharsetUtil;
import org.junit.Assert;
import org.junit.Test;


public class WebSocketFrameAggregatorTest {
    private final ByteBuf content1 = Unpooled.copiedBuffer("Content1", CharsetUtil.UTF_8);
    private final ByteBuf content2 = Unpooled.copiedBuffer("Content2", CharsetUtil.UTF_8);
    private final ByteBuf content3 = Unpooled.copiedBuffer("Content3", CharsetUtil.UTF_8);
    private final ByteBuf aggregatedContent = Unpooled.buffer().writeBytes(content1.duplicate())
            .writeBytes(content2.duplicate()).writeBytes(content3.duplicate());
    @Test
    public void testAggregationBinary() {
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketFrameAggregator(Integer.MAX_VALUE));
        channel.writeInbound(new BinaryWebSocketFrame(true, 1, content1.copy()));
        channel.writeInbound(new BinaryWebSocketFrame(false, 0, content1.copy()));
        channel.writeInbound(new ContinuationWebSocketFrame(false, 0, content2.copy()));
        channel.writeInbound(new PingWebSocketFrame(content1.copy()));
        channel.writeInbound(new PongWebSocketFrame(content1.copy()));
        channel.writeInbound(new ContinuationWebSocketFrame(true, 0, content3.copy()));

        Assert.assertTrue(channel.finish());

        BinaryWebSocketFrame frame = (BinaryWebSocketFrame) channel.readInbound();
        Assert.assertTrue(frame.isFinalFragment());
        Assert.assertEquals(1, frame.rsv());
        Assert.assertEquals(content1, frame.content());

        PingWebSocketFrame frame2 = (PingWebSocketFrame) channel.readInbound();
        Assert.assertTrue(frame2.isFinalFragment());
        Assert.assertEquals(0, frame2.rsv());
        Assert.assertEquals(content1, frame2.content());

        PongWebSocketFrame frame3 = (PongWebSocketFrame) channel.readInbound();
        Assert.assertTrue(frame3.isFinalFragment());
        Assert.assertEquals(0, frame3.rsv());
        Assert.assertEquals(content1, frame3.content());

        BinaryWebSocketFrame frame4 = (BinaryWebSocketFrame) channel.readInbound();
        Assert.assertTrue(frame4.isFinalFragment());
        Assert.assertEquals(0, frame4.rsv());
        Assert.assertEquals(aggregatedContent, frame4.content());

        Assert.assertNull(channel.readInbound());
    }

    @Test
    public void testAggregationText() {
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketFrameAggregator(Integer.MAX_VALUE));
        channel.writeInbound(new TextWebSocketFrame(true, 1, content1.copy()));
        channel.writeInbound(new TextWebSocketFrame(false, 0, content1.copy()));
        channel.writeInbound(new ContinuationWebSocketFrame(false, 0, content2.copy()));
        channel.writeInbound(new PingWebSocketFrame(content1.copy()));
        channel.writeInbound(new PongWebSocketFrame(content1.copy()));
        channel.writeInbound(new ContinuationWebSocketFrame(true, 0, content3.copy()));

        Assert.assertTrue(channel.finish());

        TextWebSocketFrame frame = (TextWebSocketFrame) channel.readInbound();
        Assert.assertTrue(frame.isFinalFragment());
        Assert.assertEquals(1, frame.rsv());
        Assert.assertEquals(content1.duplicate(), frame.content());

        PingWebSocketFrame frame2 = (PingWebSocketFrame) channel.readInbound();
        Assert.assertTrue(frame2.isFinalFragment());
        Assert.assertEquals(0, frame2.rsv());
        Assert.assertEquals(content1, frame2.content());

        PongWebSocketFrame frame3 = (PongWebSocketFrame) channel.readInbound();
        Assert.assertTrue(frame3.isFinalFragment());
        Assert.assertEquals(0, frame3.rsv());
        Assert.assertEquals(content1, frame3.content());

        TextWebSocketFrame frame4 = (TextWebSocketFrame) channel.readInbound();
        Assert.assertTrue(frame4.isFinalFragment());
        Assert.assertEquals(0, frame4.rsv());
        Assert.assertEquals(aggregatedContent, frame4.content());

        Assert.assertNull(channel.readInbound());
    }

    @Test
    public void textFrameTooBig() {
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketFrameAggregator(8));
        channel.writeInbound(new BinaryWebSocketFrame(true, 1, content1.copy()));
        channel.writeInbound(new BinaryWebSocketFrame(false, 0, content1.copy()));
        try {
            channel.writeInbound(new ContinuationWebSocketFrame(false, 0, content2.copy()));
            Assert.fail();
        } catch (TooLongFrameException e) {
            // expected
        }
        channel.writeInbound(new ContinuationWebSocketFrame(false, 0, content2.copy()));
        channel.writeInbound(new ContinuationWebSocketFrame(true, 0, content2.copy()));

        channel.writeInbound(new BinaryWebSocketFrame(true, 1, content1.copy()));
        channel.writeInbound(new BinaryWebSocketFrame(false, 0, content1.copy()));
        try {
            channel.writeInbound(new ContinuationWebSocketFrame(false, 0, content2.copy()));
            Assert.fail();
        } catch (TooLongFrameException e) {
            // expected
        }
        channel.writeInbound(new ContinuationWebSocketFrame(false, 0, content2.copy()));
        channel.writeInbound(new ContinuationWebSocketFrame(true, 0, content2.copy()));
    }
}
