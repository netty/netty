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
import io.netty.util.AsciiString;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static io.netty.handler.codec.http.HttpHeaderNames.*;
import static io.netty.handler.codec.http.HttpHeadersTestUtils.of;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
                "Accept: */*" + lineDelimiter +
                "Origin: http://localhost:8080" + lineDelimiter +
                "Sec-WebSocket-Key1: 10  28 8V7 8 48     0" + lineDelimiter2 +
                "Sec-WebSocket-Key2: 8 Xt754O3Q3QW 0   _60" + lineDelimiter +
                "Content-Length: " + CONTENT_LENGTH + lineDelimiter2 +
                "\r\n"  +
                "12345678").getBytes(CharsetUtil.US_ASCII);
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

    @Test
    public void testDecodeWholeRequestAtOnceMixedDelimitersWithIntegerOverflowOnMaxBodySize() {
        testDecodeWholeRequestAtOnce(CONTENT_MIXED_DELIMITERS, Integer.MAX_VALUE);
        testDecodeWholeRequestAtOnce(CONTENT_MIXED_DELIMITERS, Integer.MAX_VALUE - 1);
    }

    private static void testDecodeWholeRequestAtOnce(byte[] content) {
        testDecodeWholeRequestAtOnce(content, HttpRequestDecoder.DEFAULT_MAX_HEADER_SIZE);
    }

    private static void testDecodeWholeRequestAtOnce(byte[] content, int maxHeaderSize) {
        EmbeddedChannel channel =
                new EmbeddedChannel(new HttpRequestDecoder(HttpObjectDecoder.DEFAULT_MAX_INITIAL_LINE_LENGTH,
                                                           maxHeaderSize,
                                                           HttpObjectDecoder.DEFAULT_MAX_CHUNK_SIZE));
        assertTrue(channel.writeInbound(Unpooled.copiedBuffer(content)));
        HttpRequest req = channel.readInbound();
        assertNotNull(req);
        checkHeaders(req.headers());
        LastHttpContent c = channel.readInbound();
        assertEquals(CONTENT_LENGTH, c.content().readableBytes());
        assertEquals(
                Unpooled.wrappedBuffer(content, content.length - CONTENT_LENGTH, CONTENT_LENGTH),
                c.content().readSlice(CONTENT_LENGTH));
        c.release();

        assertFalse(channel.finish());
        assertNull(channel.readInbound());
    }

    private static void checkHeaders(HttpHeaders headers) {
        assertEquals(8, headers.names().size());
        checkHeader(headers, "Upgrade", "WebSocket");
        checkHeader(headers, "Connection", "Upgrade");
        checkHeader(headers, "Host", "localhost");
        checkHeader(headers, "Accept", "*/*");
        checkHeader(headers, "Origin", "http://localhost:8080");
        checkHeader(headers, "Sec-WebSocket-Key1", "10  28 8V7 8 48     0");
        checkHeader(headers, "Sec-WebSocket-Key2", "8 Xt754O3Q3QW 0   _60");
        checkHeader(headers, "Content-Length", String.valueOf(CONTENT_LENGTH));
    }

    private static void checkHeader(HttpHeaders headers, String name, String value) {
        List<String> header1 = headers.getAll(of(name));
        assertEquals(1, header1.size());
        assertEquals(value, header1.get(0));
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

    private static void testDecodeWholeRequestInMultipleSteps(byte[] content) {
        for (int i = 1; i < content.length; i++) {
            testDecodeWholeRequestInMultipleSteps(content, i);
        }
    }

    private static void testDecodeWholeRequestInMultipleSteps(byte[] content, int fragmentSize) {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestDecoder());
        int headerLength = content.length - CONTENT_LENGTH;

        // split up the header
        for (int a = 0; a < headerLength;) {
            int amount = fragmentSize;
            if (a + amount > headerLength) {
                amount = headerLength -  a;
            }

            // if header is done it should produce an HttpRequest
            channel.writeInbound(Unpooled.copiedBuffer(content, a, amount));
            a += amount;
        }

        for (int i = CONTENT_LENGTH; i > 0; i --) {
            // Should produce HttpContent
            channel.writeInbound(Unpooled.copiedBuffer(content, content.length - i, 1));
        }

        HttpRequest req = channel.readInbound();
        assertNotNull(req);
        checkHeaders(req.headers());

        for (int i = CONTENT_LENGTH; i > 1; i --) {
            HttpContent c = channel.readInbound();
            assertEquals(1, c.content().readableBytes());
            assertEquals(content[content.length - i], c.content().readByte());
            c.release();
        }

        LastHttpContent c = channel.readInbound();
        assertEquals(1, c.content().readableBytes());
        assertEquals(content[content.length - 1], c.content().readByte());
        c.release();

        assertFalse(channel.finish());
        assertNull(channel.readInbound());
    }

    @Test
    public void testMultiLineHeader() {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestDecoder());
        String crlf = "\r\n";
        String request =  "GET /some/path HTTP/1.1" + crlf +
                "Host: localhost" + crlf +
                "MyTestHeader: part1" + crlf +
                "              newLinePart2" + crlf +
                "MyTestHeader2: part21" + crlf +
                "\t            newLinePart22"
                + crlf + crlf;
        assertTrue(channel.writeInbound(Unpooled.copiedBuffer(request, CharsetUtil.US_ASCII)));
        HttpRequest req = channel.readInbound();
        assertEquals("part1 newLinePart2", req.headers().get(of("MyTestHeader")));
        assertEquals("part21 newLinePart22", req.headers().get(of("MyTestHeader2")));

        LastHttpContent c = channel.readInbound();
        c.release();

        assertFalse(channel.finish());
        assertNull(channel.readInbound());
    }

    @Test
    public void testEmptyHeaderValue() {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestDecoder());
        String crlf = "\r\n";
        String request =  "GET /some/path HTTP/1.1" + crlf +
                "Host: localhost" + crlf +
                "EmptyHeader:" + crlf + crlf;
        channel.writeInbound(Unpooled.copiedBuffer(request, CharsetUtil.US_ASCII));
        HttpRequest req = channel.readInbound();
        assertEquals("", req.headers().get(of("EmptyHeader")));
    }

    @Test
    public void test100Continue() {
        HttpRequestDecoder decoder = new HttpRequestDecoder();
        EmbeddedChannel channel = new EmbeddedChannel(decoder);
        String oversized =
                "PUT /file HTTP/1.1\r\n" +
                "Expect: 100-continue\r\n" +
                "Content-Length: 1048576000\r\n\r\n";

        channel.writeInbound(Unpooled.copiedBuffer(oversized, CharsetUtil.US_ASCII));
        assertThat(channel.readInbound(), is(instanceOf(HttpRequest.class)));

        // At this point, we assume that we sent '413 Entity Too Large' to the peer without closing the connection
        // so that the client can try again.
        decoder.reset();

        String query = "GET /max-file-size HTTP/1.1\r\n\r\n";
        channel.writeInbound(Unpooled.copiedBuffer(query, CharsetUtil.US_ASCII));
        assertThat(channel.readInbound(), is(instanceOf(HttpRequest.class)));
        assertThat(channel.readInbound(), is(instanceOf(LastHttpContent.class)));

        assertThat(channel.finish(), is(false));
    }

    @Test
    public void test100ContinueWithBadClient() {
        HttpRequestDecoder decoder = new HttpRequestDecoder();
        EmbeddedChannel channel = new EmbeddedChannel(decoder);
        String oversized =
                "PUT /file HTTP/1.1\r\n" +
                "Expect: 100-continue\r\n" +
                "Content-Length: 1048576000\r\n\r\n" +
                "WAY_TOO_LARGE_DATA_BEGINS";

        channel.writeInbound(Unpooled.copiedBuffer(oversized, CharsetUtil.US_ASCII));
        assertThat(channel.readInbound(), is(instanceOf(HttpRequest.class)));

        HttpContent prematureData = channel.readInbound();
        prematureData.release();

        assertThat(channel.readInbound(), is(nullValue()));

        // At this point, we assume that we sent '413 Entity Too Large' to the peer without closing the connection
        // so that the client can try again.
        decoder.reset();

        String query = "GET /max-file-size HTTP/1.1\r\n\r\n";
        channel.writeInbound(Unpooled.copiedBuffer(query, CharsetUtil.US_ASCII));
        assertThat(channel.readInbound(), is(instanceOf(HttpRequest.class)));
        assertThat(channel.readInbound(), is(instanceOf(LastHttpContent.class)));

        assertThat(channel.finish(), is(false));
    }

    @Test
    public void testMessagesSplitBetweenMultipleBuffers() {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestDecoder());
        String crlf = "\r\n";
        String str1 = "GET /some/path HTTP/1.1" + crlf +
                "Host: localhost1" + crlf + crlf +
                "GET /some/other/path HTTP/1.0" + crlf +
                "Hos";
        String str2 = "t: localhost2" + crlf +
                "content-length: 0" + crlf + crlf;
        channel.writeInbound(Unpooled.copiedBuffer(str1, CharsetUtil.US_ASCII));
        HttpRequest req = channel.readInbound();
        assertEquals(HttpVersion.HTTP_1_1, req.protocolVersion());
        assertEquals("/some/path", req.uri());
        assertEquals(1, req.headers().size());
        assertTrue(AsciiString.contentEqualsIgnoreCase("localhost1", req.headers().get(HOST)));
        LastHttpContent cnt = channel.readInbound();
        cnt.release();

        channel.writeInbound(Unpooled.copiedBuffer(str2, CharsetUtil.US_ASCII));
        req = channel.readInbound();
        assertEquals(HttpVersion.HTTP_1_0, req.protocolVersion());
        assertEquals("/some/other/path", req.uri());
        assertEquals(2, req.headers().size());
        assertTrue(AsciiString.contentEqualsIgnoreCase("localhost2", req.headers().get(HOST)));
        assertTrue(AsciiString.contentEqualsIgnoreCase("0", req.headers().get(HttpHeaderNames.CONTENT_LENGTH)));
        cnt = channel.readInbound();
        cnt.release();
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    public void testTooLargeInitialLine() {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestDecoder(10, 1024, 1024));
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                "Host: localhost1\r\n\r\n";

        assertTrue(channel.writeInbound(Unpooled.copiedBuffer(requestStr, CharsetUtil.US_ASCII)));
        HttpRequest request = channel.readInbound();
        assertTrue(request.decoderResult().isFailure());
        assertThat(request.decoderResult().cause(), instanceOf(TooLongHttpLineException.class));
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

    private static void testTooLargeInitialLineWithControlCharsOnly(String controlChars) {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestDecoder(15, 1024, 1024));
        String requestStr = controlChars + "GET / HTTP/1.1\r\n" +
                "Host: localhost1\r\n\r\n";

        assertTrue(channel.writeInbound(Unpooled.copiedBuffer(requestStr, CharsetUtil.US_ASCII)));
        HttpRequest request = channel.readInbound();
        assertTrue(request.decoderResult().isFailure());
        assertTrue(request.decoderResult().cause() instanceof TooLongHttpLineException);
        assertFalse(channel.finish());
    }

    @Test
    public void testInitialLineWithLeadingControlChars() {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestDecoder());
        String crlf = "\r\n";
        String request =  crlf + "GET /some/path HTTP/1.1" + crlf +
                "Host: localhost" + crlf + crlf;
        assertTrue(channel.writeInbound(Unpooled.copiedBuffer(request, CharsetUtil.US_ASCII)));
        HttpRequest req = channel.readInbound();
        assertEquals(HttpMethod.GET, req.method());
        assertEquals("/some/path", req.uri());
        assertEquals(HttpVersion.HTTP_1_1, req.protocolVersion());
        assertTrue(channel.finishAndReleaseAll());
    }

    @Test
    public void testTooLargeHeaders() {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestDecoder(1024, 10, 1024));
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                "Host: localhost1\r\n\r\n";

        assertTrue(channel.writeInbound(Unpooled.copiedBuffer(requestStr, CharsetUtil.US_ASCII)));
        HttpRequest request = channel.readInbound();
        assertTrue(request.decoderResult().isFailure());
        assertTrue(request.decoderResult().cause() instanceof TooLongHttpHeaderException);
        assertFalse(channel.finish());
    }

    @Test
    void testTotalHeaderLimit() throws Exception {
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                "Host: a.b\r\n" + // 9 content bytes
                "a1: b\r\n" +     // + 5 = 14 bytes,
                "a2: b\r\n\r\n";  // + 5 = 19 bytes

        // Decoding with a max header size of 18 bytes must fail:
        EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestDecoder(1024, 18, 1024));
        assertTrue(channel.writeInbound(Unpooled.copiedBuffer(requestStr, CharsetUtil.US_ASCII)));
        HttpRequest request = channel.readInbound();
        assertTrue(request.decoderResult().isFailure());
        assertInstanceOf(TooLongHttpHeaderException.class, request.decoderResult().cause());
        assertFalse(channel.finish());

        // Decoding with a max header size of 19 must pass:
        channel = new EmbeddedChannel(new HttpRequestDecoder(1024, 19, 1024));
        assertTrue(channel.writeInbound(Unpooled.copiedBuffer(requestStr, CharsetUtil.US_ASCII)));
        request = channel.readInbound();
        assertTrue(request.decoderResult().isSuccess());
        assertEquals("a.b", request.headers().get("Host"));
        assertEquals("b", request.headers().get("a1"));
        assertEquals("b", request.headers().get("a2"));
        assertEquals(LastHttpContent.EMPTY_LAST_CONTENT, channel.readInbound());
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
        ByteBuf requestBuffer = Unpooled.buffer();
        requestBuffer.writeCharSequence("GET /some/path HTTP/1.1\r\n" +
                "Host: netty.io\r\n", CharsetUtil.US_ASCII);
        requestBuffer.writeByte(controlChar);
        requestBuffer.writeCharSequence("Transfer-Encoding: chunked\r\n\r\n", CharsetUtil.US_ASCII);
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
        ByteBuf requestBuffer = Unpooled.buffer();
        requestBuffer.writeCharSequence("GET /some/path HTTP/1.1\r\n" +
                "Host: netty.io\r\n", CharsetUtil.US_ASCII);
        requestBuffer.writeCharSequence("Transfer-Encoding", CharsetUtil.US_ASCII);
        requestBuffer.writeByte(controlChar);
        requestBuffer.writeCharSequence(": chunked\r\n\r\n", CharsetUtil.US_ASCII);
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
                "a";
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
                "b";
        testInvalidHeaders0(requestStr);
    }

    @Test
    public void testMultipleContentLengthHeaders2() {
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                "Content-Length: 1\r\n" +
                "Connection: close\r\n" +
                "Content-Length: 0\r\n\r\n" +
                "b";
        testInvalidHeaders0(requestStr);
    }

    @Test
    public void testContentLengthHeaderWithCommaValue() {
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                "Content-Length: 1,1\r\n\r\n" +
                "b";
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
        testContentLengthAndTransferEncodingHeadersWithInvalidSeparator((char) 0x0b, true);
    }

    @Test
    public void testContentLengthAndTransferEncodingHeadersWithCR() {
        testContentLengthAndTransferEncodingHeadersWithInvalidSeparator((char) 0x0d, false);
        testContentLengthAndTransferEncodingHeadersWithInvalidSeparator((char) 0x0d, true);
    }

    private static void testContentLengthAndTransferEncodingHeadersWithInvalidSeparator(
            char separator, boolean extraLine) {
        String requestStr = "POST / HTTP/1.1\r\n" +
                "Host: example.com\r\n" +
                "Connection: close\r\n" +
                "Content-Length: 9\r\n" +
                "Transfer-Encoding:" + separator + "chunked\r\n\r\n" +
                (extraLine ? "0\r\n\r\n" : "") +
                "something\r\n\r\n";
        testInvalidHeaders0(requestStr);
    }

    @Test
    public void testContentLengthHeaderAndChunked() {
        String requestStr = "POST / HTTP/1.1\r\n" +
                "Host: example.com\r\n" +
                "Connection: close\r\n" +
                "Content-Length: 5\r\n" +
                "Transfer-Encoding: chunked\r\n\r\n" +
                "0\r\n\r\n";
        EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestDecoder());
        assertTrue(channel.writeInbound(Unpooled.copiedBuffer(requestStr, CharsetUtil.US_ASCII)));
        HttpRequest request = channel.readInbound();
        assertFalse(request.decoderResult().isFailure());
        assertTrue(request.headers().names().contains("Transfer-Encoding"));
        assertTrue(request.headers().contains("Transfer-Encoding", "chunked", false));
        assertFalse(request.headers().contains("Content-Length"));
        LastHttpContent c = channel.readInbound();
        c.release();
        assertFalse(channel.finish());
    }

    @Test
    public void testOrderOfHeadersWithContentLength() {
        String requestStr = "GET /some/path HTTP/1.1\r\n" +
                "Host: example.com\r\n" +
                "Content-Length: 5\r\n" +
                "Connection: close\r\n\r\n" +
                "hello";
        EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestDecoder());
        assertTrue(channel.writeInbound(Unpooled.copiedBuffer(requestStr, CharsetUtil.US_ASCII)));
        HttpRequest request = channel.readInbound();
        List<String> headers = new ArrayList<String>();
        for (Map.Entry<String, String> header : request.headers()) {
            headers.add(header.getKey());
        }
        assertEquals(Arrays.asList("Host", "Content-Length", "Connection"), headers, "ordered headers");
    }

    @Test
    public void testHttpMessageDecoderResult() {
        String requestStr = "PUT /some/path HTTP/1.1\r\n" +
                "Content-Length: 11\r\n" +
                "Connection: close\r\n\r\n" +
                "Lorem ipsum";
        EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestDecoder());
        assertTrue(channel.writeInbound(Unpooled.copiedBuffer(requestStr, CharsetUtil.US_ASCII)));
        HttpRequest request = channel.readInbound();
        assertTrue(request.decoderResult().isSuccess());
        assertThat(request.decoderResult(), instanceOf(HttpMessageDecoderResult.class));
        HttpMessageDecoderResult decoderResult = (HttpMessageDecoderResult) request.decoderResult();
        assertThat(decoderResult.initialLineLength(), is(23));
        assertThat(decoderResult.headerSize(), is(35));
        assertThat(decoderResult.totalSize(), is(58));
        HttpContent c = channel.readInbound();
        c.release();
        assertFalse(channel.finish());
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

        assertTrue(channel.writeInbound(Unpooled.copiedBuffer(requestStr, CharsetUtil.US_ASCII)));
        HttpRequest request = channel.readInbound();
        assertTrue(request.decoderResult().isSuccess());
        HttpHeaders headers = request.headers();
        assertEquals("example.com", headers.get("Host"));
        assertEquals("x0", headers.get("X-0-Header"));
        assertEquals("x1", headers.get("X-1-Header"));
        assertEquals("x2", headers.get("X-2-Header"));
        assertEquals("x3", headers.get("X-3-Header"));
        LastHttpContent last = channel.readInbound();
        assertEquals(LastHttpContent.EMPTY_LAST_CONTENT, last);
        last.release();
        assertFalse(channel.finish());
    }

    @Test
    public void testChunkSizeOverflow() {
        String requestStr = "PUT /some/path HTTP/1.1\r\n" +
                "Transfer-Encoding: chunked\r\n\r\n" +
                "8ccccccc\r\n";
        EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestDecoder());
        assertTrue(channel.writeInbound(Unpooled.copiedBuffer(requestStr, CharsetUtil.US_ASCII)));
        HttpRequest request = channel.readInbound();
        assertTrue(request.decoderResult().isSuccess());
        HttpContent c = channel.readInbound();
        c.release();
        assertTrue(c.decoderResult().isFailure());
        assertInstanceOf(NumberFormatException.class, c.decoderResult().cause());
        assertFalse(channel.finish());
    }

    @Test
    public void testChunkSizeOverflow2() {
        String requestStr = "PUT /some/path HTTP/1.1\r\n" +
                "Transfer-Encoding: chunked\r\n\r\n" +
                "bbbbbbbe;\n\r\n";
        EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestDecoder());
        assertTrue(channel.writeInbound(Unpooled.copiedBuffer(requestStr, CharsetUtil.US_ASCII)));
        HttpRequest request = channel.readInbound();
        assertTrue(request.decoderResult().isSuccess());
        HttpContent c = channel.readInbound();
        c.release();
        assertTrue(c.decoderResult().isFailure());
        assertInstanceOf(NumberFormatException.class, c.decoderResult().cause());
        assertFalse(channel.finish());
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

    @Test
    void reentrantClose() {
        String requestStr = "GET / HTTP/1.1\r\n" +
                "Host: example.com\r\n" +
                "Content-Length: 0\r\n" +
                "\r\n" +
                "GET / HTTP/1.1\r\n" +
                "Host: example.com\r\n" +
                "Content-Length: 0\r\n" +
                "\r\n";
        EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestDecoder(), new ChannelInboundHandlerAdapter() {
            private int i;

            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                if (i == 0) {
                    assertInstanceOf(HttpRequest.class, msg);
                } else if (i == 1) {
                    assertInstanceOf(LastHttpContent.class, msg);
                } else if (i == 2) {
                    assertInstanceOf(HttpRequest.class, msg);
                } else if (i == 3) {
                    assertInstanceOf(LastHttpContent.class, msg);
                }
                ReferenceCountUtil.release(msg);

                if (++i == 1) {
                    // first request
                    ctx.close();
                }
            }
        });

        assertFalse(channel.writeInbound(Unpooled.copiedBuffer(requestStr, CharsetUtil.US_ASCII)));
        assertFalse(channel.finish());
    }

    private static void testInvalidHeaders0(String requestStr) {
        testInvalidHeaders0(Unpooled.copiedBuffer(requestStr, CharsetUtil.US_ASCII));
    }

    private static void testInvalidHeaders0(ByteBuf requestBuffer) {
        EmbeddedChannel channel = new EmbeddedChannel(new HttpRequestDecoder());
        assertTrue(channel.writeInbound(requestBuffer));
        HttpRequest request = channel.readInbound();
        assertThat(request.decoderResult().cause(), instanceOf(IllegalArgumentException.class));
        assertTrue(request.decoderResult().isFailure());
        assertFalse(channel.finish());
    }
}
