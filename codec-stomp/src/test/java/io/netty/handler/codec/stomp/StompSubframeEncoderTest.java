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
import io.netty.util.AsciiString;
import io.netty.util.CharsetUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static io.netty.handler.codec.stomp.StompTestConstants.*;
import static org.junit.Assert.*;

public class StompSubframeEncoderTest {

    private EmbeddedChannel channel;

    @Before
    public void setup() throws Exception {
        channel = new EmbeddedChannel(new StompSubframeEncoder());
    }

    @After
    public void teardown() throws Exception {
        assertFalse(channel.finish());
    }

    @Test
    public void testFrameAndContentEncoding() {
        StompHeadersSubframe frame = new DefaultStompHeadersSubframe(StompCommand.CONNECT);
        StompHeaders headers = frame.headers();
        headers.set(StompHeaders.HOST, "stomp.github.org");
        headers.set(StompHeaders.ACCEPT_VERSION, "1.1,1.2");
        channel.writeOutbound(frame);
        channel.writeOutbound(LastStompContentSubframe.EMPTY_LAST_CONTENT);
        ByteBuf aggregatedBuffer = Unpooled.buffer();
        ByteBuf byteBuf = channel.readOutbound();
        assertNotNull(byteBuf);
        aggregatedBuffer.writeBytes(byteBuf);
        byteBuf.release();

        byteBuf = channel.readOutbound();
        assertNotNull(byteBuf);
        aggregatedBuffer.writeBytes(byteBuf);
        byteBuf.release();

        aggregatedBuffer.resetReaderIndex();
        String content = aggregatedBuffer.toString(CharsetUtil.UTF_8);
        assertEquals(StompTestConstants.CONNECT_FRAME, content);
        aggregatedBuffer.release();
    }

    @Test
    public void testUtf8FrameEncoding() {
        StompFrame frame = new DefaultStompFrame(StompCommand.SEND,
                                                 Unpooled.wrappedBuffer("body".getBytes(CharsetUtil.UTF_8)));
        StompHeaders incoming = frame.headers();
        incoming.set(StompHeaders.DESTINATION, "/queue/№11±♛нетти♕");
        incoming.set(StompHeaders.CONTENT_TYPE, AsciiString.of("text/plain"));

        channel.writeOutbound(frame);

        ByteBuf fullFrame = channel.readOutbound();
        assertEquals(SEND_FRAME_UTF8, fullFrame.toString(CharsetUtil.UTF_8));
        assertTrue(fullFrame.release());
    }

    @Test
    public void testOneBufferForStompFrameWithEmptyContent() {
        StompFrame connectedFrame = new DefaultStompFrame(StompCommand.CONNECTED);
        connectedFrame.headers().set(StompHeaders.VERSION, "1.2");

        assertTrue(channel.writeOutbound(connectedFrame));

        ByteBuf stompBuffer = channel.readOutbound();

        assertNotNull(stompBuffer);
        assertNull(channel.readOutbound());
        assertEquals("CONNECTED\nversion:1.2\n\n\0", stompBuffer.toString(CharsetUtil.UTF_8));
        assertTrue(stompBuffer.release());
    }
}
