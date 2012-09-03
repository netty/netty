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

import static org.junit.Assert.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedMessageChannel;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.util.CharsetUtil;

import java.util.List;

import org.easymock.EasyMock;
import org.junit.Test;

public class HttpChunkAggregatorTest {

    @Test
    public void testAggregate() {
        HttpChunkAggregator aggr = new HttpChunkAggregator(1024 * 1024);
        EmbeddedMessageChannel embedder = new EmbeddedMessageChannel(aggr);

        HttpMessage message = new DefaultHttpMessage(HttpVersion.HTTP_1_1);
        HttpHeaders.setHeader(message, "X-Test", true);
        message.setTransferEncoding(HttpTransferEncoding.STREAMED);
        HttpChunk chunk1 = new DefaultHttpChunk(Unpooled.copiedBuffer("test", CharsetUtil.US_ASCII));
        HttpChunk chunk2 = new DefaultHttpChunk(Unpooled.copiedBuffer("test2", CharsetUtil.US_ASCII));
        HttpChunk chunk3 = new DefaultHttpChunk(Unpooled.EMPTY_BUFFER);
        assertFalse(embedder.writeInbound(message));
        assertFalse(embedder.writeInbound(chunk1));
        assertFalse(embedder.writeInbound(chunk2));

        // this should trigger a messageReceived event so return true
        assertTrue(embedder.writeInbound(chunk3));
        assertTrue(embedder.finish());
        HttpMessage aggratedMessage = (HttpMessage) embedder.readInbound();
        assertNotNull(aggratedMessage);

        assertEquals(chunk1.getContent().readableBytes() + chunk2.getContent().readableBytes(), HttpHeaders.getContentLength(aggratedMessage));
        assertEquals(aggratedMessage.getHeader("X-Test"), Boolean.TRUE.toString());
        checkContentBuffer(aggratedMessage);
        assertNull(embedder.readInbound());

    }

    private static void checkContentBuffer(HttpMessage aggregatedMessage) {
        CompositeByteBuf buffer = (CompositeByteBuf) aggregatedMessage.getContent();
        assertEquals(2, buffer.numComponents());
        List<ByteBuf> buffers = buffer.decompose(0, buffer.capacity());
        assertEquals(2, buffers.size());
        for (ByteBuf buf: buffers) {
            // This should be false as we decompose the buffer before to not have deep hierarchy
            assertFalse(buf instanceof CompositeByteBuf);
        }
    }

    @Test
    public void testAggregateWithTrailer() {
        HttpChunkAggregator aggr = new HttpChunkAggregator(1024 * 1024);
        EmbeddedMessageChannel embedder = new EmbeddedMessageChannel(aggr);
        HttpMessage message = new DefaultHttpMessage(HttpVersion.HTTP_1_1);
        HttpHeaders.setHeader(message, "X-Test", true);
        message.setTransferEncoding(HttpTransferEncoding.CHUNKED);
        HttpChunk chunk1 = new DefaultHttpChunk(Unpooled.copiedBuffer("test", CharsetUtil.US_ASCII));
        HttpChunk chunk2 = new DefaultHttpChunk(Unpooled.copiedBuffer("test2", CharsetUtil.US_ASCII));
        HttpChunkTrailer trailer = new DefaultHttpChunkTrailer();
        trailer.setHeader("X-Trailer", true);

        assertFalse(embedder.writeInbound(message));
        assertFalse(embedder.writeInbound(chunk1));
        assertFalse(embedder.writeInbound(chunk2));

        // this should trigger a messageReceived event so return true
        assertTrue(embedder.writeInbound(trailer));
        assertTrue(embedder.finish());
        HttpMessage aggratedMessage = (HttpMessage) embedder.readInbound();
        assertNotNull(aggratedMessage);

        assertEquals(chunk1.getContent().readableBytes() + chunk2.getContent().readableBytes(), HttpHeaders.getContentLength(aggratedMessage));
        assertEquals(aggratedMessage.getHeader("X-Test"), Boolean.TRUE.toString());
        assertEquals(aggratedMessage.getHeader("X-Trailer"), Boolean.TRUE.toString());
        checkContentBuffer(aggratedMessage);

        assertNull(embedder.readInbound());

    }


    @Test(expected = TooLongFrameException.class)
    public void testTooLongFrameException() {
        HttpChunkAggregator aggr = new HttpChunkAggregator(4);
        EmbeddedMessageChannel embedder = new EmbeddedMessageChannel(aggr);
        HttpMessage message = new DefaultHttpMessage(HttpVersion.HTTP_1_1);
        message.setTransferEncoding(HttpTransferEncoding.STREAMED);
        HttpChunk chunk1 = new DefaultHttpChunk(Unpooled.copiedBuffer("test", CharsetUtil.US_ASCII));
        HttpChunk chunk2 = new DefaultHttpChunk(Unpooled.copiedBuffer("test2", CharsetUtil.US_ASCII));
        assertFalse(embedder.writeInbound(message));
        assertFalse(embedder.writeInbound(chunk1));
        embedder.writeInbound(chunk2);
        fail();

    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidConstructorUsage() {
        new HttpChunkAggregator(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidMaxCumulationBufferComponents() {
        HttpChunkAggregator aggr= new HttpChunkAggregator(Integer.MAX_VALUE);
        aggr.setMaxCumulationBufferComponents(1);
    }

    @Test(expected = IllegalStateException.class)
    public void testSetMaxCumulationBufferComponentsAfterInit() throws Exception {
        HttpChunkAggregator aggr = new HttpChunkAggregator(Integer.MAX_VALUE);
        ChannelHandlerContext ctx = EasyMock.createMock(ChannelHandlerContext.class);
        EasyMock.replay(ctx);
        aggr.beforeAdd(ctx);
        aggr.setMaxCumulationBufferComponents(10);
    }
}
