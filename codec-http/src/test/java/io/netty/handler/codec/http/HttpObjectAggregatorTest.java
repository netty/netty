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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.util.CharsetUtil;
import org.easymock.EasyMock;
import org.junit.Test;

import java.util.List;

import static io.netty.util.ReferenceCountUtil.*;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

public class HttpObjectAggregatorTest {

    @Test
    public void testAggregate() {
        HttpObjectAggregator aggr = new HttpObjectAggregator(1024 * 1024);
        EmbeddedChannel embedder = new EmbeddedChannel(aggr);

        HttpRequest message = new DefaultHttpRequest(HttpVersion.HTTP_1_1,
                HttpMethod.GET, "http://localhost");
        HttpHeaders.setHeader(message, "X-Test", true);
        HttpContent chunk1 = new DefaultHttpContent(Unpooled.copiedBuffer("test", CharsetUtil.US_ASCII));
        HttpContent chunk2 = new DefaultHttpContent(Unpooled.copiedBuffer("test2", CharsetUtil.US_ASCII));
        HttpContent chunk3 = new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER);
        assertFalse(embedder.writeInbound(message));
        assertFalse(embedder.writeInbound(chunk1));
        assertFalse(embedder.writeInbound(chunk2));

        // this should trigger a channelRead event so return true
        assertTrue(embedder.writeInbound(chunk3));
        assertTrue(embedder.finish());
        FullHttpRequest aggratedMessage = (FullHttpRequest) embedder.readInbound();
        assertNotNull(aggratedMessage);

        assertEquals(chunk1.content().readableBytes() + chunk2.content().readableBytes(),
                HttpHeaders.getContentLength(aggratedMessage));
        assertEquals(aggratedMessage.headers().get("X-Test"), Boolean.TRUE.toString());
        checkContentBuffer(aggratedMessage);
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
        HttpRequest message = new DefaultHttpRequest(HttpVersion.HTTP_1_1,
                HttpMethod.GET, "http://localhost");
        HttpHeaders.setHeader(message, "X-Test", true);
        HttpHeaders.setTransferEncodingChunked(message);
        HttpContent chunk1 = new DefaultHttpContent(Unpooled.copiedBuffer("test", CharsetUtil.US_ASCII));
        HttpContent chunk2 = new DefaultHttpContent(Unpooled.copiedBuffer("test2", CharsetUtil.US_ASCII));
        LastHttpContent trailer = new DefaultLastHttpContent();
        trailer.trailingHeaders().set("X-Trailer", true);

        assertFalse(embedder.writeInbound(message));
        assertFalse(embedder.writeInbound(chunk1));
        assertFalse(embedder.writeInbound(chunk2));

        // this should trigger a channelRead event so return true
        assertTrue(embedder.writeInbound(trailer));
        assertTrue(embedder.finish());
        FullHttpRequest aggratedMessage = (FullHttpRequest) embedder.readInbound();
        assertNotNull(aggratedMessage);

        assertEquals(chunk1.content().readableBytes() + chunk2.content().readableBytes(),
                HttpHeaders.getContentLength(aggratedMessage));
        assertEquals(aggratedMessage.headers().get("X-Test"), Boolean.TRUE.toString());
        assertEquals(aggratedMessage.trailingHeaders().get("X-Trailer"), Boolean.TRUE.toString());
        checkContentBuffer(aggratedMessage);
        assertNull(embedder.readInbound());
    }

    @Test
    public void testTooLongFrameException() {
        HttpObjectAggregator aggr = new HttpObjectAggregator(4);
        EmbeddedChannel embedder = new EmbeddedChannel(aggr);
        HttpRequest message = new DefaultHttpRequest(HttpVersion.HTTP_1_1,
                HttpMethod.GET, "http://localhost");
        HttpContent chunk1 = releaseLater(new DefaultHttpContent(Unpooled.copiedBuffer("test", CharsetUtil.US_ASCII)));
        HttpContent chunk2 = releaseLater(new DefaultHttpContent(Unpooled.copiedBuffer("test2", CharsetUtil.US_ASCII)));
        HttpContent chunk3 = releaseLater(new DefaultHttpContent(Unpooled.copiedBuffer("test3", CharsetUtil.US_ASCII)));
        HttpContent chunk4 = LastHttpContent.EMPTY_LAST_CONTENT;

        assertFalse(embedder.writeInbound(message));
        assertFalse(embedder.writeInbound(chunk1.copy()));
        try {
            embedder.writeInbound(chunk2.copy());
            fail();
        } catch (TooLongFrameException e) {
            // expected
        }
        assertFalse(embedder.writeInbound(chunk3.copy()));
        assertFalse(embedder.writeInbound(chunk4.copy()));

        assertFalse(embedder.writeInbound(message));
        assertFalse(embedder.writeInbound(chunk1.copy()));
        try {
            embedder.writeInbound(chunk2.copy());
            fail();
        } catch (TooLongFrameException e) {
            // expected
        }
        assertFalse(embedder.writeInbound(chunk3.copy()));
        assertFalse(embedder.writeInbound(chunk4.copy()));
        embedder.finish();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidConstructorUsage() {
        new HttpObjectAggregator(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidMaxCumulationBufferComponents() {
        HttpObjectAggregator aggr = new HttpObjectAggregator(Integer.MAX_VALUE);
        aggr.setMaxCumulationBufferComponents(1);
    }

    @Test(expected = IllegalStateException.class)
    public void testSetMaxCumulationBufferComponentsAfterInit() throws Exception {
        HttpObjectAggregator aggr = new HttpObjectAggregator(Integer.MAX_VALUE);
        ChannelHandlerContext ctx = EasyMock.createMock(ChannelHandlerContext.class);
        EasyMock.replay(ctx);
        aggr.handlerAdded(ctx);
        aggr.setMaxCumulationBufferComponents(10);
    }

    @Test
    public void testAggregateTransferEncodingChunked() {
        HttpObjectAggregator aggr = new HttpObjectAggregator(1024 * 1024);
        EmbeddedChannel embedder = new EmbeddedChannel(aggr);

        HttpRequest message = new DefaultHttpRequest(HttpVersion.HTTP_1_1,
                HttpMethod.GET, "http://localhost");
        HttpHeaders.setHeader(message, "X-Test", true);
        HttpHeaders.setHeader(message, "Transfer-Encoding", "Chunked");
        HttpContent chunk1 = new DefaultHttpContent(Unpooled.copiedBuffer("test", CharsetUtil.US_ASCII));
        HttpContent chunk2 = new DefaultHttpContent(Unpooled.copiedBuffer("test2", CharsetUtil.US_ASCII));
        HttpContent chunk3 = LastHttpContent.EMPTY_LAST_CONTENT;
        assertFalse(embedder.writeInbound(message));
        assertFalse(embedder.writeInbound(chunk1));
        assertFalse(embedder.writeInbound(chunk2));

        // this should trigger a channelRead event so return true
        assertTrue(embedder.writeInbound(chunk3));
        assertTrue(embedder.finish());
        FullHttpRequest aggratedMessage = (FullHttpRequest) embedder.readInbound();
        assertNotNull(aggratedMessage);

        assertEquals(chunk1.content().readableBytes() + chunk2.content().readableBytes(),
                HttpHeaders.getContentLength(aggratedMessage));
        assertEquals(aggratedMessage.headers().get("X-Test"), Boolean.TRUE.toString());
        checkContentBuffer(aggratedMessage);
        assertNull(embedder.readInbound());
    }

    @Test
    public void testBadRequest() {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpRequestDecoder(), new HttpObjectAggregator(1024 * 1024));
        ch.writeInbound(Unpooled.copiedBuffer("GET / HTTP/1.0 with extra\r\n", CharsetUtil.UTF_8));
        Object inbound = ch.readInbound();
        assertThat(inbound, is(instanceOf(FullHttpRequest.class)));
        assertTrue(((HttpObject) inbound).getDecoderResult().isFailure());
        assertNull(ch.readInbound());
        ch.finish();
    }

    @Test
    public void testBadResponse() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpResponseDecoder(), new HttpObjectAggregator(1024 * 1024));
        ch.writeInbound(Unpooled.copiedBuffer("HTTP/1.0 BAD_CODE Bad Server\r\n", CharsetUtil.UTF_8));
        Object inbound = ch.readInbound();
        assertThat(inbound, is(instanceOf(FullHttpResponse.class)));
        assertTrue(((HttpObject) inbound).getDecoderResult().isFailure());
        assertNull(ch.readInbound());
        ch.finish();
    }

    @Test
    public void testOversizedRequestWith100Continue() {
        EmbeddedChannel embedder = new EmbeddedChannel(new HttpObjectAggregator(8));

        // send an oversized request with 100 continue
        HttpRequest message = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.PUT, "http://localhost");
        HttpHeaders.set100ContinueExpected(message, true);
        HttpHeaders.setContentLength(message, 16);

        HttpContent chunk1 = releaseLater(new DefaultHttpContent(Unpooled.copiedBuffer("some", CharsetUtil.US_ASCII)));
        HttpContent chunk2 = releaseLater(new DefaultHttpContent(Unpooled.copiedBuffer("test", CharsetUtil.US_ASCII)));
        HttpContent chunk3 = LastHttpContent.EMPTY_LAST_CONTENT;

        // Send a request with 100-continue + large Content-Length header value.
        assertFalse(embedder.writeInbound(message));

        // The aggregator should respond with '417.'
        FullHttpResponse response = (FullHttpResponse) embedder.readOutbound();
        assertEquals(HttpResponseStatus.EXPECTATION_FAILED, response.getStatus());
        assertEquals("0", response.headers().get(HttpHeaders.Names.CONTENT_LENGTH));

        // An ill-behaving client could continue to send data without a respect, and such data should be discarded.
        assertFalse(embedder.writeInbound(chunk1));

        // The aggregator should not close the connection because keep-alive is on.
        assertTrue(embedder.isOpen());

        // Now send a valid request.
        HttpRequest message2 = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.PUT, "http://localhost");

        assertFalse(embedder.writeInbound(message2));
        assertFalse(embedder.writeInbound(chunk2));
        assertTrue(embedder.writeInbound(chunk3));

        FullHttpRequest fullMsg = (FullHttpRequest) embedder.readInbound();
        assertNotNull(fullMsg);

        assertEquals(
                chunk2.content().readableBytes() + chunk3.content().readableBytes(),
                HttpHeaders.getContentLength(fullMsg));

        assertEquals(HttpHeaders.getContentLength(fullMsg), fullMsg.content().readableBytes());

        fullMsg.release();
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

        FullHttpResponse response = (FullHttpResponse) embedder.readOutbound();
        assertEquals(HttpResponseStatus.EXPECTATION_FAILED, response.getStatus());
        assertEquals("0", response.headers().get(HttpHeaders.Names.CONTENT_LENGTH));

        // Keep-alive is on by default in HTTP/1.1, so the connection should be still alive.
        assertTrue(embedder.isOpen());

        // The decoder should be reset by the aggregator at this point and be able to decode the next request.
        embedder.writeInbound(Unpooled.copiedBuffer("GET /max-upload-size HTTP/1.1\r\n\r\n", CharsetUtil.US_ASCII));

        FullHttpRequest request = (FullHttpRequest) embedder.readInbound();
        assertThat(request.getMethod(), is(HttpMethod.GET));
        assertThat(request.getUri(), is("/max-upload-size"));
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

        FullHttpResponse response = (FullHttpResponse) embedder.readOutbound();
        assertEquals(HttpResponseStatus.EXPECTATION_FAILED, response.getStatus());
        assertEquals("0", response.headers().get(HttpHeaders.Names.CONTENT_LENGTH));

        // We are forcing the connection closed if an expectation is exceeded.
        assertFalse(embedder.isOpen());
        assertFalse(embedder.finish());
    }

    @Test
    public void testRequestAfterOversized100ContinueAndDecoder() {
        EmbeddedChannel embedder = new EmbeddedChannel(new HttpRequestDecoder(), new HttpObjectAggregator(15));

        // Write first request with Expect: 100-continue
        HttpRequest message = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.PUT, "http://localhost");
        HttpHeaders.set100ContinueExpected(message, true);
        HttpHeaders.setContentLength(message, 16);

        HttpContent chunk1 = releaseLater(new DefaultHttpContent(Unpooled.copiedBuffer("some", CharsetUtil.US_ASCII)));
        HttpContent chunk2 = releaseLater(new DefaultHttpContent(Unpooled.copiedBuffer("test", CharsetUtil.US_ASCII)));
        HttpContent chunk3 = LastHttpContent.EMPTY_LAST_CONTENT;

        // Send a request with 100-continue + large Content-Length header value.
        assertFalse(embedder.writeInbound(message));

        // The aggregator should respond with '417'
        FullHttpResponse response = (FullHttpResponse) embedder.readOutbound();
        assertEquals(HttpResponseStatus.EXPECTATION_FAILED, response.getStatus());
        assertEquals("0", response.headers().get(HttpHeaders.Names.CONTENT_LENGTH));

        // An ill-behaving client could continue to send data without a respect, and such data should be discarded.
        assertFalse(embedder.writeInbound(chunk1));

        // The aggregator should not close the connection because keep-alive is on.
        assertTrue(embedder.isOpen());

        // Now send a valid request.
        HttpRequest message2 = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.PUT, "http://localhost");

        assertFalse(embedder.writeInbound(message2));
        assertFalse(embedder.writeInbound(chunk2));
        assertTrue(embedder.writeInbound(chunk3));

        FullHttpRequest fullMsg = (FullHttpRequest) embedder.readInbound();
        assertNotNull(fullMsg);

        assertEquals(
                chunk2.content().readableBytes() + chunk3.content().readableBytes(),
                HttpHeaders.getContentLength(fullMsg));

        assertEquals(HttpHeaders.getContentLength(fullMsg), fullMsg.content().readableBytes());

        fullMsg.release();
        assertFalse(embedder.finish());
    }
}
