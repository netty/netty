/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
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
import io.netty.channel.embedded.EmbeddedChannel;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
        channel.pipeline().addFirst("upgrade", handler);

        assertTrue(
            channel.writeOutbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "netty.io")));
        FullHttpRequest request = channel.readOutbound();

        assertEquals(request.headers().size(), 2);
        assertTrue(request.headers().contains(HttpHeaderNames.UPGRADE, "fancyhttp", false));
        assertTrue(request.headers().contains("connection", "upgrade", false));
        assertTrue(request.release());
        assertEquals(catcher.getUserEvent(), HttpClientUpgradeHandler.UpgradeEvent.UPGRADE_ISSUED);

        HttpResponse upgradeResponse =
            new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.SWITCHING_PROTOCOLS);

        upgradeResponse.headers().add(HttpHeaderNames.UPGRADE, "fancyhttp");
        assertFalse(channel.writeInbound(upgradeResponse));
        assertFalse(channel.writeInbound(LastHttpContent.EMPTY_LAST_CONTENT));

        assertEquals(catcher.getUserEvent(), HttpClientUpgradeHandler.UpgradeEvent.UPGRADE_SUCCESSFUL);
        assertNull(channel.pipeline().get("upgrade"));

        assertTrue(channel.writeInbound(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)));
        FullHttpResponse response = channel.readInbound();
        assertEquals(response.status(), HttpResponseStatus.OK);
        assertTrue(response.release());
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

        assertEquals(request.headers().size(), 2);
        assertTrue(request.headers().contains(HttpHeaderNames.UPGRADE, "fancyhttp", false));
        assertTrue(request.headers().contains("connection", "upgrade", false));
        assertTrue(request.release());
        assertEquals(catcher.getUserEvent(), HttpClientUpgradeHandler.UpgradeEvent.UPGRADE_ISSUED);

        HttpResponse upgradeResponse =
            new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.SWITCHING_PROTOCOLS);
        upgradeResponse.headers().add(HttpHeaderNames.UPGRADE, "fancyhttp");
        assertTrue(channel.writeInbound(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)));
        assertTrue(channel.writeInbound(LastHttpContent.EMPTY_LAST_CONTENT));

        assertEquals(catcher.getUserEvent(), HttpClientUpgradeHandler.UpgradeEvent.UPGRADE_REJECTED);
        assertNull(channel.pipeline().get("upgrade"));

        HttpResponse response = channel.readInbound();
        assertEquals(response.status(), HttpResponseStatus.OK);

        LastHttpContent last = channel.readInbound();
        assertEquals(last, LastHttpContent.EMPTY_LAST_CONTENT);
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

        assertEquals(request.headers().size(), 2);
        assertTrue(request.headers().contains(HttpHeaderNames.UPGRADE, "fancyhttp", false));
        assertTrue(request.headers().contains("connection", "upgrade", false));
        assertTrue(request.release());
        assertEquals(catcher.getUserEvent(), HttpClientUpgradeHandler.UpgradeEvent.UPGRADE_ISSUED);

        HttpResponse upgradeResponse =
            new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.SWITCHING_PROTOCOLS);
        upgradeResponse.headers().add(HttpHeaderNames.UPGRADE, "fancyhttp");
        assertTrue(channel.writeInbound(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)));

        assertEquals(catcher.getUserEvent(), HttpClientUpgradeHandler.UpgradeEvent.UPGRADE_REJECTED);
        assertNull(channel.pipeline().get("upgrade"));

        HttpResponse response = channel.readInbound();
        assertEquals(response.status(), HttpResponseStatus.OK);
        assertFalse(channel.finish());
    }
}
