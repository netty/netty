/*
 * Copyright 2017 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.http2;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.DefaultChannelId;
import io.netty.channel.ServerChannel;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.mockito.Mockito;

public class Http2ServerUpgradeCodecTest {

    @Test
    public void testUpgradeToHttp2ConnectionHandler() {
        testUpgrade(new Http2ConnectionHandlerBuilder().frameListener(new Http2FrameAdapter()).build(), null);
    }

    @Test
    public void testUpgradeToHttp2FrameCodec() {
        testUpgrade(new Http2FrameCodecBuilder(true).build(), null);
    }

    @Test
    public void testUpgradeToHttp2MultiplexCodec() {
        testUpgrade(new Http2MultiplexCodecBuilder(true, new HttpInboundHandler()).build(), null);
    }

    @Test
    public void testUpgradeToHttp2FrameCodecWithMultiplexer() {
        testUpgrade(new Http2FrameCodecBuilder(true).build(),
                new Http2MultiplexHandler(new HttpInboundHandler()));
    }

    private static void testUpgrade(Http2ConnectionHandler handler, ChannelHandler multiplexer) {
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.OPTIONS, "*");
        request.headers().set(HttpHeaderNames.HOST, "netty.io");
        request.headers().set(HttpHeaderNames.CONNECTION, "Upgrade, HTTP2-Settings");
        request.headers().set(HttpHeaderNames.UPGRADE, "h2c");
        request.headers().set("HTTP2-Settings", "AAMAAABkAAQAAP__");

        ServerChannel parent = Mockito.mock(ServerChannel.class);
        EmbeddedChannel channel = new EmbeddedChannel(parent, DefaultChannelId.newInstance(), true, false,
                new ChannelInboundHandlerAdapter());
        ChannelHandlerContext ctx = channel.pipeline().firstContext();
        Http2ServerUpgradeCodec codec;
        if (multiplexer == null) {
            codec = new Http2ServerUpgradeCodec(handler);
        } else {
            codec = new Http2ServerUpgradeCodec((Http2FrameCodec) handler, multiplexer);
        }
        assertTrue(codec.prepareUpgradeResponse(ctx, request, new DefaultHttpHeaders()));
        codec.upgradeTo(ctx, request);
        // Flush the channel to ensure we write out all buffered data
        channel.flush();

        channel.writeInbound(Http2CodecUtil.connectionPrefaceBuf());
        Http2FrameInboundWriter writer = new Http2FrameInboundWriter(channel);
        writer.writeInboundSettings(new Http2Settings());
        writer.writeInboundRstStream(Http2CodecUtil.HTTP_UPGRADE_STREAM_ID, Http2Error.CANCEL.code());

        assertSame(handler, channel.pipeline().remove(handler.getClass()));
        assertNull(channel.pipeline().get(handler.getClass()));
        assertTrue(channel.finish());

        // Check that the preface was send (a.k.a the settings frame)
        ByteBuf settingsBuffer = channel.readOutbound();
        assertNotNull(settingsBuffer);
        settingsBuffer.release();

        ByteBuf buf = channel.readOutbound();
        assertNotNull(buf);
        buf.release();

        assertNull(channel.readOutbound());
    }

    @ChannelHandler.Sharable
    private static final class HttpInboundHandler extends ChannelInboundHandlerAdapter { }
}
