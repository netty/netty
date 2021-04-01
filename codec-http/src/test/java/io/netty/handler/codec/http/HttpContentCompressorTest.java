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

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

import static io.netty.handler.codec.http.HttpHeadersTestUtils.of;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class HttpContentCompressorTest {

    @Test
    public void testGetTargetContentEncoding() throws Exception {
        HttpContentCompressor compressor = new HttpContentCompressor();

        String[] tests = {
            // Accept-Encoding -> Content-Encoding
            "", null,
            "*", "gzip",
            "*;q=0.0", null,
            "gzip", "gzip",
            "compress, gzip;q=0.5", "gzip",
            "gzip; q=0.5, identity", "gzip",
            "gzip ; q=0.1", "gzip",
            "gzip; q=0, deflate", "deflate",
            " deflate ; q=0 , *;q=0.5", "gzip",
        };
        for (int i = 0; i < tests.length; i += 2) {
            String acceptEncoding = tests[i];
            String contentEncoding = tests[i + 1];
            ZlibWrapper targetWrapper = compressor.determineWrapper(acceptEncoding);
            String targetEncoding = null;
            if (targetWrapper != null) {
                switch (targetWrapper) {
                case GZIP:
                    targetEncoding = "gzip";
                    break;
                case ZLIB:
                    targetEncoding = "deflate";
                    break;
                default:
                    fail();
                }
            }
            assertEquals(contentEncoding, targetEncoding);
        }
    }

    @Test
    public void testSplitContent() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpContentCompressor());
        ch.writeInbound(newRequest());

        ch.writeOutbound(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK));
        ch.writeOutbound(new DefaultHttpContent(Unpooled.copiedBuffer("Hell", CharsetUtil.US_ASCII)));
        ch.writeOutbound(new DefaultHttpContent(Unpooled.copiedBuffer("o, w", CharsetUtil.US_ASCII)));
        ch.writeOutbound(new DefaultLastHttpContent(Unpooled.copiedBuffer("orld", CharsetUtil.US_ASCII)));

        assertEncodedResponse(ch);

        HttpContent chunk;
        chunk = ch.readOutbound();
        assertThat(ByteBufUtil.hexDump(chunk.content()), is("1f8b0800000000000000f248cdc901000000ffff"));
        chunk.release();

        chunk = ch.readOutbound();
        assertThat(ByteBufUtil.hexDump(chunk.content()), is("cad7512807000000ffff"));
        chunk.release();

        chunk = ch.readOutbound();
        assertThat(ByteBufUtil.hexDump(chunk.content()), is("ca2fca4901000000ffff"));
        chunk.release();

        chunk = ch.readOutbound();
        assertThat(ByteBufUtil.hexDump(chunk.content()), is("0300c2a99ae70c000000"));
        assertThat(chunk, is(instanceOf(HttpContent.class)));
        chunk.release();

        chunk = ch.readOutbound();
        assertThat(chunk.content().isReadable(), is(false));
        assertThat(chunk, is(instanceOf(LastHttpContent.class)));
        chunk.release();

        assertThat(ch.readOutbound(), is(nullValue()));
    }

    @Test
    public void testChunkedContent() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpContentCompressor());
        ch.writeInbound(newRequest());

        HttpResponse res = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        res.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        ch.writeOutbound(res);

        assertEncodedResponse(ch);

        ch.writeOutbound(new DefaultHttpContent(Unpooled.copiedBuffer("Hell", CharsetUtil.US_ASCII)));
        ch.writeOutbound(new DefaultHttpContent(Unpooled.copiedBuffer("o, w", CharsetUtil.US_ASCII)));
        ch.writeOutbound(new DefaultLastHttpContent(Unpooled.copiedBuffer("orld", CharsetUtil.US_ASCII)));

        HttpContent chunk;
        chunk = ch.readOutbound();
        assertThat(ByteBufUtil.hexDump(chunk.content()), is("1f8b0800000000000000f248cdc901000000ffff"));
        chunk.release();

        chunk = ch.readOutbound();
        assertThat(ByteBufUtil.hexDump(chunk.content()), is("cad7512807000000ffff"));
        chunk.release();

        chunk = ch.readOutbound();
        assertThat(ByteBufUtil.hexDump(chunk.content()), is("ca2fca4901000000ffff"));
        chunk.release();

        chunk = ch.readOutbound();
        assertThat(ByteBufUtil.hexDump(chunk.content()), is("0300c2a99ae70c000000"));
        assertThat(chunk, is(instanceOf(HttpContent.class)));
        chunk.release();

        chunk = ch.readOutbound();
        assertThat(chunk.content().isReadable(), is(false));
        assertThat(chunk, is(instanceOf(LastHttpContent.class)));
        chunk.release();

        assertThat(ch.readOutbound(), is(nullValue()));
    }

    @Test
    public void testChunkedContentWithAssembledResponse() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpContentCompressor());
        ch.writeInbound(newRequest());

        HttpResponse res = new AssembledHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                Unpooled.copiedBuffer("Hell", CharsetUtil.US_ASCII));
        res.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        ch.writeOutbound(res);

        assertAssembledEncodedResponse(ch);

        ch.writeOutbound(new DefaultHttpContent(Unpooled.copiedBuffer("o, w", CharsetUtil.US_ASCII)));
        ch.writeOutbound(new DefaultLastHttpContent(Unpooled.copiedBuffer("orld", CharsetUtil.US_ASCII)));

        HttpContent chunk;
        chunk = ch.readOutbound();
        assertThat(ByteBufUtil.hexDump(chunk.content()), is("1f8b0800000000000000f248cdc901000000ffff"));
        chunk.release();

        chunk = ch.readOutbound();
        assertThat(ByteBufUtil.hexDump(chunk.content()), is("cad7512807000000ffff"));
        chunk.release();

        chunk = ch.readOutbound();
        assertThat(ByteBufUtil.hexDump(chunk.content()), is("ca2fca4901000000ffff"));
        chunk.release();

        chunk = ch.readOutbound();
        assertThat(ByteBufUtil.hexDump(chunk.content()), is("0300c2a99ae70c000000"));
        assertThat(chunk, is(instanceOf(HttpContent.class)));
        chunk.release();

        chunk = ch.readOutbound();
        assertThat(chunk.content().isReadable(), is(false));
        assertThat(chunk, is(instanceOf(LastHttpContent.class)));
        chunk.release();

        assertThat(ch.readOutbound(), is(nullValue()));
    }

    @Test
    public void testChunkedContentWithTrailingHeader() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpContentCompressor());
        ch.writeInbound(newRequest());

        HttpResponse res = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        res.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        ch.writeOutbound(res);

        assertEncodedResponse(ch);

        ch.writeOutbound(new DefaultHttpContent(Unpooled.copiedBuffer("Hell", CharsetUtil.US_ASCII)));
        ch.writeOutbound(new DefaultHttpContent(Unpooled.copiedBuffer("o, w", CharsetUtil.US_ASCII)));
        LastHttpContent content = new DefaultLastHttpContent(Unpooled.copiedBuffer("orld", CharsetUtil.US_ASCII));
        content.trailingHeaders().set(of("X-Test"), of("Netty"));
        ch.writeOutbound(content);

        HttpContent chunk;
        chunk = ch.readOutbound();
        assertThat(ByteBufUtil.hexDump(chunk.content()), is("1f8b0800000000000000f248cdc901000000ffff"));
        chunk.release();

        chunk = ch.readOutbound();
        assertThat(ByteBufUtil.hexDump(chunk.content()), is("cad7512807000000ffff"));
        chunk.release();

        chunk = ch.readOutbound();
        assertThat(ByteBufUtil.hexDump(chunk.content()), is("ca2fca4901000000ffff"));
        chunk.release();

        chunk = ch.readOutbound();
        assertThat(ByteBufUtil.hexDump(chunk.content()), is("0300c2a99ae70c000000"));
        assertThat(chunk, is(instanceOf(HttpContent.class)));
        chunk.release();

        chunk = ch.readOutbound();
        assertThat(chunk.content().isReadable(), is(false));
        assertThat(chunk, is(instanceOf(LastHttpContent.class)));
        assertEquals("Netty", ((LastHttpContent) chunk).trailingHeaders().get(of("X-Test")));
        assertEquals(DecoderResult.SUCCESS, chunk.decoderResult());
        chunk.release();

        assertThat(ch.readOutbound(), is(nullValue()));
    }

    @Test
    public void testFullContentWithContentLength() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpContentCompressor());
        ch.writeInbound(newRequest());

        FullHttpResponse fullRes = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                Unpooled.copiedBuffer("Hello, World", CharsetUtil.US_ASCII));
        fullRes.headers().set(HttpHeaderNames.CONTENT_LENGTH, fullRes.content().readableBytes());
        ch.writeOutbound(fullRes);

        HttpResponse res = ch.readOutbound();
        assertThat(res, is(not(instanceOf(HttpContent.class))));

        assertThat(res.headers().get(HttpHeaderNames.TRANSFER_ENCODING), is(nullValue()));
        assertThat(res.headers().get(HttpHeaderNames.CONTENT_ENCODING), is("gzip"));

        long contentLengthHeaderValue = HttpUtil.getContentLength(res);
        long observedLength = 0;

        HttpContent c = ch.readOutbound();
        observedLength += c.content().readableBytes();
        assertThat(ByteBufUtil.hexDump(c.content()), is("1f8b0800000000000000f248cdc9c9d75108cf2fca4901000000ffff"));
        c.release();

        c = ch.readOutbound();
        observedLength += c.content().readableBytes();
        assertThat(ByteBufUtil.hexDump(c.content()), is("0300c6865b260c000000"));
        c.release();

        LastHttpContent last = ch.readOutbound();
        assertThat(last.content().readableBytes(), is(0));
        last.release();

        assertThat(ch.readOutbound(), is(nullValue()));
        assertEquals(contentLengthHeaderValue, observedLength);
    }

    @Test
    public void testFullContent() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpContentCompressor());
        ch.writeInbound(newRequest());

        FullHttpResponse res = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
            Unpooled.copiedBuffer("Hello, World", CharsetUtil.US_ASCII));
        ch.writeOutbound(res);

        assertEncodedResponse(ch);
        HttpContent c = ch.readOutbound();
        assertThat(ByteBufUtil.hexDump(c.content()), is("1f8b0800000000000000f248cdc9c9d75108cf2fca4901000000ffff"));
        c.release();

        c = ch.readOutbound();
        assertThat(ByteBufUtil.hexDump(c.content()), is("0300c6865b260c000000"));
        c.release();

        LastHttpContent last = ch.readOutbound();
        assertThat(last.content().readableBytes(), is(0));
        last.release();

        assertThat(ch.readOutbound(), is(nullValue()));
    }

    @Test
    public void testExecutorPreserveOrdering() throws Exception {
        final EventLoopGroup compressorGroup = new DefaultEventLoopGroup(1);
        EventLoopGroup localGroup = new DefaultEventLoopGroup(1);
        Channel server = null;
        Channel client = null;
        try {
            ServerBootstrap bootstrap = new ServerBootstrap()
                .channel(LocalServerChannel.class)
                .group(localGroup)
                .childHandler(new ChannelInitializer<LocalChannel>() {
                @Override
                protected void initChannel(LocalChannel ch) throws Exception {
                    ch.pipeline()
                        .addLast(new HttpServerCodec())
                        .addLast(new HttpObjectAggregator(1024))
                        .addLast(compressorGroup, new HttpContentCompressor())
                        .addLast(new ChannelOutboundHandlerAdapter() {
                            @Override
                            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise)
                                throws Exception {
                                super.write(ctx, msg, promise);
                            }
                        })
                        .addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                if (msg instanceof FullHttpRequest) {
                                    FullHttpResponse res =
                                        new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                                            Unpooled.copiedBuffer("Hello, World", CharsetUtil.US_ASCII));
                                    ctx.writeAndFlush(res);
                                    ReferenceCountUtil.release(msg);
                                    return;
                                }
                                super.channelRead(ctx, msg);
                            }
                        });
                }
            });

            LocalAddress address = new LocalAddress(UUID.randomUUID().toString());
            server = bootstrap.bind(address).sync().channel();

            final BlockingQueue<HttpObject> responses = new LinkedBlockingQueue<HttpObject>();

            client = new Bootstrap()
                .channel(LocalChannel.class)
                .remoteAddress(address)
                .group(localGroup)
                .handler(new ChannelInitializer<LocalChannel>() {
                @Override
                protected void initChannel(LocalChannel ch) throws Exception {
                    ch.pipeline().addLast(new HttpClientCodec()).addLast(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                            if (msg instanceof HttpObject) {
                                responses.put((HttpObject) msg);
                                return;
                            }
                            super.channelRead(ctx, msg);
                        }
                    });
                }
            }).connect().sync().channel();

            client.writeAndFlush(newRequest()).sync();

            assertEncodedResponse((HttpResponse) responses.poll(1, TimeUnit.SECONDS));
            HttpContent c = (HttpContent) responses.poll(1, TimeUnit.SECONDS);
            assertNotNull(c);
            assertThat(ByteBufUtil.hexDump(c.content()),
                is("1f8b0800000000000000f248cdc9c9d75108cf2fca4901000000ffff"));
            c.release();

            c = (HttpContent) responses.poll(1, TimeUnit.SECONDS);
            assertNotNull(c);
            assertThat(ByteBufUtil.hexDump(c.content()), is("0300c6865b260c000000"));
            c.release();

            LastHttpContent last = (LastHttpContent) responses.poll(1, TimeUnit.SECONDS);
            assertNotNull(last);
            assertThat(last.content().readableBytes(), is(0));
            last.release();

            assertNull(responses.poll(1, TimeUnit.SECONDS));
        } finally {
            if (client != null) {
                client.close().sync();
            }
            if (server != null) {
                server.close().sync();
            }
            compressorGroup.shutdownGracefully();
            localGroup.shutdownGracefully();
        }
    }

    /**
     * If the length of the content is unknown, {@link HttpContentEncoder} should not skip encoding the content
     * even if the actual length is turned out to be 0.
     */
    @Test
    public void testEmptySplitContent() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpContentCompressor());
        ch.writeInbound(newRequest());

        ch.writeOutbound(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK));
        assertEncodedResponse(ch);

        ch.writeOutbound(LastHttpContent.EMPTY_LAST_CONTENT);
        HttpContent chunk = ch.readOutbound();
        assertThat(ByteBufUtil.hexDump(chunk.content()), is("1f8b080000000000000003000000000000000000"));
        assertThat(chunk, is(instanceOf(HttpContent.class)));
        chunk.release();

        chunk = ch.readOutbound();
        assertThat(chunk.content().isReadable(), is(false));
        assertThat(chunk, is(instanceOf(LastHttpContent.class)));
        chunk.release();

        assertThat(ch.readOutbound(), is(nullValue()));
    }

    /**
     * If the length of the content is 0 for sure, {@link HttpContentEncoder} should skip encoding.
     */
    @Test
    public void testEmptyFullContent() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpContentCompressor());
        ch.writeInbound(newRequest());

        FullHttpResponse res = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.EMPTY_BUFFER);
        ch.writeOutbound(res);

        Object o = ch.readOutbound();
        assertThat(o, is(instanceOf(FullHttpResponse.class)));

        res = (FullHttpResponse) o;
        assertThat(res.headers().get(HttpHeaderNames.TRANSFER_ENCODING), is(nullValue()));

        // Content encoding shouldn't be modified.
        assertThat(res.headers().get(HttpHeaderNames.CONTENT_ENCODING), is(nullValue()));
        assertThat(res.content().readableBytes(), is(0));
        assertThat(res.content().toString(CharsetUtil.US_ASCII), is(""));
        res.release();

        assertThat(ch.readOutbound(), is(nullValue()));
    }

    @Test
    public void testEmptyFullContentWithTrailer() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpContentCompressor());
        ch.writeInbound(newRequest());

        FullHttpResponse res = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.EMPTY_BUFFER);
        res.trailingHeaders().set(of("X-Test"), of("Netty"));
        ch.writeOutbound(res);

        Object o = ch.readOutbound();
        assertThat(o, is(instanceOf(FullHttpResponse.class)));

        res = (FullHttpResponse) o;
        assertThat(res.headers().get(HttpHeaderNames.TRANSFER_ENCODING), is(nullValue()));

        // Content encoding shouldn't be modified.
        assertThat(res.headers().get(HttpHeaderNames.CONTENT_ENCODING), is(nullValue()));
        assertThat(res.content().readableBytes(), is(0));
        assertThat(res.content().toString(CharsetUtil.US_ASCII), is(""));
        assertEquals("Netty", res.trailingHeaders().get(of("X-Test")));
        assertEquals(DecoderResult.SUCCESS, res.decoderResult());
        assertThat(ch.readOutbound(), is(nullValue()));
    }

    @Test
    public void test100Continue() throws Exception {
        FullHttpRequest request = newRequest();
        HttpUtil.set100ContinueExpected(request, true);

        EmbeddedChannel ch = new EmbeddedChannel(new HttpContentCompressor());
        ch.writeInbound(request);

        FullHttpResponse continueResponse = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE, Unpooled.EMPTY_BUFFER);

        ch.writeOutbound(continueResponse);

        FullHttpResponse res = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.EMPTY_BUFFER);
        res.trailingHeaders().set(of("X-Test"), of("Netty"));
        ch.writeOutbound(res);

        Object o = ch.readOutbound();
        assertThat(o, is(instanceOf(FullHttpResponse.class)));

        res = (FullHttpResponse) o;
        assertSame(continueResponse, res);
        res.release();

        o = ch.readOutbound();
        assertThat(o, is(instanceOf(FullHttpResponse.class)));

        res = (FullHttpResponse) o;
        assertThat(res.headers().get(HttpHeaderNames.TRANSFER_ENCODING), is(nullValue()));

        // Content encoding shouldn't be modified.
        assertThat(res.headers().get(HttpHeaderNames.CONTENT_ENCODING), is(nullValue()));
        assertThat(res.content().readableBytes(), is(0));
        assertThat(res.content().toString(CharsetUtil.US_ASCII), is(""));
        assertEquals("Netty", res.trailingHeaders().get(of("X-Test")));
        assertEquals(DecoderResult.SUCCESS, res.decoderResult());
        assertThat(ch.readOutbound(), is(nullValue()));
    }

    @Test
    public void testTooManyResponses() throws Exception {
        FullHttpRequest request = newRequest();
        EmbeddedChannel ch = new EmbeddedChannel(new HttpContentCompressor());
        ch.writeInbound(request);

        ch.writeOutbound(new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.EMPTY_BUFFER));

        try {
            ch.writeOutbound(new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.EMPTY_BUFFER));
            fail();
        } catch (EncoderException e) {
            assertTrue(e.getCause() instanceof IllegalStateException);
        }
        assertTrue(ch.finish());
        for (;;) {
            Object message = ch.readOutbound();
            if (message == null) {
                break;
            }
            ReferenceCountUtil.release(message);
        }
        for (;;) {
            Object message = ch.readInbound();
            if (message == null) {
                break;
            }
            ReferenceCountUtil.release(message);
        }
    }

    @Test
    public void testIdentity() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpContentCompressor());
        assertTrue(ch.writeInbound(newRequest()));

        FullHttpResponse res = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                Unpooled.copiedBuffer("Hello, World", CharsetUtil.US_ASCII));
        int len = res.content().readableBytes();
        res.headers().set(HttpHeaderNames.CONTENT_LENGTH, len);
        res.headers().set(HttpHeaderNames.CONTENT_ENCODING, HttpHeaderValues.IDENTITY);
        assertTrue(ch.writeOutbound(res));

        FullHttpResponse response = ch.readOutbound();
        assertEquals(String.valueOf(len), response.headers().get(HttpHeaderNames.CONTENT_LENGTH));
        assertEquals(HttpHeaderValues.IDENTITY.toString(), response.headers().get(HttpHeaderNames.CONTENT_ENCODING));
        assertEquals("Hello, World", response.content().toString(CharsetUtil.US_ASCII));
        response.release();

        assertTrue(ch.finishAndReleaseAll());
    }

    @Test
    public void testCustomEncoding() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpContentCompressor());
        assertTrue(ch.writeInbound(newRequest()));

        FullHttpResponse res = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                Unpooled.copiedBuffer("Hello, World", CharsetUtil.US_ASCII));
        int len = res.content().readableBytes();
        res.headers().set(HttpHeaderNames.CONTENT_LENGTH, len);
        res.headers().set(HttpHeaderNames.CONTENT_ENCODING, "ascii");
        assertTrue(ch.writeOutbound(res));

        FullHttpResponse response = ch.readOutbound();
        assertEquals(String.valueOf(len), response.headers().get(HttpHeaderNames.CONTENT_LENGTH));
        assertEquals("ascii", response.headers().get(HttpHeaderNames.CONTENT_ENCODING));
        assertEquals("Hello, World", response.content().toString(CharsetUtil.US_ASCII));
        response.release();

        assertTrue(ch.finishAndReleaseAll());
    }

    @Test
    public void testCompressThresholdAllCompress() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpContentCompressor());
        assertTrue(ch.writeInbound(newRequest()));

        FullHttpResponse res1023 = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                Unpooled.wrappedBuffer(new byte[1023]));
        assertTrue(ch.writeOutbound(res1023));
        DefaultHttpResponse response1023 = ch.readOutbound();
        assertThat(response1023.headers().get(HttpHeaderNames.CONTENT_ENCODING), is("gzip"));
        ch.releaseOutbound();

        assertTrue(ch.writeInbound(newRequest()));
        FullHttpResponse res1024 = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                Unpooled.wrappedBuffer(new byte[1024]));
        assertTrue(ch.writeOutbound(res1024));
        DefaultHttpResponse response1024 = ch.readOutbound();
        assertThat(response1024.headers().get(HttpHeaderNames.CONTENT_ENCODING), is("gzip"));
        assertTrue(ch.finishAndReleaseAll());
    }

    @Test
    public void testCompressThresholdNotCompress() throws Exception {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpContentCompressor(6, 15, 8, 1024));
        assertTrue(ch.writeInbound(newRequest()));

        FullHttpResponse res1023 = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                Unpooled.wrappedBuffer(new byte[1023]));
        assertTrue(ch.writeOutbound(res1023));
        DefaultHttpResponse response1023 = ch.readOutbound();
        assertFalse(response1023.headers().contains(HttpHeaderNames.CONTENT_ENCODING));
        ch.releaseOutbound();

        assertTrue(ch.writeInbound(newRequest()));
        FullHttpResponse res1024 = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                Unpooled.wrappedBuffer(new byte[1024]));
        assertTrue(ch.writeOutbound(res1024));
        DefaultHttpResponse response1024 = ch.readOutbound();
        assertThat(response1024.headers().get(HttpHeaderNames.CONTENT_ENCODING), is("gzip"));
        assertTrue(ch.finishAndReleaseAll());
    }

    @Test
    public void testMultipleAcceptEncodingHeaders() {
        FullHttpRequest request = newRequest();
        request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, "unknown; q=1.0")
               .add(HttpHeaderNames.ACCEPT_ENCODING, "gzip; q=0.5")
               .add(HttpHeaderNames.ACCEPT_ENCODING, "deflate; q=0");

        EmbeddedChannel ch = new EmbeddedChannel(new HttpContentCompressor());

        assertTrue(ch.writeInbound(request));

        FullHttpResponse res = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                Unpooled.copiedBuffer("Gzip Win", CharsetUtil.US_ASCII));
        assertTrue(ch.writeOutbound(res));

        assertEncodedResponse(ch);
        HttpContent c = ch.readOutbound();
        assertThat(ByteBufUtil.hexDump(c.content()), is("1f8b080000000000000072afca2c5008cfcc03000000ffff"));
        c.release();

        c = ch.readOutbound();
        assertThat(ByteBufUtil.hexDump(c.content()), is("03001f2ebf0f08000000"));
        c.release();

        LastHttpContent last = ch.readOutbound();
        assertThat(last.content().readableBytes(), is(0));
        last.release();

        assertThat(ch.readOutbound(), is(nullValue()));
        assertTrue(ch.finishAndReleaseAll());
    }

    private static FullHttpRequest newRequest() {
        FullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
        req.headers().set(HttpHeaderNames.ACCEPT_ENCODING, "gzip");
        return req;
    }

    private static void assertEncodedResponse(EmbeddedChannel ch) {
        Object o = ch.readOutbound();
        assertThat(o, is(instanceOf(HttpResponse.class)));

        assertEncodedResponse((HttpResponse) o);
    }

    private static void assertEncodedResponse(HttpResponse res) {
        assertThat(res, is(not(instanceOf(HttpContent.class))));
        assertThat(res.headers().get(HttpHeaderNames.TRANSFER_ENCODING), is("chunked"));
        assertThat(res.headers().get(HttpHeaderNames.CONTENT_LENGTH), is(nullValue()));
        assertThat(res.headers().get(HttpHeaderNames.CONTENT_ENCODING), is("gzip"));
    }
    private static void assertAssembledEncodedResponse(EmbeddedChannel ch) {
        Object o = ch.readOutbound();
        assertThat(o, is(instanceOf(AssembledHttpResponse.class)));

        AssembledHttpResponse res = (AssembledHttpResponse) o;
        try {
            assertThat(res, is(instanceOf(HttpContent.class)));
            assertThat(res.headers().get(HttpHeaderNames.TRANSFER_ENCODING), is("chunked"));
            assertThat(res.headers().get(HttpHeaderNames.CONTENT_LENGTH), is(nullValue()));
            assertThat(res.headers().get(HttpHeaderNames.CONTENT_ENCODING), is("gzip"));
        } finally {
            res.release();
        }
    }

    static class AssembledHttpResponse extends DefaultHttpResponse implements HttpContent {

        private final ByteBuf content;

        AssembledHttpResponse(HttpVersion version, HttpResponseStatus status, ByteBuf content) {
            super(version, status);
            this.content = content;
        }

        @Override
        public HttpContent copy() {
            throw new UnsupportedOperationException();
        }

        @Override
        public HttpContent duplicate() {
            throw new UnsupportedOperationException();
        }

        @Override
        public HttpContent retainedDuplicate() {
            throw new UnsupportedOperationException();
        }

        @Override
        public HttpContent replace(ByteBuf content) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AssembledHttpResponse retain() {
            content.retain();
            return this;
        }

        @Override
        public AssembledHttpResponse retain(int increment) {
            content.retain(increment);
            return this;
        }

        @Override
        public ByteBuf content() {
            return content;
        }

        @Override
        public int refCnt() {
            return content.refCnt();
        }

        @Override
        public boolean release() {
            return content.release();
        }

        @Override
        public boolean release(int decrement) {
            return content.release(decrement);
        }

        @Override
        public AssembledHttpResponse touch() {
            content.touch();
            return this;
        }

        @Override
        public AssembledHttpResponse touch(Object hint) {
            content.touch(hint);
            return this;
        }
    }
}
