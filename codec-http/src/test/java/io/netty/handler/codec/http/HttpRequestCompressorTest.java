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
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.compression.SnappyOptions;
import io.netty.handler.codec.compression.StandardCompressionOptions;
import io.netty.handler.codec.compression.ZstdOptions;
import java.util.stream.Stream;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class HttpRequestCompressorTest {
    static String TEST_CONTENT = "Hello, World";

    /**
     * this test covers the case where the request is an uncompressed
     * {@code FullHttpRequest} and it is sent unchanged because the size of the
     * content is below the threshold.
     */
    @Test
    void testSkipWithContentBelowThreshold() {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestCompressor(
                HttpRequestCompressor.DEFAULT_ENCODING,
                2 * TEST_CONTENT.length()));
        DefaultFullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST,
                "ignored", Unpooled.copiedBuffer(TEST_CONTENT, UTF_8));
        channel.writeOutbound(req);
        Object res = channel.readOutbound();
        assertThat(res).asInstanceOf(InstanceOfAssertFactories.type(FullHttpRequest.class)).satisfies(actual -> {
            assertThat(ByteBufUtil.getBytes(actual.content())).asString().isEqualTo(TEST_CONTENT);
            actual.release();
        });
        res = channel.readOutbound();
        assertThat(res).isNull();

        channel.checkException();
        channel.close();
    }

    @Test
    void testSkipWhenContentEncodingIsAlreadySet() {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestCompressor());
        HttpHeaders normalHeaders = DefaultHttpHeadersFactory.headersFactory()
                .newHeaders()
                .add(HttpHeaderNames.CONTENT_ENCODING, "other");
        DefaultFullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST,
                "ignored", Unpooled.copiedBuffer(TEST_CONTENT, UTF_8),
                normalHeaders, DefaultHttpHeadersFactory.trailersFactory().newEmptyHeaders());
        channel.writeOutbound(req);
        Object res = channel.readOutbound();
        assertThat(res).asInstanceOf(InstanceOfAssertFactories.type(FullHttpRequest.class)).satisfies(actual -> {
            assertThat(ByteBufUtil.getBytes(actual.content())).asString().isEqualTo(TEST_CONTENT);
            actual.release();
        });
        res = channel.readOutbound();
        assertThat(res).isNull();

        channel.checkException();
        channel.close();
    }

    @Test
    void testSkipWhenContentEncodingIsAlreadySetChunked() {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestCompressor());
        HttpHeaders normalHeaders = DefaultHttpHeadersFactory.headersFactory()
                .newHeaders()
                .add(HttpHeaderNames.CONTENT_ENCODING, "other");
        DefaultHttpRequest req = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST,
                "ignored", normalHeaders);
        channel.writeOutbound(req);
        channel.writeAndFlush(new DefaultLastHttpContent(Unpooled.copiedBuffer(TEST_CONTENT, UTF_8)));
        Object res = channel.readOutbound();
        assertThat(res).isInstanceOf(DefaultHttpRequest.class);
        Object last = channel.readOutbound();
        assertThat(last).asInstanceOf(InstanceOfAssertFactories.type(LastHttpContent.class)).satisfies(actual -> {
            assertThat(ByteBufUtil.getBytes(actual.content())).asString().isEqualTo(TEST_CONTENT);
            actual.release();
        });
        res = channel.readOutbound();
        assertThat(res).isNull();

        channel.checkException();
        channel.close();
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

        channel.checkException();
        channel.close();
    }

    @Test
    void testSkipBodylessRequestChunked() {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestCompressor());
        DefaultHttpRequest req = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "ignored");
        channel.writeOutbound(req);
        channel.writeAndFlush(new DefaultLastHttpContent());
        Object res = channel.readOutbound();
        assertThat(res).asInstanceOf(InstanceOfAssertFactories.type(DefaultHttpRequest.class)).satisfies(actual -> {
            assertThat(actual.headers().contains(HttpHeaderNames.TRANSFER_ENCODING)).isFalse();
        });
        Object last = channel.readOutbound();
        assertThat(last).asInstanceOf(InstanceOfAssertFactories.type(LastHttpContent.class)).satisfies(actual -> {
            assertThat(actual.content().readableBytes()).isZero();
            actual.release();
        });

        channel.checkException();
        channel.close();
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
        assertThatCode(() -> new HttpRequestCompressor("snappy",
                HttpRequestCompressor.DEFAULT_THRESHOLD, StandardCompressionOptions.zstd()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("compressionOptions")
                .hasMessageContaining("must be of type %s", SnappyOptions.class.getName())
                .hasMessageContaining("got %s", ZstdOptions.class.getName());
    }

    /**
     * this test covers the case when the request is an uncompressed
     * {@code FullHttpRequest} and is sent as a compressed {@code FullHttpRequest}.
     */
    @ParameterizedTest(name = "test encoding {0}")
    @MethodSource("fullEncodedData")
    void testEncodingOfFullHttpRequest(String preferredEncoding, String expectedContent) {
        final String uncompressedContent = TEST_CONTENT;
        HttpRequestCompressor uut = new HttpRequestCompressor(preferredEncoding);
        EmbeddedChannel channel = new EmbeddedChannel(uut);
        HttpHeaders normalHeaders = DefaultHttpHeadersFactory.headersFactory()
                .newHeaders()
                .add(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN);
        HttpHeaders trailingHeaders = DefaultHttpHeadersFactory.trailersFactory()
                .newHeaders()
                .add("trailing", "trailing-value");
        DefaultFullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "ignored",
                Unpooled.copiedBuffer(uncompressedContent, UTF_8), normalHeaders, trailingHeaders);
        channel.writeOutbound(req);
        FullHttpRequest res = channel.readOutbound();
        assertEncodedRequest(res, preferredEncoding);

        ByteBuf content = res.content();
        assertThat(ByteBufUtil.hexDump(content)).isEqualTo(expectedContent);
        res.release();

        assertThat(res).satisfies(last -> {
            assertThat(last.trailingHeaders()).isNotEmpty();
            assertThat(last.trailingHeaders().get("trailing")).asString().isEqualTo("trailing-value");
        });
        assertThat((Object) channel.readOutbound()).isNull();

        channel.checkException();
        channel.close();
    }

    /**
     * this test covers the case when the chunked request immediately reaches
     * the threshold and is sent as a compressed chunked {@code HttpRequest}.
     */
    @ParameterizedTest(name = "test encoding {0}")
    @MethodSource("chunkEncodedData")
    void testEncodingOfChunkedHttpRequest(String preferredEncoding, String expectedContent) {
        final String uncompressedContent = TEST_CONTENT;
        HttpRequestCompressor uut = new HttpRequestCompressor(preferredEncoding);
        EmbeddedChannel channel = new EmbeddedChannel(uut);
        HttpHeaders normalHeaders = DefaultHttpHeadersFactory.headersFactory()
                .newHeaders()
                .add(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN);
        HttpHeaders trailingHeaders = DefaultHttpHeadersFactory.trailersFactory()
                .newHeaders()
                .add("trailing", "trailing-value");
        DefaultHttpRequest req = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "ignored",
                normalHeaders);
        channel.write(req);
        char[] uncompressedContentChars = uncompressedContent.toCharArray();
        int numUncompressedContentChars = uncompressedContentChars.length;
        for (int i = 0; i < numUncompressedContentChars - 1; i++) {
            channel.write(Unpooled.copiedBuffer(uncompressedContentChars, i, 1, UTF_8));
        }
        channel.writeAndFlush(new DefaultLastHttpContent(
                Unpooled.copiedBuffer(uncompressedContentChars,
                        numUncompressedContentChars - 1,
                        1, UTF_8),
                trailingHeaders));
        HttpRequest res = channel.readOutbound();
        assertEncodedRequest(res, preferredEncoding);

        ByteBuf content = Unpooled.buffer();
        LastHttpContent last = null;
        Object out;
        int chunkCount = 0;
        while ((out = channel.readOutbound()) != null) {
            chunkCount++;
            if (out instanceof ByteBuf) {
                content.writeBytes((ByteBuf) out);
            } else if (out instanceof HttpContent && !(out instanceof LastHttpContent)) {
                content.writeBytes(((HttpContent) out).content());
            } else if (out instanceof LastHttpContent) {
                last = (LastHttpContent) out;
                content.writeBytes(last.content());
                break;
            } else {
                fail("unexpected type " + out.getClass().getName());
            }
        }
        assertThat(chunkCount).isEqualTo(numUncompressedContentChars);
        assertThat(ByteBufUtil.hexDump(content)).isEqualTo(expectedContent);
        content.release();

        assertThat(last).isNotNull();
        assertThat(last.trailingHeaders()).isNotEmpty();
        assertThat(last.trailingHeaders().get("trailing")).asString().isEqualTo("trailing-value");
        assertThat((Object) channel.readOutbound()).isNull();

        channel.checkException();
        channel.close();
    }

    /**
     * this test covers the case when the chunked request never reaches the
     * threshold and is sent as an uncompressed {@code FullHttpRequest}.
     */
    @Test
    void testChunkedHttpRequestBelowThreshold() {
        final String uncompressedContent = TEST_CONTENT;
        HttpRequestCompressor uut = new HttpRequestCompressor(
                HttpRequestCompressor.DEFAULT_ENCODING,
                2 * uncompressedContent.length());
        EmbeddedChannel channel = new EmbeddedChannel(uut);
        HttpHeaders normalHeaders = DefaultHttpHeadersFactory.headersFactory()
                .newHeaders()
                .add(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN);
        HttpHeaders trailingHeaders = DefaultHttpHeadersFactory.trailersFactory()
                .newHeaders()
                .add("trailing", "trailing-value");
        DefaultHttpRequest req = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "ignored",
                normalHeaders);
        channel.write(req);
        char[] uncompressedContentChars = uncompressedContent.toCharArray();
        int numUncompressedContentChars = uncompressedContentChars.length;
        for (int i = 0; i < numUncompressedContentChars - 1; i++) {
            channel.write(Unpooled.copiedBuffer(uncompressedContentChars, i, 1, UTF_8));
        }
        channel.writeAndFlush(new DefaultLastHttpContent(
                Unpooled.copiedBuffer(uncompressedContentChars,
                        numUncompressedContentChars - 1,
                        1, UTF_8),
                trailingHeaders));

        FullHttpRequest res = channel.readOutbound();
        assertUnencodedRequest(res);

        ByteBuf content = res.content();
        assertThat(ByteBufUtil.hexDump(content)).isEqualTo(ByteBufUtil.hexDump(uncompressedContent.getBytes(UTF_8)));
        res.release();

        assertThat(res).satisfies(last -> {
            assertThat(last.trailingHeaders()).isNotEmpty();
            assertThat(last.trailingHeaders().get("trailing")).asString().isEqualTo("trailing-value");
        });
        assertThat((Object) channel.readOutbound()).isNull();

        channel.checkException();
        channel.close();
    }

    /**
     * this test covers the case when the chunked request only reaches the
     * threshold at the end with the {@code LastHttpContent} and is sent as a
     * compressed {@code FullHttpRequest}.
     */
    @ParameterizedTest(name = "test encoding {0}")
    @MethodSource("fullEncodedData")
    void testChunkedRequestToFullEncodedRequest(String preferredEncoding, String expectedContent) {
        final String uncompressedContent = TEST_CONTENT;
        HttpRequestCompressor uut = new HttpRequestCompressor(
                preferredEncoding,
                uncompressedContent.getBytes(UTF_8).length);
        EmbeddedChannel channel = new EmbeddedChannel(uut);
        HttpHeaders normalHeaders = DefaultHttpHeadersFactory.headersFactory()
                .newHeaders()
                .add(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN);
        HttpHeaders trailingHeaders = DefaultHttpHeadersFactory.trailersFactory()
                .newHeaders()
                .add("trailing", "trailing-value");
        DefaultHttpRequest req = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "ignored",
                normalHeaders);
        channel.write(req);
        channel.write(new DefaultHttpContent(Unpooled.EMPTY_BUFFER));
        channel.writeAndFlush(new DefaultLastHttpContent(
                Unpooled.copiedBuffer(uncompressedContent, UTF_8),
                trailingHeaders));

        FullHttpRequest res = channel.readOutbound();
        assertEncodedRequest(res, preferredEncoding);

        ByteBuf content = res.content();
        assertThat(ByteBufUtil.hexDump(content)).isEqualTo(expectedContent);
        res.release();

        assertThat(res).satisfies(last -> {
            assertThat(last.trailingHeaders()).isNotEmpty();
            assertThat(last.trailingHeaders().get("trailing")).asString().isEqualTo("trailing-value");
        });
        assertThat((Object) channel.readOutbound()).isNull();

        channel.checkException();
        channel.close();
    }

    static Stream<Arguments> fullEncodedData() {
        return Stream.of(
                arguments("gzip", ""
                        + "1f8b0800000000000000"
                        + "f248cdc9c9d75108cf2fca4901000000ffff"
                        + "0300c6865b260c000000"),
                arguments("zstd",
                        "28b52ffd200c61000048656c6c6f2c20576f726c64"),
                arguments("deflate", ""
                        + "789c"
                        + "f248cdc9c9d75108cf2fca4901000000ffff"
                        + "03001b340449"),
                arguments("snappy", ""
                        + "ff060000734e61507059"
                        + "011000009d6b5fe548656c6c6f2c20576f726c64"),
                arguments("br", ""
                        + "8b0580"
                        + "48656c6c6f2c20576f726c64"
                        + "03")
        );
    }

    static Stream<Arguments> chunkEncodedData() {
        return Stream.of(
                arguments("gzip", ""
                        + "1f8b0800000000000000"
                        + "f200000000ffff"
                        + "4a05000000ffff"
                        + "ca01000000ffff"
                        + "ca01000000ffff"
                        + "ca07000000ffff"
                        + "d201000000ffff"
                        + "5200000000ffff"
                        + "0a07000000ffff"
                        + "ca07000000ffff"
                        + "2a02000000ffff"
                        + "ca01000000ffff"
                        + "4a01000000ffff"
                        + "0300c6865b260c000000"),
                arguments("zstd", ""
                        + "28b52ffd200109000048"
                        + "28b52ffd200109000065"
                        + "28b52ffd20010900006c"
                        + "28b52ffd20010900006c"
                        + "28b52ffd20010900006f"
                        + "28b52ffd20010900002c"
                        + "28b52ffd200109000020"
                        + "28b52ffd200109000057"
                        + "28b52ffd20010900006f"
                        + "28b52ffd200109000072"
                        + "28b52ffd20010900006c"
                        + "28b52ffd200109000064"),
                arguments("deflate", ""
                        + "789c"
                        + "f200000000ffff"
                        + "4a05000000ffff"
                        + "ca01000000ffff"
                        + "ca01000000ffff"
                        + "ca07000000ffff"
                        + "d201000000ffff"
                        + "5200000000ffff"
                        + "0a07000000ffff"
                        + "ca07000000ffff"
                        + "2a02000000ffff"
                        + "ca01000000ffff"
                        + "4a01000000ffff"
                        + "03001b340449"),
                arguments("snappy", ""
                        + "ff060000734e61507059"
                        + "01050000961ec8ce48"
                        + "010500006df7e04a65"
                        + "01050000c8e849c26c"
                        + "01050000c8e849c26c"
                        + "0105000029c6b19a6f"
                        + "01050000de6941c72c"
                        + "0105000059d0a15d20"
                        + "01050000fd99567d57"
                        + "0105000029c6b19a6f"
                        + "010500009470da9172"
                        + "01050000c8e849c26c"
                        + "010500001ad3dc5064"),
                arguments("br", ""
                        + "0b008048"
                        + "00000865"
                        + "0000086c"
                        + "0000086c"
                        + "0000086f"
                        + "0000082c"
                        + "00000820"
                        + "00000857"
                        + "0000086f"
                        + "00000872"
                        + "0000086c"
                        + "00000864"
                        + "03")
        );
    }

    private static void assertEncodedRequest(FullHttpRequest req, String encoding) {
        assertThat(req.headers()).isNotEmpty();
        assertThat(HttpUtil.isTransferEncodingChunked(req)).isFalse();
        assertThat(req.headers().get(HttpHeaderNames.CONTENT_ENCODING))
                .asString()
                .isEqualTo(encoding);
        assertThat(HttpUtil.getMimeType(req)).isEqualTo(HttpHeaderValues.TEXT_PLAIN.toString());
        assertThat(HttpUtil.isContentLengthSet(req)).isTrue();
        assertThat(HttpUtil.getContentLength(req)).isPositive();
    }

    private static void assertUnencodedRequest(FullHttpRequest req) {
        assertThat(req.headers()).isNotEmpty();
        assertThat(HttpUtil.isTransferEncodingChunked(req)).isFalse();
        assertThat(req.headers().contains(HttpHeaderNames.CONTENT_ENCODING)).isFalse();
        assertThat(HttpUtil.getMimeType(req)).isEqualTo(HttpHeaderValues.TEXT_PLAIN.toString());
        assertThat(HttpUtil.isContentLengthSet(req)).isTrue();
        assertThat(HttpUtil.getContentLength(req)).isPositive();
    }

    private static void assertEncodedRequest(HttpRequest req, String encoding) {
        assertThat(req.headers()).isNotEmpty();
        assertThat(HttpUtil.isTransferEncodingChunked(req)).isTrue();
        assertThat(req.headers().get(HttpHeaderNames.CONTENT_ENCODING))
                .asString()
                .isEqualTo(encoding);
        assertThat(HttpUtil.getMimeType(req)).isEqualTo(HttpHeaderValues.TEXT_PLAIN.toString());
        assertThat(HttpUtil.isContentLengthSet(req)).isFalse();
    }
}
