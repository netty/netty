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
package io.netty.handler.codec.http3;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.AsciiString;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.stream.Stream;

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
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
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

        // Regular CONNECT (RFC 9114) must not carry Extended CONNECT (RFC 9220) state.
        assertFalse(request.headers().contains(HttpConversionUtil.ExtensionHeaderNames.PROTOCOL.text()));
        assertFalse(request.headers().contains(HttpConversionUtil.ExtensionHeaderNames.PATH.text()));
    }

    @Test
    public void extendedConnectPreservesProtocolAndPathAsExtensionHeaders() throws Exception {
        String authority = "ws.example:443";
        Http3Headers headers = new DefaultHttp3Headers();
        headers.method(HttpMethod.CONNECT.asciiName());
        headers.authority(authority);
        headers.scheme("https");
        headers.path("/admin/ws");
        headers.protocol("websocket");

        HttpRequest request = HttpConversionUtil.toHttpRequest(0, headers, true);
        assertNotNull(request);

        // The request-target/URI for a CONNECT request stays the authority, same as a regular CONNECT
        // request: this fix does not change wire-level CONNECT behavior.
        assertEquals(authority, request.uri());
        assertEquals(HttpMethod.CONNECT, request.method());
        assertEquals(authority, request.headers().get(HOST));

        // But the Extended CONNECT (RFC 9220) state, which changes CONNECT semantics such that ':authority'
        // must not be treated as an ordinary tunnel target, is preserved so it is not confused with a
        // regular CONNECT request once converted to an HTTP/1.x object.
        assertEquals("websocket",
                request.headers().get(HttpConversionUtil.ExtensionHeaderNames.PROTOCOL.text()));
        assertEquals("/admin/ws",
                request.headers().get(HttpConversionUtil.ExtensionHeaderNames.PATH.text()));
    }

    @Test
    public void extendedConnectAndRegularConnectProduceDifferentHttpObjectShape() throws Exception {
        String authority = "ws.example:443";

        Http3Headers regularConnect = new DefaultHttp3Headers();
        regularConnect.method(HttpMethod.CONNECT.asciiName());
        regularConnect.authority(authority);
        HttpRequest regularConnectRequest = HttpConversionUtil.toHttpRequest(0, regularConnect, true);

        Http3Headers extendedConnect = new DefaultHttp3Headers();
        extendedConnect.method(HttpMethod.CONNECT.asciiName());
        extendedConnect.authority(authority);
        extendedConnect.scheme("https");
        extendedConnect.path("/admin/ws");
        extendedConnect.protocol("websocket");
        HttpRequest extendedConnectRequest = HttpConversionUtil.toHttpRequest(0, extendedConnect, true);

        // Both requests still have the same CONNECT method and request-target/Host, matching a regular
        // CONNECT allowlist that keys off of those alone.
        assertEquals(regularConnectRequest.method(), extendedConnectRequest.method());
        assertEquals(regularConnectRequest.uri(), extendedConnectRequest.uri());
        assertEquals(regularConnectRequest.headers().get(HOST), extendedConnectRequest.headers().get(HOST));

        // A protocol-aware policy can still tell them apart via the extension headers.
        assertFalse(regularConnectRequest.headers().contains(HttpConversionUtil.ExtensionHeaderNames.PROTOCOL.text()));
        assertTrue(extendedConnectRequest.headers().contains(HttpConversionUtil.ExtensionHeaderNames.PROTOCOL.text()));
    }

    @Test
    public void regularConnectDropsExtensionHeaderReceivedFromPeer() throws Exception {
        Http3Headers regularConnect = new DefaultHttp3Headers();
        regularConnect.method(HttpMethod.CONNECT.asciiName());
        regularConnect.authority("ws.example:443");
        regularConnect.add(HttpConversionUtil.ExtensionHeaderNames.PROTOCOL.text(), "websocket");
        HttpRequest regularConnectRequest = HttpConversionUtil.toHttpRequest(0, regularConnect, true);
        assertFalse(regularConnectRequest.headers().contains(HttpConversionUtil.ExtensionHeaderNames.PROTOCOL.text()));
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
        // Disable header validation, since it will otherwise reject the header.
        boolean validate = false;
        HttpHeaders inHeaders = new DefaultHttpHeaders(validate);
        inHeaders.add(TE, " " + TRAILERS + ' ');
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

    @ParameterizedTest
    @MethodSource("matchingAuthorityAndHost")
    public void addHttp3ToHttpHeadersDeduplicatesMatchingAuthorityAndHost(
            String authority, List<String> hosts, boolean hostAddedFirst, String expectedHost)
            throws Http3Exception {
        Http3Headers inHeaders = newAuthorityAndHostHeaders(authority, hosts, hostAddedFirst);
        HttpHeaders outHeaders = new DefaultHttpHeaders();

        HttpConversionUtil.addHttp3ToHttpHeaders(5, inHeaders, outHeaders, HttpVersion.HTTP_1_1, false, true);
        assertEquals(1, outHeaders.getAll(HOST).size());
        assertEquals(expectedHost, outHeaders.get(HOST));
    }

    @ParameterizedTest
    @MethodSource("conflictingAuthorityAndHost")
    public void addHttp3ToHttpHeadersRejectsConflictingAuthorityAndHost(
            String authority, List<String> hosts, boolean hostAddedFirst) {
        Http3Headers inHeaders = newAuthorityAndHostHeaders(authority, hosts, hostAddedFirst);
        HttpHeaders outHeaders = new DefaultHttpHeaders();

        Http3Exception exception = assertThrows(Http3Exception.class, () ->
            HttpConversionUtil.addHttp3ToHttpHeaders(5, inHeaders, outHeaders, HttpVersion.HTTP_1_1, false, true));
        assertEquals(Http3ErrorCode.H3_MESSAGE_ERROR, exception.errorCode());
    }

    // :authority, host, host header added first, expected value
    private static Stream<Arguments> matchingAuthorityAndHost() {
        return Stream.of(
            Arguments.of("example.com", singletonList("example.com"), false, "example.com"),
            Arguments.of("Example.COM", singletonList("example.com"), false, "Example.COM"),
            Arguments.of("example.com", singletonList("Example.COM"), false, "example.com"),
            Arguments.of("example.com", singletonList("example.com"), true, "example.com"),
            Arguments.of("example.com", asList("example.com", "example.com"), false, "example.com"),
            Arguments.of(null, asList("example.com", "example.com"), false, "example.com"));
    }

    // :authority, host, host header added first
    private static Stream<Arguments> conflictingAuthorityAndHost() {
        return Stream.of(
            Arguments.of("public.example.com", singletonList("internal-admin.local"), false),
            Arguments.of("public.example.com", singletonList("internal-admin.local"), true),
            Arguments.of("public.example.com", asList("public.example.com", "internal-admin.local"), false),
            Arguments.of("example.com", singletonList("example.com:4433"), false),
            Arguments.of(null, asList("public.example.com", "internal-admin.local"), false));
    }

    private static Http3Headers newAuthorityAndHostHeaders(
            String authority, List<String> hosts, boolean hostAddedFirst) {
        Http3Headers headers = new DefaultHttp3Headers();
        if (!hostAddedFirst && authority != null) {
            headers.authority(authority);
        }
        for (String host : hosts) {
            headers.add(HOST, host);
        }
        if (hostAddedFirst && authority != null) {
            headers.authority(authority);
        }
        return headers;
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
    public void toHttp3HeadersConnectUsesRequestTargetAsAuthorityIgnoringHost() {
        // The Host header intentionally conflicts with the CONNECT authority-form request-target. HTTP/3
        // must tunnel to the request-target, not to whatever a client claims via Host.
        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.CONNECT,
                "trusted.example:443");
        request.headers().add(HOST, "attacker.example:443");

        Http3Headers headers = HttpConversionUtil.toHttp3Headers(request, true);

        assertEquals(HttpMethod.CONNECT.asciiName(), headers.method());
        assertEquals(new AsciiString("trusted.example:443"), headers.authority());
        assertNull(headers.scheme());
        assertNull(headers.path());
        assertFalse(headers.contains(HOST));
    }

    @Test
    public void toHttp3HeadersConnectUsesRequestTargetAsAuthorityWithoutHostHeader() {
        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.CONNECT,
                "trusted.example:443");

        Http3Headers headers = HttpConversionUtil.toHttp3Headers(request, true);

        assertEquals(new AsciiString("trusted.example:443"), headers.authority());
        assertNull(headers.scheme());
        assertNull(headers.path());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "trusted.example:443@attacker.example:443",
        "",
        "/",
        "http://www.example.com:80",
        "trusted.example:443/../attacker.example"
    })
    public void connectAuthorityFormInvalid(String uri) {
        HttpRequest msg = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.CONNECT, uri);
        assertThrows(IllegalArgumentException.class, () -> HttpConversionUtil.toHttp3Headers(msg, true));
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

    @Test
    public void absoluteFormRequestTargetAuthorityTakesPrecedenceOverConflictingHost() {
        HttpRequest msg = new DefaultHttpRequest(
            HttpVersion.HTTP_1_1, HttpMethod.GET, "http://request-target.example/admin", true);
        msg.headers().add(HOST, "host-header.example");

        Http3Headers out = HttpConversionUtil.toHttp3Headers(msg, true);

        assertEquals(new AsciiString("/admin"), out.path());
        assertEquals(new AsciiString("http"), out.scheme());
        assertEquals(new AsciiString("request-target.example"), out.authority());
    }

    @Test
    public void absoluteFormRequestTargetAuthorityMatchingHostIsUnaffected() {
        HttpRequest msg = new DefaultHttpRequest(
            HttpVersion.HTTP_1_1, HttpMethod.GET, "http://example.com/admin", true);
        msg.headers().add(HOST, "example.com");

        Http3Headers out = HttpConversionUtil.toHttp3Headers(msg, true);

        assertEquals(new AsciiString("/admin"), out.path());
        assertEquals(new AsciiString("http"), out.scheme());
        assertEquals(new AsciiString("example.com"), out.authority());
    }

    @Test
    public void originFormRequestStillUsesHostAsAuthority() {
        HttpRequest msg = new DefaultHttpRequest(
            HttpVersion.HTTP_1_1, HttpMethod.GET, "/admin", true);
        msg.headers().add(HOST, "host-header.example");
        msg.headers().add(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), "http");

        Http3Headers out = HttpConversionUtil.toHttp3Headers(msg, true);

        assertEquals(new AsciiString("/admin"), out.path());
        assertEquals(new AsciiString("http"), out.scheme());
        assertEquals(new AsciiString("host-header.example"), out.authority());
    }

    @Test
    public void absoluteFormRequestTargetWithUserInfoStripsUserInfoFromAuthority() {
        HttpRequest msg = new DefaultHttpRequest(
            HttpVersion.HTTP_1_1, HttpMethod.GET, "http://user:pass@request-target.example/admin", true);
        msg.headers().add(HOST, "host-header.example");

        Http3Headers out = HttpConversionUtil.toHttp3Headers(msg, true);

        assertEquals(new AsciiString("request-target.example"), out.authority());
    }

    @Test
    public void absoluteFormRequestTargetWithIPv6LiteralTakesPrecedenceOverHost() {
        HttpRequest msg = new DefaultHttpRequest(
            HttpVersion.HTTP_1_1, HttpMethod.GET, "http://[::1]:8080/admin", true);
        msg.headers().add(HOST, "host-header.example");

        Http3Headers out = HttpConversionUtil.toHttp3Headers(msg, true);

        assertEquals(new AsciiString("[::1]:8080"), out.authority());
    }

    @Test
    public void absoluteFormRequestTargetWithExplicitPortTakesPrecedenceOverHost() {
        HttpRequest msg = new DefaultHttpRequest(
            HttpVersion.HTTP_1_1, HttpMethod.GET, "http://request-target.example:8080/admin", true);
        msg.headers().add(HOST, "host-header.example");

        Http3Headers out = HttpConversionUtil.toHttp3Headers(msg, true);

        assertEquals(new AsciiString("request-target.example:8080"), out.authority());
    }
}
