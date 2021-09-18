/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
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
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;


public class WebSocketFrameAggregatorTest {
    private static final byte[] content1 = "Content1".getBytes(CharsetUtil.UTF_8);
    private static final byte[] content2 = "Content2".getBytes(CharsetUtil.UTF_8);
    private static final byte[] content3 = "Content3".getBytes(CharsetUtil.UTF_8);
    private static final byte[] aggregatedContent = new byte[content1.length + content2.length + content3.length];
    static {
        System.arraycopy(content1, 0, aggregatedContent, 0, content1.length);
        System.arraycopy(content2, 0, aggregatedContent, content1.length, content2.length);
        System.arraycopy(content3, 0, aggregatedContent, content1.length + content2.length, content3.length);
    }

    @Test
    public void testAggregationBinary() {
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketFrameAggregator(Integer.MAX_VALUE));
        channel.writeInbound(new BinaryWebSocketFrame(true, 1, Unpooled.wrappedBuffer(content1)));
        channel.writeInbound(new BinaryWebSocketFrame(false, 0, Unpooled.wrappedBuffer(content1)));
        channel.writeInbound(new ContinuationWebSocketFrame(false, 0, Unpooled.wrappedBuffer(content2)));
        channel.writeInbound(new PingWebSocketFrame(Unpooled.wrappedBuffer(content1)));
        channel.writeInbound(new PongWebSocketFrame(Unpooled.wrappedBuffer(content1)));
        channel.writeInbound(new ContinuationWebSocketFrame(true, 0, Unpooled.wrappedBuffer(content3)));

        assertTrue(channel.finish());

        BinaryWebSocketFrame frame = channel.readInbound();
        assertTrue(frame.isFinalFragment());
        assertEquals(1, frame.rsv());
        assertArrayEquals(content1, toBytes(frame.content()));

        PingWebSocketFrame frame2 = channel.readInbound();
        assertTrue(frame2.isFinalFragment());
        assertEquals(0, frame2.rsv());
        assertArrayEquals(content1, toBytes(frame2.content()));

        PongWebSocketFrame frame3 = channel.readInbound();
        assertTrue(frame3.isFinalFragment());
        assertEquals(0, frame3.rsv());
        assertArrayEquals(content1, toBytes(frame3.content()));

        BinaryWebSocketFrame frame4 = channel.readInbound();
        assertTrue(frame4.isFinalFragment());
        assertEquals(0, frame4.rsv());
        assertArrayEquals(aggregatedContent, toBytes(frame4.content()));

        assertNull(channel.readInbound());
    }

    @Test
    public void testAggregationText() {
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketFrameAggregator(Integer.MAX_VALUE));
        channel.writeInbound(new TextWebSocketFrame(true, 1, Unpooled.wrappedBuffer(content1)));
        channel.writeInbound(new TextWebSocketFrame(false, 0, Unpooled.wrappedBuffer(content1)));
        channel.writeInbound(new ContinuationWebSocketFrame(false, 0, Unpooled.wrappedBuffer(content2)));
        channel.writeInbound(new PingWebSocketFrame(Unpooled.wrappedBuffer(content1)));
        channel.writeInbound(new PongWebSocketFrame(Unpooled.wrappedBuffer(content1)));
        channel.writeInbound(new ContinuationWebSocketFrame(true, 0, Unpooled.wrappedBuffer(content3)));

        assertTrue(channel.finish());

        TextWebSocketFrame frame = channel.readInbound();
        assertTrue(frame.isFinalFragment());
        assertEquals(1, frame.rsv());
        assertArrayEquals(content1, toBytes(frame.content()));

        PingWebSocketFrame frame2 = channel.readInbound();
        assertTrue(frame2.isFinalFragment());
        assertEquals(0, frame2.rsv());
        assertArrayEquals(content1, toBytes(frame2.content()));

        PongWebSocketFrame frame3 = channel.readInbound();
        assertTrue(frame3.isFinalFragment());
        assertEquals(0, frame3.rsv());
        assertArrayEquals(content1, toBytes(frame3.content()));

        TextWebSocketFrame frame4 = channel.readInbound();
        assertTrue(frame4.isFinalFragment());
        assertEquals(0, frame4.rsv());
        assertArrayEquals(aggregatedContent, toBytes(frame4.content()));

        assertNull(channel.readInbound());
    }

    @Test
    public void textFrameTooBig() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketFrameAggregator(8));
        channel.writeInbound(new BinaryWebSocketFrame(true, 1, Unpooled.wrappedBuffer(content1)));
        channel.writeInbound(new BinaryWebSocketFrame(false, 0, Unpooled.wrappedBuffer(content1)));
        try {
            channel.writeInbound(new ContinuationWebSocketFrame(false, 0, Unpooled.wrappedBuffer(content2)));
            fail();
        } catch (TooLongFrameException e) {
            // expected
        }
        channel.writeInbound(new ContinuationWebSocketFrame(false, 0, Unpooled.wrappedBuffer(content2)));
        channel.writeInbound(new ContinuationWebSocketFrame(true, 0, Unpooled.wrappedBuffer(content2)));

        channel.writeInbound(new BinaryWebSocketFrame(true, 1, Unpooled.wrappedBuffer(content1)));
        channel.writeInbound(new BinaryWebSocketFrame(false, 0, Unpooled.wrappedBuffer(content1)));
        try {
            channel.writeInbound(new ContinuationWebSocketFrame(false, 0, Unpooled.wrappedBuffer(content2)));
            fail();
        } catch (TooLongFrameException e) {
            // expected
        }
        channel.writeInbound(new ContinuationWebSocketFrame(false, 0, Unpooled.wrappedBuffer(content2)));
        channel.writeInbound(new ContinuationWebSocketFrame(true, 0, Unpooled.wrappedBuffer(content2)));
        for (;;) {
            Object msg = channel.readInbound();
            if (msg == null) {
                break;
            }
            ReferenceCountUtil.release(msg);
        }
        channel.finish();
    }

    private static byte[] toBytes(ByteBuf buf) {
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        buf.release();
        return bytes;
    }
}
