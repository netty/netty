/*
 * Copyright 2014 The Netty Project
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
package io.netty.handler.codec.stomp;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class StompSubframeAggregatorTest {

    private EmbeddedChannel channel;

    @BeforeEach
    public void setup() {
        channel = new EmbeddedChannel(new StompSubframeDecoder(), new StompSubframeAggregator(100000));
    }

    @AfterEach
    public void teardown() {
        assertFalse(channel.finish());
    }

    @Test
    public void testSingleFrameDecoding() {
        ByteBuf incoming = Unpooled.buffer();
        incoming.writeBytes(StompTestConstants.CONNECT_FRAME.getBytes());
        channel.writeInbound(incoming);

        StompFrame frame = channel.readInbound();
        frame.release();

        assertNull(channel.readInbound());
    }

    @Test
    public void testSingleFrameWithBodyAndContentLength() {
        ByteBuf incoming = Unpooled.buffer();
        incoming.writeBytes(StompTestConstants.SEND_FRAME_2.getBytes());
        channel.writeInbound(incoming);

        StompFrame frame = channel.readInbound();
        assertNotNull(frame);
        assertEquals(StompCommand.SEND, frame.command());
        assertEquals("hello, queue a!!!", frame.content().toString(CharsetUtil.UTF_8));
        frame.release();

        assertNull(channel.readInbound());
    }

    @Test
    public void testSingleFrameWithBodyAndNoContentLength() {
        ByteBuf incoming = Unpooled.buffer();
        incoming.writeBytes(StompTestConstants.SEND_FRAME_4.getBytes());
        channel.writeInbound(incoming);

        StompFrame frame = channel.readInbound();
        assertNotNull(frame);
        assertEquals(StompCommand.SEND, frame.command());
        assertEquals("body", frame.content().toString(CharsetUtil.UTF_8));
        frame.release();

        assertNull(channel.readInbound());
    }

    @Test
    public void testSingleFrameWithSplitBodyAndNoContentLength() {
        for (int n = 0; n < StompTestConstants.SEND_FRAMES_3.length; ++n) {
            ByteBuf incoming = Unpooled.buffer();
            incoming.writeBytes(StompTestConstants.SEND_FRAMES_3[n].getBytes());
            channel.writeInbound(incoming);
            channel.flush();
        }

        StompFrame frame = channel.readInbound();
        assertNotNull(frame);
        assertEquals(StompCommand.SEND, frame.command());
        assertEquals("first part of body\nsecond part of body", frame.content().toString(CharsetUtil.UTF_8));
        frame.release();

        assertNull(channel.readInbound());
    }

    @Test
    public void testSingleFrameChunked() {
        EmbeddedChannel channel = new EmbeddedChannel(
                new StompSubframeDecoder(10000, 5), new StompSubframeAggregator(100000));
        ByteBuf incoming = Unpooled.buffer();
        incoming.writeBytes(StompTestConstants.SEND_FRAME_2.getBytes());
        channel.writeInbound(incoming);

        StompFrame frame = channel.readInbound();
        assertNotNull(frame);
        assertEquals(StompCommand.SEND, frame.command());
        frame.release();

        assertNull(channel.readInbound());
    }

    @Test
    public void testMultipleFramesDecoding() {
        ByteBuf incoming = Unpooled.buffer();
        incoming.writeBytes(StompTestConstants.CONNECT_FRAME.getBytes());
        incoming.writeBytes(StompTestConstants.CONNECTED_FRAME.getBytes());
        channel.writeInbound(incoming);
        channel.writeInbound(Unpooled.wrappedBuffer(StompTestConstants.SEND_FRAME_1.getBytes()));

        StompFrame frame = channel.readInbound();
        assertEquals(StompCommand.CONNECT, frame.command());
        frame.release();

        frame = channel.readInbound();
        assertEquals(StompCommand.CONNECTED, frame.command());
        frame.release();

        frame = channel.readInbound();
        assertEquals(StompCommand.SEND, frame.command());
        frame.release();

        assertNull(channel.readInbound());
    }

    @Test
    public void testTooLongFrameException() {
        final EmbeddedChannel channel = new EmbeddedChannel(new StompSubframeDecoder(),
                new StompSubframeAggregator(10));
        assertThrows(TooLongFrameException.class, new Executable() {
            @Override
            public void execute() {
                channel.writeInbound(Unpooled.wrappedBuffer(StompTestConstants.SEND_FRAME_1.getBytes()));
            }
        });
    }
}
