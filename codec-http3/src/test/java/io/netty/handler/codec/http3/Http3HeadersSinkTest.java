/*
 * Copyright 2020 The Netty Project
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


import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.util.AsciiString;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class Http3HeadersSinkTest {

    @Test
    public void testHeaderSizeExceeded() {
        Http3HeadersSink sink = new Http3HeadersSink(new DefaultHttp3Headers(), 32, false, false);
        addMandatoryPseudoHeaders(sink, false);

        Http3Exception e = assertThrows(Http3Exception.class, () -> sink.finish());
        Http3TestUtils.assertException(Http3ErrorCode.H3_EXCESSIVE_LOAD, e);
    }

    @Test
    public void testHeaderSizeNotExceed() throws Exception {
        Http3Headers headers = new DefaultHttp3Headers();
        Http3HeadersSink sink = new Http3HeadersSink(headers, 64, false, false);
        addMandatoryPseudoHeaders(sink, false);
        sink.finish();
    }

    @Test
    public void testPseudoHeaderFollowsNormalHeader() {
        Http3HeadersSink sink = new Http3HeadersSink(new DefaultHttp3Headers(), 512, true, false);
        sink.accept("name", "value");
        sink.accept(Http3Headers.PseudoHeaderName.AUTHORITY.value(), "value");
        assertThrows(Http3HeadersValidationException.class, () -> sink.finish());
    }

    @Test
    public void testInvalidatePseudoHeader() {
        Http3HeadersSink sink = new Http3HeadersSink(new DefaultHttp3Headers(), 512, true, false);
        sink.accept(":invalid", "value");
        assertThrows(Http3HeadersValidationException.class, () -> sink.finish());
    }

    @Test
    public void testMixRequestResponsePseudoHeaders() {
        Http3HeadersSink sink = new Http3HeadersSink(new DefaultHttp3Headers(), 512, true, false);
        sink.accept(Http3Headers.PseudoHeaderName.METHOD.value(), "value");
        sink.accept(Http3Headers.PseudoHeaderName.STATUS.value(), "value");
        assertThrows(Http3HeadersValidationException.class, () -> sink.finish());
    }

    @Test
    public void testValidPseudoHeadersRequest() throws Exception {
        Http3Headers headers = new DefaultHttp3Headers();
        Http3HeadersSink sink = new Http3HeadersSink(headers, 512, true, false);
        addMandatoryPseudoHeaders(sink, true);
        sink.finish();
    }

    @Test
    public void testValidPseudoHeadersResponse() throws Exception {
        Http3Headers headers = new DefaultHttp3Headers();
        Http3HeadersSink sink = new Http3HeadersSink(headers, 512, true, false);
        addMandatoryPseudoHeaders(sink, false);
        sink.finish();
    }

    @Test
    public void testDuplicatePseudoHeader() {
        Http3HeadersSink sink = new Http3HeadersSink(new DefaultHttp3Headers(), 512, true, false);
        addMandatoryPseudoHeaders(sink, false);
        sink.accept(Http3Headers.PseudoHeaderName.AUTHORITY.value(), "value");
        assertThrows(Http3HeadersValidationException.class, () -> sink.finish());
    }

    @Test
    public void testMandatoryPseudoHeaderMissingRequest() {
        Http3HeadersSink sink = new Http3HeadersSink(new DefaultHttp3Headers(), 512, true, false);
        sink.accept(Http3Headers.PseudoHeaderName.METHOD.value(), "GET");
        sink.accept(Http3Headers.PseudoHeaderName.PATH.value(), "/");
        sink.accept(Http3Headers.PseudoHeaderName.SCHEME.value(), "https");
        assertThrows(Http3HeadersValidationException.class, () -> sink.finish());
    }

    @Test
    public void testMandatoryPseudoHeaderMissingResponse() {
        Http3HeadersSink sink = new Http3HeadersSink(new DefaultHttp3Headers(), 512, true, false);
        assertThrows(Http3HeadersValidationException.class, () -> sink.finish());
    }

    @Test
    public void testInvalidPseudoHeadersForConnect() {
        Http3HeadersSink sink = new Http3HeadersSink(new DefaultHttp3Headers(), 512, true, false);
        sink.accept(Http3Headers.PseudoHeaderName.METHOD.value(), "CONNECT");
        sink.accept(Http3Headers.PseudoHeaderName.PATH.value(), "/");
        sink.accept(Http3Headers.PseudoHeaderName.SCHEME.value(), "https");
        sink.accept(Http3Headers.PseudoHeaderName.AUTHORITY.value(), "value");
        assertThrows(Http3HeadersValidationException.class, () -> sink.finish());
    }

    @Test
    public void testValidPseudoHeadersForConnect() throws Exception {
        Http3HeadersSink sink = new Http3HeadersSink(new DefaultHttp3Headers(), 512, true, false);
        sink.accept(Http3Headers.PseudoHeaderName.METHOD.value(), "CONNECT");
        sink.accept(Http3Headers.PseudoHeaderName.AUTHORITY.value(), "value");
        sink.finish();
    }

    @Test
    public void testTrailersWithRequestPseudoHeaders() {
        Http3HeadersSink sink = new Http3HeadersSink(new DefaultHttp3Headers(), 512, true, true);
        sink.accept(Http3Headers.PseudoHeaderName.METHOD.value(), "CONNECT");
        assertThrows(Http3HeadersValidationException.class, () -> sink.finish());
    }

    @Test
    public void testTrailersWithResponsePseudoHeaders() {
        Http3HeadersSink sink = new Http3HeadersSink(new DefaultHttp3Headers(), 512, true, true);
        sink.accept(Http3Headers.PseudoHeaderName.STATUS.value(), "200");
        assertThrows(Http3HeadersValidationException.class, () -> sink.finish());
    }

    @Test
    public void testAuthorityNotRequiredForOptionsWildcard() throws Http3Exception {
        Http3HeadersSink sink = new Http3HeadersSink(new DefaultHttp3Headers(), 512, true, false);
        sink.accept(Http3Headers.PseudoHeaderName.METHOD.value(), "OPTIONS");
        sink.accept(Http3Headers.PseudoHeaderName.PATH.value(), "*");
        sink.accept(Http3Headers.PseudoHeaderName.SCHEME.value(), "https");
        sink.finish();
    }

    @Test
    public void testOptionsNonWildcardWithAuthority() throws Http3Exception {
        Http3HeadersSink sink = new Http3HeadersSink(new DefaultHttp3Headers(), 512, true, false);
        sink.accept(Http3Headers.PseudoHeaderName.METHOD.value(), "OPTIONS");
        sink.accept(Http3Headers.PseudoHeaderName.PATH.value(), "/something");
        sink.accept(Http3Headers.PseudoHeaderName.SCHEME.value(), "https");
        sink.accept(Http3Headers.PseudoHeaderName.AUTHORITY.value(), "example.com:4433");
        sink.finish();
    }

    @Test
    public void testOptionsNonWildcardWithHost() throws Http3Exception {
        Http3HeadersSink sink = new Http3HeadersSink(new DefaultHttp3Headers(), 512, true, false);
        sink.accept(Http3Headers.PseudoHeaderName.METHOD.value(), "OPTIONS");
        sink.accept(Http3Headers.PseudoHeaderName.PATH.value(), "/something");
        sink.accept(Http3Headers.PseudoHeaderName.SCHEME.value(), "https");
        sink.accept(new AsciiString(HttpHeaderNames.HOST), "example.com:4433");
        sink.finish();
    }

    @Test
    public void testAuthorityOrHostRequiredForOptionsNonWildcard() throws Http3Exception {
        Http3HeadersSink sink = new Http3HeadersSink(new DefaultHttp3Headers(), 512, true, false);
        sink.accept(Http3Headers.PseudoHeaderName.METHOD.value(), "OPTIONS");
        sink.accept(Http3Headers.PseudoHeaderName.PATH.value(), "/something");
        sink.accept(Http3Headers.PseudoHeaderName.SCHEME.value(), "https");
        assertThrows(Http3HeadersValidationException.class, () -> sink.finish());
    }

    @Test
    public void testHostExistsInsteadOfAuthority() throws Http3Exception {
        Http3HeadersSink sink = new Http3HeadersSink(new DefaultHttp3Headers(), 512, true, false);
        sink.accept(Http3Headers.PseudoHeaderName.METHOD.value(), "GET");
        sink.accept(Http3Headers.PseudoHeaderName.PATH.value(), "/");
        sink.accept(Http3Headers.PseudoHeaderName.SCHEME.value(), "https");
        sink.accept(new AsciiString(HttpHeaderNames.HOST), "example.com:4433");
        sink.finish();
    }

    @ParameterizedTest
    @MethodSource("matchingAuthorityAndHost")
    public void testMatchingAuthorityAndHost(String authority, List<String> hosts) throws Http3Exception {
        Http3HeadersSink sink = newRequestSink(authority, hosts);
        sink.finish();
    }

    @ParameterizedTest
    @MethodSource("conflictingAuthorityAndHost")
    public void testConflictingAuthorityAndHost(String authority, List<String> hosts) {
        Http3HeadersSink sink = newRequestSink(authority, hosts);
        assertThrows(Http3HeadersValidationException.class, () -> sink.finish());
    }

    // :authority, host
    private static Stream<Arguments> matchingAuthorityAndHost() {
        return Stream.of(
            Arguments.of("example.com:4433", singletonList("example.com:4433")),
            Arguments.of("Example.COM:4433", singletonList("example.com:4433")),
            Arguments.of("example.com:4433", asList("example.com:4433", "example.com:4433")),
            Arguments.of(null, singletonList("example.com:4433")),
            Arguments.of(null, asList("example.com:4433", "example.com:4433")));
    }

    // :authority, host
    private static Stream<Arguments> conflictingAuthorityAndHost() {
        return Stream.of(
            Arguments.of("public.example.com", singletonList("internal-admin.local")),
            Arguments.of("public.example.com", asList("public.example.com", "internal-admin.local")),
            Arguments.of("example.com", singletonList("example.com:4433")),
            Arguments.of(null, asList("public.example.com", "internal-admin.local")));
    }

    private static Http3HeadersSink newRequestSink(String authority, List<String> hosts) {
        Http3HeadersSink sink = new Http3HeadersSink(new DefaultHttp3Headers(), 512, true, false);
        sink.accept(Http3Headers.PseudoHeaderName.METHOD.value(), "GET");
        sink.accept(Http3Headers.PseudoHeaderName.PATH.value(), "/");
        sink.accept(Http3Headers.PseudoHeaderName.SCHEME.value(), "https");
        if (authority != null) {
            sink.accept(Http3Headers.PseudoHeaderName.AUTHORITY.value(), authority);
        }
        for (String host : hosts) {
            sink.accept(new AsciiString(HttpHeaderNames.HOST), host);
        }
        return sink;
    }

    @Test
    public void testConflictingAuthorityAndHostForConnect() {
        Http3HeadersSink sink = new Http3HeadersSink(new DefaultHttp3Headers(), 512, true, false);
        sink.accept(Http3Headers.PseudoHeaderName.METHOD.value(), "CONNECT");
        sink.accept(Http3Headers.PseudoHeaderName.AUTHORITY.value(), "public.example.com");
        sink.accept(new AsciiString(HttpHeaderNames.HOST), "internal-admin.local");
        assertThrows(Http3HeadersValidationException.class, () -> sink.finish());
    }

    private static void addMandatoryPseudoHeaders(Http3HeadersSink sink, boolean req) {
        if (req) {
            sink.accept(Http3Headers.PseudoHeaderName.METHOD.value(), "GET");
            sink.accept(Http3Headers.PseudoHeaderName.PATH.value(), "/");
            sink.accept(Http3Headers.PseudoHeaderName.SCHEME.value(), "https");
            sink.accept(Http3Headers.PseudoHeaderName.AUTHORITY.value(), "value");
        } else {
            sink.accept(Http3Headers.PseudoHeaderName.STATUS.value(), "200");
        }
    }
}
