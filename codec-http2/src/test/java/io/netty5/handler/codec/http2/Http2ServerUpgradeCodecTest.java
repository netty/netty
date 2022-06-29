/*
 * Copyright 2017 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty5.handler.codec.http2;

import io.netty5.buffer.api.Buffer;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.DefaultChannelId;
import io.netty5.channel.ServerChannel;
import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.handler.codec.http.DefaultFullHttpRequest;
import io.netty5.handler.codec.http.DefaultHttpHeaders;
import io.netty5.handler.codec.http.FullHttpRequest;
import io.netty5.handler.codec.http.HttpHeaderNames;
import io.netty5.handler.codec.http.HttpMethod;
import io.netty5.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static io.netty5.buffer.api.DefaultBufferAllocators.preferredAllocator;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Http2ServerUpgradeCodecTest {

    @Test
    public void testUpgradeToHttp2ConnectionHandler() throws Exception {
        testUpgrade(new Http2ConnectionHandlerBuilder().frameListener(new Http2FrameAdapter()).build(), null);
    }

    @Test
    public void testUpgradeToHttp2FrameCodec() throws Exception {
        testUpgrade(new Http2FrameCodecBuilder(true).build(), null);
    }

    @Test
    public void testUpgradeToHttp2FrameCodecWithMultiplexer() throws Exception {
        testUpgrade(new Http2FrameCodecBuilder(true).build(),
                new Http2MultiplexHandler(new HttpInboundHandler()));
    }

    private static void testUpgrade(Http2ConnectionHandler handler, ChannelHandler multiplexer) throws Exception {
        FullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.OPTIONS, "*", preferredAllocator().allocate(0));
        request.headers().set(HttpHeaderNames.HOST, "netty.io");
        request.headers().set(HttpHeaderNames.CONNECTION, "Upgrade, HTTP2-Settings");
        request.headers().set(HttpHeaderNames.UPGRADE, "h2c");
        request.headers().set("HTTP2-Settings", "AAMAAABkAAQAAP__");

        ServerChannel parent = Mockito.mock(ServerChannel.class);
        EmbeddedChannel channel = new EmbeddedChannel(parent, DefaultChannelId.newInstance(), true, false,
                new ChannelHandler() { });
        ChannelHandlerContext ctx = channel.pipeline().firstContext();
        Http2ServerUpgradeCodec codec;
        if (multiplexer == null) {
            codec = new Http2ServerUpgradeCodec(handler);
        } else {
            codec = new Http2ServerUpgradeCodec((Http2FrameCodec) handler, multiplexer);
        }
        channel.executor().execute(() -> {
            assertTrue(codec.prepareUpgradeResponse(ctx, request, new DefaultHttpHeaders()));
            codec.upgradeTo(ctx, request);
        });

        // Flush the channel to ensure we write out all buffered data
        channel.flush();

        channel.writeInbound(Http2CodecUtil.connectionPrefaceBuffer());
        Http2FrameInboundWriter writer = new Http2FrameInboundWriter(channel);
        writer.writeInboundSettings(new Http2Settings());
        writer.writeInboundRstStream(Http2CodecUtil.HTTP_UPGRADE_STREAM_ID, Http2Error.CANCEL.code());

        assertSame(handler, channel.pipeline().remove(handler.getClass()));
        assertNull(channel.pipeline().get(handler.getClass()));
        assertTrue(channel.finish());

        // Check that the preface was send (a.k.a the settings frame)
        Buffer settingsBuffer = channel.readOutbound();
        assertNotNull(settingsBuffer);
        settingsBuffer.close();

        Buffer buf = channel.readOutbound();
        assertNotNull(buf);
        buf.close();

        assertNull(channel.readOutbound());
    }

    private static final class HttpInboundHandler implements ChannelHandler {
        @Override
        public boolean isSharable() {
            return true;
        }
    }
}
