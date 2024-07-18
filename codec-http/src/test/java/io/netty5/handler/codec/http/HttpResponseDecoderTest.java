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
package io.netty5.handler.codec.http;

import io.netty5.buffer.Buffer;
import io.netty5.buffer.BufferAllocator;
import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.handler.codec.DateFormatter;
import io.netty5.handler.codec.PrematureChannelClosureException;
import io.netty5.handler.codec.http.headers.HttpHeaders;
import io.netty5.handler.codec.http.headers.HttpSetCookie;
import io.netty5.handler.codec.http.headers.HttpSetCookie.SameSite;
import io.netty5.util.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HttpResponseDecoderTest {
    HttpResponseDecoder decoder;
    EmbeddedChannel channel;
    BufferAllocator allocator;

    @BeforeEach
    void setUp() {
        setUpDecoder(new HttpResponseDecoder());
    }

    private void setUpNoValidation() {
        setUpDecoder(new HttpResponseDecoder(new HttpDecoderConfig().setValidateHeaders(false)));
    }

    private void setUpDecoder(HttpResponseDecoder decoder) {
        this.decoder = decoder;
        channel = new EmbeddedChannel(decoder);
        allocator = channel.bufferAllocator();
    }

    /**
     * The size of headers should be calculated correctly even if a single header is split into multiple fragments.
     * @see <a href="https://github.com/netty/netty/issues/3445">#3445</a>
     */
    @Test
    public void testMaxHeaderSize1() {
        final int maxHeaderSize = 8192;

        setUpDecoder(new HttpResponseDecoder(4096, maxHeaderSize));
        final char[] bytes = new char[maxHeaderSize / 2 - 4];
        Arrays.fill(bytes, 'a');

        channel.writeInbound(allocator.copyOf("HTTP/1.1 200 OK\r\n", US_ASCII));

        // Write two 4096-byte headers (= 8192 bytes)
        channel.writeInbound(allocator.copyOf("A:", US_ASCII));
        channel.writeInbound(copiedBuffer(allocator, bytes, US_ASCII));
        channel.writeInbound(allocator.copyOf("\r\n", US_ASCII));
        assertNull(channel.readInbound());
        channel.writeInbound(allocator.copyOf("B:", US_ASCII));
        channel.writeInbound(copiedBuffer(allocator, bytes, US_ASCII));
        channel.writeInbound(allocator.copyOf("\r\n", US_ASCII));
        channel.writeInbound(allocator.copyOf("\r\n", US_ASCII));

        HttpResponse res = channel.readInbound();
        assertNull(res.decoderResult().cause());
        assertTrue(res.decoderResult().isSuccess());

        assertNull(channel.readInbound());
        assertTrue(channel.finish());
        Object msg = channel.readInbound();
        assertThat(msg).isInstanceOf(LastHttpContent.class);
        ((LastHttpContent<?>) msg).close();
    }

    /**
     * Complementary test case of {@link #testMaxHeaderSize1()} When it actually exceeds the maximum, it should fail.
     */
    @Test
    public void testMaxHeaderSize2() {
        final int maxHeaderSize = 8192;

        setUpDecoder(new HttpResponseDecoder(4096, maxHeaderSize));
        final char[] bytes = new char[maxHeaderSize / 2 - 2];
        Arrays.fill(bytes, 'a');

        channel.writeInbound(allocator.copyOf("HTTP/1.1 200 OK\r\n", US_ASCII));

        // Write a 4096-byte header and a 4097-byte header to test an off-by-one case (= 8193 bytes)
        channel.writeInbound(allocator.copyOf("A:", US_ASCII));
        channel.writeInbound(copiedBuffer(allocator, bytes, US_ASCII));
        channel.writeInbound(allocator.copyOf("\r\n", US_ASCII));
        assertNull(channel.readInbound());
        channel.writeInbound(allocator.copyOf("B: ", US_ASCII)); // Note an extra space.
        channel.writeInbound(copiedBuffer(allocator, bytes, US_ASCII));
        channel.writeInbound(allocator.copyOf("\r\n", US_ASCII));
        channel.writeInbound(allocator.copyOf("\r\n", US_ASCII));

        HttpResponse res = channel.readInbound();
        assertTrue(res.decoderResult().cause() instanceof TooLongHttpHeaderException);

        assertFalse(channel.finish());
        assertNull(channel.readInbound());
    }

    @Test
    public void testResponseChunked() {
        channel.writeInbound(
                allocator.copyOf("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n", US_ASCII));

        HttpResponse res = channel.readInbound();
        assertThat(res.protocolVersion()).isSameAs(HttpVersion.HTTP_1_1);
        assertThat(res.status()).isEqualTo(HttpResponseStatus.OK);

        byte[] data = new byte[64];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }

        for (int i = 0; i < 10; i++) {
            assertFalse(channel.writeInbound(
                    allocator.copyOf(Integer.toHexString(data.length) + "\r\n", US_ASCII)));
            assertTrue(channel.writeInbound(allocator.copyOf(data)));
            try (HttpContent<?> content = channel.readInbound()) {
                final Buffer payload = content.payload();
                assertEquals(data.length, payload.readableBytes());

                byte[] decodedData = new byte[data.length];
                payload.copyInto(payload.readerOffset(), decodedData, 0, payload.readableBytes());
                assertArrayEquals(data, decodedData);
            }

            assertFalse(channel.writeInbound(allocator.copyOf("\r\n", US_ASCII)));
        }

        // Write the last chunk.
        channel.writeInbound(allocator.copyOf("0\r\n\r\n", US_ASCII));

        // Ensure the last chunk was decoded.
        try (LastHttpContent<?> content = channel.readInbound()) {
            assertThat(content.payload().readableBytes()).isZero();
        }

        channel.finish();
        assertNull(channel.readInbound());
    }

    @Test
    public void testClosureWithoutContentLength1() {
        channel.writeInbound(allocator.copyOf("HTTP/1.1 200 OK\r\n\r\n", US_ASCII));

        // Read the response headers.
        HttpResponse res = channel.readInbound();
        assertThat(res.protocolVersion()).isSameAs(HttpVersion.HTTP_1_1);
        assertThat(res.status()).isEqualTo(HttpResponseStatus.OK);
        assertThat((Object) channel.readInbound()).isNull();

        // Close the connection without sending anything.
        assertTrue(channel.finish());

        // The decoder should still produce the last content.
        try (LastHttpContent<?> content = channel.readInbound()) {
            assertThat(content.payload().readableBytes()).isZero();
        }

        // But nothing more.
        assertThat((Object) channel.readInbound()).isNull();
    }

    @Test
    public void testClosureWithoutContentLength2() {
        // Write the partial response.
        channel.writeInbound(allocator.copyOf("HTTP/1.1 200 OK\r\n\r\n12345678", US_ASCII));

        // Read the response headers.
        HttpResponse res = channel.readInbound();
        assertThat(res.protocolVersion()).isSameAs(HttpVersion.HTTP_1_1);
        assertThat(res.status()).isEqualTo(HttpResponseStatus.OK);

        // Read the partial content.
        try (HttpContent<?> content = channel.readInbound()) {
            assertThat(content.payload().toString(US_ASCII)).isEqualTo("12345678");
            assertThat(content).isNotInstanceOf(LastHttpContent.class);
        }

        assertThat((Object) channel.readInbound()).isNull();

        // Close the connection.
        assertTrue(channel.finish());

        // The decoder should still produce the last content.
        try (LastHttpContent<?> content = channel.readInbound()) {
            assertThat(content.payload().readableBytes()).isZero();
        }

        // But nothing more.
        assertThat((Object) channel.readInbound()).isNull();
    }

    @Test
    public void testPrematureClosureWithChunkedEncoding1() {
        channel.writeInbound(
                allocator.copyOf("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n", US_ASCII));

        // Read the response headers.
        HttpResponse res = channel.readInbound();
        assertThat(res.protocolVersion()).isSameAs(HttpVersion.HTTP_1_1);
        assertThat(res.status()).isEqualTo(HttpResponseStatus.OK);
        assertThat(res.headers().get(HttpHeaderNames.TRANSFER_ENCODING)).isEqualTo("chunked");
        assertThat((Object) channel.readInbound()).isNull();

        // Close the connection without sending anything.
        channel.finish();
        // The decoder should not generate the last chunk because it's closed prematurely.
        assertThat((Object) channel.readInbound()).isNull();
    }

    @Test
    public void testPrematureClosureWithChunkedEncoding2() {
        // Write the partial response.
        channel.writeInbound(allocator.copyOf("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n8\r\n12345678",
                                              US_ASCII));

        // Read the response headers.
        HttpResponse res = channel.readInbound();
        assertThat(res.protocolVersion()).isSameAs(HttpVersion.HTTP_1_1);
        assertThat(res.status()).isEqualTo(HttpResponseStatus.OK);
        assertThat(res.headers().get(HttpHeaderNames.TRANSFER_ENCODING)).isEqualTo("chunked");

        // Read the partial content.
        try (HttpContent<?> content = channel.readInbound()) {
            assertThat(content.payload().toString(US_ASCII)).isEqualTo("12345678");
            assertThat(content).isNotInstanceOf(LastHttpContent.class);
        }

        assertThat((Object) channel.readInbound()).isNull();

        // Close the connection.
        channel.finish();

        // The decoder should not generate the last chunk because it's closed prematurely.
        assertThat((Object) channel.readInbound()).isNull();
    }

    @Test
    public void testLastResponseWithEmptyHeaderAndEmptyContent() {
        channel.writeInbound(allocator.copyOf("HTTP/1.1 200 OK\r\n\r\n", US_ASCII));

        HttpResponse res = channel.readInbound();
        assertThat(res.protocolVersion()).isSameAs(HttpVersion.HTTP_1_1);
        assertThat(res.status()).isEqualTo(HttpResponseStatus.OK);
        assertThat((Object) channel.readInbound()).isNull();

        assertTrue(channel.finish());

        try (LastHttpContent<?> content = channel.readInbound()) {
            assertThat(content.payload().readableBytes()).isZero();
        }

        assertThat((Object) channel.readInbound()).isNull();
    }

    @Test
    public void testLastResponseWithoutContentLengthHeader() {
        channel.writeInbound(allocator.copyOf("HTTP/1.1 200 OK\r\n\r\n", US_ASCII));

        HttpResponse res = channel.readInbound();
        assertThat(res.protocolVersion()).isSameAs(HttpVersion.HTTP_1_1);
        assertThat(res.status()).isEqualTo(HttpResponseStatus.OK);
        assertThat((Object) channel.readInbound()).isNull();

        channel.writeInbound(allocator.allocate(1024).writeBytes(new byte[1024]));
        try (HttpContent<?> content = channel.readInbound()) {
            assertThat(content.payload().readableBytes()).isEqualTo(1024);
        }

        assertTrue(channel.finish());

        try (LastHttpContent<?> content = channel.readInbound()) {
            assertThat(content.payload().readableBytes()).isZero();
        }

        assertThat((Object) channel.readInbound()).isNull();
    }

    @Test
    public void testLastResponseWithHeaderRemoveTrailingSpaces() {
        channel.writeInbound(allocator.copyOf(
                "HTTP/1.1 200 OK\r\nX-Header: h2=h2v2; Expires=Wed, 09-Jun-2021 10:18:14 GMT       \r\n\r\n",
                                     US_ASCII));

        HttpResponse res = channel.readInbound();
        assertThat(res.protocolVersion()).isSameAs(HttpVersion.HTTP_1_1);
        assertThat(res.status()).isEqualTo(HttpResponseStatus.OK);
        assertThat(res.headers().get("X-Header")).isEqualTo("h2=h2v2; Expires=Wed, 09-Jun-2021 10:18:14 GMT");
        assertThat((Object) channel.readInbound()).isNull();

        channel.writeInbound(allocator.allocate(1024).writeBytes(new byte[1024]));
        try (HttpContent<?> content = channel.readInbound()) {
            assertThat(content.payload().readableBytes()).isEqualTo(1024);
        }

        assertTrue(channel.finish());

        try (LastHttpContent<?> content = channel.readInbound()) {
            assertThat(content.payload().readableBytes()).isZero();
        }

        assertThat((Object) channel.readInbound()).isNull();
    }

    @Test
    public void testResetContentResponseWithTransferEncoding() {
        assertTrue(channel.writeInbound(allocator.copyOf("HTTP/1.1 205 Reset Content\r\n" +
                                                         "Transfer-Encoding: chunked\r\n" +
                                                         "\r\n" +
                                                         "0\r\n" +
                                                         "\r\n", US_ASCII)));

        HttpResponse res = channel.readInbound();
        assertThat(res.protocolVersion()).isSameAs(HttpVersion.HTTP_1_1);
        assertThat(res.status()).isEqualTo(HttpResponseStatus.RESET_CONTENT);

        try (LastHttpContent<?> content = channel.readInbound()) {
            assertThat(content.payload().readableBytes()).isZero();
        }

        assertFalse(channel.finish());
    }

    @Test
    public void testLastResponseWithTrailingHeader() {
        channel.writeInbound(allocator.copyOf("HTTP/1.1 200 OK\r\n" +
                                              "Transfer-Encoding: chunked\r\n" +
                                              "\r\n" +
                                              "0\r\n" +
                                              "Set-Cookie: t1=t1v1\r\n" +
                                              "Set-Cookie: t2=t2v2; Expires=Wed, 09-Jun-2021 10:18:14 GMT\r\n" +
                                              "\r\n", US_ASCII));

        HttpResponse res = channel.readInbound();
        assertThat(res.protocolVersion()).isSameAs(HttpVersion.HTTP_1_1);
        assertThat(res.status()).isEqualTo(HttpResponseStatus.OK);

        try (LastHttpContent<?> lastContent = channel.readInbound()) {
            assertThat(lastContent.payload().readableBytes()).isZero();
            HttpHeaders headers = lastContent.trailingHeaders();
            assertEquals(1, headers.names().size());
            Iterable<CharSequence> values = headers.values("Set-Cookie");
            assertThat(values).containsExactly("t1=t1v1", "t2=t2v2; Expires=Wed, 09-Jun-2021 10:18:14 GMT");
        }

        assertFalse(channel.finish());
        assertThat((Object) channel.readInbound()).isNull();
    }

    @Test
    public void testLastResponseWithTrailingHeaderFragmented() {
        byte[] data = ("HTTP/1.1 200 OK\r\n" +
                "Transfer-Encoding: chunked\r\n" +
                "\r\n" +
                "0\r\n" +
                "Set-Cookie: t1=t1v1\r\n" +
                "Set-Cookie: t2=t2v2; Expires=Wed, 09-Jun-2021 10:18:14 GMT\r\n" +
                "\r\n").getBytes(US_ASCII);

        int last = data.length - 1;
        for (int i = 1; i < last; i++) {
            testLastResponseWithTrailingHeaderFragmented(data, i);
            setUp();
        }
        testLastResponseWithTrailingHeaderFragmented(data, last);
    }

    private void testLastResponseWithTrailingHeaderFragmented(byte[] content, int fragmentSize) {
        int headerLength = 47;
        // split up the header
        for (int a = 0; a < headerLength;) {
            int amount = fragmentSize;
            if (a + amount > headerLength) {
                amount = headerLength -  a;
            }

            // if header is done it should produce an HttpRequest
            boolean headerDone = a + amount == headerLength;
            assertEquals(headerDone, channel.writeInbound(copiedBuffer(allocator, content, a, amount)));
            a += amount;
        }

        channel.writeInbound(copiedBuffer(allocator, content, headerLength, content.length - headerLength));
        HttpResponse res = channel.readInbound();
        assertThat(res.protocolVersion()).isSameAs(HttpVersion.HTTP_1_1);
        assertThat(res.status()).isEqualTo(HttpResponseStatus.OK);

        try (LastHttpContent<?> lastContent = channel.readInbound()) {
            assertThat(lastContent.payload().readableBytes()).isZero();
            HttpHeaders headers = lastContent.trailingHeaders();
            assertEquals(1, headers.names().size());
            Iterable<CharSequence> values = headers.values("Set-Cookie");
            assertThat(values).containsExactly("t1=t1v1", "t2=t2v2; Expires=Wed, 09-Jun-2021 10:18:14 GMT");
        }

        assertFalse(channel.finish());
        assertThat((Object) channel.readInbound()).isNull();
    }

    @Test
    public void testResponseWithContentLength() {
        channel.writeInbound(allocator.copyOf("HTTP/1.1 200 OK\r\n" +
                                              "Content-Length: 10\r\n" +
                                              "\r\n", US_ASCII));

        byte[] data = new byte[10];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }
        channel.writeInbound(copiedBuffer(allocator, data, 0, data.length / 2));
        channel.writeInbound(copiedBuffer(allocator, data, 5, data.length / 2));

        HttpResponse res = channel.readInbound();
        assertThat(res.protocolVersion()).isSameAs(HttpVersion.HTTP_1_1);
        assertThat(res.status()).isEqualTo(HttpResponseStatus.OK);

        try (HttpContent<?> firstContent = channel.readInbound();
             Buffer buffer = copiedBuffer(allocator, data, 0, 5)) {
            assertThat(firstContent.payload().readableBytes()).isEqualTo(5);
            assertEquals(buffer, firstContent.payload());
        }

        try (LastHttpContent<?> lastContent = channel.readInbound();
             Buffer buffer = allocator.allocate(5).writeBytes(data, 5, 5)) {
            assertEquals(5, lastContent.payload().readableBytes());
            assertEquals(buffer, lastContent.payload());
        }

        assertFalse(channel.finish());
        assertThat((Object) channel.readInbound()).isNull();
    }

    @Test
    public void testResponseWithContentLengthFragmented() {
        byte[] data = ("HTTP/1.1 200 OK\r\n" +
                "Content-Length: 10\r\n" +
                "\r\n").getBytes(US_ASCII);

        int last = data.length - 1;
        for (int i = 1; i < last; i++) {
            testResponseWithContentLengthFragmented(data, i);
            setUp();
        }
        testResponseWithContentLengthFragmented(data, last);
    }

    private void testResponseWithContentLengthFragmented(byte[] header, int fragmentSize) {
        // split up the header
        for (int a = 0; a < header.length;) {
            int amount = fragmentSize;
            if (a + amount > header.length) {
                amount = header.length -  a;
            }

            channel.writeInbound(copiedBuffer(allocator, header, a, amount));
            a += amount;
        }
        byte[] data = new byte[10];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }
        channel.writeInbound(copiedBuffer(allocator, data, 0, data.length / 2));
        channel.writeInbound(copiedBuffer(allocator, data, 5, data.length / 2));

        HttpResponse res = channel.readInbound();
        assertThat(res.protocolVersion()).isSameAs(HttpVersion.HTTP_1_1);
        assertThat(res.status()).isEqualTo(HttpResponseStatus.OK);

        try (HttpContent<?> firstContent = channel.readInbound();
             Buffer buffer = allocator.allocate(5).writeBytes(data, 0, 5)) {
            assertThat(firstContent.payload().readableBytes()).isEqualTo(5);
            assertEquals(buffer, firstContent.payload());
        }

        try (LastHttpContent<?> lastContent = channel.readInbound();
             Buffer buffer = allocator.allocate(5).writeBytes(data, 5, 5)) {
            assertEquals(5, lastContent.payload().readableBytes());
            assertEquals(buffer, lastContent.payload());
        }

        assertFalse(channel.finish());
        assertThat((Object) channel.readInbound()).isNull();
    }

    @Test
    public void testWebSocketResponse() {
        byte[] data = ("HTTP/1.1 101 Switching Protocols\r\n" +
                       "Upgrade: websocket\r\n" +
                       "Connection: Upgrade\r\n" +
                       "Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo= \r\n" +
                       "Sec-WebSocket-Protocol: chat\r\n\r\n")
                .getBytes(US_ASCII);

        assertTrue(channel.writeInbound(allocator.copyOf(data)));

        HttpResponse res = channel.readInbound();
        assertThat(res.protocolVersion()).isSameAs(HttpVersion.HTTP_1_1);
        assertThat(res.status()).isSameAs(HttpResponseStatus.SWITCHING_PROTOCOLS);
        assertEquals(4, res.headers().size());
        assertTrue(res.headers().containsIgnoreCase(HttpHeaderNames.UPGRADE, HttpHeaderValues.WEBSOCKET));
        assertTrue(res.headers().containsIgnoreCase(HttpHeaderNames.CONNECTION, HttpHeaderValues.UPGRADE));
        assertTrue(res.headers().contains(HttpHeaderNames.SEC_WEBSOCKET_ACCEPT, "s3pPLMBiTxaQ9kYGzzhZRbK+xOo="));
        assertTrue(res.headers().contains(HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL, "chat"));
        Resource.dispose(res);

        try (LastHttpContent<?> lastHttpContent = channel.readInbound();
             EmptyLastHttpContent expected = new EmptyLastHttpContent(allocator)) {
            assertEquals(expected, lastHttpContent);
        }

        assertFalse(channel.finish());
    }

    // See https://github.com/netty/netty/issues/2173
    @Test
    public void testWebSocketResponseWithDataFollowing() {
        byte[] data = ("HTTP/1.1 101 Switching Protocols\r\n" +
                       "Upgrade: websocket\r\n" +
                       "Connection: Upgrade\r\n" +
                       "Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo= \r\n" +
                       "Sec-WebSocket-Protocol: chat\r\n\r\n")
                .getBytes(US_ASCII);
        byte[] otherData = { 1, 2, 3, 4 };

        EmbeddedChannel ch = new EmbeddedChannel(new HttpResponseDecoder());
        assertTrue(ch.writeInbound(allocator.copyOf(data)));
        assertTrue(ch.writeInbound(allocator.copyOf(otherData)));

        HttpResponse res = ch.readInbound();
        assertThat(res.protocolVersion()).isSameAs(HttpVersion.HTTP_1_1);
        assertThat(res.status()).isSameAs(HttpResponseStatus.SWITCHING_PROTOCOLS);
        Resource.dispose(res);

        try (HttpContent<?> content = ch.readInbound();
             EmptyLastHttpContent expected = new EmptyLastHttpContent(allocator)) {
            assertEquals(expected, content);
        }

        assertTrue(ch.finish());

        try (Buffer expected = allocator.copyOf(otherData);
             Buffer buffer = ch.readInbound()) {
            assertEquals(expected, buffer);
        }
    }

    @Test
    public void testGarbageHeaders() {
        // A response without headers - from https://github.com/netty/netty/issues/2103
        byte[] data = ("<html>\r\n" +
                "<head><title>400 Bad Request</title></head>\r\n" +
                "<body bgcolor=\"white\">\r\n" +
                "<center><h1>400 Bad Request</h1></center>\r\n" +
                "<hr><center>nginx/1.1.19</center>\r\n" +
                "</body>\r\n" +
                "</html>\r\n").getBytes();

        channel.writeInbound(allocator.copyOf(data));

        // Garbage input should generate the 999 Unknown response.
        HttpResponse res = channel.readInbound();
        assertThat(res.protocolVersion()).isSameAs(HttpVersion.HTTP_1_0);
        assertThat(res.status().code()).isEqualTo(999);
        assertTrue(res.decoderResult().isFailure());
        assertThat(res).isInstanceOf(FullHttpResponse.class);
        ((FullHttpResponse) res).close();
        assertThat((Object) channel.readInbound()).isNull();

        // More garbage should not generate anything (i.e. the decoder discards anything beyond this point.)
        channel.writeInbound(allocator.copyOf(data));
        assertThat((Object) channel.readInbound()).isNull();

        // Closing the connection should not generate anything since the protocol has been violated.
        channel.finish();
        assertThat((Object) channel.readInbound()).isNull();
    }

    /**
     * Tests if the decoder produces one and only {@link LastHttpContent} when an invalid chunk is received and
     * the connection is closed.
     */
    @Test
    public void testGarbageChunk() {
        String responseWithIllegalChunk =
                "HTTP/1.1 200 OK\r\n" +
                "Transfer-Encoding: chunked\r\n\r\n" +
                "NOT_A_CHUNK_LENGTH\r\n";

        channel.writeInbound(allocator.copyOf(responseWithIllegalChunk, US_ASCII));
        assertThat((Object) channel.readInbound()).isInstanceOf(HttpResponse.class);

        // Ensure that the decoder generates the last chunk with correct decoder result.
        try (LastHttpContent<?> invalidChunk = channel.readInbound()) {
            assertTrue(invalidChunk.decoderResult().isFailure());
        }

        // And no more messages should be produced by the decoder.
        assertThat((Object) channel.readInbound()).isNull();

        // .. even after the connection is closed.
        assertFalse(channel.finish());
    }

    @Test
    public void testConnectionClosedBeforeHeadersReceived() {
        String responseInitialLine =
                "HTTP/1.1 200 OK\r\n";
        assertFalse(channel.writeInbound(allocator.copyOf(responseInitialLine, US_ASCII)));
        assertTrue(channel.finish());
        HttpMessage message = channel.readInbound();
        assertTrue(message.decoderResult().isFailure());
        assertThat(message.decoderResult().cause()).isInstanceOf(PrematureChannelClosureException.class);
        assertNull(channel.readInbound());
    }

    @Test
    public void testTrailerWithEmptyLineInSeparateBuffer() {
        String headers = "HTTP/1.1 200 OK\r\n"
                + "Transfer-Encoding: chunked\r\n"
                + "Trailer: My-Trailer\r\n";
        assertFalse(channel.writeInbound(allocator.copyOf(headers.getBytes(US_ASCII))));
        assertTrue(channel.writeInbound(allocator.copyOf("\r\n".getBytes(US_ASCII))));

        assertTrue(channel.writeInbound(allocator.copyOf("0\r\n", US_ASCII)));
        assertTrue(channel.writeInbound(allocator.copyOf("My-Trailer: 42\r\n", US_ASCII)));
        assertTrue(channel.writeInbound(allocator.copyOf("\r\n", US_ASCII)));

        HttpResponse response = channel.readInbound();
        assertEquals(2, response.headers().size());
        assertEquals("chunked", response.headers().get(HttpHeaderNames.TRANSFER_ENCODING));
        assertEquals("My-Trailer", response.headers().get(HttpHeaderNames.TRAILER));

        try (LastHttpContent<?> lastContent = channel.readInbound()) {
            assertEquals(1, lastContent.trailingHeaders().size());
            assertEquals("42", lastContent.trailingHeaders().get("My-Trailer"));
            assertEquals(0, lastContent.payload().readableBytes());
        }

        assertFalse(channel.finish());
    }

    @Test
    public void testWhitespace() {
        String responseStr = "HTTP/1.1 200 OK\r\n" +
                "Transfer-Encoding : chunked\r\n" +
                "Host: netty.io\n\r\n";

        assertTrue(channel.writeInbound(allocator.copyOf(responseStr, US_ASCII)));
        HttpResponse response = channel.readInbound();
        assertFalse(response.decoderResult().isFailure());
        assertEquals(HttpHeaderValues.CHUNKED.toString(), response.headers().get(HttpHeaderNames.TRANSFER_ENCODING));
        assertEquals("netty.io", response.headers().get(HttpHeaderNames.HOST));
        assertFalse(channel.finish());
    }

    @Test
    public void testHttpMessageDecoderResult() {
        String responseStr = "HTTP/1.1 200 OK\r\n" +
                "Content-Length: 11\r\n" +
                "Connection: close\r\n\r\n" +
                "Lorem ipsum";
        assertTrue(channel.writeInbound(allocator.copyOf(responseStr, US_ASCII)));
        HttpResponse response = channel.readInbound();
        assertTrue(response.decoderResult().isSuccess());
        assertThat(response.decoderResult()).isInstanceOf(HttpMessageDecoderResult.class);
        HttpMessageDecoderResult decoderResult = (HttpMessageDecoderResult) response.decoderResult();
        assertThat(decoderResult.initialLineLength()).isEqualTo(15);
        assertThat(decoderResult.headerSize()).isEqualTo(35);
        assertThat(decoderResult.totalSize()).isEqualTo(50);
        HttpContent<?> c = channel.readInbound();
        c.close();
        assertFalse(channel.finish());
    }

    private static Buffer copiedBuffer(BufferAllocator allocator, byte[] bytes, int srcPos, int length) {
        return allocator.allocate(bytes.length).writeBytes(bytes, srcPos, length);
    }

    private static Buffer copiedBuffer(BufferAllocator allocator, char[] chars, Charset charset) {
        return allocator.copyOf(new String(chars), charset);
    }

    @Test
    public void testHeaderNameStartsWithControlChar1c() {
        testHeaderNameStartsWithControlChar(0x1c);
    }

    @Test
    public void testHeaderNameStartsWithControlChar1d() {
        testHeaderNameStartsWithControlChar(0x1d);
    }

    @Test
    public void testHeaderNameStartsWithControlChar1e() {
        testHeaderNameStartsWithControlChar(0x1e);
    }

    @Test
    public void testHeaderNameStartsWithControlChar1f() {
        testHeaderNameStartsWithControlChar(0x1f);
    }

    @Test
    public void testHeaderNameStartsWithControlChar0c() {
        testHeaderNameStartsWithControlChar(0x0c);
    }

    private void testHeaderNameStartsWithControlChar(int controlChar) {
        Buffer responseBuffer = allocator.allocate(256);
        responseBuffer.writeCharSequence("HTTP/1.1 200 OK\r\n" +
                "Host: netty.io\r\n", US_ASCII);
        responseBuffer.writeByte((byte) controlChar);
        responseBuffer.writeCharSequence("Transfer-Encoding: chunked\r\n\r\n", US_ASCII);
        testInvalidHeaders0(responseBuffer);
    }

    @ParameterizedTest
    @ValueSource(strings = { "HTP/1.1", "HTTP", "HTTP/1x", "Something/1.1", "HTTP/1",
            "HTTP/1.11", "HTTP/11.1", "HTTP/A.1", "HTTP/1.B"})
    public void testInvalidVersion(String version) {
        testInvalidHeaders0(allocator.copyOf(
                version + " 200 OK\n\r\nHost: whatever\r\n\r\n", StandardCharsets.US_ASCII));
    }

    private void testInvalidHeaders0(Buffer responseBuffer) {
        assertTrue(channel.writeInbound(responseBuffer));
        HttpResponse response = channel.readInbound();
        assertThat(response.decoderResult().cause()).isInstanceOf(IllegalArgumentException.class);
        assertTrue(response.decoderResult().isFailure());
        assertFalse(channel.finish());
    }

    @Test
    public void testHeaderNameEndsWithControlChar1c() {
        testHeaderNameEndsWithControlChar(0x1c);
    }

    @Test
    public void testHeaderNameEndsWithControlChar1d() {
        testHeaderNameEndsWithControlChar(0x1d);
    }

    @Test
    public void testHeaderNameEndsWithControlChar1e() {
        testHeaderNameEndsWithControlChar(0x1e);
    }

    @Test
    public void testHeaderNameEndsWithControlChar1f() {
        testHeaderNameEndsWithControlChar(0x1f);
    }

    @Test
    public void testHeaderNameEndsWithControlChar0c() {
        testHeaderNameEndsWithControlChar(0x0c);
    }

    private void testHeaderNameEndsWithControlChar(int controlChar) {
        Buffer responseBuffer = allocator.allocate(256);
        responseBuffer.writeCharSequence("HTTP/1.1 200 OK\r\n" +
                "Host: netty.io\r\n", US_ASCII);
        responseBuffer.writeCharSequence("Transfer-Encoding", US_ASCII);
        responseBuffer.writeByte((byte) controlChar);
        responseBuffer.writeCharSequence(": chunked\r\n\r\n", US_ASCII);
        testInvalidHeaders0(responseBuffer);
    }

    @Test
    public void setCookieHeaderDecodingSingleCookieV0Lenient() {
        setUpNoValidation();
        String cookieString = "Set-Cookie: myCookie=myValue;expires="
                              + DateFormatter.format(new Date(System.currentTimeMillis() + 50000))
                              + ";path=/apathsomewhere;domain=.adomainsomewhere;secure;SameSite=None;Partitioned";
        HttpSetCookie cookie = parseRequestWithCookies(cookieString).headers().getSetCookie("myCookie");
        assertNotNull(cookie);
        assertEquals("myValue", cookie.value());
        assertEquals(".adomainsomewhere", cookie.domain());
        assertNull(cookie.maxAge());
        assertEquals("/apathsomewhere", cookie.path());
        assertTrue(cookie.isSecure());

        assertEquals(SameSite.None, cookie.sameSite());
        assertTrue(cookie.isPartitioned());
    }

    @Test
    public void setCookieHeaderDecodingSingleCookieV0Validating() {
        String cookieString = "Set-Cookie: myCookie=myValue; expires="
                              + DateFormatter.format(new Date(System.currentTimeMillis() + 50000))
                              + "; path=/apathsomewhere; domain=.adomainsomewhere; secure; SameSite=None; Partitioned";
        HttpSetCookie cookie = parseRequestWithCookies(cookieString).headers().getSetCookie("myCookie");
        assertNotNull(cookie);
        assertEquals("myValue", cookie.value());
        assertEquals(".adomainsomewhere", cookie.domain());
        assertNull(cookie.maxAge());
        assertEquals("/apathsomewhere", cookie.path());
        assertTrue(cookie.isSecure());
        assertEquals(SameSite.None, cookie.sameSite());
        assertTrue(cookie.isPartitioned());
    }

    @Test
    public void setCookieHeaderDecodingSingleCookieV0ExtraParamsIgnored() {
        setUpNoValidation();
        String cookieString = "Set-Cookie: myCookie=myValue;max-age=50;path=/apathsomewhere;" +
                              "domain=.adomainsomewhere;secure;comment=this is a comment;version=0;" +
                              "commentURL=http://aurl.com;port=\"80,8080\";discard;";
        HttpSetCookie cookie = parseRequestWithCookies(cookieString).headers().getSetCookie("myCookie");
        assertNotNull(cookie);
        assertEquals("myValue", cookie.value());
        assertEquals(".adomainsomewhere", cookie.domain());
        assertEquals(50, cookie.maxAge());
        assertEquals("/apathsomewhere", cookie.path());
        assertTrue(cookie.isSecure());
    }

    @Test
    public void setCookieHeaderDecodingSingleCookieV1() {
        setUpNoValidation();
        String cookieString = "Set-Cookie: myCookie=myValue;max-age=50;path=/apathsomewhere;domain=.adomainsomewhere;" +
                              "secure;comment=this is a comment;version=1;";
        HttpSetCookie cookie = parseRequestWithCookies(cookieString).headers().getSetCookie("myCookie");
        assertEquals("myValue", cookie.value());
        assertNotNull(cookie);
        assertEquals(".adomainsomewhere", cookie.domain());
        assertEquals(50, cookie.maxAge());
        assertEquals("/apathsomewhere", cookie.path());
        assertTrue(cookie.isSecure());
    }

    @Test
    public void setCookieHeaderDecodingSingleCookieV1ExtraParamsIgnored() {
        setUpNoValidation();
        String cookieString = "Set-Cookie: myCookie=myValue;max-age=50;path=/apathsomewhere;" +
                              "domain=.adomainsomewhere;secure;comment=this is a comment;version=1;" +
                              "commentURL=http://aurl.com;port='80,8080';discard;";
        HttpSetCookie cookie = parseRequestWithCookies(cookieString).headers().getSetCookie("myCookie");
        assertNotNull(cookie);
        assertEquals("myValue", cookie.value());
        assertEquals(".adomainsomewhere", cookie.domain());
        assertEquals(50, cookie.maxAge());
        assertEquals("/apathsomewhere", cookie.path());
        assertTrue(cookie.isSecure());
    }

    @Test
    public void setCookieHeaderDecodingSingleCookieV2() {
        setUpNoValidation();
        String cookieString = "Set-Cookie: myCookie=myValue;max-age=50;path=/apathsomewhere;"
                              + "domain=.adomainsomewhere;secure;comment=this is a comment;version=2;"
                              + "commentURL=http://aurl.com;port=\"80,8080\";discard;";
        HttpSetCookie cookie = parseRequestWithCookies(cookieString).headers().getSetCookie("myCookie");
        assertNotNull(cookie);
        assertEquals("myValue", cookie.value());
        assertEquals(".adomainsomewhere", cookie.domain());
        assertEquals(50, cookie.maxAge());
        assertEquals("/apathsomewhere", cookie.path());
        assertTrue(cookie.isSecure());
    }

    @Test
    public void setCookieHeaderDecodingComplexCookie() {
        setUpNoValidation();
        String cookieString = "Set-Cookie: myCookie=myValue;max-age=50;path=/apathsomewhere;"
                              + "domain=.adomainsomewhere;secure;comment=this is a comment;version=2;"
                              + "commentURL=\"http://aurl.com\";port='80,8080';discard;";

        HttpSetCookie cookie = parseRequestWithCookies(cookieString).headers().getSetCookie("myCookie");
        assertNotNull(cookie);
        assertEquals("myValue", cookie.value());
        assertEquals(".adomainsomewhere", cookie.domain());
        assertEquals(50, cookie.maxAge());
        assertEquals("/apathsomewhere", cookie.path());
        assertTrue(cookie.isSecure());
    }

    @Test
    public void setCookieHeaderDecodingQuotedCookie() {
        String cookieString = "Set-Cookie: myCookie=\"myValue\"; max-age=50; path=/apathsomewhere; "
                              + "domain=.adomainsomewhere";

        HttpSetCookie cookie = parseRequestWithCookies(cookieString).headers().getSetCookie("myCookie");
        assertNotNull(cookie);
        assertEquals("myValue", cookie.value());
        assertEquals(".adomainsomewhere", cookie.domain());
        assertEquals(50, cookie.maxAge());
        assertEquals("/apathsomewhere", cookie.path());
    }

    @Test
    public void setCookieHeaderDecodingQuotedCookieLenient() {
        setUpNoValidation();
        String cookieString = "Set-Cookie: myCookie=\"myValue\";max-age=50;path=/apathsomewhere;"
                              + "domain=.adomainsomewhere";

        HttpSetCookie cookie = parseRequestWithCookies(cookieString).headers().getSetCookie("myCookie");
        assertNotNull(cookie);
        assertEquals("myValue", cookie.value());
        assertEquals(".adomainsomewhere", cookie.domain());
        assertEquals(50, cookie.maxAge());
        assertEquals("/apathsomewhere", cookie.path());
    }

    @Test
    public void setCookieHeaderDecodingQuotedEmptyCookie() {
        String cookieString = "Set-Cookie: myCookie=\"\"; max-age=50; path=/apathsomewhere; "
                              + "domain=.adomainsomewhere";

        HttpSetCookie cookie = parseRequestWithCookies(cookieString).headers().getSetCookie("myCookie");
        assertNotNull(cookie);
        assertEquals("", cookie.value());
        assertEquals(".adomainsomewhere", cookie.domain());
        assertEquals(50, cookie.maxAge());
        assertEquals("/apathsomewhere", cookie.path());
    }

    @Test
    public void setCookieHeaderDecodingQuotedEmptyCookieLenient() {
        setUpNoValidation();
        String cookieString = "Set-Cookie: myCookie=\"\";max-age=50;path=/apathsomewhere;"
                              + "domain=.adomainsomewhere";

        HttpSetCookie cookie = parseRequestWithCookies(cookieString).headers().getSetCookie("myCookie");
        assertNotNull(cookie);
        assertEquals("", cookie.value());
        assertEquals(".adomainsomewhere", cookie.domain());
        assertEquals(50, cookie.maxAge());
        assertEquals("/apathsomewhere", cookie.path());
    }

    @Test
    public void setCookieHeaderDecodingQuotedEmptyCookieLenientWithSpace() {
        setUpNoValidation();
        String cookieString = "Set-Cookie: myCookie=\"\"; max-age=50;path=/apathsomewhere;"
                              + "domain=.adomainsomewhere";

        HttpSetCookie cookie = parseRequestWithCookies(cookieString).headers().getSetCookie("myCookie");
        assertNotNull(cookie);
        assertEquals("", cookie.value());
        assertEquals(".adomainsomewhere", cookie.domain());
        assertEquals(50, cookie.maxAge());
        assertEquals("/apathsomewhere", cookie.path());
    }

    @Test
    public void setCookieHeaderDecodingGoogleAnalyticsCookie() {
        String cookieString = "Set-Cookie: ARPT=LWUKQPSWRTUN04CKKJI; "
                              + "kw-2E343B92-B097-442c-BFA5-BE371E0325A2=unfinished furniture; "
                              + "__utma=48461872.1094088325.1258140131.1258140131.1258140131.1; "
                              + "__utmb=48461872.13.10.1258140131; __utmc=48461872; "
                              + "__utmz=48461872.1258140131.1.1.utmcsr=overstock.com|utmccn=(referral)|"
                              + "utmcmd=referral|utmcct=/Home-Garden/Furniture/Clearance,/clearance,/32/dept.html";
        HttpSetCookie cookie = parseRequestWithCookies(cookieString).headers().getSetCookie("ARPT");

        assertEquals("ARPT", cookie.name());
        assertEquals("LWUKQPSWRTUN04CKKJI", cookie.value());
    }

    @Test
    public void setCookieHeaderDecodingLongDates() {
        Calendar cookieDate = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cookieDate.set(9999, Calendar.DECEMBER, 31, 23, 59, 59);
        long expectedMaxAge = (cookieDate.getTimeInMillis() - System
                .currentTimeMillis()) / 1000;

        String cookieString = "Set-Cookie: Format=EU; expires=Fri, 31-Dec-9999 23:59:59 GMT; path=/";

        HttpSetCookie cookie = parseRequestWithCookies(cookieString).headers().getSetCookie("Format");

        assertTrue(Math.abs(expectedMaxAge - cookie.expiresAsMaxAge()) < 2);
    }

    @Test
    public void setCookieHeaderDecodingValueWithCommaFails() {
        String cookieString = "Set-Cookie: UserCookie=timeZoneName=" +
                              "(GMT+04:00) Moscow, St. Petersburg, Volgograd&promocode=&region=BE;" +
                              " expires=Sat, 01-Dec-2012 10:53:31 GMT; path=/";

        HttpHeaders headers = parseRequestWithCookies(cookieString).headers();
        assertThrows(IllegalArgumentException.class, () -> headers.getSetCookie("UserCookie"));
    }

    @Test
    public void setCookieHeaderDecodingWeirdNames1() {
        String cookieString = "Set-Cookie: path=; expires=Mon, 01-Jan-1990 00:00:00 GMT; path=/; " +
                              "domain=.www.google.com";
        HttpSetCookie cookie = parseRequestWithCookies(cookieString).headers().getSetCookie("path");
        assertEquals("path", cookie.name());
        assertEquals("", cookie.value());
        assertEquals("/", cookie.path());
    }

    @Test
    public void setCookieHeaderDecodingWeirdNames2() {
        String cookieString = "Set-Cookie: HTTPOnly=";
        HttpSetCookie cookie = parseRequestWithCookies(cookieString).headers().getSetCookie("HTTPOnly");
        assertEquals("HTTPOnly", cookie.name());
        assertEquals("", cookie.value());
    }

    @Test
    public void setCookieHeaderDecodingValuesWithCommasAndEqualsFails() {
        String cookieString = "Set-Cookie: A=v=1&lg=en-US,it-IT,it&intl=it&np=1;T=z=E";
        HttpHeaders headers = parseRequestWithCookies(cookieString).headers();
        assertThrows(IllegalArgumentException.class, () -> headers.getSetCookie("A"));
    }

    @Test
    public void setCookieHeaderDecodingInvalidValuesWithCommaAtStart() {
        assertThrows(IllegalArgumentException.class,
                     () -> parseRequestWithCookies("Set-Cookie: ,").headers().getSetCookies().iterator().next());
        channel.releaseInbound();
        assertThrows(IllegalArgumentException.class,
                     () -> parseRequestWithCookies("Set-Cookie: ,a").headers().getSetCookies().iterator().next());
        channel.releaseInbound();
        assertThrows(IllegalArgumentException.class,
                     () -> parseRequestWithCookies("Set-Cookie: ,a=a").headers().getSetCookies().iterator().next());
    }

    @Test
    public void setCookieHeaderDecodingLongValue() {
        String longValue =
                "b___$Q__$ha__<NC=MN(F__%#4__<NC=MN(F__2_d____#=IvZB__2_F____'=KqtH__2-9____" +
                "'=IvZM__3f:____$=HbQW__3g'____%=J^wI__3g-____%=J^wI__3g1____$=HbQW__3g2____" +
                "$=HbQW__3g5____%=J^wI__3g9____$=HbQW__3gT____$=HbQW__3gX____#=J^wI__3gY____" +
                "#=J^wI__3gh____$=HbQW__3gj____$=HbQW__3gr____$=HbQW__3gx____#=J^wI__3h_____" +
                "$=HbQW__3h$____#=J^wI__3h'____$=HbQW__3h_____$=HbQW__3h0____%=J^wI__3h1____" +
                "#=J^wI__3h2____$=HbQW__3h4____$=HbQW__3h7____$=HbQW__3h8____%=J^wI__3h:____" +
                "#=J^wI__3h@____%=J^wI__3hB____$=HbQW__3hC____$=HbQW__3hL____$=HbQW__3hQ____" +
                "$=HbQW__3hS____%=J^wI__3hU____$=HbQW__3h[____$=HbQW__3h^____$=HbQW__3hd____" +
                "%=J^wI__3he____%=J^wI__3hf____%=J^wI__3hg____$=HbQW__3hh____%=J^wI__3hi____" +
                "%=J^wI__3hv____$=HbQW__3i/____#=J^wI__3i2____#=J^wI__3i3____%=J^wI__3i4____" +
                "$=HbQW__3i7____$=HbQW__3i8____$=HbQW__3i9____%=J^wI__3i=____#=J^wI__3i>____" +
                "%=J^wI__3iD____$=HbQW__3iF____#=J^wI__3iH____%=J^wI__3iM____%=J^wI__3iS____" +
                "#=J^wI__3iU____%=J^wI__3iZ____#=J^wI__3i]____%=J^wI__3ig____%=J^wI__3ij____" +
                "%=J^wI__3ik____#=J^wI__3il____$=HbQW__3in____%=J^wI__3ip____$=HbQW__3iq____" +
                "$=HbQW__3it____%=J^wI__3ix____#=J^wI__3j_____$=HbQW__3j%____$=HbQW__3j'____" +
                "%=J^wI__3j(____%=J^wI__9mJ____'=KqtH__=SE__<NC=MN(F__?VS__<NC=MN(F__Zw`____" +
                "%=KqtH__j+C__<NC=MN(F__j+M__<NC=MN(F__j+a__<NC=MN(F__j_.__<NC=MN(F__n>M____" +
                "'=KqtH__s1X____$=MMyc__s1_____#=MN#O__ypn____'=KqtH__ypr____'=KqtH_#%h_____" +
                "%=KqtH_#%o_____'=KqtH_#)H6__<NC=MN(F_#*%'____%=KqtH_#+k(____'=KqtH_#-E_____" +
                "'=KqtH_#1)w____'=KqtH_#1)y____'=KqtH_#1*M____#=KqtH_#1*p____'=KqtH_#14Q__<N" +
                "C=MN(F_#14S__<NC=MN(F_#16I__<NC=MN(F_#16N__<NC=MN(F_#16X__<NC=MN(F_#16k__<N" +
                "C=MN(F_#17@__<NC=MN(F_#17A__<NC=MN(F_#1Cq____'=KqtH_#7)_____#=KqtH_#7)b____" +
                "#=KqtH_#7Ww____'=KqtH_#?cQ____'=KqtH_#His____'=KqtH_#Jrh____'=KqtH_#O@M__<N" +
                "C=MN(F_#O@O__<NC=MN(F_#OC6__<NC=MN(F_#Os.____#=KqtH_#YOW____#=H/Li_#Zat____" +
                "'=KqtH_#ZbI____%=KqtH_#Zbc____'=KqtH_#Zbs____%=KqtH_#Zby____'=KqtH_#Zce____" +
                "'=KqtH_#Zdc____%=KqtH_#Zea____'=KqtH_#ZhI____#=KqtH_#ZiD____'=KqtH_#Zis____" +
                "'=KqtH_#Zj0____#=KqtH_#Zj1____'=KqtH_#Zj[____'=KqtH_#Zj]____'=KqtH_#Zj^____" +
                "'=KqtH_#Zjb____'=KqtH_#Zk_____'=KqtH_#Zk6____#=KqtH_#Zk9____%=KqtH_#Zk<____" +
                "'=KqtH_#Zl>____'=KqtH_#]9R____$=H/Lt_#]I6____#=KqtH_#]Z#____%=KqtH_#^*N____" +
                "#=KqtH_#^:m____#=KqtH_#_*_____%=J^wI_#`-7____#=KqtH_#`T>____'=KqtH_#`T?____" +
                "'=KqtH_#`TA____'=KqtH_#`TB____'=KqtH_#`TG____'=KqtH_#`TP____#=KqtH_#`U_____" +
                "'=KqtH_#`U/____'=KqtH_#`U0____#=KqtH_#`U9____'=KqtH_#aEQ____%=KqtH_#b<)____" +
                "'=KqtH_#c9-____%=KqtH_#dxC____%=KqtH_#dxE____%=KqtH_#ev$____'=KqtH_#fBi____" +
                "#=KqtH_#fBj____'=KqtH_#fG)____'=KqtH_#fG+____'=KqtH_#g<d____'=KqtH_#g<e____" +
                "'=KqtH_#g=J____'=KqtH_#gat____#=KqtH_#s`D____#=J_#p_#sg?____#=J_#p_#t<a____" +
                "#=KqtH_#t<c____#=KqtH_#trY____$=JiYj_#vA$____'=KqtH_#xs_____'=KqtH_$$rO____" +
                "#=KqtH_$$rP____#=KqtH_$(_%____'=KqtH_$)]o____%=KqtH_$_@)____'=KqtH_$_k]____" +
                "'=KqtH_$1]+____%=KqtH_$3IO____%=KqtH_$3J#____'=KqtH_$3J.____'=KqtH_$3J:____" +
                "#=KqtH_$3JH____#=KqtH_$3JI____#=KqtH_$3JK____%=KqtH_$3JL____'=KqtH_$3JS____" +
                "'=KqtH_$8+M____#=KqtH_$99d____%=KqtH_$:Lw____#=LK+x_$:N@____#=KqtG_$:NC____" +
                "#=KqtG_$:hW____'=KqtH_$:i[____'=KqtH_$:ih____'=KqtH_$:it____'=KqtH_$:kO____" +
                "'=KqtH_$>*B____'=KqtH_$>hD____+=J^x0_$?lW____'=KqtH_$?ll____'=KqtH_$?lm____" +
                "%=KqtH_$?mi____'=KqtH_$?mx____'=KqtH_$D7]____#=J_#p_$D@T____#=J_#p_$V<g____" +
                "'=KqtH";

        HttpSetCookie cookie = parseRequestWithCookies(
                "Set-Cookie: bh=\"" + longValue + '"').headers().getSetCookie("bh");
        assertEquals("bh", cookie.name());
        assertEquals(longValue, cookie.value());
    }

    @Test
    public void setCookieHeaderIgnoreEmptyDomain() {
        String cookieString = "Set-Cookie: sessionid=OTY4ZDllNTgtYjU3OC00MWRjLTkzMWMtNGUwNzk4MTY0MTUw; Domain=; Path=/";
        HttpSetCookie cookie = parseRequestWithCookies(cookieString).headers().getSetCookie("sessionid");
        assertThat(cookie.domain()).isEmpty();
    }

    @Test
    public void setCookieHeaderIgnoreEmptyPath() {
        String cookieString = "Set-Cookie: sessionid=OTY4ZDllNTgtYjU3OC00MWRjLTkzMWMtNGUwNzk4MTY0MTUw; Domain=; Path=";
        HttpSetCookie cookie = parseRequestWithCookies(cookieString).headers().getSetCookie("sessionid");
        assertThat(cookie.path()).isEmpty();
    }

    private HttpResponse parseRequestWithCookies(String cookieString) {
        String requestStr = "HTTP/1.1 200 OK\r\n" +
                            "Content-Length: 0\r\n" +
                            cookieString +
                            "\r\n\r\n";
        return parseResponse(requestStr);
    }

    private HttpResponse parseResponse(String requestStr) {
        assertTrue(channel.writeInbound(allocator.copyOf(requestStr, US_ASCII)));
        return channel.readInbound();
    }
}
