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
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedByteChannel;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpChunkAggregator;
import io.netty.handler.codec.http.HttpHeaders.Names;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.util.CharsetUtil;

import org.junit.Assert;
import org.junit.Test;

public class WebSocketServerHandshaker00Test {

    @Test
    public void testPerformOpeningHandshake() {
        EmbeddedByteChannel ch = new EmbeddedByteChannel(
                new HttpChunkAggregator(42), new HttpRequestDecoder(), new HttpResponseEncoder());

        HttpRequest req = new DefaultHttpRequest(HTTP_1_1, HttpMethod.GET, "/chat");
        req.setHeader(Names.HOST, "server.example.com");
        req.setHeader(Names.UPGRADE, WEBSOCKET.toLowerCase());
        req.setHeader(Names.CONNECTION, "Upgrade");
        req.setHeader(Names.ORIGIN, "http://example.com");
        req.setHeader(Names.SEC_WEBSOCKET_KEY1, "4 @1  46546xW%0l 1 5");
        req.setHeader(Names.SEC_WEBSOCKET_KEY2, "12998 5 Y3 1  .P00");
        req.setHeader(Names.SEC_WEBSOCKET_PROTOCOL, "chat, superchat");

        ByteBuf buffer = Unpooled.copiedBuffer("^n:ds[4U", CharsetUtil.US_ASCII);
        req.setContent(buffer);

        new WebSocketServerHandshaker00(
                "ws://example.com/chat", "chat", Integer.MAX_VALUE).handshake(ch, req);

        ByteBuf resBuf = ch.readOutbound();

        EmbeddedByteChannel ch2 = new EmbeddedByteChannel(new HttpResponseDecoder());
        ch2.writeInbound(resBuf);
        HttpResponse res = (HttpResponse) ch2.readInbound();

        Assert.assertEquals("ws://example.com/chat", res.getHeader(Names.SEC_WEBSOCKET_LOCATION));
        Assert.assertEquals("chat", res.getHeader(Names.SEC_WEBSOCKET_PROTOCOL));
        Assert.assertEquals("8jKS'y:G*Co,Wxa-", res.getContent().toString(CharsetUtil.US_ASCII));
    }
}
