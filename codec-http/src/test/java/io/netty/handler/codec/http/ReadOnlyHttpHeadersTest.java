/*
 * Copyright 2017 The Netty Project
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
import org.junit.Test;

import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import static io.netty.handler.codec.http.HttpHeaderNames.ACCEPT;
import static io.netty.handler.codec.http.HttpHeaderNames.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderValues.APPLICATION_JSON;
import static io.netty.handler.codec.http.HttpHeaderValues.APPLICATION_OCTET_STREAM;
import static io.netty.handler.codec.http.HttpHeaderValues.CLOSE;
import static io.netty.handler.codec.http.HttpHeaderValues.ZERO;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ReadOnlyHttpHeadersTest {
    @Test
    public void getValue() {
        ReadOnlyHttpHeaders headers = new ReadOnlyHttpHeaders(true,
                ACCEPT, APPLICATION_JSON);
        assertFalse(headers.isEmpty());
        assertEquals(1, headers.size());
        assertTrue(APPLICATION_JSON.contentEquals(headers.get(ACCEPT)));
        assertTrue(headers.contains(ACCEPT));
        assertNull(headers.get(CONTENT_LENGTH));
        assertFalse(headers.contains(CONTENT_LENGTH));
    }

    @Test
    public void charSequenceIterator() {
        ReadOnlyHttpHeaders headers = new ReadOnlyHttpHeaders(true,
                ACCEPT, APPLICATION_JSON, CONTENT_LENGTH, ZERO, CONNECTION, CLOSE);
        assertFalse(headers.isEmpty());
        assertEquals(3, headers.size());
        Iterator<Entry<CharSequence, CharSequence>> itr = headers.iteratorCharSequence();
        assertTrue(itr.hasNext());
        Entry<CharSequence, CharSequence> next = itr.next();
        assertTrue(ACCEPT.contentEqualsIgnoreCase(next.getKey()));
        assertTrue(APPLICATION_JSON.contentEqualsIgnoreCase(next.getValue()));
        assertTrue(itr.hasNext());
        next = itr.next();
        assertTrue(CONTENT_LENGTH.contentEqualsIgnoreCase(next.getKey()));
        assertTrue(ZERO.contentEqualsIgnoreCase(next.getValue()));
        assertTrue(itr.hasNext());
        next = itr.next();
        assertTrue(CONNECTION.contentEqualsIgnoreCase(next.getKey()));
        assertTrue(CLOSE.contentEqualsIgnoreCase(next.getValue()));
        assertFalse(itr.hasNext());
    }

    @Test
    public void stringIterator() {
        ReadOnlyHttpHeaders headers = new ReadOnlyHttpHeaders(true,
                ACCEPT, APPLICATION_JSON, CONTENT_LENGTH, ZERO, CONNECTION, CLOSE);
        assertFalse(headers.isEmpty());
        assertEquals(3, headers.size());
        assert3ParisEquals(headers.iterator());
    }

    @Test
    public void entries() {
        ReadOnlyHttpHeaders headers = new ReadOnlyHttpHeaders(true,
                ACCEPT, APPLICATION_JSON, CONTENT_LENGTH, ZERO, CONNECTION, CLOSE);
        assertFalse(headers.isEmpty());
        assertEquals(3, headers.size());
        assert3ParisEquals(headers.entries().iterator());
    }

    @Test
    public void names() {
        ReadOnlyHttpHeaders headers = new ReadOnlyHttpHeaders(true,
                ACCEPT, APPLICATION_JSON, CONTENT_LENGTH, ZERO, CONNECTION, CLOSE);
        assertFalse(headers.isEmpty());
        assertEquals(3, headers.size());
        Set<String> names = headers.names();
        assertEquals(3, names.size());
        assertTrue(names.contains(ACCEPT.toString()));
        assertTrue(names.contains(CONTENT_LENGTH.toString()));
        assertTrue(names.contains(CONNECTION.toString()));
    }

    @Test
    public void getAll() {
        ReadOnlyHttpHeaders headers = new ReadOnlyHttpHeaders(false,
                ACCEPT, APPLICATION_JSON, CONTENT_LENGTH, ZERO, ACCEPT, APPLICATION_OCTET_STREAM);
        assertFalse(headers.isEmpty());
        assertEquals(3, headers.size());
        List<String> names = headers.getAll(ACCEPT);
        assertEquals(2, names.size());
        assertTrue(APPLICATION_JSON.contentEqualsIgnoreCase(names.get(0)));
        assertTrue(APPLICATION_OCTET_STREAM.contentEqualsIgnoreCase(names.get(1)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void validateNamesFail() {
        new ReadOnlyHttpHeaders(true,
                ACCEPT, APPLICATION_JSON, AsciiString.cached(" "));
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyHeaderName() {
        new ReadOnlyHttpHeaders(true,
                                ACCEPT, APPLICATION_JSON, AsciiString.cached(" "), ZERO);
    }

    @Test(expected = IllegalArgumentException.class)
    public void headerWithoutValue() {
        new ReadOnlyHttpHeaders(false,
                                ACCEPT, APPLICATION_JSON, CONTENT_LENGTH);
    }

    private static void assert3ParisEquals(Iterator<Entry<String, String>> itr) {
        assertTrue(itr.hasNext());
        Entry<String, String> next = itr.next();
        assertTrue(ACCEPT.contentEqualsIgnoreCase(next.getKey()));
        assertTrue(APPLICATION_JSON.contentEqualsIgnoreCase(next.getValue()));
        assertTrue(itr.hasNext());
        next = itr.next();
        assertTrue(CONTENT_LENGTH.contentEqualsIgnoreCase(next.getKey()));
        assertTrue(ZERO.contentEqualsIgnoreCase(next.getValue()));
        assertTrue(itr.hasNext());
        next = itr.next();
        assertTrue(CONNECTION.contentEqualsIgnoreCase(next.getKey()));
        assertTrue(CLOSE.contentEqualsIgnoreCase(next.getValue()));
        assertFalse(itr.hasNext());
    }
}
