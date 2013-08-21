/*
 * Copyright 2013 The Netty Project
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


import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.CharsetUtil;
import org.junit.Assert;
import org.junit.Test;

public class HttpRequestDecoderTest {
    private static final byte[] CONTENT = ("GET /some/path?foo=bar&wibble=eek HTTP/1.1\r\n" +
            "Upgrade: WebSocket\r\n" +
            "Connection: Upgrade\r\n" +
            "Host: localhost\r\n" +
            "Origin: http://localhost:8080\r\n" +
            "Sec-WebSocket-Key1: 10  28 8V7 8 48     0\r\n" +
            "Sec-WebSocket-Key2: 8 Xt754O3Q3QW 0   _60\r\n" +
            "Content-Length: 8\r\n" +
            "\r\n" +
            "12345678").getBytes(CharsetUtil.US_ASCII);

    @Test
    public void testDecodeWholeRequestAtOnce() {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestDecoder());
        channel.writeInbound(Unpooled.wrappedBuffer(CONTENT));
        HttpRequest req = (HttpRequest) channel.readInbound();
        Assert.assertNotNull(req);
        LastHttpContent c = (LastHttpContent) channel.readInbound();
        Assert.assertEquals(8, c.content().readableBytes());
        Assert.assertEquals(Unpooled.wrappedBuffer(CONTENT, CONTENT.length - 8, 8), c.content().readBytes(8));
        Assert.assertFalse(channel.finish());
    }

    @Test
    public void testDecodeWholeRequestInMultipleSteps() {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestDecoder());
        channel.writeInbound(Unpooled.wrappedBuffer(CONTENT, 0, CONTENT.length - 8));

        for (int i = 8; i > 0; i--) {
            channel.writeInbound(Unpooled.wrappedBuffer(CONTENT, CONTENT.length - i, 1));
        }

        HttpRequest req = (HttpRequest) channel.readInbound();
        Assert.assertNotNull(req);
        for (int i = 8; i > 1; i--) {
            HttpContent c = (HttpContent) channel.readInbound();
            Assert.assertEquals(1, c.content().readableBytes());
            Assert.assertEquals(CONTENT[CONTENT.length - i], c.content().readByte());
        }
        LastHttpContent c = (LastHttpContent) channel.readInbound();
        Assert.assertEquals(1, c.content().readableBytes());
        Assert.assertEquals(CONTENT[CONTENT.length - 1], c.content().readByte());
        Assert.assertFalse(channel.finish());
    }
}
