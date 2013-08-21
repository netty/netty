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

}
