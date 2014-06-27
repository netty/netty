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
package io.netty.handler.codec.http.websocketx.extensions;

import static io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionTestUtil.*;
import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.CodecException;
import io.netty.handler.codec.http.HttpHeaders.Names;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;

import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class WebSocketClientExtensionHandlerTest {

    WebSocketClientExtensionHandshaker mainHandshakerMock =
            createMock("mainHandshaker", WebSocketClientExtensionHandshaker.class);
    WebSocketClientExtensionHandshaker fallbackHandshakerMock =
            createMock("fallbackHandshaker", WebSocketClientExtensionHandshaker.class);
    WebSocketClientExtension mainExtensionMock =
            createMock("mainExtension", WebSocketClientExtension.class);
    WebSocketClientExtension fallbackExtensionMock =
            createMock("fallbackExtension", WebSocketClientExtension.class);

    @Test
    public void testMainSuccess() {
        // initialize
        expect(mainHandshakerMock.newRequestData()).
                andReturn(new WebSocketExtensionData("main", Collections.<String, String>emptyMap())).once();
        expect(mainHandshakerMock.handshakeExtension(
                anyObject(WebSocketExtensionData.class))).andReturn(mainExtensionMock).once();
        replay(mainHandshakerMock);

        expect(fallbackHandshakerMock.newRequestData()).
                andReturn(new WebSocketExtensionData("fallback", Collections.<String, String>emptyMap())).once();
        replay(fallbackHandshakerMock);

        expect(mainExtensionMock.rsv()).andReturn(WebSocketExtension.RSV1).anyTimes();
        expect(mainExtensionMock.createExtensionEncoder()).andReturn(new DummyEncoder()).once();
        expect(mainExtensionMock.createExtensionDecoder()).andReturn(new DummyDecoder()).once();
        replay(mainExtensionMock);

        // execute
        EmbeddedChannel ch = new EmbeddedChannel(new WebSocketClientExtensionHandler(
                mainHandshakerMock, fallbackHandshakerMock));

        HttpRequest req = createUpgradeRequest(null);
        ch.writeOutbound(req);

        HttpRequest req2 = ch.readOutbound();
        List<WebSocketExtensionData> reqExts = WebSocketExtensionUtil.extractExtensions(
                req2.headers().get(Names.SEC_WEBSOCKET_EXTENSIONS));

        HttpResponse res = createUpgradeResponse("main");
        ch.writeInbound(res);

        HttpResponse res2 = ch.readInbound();
        List<WebSocketExtensionData> resExts = WebSocketExtensionUtil.extractExtensions(
                res2.headers().get(Names.SEC_WEBSOCKET_EXTENSIONS));

        // test
        assertEquals(2, reqExts.size());
        assertEquals("main", reqExts.get(0).getName());
        assertEquals("fallback", reqExts.get(1).getName());

        assertEquals(1, resExts.size());
        assertEquals("main", resExts.get(0).getName());
        assertTrue(resExts.get(0).getParameters().isEmpty());
        assertTrue(ch.pipeline().get(DummyDecoder.class) != null);
        assertTrue(ch.pipeline().get(DummyEncoder.class) != null);
    }

    @Test
    public void testFallbackSuccess() {
        // initialize
        expect(mainHandshakerMock.newRequestData()).
                andReturn(new WebSocketExtensionData("main", Collections.<String, String>emptyMap())).once();
        expect(mainHandshakerMock.handshakeExtension(
                anyObject(WebSocketExtensionData.class))).andReturn(null).once();
        replay(mainHandshakerMock);

        expect(fallbackHandshakerMock.newRequestData()).
                andReturn(new WebSocketExtensionData("fallback", Collections.<String, String>emptyMap())).once();
        expect(fallbackHandshakerMock.handshakeExtension(
                anyObject(WebSocketExtensionData.class))).andReturn(fallbackExtensionMock).once();
        replay(fallbackHandshakerMock);

        expect(fallbackExtensionMock.rsv()).andReturn(WebSocketExtension.RSV1).anyTimes();
        expect(fallbackExtensionMock.createExtensionEncoder()).andReturn(new DummyEncoder()).once();
        expect(fallbackExtensionMock.createExtensionDecoder()).andReturn(new DummyDecoder()).once();
        replay(fallbackExtensionMock);

        // execute
        EmbeddedChannel ch = new EmbeddedChannel(new WebSocketClientExtensionHandler(
                mainHandshakerMock, fallbackHandshakerMock));

        HttpRequest req = createUpgradeRequest(null);
        ch.writeOutbound(req);

        HttpRequest req2 = ch.readOutbound();
        List<WebSocketExtensionData> reqExts = WebSocketExtensionUtil.extractExtensions(
                req2.headers().get(Names.SEC_WEBSOCKET_EXTENSIONS));

        HttpResponse res = createUpgradeResponse("fallback");
        ch.writeInbound(res);

        HttpResponse res2 = ch.readInbound();
        List<WebSocketExtensionData> resExts = WebSocketExtensionUtil.extractExtensions(
                res2.headers().get(Names.SEC_WEBSOCKET_EXTENSIONS));

        // test
        assertEquals(2, reqExts.size());
        assertEquals("main", reqExts.get(0).getName());
        assertEquals("fallback", reqExts.get(1).getName());

        assertEquals(1, resExts.size());
        assertEquals("fallback", resExts.get(0).getName());
        assertTrue(resExts.get(0).getParameters().isEmpty());
        assertTrue(ch.pipeline().get(DummyDecoder.class) != null);
        assertTrue(ch.pipeline().get(DummyEncoder.class) != null);
    }

    @Test
    public void testAllSuccess() {
        // initialize
        expect(mainHandshakerMock.newRequestData()).
                andReturn(new WebSocketExtensionData("main", Collections.<String, String>emptyMap())).once();
        expect(mainHandshakerMock.handshakeExtension(
                webSocketExtensionDataEqual("main"))).andReturn(mainExtensionMock).anyTimes();
        expect(mainHandshakerMock.handshakeExtension(
                webSocketExtensionDataEqual("fallback"))).andReturn(null).anyTimes();
        replay(mainHandshakerMock);

        expect(fallbackHandshakerMock.newRequestData()).
                andReturn(new WebSocketExtensionData("fallback", Collections.<String, String>emptyMap())).once();
        expect(fallbackHandshakerMock.handshakeExtension(
                webSocketExtensionDataEqual("main"))).andReturn(null).anyTimes();
        expect(fallbackHandshakerMock.handshakeExtension(
                webSocketExtensionDataEqual("fallback"))).andReturn(fallbackExtensionMock).anyTimes();
        replay(fallbackHandshakerMock);

        DummyEncoder mainEncoder = new DummyEncoder();
        DummyDecoder mainDecoder = new DummyDecoder();
        expect(mainExtensionMock.rsv()).andReturn(WebSocketExtension.RSV1).anyTimes();
        expect(mainExtensionMock.createExtensionEncoder()).andReturn(mainEncoder).once();
        expect(mainExtensionMock.createExtensionDecoder()).andReturn(mainDecoder).once();
        replay(mainExtensionMock);

        Dummy2Encoder fallbackEncoder = new Dummy2Encoder();
        Dummy2Decoder fallbackDecoder = new Dummy2Decoder();
        expect(fallbackExtensionMock.rsv()).andReturn(WebSocketExtension.RSV2).anyTimes();
        expect(fallbackExtensionMock.createExtensionEncoder()).andReturn(fallbackEncoder).once();
        expect(fallbackExtensionMock.createExtensionDecoder()).andReturn(fallbackDecoder).once();
        replay(fallbackExtensionMock);

        // execute
        EmbeddedChannel ch = new EmbeddedChannel(new WebSocketClientExtensionHandler(
                mainHandshakerMock, fallbackHandshakerMock));

        HttpRequest req = createUpgradeRequest(null);
        ch.writeOutbound(req);

        HttpRequest req2 = ch.readOutbound();
        List<WebSocketExtensionData> reqExts = WebSocketExtensionUtil.extractExtensions(
                req2.headers().get(Names.SEC_WEBSOCKET_EXTENSIONS));

        HttpResponse res = createUpgradeResponse("main, fallback");
        ch.writeInbound(res);

        HttpResponse res2 = ch.readInbound();
        List<WebSocketExtensionData> resExts = WebSocketExtensionUtil.extractExtensions(
                res2.headers().get(Names.SEC_WEBSOCKET_EXTENSIONS));

        // test
        assertEquals(2, reqExts.size());
        assertEquals("main", reqExts.get(0).getName());
        assertEquals("fallback", reqExts.get(1).getName());

        assertEquals(2, resExts.size());
        assertEquals("main", resExts.get(0).getName());
        assertEquals("fallback", resExts.get(1).getName());
        assertTrue(ch.pipeline().context(mainEncoder) != null);
        assertTrue(ch.pipeline().context(mainDecoder) != null);
        assertTrue(ch.pipeline().context(fallbackEncoder) != null);
        assertTrue(ch.pipeline().context(fallbackDecoder) != null);
    }

    @Test(expected = CodecException.class)
    public void testIfMainAndFallbackUseRSV1WillFail() {
        // initialize
        expect(mainHandshakerMock.newRequestData()).
                andReturn(new WebSocketExtensionData("main", Collections.<String, String>emptyMap())).once();
        expect(mainHandshakerMock.handshakeExtension(
                webSocketExtensionDataEqual("main"))).andReturn(mainExtensionMock).anyTimes();
        expect(mainHandshakerMock.handshakeExtension(
                webSocketExtensionDataEqual("fallback"))).andReturn(null).anyTimes();
        replay(mainHandshakerMock);

        expect(fallbackHandshakerMock.newRequestData()).
                andReturn(new WebSocketExtensionData("fallback", Collections.<String, String>emptyMap())).once();
        expect(fallbackHandshakerMock.handshakeExtension(
                webSocketExtensionDataEqual("main"))).andReturn(null).anyTimes();
        expect(fallbackHandshakerMock.handshakeExtension(
                webSocketExtensionDataEqual("fallback"))).andReturn(fallbackExtensionMock).anyTimes();
        replay(fallbackHandshakerMock);

        expect(mainExtensionMock.rsv()).andReturn(WebSocketExtension.RSV1).anyTimes();
        replay(mainExtensionMock);

        expect(fallbackExtensionMock.rsv()).andReturn(WebSocketExtension.RSV1).anyTimes();
        replay(fallbackExtensionMock);

        // execute
        EmbeddedChannel ch = new EmbeddedChannel(new WebSocketClientExtensionHandler(
                mainHandshakerMock, fallbackHandshakerMock));

        HttpRequest req = createUpgradeRequest(null);
        ch.writeOutbound(req);

        HttpRequest req2 = ch.readOutbound();
        List<WebSocketExtensionData> reqExts = WebSocketExtensionUtil.extractExtensions(
                req2.headers().get(Names.SEC_WEBSOCKET_EXTENSIONS));

        HttpResponse res = createUpgradeResponse("main, fallback");
        ch.writeInbound(res);

        // test
        assertEquals(2, reqExts.size());
        assertEquals("main", reqExts.get(0).getName());
        assertEquals("fallback", reqExts.get(1).getName());
    }

}
