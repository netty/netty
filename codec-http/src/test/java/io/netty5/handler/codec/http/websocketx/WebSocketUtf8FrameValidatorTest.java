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
package io.netty5.handler.codec.http.websocketx;

import io.netty5.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class WebSocketUtf8FrameValidatorTest {

    @Test
    public void testCorruptedFrameExceptionInFinish() {
        assertCorruptedFrameExceptionHandling(new byte[]{-50}, true);
    }

    @Test
    public void testCorruptedFrameExceptionInCheck() {
        assertCorruptedFrameExceptionHandling(new byte[]{-8, -120, -128, -128, -128}, true);
    }

    @Test
    void testNotCloseOnProtocolViolation() {
        assertCorruptedFrameExceptionHandling(new byte[] { -50 }, false);
    }

    private void assertCorruptedFrameExceptionHandling(byte[] data, boolean close) {
        final EmbeddedChannel channel = new EmbeddedChannel(new Utf8FrameValidator(close));
        final TextWebSocketFrame frame = new TextWebSocketFrame(channel.bufferAllocator().copyOf(data));
        assertThrows(CorruptedWebSocketFrameException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                channel.writeInbound(frame);
            }
        }, "bytes are not UTF-8");

        assertEquals(!close, channel.isActive());

        if (close) {
            CloseWebSocketFrame closeFrame = channel.readOutbound();
            assertNotNull(closeFrame);
            assertEquals("bytes are not UTF-8", closeFrame.reasonText());
            assertEquals(1007, closeFrame.statusCode());
            closeFrame.close();
        } else {
            assertNull(channel.readOutbound());
        }

        assertFalse(frame.isAccessible());
        assertFalse(channel.finish());
    }
}
