/*
 * Copyright 2018 The Netty Project
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
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.flow.FlowControlHandler;
import io.netty.util.ReferenceCountUtil;
import org.hamcrest.Matchers;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

import static io.netty.util.CharsetUtil.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.*;

/**
 * Tests common, abstract class functionality in {@link WebSocketClientProtocolHandler}.
 */
public class WebSocketProtocolHandlerTest {

    @Test
    public void testPingFrame() {
        ByteBuf pingData = Unpooled.copiedBuffer("Hello, world", UTF_8);
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
    public void testPingPongFlowControlWhenAutoReadIsDisabled() {
        String text1 = "Hello, world #1";
        String text2 = "Hello, world #2";
        String text3 = "Hello, world #3";
        String text4 = "Hello, world #4";

        EmbeddedChannel channel = new EmbeddedChannel();
        channel.config().setAutoRead(false);
        channel.pipeline().addLast(new FlowControlHandler());
        channel.pipeline().addLast(new WebSocketProtocolHandler() { });

        // When
        assertFalse(channel.writeInbound(
            new PingWebSocketFrame(Unpooled.copiedBuffer(text1, UTF_8)),
            new TextWebSocketFrame(text2),
            new TextWebSocketFrame(text3),
            new PingWebSocketFrame(Unpooled.copiedBuffer(text4, UTF_8))
        ));

        // Then - no messages were handled or propagated
        assertNull(channel.readInbound());
        assertNull(channel.readOutbound());

        // When
        channel.read();

        // Then - pong frame was written to the outbound
        PongWebSocketFrame response1 = channel.readOutbound();
        assertEquals(text1, response1.content().toString(UTF_8));

        // And - one requested message was handled and propagated inbound
        TextWebSocketFrame message2 = channel.readInbound();
        assertEquals(text2, message2.text());

        // And - no more messages were handled or propagated
        assertNull(channel.readInbound());
        assertNull(channel.readOutbound());

        // When
        channel.read();

        // Then - one requested message was handled and propagated inbound
        TextWebSocketFrame message3 = channel.readInbound();
        assertEquals(text3, message3.text());

        // And - no more messages were handled or propagated
        // Precisely, ping frame 'text4' was NOT read or handled.
        // It would be handle ONLY on the next 'channel.read()' call.
        assertNull(channel.readInbound());
        assertNull(channel.readOutbound());

        // Cleanup
        response1.release();
        message2.release();
        message3.release();
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

    @Test
    public void testTimeout() throws Exception {
        final AtomicReference<ChannelPromise> ref = new AtomicReference<ChannelPromise>();
        WebSocketProtocolHandler handler = new WebSocketProtocolHandler(
                false, WebSocketCloseStatus.NORMAL_CLOSURE, 1) { };
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                ref.set(promise);
                ReferenceCountUtil.release(msg);
            }
        }, handler);

        ChannelFuture future = channel.writeAndFlush(new CloseWebSocketFrame());
        ChannelHandlerContext ctx = channel.pipeline().context(WebSocketProtocolHandler.class);
        handler.close(ctx, ctx.newPromise());

        do {
            Thread.sleep(10);
            channel.runPendingTasks();
        } while (!future.isDone());

        assertThat(future.cause(), Matchers.instanceOf(WebSocketHandshakeException.class));
        assertFalse(ref.get().isDone());
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
