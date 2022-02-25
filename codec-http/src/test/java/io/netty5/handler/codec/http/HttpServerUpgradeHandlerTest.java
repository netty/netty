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
package io.netty5.handler.codec.http;

import io.netty5.buffer.api.Buffer;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.handler.codec.http.HttpServerUpgradeHandler.UpgradeCodec;
import io.netty5.handler.codec.http.HttpServerUpgradeHandler.UpgradeCodecFactory;
import io.netty5.util.CharsetUtil;
import io.netty5.util.ReferenceCountUtil;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.Promise;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Collections;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class HttpServerUpgradeHandlerTest {

    private static class TestUpgradeCodec implements UpgradeCodec {
        @Override
        public Collection<CharSequence> requiredUpgradeHeaders() {
            return Collections.emptyList();
        }

        @Override
        public boolean prepareUpgradeResponse(ChannelHandlerContext ctx, FullHttpRequest upgradeRequest,
                                              HttpHeaders upgradeHeaders) {
            return true;
        }

        @Override
        public void upgradeTo(ChannelHandlerContext ctx, FullHttpRequest upgradeRequest) {
            // Ensure that the HttpServerUpgradeHandler is still installed when this is called
            assertEquals(ctx.pipeline().context(HttpServerUpgradeHandler.class), ctx);
            assertNotNull(ctx.pipeline().get(HttpServerUpgradeHandler.class));

            // Add a marker handler to signal that the upgrade has happened
            ctx.pipeline().addAfter(ctx.name(), "marker", new ChannelHandler() { });
          }
    }

    @Test
    public void upgradesPipelineInSameMethodInvocation() {
        final HttpServerCodec httpServerCodec = new HttpServerCodec();
        final UpgradeCodecFactory factory = protocol -> new TestUpgradeCodec();

        ChannelHandler testInStackFrame = new ChannelHandler() {
            // marker boolean to signal that we're in the `channelRead` method
            private boolean inReadCall;
            private boolean writeUpgradeMessage;
            private boolean writeFlushed;

            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                assertFalse(inReadCall);
                assertFalse(writeUpgradeMessage);

                inReadCall = true;
                try {
                    ctx.fireChannelRead(msg);
                    // All in the same call stack, the upgrade codec should receive the message,
                    // written the upgrade response, and upgraded the pipeline.
                    assertTrue(writeUpgradeMessage);
                    assertFalse(writeFlushed);
                    assertNull(ctx.pipeline().get(HttpServerCodec.class));
                    assertNotNull(ctx.pipeline().get("marker"));
                } finally {
                    inReadCall = false;
                }
            }

            @Override
            public Future<Void> write(final ChannelHandlerContext ctx, final Object msg) {
                // We ensure that we're in the read call and defer the write so we can
                // make sure the pipeline was reformed irrespective of the flush completing.
                assertTrue(inReadCall);
                writeUpgradeMessage = true;
                Promise<Void> promise = ctx.newPromise();
                ctx.channel().executor().execute(() -> ctx.write(msg).cascadeTo(promise));
                Future<Void> future = promise.asFuture();
                return future.addListener(f -> writeFlushed = true);
            }
        };

        HttpServerUpgradeHandler<?> upgradeHandler =
                new HttpServerUpgradeHandler<DefaultHttpContent>(httpServerCodec, factory);

        EmbeddedChannel channel = new EmbeddedChannel(testInStackFrame, httpServerCodec, upgradeHandler);

        byte[] upgradeString = ("GET / HTTP/1.1\r\n" +
            "Host: example.com\r\n" +
            "Connection: Upgrade, HTTP2-Settings\r\n" +
            "Upgrade: nextprotocol\r\n" +
            "HTTP2-Settings: AAMAAABkAAQAAP__\r\n\r\n").getBytes(US_ASCII);
        Buffer upgrade = channel.bufferAllocator().allocate(upgradeString.length).writeBytes(upgradeString);

        assertFalse(channel.writeInbound(upgrade));
        assertNull(channel.pipeline().get(HttpServerCodec.class));
        assertNotNull(channel.pipeline().get("marker"));

        channel.flushOutbound();
        try (Buffer upgradeMessage = channel.readOutbound()) {
            String expectedHttpResponse = "HTTP/1.1 101 Switching Protocols\r\n" +
                    "connection: upgrade\r\n" +
                    "upgrade: nextprotocol\r\n\r\n";
            assertEquals(expectedHttpResponse, upgradeMessage.toString(CharsetUtil.US_ASCII));
            assertTrue(upgradeMessage.isAccessible());
        }
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    public void skippedUpgrade() {
        final HttpServerCodec httpServerCodec = new HttpServerCodec();
        final UpgradeCodecFactory factory = protocol -> {
            fail("Should never be invoked");
            return null;
        };

        HttpServerUpgradeHandler<?> upgradeHandler =
                new HttpServerUpgradeHandler<DefaultHttpContent>(httpServerCodec, factory) {
            @Override
            protected boolean shouldHandleUpgradeRequest(HttpRequest req) {
                return !req.headers().contains(HttpHeaderNames.UPGRADE, "do-not-upgrade", false);
            }
        };

        EmbeddedChannel channel = new EmbeddedChannel(httpServerCodec, upgradeHandler);

        byte[] upgradeString = ("GET / HTTP/1.1\r\n" +
                               "Host: example.com\r\n" +
                               "Connection: Upgrade\r\n" +
                               "Upgrade: do-not-upgrade\r\n\r\n").getBytes(US_ASCII);
        Buffer upgrade = channel.bufferAllocator().allocate(upgradeString.length).writeBytes(upgradeString);

        // The upgrade request should not be passed to the next handler without any processing.
        assertTrue(channel.writeInbound(upgrade));
        assertNotNull(channel.pipeline().get(HttpServerCodec.class));
        assertNull(channel.pipeline().get("marker"));

        HttpRequest req = channel.readInbound();
        assertFalse(req instanceof FullHttpRequest); // Should not be aggregated.
        assertTrue(req.headers().contains(HttpHeaderNames.CONNECTION, "Upgrade", false));
        assertTrue(req.headers().contains(HttpHeaderNames.UPGRADE, "do-not-upgrade", false));
        assertTrue(channel.readInbound() instanceof LastHttpContent);
        assertNull(channel.readInbound());

        // No response should be written because we're just passing through.
        channel.flushOutbound();
        assertNull(channel.readOutbound());
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    public void upgradeFail() {
        final HttpServerCodec httpServerCodec = new HttpServerCodec();
        final UpgradeCodecFactory factory = protocol -> new TestUpgradeCodec();

        HttpServerUpgradeHandler<?> upgradeHandler =
                new HttpServerUpgradeHandler<DefaultHttpContent>(httpServerCodec, factory);

        EmbeddedChannel channel = new EmbeddedChannel(httpServerCodec, upgradeHandler);

        // Build a h2c upgrade request, but without connection header.
        byte[] upgradeString = ("GET / HTTP/1.1\r\n" +
                               "Host: example.com\r\n" +
                               "Upgrade: h2c\r\n\r\n").getBytes(US_ASCII);
        Buffer upgrade = channel.bufferAllocator().allocate(upgradeString.length).writeBytes(upgradeString);

        assertTrue(channel.writeInbound(upgrade));
        assertNotNull(channel.pipeline().get(HttpServerCodec.class));
        assertNotNull(channel.pipeline().get(HttpServerUpgradeHandler.class)); // Should not be removed.
        assertNull(channel.pipeline().get("marker"));

        HttpRequest req = channel.readInbound();
        assertEquals(HttpVersion.HTTP_1_1, req.protocolVersion());
        assertTrue(req.headers().contains(HttpHeaderNames.UPGRADE, "h2c", false));
        assertFalse(req.headers().contains(HttpHeaderNames.CONNECTION));
        ReferenceCountUtil.release(req);
        assertNull(channel.readInbound());

        // No response should be written because we're just passing through.
        channel.flushOutbound();
        assertNull(channel.readOutbound());
        assertFalse(channel.finishAndReleaseAll());
    }
}
