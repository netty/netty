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
import io.netty5.buffer.api.CompositeBuffer;
import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.handler.codec.DecoderResultProvider;
import io.netty5.util.AsciiString;
import io.netty5.util.CharsetUtil;
import io.netty5.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;

import java.nio.channels.ClosedChannelException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static io.netty5.buffer.api.CompositeBuffer.isComposite;
import static io.netty5.buffer.api.DefaultBufferAllocators.preferredAllocator;
import static io.netty5.handler.codec.http.HttpHeadersTestUtils.of;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HttpObjectAggregatorTest {

    @Test
    public void testAggregate() {
        HttpObjectAggregator<?> aggr = new HttpObjectAggregator<DefaultHttpContent>(1024 * 1024);
        EmbeddedChannel embedder = new EmbeddedChannel(aggr);

        HttpRequest message = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "http://localhost");
        message.headers().set(of("X-Test"), true);
        final byte[] test = "test".getBytes(StandardCharsets.US_ASCII);
        HttpContent<?> chunk1 = new DefaultHttpContent(preferredAllocator().copyOf(test));
        final byte[] test2 = "test".getBytes(StandardCharsets.US_ASCII);
        HttpContent<?> chunk2 = new DefaultHttpContent(preferredAllocator().copyOf(test2));
        HttpContent<?> chunk3 = new DefaultLastHttpContent(preferredAllocator().allocate(0));
        int expectedContentLength = chunk1.payload().readableBytes() + chunk2.payload().readableBytes();
        assertFalse(embedder.writeInbound(message));
        assertFalse(embedder.writeInbound(chunk1));
        assertFalse(embedder.writeInbound(chunk2));

        // this should trigger a channelRead event so return true
        assertTrue(embedder.writeInbound(chunk3));
        assertTrue(embedder.finish());
        FullHttpRequest aggregatedMessage = embedder.readInbound();
        assertNotNull(aggregatedMessage);
        assertTrue(aggregatedMessage.isAccessible());
        assertEquals(expectedContentLength, HttpUtil.getContentLength(aggregatedMessage));
        assertEquals(Boolean.TRUE.toString(), aggregatedMessage.headers().get(of("X-Test")));
        checkContentBuffer(aggregatedMessage);
        assertNull(embedder.readInbound());
    }

    private static void checkContentBuffer(FullHttpRequest aggregatedMessage) {
        CompositeBuffer buffer = (CompositeBuffer) aggregatedMessage.payload();
        assertEquals(2, buffer.countComponents());
        Buffer[] buffers = buffer.decomposeBuffer();
        assertEquals(2, buffers.length);
        for (Buffer buf: buffers) {
            // This should be false as we decompose the buffer before to not have deep hierarchy
            assertFalse(isComposite(buf));
        }
    }

    @Test
    public void testAggregateWithTrailer() {
        HttpObjectAggregator<?> aggr = new HttpObjectAggregator<DefaultHttpContent>(1024 * 1024);
        EmbeddedChannel embedder = new EmbeddedChannel(aggr);
        HttpRequest message = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "http://localhost");
        message.headers().set(of("X-Test"), true);
        HttpUtil.setTransferEncodingChunked(message, true);
        final byte[] test = "test".getBytes(StandardCharsets.US_ASCII);
        HttpContent<?> chunk1 = new DefaultHttpContent(preferredAllocator().copyOf(test));
        final byte[] test2 = "test".getBytes(StandardCharsets.US_ASCII);
        HttpContent<?> chunk2 = new DefaultHttpContent(preferredAllocator().copyOf(test2));
        int expectedContentLength = chunk1.payload().readableBytes() + chunk2.payload().readableBytes();
        LastHttpContent<?> trailer = new DefaultLastHttpContent(preferredAllocator().allocate(0));
        trailer.trailingHeaders().set(of("X-Trailer"), true);

        assertFalse(embedder.writeInbound(message));
        assertFalse(embedder.writeInbound(chunk1));
        assertFalse(embedder.writeInbound(chunk2));

        // this should trigger a channelRead event so return true
        assertTrue(embedder.writeInbound(trailer));
        assertTrue(embedder.finish());
        FullHttpRequest aggregatedMessage = embedder.readInbound();
        assertNotNull(aggregatedMessage);

        assertEquals(expectedContentLength, HttpUtil.getContentLength(aggregatedMessage));
        assertEquals(Boolean.TRUE.toString(), aggregatedMessage.headers().get(of("X-Test")));
        assertEquals(Boolean.TRUE.toString(), aggregatedMessage.trailingHeaders().get(of("X-Trailer")));
        checkContentBuffer(aggregatedMessage);
        assertNull(embedder.readInbound());
    }

    @Test
    public void testOversizedRequest() {
        final EmbeddedChannel embedder = new EmbeddedChannel(new HttpObjectAggregator<DefaultHttpContent>(4));
        HttpRequest message = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.PUT, "http://localhost");
        final byte[] test = "test".getBytes(StandardCharsets.US_ASCII);
        HttpContent<?> chunk1 = new DefaultHttpContent(preferredAllocator().copyOf(test));
        final byte[] test2 = "test".getBytes(StandardCharsets.US_ASCII);
        HttpContent<?> chunk2 = new DefaultHttpContent(preferredAllocator().copyOf(test2));
        final HttpContent<?> chunk3 = new EmptyLastHttpContent(preferredAllocator());

        assertFalse(embedder.writeInbound(message));
        assertFalse(embedder.writeInbound(chunk1));
        assertFalse(embedder.writeInbound(chunk2));

        FullHttpResponse response = embedder.readOutbound();
        assertEquals(HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, response.status());
        assertEquals("0", response.headers().get(HttpHeaderNames.CONTENT_LENGTH));
        assertFalse(embedder.isOpen());

        assertThrows(ClosedChannelException.class, () -> embedder.writeInbound(chunk3));

        assertFalse(embedder.finish());
    }

    @Test
    public void testOversizedRequestWithContentLengthAndDecoder() {
        EmbeddedChannel embedder = new EmbeddedChannel(new HttpRequestDecoder(),
                new HttpObjectAggregator<DefaultHttpContent>(4, false));
        assertFalse(embedder.writeInbound(copiedBuffer(embedder.bufferAllocator(),
                "PUT /upload HTTP/1.1\r\n" +
                        "Content-Length: 5\r\n\r\n", CharsetUtil.US_ASCII)));

        assertNull(embedder.readInbound());

        FullHttpResponse response = embedder.readOutbound();
        assertEquals(HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, response.status());
        assertEquals("0", response.headers().get(HttpHeaderNames.CONTENT_LENGTH));

        assertTrue(embedder.isOpen());

        assertFalse(embedder.writeInbound(embedder.bufferAllocator().allocate(4)
                .writeBytes(new byte[] { 1, 2, 3, 4 })));
        assertFalse(embedder.writeInbound(embedder.bufferAllocator().allocate(1)
                .writeBytes(new byte[] { 5 })));

        assertNull(embedder.readOutbound());

        assertFalse(embedder.writeInbound(copiedBuffer(embedder.bufferAllocator(),
                "PUT /upload HTTP/1.1\r\n" +
                        "Content-Length: 2\r\n\r\n", CharsetUtil.US_ASCII)));

        assertEquals(HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, response.status());
        assertEquals("0", response.headers().get(HttpHeaderNames.CONTENT_LENGTH));

        assertThat(response, instanceOf(LastHttpContent.class));
        ReferenceCountUtil.release(response);

        assertTrue(embedder.isOpen());

        assertFalse(embedder.writeInbound(copiedBuffer(embedder.bufferAllocator(), new byte[] { 1 })));
        assertNull(embedder.readOutbound());
        assertTrue(embedder.writeInbound(copiedBuffer(embedder.bufferAllocator(), new byte[] { 2 })));
        assertNull(embedder.readOutbound());

        FullHttpRequest request = embedder.readInbound();
        assertEquals(HttpVersion.HTTP_1_1, request.protocolVersion());
        assertEquals(HttpMethod.PUT, request.method());
        assertEquals("/upload", request.uri());
        assertEquals(2, HttpUtil.getContentLength(request));

        final Buffer payload = request.payload();
        byte[] actual = new byte[payload.readableBytes()];
        payload.copyInto(payload.readerOffset(), actual, 0, payload.readableBytes());
        assertArrayEquals(new byte[] { 1, 2 }, actual);
        request.close();

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
        final EmbeddedChannel embedder = new EmbeddedChannel(new HttpObjectAggregator<DefaultHttpContent>(4));

        assertFalse(embedder.writeInbound(message));
        HttpResponse response = embedder.readOutbound();
        assertEquals(HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, response.status());
        assertEquals("0", response.headers().get(HttpHeaderNames.CONTENT_LENGTH));

        assertThat(response, instanceOf(LastHttpContent.class));
        ReferenceCountUtil.release(response);

        if (serverShouldCloseConnection(message, response)) {
            assertFalse(embedder.isOpen());

            assertThrows(ClosedChannelException.class,
                    () -> embedder.writeInbound(new DefaultHttpContent(preferredAllocator().allocate(0))));

            assertFalse(embedder.finish());
        } else {
            assertTrue(embedder.isOpen());
            assertFalse(embedder.writeInbound(new DefaultHttpContent(preferredAllocator().copyOf(new byte[8]))));
            assertFalse(embedder.writeInbound(new DefaultHttpContent(preferredAllocator().copyOf(new byte[8]))));

            // Now start a new message and ensure we will not reject it again.
            HttpRequest message2 = new DefaultHttpRequest(HttpVersion.HTTP_1_0, HttpMethod.PUT, "http://localhost");
            HttpUtil.setContentLength(message, 2);

            assertFalse(embedder.writeInbound(message2));
            assertNull(embedder.readOutbound());
            assertFalse(embedder.writeInbound(new DefaultHttpContent(preferredAllocator().copyOf(new byte[] { 1 }))));
            assertNull(embedder.readOutbound());
            assertTrue(embedder.writeInbound(new DefaultLastHttpContent(
                    preferredAllocator().copyOf(new byte[] { 2 }))));
            assertNull(embedder.readOutbound());

            FullHttpRequest request = embedder.readInbound();
            assertEquals(message2.protocolVersion(), request.protocolVersion());
            assertEquals(message2.method(), request.method());
            assertEquals(message2.uri(), request.uri());
            assertEquals(2, HttpUtil.getContentLength(request));

            final Buffer payload = request.payload();
            byte[] actual = new byte[payload.readableBytes()];
            payload.copyInto(payload.readerOffset(), actual, 0, payload.readableBytes());
            assertArrayEquals(new byte[] { 1, 2 }, actual);
            request.close();

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
        return !HttpUtil.isKeepAlive(message);
    }

    @Test
    public void testOversizedResponse() {
        final EmbeddedChannel embedder =
                new EmbeddedChannel(new HttpObjectAggregator<DefaultHttpContent>(4));
        HttpResponse message = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        final byte[] test = "test".getBytes(StandardCharsets.US_ASCII);
        HttpContent<?> chunk1 = new DefaultHttpContent(preferredAllocator().copyOf(test));
        final byte[] test2 = "test".getBytes(StandardCharsets.US_ASCII);
        HttpContent<?> chunk2 = new DefaultHttpContent(preferredAllocator().copyOf(test2));

        assertFalse(embedder.writeInbound(message));
        assertFalse(embedder.writeInbound(chunk1));

        assertThrows(TooLongHttpContentException.class, () -> embedder.writeInbound(chunk2));

        assertFalse(embedder.isOpen());
        assertFalse(embedder.finish());
    }

    @Test
    public void testInvalidConstructorUsage() {
        assertThrows(IllegalArgumentException.class, () -> new HttpObjectAggregator<DefaultHttpContent>(-1));
    }

    @Test
    public void testAggregateTransferEncodingChunked() {
        HttpObjectAggregator<?> aggr = new HttpObjectAggregator<DefaultHttpContent>(1024 * 1024);
        EmbeddedChannel embedder = new EmbeddedChannel(aggr);

        HttpRequest message = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.PUT, "http://localhost");
        message.headers().set(of("X-Test"), true);
        message.headers().set(of("Transfer-Encoding"), of("Chunked"));
        final byte[] test = "test".getBytes(StandardCharsets.US_ASCII);
        HttpContent<?> chunk1 = new DefaultHttpContent(preferredAllocator().copyOf(test));
        final byte[] test2 = "test".getBytes(StandardCharsets.US_ASCII);
        HttpContent<?> chunk2 = new DefaultHttpContent(preferredAllocator().copyOf(test2));
        int expectedContentLength = chunk1.payload().readableBytes() + chunk2.payload().readableBytes();
        HttpContent<?> chunk3 = new EmptyLastHttpContent(preferredAllocator());
        assertFalse(embedder.writeInbound(message));
        assertFalse(embedder.writeInbound(chunk1));
        assertFalse(embedder.writeInbound(chunk2));

        // this should trigger a channelRead event so return true
        assertTrue(embedder.writeInbound(chunk3));
        assertTrue(embedder.finish());
        FullHttpRequest aggregatedMessage = embedder.readInbound();
        assertNotNull(aggregatedMessage);

        assertEquals(expectedContentLength, HttpUtil.getContentLength(aggregatedMessage));
        assertEquals(Boolean.TRUE.toString(), aggregatedMessage.headers().get(of("X-Test")));
        checkContentBuffer(aggregatedMessage);
        assertNull(embedder.readInbound());
    }

    @Test
    public void testBadRequest() {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpRequestDecoder(),
                new HttpObjectAggregator<DefaultHttpContent>(1024 * 1024));
        ch.writeInbound(copiedBuffer(ch.bufferAllocator(), "GET / HTTP/1.0 with extra\r\n", CharsetUtil.UTF_8));
        Object inbound = ch.readInbound();
        assertThat(inbound, is(instanceOf(FullHttpRequest.class)));
        assertTrue(((DecoderResultProvider) inbound).decoderResult().isFailure());
        assertNull(ch.readInbound());
        ch.finish();
    }

    @Test
    public void testBadResponse() {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpResponseDecoder(),
                new HttpObjectAggregator<DefaultHttpContent>(1024 * 1024));
        ch.writeInbound(copiedBuffer(ch.bufferAllocator(), "HTTP/1.0 BAD_CODE Bad Server\r\n", CharsetUtil.UTF_8));
        Object inbound = ch.readInbound();
        assertThat(inbound, is(instanceOf(FullHttpResponse.class)));
        assertTrue(((DecoderResultProvider) inbound).decoderResult().isFailure());
        assertNull(ch.readInbound());
        ch.finish();
    }

    @Test
    public void testOversizedRequestWith100Continue() {
        EmbeddedChannel embedder = new EmbeddedChannel(new HttpObjectAggregator<DefaultHttpContent>(8));

        // Send an oversized request with 100 continue.
        HttpRequest message = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.PUT, "http://localhost");
        HttpUtil.set100ContinueExpected(message, true);
        HttpUtil.setContentLength(message, 16);

        final byte[] some = "some".getBytes(StandardCharsets.US_ASCII);
        HttpContent<?> chunk1 = new DefaultHttpContent(preferredAllocator().copyOf(some));
        final byte[] test2 = "test".getBytes(StandardCharsets.US_ASCII);
        HttpContent<?> chunk2 = new DefaultHttpContent(preferredAllocator().copyOf(test2));
        HttpContent<?> chunk3 = new EmptyLastHttpContent(preferredAllocator());

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
        int expectedContentLength = chunk2.payload().readableBytes() + chunk3.payload().readableBytes();
        assertFalse(embedder.writeInbound(message2));
        assertFalse(embedder.writeInbound(chunk2));
        assertTrue(embedder.writeInbound(chunk3));

        FullHttpRequest fullMsg = embedder.readInbound();
        assertNotNull(fullMsg);

        assertEquals(expectedContentLength, HttpUtil.getContentLength(fullMsg));

        assertEquals(HttpUtil.getContentLength(fullMsg), fullMsg.payload().readableBytes());

        fullMsg.close();
        assertFalse(embedder.finish());
    }

    @Test
    public void testUnsupportedExpectHeaderExpectation() {
        runUnsupportedExceptHeaderExceptionTest(true);
        runUnsupportedExceptHeaderExceptionTest(false);
    }

    private static void runUnsupportedExceptHeaderExceptionTest(final boolean close) {
        final HttpObjectAggregator<?> aggregator;
        final int maxContentLength = 4;
        if (close) {
            aggregator = new HttpObjectAggregator<DefaultHttpContent>(maxContentLength, true);
        } else {
            aggregator = new HttpObjectAggregator<DefaultHttpContent>(maxContentLength);
        }
        final EmbeddedChannel embedder = new EmbeddedChannel(new HttpRequestDecoder(), aggregator);

        assertFalse(embedder.writeInbound(copiedBuffer(embedder.bufferAllocator(),
                "GET / HTTP/1.1\r\n" +
                        "Expect: chocolate=yummy\r\n" +
                        "Content-Length: 100\r\n\r\n", CharsetUtil.US_ASCII)));
        assertNull(embedder.readInbound());

        final FullHttpResponse response = embedder.readOutbound();
        assertEquals(HttpResponseStatus.EXPECTATION_FAILED, response.status());
        assertEquals("0", response.headers().get(HttpHeaderNames.CONTENT_LENGTH));
        response.close();

        if (close) {
            assertFalse(embedder.isOpen());
        } else {
            // keep-alive is on by default in HTTP/1.1, so the connection should be still alive
            assertTrue(embedder.isOpen());

            // the decoder should be reset by the aggregator at this point and be able to decode the next request
            assertTrue(embedder.writeInbound(copiedBuffer(embedder.bufferAllocator(),
                    "GET / HTTP/1.1\r\n\r\n", CharsetUtil.US_ASCII)));

            final FullHttpRequest request = embedder.readInbound();
            assertThat(request.method(), is(HttpMethod.GET));
            assertThat(request.uri(), is("/"));
            assertThat(request.payload().readableBytes(), is(0));
            request.close();
        }

        assertFalse(embedder.finish());
    }

    @Test
    public void testValidRequestWith100ContinueAndDecoder() {
        EmbeddedChannel embedder = new EmbeddedChannel(new HttpRequestDecoder(),
                new HttpObjectAggregator<DefaultHttpContent>(100));
        embedder.writeInbound(copiedBuffer(embedder.bufferAllocator(),
            "GET /upload HTTP/1.1\r\n" +
                "Expect: 100-continue\r\n" +
                "Content-Length: 0\r\n\r\n", CharsetUtil.US_ASCII));

        FullHttpResponse response = embedder.readOutbound();
        assertEquals(HttpResponseStatus.CONTINUE, response.status());
        FullHttpRequest request = embedder.readInbound();
        assertFalse(request.headers().contains(HttpHeaderNames.EXPECT));
        request.close();
        response.close();
        assertFalse(embedder.finish());
    }

    @Test
    public void testOversizedRequestWith100ContinueAndDecoder() {
        EmbeddedChannel embedder = new EmbeddedChannel(new HttpRequestDecoder(),
                new HttpObjectAggregator<DefaultHttpContent>(4));
        embedder.writeInbound(copiedBuffer(embedder.bufferAllocator(),
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
        embedder.writeInbound(copiedBuffer(embedder.bufferAllocator(),
                "GET /max-upload-size HTTP/1.1\r\n\r\n", CharsetUtil.US_ASCII));

        FullHttpRequest request = embedder.readInbound();
        assertThat(request.method(), is(HttpMethod.GET));
        assertThat(request.uri(), is("/max-upload-size"));
        assertThat(request.payload().readableBytes(), is(0));
        request.close();

        assertFalse(embedder.finish());
    }

    @Test
    public void testOversizedRequestWith100ContinueAndDecoderCloseConnection() {
        EmbeddedChannel embedder = new EmbeddedChannel(new HttpRequestDecoder(),
                new HttpObjectAggregator<DefaultHttpContent>(4, true));
        embedder.writeInbound(copiedBuffer(embedder.bufferAllocator(),
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
        EmbeddedChannel embedder = new EmbeddedChannel(new HttpRequestDecoder(),
                new HttpObjectAggregator<DefaultHttpContent>(15));

        // Write first request with Expect: 100-continue.
        HttpRequest message = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.PUT, "http://localhost");
        HttpUtil.set100ContinueExpected(message, true);
        HttpUtil.setContentLength(message, 16);

        final byte[] some = "some".getBytes(StandardCharsets.US_ASCII);
        HttpContent<?> chunk1 = new DefaultHttpContent(preferredAllocator().copyOf(some));
        final byte[] test2 = "test".getBytes(StandardCharsets.US_ASCII);
        HttpContent<?> chunk2 = new DefaultHttpContent(preferredAllocator().copyOf(test2));
        HttpContent<?> chunk3 = new EmptyLastHttpContent(preferredAllocator());

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
        int expectedContentLength = chunk2.payload().readableBytes() + chunk3.payload().readableBytes();
        assertFalse(embedder.writeInbound(message2));
        assertFalse(embedder.writeInbound(chunk2));
        assertTrue(embedder.writeInbound(chunk3));

        FullHttpRequest fullMsg = embedder.readInbound();
        assertNotNull(fullMsg);

        assertEquals(expectedContentLength, HttpUtil.getContentLength(fullMsg));

        assertEquals(HttpUtil.getContentLength(fullMsg), fullMsg.payload().readableBytes());

        fullMsg.close();
        assertFalse(embedder.finish());
    }

    @Test
    public void testSelectiveRequestAggregation() {
        HttpObjectAggregator<?> myPostAggregator =
                new HttpObjectAggregator<DefaultHttpContent>(1024 * 1024) {
                    @Override
                    protected HttpMessage tryStartMessage(Object msg) {
                        if (msg instanceof HttpRequest) {
                            HttpRequest request = (HttpRequest) msg;
                            HttpMethod method = request.method();

                            if (method.equals(HttpMethod.POST)) {
                                return (HttpMessage) msg;
                            }
                        }
                        return null;
                    }
                };

        EmbeddedChannel channel = new EmbeddedChannel(myPostAggregator);

        try {
            // Aggregate: POST
            HttpRequest request1 = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/");
            final byte[] data = "Hello, World!".getBytes(StandardCharsets.UTF_8);
            HttpContent<?> content1 = new DefaultHttpContent(preferredAllocator().copyOf(data));
            request1.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN);

            assertTrue(channel.writeInbound(request1, content1,
                    new EmptyLastHttpContent(preferredAllocator())));

            // Getting an aggregated response out
            Object msg1 = channel.readInbound();
            try {
                assertTrue(msg1 instanceof FullHttpRequest);
            } finally {
                ReferenceCountUtil.release(msg1);
            }

            // Don't aggregate: non-POST
            HttpRequest request2 = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.PUT, "/");
            HttpContent<?> content2 = new DefaultHttpContent(preferredAllocator().copyOf(data));
            request2.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN);

            try {
                assertTrue(channel.writeInbound(request2, content2,
                        new EmptyLastHttpContent(preferredAllocator())));

                // Getting the same response objects out
                assertSame(request2, channel.readInbound());
                assertSame(content2, channel.readInbound());
                assertEquals(new EmptyLastHttpContent(preferredAllocator()), channel.readInbound());
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
        HttpObjectAggregator<?> myTextAggregator =
                new HttpObjectAggregator<DefaultHttpContent>(1024 * 1024) {
                    @Override
                    protected HttpMessage tryStartMessage(Object msg) {
                        if (msg instanceof HttpResponse) {
                            HttpResponse response = (HttpResponse) msg;
                            HttpHeaders headers = response.headers();

                            String contentType = headers.get(HttpHeaderNames.CONTENT_TYPE);
                            if (AsciiString.contentEqualsIgnoreCase(contentType, HttpHeaderValues.TEXT_PLAIN)) {
                                return (HttpMessage) msg;
                            }
                        }
                        return null;
                    }
                };

        EmbeddedChannel channel = new EmbeddedChannel(myTextAggregator);

        try {
            // Aggregate: text/plain
            HttpResponse response1 = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            final byte[] data = "Hello, World!".getBytes(StandardCharsets.UTF_8);
            HttpContent<?> content1 = new DefaultHttpContent(preferredAllocator().copyOf(data));
            response1.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN);

            assertTrue(channel.writeInbound(response1, content1,
                    new EmptyLastHttpContent(preferredAllocator())));

            // Getting an aggregated response out
            Object msg1 = channel.readInbound();
            try {
                assertTrue(msg1 instanceof FullHttpResponse);
            } finally {
                ReferenceCountUtil.release(msg1);
            }

            // Don't aggregate: application/json
            HttpResponse response2 = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            final byte[] data2 = "{key: 'value'}".getBytes(StandardCharsets.UTF_8);
            HttpContent<?> content2 = new DefaultHttpContent(preferredAllocator().copyOf(data2));
            response2.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);

            try {
                assertTrue(channel.writeInbound(response2, content2,
                        new EmptyLastHttpContent(preferredAllocator())));

                // Getting the same response objects out
                assertSame(response2, channel.readInbound());
                assertSame(content2, channel.readInbound());
                assertEquals(new EmptyLastHttpContent(preferredAllocator()), channel.readInbound());
            } finally {
                ReferenceCountUtil.release(response2);
                ReferenceCountUtil.release(content2);
            }

            assertFalse(channel.finish());
        } finally {
          channel.close();
        }
    }

    private static Buffer copiedBuffer(BufferAllocator allocator, String data, Charset charset) {
        return copiedBuffer(allocator, data.getBytes(charset));
    }

    private static Buffer copiedBuffer(BufferAllocator allocator, byte[] bytes) {
        return allocator.copyOf(bytes);
    }
}
