/*
 * Copyright 2013 The Netty Project
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
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.CodecException;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.concurrent.atomic.AtomicBoolean;

import static io.netty.handler.codec.http.HttpHeadersTestUtils.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HttpContentEncoderTest {

    private static final class TestEncoder extends HttpContentEncoder {
        @Override
        protected Result beginEncode(HttpResponse httpResponse, String acceptEncoding) {
            return new Result("test", new EmbeddedChannel(new MessageToByteEncoder<ByteBuf>() {
                @Override
                protected void encode(ChannelHandlerContext ctx, ByteBuf in, ByteBuf out) throws Exception {
                    out.writeBytes(String.valueOf(in.readableBytes()).getBytes(CharsetUtil.US_ASCII));
                    in.skipBytes(in.readableBytes());
                }
            }));
        }
    }

    @Test
    public void testSplitContent() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new TestEncoder());
        ch.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"));

        ch.writeOutbound(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK));
        ch.writeOutbound(new DefaultHttpContent(Unpooled.wrappedBuffer(new byte[3])));
        ch.writeOutbound(new DefaultHttpContent(Unpooled.wrappedBuffer(new byte[2])));
        ch.writeOutbound(new DefaultLastHttpContent(Unpooled.wrappedBuffer(new byte[1])));

        assertEncodedResponse(ch);

        HttpContent chunk;
        chunk = ch.readOutbound();
        assertEquals("3", chunk.content().toString(CharsetUtil.US_ASCII));
        chunk.release();

        chunk = ch.readOutbound();
        assertEquals("2", chunk.content().toString(CharsetUtil.US_ASCII));
        chunk.release();

        chunk = ch.readOutbound();
        assertEquals("1", chunk.content().toString(CharsetUtil.US_ASCII));
        chunk.release();

        chunk = ch.readOutbound();
        assertFalse(chunk.content().isReadable());
        assertInstanceOf(LastHttpContent.class, chunk);
        chunk.release();

        assertNull(ch.readOutbound());
    }

    @Test
    public void testChunkedContent() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new TestEncoder());
        ch.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"));

        HttpResponse res = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        res.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        ch.writeOutbound(res);

        assertEncodedResponse(ch);

        ch.writeOutbound(new DefaultHttpContent(Unpooled.wrappedBuffer(new byte[3])));
        ch.writeOutbound(new DefaultHttpContent(Unpooled.wrappedBuffer(new byte[2])));
        ch.writeOutbound(new DefaultLastHttpContent(Unpooled.wrappedBuffer(new byte[1])));

        HttpContent chunk;
        chunk = ch.readOutbound();
        assertEquals("3", chunk.content().toString(CharsetUtil.US_ASCII));
        chunk.release();

        chunk = ch.readOutbound();
        assertEquals("2", chunk.content().toString(CharsetUtil.US_ASCII));
        chunk.release();

        chunk = ch.readOutbound();
        assertEquals("1", chunk.content().toString(CharsetUtil.US_ASCII));
        assertInstanceOf(HttpContent.class, chunk);
        chunk.release();

        chunk = ch.readOutbound();
        assertFalse(chunk.content().isReadable());
        assertInstanceOf(LastHttpContent.class, chunk);
        chunk.release();

        assertNull(ch.readOutbound());
    }

    @Test
    public void testChunkedContentWithTrailingHeader() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new TestEncoder());
        ch.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"));

        HttpResponse res = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        res.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        ch.writeOutbound(res);

        assertEncodedResponse(ch);

        ch.writeOutbound(new DefaultHttpContent(Unpooled.wrappedBuffer(new byte[3])));
        ch.writeOutbound(new DefaultHttpContent(Unpooled.wrappedBuffer(new byte[2])));
        LastHttpContent content = new DefaultLastHttpContent(Unpooled.wrappedBuffer(new byte[1]));
        content.trailingHeaders().set(of("X-Test"), of("Netty"));
        ch.writeOutbound(content);

        HttpContent chunk;
        chunk = ch.readOutbound();
        assertEquals("3", chunk.content().toString(CharsetUtil.US_ASCII));
        chunk.release();

        chunk = ch.readOutbound();
        assertEquals("2", chunk.content().toString(CharsetUtil.US_ASCII));
        chunk.release();

        chunk = ch.readOutbound();
        assertEquals("1", chunk.content().toString(CharsetUtil.US_ASCII));
        assertInstanceOf(HttpContent.class, chunk);
        chunk.release();

        chunk = ch.readOutbound();
        assertFalse(chunk.content().isReadable());
        assertInstanceOf(LastHttpContent.class, chunk);
        assertEquals("Netty", ((LastHttpContent) chunk).trailingHeaders().get(of("X-Test")));
        assertEquals(DecoderResult.SUCCESS, res.decoderResult());
        chunk.release();

        assertNull(ch.readOutbound());
    }

    @Test
    public void testFullContentWithContentLength() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new TestEncoder());
        ch.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"));

        FullHttpResponse fullRes = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer(new byte[42]));
        fullRes.headers().set(HttpHeaderNames.CONTENT_LENGTH, 42);
        ch.writeOutbound(fullRes);

        HttpResponse res = ch.readOutbound();
        assertThat(res).isNotInstanceOf(HttpContent.class);
        assertNull(res.headers().get(HttpHeaderNames.TRANSFER_ENCODING));
        assertEquals("2", res.headers().get(HttpHeaderNames.CONTENT_LENGTH));
        assertEquals("test", res.headers().get(HttpHeaderNames.CONTENT_ENCODING));

        HttpContent c = ch.readOutbound();
        assertEquals(2, c.content().readableBytes());
        assertEquals("42", c.content().toString(CharsetUtil.US_ASCII));
        c.release();

        LastHttpContent last = ch.readOutbound();
        assertEquals(0, last.content().readableBytes());
        last.release();

        assertNull(ch.readOutbound());
    }

    @Test
    public void testFullContent() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new TestEncoder());
        ch.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"));

        FullHttpResponse res = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer(new byte[42]));
        ch.writeOutbound(res);

        assertEncodedResponse(ch);
        HttpContent c = ch.readOutbound();
        assertEquals(2, c.content().readableBytes());
        assertEquals("42", c.content().toString(CharsetUtil.US_ASCII));
        c.release();

        LastHttpContent last = ch.readOutbound();
        assertEquals(0, last.content().readableBytes());
        last.release();

        assertNull(ch.readOutbound());
    }

    /**
     * If the length of the content is unknown, {@link HttpContentEncoder} should not skip encoding the content
     * even if the actual length is turned out to be 0.
     */
    @Test
    public void testEmptySplitContent() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new TestEncoder());
        ch.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"));

        ch.writeOutbound(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK));
        assertEncodedResponse(ch);

        ch.writeOutbound(LastHttpContent.EMPTY_LAST_CONTENT);
        HttpContent chunk = ch.readOutbound();
        assertEquals("0", chunk.content().toString(CharsetUtil.US_ASCII));
        assertInstanceOf(HttpContent.class, chunk);
        chunk.release();

        chunk = ch.readOutbound();
        assertFalse(chunk.content().isReadable());
        assertInstanceOf(LastHttpContent.class, chunk);
        chunk.release();

        assertNull(ch.readOutbound());
    }

    /**
     * If the length of the content is 0 for sure, {@link HttpContentEncoder} should skip encoding.
     */
    @Test
    public void testEmptyFullContent() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new TestEncoder());
        ch.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"));

        FullHttpResponse res = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.EMPTY_BUFFER);
        ch.writeOutbound(res);

        Object o = ch.readOutbound();
        assertInstanceOf(FullHttpResponse.class, o);

        res = (FullHttpResponse) o;
        assertNull(res.headers().get(HttpHeaderNames.TRANSFER_ENCODING));

        // Content encoding shouldn't be modified.
        assertNull(res.headers().get(HttpHeaderNames.CONTENT_ENCODING));
        assertEquals(0, res.content().readableBytes());
        assertEquals("", res.content().toString(CharsetUtil.US_ASCII));
        res.release();

        assertNull(ch.readOutbound());
    }

    @Test
    public void testEmptyFullContentWithTrailer() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new TestEncoder());
        ch.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"));

        FullHttpResponse res = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.EMPTY_BUFFER);
        res.trailingHeaders().set(of("X-Test"), of("Netty"));
        ch.writeOutbound(res);

        Object o = ch.readOutbound();
        assertInstanceOf(FullHttpResponse.class, o);

        res = (FullHttpResponse) o;
        assertNull(res.headers().get(HttpHeaderNames.TRANSFER_ENCODING));

        // Content encoding shouldn't be modified.
        assertNull(res.headers().get(HttpHeaderNames.CONTENT_ENCODING));
        assertEquals(0, res.content().readableBytes());
        assertEquals("", res.content().toString(CharsetUtil.US_ASCII));
        assertEquals("Netty", res.trailingHeaders().get(of("X-Test")));
        assertEquals(DecoderResult.SUCCESS, res.decoderResult());
        assertNull(ch.readOutbound());
    }

    @Test
    public void testEmptyHeadResponse() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new TestEncoder());
        HttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.HEAD, "/");
        ch.writeInbound(req);

        HttpResponse res = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        res.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        ch.writeOutbound(res);
        ch.writeOutbound(LastHttpContent.EMPTY_LAST_CONTENT);

        assertEmptyResponse(ch);
    }

    @Test
    public void testHttp304Response() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new TestEncoder());
        HttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
        req.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP);
        ch.writeInbound(req);

        HttpResponse res = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_MODIFIED);
        res.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        ch.writeOutbound(res);
        ch.writeOutbound(LastHttpContent.EMPTY_LAST_CONTENT);

        assertEmptyResponse(ch);
    }

    @Test
    public void testConnect200Response() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new TestEncoder());
        HttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.CONNECT, "google.com:80");
        ch.writeInbound(req);

        HttpResponse res = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        res.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        ch.writeOutbound(res);
        ch.writeOutbound(LastHttpContent.EMPTY_LAST_CONTENT);

        assertEmptyResponse(ch);
    }

    @Test
    public void testConnectFailureResponse() throws Exception {
        String content = "Not allowed by configuration";

        EmbeddedChannel ch = new EmbeddedChannel(new TestEncoder());
        HttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.CONNECT, "google.com:80");
        ch.writeInbound(req);

        HttpResponse res = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.METHOD_NOT_ALLOWED);
        res.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        ch.writeOutbound(res);
        ch.writeOutbound(new DefaultHttpContent(Unpooled.wrappedBuffer(content.getBytes(CharsetUtil.UTF_8))));
        ch.writeOutbound(LastHttpContent.EMPTY_LAST_CONTENT);

        assertEncodedResponse(ch);
        Object o = ch.readOutbound();
        assertInstanceOf(HttpContent.class, o);
        HttpContent chunk = (HttpContent) o;
        assertEquals("28", chunk.content().toString(CharsetUtil.US_ASCII));
        chunk.release();

        chunk = ch.readOutbound();
        assertTrue(chunk.content().isReadable());
        assertEquals("0", chunk.content().toString(CharsetUtil.US_ASCII));
        chunk.release();

        chunk = ch.readOutbound();
        assertInstanceOf(LastHttpContent.class, chunk);
        chunk.release();
        assertNull(ch.readOutbound());
    }

    @Test
    public void testHttp1_0() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new TestEncoder());
        FullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_0, HttpMethod.GET, "/");
        assertTrue(ch.writeInbound(req));

        HttpResponse res = new DefaultHttpResponse(HttpVersion.HTTP_1_0, HttpResponseStatus.OK);
        res.headers().set(HttpHeaderNames.CONTENT_LENGTH, HttpHeaderValues.ZERO);
        assertTrue(ch.writeOutbound(res));
        assertTrue(ch.writeOutbound(LastHttpContent.EMPTY_LAST_CONTENT));
        assertTrue(ch.finish());

        FullHttpRequest request = ch.readInbound();
        assertTrue(request.release());
        assertNull(ch.readInbound());

        HttpResponse response = ch.readOutbound();
        assertSame(res, response);

        LastHttpContent content = ch.readOutbound();
        assertSame(LastHttpContent.EMPTY_LAST_CONTENT, content);
        content.release();
        assertNull(ch.readOutbound());
    }

    @Test
    public void testCleanupThrows() {
        HttpContentEncoder encoder = new HttpContentEncoder() {
            @Override
            protected Result beginEncode(HttpResponse httpResponse, String acceptEncoding) throws Exception {
                return new Result("myencoding", new EmbeddedChannel(
                        new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                        ctx.fireExceptionCaught(new EncoderException());
                        ctx.fireChannelInactive();
                    }
                }));
            }
        };

        final AtomicBoolean channelInactiveCalled = new AtomicBoolean();
        final EmbeddedChannel channel = new EmbeddedChannel(encoder, new ChannelInboundHandlerAdapter() {
            @Override
            public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                assertTrue(channelInactiveCalled.compareAndSet(false, true));
                super.channelInactive(ctx);
            }
        });
        assertTrue(channel.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/")));
        assertTrue(channel.writeOutbound(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)));
        HttpContent content = new DefaultHttpContent(Unpooled.buffer().writeZero(10));
        assertTrue(channel.writeOutbound(content));
        assertEquals(1, content.refCnt());
        assertThrows(CodecException.class, new Executable() {
            @Override
            public void execute() {
                channel.finishAndReleaseAll();
            }
        });

        assertTrue(channelInactiveCalled.get());
        assertEquals(0, content.refCnt());
    }

    private static void assertEmptyResponse(EmbeddedChannel ch) {
        Object o = ch.readOutbound();
        assertInstanceOf(HttpResponse.class, o);

        HttpResponse res = (HttpResponse) o;
        assertThat(res).isNotInstanceOf(HttpContent.class);
        assertEquals("chunked", res.headers().get(HttpHeaderNames.TRANSFER_ENCODING));
        assertNull(res.headers().get(HttpHeaderNames.CONTENT_LENGTH));

        HttpContent chunk = ch.readOutbound();
        assertInstanceOf(LastHttpContent.class, chunk);
        chunk.release();
        assertNull(ch.readOutbound());
    }

    private static void assertEncodedResponse(EmbeddedChannel ch) {
        Object o = ch.readOutbound();
        assertInstanceOf(HttpResponse.class, o);

        HttpResponse res = (HttpResponse) o;
        assertThat(res).isNotInstanceOf(HttpContent.class);
        assertEquals("chunked", res.headers().get(HttpHeaderNames.TRANSFER_ENCODING));
        assertNull(res.headers().get(HttpHeaderNames.CONTENT_LENGTH));
        assertEquals("test", res.headers().get(HttpHeaderNames.CONTENT_ENCODING));
    }
}
