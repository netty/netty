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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.CorruptedFrameException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class WebSocketUtf8FrameValidatorTest {

    @Test
    public void testCorruptedFrameExceptionInFinish() {
        assertCorruptedFrameExceptionHandling(new byte[]{-50});
    }

    @Test
    public void testCorruptedFrameExceptionInCheck() {
        assertCorruptedFrameExceptionHandling(new byte[]{-8, -120, -128, -128, -128});
    }

    private void assertCorruptedFrameExceptionHandling(byte[] data) {
        EmbeddedChannel channel = new EmbeddedChannel(new Utf8FrameValidator());
        TextWebSocketFrame frame = new TextWebSocketFrame(Unpooled.copiedBuffer(data));
        try {
            channel.writeInbound(frame);
            fail();
        } catch (CorruptedFrameException e) {
            // expected exception
        }
        assertTrue(channel.finish());
        ByteBuf buf = channel.readOutbound();
        assertNotNull(buf);
        try {
            assertFalse(buf.isReadable());
        } finally {
            buf.release();
        }
        assertNull(channel.readOutbound());
        assertEquals(0, frame.refCnt());
    }
}
