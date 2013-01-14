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

import static io.netty.handler.codec.http.HttpHeaders.Values.*;
import static io.netty.handler.codec.http.HttpVersion.*;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedByteChannel;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpHeaders.Names;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseHeader;
import io.netty.handler.codec.http.HttpResponseDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;

import org.junit.Assert;
import org.junit.Test;

public class WebSocketServerHandshaker08Test {

    @Test
    public void testPerformOpeningHandshake() {
        EmbeddedByteChannel ch = new EmbeddedByteChannel(
                new HttpObjectAggregator(42), new HttpRequestDecoder(), new HttpResponseEncoder());

        HttpRequest req = new DefaultHttpRequest(HTTP_1_1, HttpMethod.GET, "/chat");
        req.setHeader(Names.HOST, "server.example.com");
        req.setHeader(Names.UPGRADE, WEBSOCKET.toLowerCase());
        req.setHeader(Names.CONNECTION, "Upgrade");
        req.setHeader(Names.SEC_WEBSOCKET_KEY, "dGhlIHNhbXBsZSBub25jZQ==");
        req.setHeader(Names.SEC_WEBSOCKET_ORIGIN, "http://example.com");
        req.setHeader(Names.SEC_WEBSOCKET_PROTOCOL, "chat, superchat");
        req.setHeader(Names.SEC_WEBSOCKET_VERSION, "8");

        new WebSocketServerHandshaker08(
                "ws://example.com/chat", "chat", false, Integer.MAX_VALUE).handshake(ch, req);

        ByteBuf resBuf = ch.readOutbound();

        EmbeddedByteChannel ch2 = new EmbeddedByteChannel(new HttpResponseDecoder());
        ch2.writeInbound(resBuf);
        HttpResponseHeader res = (HttpResponseHeader) ch2.readInbound();

        Assert.assertEquals(
                "s3pPLMBiTxaQ9kYGzzhZRbK+xOo=", res.getHeader(Names.SEC_WEBSOCKET_ACCEPT));
        Assert.assertEquals("chat", res.getHeader(Names.SEC_WEBSOCKET_PROTOCOL));
    }
}
