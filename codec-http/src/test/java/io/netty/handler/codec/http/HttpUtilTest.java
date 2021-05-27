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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import org.junit.Test;

import static io.netty.handler.codec.http.HttpHeadersTestUtils.of;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class HttpUtilTest {

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
        String NORMAL_CONTENT_TYPE = "text/html; charset=utf-8";
        String UPPER_CASE_NORMAL_CONTENT_TYPE = "TEXT/HTML; CHARSET=UTF-8";

        HttpMessage message = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        message.headers().set(HttpHeaderNames.CONTENT_TYPE, NORMAL_CONTENT_TYPE);
        assertEquals(CharsetUtil.UTF_8, HttpUtil.getCharset(message));
        assertEquals(CharsetUtil.UTF_8, HttpUtil.getCharset(NORMAL_CONTENT_TYPE));

        message.headers().set(HttpHeaderNames.CONTENT_TYPE, UPPER_CASE_NORMAL_CONTENT_TYPE);
        assertEquals(CharsetUtil.UTF_8, HttpUtil.getCharset(message));
        assertEquals(CharsetUtil.UTF_8, HttpUtil.getCharset(UPPER_CASE_NORMAL_CONTENT_TYPE));
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
        List<String> expected = Collections.singletonList("chunked");
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
}
