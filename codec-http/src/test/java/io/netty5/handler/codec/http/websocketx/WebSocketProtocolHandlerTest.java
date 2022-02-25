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

package io.netty5.handler.codec.http.websocketx;

import io.netty5.buffer.api.Buffer;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.handler.flow.FlowControlHandler;
import io.netty5.util.ReferenceCountUtil;
import io.netty5.util.concurrent.Future;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static io.netty5.buffer.api.DefaultBufferAllocators.preferredAllocator;
import static io.netty5.util.CharsetUtil.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests common, abstract class functionality in {@link WebSocketClientProtocolHandler}.
 */
public class WebSocketProtocolHandlerTest {

    @Test
    public void testPingFrame() {
        String message = "Hello, world";
        Buffer pingData = preferredAllocator().copyOf(message.getBytes(UTF_8));
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketProtocolHandler() { });

        PingWebSocketFrame inputMessage = new PingWebSocketFrame(pingData);
        assertFalse(channel.writeInbound(inputMessage)); // the message was not propagated inbound

        // a Pong frame was written to the channel
        try (PongWebSocketFrame response = channel.readOutbound()) {
            assertEquals(message, response.binaryData().toString(UTF_8));
        }

        pingData.close();
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
                new PingWebSocketFrame(preferredAllocator().copyOf(text1.getBytes(UTF_8))),
                new TextWebSocketFrame(preferredAllocator(), text2),
                new TextWebSocketFrame(preferredAllocator(), text3),
                new PingWebSocketFrame(preferredAllocator().copyOf(text4.getBytes(UTF_8))
                )));

        // Then - no messages were handled or propagated
        assertNull(channel.readInbound());
        assertNull(channel.readOutbound());

        // When
        channel.read();

        // Then - pong frame was written to the outbound
        try (PongWebSocketFrame response1 = channel.readOutbound()) {
            assertEquals(text1, response1.binaryData().toString(UTF_8));

            // And - one requested message was handled and propagated inbound
            try (TextWebSocketFrame message2 = channel.readInbound()) {
                assertEquals(text2, message2.text());

                // And - no more messages were handled or propagated
                assertNull(channel.readInbound());
                assertNull(channel.readOutbound());

                // When
                channel.read();

                // Then - one requested message was handled and propagated inbound
                try (TextWebSocketFrame message3 = channel.readInbound()) {
                    assertEquals(text3, message3.text());

                    // And - no more messages were handled or propagated
                    // Precisely, ping frame 'text4' was NOT read or handled.
                    // It would be handle ONLY on the next 'channel.read()' call.
                    assertNull(channel.readInbound());
                    assertNull(channel.readOutbound());
                }
            }
        }
        assertFalse(channel.finish());
    }

    @Test
    public void testPongFrameDropFrameFalse() {
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketProtocolHandler(false) { });

        PongWebSocketFrame pingResponse = new PongWebSocketFrame(true, 0, preferredAllocator().allocate(0));
        assertTrue(channel.writeInbound(pingResponse));

        assertPropagatedInbound(pingResponse, channel);

        pingResponse.close();
        assertFalse(channel.finish());
    }

    @Test
    public void testPongFrameDropFrameTrue() {
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketProtocolHandler(true) { });

        PongWebSocketFrame pingResponse = new PongWebSocketFrame(true, 0, preferredAllocator().allocate(0));
        assertFalse(channel.writeInbound(pingResponse)); // message was not propagated inbound
    }

    @Test
    public void testTextFrame() {
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketProtocolHandler() { });

        TextWebSocketFrame textFrame = new TextWebSocketFrame(preferredAllocator().allocate(0));
        assertTrue(channel.writeInbound(textFrame));

        assertPropagatedInbound(textFrame, channel);

        textFrame.close();
        assertFalse(channel.finish());
    }

    @Test
    public void testTimeout() throws Exception {
        final AtomicReference<Future<Void>> ref = new AtomicReference<>();
        WebSocketProtocolHandler handler = new WebSocketProtocolHandler(
                false, WebSocketCloseStatus.NORMAL_CLOSURE, 1) { };
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelHandler() {
            @Override
            public Future<Void> write(ChannelHandlerContext ctx, Object msg) {
                Future<Void> future = ctx.newPromise().asFuture();
                ref.set(future);
                ReferenceCountUtil.release(msg);
                return future;
            }
        }, handler);

        Future<Void> future = channel.writeAndFlush(new CloseWebSocketFrame(
                true, 0, channel.bufferAllocator().allocate(0)));
        ChannelHandlerContext ctx = channel.pipeline().context(WebSocketProtocolHandler.class);
        handler.close(ctx);

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
