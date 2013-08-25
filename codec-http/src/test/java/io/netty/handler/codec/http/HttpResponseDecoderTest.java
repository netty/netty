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
import org.junit.Test;

import java.util.List;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

public class HttpResponseDecoderTest {

    @Test
    public void testResponseChunked() {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpResponseDecoder());
        ch.writeInbound(Unpooled.copiedBuffer("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n",
                CharsetUtil.US_ASCII));

        HttpResponse res = (HttpResponse) ch.readInbound();
        assertThat(res.getProtocolVersion(), sameInstance(HttpVersion.HTTP_1_1));
        assertThat(res.getStatus(), is(HttpResponseStatus.OK));

        byte[] data = new byte[64];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }

        for (int i = 0; i < 10; i++) {
            assertFalse(ch.writeInbound(Unpooled.copiedBuffer(Integer.toHexString(data.length) + "\r\n",
                    CharsetUtil.US_ASCII)));
            assertTrue(ch.writeInbound(Unpooled.wrappedBuffer(data)));
            HttpContent content = (HttpContent) ch.readInbound();
            assertEquals(data.length, content.content().readableBytes());

            byte[] decodedData = new byte[data.length];
            content.content().readBytes(decodedData);
            assertArrayEquals(data, decodedData);
            assertFalse(ch.writeInbound(Unpooled.copiedBuffer("\r\n", CharsetUtil.US_ASCII)));
        }
        assertTrue(ch.finish());

        LastHttpContent content = (LastHttpContent) ch.readInbound();
        assertFalse(content.content().isReadable());

        assertNull(ch.readInbound());
    }

    @Test
    public void testResponseChunkedExceedMaxChunkSize() {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpResponseDecoder(4096, 8192, 32));
        ch.writeInbound(Unpooled.copiedBuffer("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n",
                CharsetUtil.US_ASCII));

        HttpResponse res = (HttpResponse) ch.readInbound();
        assertThat(res.getProtocolVersion(), sameInstance(HttpVersion.HTTP_1_1));
        assertThat(res.getStatus(), is(HttpResponseStatus.OK));

        byte[] data = new byte[64];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }

        for (int i = 0; i < 10; i++) {
            assertFalse(ch.writeInbound(Unpooled.copiedBuffer(Integer.toHexString(data.length) + "\r\n",
                    CharsetUtil.US_ASCII)));
            assertTrue(ch.writeInbound(Unpooled.wrappedBuffer(data)));

            byte[] decodedData = new byte[data.length];
            HttpContent content = (HttpContent) ch.readInbound();
            assertEquals(32, content.content().readableBytes());
            content.content().readBytes(decodedData, 0, 32);

            content = (HttpContent) ch.readInbound();
            assertEquals(32, content.content().readableBytes());

            content.content().readBytes(decodedData, 32, 32);

            assertArrayEquals(data, decodedData);
            assertFalse(ch.writeInbound(Unpooled.copiedBuffer("\r\n", CharsetUtil.US_ASCII)));
        }
        assertTrue(ch.finish());

        LastHttpContent content = (LastHttpContent) ch.readInbound();
        assertFalse(content.content().isReadable());

        assertNull(ch.readInbound());
    }

    @Test
    public void testLastResponseWithEmptyHeaderAndEmptyContent() {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpResponseDecoder());
        ch.writeInbound(Unpooled.copiedBuffer("HTTP/1.1 200 OK\r\n\r\n", CharsetUtil.US_ASCII));

        HttpResponse res = (HttpResponse) ch.readInbound();
        assertThat(res.getProtocolVersion(), sameInstance(HttpVersion.HTTP_1_1));
        assertThat(res.getStatus(), is(HttpResponseStatus.OK));
        assertThat(ch.readInbound(), is(nullValue()));

        assertThat(ch.finish(), is(true));

        LastHttpContent content = (LastHttpContent) ch.readInbound();
        assertThat(content.content().isReadable(), is(false));

        assertThat(ch.readInbound(), is(nullValue()));
    }

    @Test
    public void testLastResponseWithoutContentLengthHeader() {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpResponseDecoder());
        ch.writeInbound(Unpooled.copiedBuffer("HTTP/1.1 200 OK\r\n\r\n", CharsetUtil.US_ASCII));

        HttpResponse res = (HttpResponse) ch.readInbound();
        assertThat(res.getProtocolVersion(), sameInstance(HttpVersion.HTTP_1_1));
        assertThat(res.getStatus(), is(HttpResponseStatus.OK));
        assertThat(ch.readInbound(), is(nullValue()));

        ch.writeInbound(Unpooled.wrappedBuffer(new byte[1024]));
        HttpContent content = (HttpContent) ch.readInbound();
        assertThat(content.content().readableBytes(), is(1024));

        assertThat(ch.finish(), is(true));

        LastHttpContent lastContent = (LastHttpContent) ch.readInbound();
        assertThat(lastContent.content().isReadable(), is(false));

        assertThat(ch.readInbound(), is(nullValue()));
    }

    @Test
    public void testLastResponseWithHeaderRemoveTrailingSpaces() {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpResponseDecoder());
        ch.writeInbound(Unpooled.copiedBuffer(
                "HTTP/1.1 200 OK\r\nX-Header: h2=h2v2; Expires=Wed, 09-Jun-2021 10:18:14 GMT       \r\n\r\n",
                CharsetUtil.US_ASCII));

        HttpResponse res = (HttpResponse) ch.readInbound();
        assertThat(res.getProtocolVersion(), sameInstance(HttpVersion.HTTP_1_1));
        assertThat(res.getStatus(), is(HttpResponseStatus.OK));
        assertThat(res.headers().get("X-Header"), is("h2=h2v2; Expires=Wed, 09-Jun-2021 10:18:14 GMT"));
        assertThat(ch.readInbound(), is(nullValue()));

        ch.writeInbound(Unpooled.wrappedBuffer(new byte[1024]));
        HttpContent content = (HttpContent) ch.readInbound();
        assertThat(content.content().readableBytes(), is(1024));

        assertThat(ch.finish(), is(true));

        LastHttpContent lastContent = (LastHttpContent) ch.readInbound();
        assertThat(lastContent.content().isReadable(), is(false));

        assertThat(ch.readInbound(), is(nullValue()));
    }

    @Test
    public void testLastResponseWithTrailingHeader() {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpResponseDecoder());
        ch.writeInbound(Unpooled.copiedBuffer(
                "HTTP/1.1 200 OK\r\n" +
                        "Transfer-Encoding: chunked\r\n" +
                        "\r\n" +
                        "0\r\n" +
                        "Set-Cookie: t1=t1v1\r\n" +
                        "Set-Cookie: t2=t2v2; Expires=Wed, 09-Jun-2021 10:18:14 GMT\r\n" +
                        "\r\n",
                CharsetUtil.US_ASCII));

        HttpResponse res = (HttpResponse) ch.readInbound();
        assertThat(res.getProtocolVersion(), sameInstance(HttpVersion.HTTP_1_1));
        assertThat(res.getStatus(), is(HttpResponseStatus.OK));

        LastHttpContent lastContent = (LastHttpContent) ch.readInbound();
        assertThat(lastContent.content().isReadable(), is(false));
        HttpHeaders headers = lastContent.trailingHeaders();
        assertEquals(1, headers.names().size());
        List<String> values = headers.getAll("Set-Cookie");
        assertEquals(2, values.size());
        assertTrue(values.contains("t1=t1v1"));
        assertTrue(values.contains("t2=t2v2; Expires=Wed, 09-Jun-2021 10:18:14 GMT"));

        assertThat(ch.finish(), is(false));
        assertThat(ch.readInbound(), is(nullValue()));
    }

    @Test
    public void testLastResponseWithTrailingHeaderFragmented() {
        byte[] data = ("HTTP/1.1 200 OK\r\n" +
                "Transfer-Encoding: chunked\r\n" +
                "\r\n" +
                "0\r\n" +
                "Set-Cookie: t1=t1v1\r\n" +
                "Set-Cookie: t2=t2v2; Expires=Wed, 09-Jun-2021 10:18:14 GMT\r\n" +
                "\r\n").getBytes(CharsetUtil.US_ASCII);

        for (int i = 1; i < data.length; i++) {
            testLastResponseWithTrailingHeaderFragmented(data, i);
        }
    }

    private static void testLastResponseWithTrailingHeaderFragmented(byte[] content, int fragmentSize) {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpResponseDecoder());
        int headerLength = 47;
        // split up the header
        for (int a = 0; a < headerLength;) {
            int amount = fragmentSize;
            if (a + amount > headerLength) {
                amount = headerLength -  a;
            }

            // if header is done it should produce a HttpRequest
            boolean headerDone = a + amount == headerLength;
            assertEquals(headerDone, ch.writeInbound(Unpooled.wrappedBuffer(content, a, amount)));
            a += amount;
        }

        ch.writeInbound(Unpooled.wrappedBuffer(content, headerLength, content.length - headerLength));
        HttpResponse res = (HttpResponse) ch.readInbound();
        assertThat(res.getProtocolVersion(), sameInstance(HttpVersion.HTTP_1_1));
        assertThat(res.getStatus(), is(HttpResponseStatus.OK));

        LastHttpContent lastContent = (LastHttpContent) ch.readInbound();
        assertThat(lastContent.content().isReadable(), is(false));
        HttpHeaders headers = lastContent.trailingHeaders();
        assertEquals(1, headers.names().size());
        List<String> values = headers.getAll("Set-Cookie");
        assertEquals(2, values.size());
        assertTrue(values.contains("t1=t1v1"));
        assertTrue(values.contains("t2=t2v2; Expires=Wed, 09-Jun-2021 10:18:14 GMT"));

        assertThat(ch.finish(), is(false));
        assertThat(ch.readInbound(), is(nullValue()));
    }

    @Test
    public void testResponseWithContentLength() {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpResponseDecoder());
        ch.writeInbound(Unpooled.copiedBuffer(
                "HTTP/1.1 200 OK\r\n" +
                        "Content-Length: 10\r\n" +
                        "\r\n", CharsetUtil.US_ASCII));

        HttpResponse res = (HttpResponse) ch.readInbound();
        assertThat(res.getProtocolVersion(), sameInstance(HttpVersion.HTTP_1_1));
        assertThat(res.getStatus(), is(HttpResponseStatus.OK));
        byte[] data = new byte[10];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }
        ch.writeInbound(Unpooled.wrappedBuffer(data, 0, data.length / 2));
        HttpContent content = (HttpContent) ch.readInbound();
        assertEquals(content.content().readableBytes(), 5);

        ch.writeInbound(Unpooled.wrappedBuffer(data, 5, data.length / 2));
        LastHttpContent lastContent = (LastHttpContent) ch.readInbound();
        assertEquals(lastContent.content().readableBytes(), 5);
        assertThat(ch.finish(), is(false));
        assertThat(ch.readInbound(), is(nullValue()));
    }

    @Test
    public void testResponseWithContentLengthFragmented() {
        byte[] data = ("HTTP/1.1 200 OK\r\n" +
                "Content-Length: 10\r\n" +
                "\r\n").getBytes(CharsetUtil.US_ASCII);

        for (int i = 1; i < data.length; i++) {
            testResponseWithContentLengthFragmented(data, i);
        }
    }

    private static void testResponseWithContentLengthFragmented(byte[] header, int fragmentSize) {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpResponseDecoder());
        // split up the header
        for (int a = 0; a < header.length;) {
            int amount = fragmentSize;
            if (a + amount > header.length) {
                amount = header.length -  a;
            }

            // if header is done it should produce a HttpRequest
            boolean headerDone = a + amount == header.length;
            assertEquals(headerDone, ch.writeInbound(Unpooled.wrappedBuffer(header, a, amount)));
            a += amount;
        }

        HttpResponse res = (HttpResponse) ch.readInbound();
        assertThat(res.getProtocolVersion(), sameInstance(HttpVersion.HTTP_1_1));
        assertThat(res.getStatus(), is(HttpResponseStatus.OK));

        byte[] data = new byte[10];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }
        ch.writeInbound(Unpooled.wrappedBuffer(data, 0, data.length / 2));
        HttpContent content = (HttpContent) ch.readInbound();
        assertEquals(content.content().readableBytes(), 5);

        ch.writeInbound(Unpooled.wrappedBuffer(data, 5, data.length / 2));
        LastHttpContent lastContent = (LastHttpContent) ch.readInbound();
        assertEquals(lastContent.content().readableBytes(), 5);
        assertThat(ch.finish(), is(false));
        assertThat(ch.readInbound(), is(nullValue()));
    }

    @Test
    public void testWebSocketResponse() {
        byte[] data = ("HTTP/1.1 101 WebSocket Protocol Handshake\r\n" +
                "Upgrade: WebSocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Origin: http://localhost:8080\r\n" +
                "Sec-WebSocket-Location: ws://localhost/some/path\r\n" +
                "\r\n" +
                "1234567812345678").getBytes();
        EmbeddedChannel ch = new EmbeddedChannel(new HttpResponseDecoder());
        ch.writeInbound(Unpooled.wrappedBuffer(data));

        HttpResponse res = (HttpResponse) ch.readInbound();
        assertThat(res.getProtocolVersion(), sameInstance(HttpVersion.HTTP_1_1));
        assertThat(res.getStatus(), is(HttpResponseStatus.SWITCHING_PROTOCOLS));
        HttpContent content = (HttpContent) ch.readInbound();
        assertThat(content.content().readableBytes(), is(16));

        assertThat(ch.finish(), is(false));

        assertThat(ch.readInbound(), is(nullValue()));
    }
}
