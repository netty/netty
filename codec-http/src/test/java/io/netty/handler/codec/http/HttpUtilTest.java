/*
 * Copyright 2015 The Netty Project
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
package io.netty.handler.codec.http;

import io.netty.util.CharsetUtil;
import org.junit.Test;

import java.util.List;

import static io.netty.handler.codec.http.HttpHeadersTestUtils.of;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
    public void testGetCharsetAsRawString() {
        HttpMessage message = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        message.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=\"utf8\"");
        assertEquals("\"utf8\"", HttpUtil.getCharsetAsSequence(message));

        message.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html");
        assertNull(HttpUtil.getCharsetAsSequence(message));
    }

    @Test
    public void testGetCharset() {
        HttpMessage message = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        message.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=utf-8");
        assertEquals(CharsetUtil.UTF_8, HttpUtil.getCharset(message));

        message.headers().set(HttpHeaderNames.CONTENT_TYPE, "TEXT/HTML; CHARSET=UTF-8");
        assertEquals(CharsetUtil.UTF_8, HttpUtil.getCharset(message));
    }

    @Test
    public void testGetCharset_defaultValue() {
        HttpMessage message = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        message.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html");
        assertEquals(CharsetUtil.ISO_8859_1, HttpUtil.getCharset(message));

        message.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html");
        assertEquals(CharsetUtil.UTF_8, HttpUtil.getCharset(message, CharsetUtil.UTF_8));

        message.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=UTFFF");
        assertEquals(CharsetUtil.ISO_8859_1, HttpUtil.getCharset(message));

        message.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=UTFFF");
        assertEquals(CharsetUtil.UTF_8, HttpUtil.getCharset(message, CharsetUtil.UTF_8));
    }

    @Test
    public void testGetMimeType() {
        HttpMessage message = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        assertNull(HttpUtil.getMimeType(message));
        message.headers().set(HttpHeaderNames.CONTENT_TYPE, "");
        assertNull(HttpUtil.getMimeType(message));
        message.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html");
        assertEquals("text/html", HttpUtil.getMimeType(message));
        message.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=utf-8");
        assertEquals("text/html", HttpUtil.getMimeType(message));
    }

    @Test
    public void testGetContentLengthDefaultValue() {
        HttpMessage message = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        assertNull(message.headers().get(HttpHeaderNames.CONTENT_LENGTH));
        message.headers().set(HttpHeaderNames.CONTENT_LENGTH, "bar");
        assertEquals("bar", message.headers().get(HttpHeaderNames.CONTENT_LENGTH));
        assertEquals(1L, HttpUtil.getContentLength(message, 1L));
    }
}
