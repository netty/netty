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


public class WebSocketClientCompressionHandlerTest {

//    @Test
//    public void testNormalSuccess() {
//        EmbeddedChannel ch = new EmbeddedChannel(new WebSocketClientCompressionHandler());
//
//        HttpRequest req = createUpgradeRequest(null);
//        ch.writeOutbound(req);
//
//        HttpRequest req2 = ch.readOutbound();
//        List<WebSocketExtensionData> reqExts = WebSocketExtensionUtil.extractExtensions(
//                req2.headers().get(Names.SEC_WEBSOCKET_EXTENSIONS));
//
//        Assert.assertEquals(PERMESSAGE_DEFLATE_EXTENSION, reqExts.get(0).getName());
//        Assert.assertTrue(reqExts.get(0).getParameters().isEmpty());
//
//        HttpResponse res = createUpgradeResponse(PERMESSAGE_DEFLATE_EXTENSION);
//        ch.writeInbound(res);
//
//        HttpResponse res2 = ch.readInbound();
//        List<WebSocketExtensionData> resExts = WebSocketExtensionUtil.extractExtensions(
//                res2.headers().get(Names.SEC_WEBSOCKET_EXTENSIONS));
//
//        Assert.assertEquals(PERMESSAGE_DEFLATE_EXTENSION, resExts.get(0).getName());
//        Assert.assertTrue(resExts.get(0).getParameters().isEmpty());
//        Assert.assertTrue(ch.pipeline().get(PermessageDeflateDecoder.class) != null);
//        Assert.assertTrue(ch.pipeline().get(PermessageDeflateExtensionEncoder.class) != null);
//    }
//
//    @Test
//    public void testServerWindowSizeSuccess() {
//        EmbeddedChannel ch = new EmbeddedChannel(new WebSocketClientCompressionHandler(6, false, 10));
//
//        HttpRequest req = createUpgradeRequest(null);
//        ch.writeOutbound(req);
//
//        HttpRequest req2 = ch.readOutbound();
//        List<WebSocketExtensionData> reqExts = WebSocketExtensionUtil.extractExtensions(
//                req2.headers().get(Names.SEC_WEBSOCKET_EXTENSIONS));
//
//        Assert.assertEquals(PERMESSAGE_DEFLATE_EXTENSION, reqExts.get(0).getName());
//        Assert.assertTrue(reqExts.get(0).getParameters().containsKey(SERVER_MAX_WINDOW));
//
//        HttpResponse res = createUpgradeResponse(PERMESSAGE_DEFLATE_EXTENSION + "; " + SERVER_MAX_WINDOW + "=10");
//        ch.writeInbound(res);
//
//        Assert.assertTrue(ch.pipeline().get(PermessageDeflateDecoder.class) != null);
//        Assert.assertTrue(ch.pipeline().get(PermessageDeflateExtensionEncoder.class) != null);
//    }
//
//    @Test
//    public void testAvailableClientWindowSizeSuccess() {
//        EmbeddedChannel ch = new EmbeddedChannel(new WebSocketClientCompressionHandler(6, true, 15));
//
//        HttpRequest req = createUpgradeRequest(null);
//        ch.writeOutbound(req);
//
//        HttpRequest req2 = ch.readOutbound();
//        List<WebSocketExtensionData> reqExts = WebSocketExtensionUtil.extractExtensions(
//                req2.headers().get(Names.SEC_WEBSOCKET_EXTENSIONS));
//
//        Assert.assertEquals(PERMESSAGE_DEFLATE_EXTENSION, reqExts.get(0).getName());
//        Assert.assertTrue(reqExts.get(0).getParameters().containsKey(CLIENT_MAX_WINDOW));
//
//        HttpResponse res = createUpgradeResponse(PERMESSAGE_DEFLATE_EXTENSION + "; " + CLIENT_MAX_WINDOW + "=10");
//        ch.writeInbound(res);
//
//        Assert.assertTrue(ch.pipeline().get(PermessageDeflateDecoder.class) != null);
//        Assert.assertTrue(ch.pipeline().get(PermessageDeflateExtensionEncoder.class) != null);
//    }
//
//    @Test
//    public void testUnavailableClientWindowSizeSuccess() {
//        EmbeddedChannel ch = new EmbeddedChannel(new WebSocketClientCompressionHandler(6, true, 15));
//
//        HttpRequest req = createUpgradeRequest(null);
//        ch.writeOutbound(req);
//
//        HttpRequest req2 = ch.readOutbound();
//        List<WebSocketExtensionData> reqExts = WebSocketExtensionUtil.extractExtensions(
//                req2.headers().get(Names.SEC_WEBSOCKET_EXTENSIONS));
//
//        Assert.assertEquals(PERMESSAGE_DEFLATE_EXTENSION, reqExts.get(0).getName());
//        Assert.assertTrue(reqExts.get(0).getParameters().containsKey(CLIENT_MAX_WINDOW));
//
//        HttpResponse res = createUpgradeResponse(PERMESSAGE_DEFLATE_EXTENSION);
//        ch.writeInbound(res);
//
//        Assert.assertTrue(ch.pipeline().get(PermessageDeflateDecoder.class) != null);
//        Assert.assertTrue(ch.pipeline().get(PermessageDeflateExtensionEncoder.class) != null);
//    }

}
