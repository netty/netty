/*
 * Copyright 2018 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
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
import io.netty.util.CharsetUtil;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests common, abstract class functionality in {@link WebSocketClientProtocolHandler}.
 */
public class WebSocketProtocolHandlerTest {

    @Test
    public void testPingFrame() {
        ByteBuf pingData = Unpooled.copiedBuffer("Hello, world", CharsetUtil.UTF_8);
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketProtocolHandler() { });

        PingWebSocketFrame inputMessage = new PingWebSocketFrame(pingData);
        assertFalse(channel.writeInbound(inputMessage)); // the message was not propagated inbound

        // a Pong frame was written to the channel
        PongWebSocketFrame response = channel.readOutbound();
        assertEquals(pingData, response.content());

        pingData.release();
        assertFalse(channel.finish());
    }

    @Test
    public void testPongFrameDropFrameFalse() {
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketProtocolHandler(false) { });

        PongWebSocketFrame pingResponse = new PongWebSocketFrame();
        assertTrue(channel.writeInbound(pingResponse));

        assertPropagatedInbound(pingResponse, channel);

        pingResponse.release();
        assertFalse(channel.finish());
    }

    @Test
    public void testPongFrameDropFrameTrue() {
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketProtocolHandler(true) { });

        PongWebSocketFrame pingResponse = new PongWebSocketFrame();
        assertFalse(channel.writeInbound(pingResponse)); // message was not propagated inbound
    }

    @Test
    public void testTextFrame() {
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketProtocolHandler() { });

        TextWebSocketFrame textFrame = new TextWebSocketFrame();
        assertTrue(channel.writeInbound(textFrame));

        assertPropagatedInbound(textFrame, channel);

        textFrame.release();
        assertFalse(channel.finish());
    }

    /**
     * Asserts that a message was propagated inbound through the channel.
     */
    private static <T extends WebSocketFrame> void assertPropagatedInbound(T message, EmbeddedChannel channel) {
        T propagatedResponse = channel.readInbound();
        assertEquals(message, propagatedResponse);
    }
}
