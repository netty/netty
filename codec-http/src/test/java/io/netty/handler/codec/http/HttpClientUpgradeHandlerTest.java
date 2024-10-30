/*
 * Copyright 2016 The Netty Project
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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HttpClientUpgradeHandlerTest {

    private static final class FakeSourceCodec implements HttpClientUpgradeHandler.SourceCodec {
        @Override
        public void prepareUpgradeFrom(ChannelHandlerContext ctx) {
        }

        @Override
        public void upgradeFrom(ChannelHandlerContext ctx) {
        }
    }

    private static final class FakeUpgradeCodec implements HttpClientUpgradeHandler.UpgradeCodec {
        @Override
        public CharSequence protocol() {
            return "fancyhttp";
        }

        @Override
        public Collection<CharSequence> setUpgradeHeaders(ChannelHandlerContext ctx, HttpRequest upgradeRequest) {
            return Collections.emptyList();
        }

        @Override
        public void upgradeTo(ChannelHandlerContext ctx, FullHttpResponse upgradeResponse) throws Exception {
        }
    }

    private static final class UserEventCatcher extends ChannelInboundHandlerAdapter {
        private Object evt;

        public Object getUserEvent() {
            return evt;
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            this.evt = evt;
        }
    }

    @Test
    public void testSuccessfulUpgrade() {
        HttpClientUpgradeHandler.SourceCodec sourceCodec = new FakeSourceCodec();
        HttpClientUpgradeHandler.UpgradeCodec upgradeCodec = new FakeUpgradeCodec();
        HttpClientUpgradeHandler handler = new HttpClientUpgradeHandler(sourceCodec, upgradeCodec, 1024);
        UserEventCatcher catcher = new UserEventCatcher();
        EmbeddedChannel channel = new EmbeddedChannel(catcher);
        final HttpRequest afterUpgradeMessage =
                new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "netty.io");
        final ChannelPromise promise = channel.newPromise();
        channel.pipeline().addFirst(new ChannelInboundHandlerAdapter() {
            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                if (evt == HttpClientUpgradeHandler.UpgradeEvent.UPGRADE_SUCCESSFUL) {
                    ctx.writeAndFlush(afterUpgradeMessage, promise);
                }
                super.userEventTriggered(ctx, evt);
            }
        });

        channel.pipeline().addFirst("upgrade", handler);

        assertTrue(
            channel.writeOutbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "netty.io")));
        FullHttpRequest request = channel.readOutbound();

        assertEquals(2, request.headers().size());
        assertTrue(request.headers().contains(HttpHeaderNames.UPGRADE, "fancyhttp", false));
        assertTrue(request.headers().contains("connection", "upgrade", false));
        assertTrue(request.release());
        assertEquals(HttpClientUpgradeHandler.UpgradeEvent.UPGRADE_ISSUED, catcher.getUserEvent());

        HttpResponse upgradeResponse =
            new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.SWITCHING_PROTOCOLS);

        upgradeResponse.headers().add(HttpHeaderNames.UPGRADE, "fancyhttp");
        assertFalse(channel.writeInbound(upgradeResponse));
        assertFalse(channel.writeInbound(LastHttpContent.EMPTY_LAST_CONTENT));

        assertEquals(HttpClientUpgradeHandler.UpgradeEvent.UPGRADE_SUCCESSFUL, catcher.getUserEvent());
        assertNull(channel.pipeline().get("upgrade"));

        assertTrue(channel.writeInbound(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)));
        FullHttpResponse response = channel.readInbound();
        assertEquals(HttpResponseStatus.OK, response.status());
        assertTrue(response.release());

        assertTrue(promise.isSuccess());
        assertEquals(afterUpgradeMessage, channel.readOutbound());

        assertFalse(channel.finish());
    }

    @Test
    public void testUpgradeRejected() {
        HttpClientUpgradeHandler.SourceCodec sourceCodec = new FakeSourceCodec();
        HttpClientUpgradeHandler.UpgradeCodec upgradeCodec = new FakeUpgradeCodec();
        HttpClientUpgradeHandler handler = new HttpClientUpgradeHandler(sourceCodec, upgradeCodec, 1024);
        UserEventCatcher catcher = new UserEventCatcher();
        EmbeddedChannel channel = new EmbeddedChannel(catcher);
        channel.pipeline().addFirst("upgrade", handler);

        assertTrue(
            channel.writeOutbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "netty.io")));
        FullHttpRequest request = channel.readOutbound();

        assertEquals(2, request.headers().size());
        assertTrue(request.headers().contains(HttpHeaderNames.UPGRADE, "fancyhttp", false));
        assertTrue(request.headers().contains("connection", "upgrade", false));
        assertTrue(request.release());
        assertEquals(HttpClientUpgradeHandler.UpgradeEvent.UPGRADE_ISSUED, catcher.getUserEvent());

        HttpResponse upgradeResponse =
            new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.SWITCHING_PROTOCOLS);
        upgradeResponse.headers().add(HttpHeaderNames.UPGRADE, "fancyhttp");
        assertTrue(channel.writeInbound(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)));
        assertTrue(channel.writeInbound(LastHttpContent.EMPTY_LAST_CONTENT));

        assertEquals(HttpClientUpgradeHandler.UpgradeEvent.UPGRADE_REJECTED, catcher.getUserEvent());
        assertNull(channel.pipeline().get("upgrade"));

        HttpResponse response = channel.readInbound();
        assertEquals(HttpResponseStatus.OK, response.status());

        LastHttpContent last = channel.readInbound();
        assertEquals(LastHttpContent.EMPTY_LAST_CONTENT, last);
        assertFalse(last.release());
        assertFalse(channel.finish());
    }

    @Test
    public void testEarlyBailout() {
        HttpClientUpgradeHandler.SourceCodec sourceCodec = new FakeSourceCodec();
        HttpClientUpgradeHandler.UpgradeCodec upgradeCodec = new FakeUpgradeCodec();
        HttpClientUpgradeHandler handler = new HttpClientUpgradeHandler(sourceCodec, upgradeCodec, 1024);
        UserEventCatcher catcher = new UserEventCatcher();
        EmbeddedChannel channel = new EmbeddedChannel(catcher);
        channel.pipeline().addFirst("upgrade", handler);

        assertTrue(
            channel.writeOutbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "netty.io")));
        FullHttpRequest request = channel.readOutbound();

        assertEquals(2, request.headers().size());
        assertTrue(request.headers().contains(HttpHeaderNames.UPGRADE, "fancyhttp", false));
        assertTrue(request.headers().contains("connection", "upgrade", false));
        assertTrue(request.release());
        assertEquals(HttpClientUpgradeHandler.UpgradeEvent.UPGRADE_ISSUED, catcher.getUserEvent());

        HttpResponse upgradeResponse =
            new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.SWITCHING_PROTOCOLS);
        upgradeResponse.headers().add(HttpHeaderNames.UPGRADE, "fancyhttp");
        assertTrue(channel.writeInbound(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)));

        assertEquals(HttpClientUpgradeHandler.UpgradeEvent.UPGRADE_REJECTED, catcher.getUserEvent());
        assertNull(channel.pipeline().get("upgrade"));

        HttpResponse response = channel.readInbound();
        assertEquals(HttpResponseStatus.OK, response.status());
        assertFalse(channel.finish());
    }

    @Test
    public void dontStripConnectionHeaders() {
        HttpClientUpgradeHandler.SourceCodec sourceCodec = new FakeSourceCodec();
        HttpClientUpgradeHandler.UpgradeCodec upgradeCodec = new FakeUpgradeCodec();
        HttpClientUpgradeHandler handler = new HttpClientUpgradeHandler(sourceCodec, upgradeCodec, 1024);
        UserEventCatcher catcher = new UserEventCatcher();
        EmbeddedChannel channel = new EmbeddedChannel(catcher);
        channel.pipeline().addFirst("upgrade", handler);

        DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "netty.io");
        request.headers().add("connection", "extra");
        request.headers().add("extra", "value");
        assertTrue(channel.writeOutbound(request));
        FullHttpRequest readRequest = channel.readOutbound();

        List<String> connectionHeaders = readRequest.headers().getAll("connection");
        assertTrue(connectionHeaders.contains("extra"));
        assertTrue(readRequest.release());
        assertFalse(channel.finish());
    }

    @Test
    public void testMultipleUpgradeRequestsFail() {
        HttpClientUpgradeHandler.SourceCodec sourceCodec = new FakeSourceCodec();
        HttpClientUpgradeHandler.UpgradeCodec upgradeCodec = new FakeUpgradeCodec();
        HttpClientUpgradeHandler handler = new HttpClientUpgradeHandler(sourceCodec, upgradeCodec, 1024);
        final EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addFirst("upgrade", handler);

        assertTrue(
                channel.writeOutbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "netty.io")));
        FullHttpRequest request = channel.readOutbound();
        assertTrue(request.release());

        final FullHttpRequest secondReq = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "netty.io");
        assertThrows(IllegalStateException.class, new Executable() {
                    @Override
                    public void execute() throws Throwable {
                        channel.writeOutbound(secondReq);
                    }
                });

        assertEquals(0, secondReq.refCnt());
        assertFalse(channel.finish());
    }
}
