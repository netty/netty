/*
 * Copyright 2019 The Netty Project
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

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WebSocketUtf8FrameValidatorTest {

    @Test
    public void testCorruptedFrameExceptionInFinish() {
        assertCorruptedFrameExceptionHandling(new byte[]{-50});
    }

    @Test
    public void testCorruptedFrameExceptionInCheck() {
        assertCorruptedFrameExceptionHandling(new byte[]{-8, -120, -128, -128, -128});
    }

    @Test
    void testNotCloseOnProtocolViolation() {
        final EmbeddedChannel channel = new EmbeddedChannel(new Utf8FrameValidator(false));
        final TextWebSocketFrame frame = new TextWebSocketFrame(Unpooled.copiedBuffer(new byte[] { -50 }));
        assertThrows(CorruptedWebSocketFrameException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                channel.writeInbound(frame);
            }
        }, "bytes are not UTF-8");

        assertTrue(channel.isActive());
        assertFalse(channel.finish());
        assertEquals(0, frame.refCnt());
    }

    private void assertCorruptedFrameExceptionHandling(byte[] data) {
        final EmbeddedChannel channel = new EmbeddedChannel(new Utf8FrameValidator());
        final TextWebSocketFrame frame = new TextWebSocketFrame(Unpooled.copiedBuffer(data));
        assertThrows(CorruptedWebSocketFrameException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                channel.writeInbound(frame);
            }
        }, "bytes are not UTF-8");

        assertFalse(channel.isActive());

        CloseWebSocketFrame closeFrame = channel.readOutbound();
        assertNotNull(closeFrame);
        assertEquals("bytes are not UTF-8", closeFrame.reasonText());
        assertEquals(1007, closeFrame.statusCode());
        assertTrue(closeFrame.release());

        assertEquals(0, frame.refCnt());
        assertFalse(channel.finish());
    }

    @Test
    void testCloseWithStatusInTheMiddleOfFragmentAllowed() {
        testControlFrameInTheMiddleOfFragmentAllowed(new CloseWebSocketFrame(WebSocketCloseStatus.NORMAL_CLOSURE));
    }

    @Test
    void testPingInTheMiddleOfFragmentAllowed() {
        testControlFrameInTheMiddleOfFragmentAllowed(new PingWebSocketFrame(Unpooled.EMPTY_BUFFER));
    }

    @Test
    void testPongInTheMiddleOfFragmentAllowed() {
        testControlFrameInTheMiddleOfFragmentAllowed(new PongWebSocketFrame(Unpooled.EMPTY_BUFFER));
    }

    private static void testControlFrameInTheMiddleOfFragmentAllowed(WebSocketFrame controlFrame) {
        final EmbeddedChannel channel = new EmbeddedChannel(new Utf8FrameValidator(false));
        final TextWebSocketFrame frame = new TextWebSocketFrame(false, 0, "text");
        assertTrue(channel.writeInbound(frame));
        assertTrue(channel.writeInbound(controlFrame));
        assertTrue(channel.finishAndReleaseAll());
    }
}
