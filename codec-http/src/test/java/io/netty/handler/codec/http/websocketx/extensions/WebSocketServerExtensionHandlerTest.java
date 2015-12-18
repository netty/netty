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

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;

import java.util.Collections;
import java.util.List;

import org.junit.Test;

import static io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionTestUtil.*;
import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

public class WebSocketServerExtensionHandlerTest {

    WebSocketServerExtensionHandshaker mainHandshakerMock =
            createMock("mainHandshaker", WebSocketServerExtensionHandshaker.class);
    WebSocketServerExtensionHandshaker fallbackHandshakerMock =
            createMock("fallbackHandshaker", WebSocketServerExtensionHandshaker.class);
    WebSocketServerExtension mainExtensionMock =
            createMock("mainExtension", WebSocketServerExtension.class);
    WebSocketServerExtension fallbackExtensionMock =
            createMock("fallbackExtension", WebSocketServerExtension.class);

    @Test
    public void testMainSuccess() {
        // initialize
        expect(mainHandshakerMock.handshakeExtension(webSocketExtensionDataEqual("main"))).
                andReturn(mainExtensionMock).anyTimes();
        expect(mainHandshakerMock.handshakeExtension(webSocketExtensionDataEqual("fallback"))).
                andReturn(null).anyTimes();
        replay(mainHandshakerMock);

        expect(fallbackHandshakerMock.handshakeExtension(webSocketExtensionDataEqual("fallback"))).
                andReturn(fallbackExtensionMock).anyTimes();
        expect(fallbackHandshakerMock.handshakeExtension(webSocketExtensionDataEqual("main"))).
                andReturn(null).anyTimes();
        replay(fallbackHandshakerMock);

        expect(mainExtensionMock.rsv()).andReturn(WebSocketExtension.RSV1).anyTimes();
        expect(mainExtensionMock.newReponseData()).andReturn(
                new WebSocketExtensionData("main", Collections.<String, String>emptyMap())).once();
        expect(mainExtensionMock.newExtensionEncoder()).andReturn(new DummyEncoder()).once();
        expect(mainExtensionMock.newExtensionDecoder()).andReturn(new DummyDecoder()).once();
        replay(mainExtensionMock);

        expect(fallbackExtensionMock.rsv()).andReturn(WebSocketExtension.RSV1).anyTimes();
        replay(fallbackExtensionMock);

        // execute
        EmbeddedChannel ch = new EmbeddedChannel(new WebSocketServerExtensionHandler(
                mainHandshakerMock, fallbackHandshakerMock));

        HttpRequest req = newUpgradeRequest("main, fallback");
        ch.writeInbound(req);

        HttpResponse res = newUpgradeResponse(null);
        ch.writeOutbound(res);

        HttpResponse res2 = ch.readOutbound();
        List<WebSocketExtensionData> resExts = WebSocketExtensionUtil.extractExtensions(
                res2.headers().get(HttpHeaderNames.SEC_WEBSOCKET_EXTENSIONS));

        // test
        assertEquals(1, resExts.size());
        assertEquals("main", resExts.get(0).name());
        assertTrue(resExts.get(0).parameters().isEmpty());
        assertTrue(ch.pipeline().get(DummyDecoder.class) != null);
        assertTrue(ch.pipeline().get(DummyEncoder.class) != null);
    }

    @Test
    public void testCompatibleExtensionTogetherSuccess() {
        // initialize
        expect(mainHandshakerMock.handshakeExtension(webSocketExtensionDataEqual("main"))).
                andReturn(mainExtensionMock).anyTimes();
        expect(mainHandshakerMock.handshakeExtension(webSocketExtensionDataEqual("fallback"))).
                andReturn(null).anyTimes();
        replay(mainHandshakerMock);

        expect(fallbackHandshakerMock.handshakeExtension(webSocketExtensionDataEqual("fallback"))).
                andReturn(fallbackExtensionMock).anyTimes();
        expect(fallbackHandshakerMock.handshakeExtension(webSocketExtensionDataEqual("main"))).
                andReturn(null).anyTimes();
        replay(fallbackHandshakerMock);

        expect(mainExtensionMock.rsv()).andReturn(WebSocketExtension.RSV1).anyTimes();
        expect(mainExtensionMock.newReponseData()).andReturn(
                new WebSocketExtensionData("main", Collections.<String, String>emptyMap())).once();
        expect(mainExtensionMock.newExtensionEncoder()).andReturn(new DummyEncoder()).once();
        expect(mainExtensionMock.newExtensionDecoder()).andReturn(new DummyDecoder()).once();
        replay(mainExtensionMock);

        expect(fallbackExtensionMock.rsv()).andReturn(WebSocketExtension.RSV2).anyTimes();
        expect(fallbackExtensionMock.newReponseData()).andReturn(
                new WebSocketExtensionData("fallback", Collections.<String, String>emptyMap())).once();
        expect(fallbackExtensionMock.newExtensionEncoder()).andReturn(new Dummy2Encoder()).once();
        expect(fallbackExtensionMock.newExtensionDecoder()).andReturn(new Dummy2Decoder()).once();
        replay(fallbackExtensionMock);

        // execute
        EmbeddedChannel ch = new EmbeddedChannel(new WebSocketServerExtensionHandler(
                mainHandshakerMock, fallbackHandshakerMock));

        HttpRequest req = newUpgradeRequest("main, fallback");
        ch.writeInbound(req);

        HttpResponse res = newUpgradeResponse(null);
        ch.writeOutbound(res);

        HttpResponse res2 = ch.readOutbound();
        List<WebSocketExtensionData> resExts = WebSocketExtensionUtil.extractExtensions(
                res2.headers().get(HttpHeaderNames.SEC_WEBSOCKET_EXTENSIONS));

        // test
        assertEquals(2, resExts.size());
        assertEquals("main", resExts.get(0).name());
        assertEquals("fallback", resExts.get(1).name());
        assertTrue(ch.pipeline().get(DummyDecoder.class) != null);
        assertTrue(ch.pipeline().get(DummyEncoder.class) != null);
        assertTrue(ch.pipeline().get(Dummy2Decoder.class) != null);
        assertTrue(ch.pipeline().get(Dummy2Encoder.class) != null);
    }

    @Test
    public void testNoneExtensionMatchingSuccess() {
        // initialize
        expect(mainHandshakerMock.handshakeExtension(webSocketExtensionDataEqual("unknown"))).
                andReturn(null).anyTimes();
        expect(mainHandshakerMock.handshakeExtension(webSocketExtensionDataEqual("unknown2"))).
                andReturn(null).anyTimes();
        replay(mainHandshakerMock);

        expect(fallbackHandshakerMock.handshakeExtension(webSocketExtensionDataEqual("unknown"))).
                andReturn(null).anyTimes();
        expect(fallbackHandshakerMock.handshakeExtension(webSocketExtensionDataEqual("unknown2"))).
                andReturn(null).anyTimes();
        replay(fallbackHandshakerMock);

        // execute
        EmbeddedChannel ch = new EmbeddedChannel(new WebSocketServerExtensionHandler(
                mainHandshakerMock, fallbackHandshakerMock));

        HttpRequest req = newUpgradeRequest("unknown, unknown2");
        ch.writeInbound(req);

        HttpResponse res = newUpgradeResponse(null);
        ch.writeOutbound(res);

        HttpResponse res2 = ch.readOutbound();

        // test
        assertFalse(res2.headers().contains(HttpHeaderNames.SEC_WEBSOCKET_EXTENSIONS));
    }

}
