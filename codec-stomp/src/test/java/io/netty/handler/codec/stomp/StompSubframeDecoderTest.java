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

    @Test
    void testUnescapeHeaders() {
        channel = new EmbeddedChannel(new StompSubframeDecoder(true));

        ByteBuf incoming = Unpooled.wrappedBuffer(StompTestConstants.ESCAPED_MESSAGE_FRAME.getBytes(UTF_8));
        assertTrue(channel.writeInbound(incoming));

        StompHeadersSubframe headersSubFrame = channel.readInbound();
        assertNotNull(headersSubFrame);
        assertFalse(headersSubFrame.decoderResult().isFailure());
        assertEquals(6, headersSubFrame.headers().size());
        assertEquals("/queue/a:", headersSubFrame.headers().get(StompHeaders.DESTINATION));
        assertEquals("header\\\r\n:Value", headersSubFrame.headers().get("header\\\r\n:Name"));
        assertEquals("header_\\_\r_\n_:_Value", headersSubFrame.headers().get("header_\\_\r_\n_:_Name"));
        assertEquals(":headerValue", headersSubFrame.headers().get("headerName:"));

        StompContentSubframe content = channel.readInbound();
        assertSame(LastStompContentSubframe.EMPTY_LAST_CONTENT, content);
        content.release();

        Object obj = channel.readInbound();
        assertNull(obj);
    }

    @Test
    void testNotUnescapeHeadersForConnectCommand() {
        String expectedStompFrame = "CONNECT\n"
                + "headerName-\\\\:headerValue-\\\\\n"
                + "\n" + '\0';
        channel = new EmbeddedChannel(new StompSubframeDecoder(true));

        ByteBuf incoming = Unpooled.wrappedBuffer(expectedStompFrame.getBytes(UTF_8));
        assertTrue(channel.writeInbound(incoming));

        StompHeadersSubframe headersSubFrame = channel.readInbound();
        assertNotNull(headersSubFrame);
        assertFalse(headersSubFrame.decoderResult().isFailure());
        assertEquals(1, headersSubFrame.headers().size());
        assertEquals("headerValue-\\\\", headersSubFrame.headers().get("headerName-\\\\"));

        StompContentSubframe content = channel.readInbound();
        assertSame(LastStompContentSubframe.EMPTY_LAST_CONTENT, content);
        content.release();

        Object obj = channel.readInbound();
        assertNull(obj);
    }

    @Test
    void testNotUnescapeHeadersForConnectedCommand() {
        String expectedStompFrame = "CONNECTED\n"
                + "headerName-\\\\:headerValue-\\\\\n"
                + "\n" + '\0';
        channel = new EmbeddedChannel(new StompSubframeDecoder(true));

        ByteBuf incoming = Unpooled.wrappedBuffer(expectedStompFrame.getBytes(UTF_8));
        assertTrue(channel.writeInbound(incoming));

        StompHeadersSubframe headersSubFrame = channel.readInbound();
        assertNotNull(headersSubFrame);
        assertFalse(headersSubFrame.decoderResult().isFailure());
        assertEquals(1, headersSubFrame.headers().size());
        assertEquals("headerValue-\\\\", headersSubFrame.headers().get("headerName-\\\\"));

        StompContentSubframe content = channel.readInbound();
        assertSame(LastStompContentSubframe.EMPTY_LAST_CONTENT, content);
        content.release();

        Object obj = channel.readInbound();
        assertNull(obj);
    }

    @Test
    public void testHeartbeatOnlyDoesNotThrowException() {
        // STOMP heartbeat is just a LF byte - should not cause IndexOutOfBoundsException
        ByteBuf heartbeat = Unpooled.buffer();
        heartbeat.writeByte('\n');
        channel.writeInbound(heartbeat);

        // Heartbeat should be consumed silently, no output produced
        Object result = channel.readInbound();
        assertNull(result);
    }

    @Test
    public void testMultipleHeartbeatsDoNotThrowException() {
        // Multiple consecutive heartbeats
        ByteBuf heartbeats = Unpooled.buffer();
        heartbeats.writeByte('\n');
        heartbeats.writeByte('\n');
        heartbeats.writeByte('\n');
        channel.writeInbound(heartbeats);

        Object result = channel.readInbound();
        assertNull(result);
    }

    @Test
    public void testCarriageReturnLineFeedHeartbeat() {
        // CR+LF heartbeat
        ByteBuf heartbeat = Unpooled.buffer();
        heartbeat.writeByte('\r');
        heartbeat.writeByte('\n');
        channel.writeInbound(heartbeat);

        Object result = channel.readInbound();
        assertNull(result);
    }

    @Test
    public void testHeartbeatFollowedByFrame() {
        // Heartbeat bytes followed by a real STOMP frame should decode correctly
        ByteBuf incoming = Unpooled.buffer();
        incoming.writeByte('\n');
        incoming.writeByte('\n');
        incoming.writeBytes(StompTestConstants.CONNECT_FRAME.getBytes());
        channel.writeInbound(incoming);

        StompHeadersSubframe frame = channel.readInbound();
        assertNotNull(frame);
        assertEquals(StompCommand.CONNECT, frame.command());

        StompContentSubframe content = channel.readInbound();
        assertSame(LastStompContentSubframe.EMPTY_LAST_CONTENT, content);
        content.release();

        assertNull(channel.readInbound());
    }

    @Test
    public void testHeartbeatBetweenFrames() {
        // Heartbeat bytes between two STOMP frames
        ByteBuf incoming = Unpooled.buffer();
        incoming.writeBytes(StompTestConstants.CONNECT_FRAME.getBytes());
        incoming.writeByte('\n');
        incoming.writeByte('\n');
        incoming.writeBytes(StompTestConstants.CONNECTED_FRAME.getBytes());
        channel.writeInbound(incoming);

        StompHeadersSubframe frame1 = channel.readInbound();
        assertNotNull(frame1);
        assertEquals(StompCommand.CONNECT, frame1.command());

        StompContentSubframe content1 = channel.readInbound();
        assertSame(LastStompContentSubframe.EMPTY_LAST_CONTENT, content1);
        content1.release();

        StompHeadersSubframe frame2 = channel.readInbound();
        assertNotNull(frame2);
        assertEquals(StompCommand.CONNECTED, frame2.command());

        StompContentSubframe content2 = channel.readInbound();
        assertSame(LastStompContentSubframe.EMPTY_LAST_CONTENT, content2);
        content2.release();

        assertNull(channel.readInbound());
    }

    @Test
    public void testHeartbeatSentSeparatelyThenFrame() {
        // Simulate heartbeat arriving in a separate TCP segment, then a frame later
        ByteBuf heartbeat = Unpooled.buffer();
        heartbeat.writeByte('\n');
        channel.writeInbound(heartbeat);

        // No output from heartbeat
        assertNull(channel.readInbound());

        // Now send a real frame
        ByteBuf frame = Unpooled.buffer();
        frame.writeBytes(StompTestConstants.CONNECT_FRAME.getBytes());
        channel.writeInbound(frame);

        StompHeadersSubframe headersSubframe = channel.readInbound();
        assertNotNull(headersSubframe);
        assertEquals(StompCommand.CONNECT, headersSubframe.command());

        StompContentSubframe content = channel.readInbound();
        assertSame(LastStompContentSubframe.EMPTY_LAST_CONTENT, content);
        content.release();

        assertNull(channel.readInbound());
    }

    @Test
    void testInvalidEscapeHeadersSequence() {
        channel = new EmbeddedChannel(new StompSubframeDecoder(true));

        ByteBuf incoming = Unpooled.wrappedBuffer(INVALID_ESCAPED_MESSAGE_FRAME.getBytes(UTF_8));
        assertTrue(channel.writeInbound(incoming));

        StompHeadersSubframe headersSubFrame = channel.readInbound();
        assertNotNull(headersSubFrame);
        assertTrue(headersSubFrame.decoderResult().isFailure());

        assertEquals("received an invalid escape header sequence 'custom_invalid\\t'",
                     headersSubFrame.decoderResult().cause().getMessage());
    }

    @Test
    public void testCRResetsUtf8DecodeState() {
        // When a CR byte appears during a multi-byte UTF-8 sequence, the parser's interim state
        // should be cleared so that subsequent bytes are decoded correctly.
        // Bug: CR is skipped without resetting interim/nextRead, so the next byte gets
        // incorrectly combined with dirty UTF-8 state, producing garbage characters.
        //
        // Craft a header value where:
        // - 0xC3 starts a 2-byte UTF-8 sequence (sets interim)
        // - 0x0D (CR) is skipped but should clear interim state
        // - 0x41 ('A') should be decoded as plain ASCII 'A', not combined with dirty interim
        channel = new EmbeddedChannel(new StompSubframeDecoder());

        ByteBuf incoming = Unpooled.buffer();
        // CONNECT command line
        incoming.writeBytes("CONNECT\r\n".getBytes(UTF_8));
        // header with multi-byte UTF-8 start byte (0xC3), then CR, then ASCII 'A'
        incoming.writeByte((byte) 'h');
        incoming.writeByte((byte) 'e');
        incoming.writeByte((byte) 'a');
        incoming.writeByte((byte) 'd');
        incoming.writeByte((byte) 'e');
        incoming.writeByte((byte) 'r');
        incoming.writeByte((byte) ':');
        // 0xC3 starts a 2-byte UTF-8 sequence - sets interim state
        incoming.writeByte((byte) 0xC3);
        // CR (0x0D) - should be skipped AND clear the interim UTF-8 state
        incoming.writeByte((byte) 0x0D);
        // 'A' - should be decoded as plain 'A' since CR should have reset state
        incoming.writeByte((byte) 'A');
        // end of header line
        incoming.writeByte((byte) '\n');
        // empty line to end headers
        incoming.writeByte((byte) '\n');
        // null byte to end frame
        incoming.writeByte((byte) '\0');

        assertTrue(channel.writeInbound(incoming));

        StompHeadersSubframe frame = channel.readInbound();
        assertNotNull(frame);
        assertEquals(StompCommand.CONNECT, frame.command());
        // The header value should be just "A" (CR is skipped, UTF-8 state reset)
        // With the bug, it would contain corrupted UTF-8 combining 0xC3 with 'A'
        assertEquals("A", frame.headers().get("header"));

        StompContentSubframe content = channel.readInbound();
        assertSame(LastStompContentSubframe.EMPTY_LAST_CONTENT, content);
        content.release();

        assertNull(channel.readInbound());
    }
}
