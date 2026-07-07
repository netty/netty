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
import io.netty.handler.codec.EncoderException;
import io.netty.util.AsciiString;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;

import static io.netty.handler.codec.stomp.StompTestConstants.SEND_FRAME_UTF8;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StompSubframeEncoderTest {

    private EmbeddedChannel channel;

    @BeforeEach
    public void setup() throws Exception {
        channel = new EmbeddedChannel(new StompSubframeEncoder());
    }

    @AfterEach
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

    @Test
    void testEscapeStompHeaders() {
        StompFrame messageFrame = new DefaultStompFrame(StompCommand.MESSAGE);
        messageFrame.headers()
                  .add(StompHeaders.MESSAGE_ID, "100")
                  .add(StompHeaders.SUBSCRIPTION, "1")
                  .add(StompHeaders.DESTINATION, "/queue/a:")
                  .add("header\\\r\n:Name", "header\\\r\n:Value")
                  .add("header_\\_\r_\n_:_Name", "header_\\_\r_\n_:_Value")
                  .add("headerName:", ":headerValue");

        assertTrue(channel.writeOutbound(messageFrame));

        ByteBuf stompBuffer = channel.readOutbound();
        assertNotNull(stompBuffer);
        assertNull(channel.readOutbound());

        assertEquals(StompTestConstants.ESCAPED_MESSAGE_FRAME, stompBuffer.toString(StandardCharsets.UTF_8));
        assertTrue(stompBuffer.release());
    }

    @Test
    void mustRejectNulCharacterInHeaders() {
        // The NUL character has no escape and is always rejected.
        StompFrame frame1 = new DefaultStompFrame(StompCommand.MESSAGE);
        frame1.headers()
                .add("header\0", "value");
        assertThatThrownBy(() -> channel.writeOutbound(frame1))
                .isInstanceOf(EncoderException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("illegal character");

        StompFrame frame2 = new DefaultStompFrame(StompCommand.CONNECT);
        frame2.headers()
                .add("header", "value\0");
        assertThatThrownBy(() -> channel.writeOutbound(frame2))
                .isInstanceOf(EncoderException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("illegal character");
    }

    @Test
    void mustRejectEmptyHeaderNames() {
        StompFrame frame1 = new DefaultStompFrame(StompCommand.MESSAGE);
        frame1.headers()
                .add("", "value");
        assertThatThrownBy(() -> channel.writeOutbound(frame1))
                .isInstanceOf(EncoderException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty header name");
    }

    @Test
    void testNotEscapeStompHeadersForConnectCommand() {
        String expectedStompFrame = "CONNECT\n"
                + "backslashHeaderName-\\:backslashHeaderValue-\\\n"
                + '\n' + '\0';
        StompFrame connectFrame = new DefaultStompFrame(StompCommand.CONNECT);
        connectFrame.headers()
                  .add("backslashHeaderName-\\", "backslashHeaderValue-\\");

        assertTrue(channel.writeOutbound(connectFrame));

        ByteBuf stompBuffer = channel.readOutbound();
        assertNotNull(stompBuffer);
        assertNull(channel.readOutbound());

        assertEquals(expectedStompFrame, stompBuffer.toString(StandardCharsets.UTF_8));
        assertTrue(stompBuffer.release());
    }

    @ParameterizedTest
    @ValueSource(chars = {'\r', '\n', '\0', ':'})
    void mustRejectIllegalCharsInConnectCommandHeaders(char illegalChar) {
        StompFrame connectFrame1 = new DefaultStompFrame(StompCommand.CONNECT);
        connectFrame1.headers()
                .add("header" + illegalChar, "value");
        assertThatThrownBy(() -> channel.writeOutbound(connectFrame1))
                .isInstanceOf(EncoderException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("illegal character");

        StompFrame connectFrame2 = new DefaultStompFrame(StompCommand.CONNECT);
        connectFrame2.headers()
                .add("header", "value" + illegalChar);
        assertThatThrownBy(() -> channel.writeOutbound(connectFrame2))
                .isInstanceOf(EncoderException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("illegal character");
    }

    @Test
    void testNotEscapeStompHeadersForConnectedCommand() {
        String expectedStompFrame = "CONNECTED\n"
                                    + "backslashHeaderName-\\:backslashHeaderValue-\\\n"
                                    + '\n' + '\0';
        StompFrame connectedFrame = new DefaultStompFrame(StompCommand.CONNECTED);
        connectedFrame.headers()
                    .add("backslashHeaderName-\\", "backslashHeaderValue-\\");

        assertTrue(channel.writeOutbound(connectedFrame));

        ByteBuf stompBuffer = channel.readOutbound();
        assertNotNull(stompBuffer);
        assertNull(channel.readOutbound());

        assertEquals(expectedStompFrame, stompBuffer.toString(StandardCharsets.UTF_8));
        assertTrue(stompBuffer.release());
    }

    @ParameterizedTest
    @ValueSource(chars = {'\r', '\n', '\0', ':'})
    void mustRejectIllegalCharsInConnectedCommandHeaders(char illegalChar) {
        StompFrame connectedFrame1 = new DefaultStompFrame(StompCommand.CONNECTED);
        connectedFrame1.headers()
                .add("header" + illegalChar, "name");
        assertThatThrownBy(() -> channel.writeOutbound(connectedFrame1))
                .isInstanceOf(EncoderException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("illegal character");

        StompFrame connectedFrame2 = new DefaultStompFrame(StompCommand.CONNECTED);
        connectedFrame2.headers()
                .add("header", "name" + illegalChar);
        assertThatThrownBy(() -> channel.writeOutbound(connectedFrame2))
                .isInstanceOf(EncoderException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("illegal character");
    }
}
