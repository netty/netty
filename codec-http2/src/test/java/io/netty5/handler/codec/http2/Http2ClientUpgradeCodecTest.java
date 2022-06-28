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

import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.handler.codec.http.DefaultFullHttpRequest;
import io.netty5.handler.codec.http.DefaultFullHttpResponse;
import io.netty5.handler.codec.http.FullHttpRequest;
import io.netty5.handler.codec.http.HttpMethod;
import org.junit.jupiter.api.Test;

import static io.netty5.buffer.api.DefaultBufferAllocators.preferredAllocator;
import static io.netty5.handler.codec.http.HttpResponseStatus.OK;
import static io.netty5.handler.codec.http.HttpVersion.HTTP_1_1;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Http2ClientUpgradeCodecTest {

    @Test
    public void testUpgradeToHttp2ConnectionHandler() throws Exception {
        testUpgrade(new Http2ConnectionHandlerBuilder().server(false).frameListener(
            new Http2FrameAdapter()).build(), null);
    }

    @Test
    public void testUpgradeToHttp2FrameCodec() throws Exception {
        testUpgrade(Http2FrameCodecBuilder.forClient().build(), null);
    }

    @Test
    public void testUpgradeToHttp2FrameCodecWithMultiplexer() throws Exception {
        testUpgrade(Http2FrameCodecBuilder.forClient().build(),
            new Http2MultiplexHandler(new HttpInboundHandler(), new HttpInboundHandler()));
    }

    private static void testUpgrade(Http2ConnectionHandler handler, Http2MultiplexHandler multiplexer)
            throws Exception {
        FullHttpRequest request = new DefaultFullHttpRequest(
                HTTP_1_1, HttpMethod.OPTIONS, "*", preferredAllocator().allocate(0));

        EmbeddedChannel channel = new EmbeddedChannel(new ChannelHandler() { });
        ChannelHandlerContext ctx = channel.pipeline().firstContext();

        Http2ClientUpgradeCodec codec;

        if (multiplexer == null) {
            codec = new Http2ClientUpgradeCodec("connectionHandler", handler);
        } else {
            codec = new Http2ClientUpgradeCodec("connectionHandler", handler, multiplexer);
        }

        codec.setUpgradeHeaders(ctx, request);
        // Flush the channel to ensure we write out all buffered data
        channel.flush();

        channel.executor().submit(() -> {
            codec.upgradeTo(ctx,
                    new DefaultFullHttpResponse(HTTP_1_1, OK, channel.bufferAllocator().allocate(0)).send());
            return null;
        }).sync();
        assertNotNull(channel.pipeline().get("connectionHandler"));

        if (multiplexer != null) {
            assertNotNull(channel.pipeline().get(Http2MultiplexHandler.class));
        }

        assertTrue(channel.finishAndReleaseAll());
    }

    private static final class HttpInboundHandler implements ChannelHandler {
        @Override
        public boolean isSharable() {
            return true;
        }
    }
}
