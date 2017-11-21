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

import io.netty.handler.codec.http.HttpHeadersTestUtils.HeaderValue;
import io.netty.util.AsciiString;
import io.netty.util.internal.StringUtil;
import org.junit.Test;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static io.netty.handler.codec.http.HttpHeadersTestUtils.of;
import static io.netty.util.AsciiString.contentEquals;
import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

public class DefaultHttpHeadersTest {
    private static final CharSequence HEADER_NAME = "testHeader";

    @Test(expected = IllegalArgumentException.class)
    public void nullHeaderNameNotAllowed() {
        new DefaultHttpHeaders().add(null, "foo");
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyHeaderNameNotAllowed() {
        new DefaultHttpHeaders().add(StringUtil.EMPTY_STRING, "foo");
    }

    @Test
    public void keysShouldBeCaseInsensitive() {
        DefaultHttpHeaders headers = new DefaultHttpHeaders();
        headers.add(of("Name"), of("value1"));
        headers.add(of("name"), of("value2"));
        headers.add(of("NAME"), of("value3"));
        assertEquals(3, headers.size());

        List<String> values = asList("value1", "value2", "value3");

        assertEquals(values, headers.getAll(of("NAME")));
        assertEquals(values, headers.getAll(of("name")));
        assertEquals(values, headers.getAll(of("Name")));
        assertEquals(values, headers.getAll(of("nAmE")));
    }

    @Test
    public void keysShouldBeCaseInsensitiveInHeadersEquals() {
        DefaultHttpHeaders headers1 = new DefaultHttpHeaders();
        headers1.add(of("name1"), Arrays.asList("value1", "value2", "value3"));
        headers1.add(of("nAmE2"), of("value4"));

        DefaultHttpHeaders headers2 = new DefaultHttpHeaders();
        headers2.add(of("naMe1"), Arrays.asList("value1", "value2", "value3"));
        headers2.add(of("NAME2"), of("value4"));

        assertEquals(headers1, headers1);
        assertEquals(headers2, headers2);
        assertEquals(headers1, headers2);
        assertEquals(headers2, headers1);
        assertEquals(headers1.hashCode(), headers2.hashCode());
    }

    @Test
    public void testStringKeyRetrievedAsAsciiString() {
        final HttpHeaders headers = new DefaultHttpHeaders(false);

        // Test adding String key and retrieving it using a AsciiString key
        final String connection = "keep-alive";
        headers.add(of("Connection"), connection);

        // Passes
        final String value = headers.getAsString(HttpHeaderNames.CONNECTION.toString());
        assertNotNull(value);
        assertEquals(connection, value);

        // Passes
        final String value2 = headers.getAsString(HttpHeaderNames.CONNECTION);
        assertNotNull(value2);
        assertEquals(connection, value2);
    }

    @Test
    public void testAsciiStringKeyRetrievedAsString() {
        final HttpHeaders headers = new DefaultHttpHeaders(false);

        // Test adding AsciiString key and retrieving it using a String key
        final String cacheControl = "no-cache";
        headers.add(HttpHeaderNames.CACHE_CONTROL, cacheControl);

        final String value = headers.getAsString(HttpHeaderNames.CACHE_CONTROL);
        assertNotNull(value);
        assertEquals(cacheControl, value);

        final String value2 = headers.getAsString(HttpHeaderNames.CACHE_CONTROL.toString());
        assertNotNull(value2);
        assertEquals(cacheControl, value2);
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
    public void testEqualsIgnoreCase() {
        assertThat(AsciiString.contentEqualsIgnoreCase(null, null), is(true));
        assertThat(AsciiString.contentEqualsIgnoreCase(null, "foo"), is(false));
        assertThat(AsciiString.contentEqualsIgnoreCase("bar", null), is(false));
        assertThat(AsciiString.contentEqualsIgnoreCase("FoO", "fOo"), is(true));
    }

    @Test(expected = NullPointerException.class)
    public void testSetNullHeaderValueValidate() {
        HttpHeaders headers = new DefaultHttpHeaders(true);
        headers.set(of("test"), (CharSequence) null);
    }

    @Test(expected = NullPointerException.class)
    public void testSetNullHeaderValueNotValidate() {
        HttpHeaders headers = new DefaultHttpHeaders(false);
        headers.set(of("test"), (CharSequence) null);
    }

    @Test
    public void addCharSequences() {
        final DefaultHttpHeaders headers = newDefaultDefaultHttpHeaders();
        headers.add(HEADER_NAME, HeaderValue.THREE.asList());
        assertDefaultValues(headers, HeaderValue.THREE);
    }

    @Test
    public void addIterable() {
        final DefaultHttpHeaders headers = newDefaultDefaultHttpHeaders();
        headers.add(HEADER_NAME, HeaderValue.THREE.asList());
        assertDefaultValues(headers, HeaderValue.THREE);
    }

    @Test
    public void addObjects() {
        final DefaultHttpHeaders headers = newDefaultDefaultHttpHeaders();
        headers.add(HEADER_NAME, HeaderValue.THREE.asList());
        assertDefaultValues(headers, HeaderValue.THREE);
    }

    @Test
    public void setCharSequences() {
        final DefaultHttpHeaders headers = newDefaultDefaultHttpHeaders();
        headers.set(HEADER_NAME, HeaderValue.THREE.asList());
        assertDefaultValues(headers, HeaderValue.THREE);
    }

    @Test
    public void setIterable() {
        final DefaultHttpHeaders headers = newDefaultDefaultHttpHeaders();
        headers.set(HEADER_NAME, HeaderValue.THREE.asList());
        assertDefaultValues(headers, HeaderValue.THREE);
    }

    @Test
    public void setObjectObjects() {
        final DefaultHttpHeaders headers = newDefaultDefaultHttpHeaders();
        headers.set(HEADER_NAME, HeaderValue.THREE.asList());
        assertDefaultValues(headers, HeaderValue.THREE);
    }

    @Test
    public void setObjectIterable() {
        final DefaultHttpHeaders headers = newDefaultDefaultHttpHeaders();
        headers.set(HEADER_NAME, HeaderValue.THREE.asList());
        assertDefaultValues(headers, HeaderValue.THREE);
    }

    @Test
    public void toStringOnEmptyHeaders() {
        assertEquals("DefaultHttpHeaders[]", newDefaultDefaultHttpHeaders().toString());
    }

    @Test
    public void toStringOnSingleHeader() {
        assertEquals("DefaultHttpHeaders[foo: bar]", newDefaultDefaultHttpHeaders()
                .add("foo", "bar")
                .toString());
    }

    @Test
    public void toStringOnMultipleHeaders() {
        assertEquals("DefaultHttpHeaders[foo: bar, baz: qix]", newDefaultDefaultHttpHeaders()
                .add("foo", "bar")
                .add("baz", "qix")
                .toString());
    }

    @Test
    public void providesHeaderNamesAsArray() throws Exception {
        Set<String> nettyHeaders = new DefaultHttpHeaders()
                .add(HttpHeaderNames.CONTENT_LENGTH, 10)
                .names();

        String[] namesArray = nettyHeaders.toArray(new String[nettyHeaders.size()]);
        assertArrayEquals(namesArray, new String[] { HttpHeaderNames.CONTENT_LENGTH.toString() });
    }

    private static void assertDefaultValues(final DefaultHttpHeaders headers, final HeaderValue headerValue) {
        assertTrue(contentEquals(headerValue.asList().get(0), headers.get(HEADER_NAME)));
        List<CharSequence> expected = headerValue.asList();
        List<String> actual = headers.getAll(HEADER_NAME);
        assertEquals(expected.size(), actual.size());
        Iterator<CharSequence> eItr = expected.iterator();
        Iterator<String> aItr = actual.iterator();
        while (eItr.hasNext()) {
            assertTrue(contentEquals(eItr.next(), aItr.next()));
        }
    }

    private static DefaultHttpHeaders newDefaultDefaultHttpHeaders() {
        return new DefaultHttpHeaders(true);
    }
}
