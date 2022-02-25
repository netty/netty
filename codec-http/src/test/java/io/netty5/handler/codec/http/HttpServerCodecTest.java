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
package io.netty5.handler.codec.http;

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.util.CharsetUtil;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static io.netty5.buffer.api.DefaultBufferAllocators.preferredAllocator;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HttpServerCodecTest {

    /**
     * Testcase for https://github.com/netty/netty/issues/433
     */
    @Test
    public void testUnfinishedChunkedHttpRequestIsLastFlag() {

        int maxChunkSize = 2000;
        HttpServerCodec httpServerCodec = new HttpServerCodec(1000, 1000);
        EmbeddedChannel decoderEmbedder = new EmbeddedChannel(httpServerCodec);

        int totalContentLength = maxChunkSize * 5;
        final byte[] data = ("PUT /test HTTP/1.1\r\n" +
                "Content-Length: " + totalContentLength + "\r\n" +
                "\r\n").getBytes(StandardCharsets.UTF_8);
        decoderEmbedder.writeInbound(decoderEmbedder.bufferAllocator().allocate(data.length).writeBytes(data));

        int offeredContentLength = (int) (maxChunkSize * 2.5);
        decoderEmbedder.writeInbound(prepareDataChunk(decoderEmbedder.bufferAllocator(), offeredContentLength));
        decoderEmbedder.finish();

        HttpMessage httpMessage = decoderEmbedder.readInbound();
        assertNotNull(httpMessage);

        boolean empty = true;
        int totalBytesPolled = 0;
        for (;;) {
            HttpContent<?> httpChunk = decoderEmbedder.readInbound();
            if (httpChunk == null) {
                break;
            }
            empty = false;
            totalBytesPolled += httpChunk.payload().readableBytes();
            assertFalse(httpChunk instanceof LastHttpContent);
            httpChunk.close();
        }
        assertFalse(empty);
        assertEquals(offeredContentLength, totalBytesPolled);
    }

    @Test
    public void test100Continue() {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpServerCodec(),
                new HttpObjectAggregator<DefaultHttpContent>(1024));

        // Send the request headers.
        final byte[] data = ("PUT /upload-large HTTP/1.1\r\n" +
                "Expect: 100-continue\r\n" +
                "Content-Length: 1\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        ch.writeInbound(ch.bufferAllocator().allocate(data.length).writeBytes(data));

        // Ensure the aggregator generates nothing.
        assertThat(ch.readInbound(), is(nullValue()));

        // Ensure the aggregator writes a 100 Continue response.
        try (Buffer continueResponse = ch.readOutbound()) {
            assertThat(continueResponse.toString(CharsetUtil.UTF_8), is("HTTP/1.1 100 Continue\r\n\r\n"));
        }

        // But nothing more.
        assertThat(ch.readOutbound(), is(nullValue()));

        // Send the content of the request.
        ch.writeInbound(ch.bufferAllocator().allocate(1).writeByte((byte) 42));

        // Ensure the aggregator generates a full request.
        FullHttpRequest req = ch.readInbound();
        assertThat(req.headers().get(HttpHeaderNames.CONTENT_LENGTH), is("1"));
        assertThat(req.payload().readableBytes(), is(1));
        assertThat(req.payload().readByte(), is((byte) 42));
        req.close();

        // But nothing more.
        assertThat(ch.readInbound(), is(nullValue()));

        // Send the actual response.
        FullHttpResponse res = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CREATED,
                                                           preferredAllocator().allocate(16));
        res.payload().writeBytes("OK".getBytes(CharsetUtil.UTF_8));
        res.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, 2);
        ch.writeOutbound(res);

        // Ensure the encoder handles the response after handling 100 Continue.
        try (Buffer encodedRes = ch.readOutbound()) {
            assertThat(encodedRes.toString(CharsetUtil.UTF_8),
                    is("HTTP/1.1 201 Created\r\n" + HttpHeaderNames.CONTENT_LENGTH + ": 2\r\n\r\nOK"));
        }

        ch.finish();
    }

    @Test
    public void testChunkedHeadResponse() {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpServerCodec());

        // Send the request headers.
        final byte[] data = "HEAD / HTTP/1.1\r\n\r\n".getBytes(StandardCharsets.UTF_8);
        assertTrue(ch.writeInbound(ch.bufferAllocator().allocate(data.length).writeBytes(data)));

        HttpRequest request = ch.readInbound();
        assertEquals(HttpMethod.HEAD, request.method());
        LastHttpContent<?> content = ch.readInbound();
        assertThat(content.payload().readableBytes(), is(0));
        content.close();

        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        HttpUtil.setTransferEncodingChunked(response, true);
        assertTrue(ch.writeOutbound(response));
        assertTrue(ch.writeOutbound(new EmptyLastHttpContent(preferredAllocator())));
        assertTrue(ch.finish());

        Buffer buf = ch.readOutbound();
        assertEquals("HTTP/1.1 200 OK\r\ntransfer-encoding: chunked\r\n\r\n", buf.toString(CharsetUtil.US_ASCII));
        buf.close();

        buf = ch.readOutbound();
        assertEquals(buf.readableBytes(), 0);
        buf.close();

        assertFalse(ch.finishAndReleaseAll());
    }

    @Test
    public void testChunkedHeadFullHttpResponse() {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpServerCodec());

        // Send the request headers.
        final byte[] data = "HEAD / HTTP/1.1\r\n\r\n".getBytes(StandardCharsets.UTF_8);
        assertTrue(ch.writeInbound(ch.bufferAllocator().allocate(data.length).writeBytes(data)));

        HttpRequest request = ch.readInbound();
        assertEquals(HttpMethod.HEAD, request.method());
        LastHttpContent<?> content = ch.readInbound();
        assertThat(content.payload().readableBytes(), is(0));
        content.close();

        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                                                                preferredAllocator().allocate(0));
        HttpUtil.setTransferEncodingChunked(response, true);
        assertTrue(ch.writeOutbound(response));
        assertTrue(ch.finish());

        Buffer buf = ch.readOutbound();
        assertEquals("HTTP/1.1 200 OK\r\ntransfer-encoding: chunked\r\n\r\n",
                buf.toString(CharsetUtil.US_ASCII));
        buf.close();

        assertFalse(ch.finishAndReleaseAll());
    }

    private static Buffer prepareDataChunk(BufferAllocator allocator, int size) {
        return allocator.copyOf("a".repeat(Math.max(0, size)).getBytes(StandardCharsets.UTF_8));
    }
}
