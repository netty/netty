/*
 * Copyright 2014 The Netty Project
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

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.embedder.EncoderEmbedder;
import org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import org.jboss.netty.handler.codec.http.HttpHeaders.Values;
import org.jboss.netty.handler.codec.oneone.OneToOneEncoder;
import org.jboss.netty.util.CharsetUtil;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

public class HttpContentEncoderTest {

    private static final class TestEncoder extends HttpContentEncoder {
        @Override
        protected EncoderEmbedder<ChannelBuffer> newContentEncoder(HttpMessage msg, String acceptEncoding)
                throws Exception {
            return new EncoderEmbedder<ChannelBuffer>(new OneToOneEncoder() {
                @Override
                protected Object encode(ChannelHandlerContext ctx, Channel channel, Object msg) throws Exception {
                    ChannelBuffer in = (ChannelBuffer) msg;
                    return ChannelBuffers.wrappedBuffer(String.valueOf(in.readableBytes()).getBytes(CharsetUtil.US_ASCII));
                }
            });
        }

        @Override
        protected String getTargetContentEncoding(String acceptEncoding) throws Exception {
            return "test";
        }
    }

    static final class HttpContentEncoderEmbedder extends EncoderEmbedder<Object> {
        HttpContentEncoderEmbedder() {
            super(new TestEncoder());
        }

        public void fireMessageReceived(Object msg) {
            Channels.fireMessageReceived(getChannel(), msg, null);
        }
    }

    @Test
    public void testSplitContent() throws Exception {
        HttpContentEncoderEmbedder e = new HttpContentEncoderEmbedder();
        e.fireMessageReceived(new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"));

        HttpResponse res = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        res.setChunked(true);
        e.offer(res);
        e.offer(new DefaultHttpChunk(ChannelBuffers.wrappedBuffer(new byte[3])));
        e.offer(new DefaultHttpChunk(ChannelBuffers.wrappedBuffer(new byte[2])));
        e.offer(new DefaultHttpChunk(ChannelBuffers.wrappedBuffer(new byte[1])));
        e.offer(HttpChunk.LAST_CHUNK);

        Object o = e.poll();
        assertThat(o, is(instanceOf(HttpRequest.class)));

        o = e.poll();
        assertThat(o, is(instanceOf(HttpResponse.class)));

        res = (HttpResponse) o;
        assertThat(res.isChunked(), is(true));
        assertThat(res.getContent().readableBytes(), is(0));
        assertThat(res.headers().get(Names.TRANSFER_ENCODING), is(nullValue()));
        assertThat(res.headers().get(Names.CONTENT_LENGTH), is(nullValue()));
        assertThat(res.headers().get(Names.CONTENT_ENCODING), is("test"));

        HttpChunk chunk;
        chunk = (HttpChunk) e.poll();
        assertThat(chunk.getContent().toString(CharsetUtil.US_ASCII), is("3"));

        chunk = (HttpChunk) e.poll();
        assertThat(chunk.getContent().toString(CharsetUtil.US_ASCII), is("2"));

        chunk = (HttpChunk) e.poll();
        assertThat(chunk.getContent().toString(CharsetUtil.US_ASCII), is("1"));

        chunk = (HttpChunk) e.poll();
        assertThat(chunk, is(instanceOf(HttpChunkTrailer.class)));

        assertThat(e.poll(), is(nullValue()));
    }

    @Test
    public void testChunkedContent() throws Exception {
        HttpContentEncoderEmbedder e = new HttpContentEncoderEmbedder();
        e.fireMessageReceived(new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"));

        HttpResponse res = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        res.headers().set(Names.TRANSFER_ENCODING, Values.CHUNKED);
        e.offer(res);

        assertEncodedResponse(e);

        e.offer(new DefaultHttpChunk(ChannelBuffers.wrappedBuffer(new byte[3])));
        e.offer(new DefaultHttpChunk(ChannelBuffers.wrappedBuffer(new byte[2])));
        e.offer(new DefaultHttpChunk(ChannelBuffers.wrappedBuffer(new byte[1])));
        e.offer(HttpChunk.LAST_CHUNK);

        HttpChunk chunk;
        chunk = (HttpChunk) e.poll();
        assertThat(chunk.getContent().toString(CharsetUtil.US_ASCII), is("3"));

        chunk = (HttpChunk) e.poll();
        assertThat(chunk.getContent().toString(CharsetUtil.US_ASCII), is("2"));

        chunk = (HttpChunk) e.poll();
        assertThat(chunk.getContent().toString(CharsetUtil.US_ASCII), is("1"));

        chunk = (HttpChunk) e.poll();
        assertThat(chunk.getContent().readable(), is(false));
        assertThat(chunk, is(instanceOf(HttpChunkTrailer.class)));

        assertThat(e.poll(), is(nullValue()));
    }

    @Test
    public void testChunkedContentWithTrailingHeader() throws Exception {
        HttpContentEncoderEmbedder e = new HttpContentEncoderEmbedder();
        e.fireMessageReceived(new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"));

        HttpResponse res = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        res.headers().set(Names.TRANSFER_ENCODING, Values.CHUNKED);
        e.offer(res);

        assertEncodedResponse(e);

        e.offer(new DefaultHttpChunk(ChannelBuffers.wrappedBuffer(new byte[3])));
        e.offer(new DefaultHttpChunk(ChannelBuffers.wrappedBuffer(new byte[2])));
        e.offer(new DefaultHttpChunk(ChannelBuffers.wrappedBuffer(new byte[1])));
        HttpChunkTrailer trailer = new DefaultHttpChunkTrailer();
        trailer.trailingHeaders().set("X-Test", "Netty");
        e.offer(trailer);

        HttpChunk chunk;
        chunk = (HttpChunk) e.poll();
        assertThat(chunk.getContent().toString(CharsetUtil.US_ASCII), is("3"));

        chunk = (HttpChunk) e.poll();
        assertThat(chunk.getContent().toString(CharsetUtil.US_ASCII), is("2"));

        chunk = (HttpChunk) e.poll();
        assertThat(chunk.getContent().toString(CharsetUtil.US_ASCII), is("1"));
        assertThat(chunk, is(instanceOf(HttpChunk.class)));

        chunk = (HttpChunk) e.poll();
        assertThat(chunk.getContent().readable(), is(false));
        assertThat(chunk, is(instanceOf(HttpChunkTrailer.class)));
        assertEquals("Netty", ((HttpChunkTrailer) chunk).trailingHeaders().get("X-Test"));

        assertThat(e.poll(), is(nullValue()));
    }

    @Test
    public void testFullContent() throws Exception {
        HttpContentEncoderEmbedder e = new HttpContentEncoderEmbedder();
        e.fireMessageReceived(new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"));

        HttpResponse res = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        res.setContent(ChannelBuffers.wrappedBuffer(new byte[42]));
        res.headers().set(Names.CONTENT_LENGTH, 42);
        e.offer(res);

        Object o = e.poll();
        assertThat(o, is(instanceOf(HttpRequest.class)));

        o = e.poll();
        assertThat(o, is(instanceOf(HttpResponse.class)));

        res = (HttpResponse) o;
        assertThat(res.isChunked(), is(false));
        assertThat(res.getContent().readableBytes(), is(2));
        assertThat(res.getContent().toString(CharsetUtil.US_ASCII), is("42"));
        assertThat(res.headers().get(Names.TRANSFER_ENCODING), is(nullValue()));
        assertThat(res.headers().get(Names.CONTENT_LENGTH), is("2"));
        assertThat(res.headers().get(Names.CONTENT_ENCODING), is("test"));

        assertThat(e.poll(), is(nullValue()));
    }

    /**
     * If the length of the content is unknown, {@link HttpContentEncoder} should not skip encoding the content
     * even if the actual length is turned out to be 0.
     */
    @Test
    public void testEmptySplitContent() throws Exception {
        HttpContentEncoderEmbedder e = new HttpContentEncoderEmbedder();
        e.fireMessageReceived(new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"));

        HttpResponse res = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        res.setChunked(true);
        e.offer(res);

        Object o = e.poll();
        assertThat(o, is(instanceOf(HttpRequest.class)));

        o = e.poll();
        assertThat(o, is(instanceOf(HttpResponse.class)));

        res = (HttpResponse) o;
        assertThat(res.getContent().readableBytes(), is(0));
        assertThat(res.isChunked(), is(true));
        assertThat(res.headers().get(Names.TRANSFER_ENCODING), is(nullValue()));
        assertThat(res.headers().get(Names.CONTENT_LENGTH), is(nullValue()));
        assertThat(res.headers().get(Names.CONTENT_ENCODING), is("test"));

        e.offer(HttpChunk.LAST_CHUNK);

        HttpChunk chunk = (HttpChunk) e.poll();
        assertThat(chunk.getContent().toString(CharsetUtil.US_ASCII), is("0"));
        assertThat(chunk, is(instanceOf(HttpChunk.class)));

        chunk = (HttpChunk) e.poll();
        assertThat(chunk.getContent().readable(), is(false));
        assertThat(chunk, is(instanceOf(HttpChunkTrailer.class)));

        assertThat(e.poll(), is(nullValue()));
    }

    /**
     * If the length of the content is 0 for sure, {@link HttpContentEncoder} should skip encoding.
     */
    @Test
    public void testEmptyFullContent() throws Exception {
        HttpContentEncoderEmbedder ch = new HttpContentEncoderEmbedder();
        ch.fireMessageReceived(new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"));

        HttpResponse res = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        res.setContent(ChannelBuffers.EMPTY_BUFFER);
        ch.offer(res);

        Object o = ch.poll();
        assertThat(o, is(instanceOf(HttpRequest.class)));

        o = ch.poll();
        assertThat(o, is(instanceOf(HttpResponse.class)));

        res = (HttpResponse) o;
        assertThat(res.headers().get(Names.TRANSFER_ENCODING), is(nullValue()));

        // Content encoding shouldn't be modified.
        assertThat(res.headers().get(Names.CONTENT_ENCODING), is(nullValue()));
        assertThat(res.getContent().readableBytes(), is(0));
        assertThat(res.getContent().toString(CharsetUtil.US_ASCII), is(""));

        assertThat(ch.poll(), is(nullValue()));
    }

    private static void assertEncodedResponse(HttpContentEncoderEmbedder e) {
        Object o = e.poll();
        assertThat(o, is(instanceOf(HttpRequest.class)));

        o = e.poll();
        assertThat(o, is(instanceOf(HttpResponse.class)));

        HttpResponse res = (HttpResponse) o;
        assertThat(res.getContent().readableBytes(), is(0));
        assertThat(res.headers().get(Names.TRANSFER_ENCODING), is("chunked"));
        assertThat(res.headers().get(Names.CONTENT_LENGTH), is(nullValue()));
        assertThat(res.headers().get(Names.CONTENT_ENCODING), is("test"));
    }
}
