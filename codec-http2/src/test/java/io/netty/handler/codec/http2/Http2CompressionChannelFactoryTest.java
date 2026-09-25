/*
 * Copyright 2026 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.http2;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.compression.StandardCompressionOptions;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_ENCODING;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderValues.BR;
import static io.netty.handler.codec.http.HttpHeaderValues.DEFLATE;
import static io.netty.handler.codec.http.HttpHeaderValues.GZIP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link Http2CompressionChannelFactory} and, since the two are tightly coupled,
 * the compression negotiation performed by
 * {@code writeHeaders} of {@link NegotiatingCompressorHttp2ConnectionEncoder}.
 */
public class Http2CompressionChannelFactoryTest {

    @Mock
    private Http2ConnectionEncoder delegate;

    private DefaultHttp2Connection connection;
    private Http2RequestAcceptEncodingPropertyKey acceptEncodingKey;
    private NegotiatingCompressorHttp2ConnectionEncoder encoder;

    @BeforeEach
    public void setUp() {
        initMocks(this);
        connection = new DefaultHttp2Connection(true);
        when(delegate.connection()).thenReturn(connection);
        acceptEncodingKey = new Http2RequestAcceptEncodingPropertyKey(connection);
        encoder = new NegotiatingCompressorHttp2ConnectionEncoder(delegate, acceptEncodingKey,
                StandardCompressionOptions.gzip());
        when(delegate.writeHeaders(any(ChannelHandlerContext.class), anyInt(), any(Http2Headers.class), anyInt(),
                anyBoolean(), any(ChannelPromise.class))).thenReturn(mock(ChannelFuture.class));
    }

    @Test
    public void newCompressionChannelForGzipProducesValidGzipStream() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel();
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        when(ctx.channel()).thenReturn(channel);
        NegotiatedEncoding negotiated = new NegotiatedEncoding(GZIP, StandardCompressionOptions.gzip());

        EmbeddedChannel compressor = Http2CompressionChannelFactory.newCompressionChannel(ctx, negotiated);
        try {
            String payload = "hello world";
            compressor.writeOutbound(Unpooled.copiedBuffer(payload, CharsetUtil.UTF_8));
            assertTrue(compressor.finish());

            ByteBuf compressed = readAllOutbound(compressor);
            try {
                assertTrue(compressed.readableBytes() > 2);
                assertEquals(0x1f, compressed.getUnsignedByte(0) & 0xFF);
                assertEquals(0x8b, compressed.getUnsignedByte(1) & 0xFF);
                assertEquals(payload, gunzip(compressed));
            } finally {
                compressed.release();
            }
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    public void newCompressionChannelForDeflateProducesValidZlibStream() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel();
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        when(ctx.channel()).thenReturn(channel);
        NegotiatedEncoding negotiated = new NegotiatedEncoding(DEFLATE, StandardCompressionOptions.deflate());

        EmbeddedChannel compressor = Http2CompressionChannelFactory.newCompressionChannel(ctx, negotiated);
        try {
            String payload = "hello world";
            compressor.writeOutbound(Unpooled.copiedBuffer(payload, CharsetUtil.UTF_8));
            assertTrue(compressor.finish());

            ByteBuf compressed = readAllOutbound(compressor);
            try {
                assertTrue(compressed.readableBytes() > 2);
                assertEquals(0x78, compressed.getUnsignedByte(0) & 0xFF);
                assertEquals(payload, inflateZlib(compressed));
            } finally {
                compressed.release();
            }
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    public void newCompressionChannelReturnsNullForUnrecognizedContentEncoding() throws Http2Exception {
        EmbeddedChannel channel = new EmbeddedChannel();
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        when(ctx.channel()).thenReturn(channel);
        NegotiatedEncoding negotiated = new NegotiatedEncoding("x-unknown", StandardCompressionOptions.gzip());

        try {
            assertNull(Http2CompressionChannelFactory.newCompressionChannel(ctx, negotiated));
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    public void writeHeadersSkipsCompressionWhenContentEncodingAlreadyPresent() throws Http2Exception {
        Http2Stream stream = connection.remote().createStream(3, false);
        acceptEncodingKey.setAcceptEncoding(stream, "gzip");
        Http2Headers headers = new DefaultHttp2Headers().status("200")
                .set(CONTENT_ENCODING, BR)
                .set(CONTENT_LENGTH, "100");
        ChannelPromise promise = mock(ChannelPromise.class);
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);

        encoder.writeHeaders(ctx, 3, headers, 0, false, promise);

        assertEquals(BR.toString(), headers.get(CONTENT_ENCODING).toString());
        assertEquals("100", headers.get(CONTENT_LENGTH).toString());
        verify(delegate).writeHeaders(any(ChannelHandlerContext.class), eq(3), eq(headers), eq(0), eq(false),
                eq(promise));
    }

    @Test
    public void writeHeadersSkipsCompressionWhenNoAcceptEncodingCaptured() throws Http2Exception {
        connection.remote().createStream(3, false);
        Http2Headers headers = new DefaultHttp2Headers().status("200").set(CONTENT_LENGTH, "100");
        ChannelPromise promise = mock(ChannelPromise.class);
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);

        encoder.writeHeaders(ctx, 3, headers, 0, false, promise);

        assertNull(headers.get(CONTENT_ENCODING));
        assertEquals("100", headers.get(CONTENT_LENGTH).toString());
    }

    @Test
    public void writeHeadersNegotiatesCompressionWhenAcceptEncodingMatches() throws Http2Exception {
        Http2Stream stream = connection.remote().createStream(3, false);
        acceptEncodingKey.setAcceptEncoding(stream, "gzip");
        Http2Headers headers = new DefaultHttp2Headers().status("200").set(CONTENT_LENGTH, "100");
        ChannelPromise promise = mock(ChannelPromise.class);
        EmbeddedChannel underlying = new EmbeddedChannel();
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        when(ctx.channel()).thenReturn(underlying);

        try {
            encoder.writeHeaders(ctx, 3, headers, 0, false, promise);

            assertEquals(GZIP.toString(), headers.get(CONTENT_ENCODING).toString());
            assertNull(headers.get(CONTENT_LENGTH));
        } finally {
            stream.close();
            underlying.finishAndReleaseAll();
        }
    }

    private static ByteBuf readAllOutbound(EmbeddedChannel channel) {
        CompositeByteBuf composite = Unpooled.compositeBuffer();
        for (;;) {
            ByteBuf buf = channel.readOutbound();
            if (buf == null) {
                break;
            }
            composite.addComponent(true, buf);
        }
        return composite;
    }

    private static String gunzip(ByteBuf compressed) throws IOException {
        return inflate(new GZIPInputStream(new ByteBufInputStream(compressed.duplicate())));
    }

    private static String inflateZlib(ByteBuf compressed) throws IOException {
        return inflate(new InflaterInputStream(new ByteBufInputStream(compressed.duplicate())));
    }

    private static String inflate(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[64];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return new String(out.toByteArray(), CharsetUtil.UTF_8);
    }
}
