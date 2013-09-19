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
package io.netty.handler.codec.http;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.CodecException;
import io.netty.handler.codec.PrematureChannelClosureException;
import io.netty.util.CharsetUtil;
import org.junit.Test;

import static org.junit.Assert.*;

public class HttpClientCodecTest {

    private static final String RESPONSE = "HTTP/1.0 200 OK\r\n" + "Date: Fri, 31 Dec 1999 23:59:59 GMT\r\n" +
            "Content-Type: text/html\r\n" + "Content-Length: 28\r\n" + "\r\n"
            + "<html><body></body></html>\r\n";
    private static final String INCOMPLETE_CHUNKED_RESPONSE = "HTTP/1.1 200 OK\r\n" + "Content-Type: text/plain\r\n" +
            "Transfer-Encoding: chunked\r\n" + "\r\n" +
            "5\r\n" + "first\r\n" + "6\r\n" + "second\r\n" + "0\r\n";
    private static final String CHUNKED_RESPONSE = INCOMPLETE_CHUNKED_RESPONSE + "\r\n";

    @Test
    public void testFailsNotOnRequestResponse() {
        HttpClientCodec codec = new HttpClientCodec(4096, 8192, 8192, true);
        EmbeddedChannel ch = new EmbeddedChannel(codec);

        ch.writeOutbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "http://localhost/"));
        ch.writeInbound(Unpooled.copiedBuffer(RESPONSE, CharsetUtil.ISO_8859_1));
        ch.finish();
    }

    @Test
    public void testFailsNotOnRequestResponseChunked() {
        HttpClientCodec codec = new HttpClientCodec(4096, 8192, 8192, true);
        EmbeddedChannel ch = new EmbeddedChannel(codec);

        ch.writeOutbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "http://localhost/"));
        ch.writeInbound(Unpooled.copiedBuffer(CHUNKED_RESPONSE, CharsetUtil.ISO_8859_1));
        ch.finish();
    }

    @Test
    public void testFailsOnMissingResponse() {
        HttpClientCodec codec = new HttpClientCodec(4096, 8192, 8192, true);
        EmbeddedChannel ch = new EmbeddedChannel(codec);

        assertTrue(ch.writeOutbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET,
                "http://localhost/")));
        assertNotNull(ch.readOutbound());

        try {
            ch.finish();
            fail();
        } catch (CodecException e) {
            assertTrue(e instanceof PrematureChannelClosureException);
        }
    }

    @Test
    public void testFailsOnIncompleteChunkedResponse() {
        HttpClientCodec codec = new HttpClientCodec(4096, 8192, 8192, true);
        EmbeddedChannel ch = new EmbeddedChannel(codec);

        ch.writeOutbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "http://localhost/"));
        ch.writeInbound(Unpooled.copiedBuffer(INCOMPLETE_CHUNKED_RESPONSE, CharsetUtil.ISO_8859_1));

        try {
            ch.finish();
            fail();
        } catch (CodecException e) {
            assertTrue(e instanceof PrematureChannelClosureException);
        }
    }
}
