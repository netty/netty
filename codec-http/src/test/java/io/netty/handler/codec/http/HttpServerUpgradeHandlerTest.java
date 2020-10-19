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
package io.netty.handler.codec.http;

import java.util.Collection;
import java.util.Collections;

import org.junit.Test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpServerUpgradeHandler.UpgradeCodec;
import io.netty.handler.codec.http.HttpServerUpgradeHandler.UpgradeCodecFactory;
import io.netty.util.CharsetUtil;

import static org.junit.Assert.*;

public class HttpServerUpgradeHandlerTest {

    private class TestUpgradeCodec implements UpgradeCodec {
        @Override
        public Collection<CharSequence> requiredUpgradeHeaders() {
            return Collections.<CharSequence>emptyList();
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
            ctx.pipeline().addAfter(ctx.name(), "marker", new ChannelInboundHandlerAdapter());
          }
    }

    @Test
    public void upgradesPipelineInSameMethodInvocation() {
        final HttpServerCodec httpServerCodec = new HttpServerCodec();
        final UpgradeCodecFactory factory = new UpgradeCodecFactory() {
            @Override
            public UpgradeCodec newUpgradeCodec(CharSequence protocol) {
                return new TestUpgradeCodec();
            }
        };

        ChannelHandler testInStackFrame = new ChannelDuplexHandler() {
            // marker boolean to signal that we're in the `channelRead` method
            private boolean inReadCall;
            private boolean writeUpgradeMessage;
            private boolean writeFlushed;

            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                assertFalse(inReadCall);
                assertFalse(writeUpgradeMessage);

                inReadCall = true;
                try {
                    super.channelRead(ctx, msg);
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
            public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise) {
                // We ensure that we're in the read call and defer the write so we can
                // make sure the pipeline was reformed irrespective of the flush completing.
                assertTrue(inReadCall);
                writeUpgradeMessage = true;
                ctx.channel().eventLoop().execute(new Runnable() {
                    @Override
                    public void run() {
                        ctx.write(msg, promise);
                    }
                });
                promise.addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) {
                        writeFlushed = true;
                    }
                });
            }
        };

        HttpServerUpgradeHandler upgradeHandler = new HttpServerUpgradeHandler(httpServerCodec, factory);

        EmbeddedChannel channel = new EmbeddedChannel(testInStackFrame, httpServerCodec, upgradeHandler);

        String upgradeString = "GET / HTTP/1.1\r\n" +
            "Host: example.com\r\n" +
            "Connection: Upgrade, HTTP2-Settings\r\n" +
            "Upgrade: nextprotocol\r\n" +
            "HTTP2-Settings: AAMAAABkAAQAAP__\r\n\r\n";
        ByteBuf upgrade = Unpooled.copiedBuffer(upgradeString, CharsetUtil.US_ASCII);

        assertFalse(channel.writeInbound(upgrade));
        assertNull(channel.pipeline().get(HttpServerCodec.class));
        assertNotNull(channel.pipeline().get("marker"));

        channel.flushOutbound();
        ByteBuf upgradeMessage = channel.readOutbound();
        String expectedHttpResponse = "HTTP/1.1 101 Switching Protocols\r\n" +
            "connection: upgrade\r\n" +
            "upgrade: nextprotocol\r\n\r\n";
        assertEquals(expectedHttpResponse, upgradeMessage.toString(CharsetUtil.US_ASCII));
        assertTrue(upgradeMessage.release());
        assertFalse(channel.finishAndReleaseAll());
    }
}
