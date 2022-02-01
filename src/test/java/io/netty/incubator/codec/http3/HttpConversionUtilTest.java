/*
 * Copyright 2021 The Netty Project
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
package io.netty.incubator.codec.http3;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.AsciiString;
import org.junit.jupiter.api.Test;

import static io.netty.handler.codec.http.HttpHeaderNames.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaderNames.COOKIE;
import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static io.netty.handler.codec.http.HttpHeaderNames.KEEP_ALIVE;
import static io.netty.handler.codec.http.HttpHeaderNames.PROXY_CONNECTION;
import static io.netty.handler.codec.http.HttpHeaderNames.TE;
import static io.netty.handler.codec.http.HttpHeaderNames.TRANSFER_ENCODING;
import static io.netty.handler.codec.http.HttpHeaderNames.UPGRADE;
import static io.netty.handler.codec.http.HttpHeaderValues.GZIP;
import static io.netty.handler.codec.http.HttpHeaderValues.TRAILERS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HttpConversionUtilTest {

    @Test
    public void connectNoPath() throws Exception {
        String authority = "netty.io:80";
        Http3Headers headers = new DefaultHttp3Headers();
        headers.authority(authority);
        headers.method(HttpMethod.CONNECT.asciiName());
        HttpRequest request = HttpConversionUtil.toHttpRequest(0, headers, true);
        assertNotNull(request);
        assertEquals(authority, request.uri());
        assertEquals(authority, request.headers().get(HOST));
    }

    @Test
    public void setHttp3AuthorityWithoutUserInfo() {
        Http3Headers headers = new DefaultHttp3Headers();

        HttpConversionUtil.setHttp3Authority("foo", headers);
        assertEquals(new AsciiString("foo"), headers.authority());
    }

    @Test
    public void setHttp3AuthorityWithUserInfo() {
        Http3Headers headers = new DefaultHttp3Headers();

        HttpConversionUtil.setHttp3Authority("info@foo", headers);
        assertEquals(new AsciiString("foo"), headers.authority());

        HttpConversionUtil.setHttp3Authority("@foo.bar", headers);
        assertEquals(new AsciiString("foo.bar"), headers.authority());
    }

    @Test
    public void setHttp3AuthorityNullOrEmpty() {
        Http3Headers headers = new DefaultHttp3Headers();

        HttpConversionUtil.setHttp3Authority(null, headers);
        assertNull(headers.authority());

        HttpConversionUtil.setHttp3Authority("", headers);
        assertSame(AsciiString.EMPTY_STRING, headers.authority());
    }

    @Test
    public void setHttp2AuthorityWithEmptyAuthority() {
        assertThrows(IllegalArgumentException.class,
                () -> HttpConversionUtil.setHttp3Authority("info@", new DefaultHttp3Headers()));
    }

    @Test
    public void stripTEHeaders() {
        HttpHeaders inHeaders = new DefaultHttpHeaders();
        inHeaders.add(TE, GZIP);
        Http3Headers out = new DefaultHttp3Headers();
        HttpConversionUtil.toHttp3Headers(inHeaders, out);
        assertTrue(out.isEmpty());
    }

    @Test
    public void stripTEHeadersExcludingTrailers() {
        HttpHeaders inHeaders = new DefaultHttpHeaders();
        inHeaders.add(TE, GZIP);
        inHeaders.add(TE, TRAILERS);
        Http3Headers out = new DefaultHttp3Headers();
        HttpConversionUtil.toHttp3Headers(inHeaders, out);
        assertSame(TRAILERS, out.get(TE));
    }

    @Test
    public void stripTEHeadersCsvSeparatedExcludingTrailers() {
        HttpHeaders inHeaders = new DefaultHttpHeaders();
        inHeaders.add(TE, GZIP + "," + TRAILERS);
        Http3Headers out = new DefaultHttp3Headers();
        HttpConversionUtil.toHttp3Headers(inHeaders, out);
        assertSame(TRAILERS, out.get(TE));
    }

    @Test
    public void stripTEHeadersCsvSeparatedAccountsForValueSimilarToTrailers() {
        HttpHeaders inHeaders = new DefaultHttpHeaders();
        inHeaders.add(TE, GZIP + "," + TRAILERS + "foo");
        Http3Headers out = new DefaultHttp3Headers();
        HttpConversionUtil.toHttp3Headers(inHeaders, out);
        assertFalse(out.contains(TE));
    }

    @Test
    public void stripTEHeadersAccountsForValueSimilarToTrailers() {
        HttpHeaders inHeaders = new DefaultHttpHeaders();
        inHeaders.add(TE, TRAILERS + "foo");
        Http3Headers out = new DefaultHttp3Headers();
        HttpConversionUtil.toHttp3Headers(inHeaders, out);
        assertFalse(out.contains(TE));
    }

    @Test
    public void stripTEHeadersAccountsForOWS() {
        HttpHeaders inHeaders = new DefaultHttpHeaders();
        inHeaders.add(TE, " " + TRAILERS + " ");
        Http3Headers out = new DefaultHttp3Headers();
        HttpConversionUtil.toHttp3Headers(inHeaders, out);
        assertSame(TRAILERS, out.get(TE));
    }

    @Test
    public void stripConnectionHeadersAndNominees() {
        HttpHeaders inHeaders = new DefaultHttpHeaders();
        inHeaders.add(CONNECTION, "foo");
        inHeaders.add("foo", "bar");
        Http3Headers out = new DefaultHttp3Headers();
        HttpConversionUtil.toHttp3Headers(inHeaders, out);
        assertTrue(out.isEmpty());
    }

    @Test
    public void stripConnectionNomineesWithCsv() {
        HttpHeaders inHeaders = new DefaultHttpHeaders();
        inHeaders.add(CONNECTION, "foo,  bar");
        inHeaders.add("foo", "baz");
        inHeaders.add("bar", "qux");
        inHeaders.add("hello", "world");
        Http3Headers out = new DefaultHttp3Headers();
        HttpConversionUtil.toHttp3Headers(inHeaders, out);
        assertEquals(1, out.size());
        assertSame("world", out.get("hello"));
    }

    @Test
    public void addHttp3ToHttpHeadersCombinesCookies() throws Http3Exception {
        Http3Headers inHeaders = new DefaultHttp3Headers();
        inHeaders.add("yes", "no");
        inHeaders.add(COOKIE, "foo=bar");
        inHeaders.add(COOKIE, "bax=baz");

        HttpHeaders outHeaders = new DefaultHttpHeaders();

        HttpConversionUtil.addHttp3ToHttpHeaders(5, inHeaders, outHeaders, HttpVersion.HTTP_1_1, false, false);
        assertEquals("no", outHeaders.get("yes"));
        assertEquals("foo=bar; bax=baz", outHeaders.get(COOKIE.toString()));
    }

    @Test
    public void connectionSpecificHeadersShouldBeRemoved() {
        HttpHeaders inHeaders = new DefaultHttpHeaders();
        inHeaders.add(CONNECTION, "keep-alive");
        inHeaders.add(HOST, "example.com");
        @SuppressWarnings("deprecation")
        AsciiString keepAlive = KEEP_ALIVE;
        inHeaders.add(keepAlive, "timeout=5, max=1000");
        @SuppressWarnings("deprecation")
        AsciiString proxyConnection = PROXY_CONNECTION;
        inHeaders.add(proxyConnection, "timeout=5, max=1000");
        inHeaders.add(TRANSFER_ENCODING, "chunked");
        inHeaders.add(UPGRADE, "h2c");

        Http3Headers outHeaders = new DefaultHttp3Headers();
        HttpConversionUtil.toHttp3Headers(inHeaders, outHeaders);

        assertFalse(outHeaders.contains(CONNECTION));
        assertFalse(outHeaders.contains(HOST));
        assertFalse(outHeaders.contains(keepAlive));
        assertFalse(outHeaders.contains(proxyConnection));
        assertFalse(outHeaders.contains(TRANSFER_ENCODING));
        assertFalse(outHeaders.contains(UPGRADE));
    }

    @Test
    public void http3ToHttpHeaderTest() throws Exception {
        Http3Headers http3Headers = new DefaultHttp3Headers();
        http3Headers.status("200");
        http3Headers.path("/meow"); // HTTP/2 Header response should not contain 'path' in response.
        http3Headers.set("cat", "meow");

        HttpHeaders httpHeaders = new DefaultHttpHeaders();
        HttpConversionUtil.addHttp3ToHttpHeaders(3, http3Headers, httpHeaders, HttpVersion.HTTP_1_1, false, true);
        assertFalse(httpHeaders.contains(HttpConversionUtil.ExtensionHeaderNames.PATH.text()));
        assertEquals("meow", httpHeaders.get("cat"));

        httpHeaders.clear();
        HttpConversionUtil.addHttp3ToHttpHeaders(3, http3Headers, httpHeaders, HttpVersion.HTTP_1_1, false, false);
        assertTrue(httpHeaders.contains(HttpConversionUtil.ExtensionHeaderNames.PATH.text()));
        assertEquals("meow", httpHeaders.get("cat"));
    }
}
