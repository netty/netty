/*
 * Copyright 2018 The Netty Project
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

import org.junit.Test;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public abstract class Http2MultiplexClientUpgradeTest<C extends Http2FrameCodec> {

    @ChannelHandler.Sharable
    static final class NoopHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            ctx.channel().close();
        }
    }

    private static final class UpgradeHandler extends ChannelInboundHandlerAdapter {
        Http2Stream.State stateOnActive;
        int streamId;
        boolean channelInactiveCalled;

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            Http2StreamChannel ch = (Http2StreamChannel) ctx.channel();
            stateOnActive = ch.stream().state();
            streamId = ch.stream().id();
            super.channelActive(ctx);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            channelInactiveCalled = true;
            super.channelInactive(ctx);
        }
    }

    protected abstract C newCodec(ChannelHandler upgradeHandler);

    protected abstract ChannelHandler newMultiplexer(ChannelHandler upgradeHandler);

    @Test
    public void upgradeHandlerGetsActivated() throws Exception {
        UpgradeHandler upgradeHandler = new UpgradeHandler();
        C codec = newCodec(upgradeHandler);
        EmbeddedChannel ch = new EmbeddedChannel(codec, newMultiplexer(upgradeHandler));

        codec.onHttpClientUpgrade();

        assertFalse(upgradeHandler.stateOnActive.localSideOpen());
        assertTrue(upgradeHandler.stateOnActive.remoteSideOpen());
        assertNotNull(codec.connection().stream(Http2CodecUtil.HTTP_UPGRADE_STREAM_ID).getProperty(codec.streamKey));
        assertEquals(Http2CodecUtil.HTTP_UPGRADE_STREAM_ID, upgradeHandler.streamId);
        assertTrue(ch.finishAndReleaseAll());
        assertTrue(upgradeHandler.channelInactiveCalled);
    }

    @Test(expected = Http2Exception.class)
    public void clientUpgradeWithoutUpgradeHandlerThrowsHttp2Exception() throws Http2Exception {
        C codec = newCodec(null);
        EmbeddedChannel ch = new EmbeddedChannel(codec, newMultiplexer(null));
        try {
            codec.onHttpClientUpgrade();
        } finally {
            assertTrue(ch.finishAndReleaseAll());
        }
    }
}
