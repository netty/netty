/*
 * Copyright 2025 The Netty Project
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
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.compression.CompressionOptions;
import io.netty.handler.codec.compression.StandardCompressionOptions;
import io.netty.handler.codec.compression.ZstdOptions;
import java.nio.charset.StandardCharsets;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class HttpRequestCompressorTest {

    @Test
    void testSkipWithThreshold() {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestCompressor("gzip", 1024));
        DefaultFullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST,
                "ignored", Unpooled.copiedBuffer("Hello, World", StandardCharsets.UTF_8));
        channel.writeOutbound(req);
        Object res = channel.readOutbound();
        assertThat(res).asInstanceOf(InstanceOfAssertFactories.type(FullHttpRequest.class)).satisfies(actual -> {
            assertThat(ByteBufUtil.getBytes(actual.content())).asString().isEqualTo("Hello, World");
            actual.release();
        });
        res = channel.readOutbound();
        assertThat(res).isNull();
    }

    @Test
    void testSkipWhenContentEncodingIsAlreadySet() {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestCompressor());
        HttpHeaders normalHeaders = DefaultHttpHeadersFactory.headersFactory()
                .newHeaders()
                .add(HttpHeaderNames.CONTENT_ENCODING, "other");
        DefaultFullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST,
                "ignored", Unpooled.copiedBuffer("Hello, World", StandardCharsets.UTF_8),
                normalHeaders, DefaultHttpHeadersFactory.trailersFactory().newEmptyHeaders());
        channel.writeOutbound(req);
        Object res = channel.readOutbound();
        assertThat(res).asInstanceOf(InstanceOfAssertFactories.type(FullHttpRequest.class)).satisfies(actual -> {
            assertThat(ByteBufUtil.getBytes(actual.content())).asString().isEqualTo("Hello, World");
            actual.release();
        });
        res = channel.readOutbound();
        assertThat(res).isNull();
    }

    @Test
    void testSkipIncompleteRequest() {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestCompressor("gzip", 1024));
        DefaultHttpRequest req = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "ignored");
        channel.writeOutbound(req);
        Object res = channel.readOutbound();
        assertThat(res).isInstanceOf(HttpRequest.class);
        res = channel.readOutbound();
        assertThat(res).isNull();
    }

    @Test
    void testSkipBodylessRequest() {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestCompressor());
        DefaultFullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "ignored");
        channel.writeOutbound(req);
        Object res = channel.readOutbound();
        assertThat(res).asInstanceOf(InstanceOfAssertFactories.type(FullHttpRequest.class)).satisfies(actual -> {
            assertThat(actual.content().readableBytes()).isZero();
            assertThat(actual.headers().contains(HttpHeaderNames.TRANSFER_ENCODING)).isFalse();
            actual.release();
        });
    }

    @Test
    void testUnsupportedEncoding() {
        assertThatCode(() -> new HttpRequestCompressor("foo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Unsupported encoding foo.");
    }

    @Test
    void testInvalidArgs() {
        assertThatCode(() -> new HttpRequestCompressor(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("preferredEncoding")
                .hasMessageContaining("not be empty");
        assertThatCode(() -> new HttpRequestCompressor(HttpRequestCompressor.DEFAULT_ENCODING, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contentSizeThreshold");
    }

    @Test
    void testIllegalState() throws Exception {
        HttpRequestCompressor uut = new HttpRequestCompressor();
        EmbeddedChannel channel = new EmbeddedChannel();
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        given(ctx.channel()).willReturn(channel);
        ChannelPromise promise = mock(ChannelPromise.class);

        assertThatCode(() -> uut.write(ctx, uut, promise))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("handler not added. call handlerAdded(ctx) first");

        uut.handlerAdded(ctx);

        assertThatCode(() -> uut.handlerAdded(ctx))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("handler already added");

        uut.handlerRemoved(ctx);
    }

    @ParameterizedTest(name = "test preferred encoding {0} / actual {1}")
    @CsvSource({
        "gzip, gzip, 1f8b0800000000000000f248cdc9c9d75108cf2fca4901000000ffff",
        "zstd, zstd, 28b52ffd200c61000048656c6c6f2c20576f726c64",
        "deflate, deflate, 789cf248cdc9c9d75108cf2fca4901000000ffff",
        "snappy, snappy, ff060000734e61507059011000009d6b5fe548656c6c6f2c20576f726c64",
        "br, br, 8b058048656c6c6f2c20576f726c64"
    })
    void testEncodings(String preferredEncoding, String actualEncoding, String expectedContent) {
        final String uncompressedContent = "Hello, World";
        HttpRequestCompressor uut;
        // special handling for zstd due to https://github.com/netty/netty/issues/15340
        if ("zstd".equalsIgnoreCase(preferredEncoding)) {
            ZstdOptions defaultOpts = StandardCompressionOptions.zstd();
            ZstdOptions testingOpts = StandardCompressionOptions.zstd(
                    defaultOpts.compressionLevel(),
                    uncompressedContent.getBytes(StandardCharsets.UTF_8).length,
                    defaultOpts.maxEncodeSize()
            );
            uut = new HttpRequestCompressor(preferredEncoding, 0, new CompressionOptions[]{testingOpts});
        } else {
            uut = new HttpRequestCompressor(preferredEncoding);
        }
        EmbeddedChannel channel = new EmbeddedChannel(uut);
        HttpHeaders normalHeaders = DefaultHttpHeadersFactory.headersFactory()
                .newHeaders()
                .add(HttpHeaderNames.CONTENT_TYPE, "string");
        HttpHeaders trailingHeaders = DefaultHttpHeadersFactory.trailersFactory()
                .newHeaders()
                .add("trailing", "trailing-value");
        DefaultFullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "ignored",
                Unpooled.copiedBuffer(uncompressedContent, StandardCharsets.UTF_8), normalHeaders, trailingHeaders);
        channel.writeOutbound(req);
        FullHttpRequest res = channel.readOutbound();
        assertEncodedRequest(res, actualEncoding);

        ByteBuf content = res.content();
        assertThat(ByteBufUtil.hexDump(content)).isEqualTo(expectedContent);
        res.release();

        assertThat(res).satisfies(last -> {
                assertThat(last.trailingHeaders()).isNotEmpty();
                assertThat(last.trailingHeaders().get("trailing")).asString().isEqualTo("trailing-value");
        });
        assertThat((Object) channel.readOutbound()).isNull();
    }

    private static void assertEncodedRequest(HttpRequest req, String encoding) {
        assertThat(req.headers()).isNotEmpty();
        assertThat(req.headers().contains(HttpHeaderNames.TRANSFER_ENCODING)).isFalse();
        assertThat(req.headers().get(HttpHeaderNames.CONTENT_ENCODING))
                .asString()
                .isEqualTo(encoding);
        assertThat(req.headers().contains(HttpHeaderNames.CONTENT_LENGTH)).isTrue();
        byte contentLength = Byte.parseByte(req.headers().get(HttpHeaderNames.CONTENT_LENGTH));
        assertThat(contentLength).isPositive();
    }
}
