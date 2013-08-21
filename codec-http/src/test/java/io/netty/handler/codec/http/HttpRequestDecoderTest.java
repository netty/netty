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
    private static final byte[] CONTENT_CRLF_DELIMITERS = createContent("\r\n");
    private static final byte[] CONTENT_LF_DELIMITERS = createContent("\n");
    private static final byte[] CONTENT_MIXED_DELIMITERS = createContent("\r\n", "\n");

    private static byte[] createContent(String... lineDelimiters) {
        String lineDelimiter;
        String lineDelimiter2;
        if (lineDelimiters.length == 2) {
            lineDelimiter = lineDelimiters[0];
            lineDelimiter2= lineDelimiters[1];
        } else {
            lineDelimiter = lineDelimiters[0];
            lineDelimiter2= lineDelimiters[0];
        }
        return ("GET /some/path?foo=bar&wibble=eek HTTP/1.1" + lineDelimiter +
                "Upgrade: WebSocket" + lineDelimiter2 +
                "Connection: Upgrade" + lineDelimiter +
                "Host: localhost" + lineDelimiter2 +
                "Origin: http://localhost:8080" + lineDelimiter +
                "Sec-WebSocket-Key1: 10  28 8V7 8 48     0" + lineDelimiter2 +
                "Sec-WebSocket-Key2: 8 Xt754O3Q3QW 0   _60" + lineDelimiter +
                "Content-Length: 8" + lineDelimiter2 +
                lineDelimiter +
                "12345678").getBytes(CharsetUtil.US_ASCII);
    }

    @Test
    public void testDecodeWholeRequestAtOnceCRLFDelimiters() {
        testDecodeWholeRequestAtOnce(CONTENT_CRLF_DELIMITERS);
    }

    @Test
    public void testDecodeWholeRequestAtOnceLFDelimiters() {
        testDecodeWholeRequestAtOnce(CONTENT_LF_DELIMITERS);
    }

    @Test
    public void testDecodeWholeRequestAtOnceMixedDelimiters() {
        testDecodeWholeRequestAtOnce(CONTENT_MIXED_DELIMITERS);
    }

    private static void testDecodeWholeRequestAtOnce(byte[] content) {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestDecoder());
        channel.writeInbound(Unpooled.wrappedBuffer(content));
        HttpRequest req = (HttpRequest) channel.readInbound();
        Assert.assertNotNull(req);
        LastHttpContent c = (LastHttpContent) channel.readInbound();
        Assert.assertEquals(8, c.content().readableBytes());
        Assert.assertEquals(Unpooled.wrappedBuffer(content, content.length - 8, 8), c.content().readBytes(8));
        Assert.assertFalse(channel.finish());
    }

    @Test
    public void testDecodeWholeRequestInMultipleStepsCRLFDelimiters() {
        testDecodeWholeRequestInMultipleSteps(CONTENT_CRLF_DELIMITERS);
    }

    @Test
    public void testDecodeWholeRequestInMultipleStepsLFDelimiters() {
        testDecodeWholeRequestInMultipleSteps(CONTENT_LF_DELIMITERS);
    }

    @Test
    public void testDecodeWholeRequestInMultipleStepsMixedDelimiters() {
        testDecodeWholeRequestInMultipleSteps(CONTENT_MIXED_DELIMITERS);
    }

    private static void testDecodeWholeRequestInMultipleSteps(byte[] content) {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestDecoder());
        channel.writeInbound(Unpooled.wrappedBuffer(content, 0, content.length - 8));

        for (int i = 8; i > 0; i--) {
            channel.writeInbound(Unpooled.wrappedBuffer(content, content.length - i, 1));
        }

        HttpRequest req = (HttpRequest) channel.readInbound();
        Assert.assertNotNull(req);
        for (int i = 8; i > 1; i--) {
            HttpContent c = (HttpContent) channel.readInbound();
            Assert.assertEquals(1, c.content().readableBytes());
            Assert.assertEquals(content[content.length - i], c.content().readByte());
        }
        LastHttpContent c = (LastHttpContent) channel.readInbound();
        Assert.assertEquals(1, c.content().readableBytes());
        Assert.assertEquals(content[content.length - 1], c.content().readByte());
        Assert.assertFalse(channel.finish());
    }
}
