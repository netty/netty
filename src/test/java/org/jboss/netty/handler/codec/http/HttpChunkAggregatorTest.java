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
package org.jboss.netty.handler.codec.http;

import org.easymock.EasyMock;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.buffer.CompositeChannelBuffer;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.embedder.CodecEmbedderException;
import org.jboss.netty.handler.codec.embedder.DecoderEmbedder;
import org.jboss.netty.handler.codec.frame.TooLongFrameException;
import org.jboss.netty.util.CharsetUtil;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class HttpChunkAggregatorTest {

    @Test
    public void testAggregate() {
        HttpChunkAggregator aggr = new HttpChunkAggregator(1024 * 1024);
        DecoderEmbedder<HttpMessage> embedder = new DecoderEmbedder<HttpMessage>(aggr);
        HttpMessage message = new DefaultHttpMessage(HttpVersion.HTTP_1_1);
        message.headers().set("X-Test", true);
        message.setChunked(true);
        HttpChunk chunk1 = new DefaultHttpChunk(ChannelBuffers.copiedBuffer("test", CharsetUtil.US_ASCII));
        HttpChunk chunk2 = new DefaultHttpChunk(ChannelBuffers.copiedBuffer("test2", CharsetUtil.US_ASCII));
        HttpChunk chunk3 = new DefaultHttpChunk(ChannelBuffers.EMPTY_BUFFER);
        assertFalse(embedder.offer(message));
        assertFalse(embedder.offer(chunk1));
        assertFalse(embedder.offer(chunk2));
        
        // this should trigger a messageReceived event so return true
        assertTrue(embedder.offer(chunk3));
        assertTrue(embedder.finish());
        HttpMessage aggratedMessage = embedder.poll();
        assertNotNull(aggratedMessage);
        
        assertEquals(chunk1.getContent().readableBytes() + chunk2.getContent().readableBytes(), HttpHeaders.getContentLength(aggratedMessage));
        assertEquals(aggratedMessage.headers().get("X-Test"), Boolean.TRUE.toString());
        checkContentBuffer(aggratedMessage);
        assertNull(embedder.poll());

    }

    private static void checkContentBuffer(HttpMessage aggregatedMessage) {
        CompositeChannelBuffer buffer = (CompositeChannelBuffer) aggregatedMessage.getContent();
        assertEquals(2, buffer.numComponents());
        List<ChannelBuffer> buffers = buffer.decompose(0, buffer.capacity());
        assertEquals(2, buffers.size());
        for (ChannelBuffer buf: buffers) {
            // This should be false as we decompose the buffer before to not have deep hierarchy
            assertFalse(buf instanceof CompositeChannelBuffer);
        }
    }

    @Test
    public void testAggregateWithTrailer() {
        HttpChunkAggregator aggr = new HttpChunkAggregator(1024 * 1024);
        DecoderEmbedder<HttpMessage> embedder = new DecoderEmbedder<HttpMessage>(aggr);
        HttpMessage message = new DefaultHttpMessage(HttpVersion.HTTP_1_1);
        message.headers().set("X-Test", true);
        message.setChunked(true);
        HttpChunk chunk1 = new DefaultHttpChunk(ChannelBuffers.copiedBuffer("test", CharsetUtil.US_ASCII));
        HttpChunk chunk2 = new DefaultHttpChunk(ChannelBuffers.copiedBuffer("test2", CharsetUtil.US_ASCII));
        HttpChunkTrailer trailer = new DefaultHttpChunkTrailer();
        trailer.trailingHeaders().set("X-Trailer", true);
        
        assertFalse(embedder.offer(message));
        assertFalse(embedder.offer(chunk1));
        assertFalse(embedder.offer(chunk2));
        
        // this should trigger a messageReceived event so return true
        assertTrue(embedder.offer(trailer));
        assertTrue(embedder.finish());
        HttpMessage aggratedMessage = embedder.poll();
        assertNotNull(aggratedMessage);
        
        assertEquals(chunk1.getContent().readableBytes() + chunk2.getContent().readableBytes(), HttpHeaders.getContentLength(aggratedMessage));
        assertEquals(aggratedMessage.headers().get("X-Test"), Boolean.TRUE.toString());
        assertEquals(aggratedMessage.headers().get("X-Trailer"), Boolean.TRUE.toString());
        checkContentBuffer(aggratedMessage);

        assertNull(embedder.poll());

    }


    @Test
    public void testTooLongFrameException() {
        HttpChunkAggregator aggr = new HttpChunkAggregator(4);
        DecoderEmbedder<HttpMessage> embedder = new DecoderEmbedder<HttpMessage>(aggr);
        HttpMessage message = new DefaultHttpMessage(HttpVersion.HTTP_1_1);
        message.setChunked(true);
        HttpMessage message2= new DefaultHttpMessage(HttpVersion.HTTP_1_1);
        message2.setChunked(true);
        HttpChunk chunk1 = new DefaultHttpChunk(ChannelBuffers.copiedBuffer("test", CharsetUtil.US_ASCII));
        HttpChunk chunk2 = new DefaultHttpChunk(ChannelBuffers.copiedBuffer("test2", CharsetUtil.US_ASCII));
        HttpChunk chunk3 = new DefaultHttpChunk(ChannelBuffers.copiedBuffer("test3", CharsetUtil.US_ASCII));
        HttpChunk chunk4 = HttpChunk.LAST_CHUNK;
        HttpChunk chunk5 = new DefaultHttpChunk(ChannelBuffers.copiedBuffer("test", CharsetUtil.US_ASCII));
        HttpChunk chunk6 = new DefaultHttpChunk(ChannelBuffers.copiedBuffer("test2", CharsetUtil.US_ASCII));
        HttpChunk chunk7 = new DefaultHttpChunk(ChannelBuffers.copiedBuffer("test3", CharsetUtil.US_ASCII));
        HttpChunk chunk8 = HttpChunk.LAST_CHUNK;

        assertFalse(embedder.offer(message));
        assertFalse(embedder.offer(chunk1));
        try {
            embedder.offer(chunk2);
            fail();
        } catch (CodecEmbedderException e) {
            assertTrue(e.getCause() instanceof TooLongFrameException);
        }
        assertFalse(embedder.offer(chunk3));
        assertFalse(embedder.offer(chunk4));

        assertFalse(embedder.offer(message2));
        assertFalse(embedder.offer(chunk5));
        try {
            embedder.offer(chunk6);
            fail();
        } catch (CodecEmbedderException e) {
            assertTrue(e.getCause() instanceof TooLongFrameException);
        }
        assertFalse(embedder.offer(chunk7));
        assertFalse(embedder.offer(chunk8));
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

    @Test
    public void testAggregateTransferEncodingChunked() {
        HttpChunkAggregator aggr = new HttpChunkAggregator(1024 * 1024);
        DecoderEmbedder<HttpMessage> embedder = new DecoderEmbedder<HttpMessage>(aggr);
        HttpMessage message = new DefaultHttpMessage(HttpVersion.HTTP_1_1);
        message.headers().set("X-Test", true);
        message.headers().set("Transfer-Encoding", "Chunked");
        message.setChunked(true);
        HttpChunk chunk1 = new DefaultHttpChunk(ChannelBuffers.copiedBuffer("test", CharsetUtil.US_ASCII));
        HttpChunk chunk2 = new DefaultHttpChunk(ChannelBuffers.copiedBuffer("test2", CharsetUtil.US_ASCII));
        HttpChunk chunk3 = new DefaultHttpChunk(ChannelBuffers.EMPTY_BUFFER);
        assertFalse(embedder.offer(message));
        assertFalse(embedder.offer(chunk1));
        assertFalse(embedder.offer(chunk2));

        // this should trigger a messageReceived event so return true
        assertTrue(embedder.offer(chunk3));
        assertTrue(embedder.finish());
        HttpMessage aggratedMessage = embedder.poll();
        assertNotNull(aggratedMessage);

        assertEquals(chunk1.getContent().readableBytes() + chunk2.getContent().readableBytes(), HttpHeaders.getContentLength(aggratedMessage));
        assertEquals(aggratedMessage.headers().get("X-Test"), Boolean.TRUE.toString());
        checkContentBuffer(aggratedMessage);
        assertNull(embedder.poll());
    }
}
