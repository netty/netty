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
package io.netty.incubator.codec.http3;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.util.ReferenceCountUtil;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;


public class Http3RequestStreamHandlerTest {

    @Test
    public void testDetectLastViaIsShutdown() {
        EmbeddedQuicStreamChannel channel = new EmbeddedQuicStreamChannel(new TestHttp3RequestStreamHandler());
        assertTrue(channel.writeInbound(new DefaultHttp3HeadersFrame()));
        assertTrue(channel.writeInbound(new DefaultHttp3DataFrame(Unpooled.buffer())));
        channel.shutdownInput();
        assertTrue(channel.writeInbound(new DefaultHttp3DataFrame(Unpooled.buffer())));
        assertFrame(channel, false);
        assertFrame(channel, false);
        assertFrame(channel, true);
        assertFalse(channel.finish());
    }

    @Test
    public void testDetectLastViaUserEvent() {
        EmbeddedQuicStreamChannel channel = new EmbeddedQuicStreamChannel(new TestHttp3RequestStreamHandler());
        assertTrue(channel.writeInbound(new DefaultHttp3HeadersFrame()));
        assertTrue(channel.writeInbound(new DefaultHttp3DataFrame(Unpooled.buffer())));
        assertTrue(channel.writeInbound(new DefaultHttp3DataFrame(Unpooled.buffer())));
        channel.pipeline().fireUserEventTriggered(ChannelInputShutdownEvent.INSTANCE);
        assertFrame(channel, false);
        assertFrame(channel, false);
        assertFrame(channel, false);
        assertFrame(channel, true);
        assertFalse(channel.finish());
    }

    private void assertFrame(EmbeddedChannel channel, boolean isLast) {
        Http3Frame frame = channel.readInbound();
        assertNotNull(frame);
        ReferenceCountUtil.release(frame);
        assertEquals(isLast, channel.readInbound());
    }

    private static final class TestHttp3RequestStreamHandler extends Http3RequestStreamHandler {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Http3RequestStreamFrame frame, boolean isLast) {
            ctx.fireChannelRead(frame);
            ctx.fireChannelRead(isLast);
        }
    }
}
