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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertNull(ch.readInbound());

        // Ensure the aggregator writes a 100 Continue response.
        ByteBuf continueResponse = ch.readOutbound();
        assertEquals("HTTP/1.1 100 Continue\r\n\r\n", continueResponse.toString(CharsetUtil.UTF_8));
        continueResponse.release();

        // But nothing more.
        assertNull(ch.readOutbound());

        // Send the content of the request.
        ch.writeInbound(Unpooled.wrappedBuffer(new byte[] { 42 }));

        // Ensure the aggregator generates a full request.
        FullHttpRequest req = ch.readInbound();
        assertEquals("1", req.headers().get(HttpHeaderNames.CONTENT_LENGTH));
        assertEquals(1, req.content().readableBytes());
        assertEquals((byte) 42, req.content().readByte());
        req.release();

        // But nothing more.
        assertNull(ch.readInbound());

        // Send the actual response.
        FullHttpResponse res = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CREATED);
        res.content().writeBytes("OK".getBytes(CharsetUtil.UTF_8));
        res.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, 2);
        ch.writeOutbound(res);

        // Ensure the encoder handles the response after handling 100 Continue.
        ByteBuf encodedRes = ch.readOutbound();
        assertEquals("HTTP/1.1 201 Created\r\n" + HttpHeaderNames.CONTENT_LENGTH + ": 2\r\n\r\nOK",
                encodedRes.toString(CharsetUtil.UTF_8));
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

    @Test
    public void testInterleavedRequestResponseAcrossOverflow() {
        // Test interleaved enqueue/dequeue that crosses the inline-to-overflow boundary.
        // Send some requests, process some responses, then send more requests to trigger overflow.
        EmbeddedChannel ch = new EmbeddedChannel(new HttpServerCodec());

        // Send 30 GET requests (not yet filling the 32-slot inline queue).
        StringBuilder requests = new StringBuilder();
        for (int i = 0; i < 30; i++) {
            requests.append("GET /").append(i).append(" HTTP/1.1\r\nHost: a\r\n\r\n");
        }
        assertTrue(ch.writeInbound(Unpooled.copiedBuffer(requests.toString(), CharsetUtil.UTF_8)));

        // Drain inbound.
        for (;;) {
            Object msg = ch.readInbound();
            if (msg == null) {
                break;
            }
            if (msg instanceof HttpContent) {
                ((HttpContent) msg).release();
            }
        }

        // Respond to 10 of them (draining 10 from the queue, leaving 20).
        for (int i = 0; i < 10; i++) {
            FullHttpResponse resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                    Unpooled.copiedBuffer("ok", CharsetUtil.UTF_8));
            resp.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, 2);
            assertTrue(ch.writeOutbound(resp));
            ByteBuf buf = ch.readOutbound();
            buf.release();
        }

        // Now send 15 more requests (20 remaining + 15 = 35 total, exceeding inline capacity of 32).
        // Put a HEAD request as the last one to verify overflow ordering.
        requests = new StringBuilder();
        for (int i = 30; i < 44; i++) {
            requests.append("GET /").append(i).append(" HTTP/1.1\r\nHost: a\r\n\r\n");
        }
        requests.append("HEAD /44 HTTP/1.1\r\nHost: a\r\n\r\n");
        assertTrue(ch.writeInbound(Unpooled.copiedBuffer(requests.toString(), CharsetUtil.UTF_8)));

        // Drain inbound.
        for (;;) {
            Object msg = ch.readInbound();
            if (msg == null) {
                break;
            }
            if (msg instanceof HttpContent) {
                ((HttpContent) msg).release();
            }
        }

        // Respond to remaining 20 GET requests (inline queue).
        for (int i = 10; i < 30; i++) {
            FullHttpResponse resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                    Unpooled.copiedBuffer("ok", CharsetUtil.UTF_8));
            resp.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, 2);
            assertTrue(ch.writeOutbound(resp));
            ByteBuf buf = ch.readOutbound();
            buf.release();
        }

        // Respond to the 14 GET requests that were added.
        for (int i = 30; i < 44; i++) {
            FullHttpResponse resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                    Unpooled.copiedBuffer("ok", CharsetUtil.UTF_8));
            resp.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, 2);
            assertTrue(ch.writeOutbound(resp));
            ByteBuf buf = ch.readOutbound();
            String encoded = buf.toString(CharsetUtil.US_ASCII);
            assertTrue(encoded.contains("ok"), "GET response at position " + i + " should contain body");
            buf.release();
        }

        // Respond to the HEAD request at position 44 — must be content-always-empty.
        HttpResponse headResp = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        headResp.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, 999);
        assertTrue(ch.writeOutbound(headResp));
        assertTrue(ch.writeOutbound(LastHttpContent.EMPTY_LAST_CONTENT));

        ByteBuf buf = ch.readOutbound();
        String encoded = buf.toString(CharsetUtil.US_ASCII);
        assertTrue(encoded.contains("HTTP/1.1 200 OK"), "HEAD response should be 200 OK");
        buf.release();

        buf = ch.readOutbound();
        assertFalse(buf.isReadable(), "HEAD response body should be empty in overflow scenario");
        buf.release();

        assertFalse(ch.finishAndReleaseAll());
    }

    @Test
    public void testGetMethodHasNormalBody() {
        // Verify a simple GET request is treated as METHOD_FLAG_NONE and body is included.
        EmbeddedChannel ch = new EmbeddedChannel(new HttpServerCodec());

        assertTrue(ch.writeInbound(Unpooled.copiedBuffer(
                "GET / HTTP/1.1\r\nHost: a\r\n\r\n", CharsetUtil.UTF_8)));

        HttpRequest request = ch.readInbound();
        assertEquals(HttpMethod.GET, request.method());
        LastHttpContent content = ch.readInbound();
        content.release();

        FullHttpResponse resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                Unpooled.copiedBuffer("hello", CharsetUtil.UTF_8));
        resp.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, 5);
        assertTrue(ch.writeOutbound(resp));

        ByteBuf buf = ch.readOutbound();
        String encoded = buf.toString(CharsetUtil.US_ASCII);
        assertTrue(encoded.contains("hello"), "GET response should contain body");
        assertTrue(encoded.contains("content-length: 5"));
        buf.release();

        assertFalse(ch.finishAndReleaseAll());
    }

    @Test
    public void testPostMethodHasNormalBody() {
        // POST should also be METHOD_FLAG_NONE.
        EmbeddedChannel ch = new EmbeddedChannel(new HttpServerCodec());

        assertTrue(ch.writeInbound(Unpooled.copiedBuffer(
                "POST / HTTP/1.1\r\nHost: a\r\nContent-Length: 0\r\n\r\n", CharsetUtil.UTF_8)));

        for (;;) {
            Object msg = ch.readInbound();
            if (msg == null) {
                break;
            }
            if (msg instanceof HttpContent) {
                ((HttpContent) msg).release();
            }
        }

        FullHttpResponse resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                Unpooled.copiedBuffer("result", CharsetUtil.UTF_8));
        resp.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, 6);
        assertTrue(ch.writeOutbound(resp));

        ByteBuf buf = ch.readOutbound();
        String encoded = buf.toString(CharsetUtil.US_ASCII);
        assertTrue(encoded.contains("result"), "POST response should contain body");
        buf.release();

        assertFalse(ch.finishAndReleaseAll());
    }

    @Test
    public void testOverflowDrainsBeforeInlineRefill() {
        // Verify that once overflow is activated, subsequent enqueues also go to overflow
        // until the overflow drains, keeping FIFO order correct.
        EmbeddedChannel ch = new EmbeddedChannel(new HttpServerCodec());

        int totalRequests = 34; // 32 inline + 2 overflow

        // Send 34 pipelined requests: position 33 (overflow) is HEAD.
        StringBuilder requests = new StringBuilder();
        for (int i = 0; i < totalRequests; i++) {
            if (i == 33) {
                requests.append("HEAD /").append(i).append(" HTTP/1.1\r\nHost: a\r\n\r\n");
            } else {
                requests.append("GET /").append(i).append(" HTTP/1.1\r\nHost: a\r\n\r\n");
            }
        }
        assertTrue(ch.writeInbound(Unpooled.copiedBuffer(requests.toString(), CharsetUtil.UTF_8)));

        // Drain inbound.
        for (;;) {
            Object msg = ch.readInbound();
            if (msg == null) {
                break;
            }
            if (msg instanceof HttpContent) {
                ((HttpContent) msg).release();
            }
        }

        // Send responses for first 33 (all GET).
        for (int i = 0; i < 33; i++) {
            FullHttpResponse resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                    Unpooled.copiedBuffer("ok", CharsetUtil.UTF_8));
            resp.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, 2);
            assertTrue(ch.writeOutbound(resp));
            ByteBuf buf = ch.readOutbound();
            String encoded = buf.toString(CharsetUtil.US_ASCII);
            assertTrue(encoded.contains("ok"), "GET response at position " + i + " should contain body");
            buf.release();
        }

        // Response for position 33 — HEAD from overflow queue.
        HttpResponse headResp = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        headResp.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, 42);
        assertTrue(ch.writeOutbound(headResp));
        assertTrue(ch.writeOutbound(LastHttpContent.EMPTY_LAST_CONTENT));

        ByteBuf buf = ch.readOutbound();
        buf.release();

        buf = ch.readOutbound();
        assertFalse(buf.isReadable(), "HEAD response from overflow queue body should be empty");
        buf.release();

        assertFalse(ch.finishAndReleaseAll());
    }

    @Test
    public void testPollFromEmptyQueueReturnsNone() {
        // If a response is written without a matching request (unusual but defensive),
        // pollMethod should return METHOD_FLAG_NONE, so body is treated normally.
        EmbeddedChannel ch = new EmbeddedChannel(new HttpServerCodec());

        // Write a response without any prior request.
        FullHttpResponse resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                Unpooled.copiedBuffer("data", CharsetUtil.UTF_8));
        resp.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, 4);
        assertDoesNotThrow(() -> ch.writeOutbound(resp));

        ByteBuf buf = ch.readOutbound();
        assertNotNull(buf);
        String encoded = buf.toString(CharsetUtil.US_ASCII);
        assertTrue(encoded.contains("data"), "Response body should be present even without prior request");
        buf.release();

        ch.finishAndReleaseAll();
    }

    private static ByteBuf prepareDataChunk(int size) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < size; ++i) {
            sb.append('a');
        }
        return Unpooled.copiedBuffer(sb.toString(), CharsetUtil.UTF_8);
    }
}
