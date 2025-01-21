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

import io.netty.util.AsciiString;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static io.netty.handler.codec.http.HttpHeadersTestUtils.of;
import static io.netty.handler.codec.http.HttpUtil.normalizeAndGetContentLength;
import static io.netty.handler.codec.http.HttpUtil.validateToken;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class HttpUtilTest {

    @Test
    public void testRecognizesOriginForm() {
        // Origin form: https://tools.ietf.org/html/rfc7230#section-5.3.1
        assertTrue(HttpUtil.isOriginForm(URI.create("/where?q=now")));
        // Absolute form: https://tools.ietf.org/html/rfc7230#section-5.3.2
        assertFalse(HttpUtil.isOriginForm(URI.create("http://www.example.org/pub/WWW/TheProject.html")));
        // Authority form: https://tools.ietf.org/html/rfc7230#section-5.3.3
        assertFalse(HttpUtil.isOriginForm(URI.create("www.example.com:80")));
        // Asterisk form: https://tools.ietf.org/html/rfc7230#section-5.3.4
        assertFalse(HttpUtil.isOriginForm(URI.create("*")));
    }

    @Test public void testRecognizesAsteriskForm() {
        // Asterisk form: https://tools.ietf.org/html/rfc7230#section-5.3.4
        assertTrue(HttpUtil.isAsteriskForm(URI.create("*")));
        // Origin form: https://tools.ietf.org/html/rfc7230#section-5.3.1
        assertFalse(HttpUtil.isAsteriskForm(URI.create("/where?q=now")));
        // Absolute form: https://tools.ietf.org/html/rfc7230#section-5.3.2
        assertFalse(HttpUtil.isAsteriskForm(URI.create("http://www.example.org/pub/WWW/TheProject.html")));
        // Authority form: https://tools.ietf.org/html/rfc7230#section-5.3.3
        assertFalse(HttpUtil.isAsteriskForm(URI.create("www.example.com:80")));
    }

    @Test
    public void testRemoveTransferEncodingIgnoreCase() {
        HttpMessage message = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        message.headers().set(HttpHeaderNames.TRANSFER_ENCODING, "Chunked");
        assertFalse(message.headers().isEmpty());
        HttpUtil.setTransferEncodingChunked(message, false);
        assertTrue(message.headers().isEmpty());
    }

    // Test for https://github.com/netty/netty/issues/1690
    @Test
    public void testGetOperations() {
        HttpHeaders headers = new DefaultHttpHeaders();
        headers.add(of("Foo"), of("1"));
        headers.add(of("Foo"), of("2"));

        assertEquals("1", headers.get(of("Foo")));

        List<String> values = headers.getAll(of("Foo"));
        assertEquals(2, values.size());
        assertEquals("1", values.get(0));
        assertEquals("2", values.get(1));
    }

    @Test
    public void testGetCharsetAsRawCharSequence() {
        String QUOTES_CHARSET_CONTENT_TYPE = "text/html; charset=\"utf8\"";
        String SIMPLE_CONTENT_TYPE = "text/html";

        HttpMessage message = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        message.headers().set(HttpHeaderNames.CONTENT_TYPE, QUOTES_CHARSET_CONTENT_TYPE);
        assertEquals("\"utf8\"", HttpUtil.getCharsetAsSequence(message));
        assertEquals("\"utf8\"", HttpUtil.getCharsetAsSequence(QUOTES_CHARSET_CONTENT_TYPE));

        message.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html");
        assertNull(HttpUtil.getCharsetAsSequence(message));
        assertNull(HttpUtil.getCharsetAsSequence(SIMPLE_CONTENT_TYPE));
    }

    @Test
    public void testGetCharset() {
        testGetCharsetUtf8("text/html; charset=utf-8");
    }

    @Test
    public void testGetCharsetNoSpace() {
        testGetCharsetUtf8("text/html;charset=utf-8");
    }

    @Test
    public void testGetCharsetQuoted() {
        testGetCharsetUtf8("text/html; charset=\"utf-8\"");
    }

    @Test
    public void testGetCharsetNoSpaceQuoted() {
        testGetCharsetUtf8("text/html;charset=\"utf-8\"");
    }

    private void testGetCharsetUtf8(String contentType) {
        String UPPER_CASE_NORMAL_CONTENT_TYPE = contentType.toUpperCase();

        HttpMessage message = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        message.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
        assertEquals(CharsetUtil.UTF_8, HttpUtil.getCharset(message));
        assertEquals(CharsetUtil.UTF_8, HttpUtil.getCharset(contentType));

        message.headers().set(HttpHeaderNames.CONTENT_TYPE, UPPER_CASE_NORMAL_CONTENT_TYPE);
        assertEquals(CharsetUtil.UTF_8, HttpUtil.getCharset(message));
        assertEquals(CharsetUtil.UTF_8, HttpUtil.getCharset(UPPER_CASE_NORMAL_CONTENT_TYPE));
    }

    @Test
    public void testGetCharsetNoLeadingQuotes() {
        testGetCharsetInvalidQuotes("text/html;charset=utf-8\"");
    }

    @Test
    public void testGetCharsetNoTrailingQuotes() {
        testGetCharsetInvalidQuotes("text/html;charset=\"utf-8");
    }

    @Test
    public void testGetCharsetOnlyQuotes() {
        testGetCharsetInvalidQuotes("text/html;charset=\"\"");
    }

    private static void testGetCharsetInvalidQuotes(String contentType) {
        String UPPER_CASE_NORMAL_CONTENT_TYPE = contentType.toUpperCase();

        HttpMessage message = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        message.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
        assertEquals(CharsetUtil.ISO_8859_1, HttpUtil.getCharset(message, CharsetUtil.ISO_8859_1));
        assertEquals(CharsetUtil.ISO_8859_1, HttpUtil.getCharset(contentType, CharsetUtil.ISO_8859_1));

        message.headers().set(HttpHeaderNames.CONTENT_TYPE, UPPER_CASE_NORMAL_CONTENT_TYPE);
        assertEquals(CharsetUtil.ISO_8859_1, HttpUtil.getCharset(message, CharsetUtil.ISO_8859_1));
        assertEquals(CharsetUtil.ISO_8859_1, HttpUtil.getCharset(UPPER_CASE_NORMAL_CONTENT_TYPE,
                CharsetUtil.ISO_8859_1));
    }

    @Test
    public void testGetCharsetIfNotLastParameter() {
        String NORMAL_CONTENT_TYPE_WITH_PARAMETERS = "application/soap-xml; charset=utf-8; "
            + "action=\"http://www.soap-service.by/foo/add\"";

        HttpMessage message = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST,
            "http://localhost:7788/foo");
        message.headers().set(HttpHeaderNames.CONTENT_TYPE, NORMAL_CONTENT_TYPE_WITH_PARAMETERS);

        assertEquals(CharsetUtil.UTF_8, HttpUtil.getCharset(message));
        assertEquals(CharsetUtil.UTF_8, HttpUtil.getCharset(NORMAL_CONTENT_TYPE_WITH_PARAMETERS));

        assertEquals("utf-8", HttpUtil.getCharsetAsSequence(message));
        assertEquals("utf-8", HttpUtil.getCharsetAsSequence(NORMAL_CONTENT_TYPE_WITH_PARAMETERS));
    }

    @Test
    public void testGetCharset_defaultValue() {
        final String SIMPLE_CONTENT_TYPE = "text/html";
        final String CONTENT_TYPE_WITH_INCORRECT_CHARSET = "text/html; charset=UTFFF";
        final String CONTENT_TYPE_WITH_ILLEGAL_CHARSET_NAME = "text/html; charset=!illegal!";

        HttpMessage message = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        message.headers().set(HttpHeaderNames.CONTENT_TYPE, SIMPLE_CONTENT_TYPE);
        assertEquals(CharsetUtil.ISO_8859_1, HttpUtil.getCharset(message));
        assertEquals(CharsetUtil.ISO_8859_1, HttpUtil.getCharset(SIMPLE_CONTENT_TYPE));

        message.headers().set(HttpHeaderNames.CONTENT_TYPE, SIMPLE_CONTENT_TYPE);
        assertEquals(CharsetUtil.UTF_8, HttpUtil.getCharset(message, StandardCharsets.UTF_8));
        assertEquals(CharsetUtil.UTF_8, HttpUtil.getCharset(SIMPLE_CONTENT_TYPE, StandardCharsets.UTF_8));

        message.headers().set(HttpHeaderNames.CONTENT_TYPE, CONTENT_TYPE_WITH_INCORRECT_CHARSET);
        assertEquals(CharsetUtil.ISO_8859_1, HttpUtil.getCharset(message));
        assertEquals(CharsetUtil.ISO_8859_1, HttpUtil.getCharset(CONTENT_TYPE_WITH_INCORRECT_CHARSET));

        message.headers().set(HttpHeaderNames.CONTENT_TYPE, CONTENT_TYPE_WITH_INCORRECT_CHARSET);
        assertEquals(CharsetUtil.UTF_8, HttpUtil.getCharset(message, StandardCharsets.UTF_8));
        assertEquals(CharsetUtil.UTF_8,
                     HttpUtil.getCharset(CONTENT_TYPE_WITH_INCORRECT_CHARSET, StandardCharsets.UTF_8));

        message.headers().set(HttpHeaderNames.CONTENT_TYPE, CONTENT_TYPE_WITH_ILLEGAL_CHARSET_NAME);
        assertEquals(CharsetUtil.ISO_8859_1, HttpUtil.getCharset(message));
        assertEquals(CharsetUtil.ISO_8859_1, HttpUtil.getCharset(CONTENT_TYPE_WITH_ILLEGAL_CHARSET_NAME));

        message.headers().set(HttpHeaderNames.CONTENT_TYPE, CONTENT_TYPE_WITH_ILLEGAL_CHARSET_NAME);
        assertEquals(CharsetUtil.UTF_8, HttpUtil.getCharset(message, StandardCharsets.UTF_8));
        assertEquals(CharsetUtil.UTF_8,
                HttpUtil.getCharset(CONTENT_TYPE_WITH_ILLEGAL_CHARSET_NAME, StandardCharsets.UTF_8));
    }

    @Test
    public void testGetMimeType() {
        final String SIMPLE_CONTENT_TYPE = "text/html";
        final String NORMAL_CONTENT_TYPE = "text/html; charset=utf-8";

        HttpMessage message = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        assertNull(HttpUtil.getMimeType(message));
        message.headers().set(HttpHeaderNames.CONTENT_TYPE, "");
        assertNull(HttpUtil.getMimeType(message));
        assertNull(HttpUtil.getMimeType(""));
        message.headers().set(HttpHeaderNames.CONTENT_TYPE, SIMPLE_CONTENT_TYPE);
        assertEquals("text/html", HttpUtil.getMimeType(message));
        assertEquals("text/html", HttpUtil.getMimeType(SIMPLE_CONTENT_TYPE));

        message.headers().set(HttpHeaderNames.CONTENT_TYPE, NORMAL_CONTENT_TYPE);
        assertEquals("text/html", HttpUtil.getMimeType(message));
        assertEquals("text/html", HttpUtil.getMimeType(NORMAL_CONTENT_TYPE));
    }

    @Test
    public void testGetContentLengthThrowsNumberFormatException() {
        final HttpMessage message = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        message.headers().set(HttpHeaderNames.CONTENT_LENGTH, "bar");
        try {
            HttpUtil.getContentLength(message);
            fail();
        } catch (final NumberFormatException e) {
            // a number format exception is expected here
        }
    }

    @Test
    public void testGetContentLengthIntDefaultValueThrowsNumberFormatException() {
        final HttpMessage message = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        message.headers().set(HttpHeaderNames.CONTENT_LENGTH, "bar");
        try {
            HttpUtil.getContentLength(message, 1);
            fail();
        } catch (final NumberFormatException e) {
            // a number format exception is expected here
        }
    }

    @Test
    public void testGetContentLengthLongDefaultValueThrowsNumberFormatException() {
        final HttpMessage message = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        message.headers().set(HttpHeaderNames.CONTENT_LENGTH, "bar");
        try {
            HttpUtil.getContentLength(message, 1L);
            fail();
        } catch (final NumberFormatException e) {
            // a number format exception is expected here
        }
    }

    @Test
    public void testDoubleChunkedHeader() {
        HttpMessage message = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        message.headers().add(HttpHeaderNames.TRANSFER_ENCODING, "chunked");
        HttpUtil.setTransferEncodingChunked(message, true);
        List<String> expected = singletonList("chunked");
        assertEquals(expected, message.headers().getAll(HttpHeaderNames.TRANSFER_ENCODING));
    }

    private static List<String> allPossibleCasesOfContinue() {
        final List<String> cases = new ArrayList<String>();
        final String c = "continue";
        for (int i = 0; i < Math.pow(2, c.length()); i++) {
            final StringBuilder sb = new StringBuilder(c.length());
            int j = i;
            int k = 0;
            while (j > 0) {
                if ((j & 1) == 1) {
                    sb.append(Character.toUpperCase(c.charAt(k++)));
                } else {
                    sb.append(c.charAt(k++));
                }
                j >>= 1;
            }
            for (; k < c.length(); k++) {
                sb.append(c.charAt(k));
            }
            cases.add(sb.toString());
        }
        return cases;
    }

    @Test
    public void testIs100Continue() {
        // test all possible cases of 100-continue
        for (final String continueCase : allPossibleCasesOfContinue()) {
            run100ContinueTest(HttpVersion.HTTP_1_1, "100-" + continueCase, true);
        }
        run100ContinueTest(HttpVersion.HTTP_1_1, null, false);
        run100ContinueTest(HttpVersion.HTTP_1_1, "chocolate=yummy", false);
        run100ContinueTest(HttpVersion.HTTP_1_0, "100-continue", false);
        final HttpMessage message = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        message.headers().set(HttpHeaderNames.EXPECT, "100-continue");
        run100ContinueTest(message, false);
    }

    private static void run100ContinueTest(final HttpVersion version, final String expectations, boolean expect) {
        final HttpMessage message = new DefaultFullHttpRequest(version, HttpMethod.GET, "/");
        if (expectations != null) {
            message.headers().set(HttpHeaderNames.EXPECT, expectations);
        }
        run100ContinueTest(message, expect);
    }

    private static void run100ContinueTest(final HttpMessage message, final boolean expected) {
        assertEquals(expected, HttpUtil.is100ContinueExpected(message));
        ReferenceCountUtil.release(message);
    }

    @Test
    public void testContainsUnsupportedExpectation() {
        // test all possible cases of 100-continue
        for (final String continueCase : allPossibleCasesOfContinue()) {
            runUnsupportedExpectationTest(HttpVersion.HTTP_1_1, "100-" + continueCase, false);
        }
        runUnsupportedExpectationTest(HttpVersion.HTTP_1_1, null, false);
        runUnsupportedExpectationTest(HttpVersion.HTTP_1_1, "chocolate=yummy", true);
        runUnsupportedExpectationTest(HttpVersion.HTTP_1_0, "100-continue", false);
        final HttpMessage message = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        message.headers().set("Expect", "100-continue");
        runUnsupportedExpectationTest(message, false);
    }

    private static void runUnsupportedExpectationTest(final HttpVersion version,
                                                      final String expectations, boolean expect) {
        final HttpMessage message = new DefaultFullHttpRequest(version, HttpMethod.GET, "/");
        if (expectations != null) {
            message.headers().set("Expect", expectations);
        }
        runUnsupportedExpectationTest(message, expect);
    }

    private static void runUnsupportedExpectationTest(final HttpMessage message, final boolean expected) {
        assertEquals(expected, HttpUtil.isUnsupportedExpectation(message));
        ReferenceCountUtil.release(message);
    }

    @Test
    public void testFormatHostnameForHttpFromResolvedAddressWithHostname() throws Exception {
        InetSocketAddress socketAddress = new InetSocketAddress(InetAddress.getByName("localhost"), 8080);
        assertEquals("localhost", HttpUtil.formatHostnameForHttp(socketAddress));
    }

    @Test
    public void testFormatHostnameForHttpFromUnesolvedAddressWithHostname() {
        InetSocketAddress socketAddress = InetSocketAddress.createUnresolved("localhost", 80);
        assertEquals("localhost", HttpUtil.formatHostnameForHttp(socketAddress));
    }

    @Test
    public void testIpv6() throws Exception  {
        InetSocketAddress socketAddress = new InetSocketAddress(InetAddress.getByName("::1"), 8080);
        assertEquals("[::1]", HttpUtil.formatHostnameForHttp(socketAddress));
    }

    @Test
    public void testIpv6Unresolved()  {
        InetSocketAddress socketAddress = InetSocketAddress.createUnresolved("::1", 8080);
        assertEquals("[::1]", HttpUtil.formatHostnameForHttp(socketAddress));
    }

    @Test
    public void testIpv6UnresolvedExplicitAddress()  {
        InetSocketAddress socketAddress = InetSocketAddress.createUnresolved("[2001:4860:4860:0:0:0:0:8888]", 8080);
        assertEquals("[2001:4860:4860:0:0:0:0:8888]", HttpUtil.formatHostnameForHttp(socketAddress));
    }

    @Test
    public void testIpv4() throws Exception  {
        InetSocketAddress socketAddress = new InetSocketAddress(InetAddress.getByName("10.0.0.1"), 8080);
        assertEquals("10.0.0.1", HttpUtil.formatHostnameForHttp(socketAddress));
    }

    @Test
    public void testIpv4Unresolved()  {
        InetSocketAddress socketAddress = InetSocketAddress.createUnresolved("10.0.0.1", 8080);
        assertEquals("10.0.0.1", HttpUtil.formatHostnameForHttp(socketAddress));
    }

    @Test
    public void testKeepAliveIfConnectionHeaderAbsent() {
        HttpMessage http11Message = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET,
            "http:localhost/http_1_1");
        assertTrue(HttpUtil.isKeepAlive(http11Message));

        HttpMessage http10Message = new DefaultHttpRequest(HttpVersion.HTTP_1_0, HttpMethod.GET,
            "http:localhost/http_1_0");
        assertFalse(HttpUtil.isKeepAlive(http10Message));
    }

    @Test
    public void testKeepAliveIfConnectionHeaderMultipleValues() {
        HttpMessage http11Message = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET,
            "http:localhost/http_1_1");
        http11Message.headers().set(
                HttpHeaderNames.CONNECTION, HttpHeaderValues.UPGRADE + ", " + HttpHeaderValues.CLOSE);
        assertFalse(HttpUtil.isKeepAlive(http11Message));

        http11Message.headers().set(
                HttpHeaderNames.CONNECTION, HttpHeaderValues.UPGRADE + ", Close");
        assertFalse(HttpUtil.isKeepAlive(http11Message));

        http11Message.headers().set(
                HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE + ", " + HttpHeaderValues.UPGRADE);
        assertFalse(HttpUtil.isKeepAlive(http11Message));

        http11Message.headers().set(
                HttpHeaderNames.CONNECTION, HttpHeaderValues.UPGRADE + ", " + HttpHeaderValues.KEEP_ALIVE);
        assertTrue(HttpUtil.isKeepAlive(http11Message));
    }

    @Test
    public void normalizeAndGetContentLengthEmpty() {
        testNormalizeAndGetContentLengthInvalidContentLength("");
    }

    @Test
    public void normalizeAndGetContentLengthNotANumber() {
        testNormalizeAndGetContentLengthInvalidContentLength("foo");
    }

    @Test
    public void normalizeAndGetContentLengthNegative() {
        testNormalizeAndGetContentLengthInvalidContentLength("-1");
    }

    private static void testNormalizeAndGetContentLengthInvalidContentLength(final String contentLengthField) {
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                normalizeAndGetContentLength(singletonList(contentLengthField), false, false);
            }
        });
    }

    private static List<Character> validTokenChars() {
        List<Character> list = new ArrayList<Character>();
        for (char c = '0'; c <= '9'; c++) {
            list.add(c);
        }
        for (char c = 'a'; c <= 'z'; c++) {
            list.add(c);
        }
        for (char c = 'A'; c <= 'Z'; c++) {
            list.add(c);
        }

        // Unreserved characters:
        list.add('-');
        list.add('.');
        list.add('_');
        list.add('~');

        // Token special characters:
        list.add('!');
        list.add('#');
        list.add('$');
        list.add('%');
        list.add('&');
        list.add('\'');
        list.add('*');
        list.add('+');
        list.add('^');
        list.add('`');
        list.add('|');

        return list;
    }

    @ParameterizedTest
    @MethodSource("validTokenChars")
    public void testValidTokenChars(char validChar) {
        AsciiString asciiStringToken =
                new AsciiString(new byte[] { 'G', 'E', (byte) validChar, 'T' });
        String token = "GE" + validChar + 'T';
        assertEquals(-1, validateToken(asciiStringToken));
        assertEquals(-1, validateToken(token));
    }

    @ParameterizedTest
    @ValueSource(chars = {
            '(', ')', ',', '/', ':', ';', '<', '=', '>', '?', '@',
            '\"', '[', '\\', ']', '{', '}', '\u0000', ' ', '\u007f', 'ÿ'
    })
    public void testInvalidTokenChars(char invalidChar) {
        AsciiString asciiStringToken =
                new AsciiString(new byte[] { 'G', 'E', (byte) invalidChar, 'T' });
        String token = "GE" + invalidChar + 'T';
        assertEquals(2, validateToken(asciiStringToken));
        assertEquals(2, validateToken(token));
    }
}
