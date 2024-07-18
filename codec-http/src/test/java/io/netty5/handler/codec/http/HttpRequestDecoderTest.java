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
import io.netty5.handler.codec.http.headers.HttpCookiePair;
import io.netty5.handler.codec.http.headers.HttpHeaders;
import io.netty5.util.AsciiString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;

import static io.netty5.handler.codec.http.HttpHeaderNames.HOST;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HttpRequestDecoderTest {
    private static final byte[] CONTENT_CRLF_DELIMITERS = createContent("\r\n");
    private static final byte[] CONTENT_LF_DELIMITERS = createContent("\n");
    private static final byte[] CONTENT_MIXED_DELIMITERS = createContent("\r\n", "\n");
    private static final int CONTENT_LENGTH = 8;

    private static byte[] createContent(String... lineDelimiters) {
        String lineDelimiter;
        String lineDelimiter2;
        if (lineDelimiters.length == 2) {
            lineDelimiter = lineDelimiters[0];
            lineDelimiter2 = lineDelimiters[1];
        } else {
            lineDelimiter = lineDelimiters[0];
            lineDelimiter2 = lineDelimiters[0];
        }
        return ("GET /some/path?foo=bar&wibble=eek HTTP/1.1" + "\r\n" +
                "Upgrade: WebSocket" + lineDelimiter2 +
                "Connection: Upgrade" + lineDelimiter +
                "Host: localhost" + lineDelimiter2 +
                "Origin: http://localhost:8080" + lineDelimiter +
                "Sec-WebSocket-Key1: 10  28 8V7 8 48     0" + lineDelimiter2 +
                "Sec-WebSocket-Key2: 8 Xt754O3Q3QW 0   _60" + lineDelimiter +
                "Content-Length: " + CONTENT_LENGTH + lineDelimiter2 +
                "\r\n"  +
                "12345678").getBytes(US_ASCII);
    }

    private HttpRequestDecoder decoder;
    private EmbeddedChannel channel;
    private BufferAllocator allocator;

    @BeforeEach
    void setUp() {
        setUpDecoder(new HttpRequestDecoder());
    }

    void setUpNoValidation() {
        setUpDecoder(new HttpRequestDecoder(new HttpDecoderConfig().setValidateHeaders(false)));
    }

    void setUpDecoder(HttpRequestDecoder decoder) {
        this.decoder = decoder;
        channel = new EmbeddedChannel(decoder);
        allocator = channel.bufferAllocator();
    }

    @Test
    public void testDecodeWholeRequestAtOnceCRLFDelimiters() {
        testDecodeWholeRequestAtOnce(CONTENT_CRLF_DELIMITERS);
    }

    @Test
    public void testDecodeWholeRequestAtOnceLFDelimiters() {
        testDecodeWholeRequestAtOnce(CONTENT_LF_DELIMITERS);
    }

    @Test
    public void testDecodeWholeRequestAtOnceMixedDelimiters() {
        testDecodeWholeRequestAtOnce(CONTENT_MIXED_DELIMITERS);
    }

    private void testDecodeWholeRequestAtOnce(byte[] content) {
        assertTrue(channel.writeInbound(allocator.copyOf(content)));
        HttpRequest req = channel.readInbound();
        assertNotNull(req);
        checkHeaders(req.headers());
        LastHttpContent<?> c = channel.readInbound();
        final Buffer payload = c.payload();
        assertEquals(CONTENT_LENGTH, payload.readableBytes());
        try (Buffer buffer1 = allocator.allocate(CONTENT_LENGTH)
                .writeBytes(content, content.length - CONTENT_LENGTH, CONTENT_LENGTH);
             Buffer buffer2 = payload.copy(payload.readerOffset(), CONTENT_LENGTH)) {
            assertEquals(buffer1, buffer2);
        }
        c.close();

        assertFalse(channel.finish());
        assertNull(channel.readInbound());
    }

    private static void checkHeaders(HttpHeaders headers) {
        assertEquals(7, headers.names().size());
        checkHeader(headers, "Upgrade", "WebSocket");
        checkHeader(headers, "Connection", "Upgrade");
        checkHeader(headers, "Host", "localhost");
        checkHeader(headers, "Origin", "http://localhost:8080");
        checkHeader(headers, "Sec-WebSocket-Key1", "10  28 8V7 8 48     0");
        checkHeader(headers, "Sec-WebSocket-Key2", "8 Xt754O3Q3QW 0   _60");
        checkHeader(headers, "Content-Length", String.valueOf(CONTENT_LENGTH));
    }

    private static void checkHeader(HttpHeaders headers, String name, String value) {
        assertThat(headers.values(name)).containsExactly(value);
    }

    @Test
    public void testDecodeWholeRequestInMultipleStepsCRLFDelimiters() {
        testDecodeWholeRequestInMultipleSteps(CONTENT_CRLF_DELIMITERS);
    }

    @Test
    public void testDecodeWholeRequestInMultipleStepsLFDelimiters() {
        testDecodeWholeRequestInMultipleSteps(CONTENT_LF_DELIMITERS);
    }

    @Test
    public void testDecodeWholeRequestInMultipleStepsMixedDelimiters() {
        testDecodeWholeRequestInMultipleSteps(CONTENT_MIXED_DELIMITERS);
    }

    private void testDecodeWholeRequestInMultipleSteps(byte[] content) {
        int last = content.length - 1;
        for (int i = 1; i < last; i++) {
            testDecodeWholeRequestInMultipleSteps(content, i);
            setUp();
        }
        testDecodeWholeRequestInMultipleSteps(content, last);
    }

    private void testDecodeWholeRequestInMultipleSteps(byte[] content, int fragmentSize) {
        int headerLength = content.length - CONTENT_LENGTH;

        // split up the header
        for (int a = 0; a < headerLength;) {
            int amount = fragmentSize;
            if (a + amount > headerLength) {
                amount = headerLength -  a;
            }

            // if header is done it should produce an HttpRequest
            channel.writeInbound(allocator.allocate(content.length).writeBytes(content, a, amount));
            a += amount;
        }

        for (int i = CONTENT_LENGTH; i > 0; i --) {
            // Should produce HttpContent
            channel.writeInbound(allocator.allocate(content.length)
                    .writeBytes(content, content.length - i, 1));
        }

        HttpRequest req = channel.readInbound();
        assertNotNull(req);
        checkHeaders(req.headers());

        for (int i = CONTENT_LENGTH; i > 1; i --) {
            try (HttpContent<?> c = channel.readInbound()) {
                assertEquals(1, c.payload().readableBytes());
                assertEquals(content[content.length - i], c.payload().readByte());
            }
        }

        try (LastHttpContent<?> c = channel.readInbound()) {
            assertEquals(1, c.payload().readableBytes());
            assertEquals(content[content.length - 1], c.payload().readByte());
        }

        assertFalse(channel.finish());
        assertNull(channel.readInbound());
    }

    @Test
    public void testMultiLineHeader() {
        String crlf = "\r\n";
        String request = "GET /some/path HTTP/1.1" + crlf +
                "Host: localhost" + crlf +
                "MyTestHeader: part1" + crlf +
                "              newLinePart2" + crlf +
                "MyTestHeader2: part21" + crlf +
                "\t            newLinePart22"
                + crlf + crlf;
        HttpRequest req = parseRequest(request);
        assertEquals("part1 newLinePart2", req.headers().get("MyTestHeader"));
        assertEquals("part21 newLinePart22", req.headers().get("MyTestHeader2"));

        LastHttpContent<?> c = channel.readInbound();
        c.close();

        assertFalse(channel.finish());
        assertNull(channel.readInbound());
    }

    @Test
    public void testEmptyHeaderValue() {
        String crlf = "\r\n";
        String request = "GET /some/path HTTP/1.1" + crlf +
                "Host: localhost" + crlf +
                "EmptyHeader:" + crlf + crlf;
        HttpRequest req = parseRequest(request);
        assertEquals("", req.headers().get("EmptyHeader"));
    }

    @Test
    public void test100Continue() {
        String oversized =
                "PUT /file HTTP/1.1\r\n" +
                "Expect: 100-continue\r\n" +
                "Content-Length: 1048576000\r\n\r\n";

        HttpRequest request = parseRequest(oversized);
        assertThat(request).isInstanceOf(HttpRequest.class);

        // At this point, we assume that we sent '413 Entity Too Large' to the peer without closing the connection
        // so that the client can try again.
        decoder.reset();

        byte[] query = "GET /max-file-size HTTP/1.1\r\n\r\n".getBytes(US_ASCII);
        channel.writeInbound(allocator.copyOf(query));
        assertThat((Object) channel.readInbound()).isInstanceOf(HttpRequest.class);
        Object msg = channel.readInbound();
        assertThat(msg).isInstanceOf(LastHttpContent.class);
        ((LastHttpContent<?>) msg).close();

        assertFalse(channel.finish());
    }

    @Test
    public void test100ContinueWithBadClient() {
        String oversized = "PUT /file HTTP/1.1\r\n" +
                "Expect: 100-continue\r\n" +
                "Content-Length: 1048576000\r\n\r\n" +
                "WAY_TOO_LARGE_DATA_BEGINS";

        HttpRequest request = parseRequest(oversized);
        assertThat(request).isInstanceOf(HttpRequest.class);

        HttpContent<?> prematureData = channel.readInbound();
        prematureData.close();

        assertThat((Object) channel.readInbound()).isNull();

        // At this point, we assume that we sent '413 Entity Too Large' to the peer without closing the connection
        // so that the client can try again.
        decoder.reset();

        byte[] query = "GET /max-file-size HTTP/1.1\r\n\r\n".getBytes(US_ASCII);
        channel.writeInbound(allocator.copyOf(query));
        Object msg = channel.readInbound();
        assertThat(msg).isInstanceOf(HttpRequest.class);
        msg = channel.readInbound();
        assertThat(msg).isInstanceOf(LastHttpContent.class);
        ((LastHttpContent<?>) msg).close();

        assertFalse(channel.finish());
    }

    @Test
    public void testMessagesSplitBetweenMultipleBuffers() {
        String crlf = "\r\n";
        String str1 = "GET /some/path HTTP/1.1" + crlf +
                "Host: localhost1" + crlf + crlf +
                "GET /some/other/path HTTP/1.0" + crlf +
                "Hos";
        HttpRequest req = parseRequest(str1);
        assertEquals(HttpVersion.HTTP_1_1, req.protocolVersion());
        assertEquals("/some/path", req.uri());
        assertEquals(1, req.headers().size());
        assertTrue(AsciiString.contentEqualsIgnoreCase("localhost1", req.headers().get(HOST)));
        LastHttpContent<?> cnt = channel.readInbound();
        cnt.close();

        String str2 = "t: localhost2" + crlf +
                "content-length: 0" + crlf + crlf;
        req = parseRequest(str2);
        assertEquals(HttpVersion.HTTP_1_0, req.protocolVersion());
        assertEquals("/some/other/path", req.uri());
        assertEquals(2, req.headers().size());
        assertTrue(AsciiString.contentEqualsIgnoreCase("localhost2", req.headers().get(HOST)));
        assertTrue(AsciiString.contentEqualsIgnoreCase("0", req.headers().get(HttpHeaderNames.CONTENT_LENGTH)));
        cnt = channel.readInbound();
        cnt.close();
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    public void testTooLargeInitialLine() {
        setUpDecoder(new HttpRequestDecoder(10, 1024));
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                "Host: localhost1\r\n\r\n";

        HttpRequest request = parseRequest(requestStr);
        assertTrue(request.decoderResult().isFailure());
        assertTrue(request.decoderResult().cause() instanceof TooLongHttpLineException);
        assertThat(request).isInstanceOf(FullHttpRequest.class);
        ((FullHttpRequest) request).close();
        assertFalse(channel.finish());
    }

    @Test
    public void testTooLargeInitialLineWithWSOnly() {
        testTooLargeInitialLineWithControlCharsOnly("                    ");
    }

    @Test
    public void testTooLargeInitialLineWithCRLFOnly() {
        testTooLargeInitialLineWithControlCharsOnly("\r\n\r\n\r\n\r\n\r\n\r\n\r\n\r\n");
    }

    private void testTooLargeInitialLineWithControlCharsOnly(String controlChars) {
        setUpDecoder(new HttpRequestDecoder(15, 1024));
        String requestStr = controlChars + "GET / HTTP/1.1\r\n" +
                "Host: localhost1\r\n\r\n";

        HttpRequest request = parseRequest(requestStr);
        assertTrue(request.decoderResult().isFailure());
        assertTrue(request.decoderResult().cause() instanceof TooLongHttpLineException);
        assertThat(request).isInstanceOf(FullHttpRequest.class);
        ((FullHttpRequest) request).close();
        assertFalse(channel.finish());
    }

    @Test
    public void testInitialLineWithLeadingControlChars() {
        String crlf = "\r\n";
        String request = crlf + "GET /some/path HTTP/1.1" + crlf +
                "Host: localhost" + crlf + crlf;
        HttpRequest req = parseRequest(request);
        assertEquals(HttpMethod.GET, req.method());
        assertEquals("/some/path", req.uri());
        assertEquals(HttpVersion.HTTP_1_1, req.protocolVersion());
        assertTrue(channel.finishAndReleaseAll());
    }

    @Test
    public void testTooLargeHeaders() {
        setUpDecoder(new HttpRequestDecoder(1024, 10));
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                "Host: localhost1\r\n\r\n";

        HttpRequest request = parseRequest(requestStr);
        assertTrue(request.decoderResult().isFailure());
        assertTrue(request.decoderResult().cause() instanceof TooLongHttpHeaderException);
        assertFalse(channel.finish());
    }

    @Test
    public void testStatusWithoutReasonPhrase() {
        String responseStr = "HTTP/1.1 200 \r\n" +
                "Content-Length: 0\r\n\r\n";
        EmbeddedChannel channel = new EmbeddedChannel(new HttpResponseDecoder());
        assertTrue(channel.writeInbound(allocator.copyOf(responseStr, StandardCharsets.US_ASCII)));
        HttpResponse response = channel.readInbound();
        assertTrue(response.decoderResult().isSuccess());
        assertEquals(HttpResponseStatus.OK, response.status());
        HttpContent c = channel.readInbound();
        c.close();
        assertFalse(channel.finish());
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
        Buffer requestBuffer = allocator.allocate(256);
        requestBuffer.writeCharSequence("GET /some/path HTTP/1.1\r\n" +
                "Host: netty.io\r\n", US_ASCII);
        requestBuffer.writeByte((byte) controlChar);
        requestBuffer.writeCharSequence("Transfer-Encoding: chunked\r\n\r\n", US_ASCII);
        testInvalidHeaders0(requestBuffer);
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
        Buffer requestBuffer = allocator.allocate(256);
        requestBuffer.writeCharSequence("GET /some/path HTTP/1.1\r\n" +
                "Host: netty.io\r\n", US_ASCII);
        requestBuffer.writeCharSequence("Transfer-Encoding", US_ASCII);
        requestBuffer.writeByte((byte) controlChar);
        requestBuffer.writeCharSequence(": chunked\r\n\r\n", US_ASCII);
        testInvalidHeaders0(requestBuffer);
    }

    @Test
    public void testWhitespace() {
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                            "Transfer-Encoding : chunked\r\n" +
                            "Host: netty.io\r\n\r\n";
        testInvalidHeaders0(requestStr);
    }

    @Test
    public void testWhitespaceInTransferEncoding01() {
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                            "Transfer-Encoding : chunked\r\n" +
                            "Content-Length: 1\r\n" +
                            "Host: netty.io\r\n\r\n" +
                            'a';
        testInvalidHeaders0(requestStr);
    }

    @Test
    public void testWhitespaceInTransferEncoding02() {
        String requestStr = "POST / HTTP/1.1" +
                            "Transfer-Encoding : chunked\r\n" +
                            "Host: target.com" +
                            "Content-Length: 65\r\n\r\n" +
                            "0\r\n\r\n" +
                            "GET /maliciousRequest HTTP/1.1\r\n" +
                            "Host: evilServer.com\r\n" +
                            "Foo: x";
        testInvalidHeaders0(requestStr);
    }

    @Test
    public void testHeaderWithNoValueAndMissingColon() {
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                            "Content-Length: 0\r\n" +
                            "Host:\r\n" +
                            "netty.io\r\n\r\n";
        testInvalidHeaders0(requestStr);
    }

    @Test
    public void testMultipleContentLengthHeaders() {
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                            "Content-Length: 1\r\n" +
                            "Content-Length: 0\r\n\r\n" +
                            'b';
        testInvalidHeaders0(requestStr);
    }

    @Test
    public void testMultipleContentLengthHeaders2() {
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                            "Content-Length: 1\r\n" +
                            "Connection: close\r\n" +
                            "Content-Length: 0\r\n\r\n" +
                            'b';
        testInvalidHeaders0(requestStr);
    }

    @Test
    public void testContentLengthHeaderWithCommaValue() {
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                            "Content-Length: 1,1\r\n\r\n" +
                            'b';
        testInvalidHeaders0(requestStr);
    }

    @Test
    public void testMultipleContentLengthHeadersWithFolding() {
        String requestStr = "POST / HTTP/1.1\r\n" +
                            "Host: example.com\r\n" +
                            "Connection: close\r\n" +
                            "Content-Length: 5\r\n" +
                            "Content-Length:\r\n" +
                            "\t6\r\n\r\n" +
                            "123456";
        testInvalidHeaders0(requestStr);
    }

    @Test
    public void testContentLengthAndTransferEncodingHeadersWithVerticalTab() {
        testContentLengthAndTransferEncodingHeadersWithInvalidSeparator((char) 0x0b, false);
        setUp();
        testContentLengthAndTransferEncodingHeadersWithInvalidSeparator((char) 0x0b, true);
    }

    @Test
    public void testContentLengthAndTransferEncodingHeadersWithCR() {
        testContentLengthAndTransferEncodingHeadersWithInvalidSeparator((char) 0x0d, false);
        setUp();
        testContentLengthAndTransferEncodingHeadersWithInvalidSeparator((char) 0x0d, true);
    }

    private void testContentLengthAndTransferEncodingHeadersWithInvalidSeparator(
            char separator, boolean extraLine) {
        String requestStr = "POST / HTTP/1.1\r\n" +
                            "Host: example.com\r\n" +
                            "Connection: close\r\n" +
                            "Content-Length: 9\r\n" +
                            "Transfer-Encoding:" + separator + "chunked\r\n\r\n" +
                            (extraLine? "0\r\n\r\n" : "") +
                            "something\r\n\r\n";
        testInvalidHeaders0(requestStr);
    }

    @ParameterizedTest
    @ValueSource(strings = { "HTP/1.1", "HTTP", "HTTP/1x", "Something/1.1", "HTTP/1",
            "HTTP/1.11", "HTTP/11.1", "HTTP/A.1", "HTTP/1.B"})
    public void testInvalidVersion(String version) {
        testInvalidHeaders0("GET / " + version + "\r\nHost: whatever\r\n\r\n");
    }

    @ParameterizedTest
    // See https://www.unicode.org/charts/nameslist/n_0000.html
    @ValueSource(strings = { "\r", "\u000b", "\u000c" })
    public void testHeaderValueWithInvalidSuffix(String suffix) {
        testInvalidHeaders0("GET / HTTP/1.1\r\nHost: whatever\r\nTest-Key: test-value" + suffix + "\r\n\r\n");
    }

    @Test
    public void testLeadingWhitespaceInFirstHeaderName() {
        testInvalidHeaders0("POST / HTTP/1.1\r\n\tContent-Length: 1\r\n\r\nX");
    }

    @Test
    public void testNulInInitialLine() {
        testInvalidHeaders0("GET / HTTP/1.1\r\u0000\nHost: whatever\r\n\r\n");
    }

    private void testInvalidHeaders0(String request) {
        testInvalidHeaders0(allocator.copyOf(request, US_ASCII));
    }

    private void testInvalidHeaders0(Buffer requestBuffer) {
        assertTrue(channel.writeInbound(requestBuffer));
        HttpRequest request = channel.readInbound();
        assertThat(request.decoderResult().cause()).isInstanceOf(IllegalArgumentException.class);
        assertTrue(request.decoderResult().isFailure());
        if (request instanceof FullHttpRequest) {
            ((FullHttpRequest) request).close();
        }
        assertFalse(channel.finish());
    }

    @Test
    public void testContentLengthHeaderAndChunked() {
        String requestStr = "POST / HTTP/1.1\r\n" +
                            "Host: example.com\r\n" +
                            "Connection: close\r\n" +
                            "Content-Length: 5\r\n" +
                            "Transfer-Encoding: chunked\r\n\r\n" +
                            "0\r\n\r\n";
        HttpRequest request = parseRequest(requestStr);
        assertFalse(request.decoderResult().isFailure());
        assertTrue(request.headers().contains("Transfer-Encoding", "chunked"));
        assertFalse(request.headers().contains("Content-Length"));
        Object msg = channel.readInbound();
        assertThat(msg).isInstanceOf(LastHttpContent.class);
        ((LastHttpContent<?>) msg).close();
        assertFalse(channel.finish());
    }

    @Test
    public void testHttpMessageDecoderResult() {
        String requestStr = "PUT /some/path HTTP/1.1\r\n" +
                            "Content-Length: 11\r\n" +
                            "Connection: close\r\n\r\n" +
                            "Lorem ipsum";
        HttpRequest request = parseRequest(requestStr);
        assertTrue(request.decoderResult().isSuccess());
        assertThat(request.decoderResult()).isInstanceOf(HttpMessageDecoderResult.class);
        HttpMessageDecoderResult decoderResult = (HttpMessageDecoderResult) request.decoderResult();
        assertThat(decoderResult.initialLineLength()).isEqualTo(23);
        assertThat(decoderResult.headerSize()).isEqualTo(35);
        assertThat(decoderResult.totalSize()).isEqualTo(58);
        HttpContent<?> c = channel.readInbound();
        c.close();
        assertFalse(channel.finish());
    }

    @Test
    public void cookieHeaderDecodingSingleCookie() {
        String cookieString = "Cookie: myCookie=myValue";
        HttpHeaders headers = parseRequestWithCookies(cookieString).headers();
        assertThat(headers.getCookies()).hasSize(1);
        HttpCookiePair cookie = headers.getCookies().iterator().next();
        assertNotNull(cookie);
        assertEquals("myValue", cookie.value());
    }

    @Test
    public void cookieHeaderDecodingMultipleCookies() {
        String c1 = "myCookie=myValue;";
        String c2 = "myCookie2=myValue2;";
        String c3 = "myCookie3=myValue3;";

        HttpHeaders headers = parseRequestWithCookies("Cookie: " + c1 + c2 + c3).headers();
        assertThat(headers.getCookies()).hasSize(3);
        Iterator<HttpCookiePair> it = headers.getCookies().iterator();
        HttpCookiePair cookie = it.next();
        assertNotNull(cookie);
        assertEquals("myValue", cookie.value());
        cookie = it.next();
        assertNotNull(cookie);
        assertEquals("myValue2", cookie.value());
        cookie = it.next();
        assertNotNull(cookie);
        assertEquals("myValue3", cookie.value());
    }

    @Test
    public void cookieHeaderDecodingAllMultipleCookies() {
        String c1 = "myCookie=myValue;";
        String c2 = "myCookie=myValue2;";
        String c3 = "myCookie=myValue3;";

        HttpHeaders headers = parseRequestWithCookies("Cookie: " + c1 + c2 + c3).headers();
        assertThat(headers.getCookies()).hasSize(3);
        Iterator<HttpCookiePair> it = headers.getCookies().iterator();
        HttpCookiePair cookie = it.next();
        assertNotNull(cookie);
        assertEquals("myValue", cookie.value());
        cookie = it.next();
        assertNotNull(cookie);
        assertEquals("myValue2", cookie.value());
        cookie = it.next();
        assertNotNull(cookie);
        assertEquals("myValue3", cookie.value());
    }

    @Test
    public void cookieHeaderDecodingGoogleAnalyticsCookie() {
        String source =
            "ARPT=LWUKQPSWRTUN04CKKJI; " +
            "kw-2E343B92-B097-442c-BFA5-BE371E0325A2=unfinished_furniture; " +
            "__utma=48461872.1094088325.1258140131.1258140131.1258140131.1; " +
            "__utmb=48461872.13.10.1258140131; __utmc=48461872; " +
            "__utmz=48461872.1258140131.1.1.utmcsr=overstock.com|utmccn=(referral)|" +
                    "utmcmd=referral|utmcct=/Home-Garden/Furniture/Clearance/clearance/32/dept.html";
        HttpHeaders headers = parseRequestWithCookies("Cookie: " + source).headers();
        Iterator<HttpCookiePair> it = headers.getCookies().iterator();
        HttpCookiePair c;

        c = it.next();
        assertEquals("ARPT", c.name());
        assertEquals("LWUKQPSWRTUN04CKKJI", c.value());

        c = it.next();
        assertEquals("kw-2E343B92-B097-442c-BFA5-BE371E0325A2", c.name());
        assertEquals("unfinished_furniture", c.value());

        c = it.next();
        assertEquals("__utma", c.name());
        assertEquals("48461872.1094088325.1258140131.1258140131.1258140131.1", c.value());

        c = it.next();
        assertEquals("__utmb", c.name());
        assertEquals("48461872.13.10.1258140131", c.value());

        c = it.next();
        assertEquals("__utmc", c.name());
        assertEquals("48461872", c.value());

        c = it.next();
        assertEquals("__utmz", c.name());
        assertEquals("48461872.1258140131.1.1.utmcsr=overstock.com|" +
                "utmccn=(referral)|utmcmd=referral|utmcct=/Home-Garden/Furniture/Clearance/clearance/32/dept.html",
                c.value());

        assertFalse(it.hasNext());
    }

    @Test
    public void cookieHeaderDecodingLongValue() {
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

        HttpHeaders headers = parseRequestWithCookies("Cookie: bh=\"" + longValue + "\";").headers();
        assertThat(headers.getCookies()).hasSize(1);
        HttpCookiePair c = headers.getCookies().iterator().next();
        assertEquals("bh", c.name());
        assertEquals(longValue, c.value());
    }

    /**
     * In Netty 5 and beyond, we no longer support RFC 2965 cookies, so they will parse as if they are RFC 6265 cookies.
     * In RFC 6265, the $ character no longer has any special meaning when used as a cookie name prefix.
     */
    @Test
    public void cookieHeaderDecodingOldRFC2965Cookies() {
        String source = "$Version=\"1\"; " +
                "Part_Number1=\"Riding_Rocket_0023\"; $Path=\"/acme/ammo\"; " +
                "Part_Number2=\"Rocket_Launcher_0001\"; $Path=\"/acme\"";

        HttpHeaders headers = parseRequestWithCookies("Cookie: " + source).headers();
        Iterator<HttpCookiePair> it = headers.getCookies().iterator();
        HttpCookiePair c;

        c = it.next();
        assertEquals("$Version", c.name());
        assertEquals("1", c.value());

        c = it.next();
        assertEquals("Part_Number1", c.name());
        assertEquals("Riding_Rocket_0023", c.value());

        c = it.next();
        assertEquals("$Path", c.name());
        assertEquals("/acme/ammo", c.value());

        c = it.next();
        assertEquals("Part_Number2", c.name());
        assertEquals("Rocket_Launcher_0001", c.value());

        // Duplicate cookie names are strongly discouraged, but we still support them.
        c = it.next();
        assertEquals("$Path", c.name());
        assertEquals("/acme", c.value());

        assertFalse(it.hasNext());
    }

    @Test
    public void cookieHeaderRejectCookieValueWithSemicolon() {
        HttpHeaders headers = parseRequestWithCookies("Cookie: name=\"foo;bar\";").headers();
        // The cookie should be there, but fail to parse.
        assertThrows(IllegalArgumentException.class, () -> headers.getCookies().iterator().next());
    }

    @Test
    public void cookieHeaderCaseSensitiveNames() {
        HttpHeaders headers = parseRequestWithCookies("Cookie: session_id=a; Session_id=b;").headers();
        Iterator<HttpCookiePair> it = headers.getCookies().iterator();
        HttpCookiePair c;

        c = it.next();
        assertEquals("session_id", c.name());
        assertEquals("a", c.value());

        c = it.next();
        assertEquals("Session_id", c.name());
        assertEquals("b", c.value());

        assertFalse(it.hasNext());
    }

    /**
     * <a href="https://datatracker.ietf.org/doc/html/rfc9112#name-field-syntax">RFC 9112</a> define the header field
     * syntax thusly, where the field value is bracketed by optional whitespace:
     * <pre>
     *     field-line   = field-name ":" OWS field-value OWS
     * </pre>
     * Meanwhile, <a href="https://datatracker.ietf.org/doc/html/rfc9110#name-whitespace">RFC 9110</a> says that
     * "optional whitespace" (OWS) is defined as "zero or more linear whitespace octets".
     * And a "linear whitespace octet" is defined in the ABNF as either a space or a tab character.
     */
    @Test
    void headerValuesMayBeBracketedByZeroOrMoreWhitespace() throws Exception {
        String requestStr = "GET / HTTP/1.1\r\n" +
                "Host:example.com\r\n" + // zero whitespace
                "X-0-Header:  x0\r\n" + // two whitespace
                "X-1-Header:\tx1\r\n" + // tab whitespace
                "X-2-Header: \t x2\r\n" + // mixed whitespace
                "X-3-Header:x3\t \r\n" + // whitespace after the value
                "\r\n";
        HttpRequestDecoder decoder = new HttpRequestDecoder();
        EmbeddedChannel channel = new EmbeddedChannel(decoder);

        assertTrue(channel.writeInbound(channel.bufferAllocator().copyOf(requestStr, US_ASCII)));
        HttpRequest request = channel.readInbound();
        assertTrue(request.decoderResult().isSuccess());
        HttpHeaders headers = request.headers();
        assertEquals("example.com", headers.get("Host"));
        assertEquals("x0", headers.get("X-0-Header"));
        assertEquals("x1", headers.get("X-1-Header"));
        assertEquals("x2", headers.get("X-2-Header"));
        assertEquals("x3", headers.get("X-3-Header"));
        try (HttpContent<?> last = channel.readInbound()) {
            assertThat(last).isInstanceOf(LastHttpContent.class);
        }
        assertFalse(channel.finish());
    }

    private HttpRequest parseRequestWithCookies(String cookieString) {
        String requestStr = "GET / HTTP/1.1\r\n" +
                            "Host: example.com\r\n" +
                            cookieString +
                            "\r\n\r\n";
        return parseRequest(requestStr);
    }

    private HttpRequest parseRequest(String requestStr) {
        assertTrue(channel.writeInbound(allocator.copyOf(requestStr, US_ASCII)));
        return channel.readInbound();
    }
}
