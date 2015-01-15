/*
 * Copyright 2015 The Netty Project
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
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.compression.ZlibCodecFactory;
import io.netty.handler.codec.compression.ZlibDecoder;
import io.netty.handler.codec.compression.ZlibEncoder;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

public class HttpContentDecoderTest {
    private static final String HELLO_WORLD = "hello, world";
    private static final byte[] GZ_HELLO_WORLD = {
            31, -117, 8, 8, 12, 3, -74, 84, 0, 3, 50, 0, -53, 72, -51, -55, -55,
            -41, 81, 40, -49, 47, -54, 73, 1, 0, 58, 114, -85, -1, 12, 0, 0, 0
    };

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
        assertTrue(channel.writeInbound(Unpooled.wrappedBuffer(GZ_HELLO_WORLD)));
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
        assertTrue(channel.writeInbound(Unpooled.wrappedBuffer(GZ_HELLO_WORLD)));

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
        assertTrue(channel.writeInbound(Unpooled.wrappedBuffer(GZ_HELLO_WORLD)));

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
        assertTrue(channel.writeInbound(Unpooled.wrappedBuffer(GZ_HELLO_WORLD)));

        assertHasInboundMessages(channel, true);
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
        Long value = r.headers().getLong(HttpHeaderNames.CONTENT_LENGTH);
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
        Long value = r.headers().getLong(HttpHeaderNames.CONTENT_LENGTH);
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
        Long value = r.headers().getLong(HttpHeaderNames.CONTENT_LENGTH);
        assertTrue(value == null || value.longValue() == HELLO_WORLD.length());

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
        Long value = r.headers().getLong(HttpHeaderNames.CONTENT_LENGTH);
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
        for (Object o : req) {
            if (o instanceof HttpContent) {
                assertTrue(((HttpContent) o).refCnt() > 0);
                ByteBuf b = ((HttpContent) o).content();
                contentLength += b.readableBytes();
            }
        }

        int readCount = 0;
        byte[] receivedContent = new byte[contentLength];
        for (Object o : req) {
            if (o instanceof HttpContent) {
                ByteBuf b = ((HttpContent) o).content();
                int readableBytes = b.readableBytes();
                b.readBytes(receivedContent, readCount, readableBytes);
                readCount += readableBytes;
            }
        }

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
        for (Object o : resp) {
            if (o instanceof HttpContent) {
                assertTrue(((HttpContent) o).refCnt() > 0);
                ByteBuf b = ((HttpContent) o).content();
                contentLength += b.readableBytes();
            }
        }

        int readCount = 0;
        byte[] receivedContent = new byte[contentLength];
        for (Object o : resp) {
            if (o instanceof HttpContent) {
                ByteBuf b = ((HttpContent) o).content();
                int readableBytes = b.readableBytes();
                b.readBytes(receivedContent, readCount, readableBytes);
                readCount += readableBytes;
            }
        }

        assertEquals(HELLO_WORLD, new String(receivedContent, CharsetUtil.US_ASCII));

        assertHasInboundMessages(channel, true);
        assertHasOutboundMessages(channel, false);
        assertFalse(channel.finish());
    }

    private byte[] gzDecompress(byte[] input) {
        ZlibDecoder decoder = ZlibCodecFactory.newZlibDecoder(ZlibWrapper.GZIP);
        EmbeddedChannel channel = new EmbeddedChannel(decoder);
        assertTrue(channel.writeInbound(Unpooled.wrappedBuffer(input)));
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

    private byte[] gzCompress(byte[] input) {
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

    private void assertHasInboundMessages(EmbeddedChannel channel, boolean hasMessages) {
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

    private void assertHasOutboundMessages(EmbeddedChannel channel, boolean hasMessages) {
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
}
