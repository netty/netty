/*
 * Copyright 2017 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.http2;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.util.AsciiString;
import org.junit.Test;

import static org.junit.Assert.*;

public class HttpConversionUtilTest {
    @Test
    public void setHttp2AuthorityWithoutUserInfo() {
        Http2Headers headers = new DefaultHttp2Headers();

        HttpConversionUtil.setHttp2Authority("foo", headers);
        assertEquals(new AsciiString("foo"), headers.authority());
    }

    @Test
    public void setHttp2AuthorityWithUserInfo() {
        Http2Headers headers = new DefaultHttp2Headers();

        HttpConversionUtil.setHttp2Authority("info@foo", headers);
        assertEquals(new AsciiString("foo"), headers.authority());

        HttpConversionUtil.setHttp2Authority("@foo.bar", headers);
        assertEquals(new AsciiString("foo.bar"), headers.authority());
    }

    @Test
    public void setHttp2AuthorityNullOrEmpty() {
        Http2Headers headers = new DefaultHttp2Headers();

        HttpConversionUtil.setHttp2Authority(null, headers);
        assertNull(headers.authority());

        HttpConversionUtil.setHttp2Authority("", headers);
        assertSame(AsciiString.EMPTY_STRING, headers.authority());
    }

    @Test(expected = IllegalArgumentException.class)
    public void setHttp2AuthorityWithEmptyAuthority() {
        HttpConversionUtil.setHttp2Authority("info@", new DefaultHttp2Headers());
    }

    @Test
    public void stripTEHeaders() {
        HttpHeaders inHeaders = new DefaultHttpHeaders();
        inHeaders.add(HttpHeaderNames.TE, HttpHeaderValues.GZIP);
        Http2Headers out = new DefaultHttp2Headers();
        HttpConversionUtil.toHttp2Headers(inHeaders, out);
        assertTrue(out.isEmpty());
    }

    @Test
    public void stripTEHeadersExcludingTrailers() {
        HttpHeaders inHeaders = new DefaultHttpHeaders();
        inHeaders.add(HttpHeaderNames.TE, HttpHeaderValues.GZIP);
        inHeaders.add(HttpHeaderNames.TE, HttpHeaderValues.TRAILERS);
        Http2Headers out = new DefaultHttp2Headers();
        HttpConversionUtil.toHttp2Headers(inHeaders, out);
        assertSame(HttpHeaderValues.TRAILERS, out.get(HttpHeaderNames.TE));
    }

    @Test
    public void stripConnectionHeadersAndNominees() {
        HttpHeaders inHeaders = new DefaultHttpHeaders();
        inHeaders.add(HttpHeaderNames.CONNECTION, "foo");
        inHeaders.add("foo", "bar");
        Http2Headers out = new DefaultHttp2Headers();
        HttpConversionUtil.toHttp2Headers(inHeaders, out);
        assertTrue(out.isEmpty());
    }

    @Test
    public void stripConnectionNomineesWithCsv() {
        HttpHeaders inHeaders = new DefaultHttpHeaders();
        inHeaders.add(HttpHeaderNames.CONNECTION, "foo,  bar");
        inHeaders.add("foo", "baz");
        inHeaders.add("bar", "qux");
        inHeaders.add("hello", "world");
        Http2Headers out = new DefaultHttp2Headers();
        HttpConversionUtil.toHttp2Headers(inHeaders, out);
        assertEquals(1, out.size());
        assertSame("world", out.get("hello"));
    }
}
