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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.netty.handler.codec.stomp.StompTestConstants.*;
import static io.netty.util.CharsetUtil.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StompSubframeDecoderTest {

    private EmbeddedChannel channel;

    @BeforeEach
    public void setup() throws Exception {
        channel = new EmbeddedChannel(new StompSubframeDecoder());
    }

    @AfterEach
    public void teardown() throws Exception {
        assertFalse(channel.finish());
    }

    @Test
    public void testSingleFrameDecoding() {
        ByteBuf incoming = Unpooled.buffer();
        incoming.writeBytes(StompTestConstants.CONNECT_FRAME.getBytes());
        channel.writeInbound(incoming);

        StompHeadersSubframe frame = channel.readInbound();
        assertNotNull(frame);
        assertEquals(StompCommand.CONNECT, frame.command());

        StompContentSubframe content = channel.readInbound();
        assertSame(LastStompContentSubframe.EMPTY_LAST_CONTENT, content);
        content.release();

        Object o = channel.readInbound();
        assertNull(o);
    }

    @Test
    public void testSingleFrameWithBodyAndContentLength() {
        ByteBuf incoming = Unpooled.buffer();
        incoming.writeBytes(StompTestConstants.SEND_FRAME_2.getBytes());
        channel.writeInbound(incoming);

        StompHeadersSubframe frame = channel.readInbound();
        assertNotNull(frame);
        assertEquals(StompCommand.SEND, frame.command());

        StompContentSubframe content = channel.readInbound();
        assertTrue(content instanceof LastStompContentSubframe);
        String s = content.content().toString(UTF_8);
        assertEquals("hello, queue a!!!", s);
        content.release();

        assertNull(channel.readInbound());
    }

    @Test
    public void testSingleFrameWithBodyWithoutContentLength() {
        ByteBuf incoming = Unpooled.buffer();
        incoming.writeBytes(StompTestConstants.SEND_FRAME_1.getBytes());
        channel.writeInbound(incoming);

        StompHeadersSubframe frame = channel.readInbound();
        assertNotNull(frame);
        assertEquals(StompCommand.SEND, frame.command());

        StompContentSubframe content = channel.readInbound();
        assertTrue(content instanceof LastStompContentSubframe);
        String s = content.content().toString(UTF_8);
        assertEquals("hello, queue a!", s);
        content.release();

        assertNull(channel.readInbound());
    }

    @Test
    public void testSingleFrameChunked() {
        EmbeddedChannel channel = new EmbeddedChannel(new StompSubframeDecoder(10000, 5));

        ByteBuf incoming = Unpooled.buffer();
        incoming.writeBytes(StompTestConstants.SEND_FRAME_2.getBytes());
        channel.writeInbound(incoming);

        StompHeadersSubframe frame = channel.readInbound();
        assertNotNull(frame);
        assertEquals(StompCommand.SEND, frame.command());

        StompContentSubframe content = channel.readInbound();
        String s = content.content().toString(UTF_8);
        assertEquals("hello", s);
        content.release();

        content = channel.readInbound();
        s = content.content().toString(UTF_8);
        assertEquals(", que", s);
        content.release();

        content = channel.readInbound();
        s = content.content().toString(UTF_8);
        assertEquals("ue a!", s);
        content.release();

        content = channel.readInbound();
        s = content.content().toString(UTF_8);
        assertEquals("!!", s);
        content.release();

        assertNull(channel.readInbound());
    }

    @Test
    public void testMultipleFramesDecoding() {
        ByteBuf incoming = Unpooled.buffer();
        incoming.writeBytes(StompTestConstants.CONNECT_FRAME.getBytes());
        incoming.writeBytes(StompTestConstants.CONNECTED_FRAME.getBytes());
        channel.writeInbound(incoming);

        StompHeadersSubframe frame = channel.readInbound();
        assertNotNull(frame);
        assertEquals(StompCommand.CONNECT, frame.command());

        StompContentSubframe content = channel.readInbound();
        assertSame(LastStompContentSubframe.EMPTY_LAST_CONTENT, content);
        content.release();

        StompHeadersSubframe frame2 = channel.readInbound();
        assertNotNull(frame2);
        assertEquals(StompCommand.CONNECTED, frame2.command());

        StompContentSubframe content2 = channel.readInbound();
        assertSame(LastStompContentSubframe.EMPTY_LAST_CONTENT, content2);
        content2.release();

        assertNull(channel.readInbound());
    }

    @Test
    public void testValidateHeadersDecodingDisabled() {
        ByteBuf invalidIncoming = Unpooled.copiedBuffer(FRAME_WITH_INVALID_HEADER.getBytes(UTF_8));
        assertTrue(channel.writeInbound(invalidIncoming));

        StompHeadersSubframe frame = channel.readInbound();
        assertNotNull(frame);
        assertEquals(StompCommand.SEND, frame.command());
        assertTrue(frame.headers().contains("destination"));
        assertTrue(frame.headers().contains("content-type"));
        assertFalse(frame.headers().contains("current-time"));

        StompContentSubframe content = channel.readInbound();
        String s = content.content().toString(UTF_8);
        assertEquals("some body", s);
        content.release();
    }

    @Test
    public void testValidateHeadersDecodingEnabled() {
        channel = new EmbeddedChannel(new StompSubframeDecoder(true));

        ByteBuf invalidIncoming = Unpooled.wrappedBuffer(FRAME_WITH_INVALID_HEADER.getBytes(UTF_8));
        assertTrue(channel.writeInbound(invalidIncoming));

        StompHeadersSubframe frame = channel.readInbound();
        assertNotNull(frame);
        assertTrue(frame.decoderResult().isFailure());
        assertEquals("a header value or name contains a prohibited character ':', current-time:2000-01-01T00:00:00",
                frame.decoderResult().cause().getMessage());
    }

    @Test
    public void testNotValidFrameWithEmptyHeaderName() {
        channel = new EmbeddedChannel(new StompSubframeDecoder(true));

        ByteBuf invalidIncoming = Unpooled.wrappedBuffer(FRAME_WITH_EMPTY_HEADER_NAME.getBytes(UTF_8));
        assertTrue(channel.writeInbound(invalidIncoming));

        StompHeadersSubframe frame = channel.readInbound();
        assertNotNull(frame);
        assertTrue(frame.decoderResult().isFailure());
        assertEquals("received an invalid header line ':header-value'",
                     frame.decoderResult().cause().getMessage());
    }

    @Test
    public void testUtf8FrameDecoding() {
        channel = new EmbeddedChannel(new StompSubframeDecoder(true));

        ByteBuf incoming = Unpooled.wrappedBuffer(SEND_FRAME_UTF8.getBytes(UTF_8));
        assertTrue(channel.writeInbound(incoming));

        StompHeadersSubframe headersSubFrame = channel.readInbound();
        assertNotNull(headersSubFrame);
        assertFalse(headersSubFrame.decoderResult().isFailure());
        assertEquals("/queue/№11±♛нетти♕", headersSubFrame.headers().getAsString("destination"));
        assertTrue(headersSubFrame.headers().contains("content-type"));

        StompContentSubframe contentSubFrame = channel.readInbound();
        assertNotNull(contentSubFrame);
        assertEquals("body", contentSubFrame.content().toString(UTF_8));
        assertTrue(contentSubFrame.release());
    }

    @Test
    void testFrameWithContentLengthAndWithoutNullEnding() {
        channel = new EmbeddedChannel(new StompSubframeDecoder(true));

        ByteBuf incoming = Unpooled.wrappedBuffer(FRAME_WITHOUT_NULL_ENDING.getBytes(UTF_8));
        assertTrue(channel.writeInbound(incoming));

        StompHeadersSubframe headersFrame = channel.readInbound();
        assertNotNull(headersFrame);
        assertFalse(headersFrame.decoderResult().isFailure());

        StompContentSubframe lastContentFrame = channel.readInbound();
        assertNotNull(lastContentFrame);
        assertTrue(lastContentFrame.decoderResult().isFailure());
        assertEquals("unexpected byte in buffer 1 while expecting NULL byte",
                     lastContentFrame.decoderResult().cause().getMessage());
    }
}
