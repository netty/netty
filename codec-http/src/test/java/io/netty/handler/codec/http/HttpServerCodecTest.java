/*
 * Copyright 2012 The Netty Project
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
package io.netty.handler.codec.http;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.CharsetUtil;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.*;

public class HttpServerCodecTest {

    /**
     * Testcase for https://github.com/netty/netty/issues/433
     */
    @Test
    public void testUnfinishedChunkedHttpRequestIsLastFlag() throws Exception {

        int maxChunkSize = 2000;
        HttpServerCodec httpServerCodec = new HttpServerCodec(1000, 1000, maxChunkSize);
        EmbeddedChannel decoderEmbedder = new EmbeddedChannel(httpServerCodec);

        int totalContentLength = maxChunkSize * 5;
        decoderEmbedder.writeInbound(Unpooled.copiedBuffer(
                "PUT /test HTTP/1.1\r\n" +
                "Content-Length: " + totalContentLength + "\r\n" +
                "\r\n", CharsetUtil.UTF_8));

        int offeredContentLength = (int) (maxChunkSize * 2.5);
        decoderEmbedder.writeInbound(prepareDataChunk(offeredContentLength));
        decoderEmbedder.finish();

        HttpMessage httpMessage = decoderEmbedder.readInbound();
        assertNotNull(httpMessage);

        boolean empty = true;
        int totalBytesPolled = 0;
        for (;;) {
            HttpContent httpChunk = decoderEmbedder.readInbound();
            if (httpChunk == null) {
                break;
            }
            empty = false;
            totalBytesPolled += httpChunk.content().readableBytes();
            assertFalse(httpChunk instanceof LastHttpContent);
            httpChunk.release();
        }
        assertFalse(empty);
        assertEquals(offeredContentLength, totalBytesPolled);
    }

    @Test
    public void test100Continue() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpServerCodec(), new HttpObjectAggregator(1024));

        // Send the request headers.
        ch.writeInbound(Unpooled.copiedBuffer(
                "PUT /upload-large HTTP/1.1\r\n" +
                "Expect: 100-continue\r\n" +
                "Content-Length: 1\r\n\r\n", CharsetUtil.UTF_8));

        // Ensure the aggregator generates nothing.
        assertThat(ch.readInbound(), is(nullValue()));

        // Ensure the aggregator writes a 100 Continue response.
        ByteBuf continueResponse = ch.readOutbound();
        assertThat(continueResponse.toString(CharsetUtil.UTF_8), is("HTTP/1.1 100 Continue\r\n\r\n"));
        continueResponse.release();

        // But nothing more.
        assertThat(ch.readOutbound(), is(nullValue()));

        // Send the content of the request.
        ch.writeInbound(Unpooled.wrappedBuffer(new byte[] { 42 }));

        // Ensure the aggregator generates a full request.
        FullHttpRequest req = ch.readInbound();
        assertThat(req.headers().get(HttpHeaderNames.CONTENT_LENGTH), is("1"));
        assertThat(req.content().readableBytes(), is(1));
        assertThat(req.content().readByte(), is((byte) 42));
        req.release();

        // But nothing more.
        assertThat(ch.readInbound(), is(nullValue()));

        // Send the actual response.
        FullHttpResponse res = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CREATED);
        res.content().writeBytes("OK".getBytes(CharsetUtil.UTF_8));
        res.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, 2);
        ch.writeOutbound(res);

        // Ensure the encoder handles the response after handling 100 Continue.
        ByteBuf encodedRes = ch.readOutbound();
        assertThat(encodedRes.toString(CharsetUtil.UTF_8),
                   is("HTTP/1.1 201 Created\r\n" + HttpHeaderNames.CONTENT_LENGTH + ": 2\r\n\r\nOK"));
        encodedRes.release();

        ch.finish();
    }

    @Test
    public void testChunkedHeadResponse() {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpServerCodec());

        // Send the request headers.
        assertTrue(ch.writeInbound(Unpooled.copiedBuffer(
                "HEAD / HTTP/1.1\r\n\r\n", CharsetUtil.UTF_8)));

        HttpRequest request = ch.readInbound();
        assertEquals(HttpMethod.HEAD, request.method());
        LastHttpContent content = ch.readInbound();
        assertFalse(content.content().isReadable());
        content.release();

        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        HttpUtil.setTransferEncodingChunked(response, true);
        assertTrue(ch.writeOutbound(response));
        assertTrue(ch.writeOutbound(LastHttpContent.EMPTY_LAST_CONTENT));
        assertTrue(ch.finish());

        ByteBuf buf = ch.readOutbound();
        assertEquals("HTTP/1.1 200 OK\r\ntransfer-encoding: chunked\r\n\r\n", buf.toString(CharsetUtil.US_ASCII));
        buf.release();

        buf = ch.readOutbound();
        assertFalse(buf.isReadable());
        buf.release();

        assertFalse(ch.finishAndReleaseAll());
    }

    @Test
    public void testChunkedHeadFullHttpResponse() {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpServerCodec());

        // Send the request headers.
        assertTrue(ch.writeInbound(Unpooled.copiedBuffer(
                "HEAD / HTTP/1.1\r\n\r\n", CharsetUtil.UTF_8)));

        HttpRequest request = ch.readInbound();
        assertEquals(HttpMethod.HEAD, request.method());
        LastHttpContent content = ch.readInbound();
        assertFalse(content.content().isReadable());
        content.release();

        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        HttpUtil.setTransferEncodingChunked(response, true);
        assertTrue(ch.writeOutbound(response));
        assertTrue(ch.finish());

        ByteBuf buf = ch.readOutbound();
        assertEquals("HTTP/1.1 200 OK\r\ntransfer-encoding: chunked\r\n\r\n", buf.toString(CharsetUtil.US_ASCII));
        buf.release();

        assertFalse(ch.finishAndReleaseAll());
    }

    private static ByteBuf prepareDataChunk(int size) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < size; ++i) {
            sb.append('a');
        }
        return Unpooled.copiedBuffer(sb.toString(), CharsetUtil.UTF_8);
    }
}
