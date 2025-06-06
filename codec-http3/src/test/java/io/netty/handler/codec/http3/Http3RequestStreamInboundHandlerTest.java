/*
 * Copyright 2020 The Netty Project
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
package io.netty.handler.codec.http3;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Http3RequestStreamInboundHandlerTest {

    @Test
    public void testDetectLastViaUserEvent() {
        EmbeddedQuicStreamChannel channel = new EmbeddedQuicStreamChannel(new TestHttp3RequestStreamInboundHandler());
        assertTrue(channel.writeInbound(new DefaultHttp3HeadersFrame()));
        assertTrue(channel.writeInbound(new DefaultHttp3DataFrame(Unpooled.buffer())));
        assertTrue(channel.writeInbound(new DefaultHttp3DataFrame(Unpooled.buffer())));
        channel.pipeline().fireUserEventTriggered(ChannelInputShutdownEvent.INSTANCE);
        assertFrame(channel);
        assertFrame(channel);
        assertFrame(channel);
        assertEquals(true, channel.readInbound());
        assertFalse(channel.finish());
    }

    private void assertFrame(EmbeddedChannel channel) {
        Http3Frame frame = channel.readInbound();
        assertNotNull(frame);
        ReferenceCountUtil.release(frame);
    }

    private static final class TestHttp3RequestStreamInboundHandler extends Http3RequestStreamInboundHandler {

        @Override
        public void channelRead(ChannelHandlerContext ctx, Http3HeadersFrame frame) {
            ctx.fireChannelRead(frame);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Http3DataFrame frame) {
            ctx.fireChannelRead(frame);
        }

        @Override
        protected void channelInputClosed(ChannelHandlerContext ctx) {
            ctx.fireChannelRead(true);
        }
    }
}
