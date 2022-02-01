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
package io.netty.incubator.codec.http3;


import org.junit.jupiter.api.Test;

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
