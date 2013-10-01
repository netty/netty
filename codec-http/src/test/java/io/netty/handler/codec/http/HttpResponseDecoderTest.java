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
        content.release();

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
        content.release();

        assertThat(ch.finish(), is(true));

        LastHttpContent lastContent = (LastHttpContent) ch.readInbound();
        assertThat(lastContent.content().isReadable(), is(false));
        lastContent.release();

        assertThat(ch.readInbound(), is(nullValue()));
    }
}
