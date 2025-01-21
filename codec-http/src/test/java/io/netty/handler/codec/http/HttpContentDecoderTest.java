/*
 * Copyright 2015 The Netty Project
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
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.compression.Brotli;
import io.netty.handler.codec.compression.ZlibCodecFactory;
import io.netty.handler.codec.compression.ZlibDecoder;
import io.netty.handler.codec.compression.ZlibEncoder;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.PlatformDependent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class HttpContentDecoderTest {
    private static final String HELLO_WORLD = "hello, world";
    private static final byte[] GZ_HELLO_WORLD = {
            31, -117, 8, 8, 12, 3, -74, 84, 0, 3, 50, 0, -53, 72, -51, -55, -55,
            -41, 81, 40, -49, 47, -54, 73, 1, 0, 58, 114, -85, -1, 12, 0, 0, 0
    };
    private static final byte[] SNAPPY_HELLO_WORLD = {
            -1, 6, 0, 0, 115, 78, 97, 80, 112, 89, 1, 16, 0, 0, 11, -66, -63,
            -22, 104, 101, 108, 108, 111, 44, 32, 119, 111, 114, 108, 100
    };
    private static final String SAMPLE_STRING = "Hello, I am Meow!. A small kitten. :)" +
            "I sleep all day, and meow all night.";
    private static final byte[] SAMPLE_BZ_BYTES = new byte[]{27, 72, 0, 0, -60, -102, 91, -86, 103, 20,
            -28, -23, 54, -101, 11, -106, -16, -32, -95, -61, -37, 94, -16, 97, -40, -93, -56, 18, 21, 86,
            -110, 82, -41, 102, -89, 20, 11, 10, -68, -31, 96, -116, -55, -80, -31, -91, 96, -64, 83, 51,
            -39, 13, -21, 92, -16, -119, 124, -31, 18, 78, -1, 91, 82, 105, -116, -95, -22, -11, -70, -45, 0};

    private static final byte[] SAMPLE_ZSTD_BYTES = new byte[]{40, -75, 47, -3, 32, 73, 45, 2, 0, 2, -124, 14,
            22, -112, -75, 109, 11, -112, 113, 101, -65, 53, 59, -25, -51, -51, -78, 40, -120, -69, -32, 110, -7,
            -81, 28, -90, -112, 3, -23, -38, -89, -113, -98, 75, -32, -114, 1, 63, 38, 27, 97, -80, -23, -107, 66,
            -119, 17, 47, -109, 9, 91, -80, -10, -122, -13, 108, 92, 22, -15, 69, 2, 0, 53, -27, 55, -125, 89, 6};

    @Test
    public void testBinaryDecompression() throws Exception {
        // baseline test: zlib library and test helpers work correctly.
        byte[] helloWorld = gzDecompress(GZ_HELLO_WORLD);
        assertEquals(HELLO_WORLD.length(), helloWorld.length);
        assertEquals(HELLO_WORLD, new String(helloWorld, CharsetUtil.US_ASCII));

        String fullCycleTest = "full cycle test";
        byte[] compressed = gzCompress(fullCycleTest.getBytes(CharsetUtil.US_ASCII));
        byte[] decompressed = gzDecompress(compressed);
        assertEquals(decompressed.length, fullCycleTest.length());
        assertEquals(fullCycleTest, new String(decompressed, CharsetUtil.US_ASCII));
    }

    @Test
    public void testRequestDecompression() {
        // baseline test: request decoder, content decompressor && request aggregator work as expected
        HttpRequestDecoder decoder = new HttpRequestDecoder();
        HttpContentDecoder decompressor = new HttpContentDecompressor();
        HttpObjectAggregator aggregator = new HttpObjectAggregator(1024);
        EmbeddedChannel channel = new EmbeddedChannel(decoder, decompressor, aggregator);

        String headers = "POST / HTTP/1.1\r\n" +
                         "Content-Length: " + GZ_HELLO_WORLD.length + "\r\n" +
                         "Content-Encoding: gzip\r\n" +
                         "\r\n";
        ByteBuf buf = Unpooled.copiedBuffer(headers.getBytes(CharsetUtil.US_ASCII), GZ_HELLO_WORLD);
        assertTrue(channel.writeInbound(buf));

        Object o = channel.readInbound();
        assertThat(o, is(instanceOf(FullHttpRequest.class)));
        FullHttpRequest req = (FullHttpRequest) o;
        assertEquals(HELLO_WORLD.length(), req.headers().getInt(HttpHeaderNames.CONTENT_LENGTH).intValue());
        assertEquals(HELLO_WORLD, req.content().toString(CharsetUtil.US_ASCII));
        req.release();

        assertHasInboundMessages(channel, false);
        assertHasOutboundMessages(channel, false);
        assertFalse(channel.finish()); // assert that no messages are left in channel
    }

    @Test
    public void testChunkedRequestDecompression() {
        HttpResponseDecoder decoder = new HttpResponseDecoder();
        HttpContentDecoder decompressor = new HttpContentDecompressor();

        EmbeddedChannel channel = new EmbeddedChannel(decoder, decompressor, null);

        String headers = "HTTP/1.1 200 OK\r\n"
                + "Transfer-Encoding: chunked\r\n"
                + "Trailer: My-Trailer\r\n"
                + "Content-Encoding: gzip\r\n\r\n";

        channel.writeInbound(Unpooled.copiedBuffer(headers.getBytes(CharsetUtil.US_ASCII)));

        String chunkLength = Integer.toHexString(GZ_HELLO_WORLD.length);
        assertTrue(channel.writeInbound(Unpooled.copiedBuffer(chunkLength + "\r\n", CharsetUtil.US_ASCII)));
        assertTrue(channel.writeInbound(Unpooled.copiedBuffer(GZ_HELLO_WORLD)));
        assertTrue(channel.writeInbound(Unpooled.copiedBuffer("\r\n".getBytes(CharsetUtil.US_ASCII))));
        assertTrue(channel.writeInbound(Unpooled.copiedBuffer("0\r\n", CharsetUtil.US_ASCII)));
        assertTrue(channel.writeInbound(Unpooled.copiedBuffer("My-Trailer: 42\r\n\r\n\r\n", CharsetUtil.US_ASCII)));

        Object ob1 = channel.readInbound();
        assertThat(ob1, is(instanceOf(DefaultHttpResponse.class)));

        Object ob2 = channel.readInbound();
        assertThat(ob2, is(instanceOf(HttpContent.class)));
        HttpContent content = (HttpContent) ob2;
        assertEquals(HELLO_WORLD, content.content().toString(CharsetUtil.US_ASCII));
        content.release();

        Object ob3 = channel.readInbound();
        assertThat(ob3, is(instanceOf(LastHttpContent.class)));
        LastHttpContent lastContent = (LastHttpContent) ob3;
        assertNotNull(lastContent.decoderResult());
        assertTrue(lastContent.decoderResult().isSuccess());
        assertFalse(lastContent.trailingHeaders().isEmpty());
        assertEquals("42", lastContent.trailingHeaders().get("My-Trailer"));
        assertHasInboundMessages(channel, false);
        assertHasOutboundMessages(channel, false);
        assertFalse(channel.finish());
    }

    @Test
    public void testSnappyResponseDecompression() {
        // baseline test: response decoder, content decompressor && request aggregator work as expected
        HttpResponseDecoder decoder = new HttpResponseDecoder();
        HttpContentDecoder decompressor = new HttpContentDecompressor();
        HttpObjectAggregator aggregator = new HttpObjectAggregator(1024);
        EmbeddedChannel channel = new EmbeddedChannel(decoder, decompressor, aggregator);

        String headers = "HTTP/1.1 200 OK\r\n" +
                         "Content-Length: " + SNAPPY_HELLO_WORLD.length + "\r\n" +
                         "Content-Encoding: snappy\r\n" +
                         "\r\n";
        ByteBuf buf = Unpooled.copiedBuffer(headers.getBytes(CharsetUtil.UTF_8), SNAPPY_HELLO_WORLD);
        assertTrue(channel.writeInbound(buf));

        Object o = channel.readInbound();
        assertThat(o, is(instanceOf(FullHttpResponse.class)));
        FullHttpResponse resp = (FullHttpResponse) o;
        assertEquals(HELLO_WORLD.length(), resp.headers().getInt(HttpHeaderNames.CONTENT_LENGTH).intValue());
        assertEquals(HELLO_WORLD, resp.content().toString(CharsetUtil.UTF_8));
        resp.release();

        assertHasInboundMessages(channel, false);
        assertHasOutboundMessages(channel, false);
        assertFalse(channel.finish()); // assert that no messages are left in channel
    }

    @Test
    public void testResponseDecompression() {
        // baseline test: response decoder, content decompressor && request aggregator work as expected
        HttpResponseDecoder decoder = new HttpResponseDecoder();
        HttpContentDecoder decompressor = new HttpContentDecompressor();
        HttpObjectAggregator aggregator = new HttpObjectAggregator(1024);

        EmbeddedChannel channel = new EmbeddedChannel(decoder, decompressor, aggregator);

        String headers = "HTTP/1.1 200 OK\r\n" +
                "Content-Length: " + GZ_HELLO_WORLD.length + "\r\n" +
                "Content-Encoding: gzip\r\n" +
                "\r\n";
        ByteBuf buf = Unpooled.copiedBuffer(headers.getBytes(CharsetUtil.US_ASCII), GZ_HELLO_WORLD);
        assertTrue(channel.writeInbound(buf));

        Object o = channel.readInbound();
        assertThat(o, is(instanceOf(FullHttpResponse.class)));
        FullHttpResponse resp = (FullHttpResponse) o;
        assertEquals(HELLO_WORLD.length(), resp.headers().getInt(HttpHeaderNames.CONTENT_LENGTH).intValue());
        assertEquals(HELLO_WORLD, resp.content().toString(CharsetUtil.US_ASCII));
        resp.release();

        assertHasInboundMessages(channel, false);
        assertHasOutboundMessages(channel, false);
        assertFalse(channel.finish()); // assert that no messages are left in channel
    }

    @DisabledIf(value = "isNotSupported", disabledReason = "Brotli is not supported on this platform")
    @Test
    public void testResponseBrotliDecompression() throws Throwable {
        Brotli.ensureAvailability();

        HttpResponseDecoder decoder = new HttpResponseDecoder();
        HttpContentDecoder decompressor = new HttpContentDecompressor();
        HttpObjectAggregator aggregator = new HttpObjectAggregator(Integer.MAX_VALUE);
        EmbeddedChannel channel = new EmbeddedChannel(decoder, decompressor, aggregator);

        String headers = "HTTP/1.1 200 OK\r\n" +
          "Content-Length: " + SAMPLE_BZ_BYTES.length + "\r\n" +
          "Content-Encoding: br\r\n" +
          "\r\n";
        ByteBuf buf = Unpooled.wrappedBuffer(headers.getBytes(CharsetUtil.US_ASCII), SAMPLE_BZ_BYTES);
        assertTrue(channel.writeInbound(buf));

        Object o = channel.readInbound();
        assertThat(o, is(instanceOf(FullHttpResponse.class)));
        FullHttpResponse resp = (FullHttpResponse) o;
        assertNull(resp.headers().get(HttpHeaderNames.CONTENT_ENCODING), "Content-Encoding header should be removed");
        assertEquals(SAMPLE_STRING, resp.content().toString(CharsetUtil.UTF_8),
                "Response body should match uncompressed string");
        resp.release();

        assertHasInboundMessages(channel, false);
        assertHasOutboundMessages(channel, false);
        assertFalse(channel.finish()); // assert that no messages are left in channel
    }

    @DisabledIf(value = "isNotSupported", disabledReason = "Brotli is not supported on this platform")
    @Test
    public void testResponseChunksBrotliDecompression() throws Throwable {
        Brotli.ensureAvailability();

        HttpResponseDecoder decoder = new HttpResponseDecoder();
        HttpContentDecoder decompressor = new HttpContentDecompressor();
        HttpObjectAggregator aggregator = new HttpObjectAggregator(Integer.MAX_VALUE);
        EmbeddedChannel channel = new EmbeddedChannel(decoder, decompressor, aggregator);

        String headers = "HTTP/1.1 200 OK\r\n" +
          "Content-Length: " + SAMPLE_BZ_BYTES.length + "\r\n" +
          "Content-Encoding: br\r\n" +
          "\r\n";

        assertFalse(channel.writeInbound(Unpooled.wrappedBuffer(headers.getBytes(CharsetUtil.US_ASCII))));

        int offset = 0;
        while (offset < SAMPLE_BZ_BYTES.length) {
            int len = Math.min(1500, SAMPLE_BZ_BYTES.length - offset);
            boolean available = channel.writeInbound(Unpooled.wrappedBuffer(SAMPLE_BZ_BYTES, offset, len));
            offset += 1500;
            if (offset < SAMPLE_BZ_BYTES.length) {
                assertFalse(available);
            } else {
                assertTrue(available);
            }
        }

        Object o = channel.readInbound();
        assertThat(o, is(instanceOf(FullHttpResponse.class)));
        FullHttpResponse resp = (FullHttpResponse) o;
        assertEquals(SAMPLE_STRING, resp.content().toString(CharsetUtil.UTF_8),
                "Response body should match uncompressed string");
        resp.release();

        assertHasInboundMessages(channel, false);
        assertHasOutboundMessages(channel, false);
        assertFalse(channel.finish()); // assert that no messages are left in channel
    }

    @Test
    public void testResponseZstdDecompression() throws Throwable {
        HttpResponseDecoder decoder = new HttpResponseDecoder();
        HttpContentDecoder decompressor = new HttpContentDecompressor();
        HttpObjectAggregator aggregator = new HttpObjectAggregator(Integer.MAX_VALUE);
        EmbeddedChannel channel = new EmbeddedChannel(decoder, decompressor, aggregator);

        String headers = "HTTP/1.1 200 OK\r\n" +
                "Content-Length: " + SAMPLE_ZSTD_BYTES.length + "\r\n" +
                "Content-Encoding: zstd\r\n" +
                "\r\n";
        ByteBuf buf = Unpooled.wrappedBuffer(headers.getBytes(CharsetUtil.US_ASCII), SAMPLE_ZSTD_BYTES);
        assertTrue(channel.writeInbound(buf));

        Object o = channel.readInbound();
        assertThat(o, is(instanceOf(FullHttpResponse.class)));
        FullHttpResponse resp = (FullHttpResponse) o;
        assertNull(resp.headers().get(HttpHeaderNames.CONTENT_ENCODING), "Content-Encoding header should be removed");
        assertEquals(SAMPLE_STRING, resp.content().toString(CharsetUtil.UTF_8),
                "Response body should match uncompressed string");
        resp.release();

        assertHasInboundMessages(channel, false);
        assertHasOutboundMessages(channel, false);
        assertFalse(channel.finish()); // assert that no messages are left in channel
    }

    @Test
    public void testResponseChunksZstdDecompression() throws Throwable {
        HttpResponseDecoder decoder = new HttpResponseDecoder();
        HttpContentDecoder decompressor = new HttpContentDecompressor();
        HttpObjectAggregator aggregator = new HttpObjectAggregator(Integer.MAX_VALUE);
        EmbeddedChannel channel = new EmbeddedChannel(decoder, decompressor, aggregator);

        String headers = "HTTP/1.1 200 OK\r\n" +
                "Content-Length: " + SAMPLE_ZSTD_BYTES.length + "\r\n" +
                "Content-Encoding: zstd\r\n" +
                "\r\n";

        assertFalse(channel.writeInbound(Unpooled.wrappedBuffer(headers.getBytes(CharsetUtil.US_ASCII))));

        int offset = 0;
        while (offset < SAMPLE_ZSTD_BYTES.length) {
            int len = Math.min(1500, SAMPLE_ZSTD_BYTES.length - offset);
            boolean available = channel.writeInbound(Unpooled.wrappedBuffer(SAMPLE_ZSTD_BYTES, offset, len));
            offset += 1500;
            if (offset < SAMPLE_ZSTD_BYTES.length) {
                assertFalse(available);
            } else {
                assertTrue(available);
            }
        }

        Object o = channel.readInbound();
        assertThat(o, is(instanceOf(FullHttpResponse.class)));
        FullHttpResponse resp = (FullHttpResponse) o;
        assertEquals(SAMPLE_STRING, resp.content().toString(CharsetUtil.UTF_8),
          "Response body should match uncompressed string");
        resp.release();

        assertHasInboundMessages(channel, false);
        assertHasOutboundMessages(channel, false);
        assertFalse(channel.finish()); // assert that no messages are left in channel
    }

    @Test
    public void testExpectContinueResponse1() {
        // request with header "Expect: 100-continue" must be replied with one "100 Continue" response
        // case 1: no ContentDecoder in chain at all (baseline test)
        HttpRequestDecoder decoder = new HttpRequestDecoder();
        HttpObjectAggregator aggregator = new HttpObjectAggregator(1024);
        EmbeddedChannel channel = new EmbeddedChannel(decoder, aggregator);
        String req = "POST / HTTP/1.1\r\n" +
                     "Content-Length: " + GZ_HELLO_WORLD.length + "\r\n" +
                     "Expect: 100-continue\r\n" +
                     "\r\n";
        // note: the following writeInbound() returns false as there is no message is inbound buffer
        // until HttpObjectAggregator caches composes a complete message.
        // however, http response "100 continue" must be sent as soon as headers are received
        assertFalse(channel.writeInbound(Unpooled.wrappedBuffer(req.getBytes())));

        Object o = channel.readOutbound();
        assertThat(o, is(instanceOf(FullHttpResponse.class)));
        FullHttpResponse r = (FullHttpResponse) o;
        assertEquals(100, r.status().code());
        assertTrue(channel.writeInbound(Unpooled.copiedBuffer(GZ_HELLO_WORLD)));
        r.release();

        assertHasInboundMessages(channel, true);
        assertHasOutboundMessages(channel, false);
        assertFalse(channel.finish());
    }

    @Test
    public void testExpectContinueResponse2() {
        // request with header "Expect: 100-continue" must be replied with one "100 Continue" response
        // case 2: contentDecoder is in chain, but the content is not encoded, should be no-op
        HttpRequestDecoder decoder = new HttpRequestDecoder();
        HttpContentDecoder decompressor = new HttpContentDecompressor();
        HttpObjectAggregator aggregator = new HttpObjectAggregator(1024);
        EmbeddedChannel channel = new EmbeddedChannel(decoder, decompressor, aggregator);
        String req = "POST / HTTP/1.1\r\n" +
                     "Content-Length: " + GZ_HELLO_WORLD.length + "\r\n" +
                     "Expect: 100-continue\r\n" +
                     "\r\n";
        assertFalse(channel.writeInbound(Unpooled.wrappedBuffer(req.getBytes())));

        Object o = channel.readOutbound();
        assertThat(o, is(instanceOf(FullHttpResponse.class)));
        FullHttpResponse r = (FullHttpResponse) o;
        assertEquals(100, r.status().code());
        r.release();
        assertTrue(channel.writeInbound(Unpooled.copiedBuffer(GZ_HELLO_WORLD)));

        assertHasInboundMessages(channel, true);
        assertHasOutboundMessages(channel, false);
        assertFalse(channel.finish());
    }

    @Test
    public void testExpectContinueResponse3() {
        // request with header "Expect: 100-continue" must be replied with one "100 Continue" response
        // case 3: ContentDecoder is in chain and content is encoded
        HttpRequestDecoder decoder = new HttpRequestDecoder();
        HttpContentDecoder decompressor = new HttpContentDecompressor();
        HttpObjectAggregator aggregator = new HttpObjectAggregator(1024);
        EmbeddedChannel channel = new EmbeddedChannel(decoder, decompressor, aggregator);
        String req = "POST / HTTP/1.1\r\n" +
                     "Content-Length: " + GZ_HELLO_WORLD.length + "\r\n" +
                     "Expect: 100-continue\r\n" +
                     "Content-Encoding: gzip\r\n" +
                     "\r\n";
        assertFalse(channel.writeInbound(Unpooled.wrappedBuffer(req.getBytes())));

        Object o = channel.readOutbound();
        assertThat(o, is(instanceOf(FullHttpResponse.class)));
        FullHttpResponse r = (FullHttpResponse) o;
        assertEquals(100, r.status().code());
        r.release();
        assertTrue(channel.writeInbound(Unpooled.copiedBuffer(GZ_HELLO_WORLD)));

        assertHasInboundMessages(channel, true);
        assertHasOutboundMessages(channel, false);
        assertFalse(channel.finish());
    }

    @Test
    public void testExpectContinueResponse4() {
        // request with header "Expect: 100-continue" must be replied with one "100 Continue" response
        // case 4: ObjectAggregator is up in chain
        HttpRequestDecoder decoder = new HttpRequestDecoder();
        HttpObjectAggregator aggregator = new HttpObjectAggregator(1024);
        HttpContentDecoder decompressor = new HttpContentDecompressor();
        EmbeddedChannel channel = new EmbeddedChannel(decoder, aggregator, decompressor);
        String req = "POST / HTTP/1.1\r\n" +
                     "Content-Length: " + GZ_HELLO_WORLD.length + "\r\n" +
                     "Expect: 100-continue\r\n" +
                     "Content-Encoding: gzip\r\n" +
                     "\r\n";
        assertFalse(channel.writeInbound(Unpooled.wrappedBuffer(req.getBytes())));

        Object o = channel.readOutbound();
        assertThat(o, is(instanceOf(FullHttpResponse.class)));
        FullHttpResponse r = (FullHttpResponse) o;
        assertEquals(100, r.status().code());
        r.release();
        assertTrue(channel.writeInbound(Unpooled.copiedBuffer(GZ_HELLO_WORLD)));

        assertHasInboundMessages(channel, true);
        assertHasOutboundMessages(channel, false);
        assertFalse(channel.finish());
    }

    @Test
    public void testExpectContinueResetHttpObjectDecoder() {
        // request with header "Expect: 100-continue" must be replied with one "100 Continue" response
        // case 5: Test that HttpObjectDecoder correctly resets its internal state after a failed expectation.
        HttpRequestDecoder decoder = new HttpRequestDecoder();
        final int maxBytes = 10;
        HttpObjectAggregator aggregator = new HttpObjectAggregator(maxBytes);
        final AtomicReference<FullHttpRequest> secondRequestRef = new AtomicReference<FullHttpRequest>();
        EmbeddedChannel channel = new EmbeddedChannel(decoder, aggregator, new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                if (msg instanceof FullHttpRequest) {
                    if (!secondRequestRef.compareAndSet(null, (FullHttpRequest) msg)) {
                        ((FullHttpRequest) msg).release();
                    }
                } else {
                    ReferenceCountUtil.release(msg);
                }
            }
        });
        String req1 = "POST /1 HTTP/1.1\r\n" +
                "Content-Length: " + (maxBytes + 1) + "\r\n" +
                "Expect: 100-continue\r\n" +
                "\r\n";
        assertFalse(channel.writeInbound(Unpooled.wrappedBuffer(req1.getBytes(CharsetUtil.US_ASCII))));

        FullHttpResponse resp = channel.readOutbound();
        assertEquals(HttpStatusClass.CLIENT_ERROR, resp.status().codeClass());
        resp.release();

        String req2 = "POST /2 HTTP/1.1\r\n" +
                "Content-Length: " + maxBytes + "\r\n" +
                "Expect: 100-continue\r\n" +
                "\r\n";
        assertFalse(channel.writeInbound(Unpooled.wrappedBuffer(req2.getBytes(CharsetUtil.US_ASCII))));

        resp = channel.readOutbound();
        assertEquals(100, resp.status().code());
        resp.release();

        byte[] content = new byte[maxBytes];
        assertFalse(channel.writeInbound(Unpooled.wrappedBuffer(content)));

        FullHttpRequest req = secondRequestRef.get();
        assertNotNull(req);
        assertEquals("/2", req.uri());
        assertEquals(10, req.content().readableBytes());
        req.release();

        assertHasInboundMessages(channel, false);
        assertHasOutboundMessages(channel, false);
        assertFalse(channel.finish());
    }

    @Test
    public void testRequestContentLength1() {
        // case 1: test that ContentDecompressor either sets the correct Content-Length header
        // or removes it completely (handlers down the chain must rely on LastHttpContent object)

        // force content to be in more than one chunk (5 bytes/chunk)
        HttpRequestDecoder decoder = new HttpRequestDecoder(4096, 4096, 5);
        HttpContentDecoder decompressor = new HttpContentDecompressor();
        EmbeddedChannel channel = new EmbeddedChannel(decoder, decompressor);
        String headers = "POST / HTTP/1.1\r\n" +
                         "Content-Length: " + GZ_HELLO_WORLD.length + "\r\n" +
                         "Content-Encoding: gzip\r\n" +
                         "\r\n";
        ByteBuf buf = Unpooled.copiedBuffer(headers.getBytes(CharsetUtil.US_ASCII), GZ_HELLO_WORLD);
        assertTrue(channel.writeInbound(buf));

        Queue<Object> req = channel.inboundMessages();
        assertTrue(req.size() >= 1);
        Object o = req.peek();
        assertThat(o, is(instanceOf(HttpRequest.class)));
        HttpRequest r = (HttpRequest) o;
        String v = r.headers().get(HttpHeaderNames.CONTENT_LENGTH);
        Long value = v == null ? null : Long.parseLong(v);
        assertTrue(value == null || value.longValue() == HELLO_WORLD.length());

        assertHasInboundMessages(channel, true);
        assertHasOutboundMessages(channel, false);
        assertFalse(channel.finish());
    }

    @Test
    public void testRequestContentLength2() {
        // case 2: if HttpObjectAggregator is down the chain, then correct Content-Length header must be set

        // force content to be in more than one chunk (5 bytes/chunk)
        HttpRequestDecoder decoder = new HttpRequestDecoder(4096, 4096, 5);
        HttpContentDecoder decompressor = new HttpContentDecompressor();
        HttpObjectAggregator aggregator = new HttpObjectAggregator(1024);
        EmbeddedChannel channel = new EmbeddedChannel(decoder, decompressor, aggregator);
        String headers = "POST / HTTP/1.1\r\n" +
                         "Content-Length: " + GZ_HELLO_WORLD.length + "\r\n" +
                         "Content-Encoding: gzip\r\n" +
                         "\r\n";
        ByteBuf buf = Unpooled.copiedBuffer(headers.getBytes(CharsetUtil.US_ASCII), GZ_HELLO_WORLD);
        assertTrue(channel.writeInbound(buf));

        Object o = channel.readInbound();
        assertThat(o, is(instanceOf(FullHttpRequest.class)));
        FullHttpRequest r = (FullHttpRequest) o;
        String v = r.headers().get(HttpHeaderNames.CONTENT_LENGTH);
        Long value = v == null ? null : Long.parseLong(v);

        r.release();
        assertNotNull(value);
        assertEquals(HELLO_WORLD.length(), value.longValue());

        assertHasInboundMessages(channel, false);
        assertHasOutboundMessages(channel, false);
        assertFalse(channel.finish());
    }

    @Test
    public void testResponseContentLength1() {
        // case 1: test that ContentDecompressor either sets the correct Content-Length header
        // or removes it completely (handlers down the chain must rely on LastHttpContent object)

        // force content to be in more than one chunk (5 bytes/chunk)
        HttpResponseDecoder decoder = new HttpResponseDecoder(4096, 4096, 5);
        HttpContentDecoder decompressor = new HttpContentDecompressor();
        EmbeddedChannel channel = new EmbeddedChannel(decoder, decompressor);
        String headers = "HTTP/1.1 200 OK\r\n" +
                         "Content-Length: " + GZ_HELLO_WORLD.length + "\r\n" +
                         "Content-Encoding: gzip\r\n" +
                         "\r\n";
        ByteBuf buf = Unpooled.copiedBuffer(headers.getBytes(CharsetUtil.US_ASCII), GZ_HELLO_WORLD);
        assertTrue(channel.writeInbound(buf));

        Queue<Object> resp = channel.inboundMessages();
        assertTrue(resp.size() >= 1);
        Object o = resp.peek();
        assertThat(o, is(instanceOf(HttpResponse.class)));
        HttpResponse r = (HttpResponse) o;

        assertFalse(r.headers().contains(HttpHeaderNames.CONTENT_LENGTH), "Content-Length header not removed.");

        String transferEncoding = r.headers().get(HttpHeaderNames.TRANSFER_ENCODING);
        assertNotNull(transferEncoding, "Content-length as well as transfer-encoding not set.");
        assertEquals(HttpHeaderValues.CHUNKED.toString(), transferEncoding, "Unexpected transfer-encoding value.");

        assertHasInboundMessages(channel, true);
        assertHasOutboundMessages(channel, false);
        assertFalse(channel.finish());
    }

    @Test
    public void testResponseContentLength2() {
        // case 2: if HttpObjectAggregator is down the chain, then correct Content-Length header must be set

        // force content to be in more than one chunk (5 bytes/chunk)
        HttpResponseDecoder decoder = new HttpResponseDecoder(4096, 4096, 5);
        HttpContentDecoder decompressor = new HttpContentDecompressor();
        HttpObjectAggregator aggregator = new HttpObjectAggregator(1024);
        EmbeddedChannel channel = new EmbeddedChannel(decoder, decompressor, aggregator);
        String headers = "HTTP/1.1 200 OK\r\n" +
                         "Content-Length: " + GZ_HELLO_WORLD.length + "\r\n" +
                         "Content-Encoding: gzip\r\n" +
                         "\r\n";
        ByteBuf buf = Unpooled.copiedBuffer(headers.getBytes(CharsetUtil.US_ASCII), GZ_HELLO_WORLD);
        assertTrue(channel.writeInbound(buf));

        Object o = channel.readInbound();
        assertThat(o, is(instanceOf(FullHttpResponse.class)));
        FullHttpResponse r = (FullHttpResponse) o;
        String v = r.headers().get(HttpHeaderNames.CONTENT_LENGTH);
        Long value = v == null ? null : Long.parseLong(v);
        assertNotNull(value);
        assertEquals(HELLO_WORLD.length(), value.longValue());
        r.release();

        assertHasInboundMessages(channel, false);
        assertHasOutboundMessages(channel, false);
        assertFalse(channel.finish());
    }

    @Test
    public void testFullHttpRequest() {
        // test that ContentDecoder can be used after the ObjectAggregator
        HttpRequestDecoder decoder = new HttpRequestDecoder(4096, 4096, 5);
        HttpObjectAggregator aggregator = new HttpObjectAggregator(1024);
        HttpContentDecoder decompressor = new HttpContentDecompressor();
        EmbeddedChannel channel = new EmbeddedChannel(decoder, aggregator, decompressor);
        String headers = "POST / HTTP/1.1\r\n" +
                         "Content-Length: " + GZ_HELLO_WORLD.length + "\r\n" +
                         "Content-Encoding: gzip\r\n" +
                         "\r\n";
        assertTrue(channel.writeInbound(Unpooled.copiedBuffer(headers.getBytes(), GZ_HELLO_WORLD)));

        Queue<Object> req = channel.inboundMessages();
        assertTrue(req.size() > 1);
        int contentLength = 0;
        contentLength = calculateContentLength(req, contentLength);

        byte[] receivedContent = readContent(req, contentLength, true);

        assertEquals(HELLO_WORLD, new String(receivedContent, CharsetUtil.US_ASCII));

        assertHasInboundMessages(channel, true);
        assertHasOutboundMessages(channel, false);
        assertFalse(channel.finish());
    }

    @Test
    public void testFullHttpResponse() {
        // test that ContentDecoder can be used after the ObjectAggregator
        HttpResponseDecoder decoder = new HttpResponseDecoder(4096, 4096, 5);
        HttpObjectAggregator aggregator = new HttpObjectAggregator(1024);
        HttpContentDecoder decompressor = new HttpContentDecompressor();
        EmbeddedChannel channel = new EmbeddedChannel(decoder, aggregator, decompressor);
        String headers = "HTTP/1.1 200 OK\r\n" +
                         "Content-Length: " + GZ_HELLO_WORLD.length + "\r\n" +
                         "Content-Encoding: gzip\r\n" +
                         "\r\n";
        assertTrue(channel.writeInbound(Unpooled.copiedBuffer(headers.getBytes(), GZ_HELLO_WORLD)));

        Queue<Object> resp = channel.inboundMessages();
        assertTrue(resp.size() > 1);
        int contentLength = 0;
        contentLength = calculateContentLength(resp, contentLength);

        byte[] receivedContent = readContent(resp, contentLength, true);

        assertEquals(HELLO_WORLD, new String(receivedContent, CharsetUtil.US_ASCII));

        assertHasInboundMessages(channel, true);
        assertHasOutboundMessages(channel, false);
        assertFalse(channel.finish());
    }

    // See https://github.com/netty/netty/issues/5892
    @Test
    public void testFullHttpResponseEOF() {
        // test that ContentDecoder can be used after the ObjectAggregator
        HttpResponseDecoder decoder = new HttpResponseDecoder(4096, 4096, 5);
        HttpContentDecoder decompressor = new HttpContentDecompressor();
        EmbeddedChannel channel = new EmbeddedChannel(decoder, decompressor);
        String headers = "HTTP/1.1 200 OK\r\n" +
                "Content-Encoding: gzip\r\n" +
                "\r\n";
        assertTrue(channel.writeInbound(Unpooled.copiedBuffer(headers.getBytes(), GZ_HELLO_WORLD)));
        // This should terminate it.
        assertTrue(channel.finish());

        Queue<Object> resp = channel.inboundMessages();
        assertTrue(resp.size() > 1);
        int contentLength = 0;
        contentLength = calculateContentLength(resp, contentLength);

        byte[] receivedContent = readContent(resp, contentLength, false);

        assertEquals(HELLO_WORLD, new String(receivedContent, CharsetUtil.US_ASCII));

        assertHasInboundMessages(channel, true);
        assertHasOutboundMessages(channel, false);
        assertFalse(channel.finish());
    }

    @Test
    public void testCleanupThrows() {
        HttpContentDecoder decoder = new HttpContentDecoder() {
            @Override
            protected EmbeddedChannel newContentDecoder(String contentEncoding) throws Exception {
                return new EmbeddedChannel(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                        ctx.fireExceptionCaught(new DecoderException());
                        ctx.fireChannelInactive();
                    }
                });
            }
        };

        final AtomicBoolean channelInactiveCalled = new AtomicBoolean();
        EmbeddedChannel channel = new EmbeddedChannel(decoder, new ChannelInboundHandlerAdapter() {
            @Override
            public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                assertTrue(channelInactiveCalled.compareAndSet(false, true));
                super.channelInactive(ctx);
            }
        });
        assertTrue(channel.writeInbound(new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/")));
        HttpContent content = new DefaultHttpContent(Unpooled.buffer().writeZero(10));
        assertTrue(channel.writeInbound(content));
        assertEquals(1, content.refCnt());
        try {
            channel.finishAndReleaseAll();
            fail();
        } catch (CodecException expected) {
            // expected
        }
        assertTrue(channelInactiveCalled.get());
        assertEquals(0, content.refCnt());
    }

    @Test
    public void testTransferCodingGZIP() {
        String requestStr = "POST / HTTP/1.1\r\n" +
                "Content-Length: " + GZ_HELLO_WORLD.length + "\r\n" +
                "Transfer-Encoding: gzip\r\n" +
                "\r\n";
        HttpRequestDecoder decoder = new HttpRequestDecoder();
        HttpContentDecoder decompressor = new HttpContentDecompressor();
        EmbeddedChannel channel = new EmbeddedChannel(decoder, decompressor);

        channel.writeInbound(Unpooled.copiedBuffer(requestStr.getBytes()));
        channel.writeInbound(Unpooled.copiedBuffer(GZ_HELLO_WORLD));

        HttpRequest request = channel.readInbound();
        assertTrue(request.decoderResult().isSuccess());
        assertFalse(request.headers().contains(HttpHeaderNames.CONTENT_LENGTH));

        HttpContent content = channel.readInbound();
        assertTrue(content.decoderResult().isSuccess());
        assertEquals(HELLO_WORLD, content.content().toString(CharsetUtil.US_ASCII));
        content.release();

        LastHttpContent lastHttpContent = channel.readInbound();
        assertTrue(lastHttpContent.decoderResult().isSuccess());
        lastHttpContent.release();

        assertHasInboundMessages(channel, false);
        assertHasOutboundMessages(channel, false);
        assertFalse(channel.finish());
        channel.releaseInbound();
    }

    @Test
    public void testTransferCodingGZIPAndChunked() {
        String requestStr = "POST / HTTP/1.1\r\n" +
                "Host: example.com\r\n" +
                "Content-Type: application/x-www-form-urlencoded\r\n" +
                "Trailer: My-Trailer\r\n" +
                "Transfer-Encoding: gzip, chunked\r\n" +
                "\r\n";
        HttpRequestDecoder decoder = new HttpRequestDecoder();
        HttpContentDecoder decompressor = new HttpContentDecompressor();
        EmbeddedChannel channel = new EmbeddedChannel(decoder, decompressor);

        assertTrue(channel.writeInbound(Unpooled.copiedBuffer(requestStr, CharsetUtil.US_ASCII)));

        String chunkLength = Integer.toHexString(GZ_HELLO_WORLD.length);
        assertTrue(channel.writeInbound(Unpooled.copiedBuffer(chunkLength + "\r\n", CharsetUtil.US_ASCII)));
        assertTrue(channel.writeInbound(Unpooled.copiedBuffer(GZ_HELLO_WORLD)));
        assertTrue(channel.writeInbound(Unpooled.copiedBuffer("\r\n".getBytes(CharsetUtil.US_ASCII))));
        assertTrue(channel.writeInbound(Unpooled.copiedBuffer("0\r\n", CharsetUtil.US_ASCII)));
        assertTrue(channel.writeInbound(Unpooled.copiedBuffer("My-Trailer: 42\r\n\r\n", CharsetUtil.US_ASCII)));

        HttpRequest request = channel.readInbound();
        assertTrue(request.decoderResult().isSuccess());
        assertTrue(request.headers().containsValue(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED, true));
        assertFalse(request.headers().contains(HttpHeaderNames.CONTENT_LENGTH));

        HttpContent chunk1 = channel.readInbound();
        assertTrue(chunk1.decoderResult().isSuccess());
        assertEquals(HELLO_WORLD, chunk1.content().toString(CharsetUtil.US_ASCII));
        chunk1.release();

        LastHttpContent chunk2 = channel.readInbound();
        assertTrue(chunk2.decoderResult().isSuccess());
        assertEquals("42", chunk2.trailingHeaders().get("My-Trailer"));
        chunk2.release();

        assertFalse(channel.finish());
        channel.releaseInbound();
    }

    private static byte[] gzDecompress(byte[] input) {
        ZlibDecoder decoder = ZlibCodecFactory.newZlibDecoder(ZlibWrapper.GZIP);
        EmbeddedChannel channel = new EmbeddedChannel(decoder);
        assertTrue(channel.writeInbound(Unpooled.copiedBuffer(input)));
        assertTrue(channel.finish()); // close the channel to indicate end-of-data

        int outputSize = 0;
        ByteBuf o;
        List<ByteBuf> inbound = new ArrayList<ByteBuf>();
        while ((o = channel.readInbound()) != null) {
            inbound.add(o);
            outputSize += o.readableBytes();
        }

        byte[] output = new byte[outputSize];
        int readCount = 0;
        for (ByteBuf b : inbound) {
            int readableBytes = b.readableBytes();
            b.readBytes(output, readCount, readableBytes);
            b.release();
            readCount += readableBytes;
        }
        assertTrue(channel.inboundMessages().isEmpty() && channel.outboundMessages().isEmpty());
        return output;
    }

    private static byte[] readContent(Queue<Object> req, int contentLength, boolean hasTransferEncoding) {
        byte[] receivedContent = new byte[contentLength];
        int readCount = 0;
        for (Object o : req) {
            if (o instanceof HttpContent) {
                ByteBuf b = ((HttpContent) o).content();
                int readableBytes = b.readableBytes();
                b.readBytes(receivedContent, readCount, readableBytes);
                readCount += readableBytes;
            }
            if (o instanceof HttpMessage) {
                assertEquals(hasTransferEncoding,
                        ((HttpMessage) o).headers().contains(HttpHeaderNames.TRANSFER_ENCODING));
            }
        }
        return receivedContent;
    }

    private static int calculateContentLength(Queue<Object> req, int contentLength) {
        for (Object o : req) {
            if (o instanceof HttpContent) {
                assertTrue(((HttpContent) o).refCnt() > 0);
                ByteBuf b = ((HttpContent) o).content();
                contentLength += b.readableBytes();
            }
        }
        return contentLength;
    }

    private static byte[] gzCompress(byte[] input) {
        ZlibEncoder encoder = ZlibCodecFactory.newZlibEncoder(ZlibWrapper.GZIP);
        EmbeddedChannel channel = new EmbeddedChannel(encoder);
        assertTrue(channel.writeOutbound(Unpooled.wrappedBuffer(input)));
        assertTrue(channel.finish());  // close the channel to indicate end-of-data

        int outputSize = 0;
        ByteBuf o;
        List<ByteBuf> outbound = new ArrayList<ByteBuf>();
        while ((o = channel.readOutbound()) != null) {
            outbound.add(o);
            outputSize += o.readableBytes();
        }

        byte[] output = new byte[outputSize];
        int readCount = 0;
        for (ByteBuf b : outbound) {
            int readableBytes = b.readableBytes();
            b.readBytes(output, readCount, readableBytes);
            b.release();
            readCount += readableBytes;
        }
        assertTrue(channel.inboundMessages().isEmpty() && channel.outboundMessages().isEmpty());
        return output;
    }

    private static void assertHasInboundMessages(EmbeddedChannel channel, boolean hasMessages) {
        Object o;
        if (hasMessages) {
            while (true) {
                o = channel.readInbound();
                assertNotNull(o);
                ReferenceCountUtil.release(o);
                if (o instanceof LastHttpContent) {
                    break;
                }
            }
        } else {
            o = channel.readInbound();
            assertNull(o);
        }
    }

    private static void assertHasOutboundMessages(EmbeddedChannel channel, boolean hasMessages) {
        Object o;
        if (hasMessages) {
            while (true) {
                o = channel.readOutbound();
                assertNotNull(o);
                ReferenceCountUtil.release(o);
                if (o instanceof LastHttpContent) {
                    break;
                }
            }
        } else {
            o = channel.readOutbound();
            assertNull(o);
        }
    }

    static boolean isNotSupported() {
        return PlatformDependent.isOsx() && "aarch_64".equals(PlatformDependent.normalizedArch());
    }
}
