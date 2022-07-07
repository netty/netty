/*
 * Copyright 2018 The Netty Project
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Http2MultiplexClientUpgradeTest {

    static final class NoopHandler implements ChannelHandler {
        @Override
        public boolean isSharable() {
            return true;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            ctx.channel().close();
        }
    }

    private static final class UpgradeHandler implements ChannelHandler {
        Http2Stream.State stateOnActive;
        int streamId;
        boolean channelInactiveCalled;

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            Http2StreamChannel ch = (Http2StreamChannel) ctx.channel();
            stateOnActive = ch.stream().state();
            streamId = ch.stream().id();
            ctx.fireChannelActive();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            channelInactiveCalled = true;
            ctx.fireChannelInactive();
        }
    }

    private Http2FrameCodec newCodec() {
        return Http2FrameCodecBuilder.forClient().build();
    }

    private ChannelHandler newMultiplexer(ChannelHandler upgradeHandler) {
        return new Http2MultiplexHandler(new NoopHandler(), upgradeHandler);
    }

    @Test
    public void upgradeHandlerGetsActivated() throws Exception {
        UpgradeHandler upgradeHandler = new UpgradeHandler();
        Http2FrameCodec codec = newCodec();
        EmbeddedChannel ch = new EmbeddedChannel(codec, newMultiplexer(upgradeHandler));

        ch.executor().submit(() -> {
            codec.onHttpClientUpgrade();
            return null;
        }).asStage().sync();

        assertFalse(upgradeHandler.stateOnActive.localSideOpen());
        assertTrue(upgradeHandler.stateOnActive.remoteSideOpen());
        assertNotNull(codec.connection().stream(Http2CodecUtil.HTTP_UPGRADE_STREAM_ID).getProperty(codec.streamKey));
        assertEquals(Http2CodecUtil.HTTP_UPGRADE_STREAM_ID, upgradeHandler.streamId);
        assertTrue(ch.finishAndReleaseAll());
        assertTrue(upgradeHandler.channelInactiveCalled);
    }

    @Test
    @Timeout(2)
    public void clientUpgradeWithoutUpgradeHandlerThrowsHttp2Exception() throws Http2Exception, InterruptedException {
        final Http2FrameCodec codec = newCodec();
        CountDownLatch latch = new CountDownLatch(1);
        final EmbeddedChannel ch = new EmbeddedChannel(codec, newMultiplexer(null), new ChannelHandler() {
            @Override
            public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause)  {
                if (cause instanceof Http2Exception) {
                    latch.countDown();
                }
            }
        });
        ch.executor().submit(() -> {
            codec.onHttpClientUpgrade();
            return null;
        }).asStage().sync();
        latch.await();
        ch.finishAndReleaseAll();
    }
}
