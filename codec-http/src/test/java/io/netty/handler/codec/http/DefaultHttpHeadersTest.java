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

import io.netty.handler.codec.http.HttpHeadersTestUtils.HeaderValue;
import io.netty.util.AsciiString;
import io.netty.util.internal.EmptyArrays;
import io.netty.util.internal.StringUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static io.netty.handler.codec.http.HttpHeaderNames.ACCEPT;
import static io.netty.handler.codec.http.HttpHeaderNames.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderValues.APPLICATION_JSON;
import static io.netty.handler.codec.http.HttpHeaderValues.CLOSE;
import static io.netty.handler.codec.http.HttpHeaderValues.ZERO;
import static io.netty.handler.codec.http.HttpHeadersTestUtils.of;
import static io.netty.util.AsciiString.contentEquals;
import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DefaultHttpHeadersTest {
    private static final CharSequence HEADER_NAME = "testHeader";
    private static final CharSequence ILLEGAL_VALUE = "testHeader\r\nContent-Length:45\r\n\r\n";

    @Test
    public void nullHeaderNameNotAllowed() {
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                new DefaultHttpHeaders().add(null, "foo");
            }
        });
    }

    @Test
    public void emptyHeaderNameNotAllowed() {
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                new DefaultHttpHeaders().add(StringUtil.EMPTY_STRING, "foo");
            }
        });
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
        headers1.add(of("name1"), asList("value1", "value2", "value3"));
        headers1.add(of("nAmE2"), of("value4"));

        DefaultHttpHeaders headers2 = new DefaultHttpHeaders();
        headers2.add(of("naMe1"), asList("value1", "value2", "value3"));
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

    @Test
    public void testSetNullHeaderValueValidate() {
        final HttpHeaders headers = new DefaultHttpHeaders(true);
        assertThrows(NullPointerException.class, new Executable() {
            @Override
            public void execute() {
                headers.set(of("test"), (CharSequence) null);
            }
        });
    }

    @Test
    public void testSetNullHeaderValueNotValidate() {
        final HttpHeaders headers = new DefaultHttpHeaders(false);
        assertThrows(NullPointerException.class, new Executable() {
            @Override
            public void execute() {
                headers.set(of("test"), (CharSequence) null);
            }
        });
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
    public void setCharSequenceValidatesValue() {
        final DefaultHttpHeaders headers = newDefaultDefaultHttpHeaders();
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                headers.set(HEADER_NAME, ILLEGAL_VALUE);
            }
        });
        assertTrue(exception.getMessage().contains(HEADER_NAME));
    }

    @Test
    public void setIterableValidatesValue() {
        final DefaultHttpHeaders headers = newDefaultDefaultHttpHeaders();
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                headers.set(HEADER_NAME, Collections.singleton(ILLEGAL_VALUE));
            }
        });
        assertTrue(exception.getMessage().contains(HEADER_NAME));
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

        String[] namesArray = nettyHeaders.toArray(EmptyArrays.EMPTY_STRINGS);
        assertArrayEquals(namesArray, new String[] { HttpHeaderNames.CONTENT_LENGTH.toString() });
    }

    @Test
    public void names() {
        HttpHeaders headers = new DefaultHttpHeaders(true)
                .add(ACCEPT, APPLICATION_JSON)
                .add(CONTENT_LENGTH, ZERO)
                .add(CONNECTION, CLOSE);
        assertFalse(headers.isEmpty());
        assertEquals(3, headers.size());
        Set<String> names = headers.names();
        assertEquals(3, names.size());
        assertTrue(names.contains(ACCEPT.toString()));
        assertTrue(names.contains(CONTENT_LENGTH.toString()));
        assertTrue(names.contains(CONNECTION.toString()));
    }

    @Test
    public void testContainsName() {
        HttpHeaders headers = new DefaultHttpHeaders(true)
                .add(CONTENT_LENGTH, "36");
        assertTrue(headers.contains("Content-Length"));
        assertTrue(headers.contains("content-length"));
        assertTrue(headers.contains(CONTENT_LENGTH));
        headers.remove(CONTENT_LENGTH);
        assertFalse(headers.contains("Content-Length"));
        assertFalse(headers.contains("content-length"));
        assertFalse(headers.contains(CONTENT_LENGTH));

        assertFalse(headers.contains("non-existent-name"));
        assertFalse(headers.contains(new AsciiString("non-existent-name")));
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
