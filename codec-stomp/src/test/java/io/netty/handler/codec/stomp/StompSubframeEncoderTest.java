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
import org.junit.jupiter.params.provider.CsvSource;
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
    public void testFrameAndContentEncoding() throws Exception {
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
    public void testUtf8FrameEncoding() throws Exception {
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
    public void testOneBufferForStompFrameWithEmptyContent() throws Exception {
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
    void testEscapeStompHeaders() throws Exception {
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
    void mustRejectNulCharacterInContentWithoutContentLength() {
        // Without a content-length header the receiver ends the frame at the first NUL octet and parses
        // the rest of the content as further frames.
        StompFrame frame = new DefaultStompFrame(StompCommand.MESSAGE,
                Unpooled.copiedBuffer("hello\0ERROR\nmessage:injected\n\n", CharsetUtil.UTF_8));
        frame.headers().set(StompHeaders.SUBSCRIPTION, "0");
        assertThatThrownBy(() -> channel.writeOutbound(frame))
                .isInstanceOf(EncoderException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("content-length");
        assertEquals(0, frame.refCnt());
    }

    @Test
    void testNulCharacterInContentWithContentLength() throws Exception {
        String body = "hello\0world";
        StompFrame frame = new DefaultStompFrame(StompCommand.MESSAGE, Unpooled.copiedBuffer(body, CharsetUtil.UTF_8));
        frame.headers().set(StompHeaders.CONTENT_LENGTH, Integer.toString(body.length()));
        assertTrue(channel.writeOutbound(frame));

        ByteBuf stompBuffer = channel.readOutbound();
        assertNotNull(stompBuffer);
        assertNull(channel.readOutbound());

        EmbeddedChannel decoder = new EmbeddedChannel(new StompSubframeDecoder(), new StompSubframeAggregator(1024));
        assertTrue(decoder.writeInbound(stompBuffer));
        StompFrame decoded = decoder.readInbound();
        assertNotNull(decoded);
        assertEquals(body, decoded.content().toString(CharsetUtil.UTF_8));
        assertTrue(decoded.release());
        assertNull(decoder.readInbound());
        assertFalse(decoder.finish());
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
    void testNotEscapeStompHeadersForConnectCommand() throws Exception {
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
    @CsvSource({
            "CONNECT, '\r'", "CONNECT, '\n'", "CONNECT, '\0'", "CONNECT, ':'",
            "CONNECTED, '\r'", "CONNECTED, '\n'", "CONNECTED, '\0'", "CONNECTED, ':'"
    })
    void mustRejectIllegalCharsInConnectionCommandHeaderNames(StompCommand command, char illegalChar) {
        StompFrame frame = new DefaultStompFrame(command);
        frame.headers()
                .add("header" + illegalChar, "value");
        assertThatThrownBy(() -> channel.writeOutbound(frame))
                .isInstanceOf(EncoderException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("illegal character");
    }

    @ParameterizedTest
    @CsvSource({
            "CONNECT, '\r'", "CONNECT, '\n'", "CONNECT, '\0'",
            "CONNECTED, '\r'", "CONNECTED, '\n'", "CONNECTED, '\0'"
    })
    void mustRejectIllegalCharsInConnectionCommandHeaderValues(StompCommand command, char illegalChar) {
        StompFrame frame = new DefaultStompFrame(command);
        frame.headers()
                .add("header", "value" + illegalChar);
        assertThatThrownBy(() -> channel.writeOutbound(frame))
                .isInstanceOf(EncoderException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("illegal character");
    }

    @ParameterizedTest
    @ValueSource(strings = {"CONNECT", "CONNECTED"})
    void mustAllowColonInConnectionCommandHeaderValues(String commandName) throws Exception {
        StompFrame frame = new DefaultStompFrame(StompCommand.valueOf(commandName));
        frame.headers().add("url", "http://example.com:8080/path");

        assertTrue(channel.writeOutbound(frame));

        ByteBuf stompBuffer = channel.readOutbound();
        assertNotNull(stompBuffer);
        assertNull(channel.readOutbound());

        assertEquals(commandName + "\nurl:http://example.com:8080/path\n\n\0",
                stompBuffer.toString(StandardCharsets.UTF_8));
        assertTrue(stompBuffer.release());
    }

    @Test
    void testNotEscapeStompHeadersForConnectedCommand() throws Exception {
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
}
