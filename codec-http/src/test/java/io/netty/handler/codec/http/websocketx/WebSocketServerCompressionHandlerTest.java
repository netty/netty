/*
 * Copyright 2012 The Netty Project
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
package io.netty.handler.codec.http.websocketx;

import static io.netty.handler.codec.http.HttpHeaders.Values.WEBSOCKET;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static io.netty.handler.codec.http.websocketx.WebSocketServerCompressionHandler.*;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpHeaders.Names;
import io.netty.handler.codec.http.websocketx.WebSocketExtensionUtil.WebSocketExtensionData;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.ReferenceCountUtil;

import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

public class WebSocketServerCompressionHandlerTest {

    @Test
    public void testNormalSuccess() {
        EmbeddedChannel ch = new EmbeddedChannel(new WebSocketServerCompressionHandler());

        HttpRequest req = createUpgradeRequest(PERMESSAGE_DEFLATE_EXTENSION);
        ch.writeInbound(req);

        HttpResponse res = createUpgradeResponse(null);
        ch.writeOutbound(res);

        HttpResponse res2 = ch.readOutbound();
        List<WebSocketExtensionData> exts = WebSocketExtensionUtil.extractExtensions(
                res2.headers().get(Names.SEC_WEBSOCKET_EXTENSIONS));

        Assert.assertEquals(PERMESSAGE_DEFLATE_EXTENSION, exts.get(0).getName());
        Assert.assertTrue(exts.get(0).getParameters().isEmpty());
        Assert.assertTrue(ch.pipeline().get(WebSocketPermessageDeflateExtensionDecoder.class) != null);
        Assert.assertTrue(ch.pipeline().get(WebSocketPermessageDeflateExtensionEncoder.class) != null);
    }

    @Test
    public void testClientWindowSizeSuccess() {
        EmbeddedChannel ch = new EmbeddedChannel(new WebSocketServerCompressionHandler(6, false, 10));

        HttpRequest req = createUpgradeRequest(PERMESSAGE_DEFLATE_EXTENSION + "; " + CLIENT_MAX_WINDOW);
        ch.writeInbound(req);

        HttpResponse res = createUpgradeResponse(null);
        ch.writeOutbound(res);

        HttpResponse res2 = ch.readOutbound();
        List<WebSocketExtensionData> exts = WebSocketExtensionUtil.extractExtensions(
                res2.headers().get(Names.SEC_WEBSOCKET_EXTENSIONS));

        Assert.assertEquals(PERMESSAGE_DEFLATE_EXTENSION, exts.get(0).getName());
        Assert.assertEquals("10", exts.get(0).getParameters().get(CLIENT_MAX_WINDOW));
        Assert.assertTrue(ch.pipeline().get(WebSocketPermessageDeflateExtensionDecoder.class) != null);
        Assert.assertTrue(ch.pipeline().get(WebSocketPermessageDeflateExtensionEncoder.class) != null);
    }

    @Test
    public void testClientWindowSizeUnavailable() {
        EmbeddedChannel ch = new EmbeddedChannel(new WebSocketServerCompressionHandler(6, false, 10));

        HttpRequest req = createUpgradeRequest(PERMESSAGE_DEFLATE_EXTENSION);
        ch.writeInbound(req);

        HttpResponse res = createUpgradeResponse(null);
        ch.writeOutbound(res);

        HttpResponse res2 = ch.readOutbound();
        List<WebSocketExtensionData> exts = WebSocketExtensionUtil.extractExtensions(
                res2.headers().get(Names.SEC_WEBSOCKET_EXTENSIONS));

        Assert.assertEquals(PERMESSAGE_DEFLATE_EXTENSION, exts.get(0).getName());
        Assert.assertTrue(exts.get(0).getParameters().isEmpty());
        Assert.assertTrue(ch.pipeline().get(WebSocketPermessageDeflateExtensionDecoder.class) != null);
        Assert.assertTrue(ch.pipeline().get(WebSocketPermessageDeflateExtensionEncoder.class) != null);
    }

    @Test
    public void testServerWindowSizeSuccess() {
        EmbeddedChannel ch = new EmbeddedChannel(new WebSocketServerCompressionHandler(6, true, 15));

        HttpRequest req = createUpgradeRequest(PERMESSAGE_DEFLATE_EXTENSION + "; " + SERVER_MAX_WINDOW + "=10");
        ch.writeInbound(req);

        HttpResponse res = createUpgradeResponse(null);
        ch.writeOutbound(res);

        HttpResponse res2 = ch.readOutbound();
        List<WebSocketExtensionData> exts = WebSocketExtensionUtil.extractExtensions(
                res2.headers().get(Names.SEC_WEBSOCKET_EXTENSIONS));

        Assert.assertEquals(PERMESSAGE_DEFLATE_EXTENSION, exts.get(0).getName());
        Assert.assertEquals("10", exts.get(0).getParameters().get(SERVER_MAX_WINDOW));
        Assert.assertTrue(ch.pipeline().get(WebSocketPermessageDeflateExtensionDecoder.class) != null);
        Assert.assertTrue(ch.pipeline().get(WebSocketPermessageDeflateExtensionEncoder.class) != null);
    }

    @Test
    public void testServerWindowSizeDisable() {
        EmbeddedChannel ch = new EmbeddedChannel(new WebSocketServerCompressionHandler(6, false, 15));

        HttpRequest req = createUpgradeRequest(PERMESSAGE_DEFLATE_EXTENSION + "; " + SERVER_MAX_WINDOW + "=10");
        ch.writeInbound(req);

        HttpResponse res = createUpgradeResponse(null);
        ch.writeOutbound(res);

        HttpResponse res2 = ch.readOutbound();

        Assert.assertFalse(res2.headers().contains(Names.SEC_WEBSOCKET_EXTENSIONS));
        Assert.assertTrue(ch.pipeline().get(WebSocketPermessageDeflateExtensionDecoder.class) == null);
        Assert.assertTrue(ch.pipeline().get(WebSocketPermessageDeflateExtensionEncoder.class) == null);
    }

    @Test
    public void testServerNoContext() {
        EmbeddedChannel ch = new EmbeddedChannel(new WebSocketServerCompressionHandler());

        HttpRequest req = createUpgradeRequest(PERMESSAGE_DEFLATE_EXTENSION + "; " + SERVER_NO_CONTEXT);
        ch.writeInbound(req);

        HttpResponse res = createUpgradeResponse(null);
        ch.writeOutbound(res);

        HttpResponse res2 = ch.readOutbound();

        Assert.assertFalse(res2.headers().contains(Names.SEC_WEBSOCKET_EXTENSIONS));
        Assert.assertTrue(ch.pipeline().get(WebSocketPermessageDeflateExtensionDecoder.class) == null);
        Assert.assertTrue(ch.pipeline().get(WebSocketPermessageDeflateExtensionEncoder.class) == null);
    }

    @Test
    public void testClientNoContext() {
        EmbeddedChannel ch = new EmbeddedChannel(new WebSocketServerCompressionHandler());

        HttpRequest req = createUpgradeRequest(PERMESSAGE_DEFLATE_EXTENSION + "; " + CLIENT_NO_CONTEXT);
        ch.writeInbound(req);

        HttpResponse res = createUpgradeResponse(null);
        ch.writeOutbound(res);

        HttpResponse res2 = ch.readOutbound();
        List<WebSocketExtensionData> exts = WebSocketExtensionUtil.extractExtensions(
                res2.headers().get(Names.SEC_WEBSOCKET_EXTENSIONS));

        Assert.assertEquals(PERMESSAGE_DEFLATE_EXTENSION, exts.get(0).getName());
        Assert.assertTrue(exts.get(0).getParameters().isEmpty());
        Assert.assertTrue(ch.pipeline().get(WebSocketPermessageDeflateExtensionDecoder.class) != null);
        Assert.assertTrue(ch.pipeline().get(WebSocketPermessageDeflateExtensionEncoder.class) != null);
    }

    @Test
    public void testServerWindowSizeDisableThenFallback() {
        EmbeddedChannel ch = new EmbeddedChannel(new WebSocketServerCompressionHandler(6, false, 15));

        HttpRequest req = createUpgradeRequest(PERMESSAGE_DEFLATE_EXTENSION + "; " + SERVER_MAX_WINDOW + "=10, " +
                PERMESSAGE_DEFLATE_EXTENSION);
        ch.writeInbound(req);

        HttpResponse res = createUpgradeResponse(null);
        ch.writeOutbound(res);

        HttpResponse res2 = ch.readOutbound();
        List<WebSocketExtensionData> exts = WebSocketExtensionUtil.extractExtensions(
                res2.headers().get(Names.SEC_WEBSOCKET_EXTENSIONS));

        Assert.assertEquals(PERMESSAGE_DEFLATE_EXTENSION, exts.get(0).getName());
        Assert.assertTrue(exts.get(0).getParameters().isEmpty());
        Assert.assertTrue(ch.pipeline().get(WebSocketPermessageDeflateExtensionDecoder.class) != null);
        Assert.assertTrue(ch.pipeline().get(WebSocketPermessageDeflateExtensionEncoder.class) != null);
    }

    static HttpRequest createUpgradeRequest(String ext) {
        HttpRequest req = ReferenceCountUtil.releaseLater(new DefaultHttpRequest(
                HTTP_1_1, HttpMethod.GET, "/chat"));

        req.headers().set(Names.HOST, "server.example.com");
        req.headers().set(Names.UPGRADE, WEBSOCKET.toString().toLowerCase());
        req.headers().set(Names.CONNECTION, "Upgrade");
        req.headers().set(Names.ORIGIN, "http://example.com");
        if (ext != null) {
            req.headers().set(Names.SEC_WEBSOCKET_EXTENSIONS, ext);
        }

        return req;
    }

    static HttpResponse createUpgradeResponse(String ext) {
        HttpResponse res = ReferenceCountUtil.releaseLater(new DefaultHttpResponse(
                HTTP_1_1, HttpResponseStatus.SWITCHING_PROTOCOLS));

        res.headers().set(Names.HOST, "server.example.com");
        res.headers().set(Names.UPGRADE, WEBSOCKET.toString().toLowerCase());
        res.headers().set(Names.CONNECTION, "Upgrade");
        res.headers().set(Names.ORIGIN, "http://example.com");
        if (ext != null) {
            res.headers().set(Names.SEC_WEBSOCKET_EXTENSIONS, ext);
        }

        return res;
    }

}
