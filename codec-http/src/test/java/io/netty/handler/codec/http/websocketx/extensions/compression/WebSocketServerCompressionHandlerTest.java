/*
 * Copyright 2014 The Netty Project
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
package io.netty.handler.codec.http.websocketx.extensions.compression;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionData;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionUtil;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketServerExtensionHandler;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import static io.netty.handler.codec.http.websocketx.extensions.compression.
        PerMessageDeflateServerExtensionHandshaker.*;
import static io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionTestUtil.*;

public class WebSocketServerCompressionHandlerTest {

    @Test
    public void testNormalSuccess() {
        EmbeddedChannel ch = new EmbeddedChannel(new WebSocketServerCompressionHandler());

        HttpRequest req = newUpgradeRequest(PERMESSAGE_DEFLATE_EXTENSION);
        ch.writeInbound(req);

        HttpResponse res = newUpgradeResponse(null);
        ch.writeOutbound(res);

        HttpResponse res2 = ch.readOutbound();
        List<WebSocketExtensionData> exts = WebSocketExtensionUtil.extractExtensions(
                res2.headers().get(HttpHeaderNames.SEC_WEBSOCKET_EXTENSIONS));

        Assert.assertEquals(PERMESSAGE_DEFLATE_EXTENSION, exts.get(0).name());
        Assert.assertTrue(exts.get(0).parameters().isEmpty());
        Assert.assertNotNull(ch.pipeline().get(PerMessageDeflateDecoder.class));
        Assert.assertNotNull(ch.pipeline().get(PerMessageDeflateEncoder.class));
    }

    @Test
    public void testClientWindowSizeSuccess() {
        EmbeddedChannel ch = new EmbeddedChannel(new WebSocketServerExtensionHandler(
                new PerMessageDeflateServerExtensionHandshaker(6, false, 10, false, false)));

        HttpRequest req = newUpgradeRequest(PERMESSAGE_DEFLATE_EXTENSION + "; " + CLIENT_MAX_WINDOW);
        ch.writeInbound(req);

        HttpResponse res = newUpgradeResponse(null);
        ch.writeOutbound(res);

        HttpResponse res2 = ch.readOutbound();
        List<WebSocketExtensionData> exts = WebSocketExtensionUtil.extractExtensions(
                res2.headers().get(HttpHeaderNames.SEC_WEBSOCKET_EXTENSIONS));

        Assert.assertEquals(PERMESSAGE_DEFLATE_EXTENSION, exts.get(0).name());
        Assert.assertEquals("10", exts.get(0).parameters().get(CLIENT_MAX_WINDOW));
        Assert.assertNotNull(ch.pipeline().get(PerMessageDeflateDecoder.class));
        Assert.assertNotNull(ch.pipeline().get(PerMessageDeflateEncoder.class));
    }

    @Test
    public void testClientWindowSizeUnavailable() {
        EmbeddedChannel ch = new EmbeddedChannel(new WebSocketServerExtensionHandler(
                new PerMessageDeflateServerExtensionHandshaker(6, false, 10, false, false)));

        HttpRequest req = newUpgradeRequest(PERMESSAGE_DEFLATE_EXTENSION);
        ch.writeInbound(req);

        HttpResponse res = newUpgradeResponse(null);
        ch.writeOutbound(res);

        HttpResponse res2 = ch.readOutbound();
        List<WebSocketExtensionData> exts = WebSocketExtensionUtil.extractExtensions(
                res2.headers().get(HttpHeaderNames.SEC_WEBSOCKET_EXTENSIONS));

        Assert.assertEquals(PERMESSAGE_DEFLATE_EXTENSION, exts.get(0).name());
        Assert.assertTrue(exts.get(0).parameters().isEmpty());
        Assert.assertNotNull(ch.pipeline().get(PerMessageDeflateDecoder.class));
        Assert.assertNotNull(ch.pipeline().get(PerMessageDeflateEncoder.class));
    }

    @Test
    public void testServerWindowSizeSuccess() {
        EmbeddedChannel ch = new EmbeddedChannel(new WebSocketServerExtensionHandler(
                new PerMessageDeflateServerExtensionHandshaker(6, true, 15, false, false)));

        HttpRequest req = newUpgradeRequest(PERMESSAGE_DEFLATE_EXTENSION + "; " + SERVER_MAX_WINDOW + "=10");
        ch.writeInbound(req);

        HttpResponse res = newUpgradeResponse(null);
        ch.writeOutbound(res);

        HttpResponse res2 = ch.readOutbound();
        List<WebSocketExtensionData> exts = WebSocketExtensionUtil.extractExtensions(
                res2.headers().get(HttpHeaderNames.SEC_WEBSOCKET_EXTENSIONS));

        Assert.assertEquals(PERMESSAGE_DEFLATE_EXTENSION, exts.get(0).name());
        Assert.assertEquals("10", exts.get(0).parameters().get(SERVER_MAX_WINDOW));
        Assert.assertNotNull(ch.pipeline().get(PerMessageDeflateDecoder.class));
        Assert.assertNotNull(ch.pipeline().get(PerMessageDeflateEncoder.class));
    }

    @Test
    public void testServerWindowSizeDisable() {
        EmbeddedChannel ch = new EmbeddedChannel(new WebSocketServerExtensionHandler(
                new PerMessageDeflateServerExtensionHandshaker(6, false, 15, false, false)));

        HttpRequest req = newUpgradeRequest(PERMESSAGE_DEFLATE_EXTENSION + "; " + SERVER_MAX_WINDOW + "=10");
        ch.writeInbound(req);

        HttpResponse res = newUpgradeResponse(null);
        ch.writeOutbound(res);

        HttpResponse res2 = ch.readOutbound();

        Assert.assertFalse(res2.headers().contains(HttpHeaderNames.SEC_WEBSOCKET_EXTENSIONS));
        Assert.assertNull(ch.pipeline().get(PerMessageDeflateDecoder.class));
        Assert.assertNull(ch.pipeline().get(PerMessageDeflateEncoder.class));
    }

    @Test
    public void testServerNoContext() {
        EmbeddedChannel ch = new EmbeddedChannel(new WebSocketServerCompressionHandler());

        HttpRequest req = newUpgradeRequest(PERMESSAGE_DEFLATE_EXTENSION + "; " + SERVER_NO_CONTEXT);
        ch.writeInbound(req);

        HttpResponse res = newUpgradeResponse(null);
        ch.writeOutbound(res);

        HttpResponse res2 = ch.readOutbound();

        Assert.assertFalse(res2.headers().contains(HttpHeaderNames.SEC_WEBSOCKET_EXTENSIONS));
        Assert.assertNull(ch.pipeline().get(PerMessageDeflateDecoder.class));
        Assert.assertNull(ch.pipeline().get(PerMessageDeflateEncoder.class));
    }

    @Test
    public void testClientNoContext() {
        EmbeddedChannel ch = new EmbeddedChannel(new WebSocketServerCompressionHandler());

        HttpRequest req = newUpgradeRequest(PERMESSAGE_DEFLATE_EXTENSION + "; " + CLIENT_NO_CONTEXT);
        ch.writeInbound(req);

        HttpResponse res = newUpgradeResponse(null);
        ch.writeOutbound(res);

        HttpResponse res2 = ch.readOutbound();
        List<WebSocketExtensionData> exts = WebSocketExtensionUtil.extractExtensions(
                res2.headers().get(HttpHeaderNames.SEC_WEBSOCKET_EXTENSIONS));

        Assert.assertEquals(PERMESSAGE_DEFLATE_EXTENSION, exts.get(0).name());
        Assert.assertTrue(exts.get(0).parameters().isEmpty());
        Assert.assertNotNull(ch.pipeline().get(PerMessageDeflateDecoder.class));
        Assert.assertNotNull(ch.pipeline().get(PerMessageDeflateEncoder.class));
    }

    @Test
    public void testServerWindowSizeDisableThenFallback() {
        EmbeddedChannel ch = new EmbeddedChannel(new WebSocketServerExtensionHandler(
                new PerMessageDeflateServerExtensionHandshaker(6, false, 15, false, false)));

        HttpRequest req = newUpgradeRequest(PERMESSAGE_DEFLATE_EXTENSION + "; " + SERVER_MAX_WINDOW + "=10, " +
                PERMESSAGE_DEFLATE_EXTENSION);
        ch.writeInbound(req);

        HttpResponse res = newUpgradeResponse(null);
        ch.writeOutbound(res);

        HttpResponse res2 = ch.readOutbound();
        List<WebSocketExtensionData> exts = WebSocketExtensionUtil.extractExtensions(
                res2.headers().get(HttpHeaderNames.SEC_WEBSOCKET_EXTENSIONS));

        Assert.assertEquals(PERMESSAGE_DEFLATE_EXTENSION, exts.get(0).name());
        Assert.assertTrue(exts.get(0).parameters().isEmpty());
        Assert.assertNotNull(ch.pipeline().get(PerMessageDeflateDecoder.class));
        Assert.assertNotNull(ch.pipeline().get(PerMessageDeflateEncoder.class));
    }

}
