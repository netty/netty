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
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.DecoderResultProvider;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.util.AsciiString;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;

import org.junit.Test;
import org.mockito.Mockito;

import java.nio.channels.ClosedChannelException;
import java.util.List;

import static io.netty.handler.codec.http.HttpHeadersTestUtils.of;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class HttpObjectAggregatorTest {

    @Test
    public void testAggregate() {
        HttpObjectAggregator aggr = new HttpObjectAggregator(1024 * 1024);
        EmbeddedChannel embedder = new EmbeddedChannel(aggr);

        HttpRequest message = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "http://localhost");
        message.headers().set(of("X-Test"), true);
        HttpContent chunk1 = new DefaultHttpContent(Unpooled.copiedBuffer("test", CharsetUtil.US_ASCII));
        HttpContent chunk2 = new DefaultHttpContent(Unpooled.copiedBuffer("test2", CharsetUtil.US_ASCII));
        HttpContent chunk3 = new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER);
        assertFalse(embedder.writeInbound(message));
        assertFalse(embedder.writeInbound(chunk1));
        assertFalse(embedder.writeInbound(chunk2));

        // this should trigger a channelRead event so return true
        assertTrue(embedder.writeInbound(chunk3));
        assertTrue(embedder.finish());
        FullHttpRequest aggregatedMessage = embedder.readInbound();
        assertNotNull(aggregatedMessage);

        assertEquals(chunk1.content().readableBytes() + chunk2.content().readableBytes(),
                HttpUtil.getContentLength(aggregatedMessage));
        assertEquals(Boolean.TRUE.toString(), aggregatedMessage.headers().get(of("X-Test")));
        checkContentBuffer(aggregatedMessage);
        assertNull(embedder.readInbound());
    }

    private static void checkContentBuffer(FullHttpRequest aggregatedMessage) {
        CompositeByteBuf buffer = (CompositeByteBuf) aggregatedMessage.content();
        assertEquals(2, buffer.numComponents());
        List<ByteBuf> buffers = buffer.decompose(0, buffer.capacity());
        assertEquals(2, buffers.size());
        for (ByteBuf buf: buffers) {
            // This should be false as we decompose the buffer before to not have deep hierarchy
            assertFalse(buf instanceof CompositeByteBuf);
        }
        aggregatedMessage.release();
    }

    @Test
    public void testAggregateWithTrailer() {
        HttpObjectAggregator aggr = new HttpObjectAggregator(1024 * 1024);
        EmbeddedChannel embedder = new EmbeddedChannel(aggr);
        HttpRequest message = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "http://localhost");
        message.headers().set(of("X-Test"), true);
        HttpUtil.setTransferEncodingChunked(message, true);
        HttpContent chunk1 = new DefaultHttpContent(Unpooled.copiedBuffer("test", CharsetUtil.US_ASCII));
        HttpContent chunk2 = new DefaultHttpContent(Unpooled.copiedBuffer("test2", CharsetUtil.US_ASCII));
        LastHttpContent trailer = new DefaultLastHttpContent();
        trailer.trailingHeaders().set(of("X-Trailer"), true);

        assertFalse(embedder.writeInbound(message));
        assertFalse(embedder.writeInbound(chunk1));
        assertFalse(embedder.writeInbound(chunk2));

        // this should trigger a channelRead event so return true
        assertTrue(embedder.writeInbound(trailer));
        assertTrue(embedder.finish());
        FullHttpRequest aggregatedMessage = embedder.readInbound();
        assertNotNull(aggregatedMessage);

        assertEquals(chunk1.content().readableBytes() + chunk2.content().readableBytes(),
                HttpUtil.getContentLength(aggregatedMessage));
        assertEquals(Boolean.TRUE.toString(), aggregatedMessage.headers().get(of("X-Test")));
        assertEquals(Boolean.TRUE.toString(), aggregatedMessage.trailingHeaders().get(of("X-Trailer")));
        checkContentBuffer(aggregatedMessage);
        assertNull(embedder.readInbound());
    }

    @Test
    public void testOversizedRequest() {
        EmbeddedChannel embedder = new EmbeddedChannel(new HttpObjectAggregator(4));
        HttpRequest message = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.PUT, "http://localhost");
        HttpContent chunk1 = new DefaultHttpContent(Unpooled.copiedBuffer("test", CharsetUtil.US_ASCII));
        HttpContent chunk2 = new DefaultHttpContent(Unpooled.copiedBuffer("test2", CharsetUtil.US_ASCII));
        HttpContent chunk3 = LastHttpContent.EMPTY_LAST_CONTENT;

        assertFalse(embedder.writeInbound(message));
        assertFalse(embedder.writeInbound(chunk1));
        assertFalse(embedder.writeInbound(chunk2));

        FullHttpResponse response = embedder.readOutbound();
        assertEquals(HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, response.status());
        assertEquals("0", response.headers().get(HttpHeaderNames.CONTENT_LENGTH));
        assertFalse(embedder.isOpen());

        try {
            assertFalse(embedder.writeInbound(chunk3));
            fail();
        } catch (Exception e) {
            assertTrue(e instanceof ClosedChannelException);
        }

        assertFalse(embedder.finish());
    }

    @Test
    public void testOversizedRequestWithContentLengthAndDecoder() {
        EmbeddedChannel embedder = new EmbeddedChannel(new HttpRequestDecoder(), new HttpObjectAggregator(4, false));
        assertFalse(embedder.writeInbound(Unpooled.copiedBuffer(
                "PUT /upload HTTP/1.1\r\n" +
                        "Content-Length: 5\r\n\r\n", CharsetUtil.US_ASCII)));

        assertNull(embedder.readInbound());

        FullHttpResponse response = embedder.readOutbound();
        assertEquals(HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, response.status());
        assertEquals("0", response.headers().get(HttpHeaderNames.CONTENT_LENGTH));

        assertTrue(embedder.isOpen());

        assertFalse(embedder.writeInbound(Unpooled.wrappedBuffer(new byte[] { 1, 2, 3, 4 })));
        assertFalse(embedder.writeInbound(Unpooled.wrappedBuffer(new byte[] { 5 })));

        assertNull(embedder.readOutbound());

        assertFalse(embedder.writeInbound(Unpooled.copiedBuffer(
                "PUT /upload HTTP/1.1\r\n" +
                        "Content-Length: 2\r\n\r\n", CharsetUtil.US_ASCII)));

        assertEquals(HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, response.status());
        assertEquals("0", response.headers().get(HttpHeaderNames.CONTENT_LENGTH));

        assertThat(response, instanceOf(LastHttpContent.class));
        ReferenceCountUtil.release(response);

        assertTrue(embedder.isOpen());

        assertFalse(embedder.writeInbound(Unpooled.copiedBuffer(new byte[] { 1 })));
        assertNull(embedder.readOutbound());
        assertTrue(embedder.writeInbound(Unpooled.copiedBuffer(new byte[] { 2 })));
        assertNull(embedder.readOutbound());

        FullHttpRequest request = embedder.readInbound();
        assertEquals(HttpVersion.HTTP_1_1, request.protocolVersion());
        assertEquals(HttpMethod.PUT, request.method());
        assertEquals("/upload", request.uri());
        assertEquals(2, HttpUtil.getContentLength(request));

        byte[] actual = new byte[request.content().readableBytes()];
        request.content().readBytes(actual);
        assertArrayEquals(new byte[] { 1, 2 }, actual);
        request.release();

        assertFalse(embedder.finish());
    }

    @Test
    public void testOversizedRequestWithoutKeepAlive() {
        // send an HTTP/1.0 request with no keep-alive header
        HttpRequest message = new DefaultHttpRequest(HttpVersion.HTTP_1_0, HttpMethod.PUT, "http://localhost");
        HttpUtil.setContentLength(message, 5);
        checkOversizedRequest(message);
    }

    @Test
    public void testOversizedRequestWithContentLength() {
        HttpRequest message = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.PUT, "http://localhost");
        HttpUtil.setContentLength(message, 5);
        checkOversizedRequest(message);
    }

    private static void checkOversizedRequest(HttpRequest message) {
        EmbeddedChannel embedder = new EmbeddedChannel(new HttpObjectAggregator(4));

        assertFalse(embedder.writeInbound(message));
        HttpResponse response = embedder.readOutbound();
        assertEquals(HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, response.status());
        assertEquals("0", response.headers().get(HttpHeaderNames.CONTENT_LENGTH));

        assertThat(response, instanceOf(LastHttpContent.class));
        ReferenceCountUtil.release(response);

        if (serverShouldCloseConnection(message, response)) {
            assertFalse(embedder.isOpen());

            try {
                embedder.writeInbound(new DefaultHttpContent(Unpooled.EMPTY_BUFFER));
                fail();
            } catch (Exception e) {
                assertThat(e, instanceOf(ClosedChannelException.class));
                // expected
            }
            assertFalse(embedder.finish());
        } else {
            assertTrue(embedder.isOpen());
            assertFalse(embedder.writeInbound(new DefaultHttpContent(Unpooled.copiedBuffer(new byte[8]))));
            assertFalse(embedder.writeInbound(new DefaultHttpContent(Unpooled.copiedBuffer(new byte[8]))));

            // Now start a new message and ensure we will not reject it again.
            HttpRequest message2 = new DefaultHttpRequest(HttpVersion.HTTP_1_0, HttpMethod.PUT, "http://localhost");
            HttpUtil.setContentLength(message, 2);

            assertFalse(embedder.writeInbound(message2));
            assertNull(embedder.readOutbound());
            assertFalse(embedder.writeInbound(new DefaultHttpContent(Unpooled.copiedBuffer(new byte[] { 1 }))));
            assertNull(embedder.readOutbound());
            assertTrue(embedder.writeInbound(new DefaultLastHttpContent(Unpooled.copiedBuffer(new byte[] { 2 }))));
            assertNull(embedder.readOutbound());

            FullHttpRequest request = embedder.readInbound();
            assertEquals(message2.protocolVersion(), request.protocolVersion());
            assertEquals(message2.method(), request.method());
            assertEquals(message2.uri(), request.uri());
            assertEquals(2, HttpUtil.getContentLength(request));

            byte[] actual = new byte[request.content().readableBytes()];
            request.content().readBytes(actual);
            assertArrayEquals(new byte[] { 1, 2 }, actual);
            request.release();

            assertFalse(embedder.finish());
        }
    }

    private static boolean serverShouldCloseConnection(HttpRequest message, HttpResponse response) {
        // If the response wasn't keep-alive, the server should close the connection.
        if (!HttpUtil.isKeepAlive(response)) {
            return true;
        }
        // The connection should only be kept open if Expect: 100-continue is set,
        // or if keep-alive is on.
        if (HttpUtil.is100ContinueExpected(message)) {
            return false;
        }
        if (HttpUtil.isKeepAlive(message)) {
            return false;
        }
        return true;
    }

    @Test
    public void testOversizedResponse() {
        EmbeddedChannel embedder = new EmbeddedChannel(new HttpObjectAggregator(4));
        HttpResponse message = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        HttpContent chunk1 = new DefaultHttpContent(Unpooled.copiedBuffer("test", CharsetUtil.US_ASCII));
        HttpContent chunk2 = new DefaultHttpContent(Unpooled.copiedBuffer("test2", CharsetUtil.US_ASCII));

        assertFalse(embedder.writeInbound(message));
        assertFalse(embedder.writeInbound(chunk1));

        try {
            embedder.writeInbound(chunk2);
            fail();
        } catch (TooLongFrameException expected) {
            // Expected
        }

        assertFalse(embedder.isOpen());
        assertFalse(embedder.finish());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidConstructorUsage() {
        new HttpObjectAggregator(-1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidMaxCumulationBufferComponents() {
        HttpObjectAggregator aggr = new HttpObjectAggregator(Integer.MAX_VALUE);
        aggr.setMaxCumulationBufferComponents(1);
    }

    @Test(expected = IllegalStateException.class)
    public void testSetMaxCumulationBufferComponentsAfterInit() throws Exception {
        HttpObjectAggregator aggr = new HttpObjectAggregator(Integer.MAX_VALUE);
        ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
        aggr.handlerAdded(ctx);
        Mockito.verifyNoMoreInteractions(ctx);
        aggr.setMaxCumulationBufferComponents(10);
    }

    @Test
    public void testAggregateTransferEncodingChunked() {
        HttpObjectAggregator aggr = new HttpObjectAggregator(1024 * 1024);
        EmbeddedChannel embedder = new EmbeddedChannel(aggr);

        HttpRequest message = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.PUT, "http://localhost");
        message.headers().set(of("X-Test"), true);
        message.headers().set(of("Transfer-Encoding"), of("Chunked"));
        HttpContent chunk1 = new DefaultHttpContent(Unpooled.copiedBuffer("test", CharsetUtil.US_ASCII));
        HttpContent chunk2 = new DefaultHttpContent(Unpooled.copiedBuffer("test2", CharsetUtil.US_ASCII));
        HttpContent chunk3 = LastHttpContent.EMPTY_LAST_CONTENT;
        assertFalse(embedder.writeInbound(message));
        assertFalse(embedder.writeInbound(chunk1));
        assertFalse(embedder.writeInbound(chunk2));

        // this should trigger a channelRead event so return true
        assertTrue(embedder.writeInbound(chunk3));
        assertTrue(embedder.finish());
        FullHttpRequest aggregatedMessage = embedder.readInbound();
        assertNotNull(aggregatedMessage);

        assertEquals(chunk1.content().readableBytes() + chunk2.content().readableBytes(),
                HttpUtil.getContentLength(aggregatedMessage));
        assertEquals(Boolean.TRUE.toString(), aggregatedMessage.headers().get(of("X-Test")));
        checkContentBuffer(aggregatedMessage);
        assertNull(embedder.readInbound());
    }

    @Test
    public void testBadRequest() {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpRequestDecoder(), new HttpObjectAggregator(1024 * 1024));
        ch.writeInbound(Unpooled.copiedBuffer("GET / HTTP/1.0 with extra\r\n", CharsetUtil.UTF_8));
        Object inbound = ch.readInbound();
        assertThat(inbound, is(instanceOf(FullHttpRequest.class)));
        assertTrue(((DecoderResultProvider) inbound).decoderResult().isFailure());
        assertNull(ch.readInbound());
        ch.finish();
    }

    @Test
    public void testBadResponse() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpResponseDecoder(), new HttpObjectAggregator(1024 * 1024));
        ch.writeInbound(Unpooled.copiedBuffer("HTTP/1.0 BAD_CODE Bad Server\r\n", CharsetUtil.UTF_8));
        Object inbound = ch.readInbound();
        assertThat(inbound, is(instanceOf(FullHttpResponse.class)));
        assertTrue(((DecoderResultProvider) inbound).decoderResult().isFailure());
        assertNull(ch.readInbound());
        ch.finish();
    }

    @Test
    public void testOversizedRequestWith100Continue() {
        EmbeddedChannel embedder = new EmbeddedChannel(new HttpObjectAggregator(8));

        // Send an oversized request with 100 continue.
        HttpRequest message = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.PUT, "http://localhost");
        HttpUtil.set100ContinueExpected(message, true);
        HttpUtil.setContentLength(message, 16);

        HttpContent chunk1 = new DefaultHttpContent(Unpooled.copiedBuffer("some", CharsetUtil.US_ASCII));
        HttpContent chunk2 = new DefaultHttpContent(Unpooled.copiedBuffer("test", CharsetUtil.US_ASCII));
        HttpContent chunk3 = LastHttpContent.EMPTY_LAST_CONTENT;

        // Send a request with 100-continue + large Content-Length header value.
        assertFalse(embedder.writeInbound(message));

        // The aggregator should respond with '413.'
        FullHttpResponse response = embedder.readOutbound();
        assertEquals(HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, response.status());
        assertEquals("0", response.headers().get(HttpHeaderNames.CONTENT_LENGTH));

        // An ill-behaving client could continue to send data without a respect, and such data should be discarded.
        assertFalse(embedder.writeInbound(chunk1));

        // The aggregator should not close the connection because keep-alive is on.
        assertTrue(embedder.isOpen());

        // Now send a valid request.
        HttpRequest message2 = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.PUT, "http://localhost");

        assertFalse(embedder.writeInbound(message2));
        assertFalse(embedder.writeInbound(chunk2));
        assertTrue(embedder.writeInbound(chunk3));

        FullHttpRequest fullMsg = embedder.readInbound();
        assertNotNull(fullMsg);

        assertEquals(
                chunk2.content().readableBytes() + chunk3.content().readableBytes(),
                HttpUtil.getContentLength(fullMsg));

        assertEquals(HttpUtil.getContentLength(fullMsg), fullMsg.content().readableBytes());

        fullMsg.release();
        assertFalse(embedder.finish());
    }

    @Test
    public void testUnsupportedExpectHeaderExpectation() {
        runUnsupportedExceptHeaderExceptionTest(true);
        runUnsupportedExceptHeaderExceptionTest(false);
    }

    private static void runUnsupportedExceptHeaderExceptionTest(final boolean close) {
        final HttpObjectAggregator aggregator;
        final int maxContentLength = 4;
        if (close) {
            aggregator = new HttpObjectAggregator(maxContentLength, true);
        } else {
            aggregator = new HttpObjectAggregator(maxContentLength);
        }
        final EmbeddedChannel embedder = new EmbeddedChannel(new HttpRequestDecoder(), aggregator);

        assertFalse(embedder.writeInbound(Unpooled.copiedBuffer(
                "GET / HTTP/1.1\r\n" +
                        "Expect: chocolate=yummy\r\n" +
                        "Content-Length: 100\r\n\r\n", CharsetUtil.US_ASCII)));
        assertNull(embedder.readInbound());

        final FullHttpResponse response = embedder.readOutbound();
        assertEquals(HttpResponseStatus.EXPECTATION_FAILED, response.status());
        assertEquals("0", response.headers().get(HttpHeaderNames.CONTENT_LENGTH));
        response.release();

        if (close) {
            assertFalse(embedder.isOpen());
        } else {
            // keep-alive is on by default in HTTP/1.1, so the connection should be still alive
            assertTrue(embedder.isOpen());

            // the decoder should be reset by the aggregator at this point and be able to decode the next request
            assertTrue(embedder.writeInbound(Unpooled.copiedBuffer("GET / HTTP/1.1\r\n\r\n", CharsetUtil.US_ASCII)));

            final FullHttpRequest request = embedder.readInbound();
            assertThat(request.method(), is(HttpMethod.GET));
            assertThat(request.uri(), is("/"));
            assertThat(request.content().readableBytes(), is(0));
            request.release();
        }

        assertFalse(embedder.finish());
    }

    @Test
    public void testValidRequestWith100ContinueAndDecoder() {
        EmbeddedChannel embedder = new EmbeddedChannel(new HttpRequestDecoder(), new HttpObjectAggregator(100));
        embedder.writeInbound(Unpooled.copiedBuffer(
            "GET /upload HTTP/1.1\r\n" +
                "Expect: 100-continue\r\n" +
                "Content-Length: 0\r\n\r\n", CharsetUtil.US_ASCII));

        FullHttpResponse response = embedder.readOutbound();
        assertEquals(HttpResponseStatus.CONTINUE, response.status());
        FullHttpRequest request = embedder.readInbound();
        assertFalse(request.headers().contains(HttpHeaderNames.EXPECT));
        request.release();
        response.release();
        assertFalse(embedder.finish());
    }

    @Test
    public void testOversizedRequestWith100ContinueAndDecoder() {
        EmbeddedChannel embedder = new EmbeddedChannel(new HttpRequestDecoder(), new HttpObjectAggregator(4));
        embedder.writeInbound(Unpooled.copiedBuffer(
                "PUT /upload HTTP/1.1\r\n" +
                        "Expect: 100-continue\r\n" +
                        "Content-Length: 100\r\n\r\n", CharsetUtil.US_ASCII));

        assertNull(embedder.readInbound());

        FullHttpResponse response = embedder.readOutbound();
        assertEquals(HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, response.status());
        assertEquals("0", response.headers().get(HttpHeaderNames.CONTENT_LENGTH));

        // Keep-alive is on by default in HTTP/1.1, so the connection should be still alive.
        assertTrue(embedder.isOpen());

        // The decoder should be reset by the aggregator at this point and be able to decode the next request.
        embedder.writeInbound(Unpooled.copiedBuffer("GET /max-upload-size HTTP/1.1\r\n\r\n", CharsetUtil.US_ASCII));

        FullHttpRequest request = embedder.readInbound();
        assertThat(request.method(), is(HttpMethod.GET));
        assertThat(request.uri(), is("/max-upload-size"));
        assertThat(request.content().readableBytes(), is(0));
        request.release();

        assertFalse(embedder.finish());
    }

    @Test
    public void testOversizedRequestWith100ContinueAndDecoderCloseConnection() {
        EmbeddedChannel embedder = new EmbeddedChannel(new HttpRequestDecoder(), new HttpObjectAggregator(4, true));
        embedder.writeInbound(Unpooled.copiedBuffer(
                "PUT /upload HTTP/1.1\r\n" +
                        "Expect: 100-continue\r\n" +
                        "Content-Length: 100\r\n\r\n", CharsetUtil.US_ASCII));

        assertNull(embedder.readInbound());

        FullHttpResponse response = embedder.readOutbound();
        assertEquals(HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, response.status());
        assertEquals("0", response.headers().get(HttpHeaderNames.CONTENT_LENGTH));

        // We are forcing the connection closed if an expectation is exceeded.
        assertFalse(embedder.isOpen());
        assertFalse(embedder.finish());
    }

    @Test
    public void testRequestAfterOversized100ContinueAndDecoder() {
        EmbeddedChannel embedder = new EmbeddedChannel(new HttpRequestDecoder(), new HttpObjectAggregator(15));

        // Write first request with Expect: 100-continue.
        HttpRequest message = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.PUT, "http://localhost");
        HttpUtil.set100ContinueExpected(message, true);
        HttpUtil.setContentLength(message, 16);

        HttpContent chunk1 = new DefaultHttpContent(Unpooled.copiedBuffer("some", CharsetUtil.US_ASCII));
        HttpContent chunk2 = new DefaultHttpContent(Unpooled.copiedBuffer("test", CharsetUtil.US_ASCII));
        HttpContent chunk3 = LastHttpContent.EMPTY_LAST_CONTENT;

        // Send a request with 100-continue + large Content-Length header value.
        assertFalse(embedder.writeInbound(message));

        // The aggregator should respond with '413'.
        FullHttpResponse response = embedder.readOutbound();
        assertEquals(HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, response.status());
        assertEquals("0", response.headers().get(HttpHeaderNames.CONTENT_LENGTH));

        // An ill-behaving client could continue to send data without a respect, and such data should be discarded.
        assertFalse(embedder.writeInbound(chunk1));

        // The aggregator should not close the connection because keep-alive is on.
        assertTrue(embedder.isOpen());

        // Now send a valid request.
        HttpRequest message2 = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.PUT, "http://localhost");

        assertFalse(embedder.writeInbound(message2));
        assertFalse(embedder.writeInbound(chunk2));
        assertTrue(embedder.writeInbound(chunk3));

        FullHttpRequest fullMsg = embedder.readInbound();
        assertNotNull(fullMsg);

        assertEquals(
                chunk2.content().readableBytes() + chunk3.content().readableBytes(),
                HttpUtil.getContentLength(fullMsg));

        assertEquals(HttpUtil.getContentLength(fullMsg), fullMsg.content().readableBytes());

        fullMsg.release();
        assertFalse(embedder.finish());
    }

    @Test
    public void testReplaceAggregatedRequest() {
        EmbeddedChannel embedder = new EmbeddedChannel(new HttpObjectAggregator(1024 * 1024));

        Exception boom = new Exception("boom");
        HttpRequest req = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "http://localhost");
        req.setDecoderResult(DecoderResult.failure(boom));

        assertTrue(embedder.writeInbound(req) && embedder.finish());

        FullHttpRequest aggregatedReq = embedder.readInbound();
        FullHttpRequest replacedReq = aggregatedReq.replace(Unpooled.EMPTY_BUFFER);

        assertEquals(replacedReq.decoderResult(), aggregatedReq.decoderResult());
        aggregatedReq.release();
        replacedReq.release();
    }

    @Test
    public void testReplaceAggregatedResponse() {
        EmbeddedChannel embedder = new EmbeddedChannel(new HttpObjectAggregator(1024 * 1024));

        Exception boom = new Exception("boom");
        HttpResponse rep = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        rep.setDecoderResult(DecoderResult.failure(boom));

        assertTrue(embedder.writeInbound(rep) && embedder.finish());

        FullHttpResponse aggregatedRep = embedder.readInbound();
        FullHttpResponse replacedRep = aggregatedRep.replace(Unpooled.EMPTY_BUFFER);

        assertEquals(replacedRep.decoderResult(), aggregatedRep.decoderResult());
        aggregatedRep.release();
        replacedRep.release();
    }

    @Test
    public void testSelectiveRequestAggregation() {
        HttpObjectAggregator myPostAggregator = new HttpObjectAggregator(1024 * 1024) {
            @Override
            protected boolean isStartMessage(HttpObject msg) throws Exception {
                if (msg instanceof HttpRequest) {
                    HttpRequest request = (HttpRequest) msg;
                    HttpMethod method = request.method();

                    if (method.equals(HttpMethod.POST)) {
                        return true;
                    }
                }

                return false;
            }
        };

        EmbeddedChannel channel = new EmbeddedChannel(myPostAggregator);

        try {
            // Aggregate: POST
            HttpRequest request1 = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/");
            HttpContent content1 = new DefaultHttpContent(Unpooled.copiedBuffer("Hello, World!", CharsetUtil.UTF_8));
            request1.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN);

            assertTrue(channel.writeInbound(request1, content1, LastHttpContent.EMPTY_LAST_CONTENT));

            // Getting an aggregated response out
            Object msg1 = channel.readInbound();
            try {
                assertTrue(msg1 instanceof FullHttpRequest);
            } finally {
                ReferenceCountUtil.release(msg1);
            }

            // Don't aggregate: non-POST
            HttpRequest request2 = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.PUT, "/");
            HttpContent content2 = new DefaultHttpContent(Unpooled.copiedBuffer("Hello, World!", CharsetUtil.UTF_8));
            request2.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN);

            try {
                assertTrue(channel.writeInbound(request2, content2, LastHttpContent.EMPTY_LAST_CONTENT));

                // Getting the same response objects out
                assertSame(request2, channel.readInbound());
                assertSame(content2, channel.readInbound());
                assertSame(LastHttpContent.EMPTY_LAST_CONTENT, channel.readInbound());
            } finally {
              ReferenceCountUtil.release(request2);
              ReferenceCountUtil.release(content2);
            }

            assertFalse(channel.finish());
        } finally {
          channel.close();
        }
    }

    @Test
    public void testSelectiveResponseAggregation() {
        HttpObjectAggregator myTextAggregator = new HttpObjectAggregator(1024 * 1024) {
            @Override
            protected boolean isStartMessage(HttpObject msg) throws Exception {
                if (msg instanceof HttpResponse) {
                    HttpResponse response = (HttpResponse) msg;
                    HttpHeaders headers = response.headers();

                    String contentType = headers.get(HttpHeaderNames.CONTENT_TYPE);
                    if (AsciiString.contentEqualsIgnoreCase(contentType, HttpHeaderValues.TEXT_PLAIN)) {
                        return true;
                    }
                }

                return false;
            }
        };

        EmbeddedChannel channel = new EmbeddedChannel(myTextAggregator);

        try {
            // Aggregate: text/plain
            HttpResponse response1 = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            HttpContent content1 = new DefaultHttpContent(Unpooled.copiedBuffer("Hello, World!", CharsetUtil.UTF_8));
            response1.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN);

            assertTrue(channel.writeInbound(response1, content1, LastHttpContent.EMPTY_LAST_CONTENT));

            // Getting an aggregated response out
            Object msg1 = channel.readInbound();
            try {
                assertTrue(msg1 instanceof FullHttpResponse);
            } finally {
                ReferenceCountUtil.release(msg1);
            }

            // Don't aggregate: application/json
            HttpResponse response2 = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            HttpContent content2 = new DefaultHttpContent(Unpooled.copiedBuffer("{key: 'value'}", CharsetUtil.UTF_8));
            response2.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);

            try {
                assertTrue(channel.writeInbound(response2, content2, LastHttpContent.EMPTY_LAST_CONTENT));

                // Getting the same response objects out
                assertSame(response2, channel.readInbound());
                assertSame(content2, channel.readInbound());
                assertSame(LastHttpContent.EMPTY_LAST_CONTENT, channel.readInbound());
            } finally {
                ReferenceCountUtil.release(response2);
                ReferenceCountUtil.release(content2);
            }

            assertFalse(channel.finish());
        } finally {
          channel.close();
        }
    }
}
