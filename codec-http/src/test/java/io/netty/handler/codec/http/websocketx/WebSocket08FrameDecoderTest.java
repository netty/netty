/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the "License");
 * you may not use this file except in compliance with the License. You may obtain a copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */
package io.netty.handler.codec.http.websocketx;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class WebSocket08FrameDecoderTest {

    @Test
    public void channelInactive() throws Exception {
        final WebSocket08FrameDecoder decoder = new WebSocket08FrameDecoder(true, true, 65535, false);
        final ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        decoder.channelInactive(ctx);
        verify(ctx).fireChannelInactive();
    }

    @Test
    public void supportIanaStatusCodes() throws Exception {
        Set<Integer> forbiddenIanaCodes = new HashSet<Integer>();
        forbiddenIanaCodes.add(1004);
        forbiddenIanaCodes.add(1005);
        forbiddenIanaCodes.add(1006);
        Set<Integer> validIanaCodes = new HashSet<Integer>();
        for (int i = 1000; i < 1015; i++) {
            validIanaCodes.add(i);
        }
        validIanaCodes.removeAll(forbiddenIanaCodes);

        for (int statusCode: validIanaCodes) {
            EmbeddedChannel encoderChannel = new EmbeddedChannel(new WebSocket08FrameEncoder(true));
            EmbeddedChannel decoderChannel = new EmbeddedChannel(new WebSocket08FrameDecoder(true, true, 65535, false));

            assertTrue(encoderChannel.writeOutbound(new CloseWebSocketFrame(statusCode, "Bye")));
            assertTrue(encoderChannel.finish());
            ByteBuf serializedCloseFrame = encoderChannel.readOutbound();
            assertNull(encoderChannel.readOutbound());

            assertTrue(decoderChannel.writeInbound(serializedCloseFrame));
            assertTrue(decoderChannel.finish());

            CloseWebSocketFrame outputFrame = decoderChannel.readInbound();
            assertNull(decoderChannel.readOutbound());
            try {
                assertEquals(statusCode, outputFrame.statusCode());
            } finally {
                outputFrame.release();
            }
        }
    }

    @Test
    void protocolViolationWhenNegativeFrameLength() {
        WebSocket08FrameDecoder decoder = new WebSocket08FrameDecoder(true, true, 65535, false);
        final EmbeddedChannel channel = new EmbeddedChannel(decoder);
        final ByteBuf invalidFrame = Unpooled.buffer(10).writeByte(0x81)
                                             .writeByte(0xFF).writeLong(-1L);

        Throwable exception = assertThrows(CorruptedWebSocketFrameException.class, new Executable() {
            @Override
            public void execute() {
                channel.writeInbound(invalidFrame);
            }
        });
        assertEquals("invalid data frame length (negative length)", exception.getMessage());

        CloseWebSocketFrame closeFrame = channel.readOutbound();
        assertEquals("invalid data frame length (negative length)", closeFrame.reasonText());
        assertTrue(closeFrame.release());
        assertFalse(channel.isActive());
    }
}
