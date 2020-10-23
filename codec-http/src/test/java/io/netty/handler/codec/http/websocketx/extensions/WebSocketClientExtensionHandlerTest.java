/*
 * Copyright 2014 The Netty Project
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
package io.netty.handler.codec.http.websocketx.extensions;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.CodecException;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;

import java.util.Collections;
import java.util.List;

import org.junit.Test;

import static io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionTestUtil.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class WebSocketClientExtensionHandlerTest {

    WebSocketClientExtensionHandshaker mainHandshakerMock =
            mock(WebSocketClientExtensionHandshaker.class, "mainHandshaker");
    WebSocketClientExtensionHandshaker fallbackHandshakerMock =
            mock(WebSocketClientExtensionHandshaker.class, "fallbackHandshaker");
    WebSocketClientExtension mainExtensionMock =
            mock(WebSocketClientExtension.class, "mainExtension");
    WebSocketClientExtension fallbackExtensionMock =
            mock(WebSocketClientExtension.class, "fallbackExtension");

    @Test
    public void testMainSuccess() {
        // initialize
        when(mainHandshakerMock.newRequestData()).
                thenReturn(new WebSocketExtensionData("main", Collections.<String, String>emptyMap()));
        when(mainHandshakerMock.handshakeExtension(any(WebSocketExtensionData.class))).thenReturn(mainExtensionMock);
        when(fallbackHandshakerMock.newRequestData()).
                thenReturn(new WebSocketExtensionData("fallback", Collections.<String, String>emptyMap()));
        when(mainExtensionMock.rsv()).thenReturn(WebSocketExtension.RSV1);
        when(mainExtensionMock.newExtensionEncoder()).thenReturn(new DummyEncoder());
        when(mainExtensionMock.newExtensionDecoder()).thenReturn(new DummyDecoder());

        // execute
        EmbeddedChannel ch = new EmbeddedChannel(new WebSocketClientExtensionHandler(
                mainHandshakerMock, fallbackHandshakerMock));

        HttpRequest req = newUpgradeRequest(null);
        ch.writeOutbound(req);

        HttpRequest req2 = ch.readOutbound();
        List<WebSocketExtensionData> reqExts = WebSocketExtensionUtil.extractExtensions(
                req2.headers().get(HttpHeaderNames.SEC_WEBSOCKET_EXTENSIONS));

        HttpResponse res = newUpgradeResponse("main");
        ch.writeInbound(res);

        HttpResponse res2 = ch.readInbound();
        List<WebSocketExtensionData> resExts = WebSocketExtensionUtil.extractExtensions(
                res2.headers().get(HttpHeaderNames.SEC_WEBSOCKET_EXTENSIONS));

        // test
        assertEquals(2, reqExts.size());
        assertEquals("main", reqExts.get(0).name());
        assertEquals("fallback", reqExts.get(1).name());

        assertEquals(1, resExts.size());
        assertEquals("main", resExts.get(0).name());
        assertTrue(resExts.get(0).parameters().isEmpty());
        assertNotNull(ch.pipeline().get(DummyDecoder.class));
        assertNotNull(ch.pipeline().get(DummyEncoder.class) != null);

        verify(mainHandshakerMock).newRequestData();
        verify(mainHandshakerMock).handshakeExtension(any(WebSocketExtensionData.class));
        verify(fallbackHandshakerMock).newRequestData();
        verify(mainExtensionMock, atLeastOnce()).rsv();
        verify(mainExtensionMock).newExtensionEncoder();
        verify(mainExtensionMock).newExtensionDecoder();
    }

    @Test
    public void testFallbackSuccess() {
        // initialize
        when(mainHandshakerMock.newRequestData()).
                thenReturn(new WebSocketExtensionData("main", Collections.<String, String>emptyMap()));
        when(mainHandshakerMock.handshakeExtension(any(WebSocketExtensionData.class))).thenReturn(null);
        when(fallbackHandshakerMock.newRequestData()).
                thenReturn(new WebSocketExtensionData("fallback", Collections.<String, String>emptyMap()));
        when(fallbackHandshakerMock.handshakeExtension(
                any(WebSocketExtensionData.class))).thenReturn(fallbackExtensionMock);
        when(fallbackExtensionMock.rsv()).thenReturn(WebSocketExtension.RSV1);
        when(fallbackExtensionMock.newExtensionEncoder()).thenReturn(new DummyEncoder());
        when(fallbackExtensionMock.newExtensionDecoder()).thenReturn(new DummyDecoder());

        // execute
        EmbeddedChannel ch = new EmbeddedChannel(new WebSocketClientExtensionHandler(
                mainHandshakerMock, fallbackHandshakerMock));

        HttpRequest req = newUpgradeRequest(null);
        ch.writeOutbound(req);

        HttpRequest req2 = ch.readOutbound();
        List<WebSocketExtensionData> reqExts = WebSocketExtensionUtil.extractExtensions(
                req2.headers().get(HttpHeaderNames.SEC_WEBSOCKET_EXTENSIONS));

        HttpResponse res = newUpgradeResponse("fallback");
        ch.writeInbound(res);

        HttpResponse res2 = ch.readInbound();
        List<WebSocketExtensionData> resExts = WebSocketExtensionUtil.extractExtensions(
                res2.headers().get(HttpHeaderNames.SEC_WEBSOCKET_EXTENSIONS));

        // test
        assertEquals(2, reqExts.size());
        assertEquals("main", reqExts.get(0).name());
        assertEquals("fallback", reqExts.get(1).name());

        assertEquals(1, resExts.size());
        assertEquals("fallback", resExts.get(0).name());
        assertTrue(resExts.get(0).parameters().isEmpty());
        assertNotNull(ch.pipeline().get(DummyDecoder.class));
        assertNotNull(ch.pipeline().get(DummyEncoder.class));

        verify(mainHandshakerMock).newRequestData();
        verify(mainHandshakerMock).handshakeExtension(any(WebSocketExtensionData.class));
        verify(fallbackHandshakerMock).newRequestData();
        verify(fallbackHandshakerMock).handshakeExtension(any(WebSocketExtensionData.class));
        verify(fallbackExtensionMock, atLeastOnce()).rsv();
        verify(fallbackExtensionMock).newExtensionEncoder();
        verify(fallbackExtensionMock).newExtensionDecoder();
    }

    @Test
    public void testAllSuccess() {
        // initialize
        when(mainHandshakerMock.newRequestData()).
                thenReturn(new WebSocketExtensionData("main", Collections.<String, String>emptyMap()));
        when(mainHandshakerMock.handshakeExtension(
                webSocketExtensionDataMatcher("main"))).thenReturn(mainExtensionMock);
        when(mainHandshakerMock.handshakeExtension(
                webSocketExtensionDataMatcher("fallback"))).thenReturn(null);
        when(fallbackHandshakerMock.newRequestData()).
                thenReturn(new WebSocketExtensionData("fallback", Collections.<String, String>emptyMap()));
        when(fallbackHandshakerMock.handshakeExtension(
                webSocketExtensionDataMatcher("main"))).thenReturn(null);
        when(fallbackHandshakerMock.handshakeExtension(
                webSocketExtensionDataMatcher("fallback"))).thenReturn(fallbackExtensionMock);

        DummyEncoder mainEncoder = new DummyEncoder();
        DummyDecoder mainDecoder = new DummyDecoder();
        when(mainExtensionMock.rsv()).thenReturn(WebSocketExtension.RSV1);
        when(mainExtensionMock.newExtensionEncoder()).thenReturn(mainEncoder);
        when(mainExtensionMock.newExtensionDecoder()).thenReturn(mainDecoder);

        Dummy2Encoder fallbackEncoder = new Dummy2Encoder();
        Dummy2Decoder fallbackDecoder = new Dummy2Decoder();
        when(fallbackExtensionMock.rsv()).thenReturn(WebSocketExtension.RSV2);
        when(fallbackExtensionMock.newExtensionEncoder()).thenReturn(fallbackEncoder);
        when(fallbackExtensionMock.newExtensionDecoder()).thenReturn(fallbackDecoder);

        // execute
        EmbeddedChannel ch = new EmbeddedChannel(new WebSocketClientExtensionHandler(
                mainHandshakerMock, fallbackHandshakerMock));

        HttpRequest req = newUpgradeRequest(null);
        ch.writeOutbound(req);

        HttpRequest req2 = ch.readOutbound();
        List<WebSocketExtensionData> reqExts = WebSocketExtensionUtil.extractExtensions(
                req2.headers().get(HttpHeaderNames.SEC_WEBSOCKET_EXTENSIONS));

        HttpResponse res = newUpgradeResponse("main, fallback");
        ch.writeInbound(res);

        HttpResponse res2 = ch.readInbound();
        List<WebSocketExtensionData> resExts = WebSocketExtensionUtil.extractExtensions(
                res2.headers().get(HttpHeaderNames.SEC_WEBSOCKET_EXTENSIONS));

        // test
        assertEquals(2, reqExts.size());
        assertEquals("main", reqExts.get(0).name());
        assertEquals("fallback", reqExts.get(1).name());

        assertEquals(2, resExts.size());
        assertEquals("main", resExts.get(0).name());
        assertEquals("fallback", resExts.get(1).name());
        assertNotNull(ch.pipeline().context(mainEncoder));
        assertNotNull(ch.pipeline().context(mainDecoder));
        assertNotNull(ch.pipeline().context(fallbackEncoder));
        assertNotNull(ch.pipeline().context(fallbackDecoder));

        verify(mainHandshakerMock).newRequestData();
        verify(mainHandshakerMock).handshakeExtension(webSocketExtensionDataMatcher("main"));
        verify(mainHandshakerMock).handshakeExtension(webSocketExtensionDataMatcher("fallback"));
        verify(fallbackHandshakerMock).newRequestData();
        verify(fallbackHandshakerMock).handshakeExtension(webSocketExtensionDataMatcher("fallback"));
        verify(mainExtensionMock, atLeastOnce()).rsv();
        verify(mainExtensionMock).newExtensionEncoder();
        verify(mainExtensionMock).newExtensionDecoder();
        verify(fallbackExtensionMock, atLeastOnce()).rsv();
        verify(fallbackExtensionMock).newExtensionEncoder();
        verify(fallbackExtensionMock).newExtensionDecoder();
    }

    @Test(expected = CodecException.class)
    public void testIfMainAndFallbackUseRSV1WillFail() {
        // initialize
        when(mainHandshakerMock.newRequestData()).
                thenReturn(new WebSocketExtensionData("main", Collections.<String, String>emptyMap()));
        when(mainHandshakerMock.handshakeExtension(
                webSocketExtensionDataMatcher("main"))).thenReturn(mainExtensionMock);
        when(mainHandshakerMock.handshakeExtension(
                webSocketExtensionDataMatcher("fallback"))).thenReturn(null);
        when(fallbackHandshakerMock.newRequestData()).
                thenReturn(new WebSocketExtensionData("fallback", Collections.<String, String>emptyMap()));
        when(fallbackHandshakerMock.handshakeExtension(
                webSocketExtensionDataMatcher("main"))).thenReturn(null);
        when(fallbackHandshakerMock.handshakeExtension(
                webSocketExtensionDataMatcher("fallback"))).thenReturn(fallbackExtensionMock);
        when(mainExtensionMock.rsv()).thenReturn(WebSocketExtension.RSV1);
        when(fallbackExtensionMock.rsv()).thenReturn(WebSocketExtension.RSV1);

        // execute
        EmbeddedChannel ch = new EmbeddedChannel(new WebSocketClientExtensionHandler(
                mainHandshakerMock, fallbackHandshakerMock));

        HttpRequest req = newUpgradeRequest(null);
        ch.writeOutbound(req);

        HttpRequest req2 = ch.readOutbound();
        List<WebSocketExtensionData> reqExts = WebSocketExtensionUtil.extractExtensions(
                req2.headers().get(HttpHeaderNames.SEC_WEBSOCKET_EXTENSIONS));

        HttpResponse res = newUpgradeResponse("main, fallback");
        ch.writeInbound(res);

        // test
        assertEquals(2, reqExts.size());
        assertEquals("main", reqExts.get(0).name());
        assertEquals("fallback", reqExts.get(1).name());

        verify(mainHandshakerMock).newRequestData();
        verify(mainHandshakerMock, atLeastOnce()).handshakeExtension(webSocketExtensionDataMatcher("main"));
        verify(mainHandshakerMock, atLeastOnce()).handshakeExtension(webSocketExtensionDataMatcher("fallback"));

        verify(fallbackHandshakerMock).newRequestData();
        verify(fallbackHandshakerMock, atLeastOnce()).handshakeExtension(webSocketExtensionDataMatcher("main"));
        verify(fallbackHandshakerMock, atLeastOnce()).handshakeExtension(webSocketExtensionDataMatcher("fallback"));

        verify(mainExtensionMock, atLeastOnce()).rsv();
        verify(fallbackExtensionMock, atLeastOnce()).rsv();
    }
}
