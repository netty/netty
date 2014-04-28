
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
package org.jboss.netty.handler.codec.http.websocketx;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.embedder.CodecEmbedderException;
import org.jboss.netty.handler.codec.embedder.DecoderEmbedder;
import org.jboss.netty.util.CharsetUtil;
import org.junit.Assert;
import org.junit.Test;

public class WebSocketFrameAggregatorTest {
    private final ChannelBuffer content1 = ChannelBuffers.copiedBuffer("Content1", CharsetUtil.UTF_8);
    private final ChannelBuffer content2 = ChannelBuffers.copiedBuffer("Content2", CharsetUtil.UTF_8);
    private final ChannelBuffer content3 = ChannelBuffers.copiedBuffer("Content3", CharsetUtil.UTF_8);
    private final ChannelBuffer aggregatedContent = ChannelBuffers.wrappedBuffer(
            content1.duplicate(), content2.duplicate(), content3.duplicate());
    @Test
    public void testAggregationBinary() {
        DecoderEmbedder<WebSocketFrame> channel = new DecoderEmbedder<WebSocketFrame>(
                new WebSocketFrameAggregator(Integer.MAX_VALUE));
        channel.offer(new BinaryWebSocketFrame(true, 1, content1.copy()));
        channel.offer(new BinaryWebSocketFrame(false, 0, content1.copy()));
        channel.offer(new ContinuationWebSocketFrame(false, 0, content2.copy()));
        channel.offer(new PingWebSocketFrame(content1.copy()));
        channel.offer(new PongWebSocketFrame(content1.copy()));
        channel.offer(new ContinuationWebSocketFrame(true, 0, content3.copy()));

        Assert.assertTrue(channel.finish());

        BinaryWebSocketFrame frame = (BinaryWebSocketFrame) channel.poll();
        Assert.assertTrue(frame.isFinalFragment());
        Assert.assertEquals(1, frame.getRsv());
        Assert.assertEquals(content1, frame.getBinaryData());

        PingWebSocketFrame frame2 = (PingWebSocketFrame) channel.poll();
        Assert.assertTrue(frame2.isFinalFragment());
        Assert.assertEquals(0, frame2.getRsv());
        Assert.assertEquals(content1, frame2.getBinaryData());

        PongWebSocketFrame frame3 = (PongWebSocketFrame) channel.poll();
        Assert.assertTrue(frame3.isFinalFragment());
        Assert.assertEquals(0, frame3.getRsv());
        Assert.assertEquals(content1, frame3.getBinaryData());

        BinaryWebSocketFrame frame4 = (BinaryWebSocketFrame) channel.poll();
        Assert.assertTrue(frame4.isFinalFragment());
        Assert.assertEquals(0, frame4.getRsv());
        Assert.assertEquals(aggregatedContent, frame4.getBinaryData());

        Assert.assertNull(channel.poll());
    }

    @Test
    public void testAggregationText() {
        DecoderEmbedder<WebSocketFrame> channel = new DecoderEmbedder<WebSocketFrame>(
                new WebSocketFrameAggregator(Integer.MAX_VALUE));
        channel.offer(new TextWebSocketFrame(true, 1, content1.copy()));
        channel.offer(new TextWebSocketFrame(false, 0, content1.copy()));
        channel.offer(new ContinuationWebSocketFrame(false, 0, content2.copy()));
        channel.offer(new PingWebSocketFrame(content1.copy()));
        channel.offer(new PongWebSocketFrame(content1.copy()));
        channel.offer(new ContinuationWebSocketFrame(true, 0, content3.copy()));

        Assert.assertTrue(channel.finish());

        TextWebSocketFrame frame = (TextWebSocketFrame) channel.poll();
        Assert.assertTrue(frame.isFinalFragment());
        Assert.assertEquals(1, frame.getRsv());
        Assert.assertEquals(content1, frame.getBinaryData());

        PingWebSocketFrame frame2 = (PingWebSocketFrame) channel.poll();
        Assert.assertTrue(frame2.isFinalFragment());
        Assert.assertEquals(0, frame2.getRsv());
        Assert.assertEquals(content1, frame2.getBinaryData());

        PongWebSocketFrame frame3 = (PongWebSocketFrame) channel.poll();
        Assert.assertTrue(frame3.isFinalFragment());
        Assert.assertEquals(0, frame3.getRsv());
        Assert.assertEquals(content1, frame3.getBinaryData());

        TextWebSocketFrame frame4 = (TextWebSocketFrame) channel.poll();
        Assert.assertTrue(frame4.isFinalFragment());
        Assert.assertEquals(0, frame4.getRsv());
        Assert.assertEquals(aggregatedContent, frame4.getBinaryData());

        Assert.assertNull(channel.poll());
    }

    @Test
    public void textFrameTooBig() throws Exception {
        DecoderEmbedder<WebSocketFrame> channel = new DecoderEmbedder<WebSocketFrame>(
                new WebSocketFrameAggregator(8));
        channel.offer(new BinaryWebSocketFrame(true, 1, content1.copy()));
        channel.offer(new BinaryWebSocketFrame(false, 0, content1.copy()));
        try {
            channel.offer(new ContinuationWebSocketFrame(false, 0, content2.copy()));
            Assert.fail();
        } catch (CodecEmbedderException e) {
            // expected
        }
        channel.offer(new ContinuationWebSocketFrame(false, 0, content2.copy()));
        channel.offer(new ContinuationWebSocketFrame(true, 0, content2.copy()));

        channel.offer(new BinaryWebSocketFrame(true, 1, content1.copy()));
        channel.offer(new BinaryWebSocketFrame(false, 0, content1.copy()));
        try {
            channel.offer(new ContinuationWebSocketFrame(false, 0, content2.copy()));
            Assert.fail();
        } catch (CodecEmbedderException e) {
            // expected
        }
        channel.offer(new ContinuationWebSocketFrame(false, 0, content2.copy()));
        channel.offer(new ContinuationWebSocketFrame(true, 0, content2.copy()));
        for (;;) {
            Object msg = channel.poll();
            if (msg == null) {
                break;
            }
        }
        channel.finish();
    }
}