/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Set;

import org.junit.Test;

/**
 * Tests for {@link HeaderMap}.
 */
public class HeaderMapTest {

    @Test
    public void binaryHeadersWithSameValuesShouldBeEquivalent() {
        byte[] key1 = randomBytes();
        byte[] value1 = randomBytes();
        byte[] key2 = randomBytes();
        byte[] value2 = randomBytes();

        HeaderMap h1 = new HeaderMap(false);
        h1.set(as(key1), as(value1));
        h1.set(as(key2), as(value2));

        HeaderMap h2 = new HeaderMap(false);
        h2.set(as(key1), as(value1));
        h2.set(as(key2), as(value2));

        assertTrue(h1.equals(h2));
        assertTrue(h2.equals(h1));
        assertTrue(h2.equals(h2));
        assertTrue(h1.equals(h1));
    }

    @Test
    public void binaryHeadersWithSameDuplicateValuesShouldBeEquivalent() {
        byte[] k1 = randomBytes();
        byte[] k2 = randomBytes();
        byte[] v1 = randomBytes();
        byte[] v2 = randomBytes();
        byte[] v3 = randomBytes();
        byte[] v4 = randomBytes();

        HeaderMap h1 = new HeaderMap(false);
        h1.set(as(k1), as(v1));
        h1.set(as(k2), as(v2));
        h1.add(as(k2), as(v3));
        h1.add(as(k1), as(v4));

        HeaderMap h2 = new HeaderMap(false);
        h2.set(as(k1), as(v1));
        h2.set(as(k2), as(v2));
        h2.add(as(k1), as(v4));
        h2.add(as(k2), as(v3));

        assertTrue(h1.equals(h2));
        assertTrue(h2.equals(h1));
        assertTrue(h2.equals(h2));
        assertTrue(h1.equals(h1));
    }

    @Test
    public void binaryHeadersWithDifferentValuesShouldNotBeEquivalent() {
        byte[] k1 = randomBytes();
        byte[] k2 = randomBytes();
        byte[] v1 = randomBytes();
        byte[] v2 = randomBytes();
        byte[] v3 = randomBytes();
        byte[] v4 = randomBytes();

        HeaderMap h1 = new HeaderMap(false);
        h1.set(as(k1), as(v1));
        h1.set(as(k2), as(v2));
        h1.add(as(k2), as(v3));
        h1.add(as(k1), as(v4));

        HeaderMap h2 = new HeaderMap(false);
        h2.set(as(k1), as(v1));
        h2.set(as(k2), as(v2));
        h2.add(as(k1), as(v4));

        assertFalse(h1.equals(h2));
        assertFalse(h2.equals(h1));
        assertTrue(h2.equals(h2));
        assertTrue(h1.equals(h1));
    }

    @Test
    public void binarySetAllShouldMergeHeaders() {
        byte[] k1 = randomBytes();
        byte[] k2 = randomBytes();
        byte[] v1 = randomBytes();
        byte[] v2 = randomBytes();
        byte[] v3 = randomBytes();
        byte[] v4 = randomBytes();

        HeaderMap h1 = new HeaderMap(false);
        h1.set(as(k1), as(v1));
        h1.set(as(k2), as(v2));
        h1.add(as(k2), as(v3));
        h1.add(as(k1), as(v4));

        HeaderMap h2 = new HeaderMap(false);
        h2.set(as(k1), as(v1));
        h2.set(as(k2), as(v2));
        h2.add(as(k1), as(v4));

        HeaderMap expected = new HeaderMap(false);
        expected.set(as(k1), as(v1));
        expected.set(as(k2), as(v2));
        expected.add(as(k2), as(v3));
        expected.add(as(k1), as(v4));
        expected.set(as(k1), as(v1));
        expected.set(as(k2), as(v2));
        expected.set(as(k1), as(v4));

        h1.setAll(h2);

        assertEquals(expected, h1);
    }

    @Test
    public void binarySetShouldReplacePreviousValues() {
        byte[] k1 = randomBytes();
        byte[] v1 = randomBytes();
        byte[] v2 = randomBytes();
        byte[] v3 = randomBytes();

        HeaderMap h1 = new HeaderMap(false);
        h1.add(as(k1), as(v1));
        h1.add(as(k1), as(v2));
        assertEquals(2, h1.size());

        h1.set(as(k1), as(v3));
        assertEquals(1, h1.size());
        List<CharSequence> list = h1.getAll(as(k1));
        assertEquals(1, list.size());
        assertEquals(as(v3), list.get(0));
    }

    @Test
    public void headersWithSameValuesShouldBeEquivalent() {
        HeaderMap h1 = new HeaderMap();
        h1.set("foo", "goo");
        h1.set("foo2", "goo2");

        HeaderMap h2 = new HeaderMap();
        h2.set("foo", "goo");
        h2.set("foo2", "goo2");

        assertTrue(h1.equals(h2));
        assertTrue(h2.equals(h1));
        assertTrue(h2.equals(h2));
        assertTrue(h1.equals(h1));
    }

    @Test
    public void headersWithSameDuplicateValuesShouldBeEquivalent() {
        HeaderMap h1 = new HeaderMap();
        h1.set("foo", "goo");
        h1.set("foo2", "goo2");
        h1.add("foo2", "goo3");
        h1.add("foo", "goo4");

        HeaderMap h2 = new HeaderMap();
        h2.set("foo", "goo");
        h2.set("foo2", "goo2");
        h2.add("foo", "goo4");
        h2.add("foo2", "goo3");

        assertTrue(h1.equals(h2));
        assertTrue(h2.equals(h1));
        assertTrue(h2.equals(h2));
        assertTrue(h1.equals(h1));
    }

    @Test
    public void headersWithDifferentValuesShouldNotBeEquivalent() {
        HeaderMap h1 = new HeaderMap();
        h1.set("foo", "goo");
        h1.set("foo2", "goo2");
        h1.add("foo2", "goo3");
        h1.add("foo", "goo4");

        HeaderMap h2 = new HeaderMap();
        h2.set("foo", "goo");
        h2.set("foo2", "goo2");
        h2.add("foo", "goo4");

        assertFalse(h1.equals(h2));
        assertFalse(h2.equals(h1));
        assertTrue(h2.equals(h2));
        assertTrue(h1.equals(h1));
    }

    @Test
    public void setAllShouldMergeHeaders() {
        HeaderMap h1 = new HeaderMap();
        h1.set("foo", "goo");
        h1.set("foo2", "goo2");
        h1.add("foo2", "goo3");
        h1.add("foo", "goo4");

        HeaderMap h2 = new HeaderMap();
        h2.set("foo", "goo");
        h2.set("foo2", "goo2");
        h2.add("foo", "goo4");

        HeaderMap expected = new HeaderMap();
        expected.set("foo", "goo");
        expected.set("foo2", "goo2");
        expected.add("foo2", "goo3");
        expected.add("foo", "goo4");
        expected.set("foo", "goo");
        expected.set("foo2", "goo2");
        expected.set("foo", "goo4");

        h1.setAll(h2);

        assertEquals(expected, h1);
    }

    @Test
    public void setShouldReplacePreviousValues() {
        HeaderMap h1 = new HeaderMap();
        h1.add("foo", "goo");
        h1.add("foo", "goo2");
        assertEquals(2, h1.size());

        h1.set("foo", "goo3");
        assertEquals(1, h1.size());
        List<CharSequence> list = h1.getAll("foo");
        assertEquals(1, list.size());
        assertEquals("goo3", list.get(0));
    }

    @Test(expected = NoSuchElementException.class)
    public void iterateEmptyHeadersShouldThrow() {
        Iterator<Map.Entry<CharSequence, CharSequence>> iterator =
                new HeaderMap().iterator();
        assertFalse(iterator.hasNext());
        iterator.next();
    }

    @Test
    public void iterateHeadersShouldReturnAllValues() {
        Set<String> headers = new HashSet<String>();
        headers.add("a:1");
        headers.add("a:2");
        headers.add("a:3");
        headers.add("b:1");
        headers.add("b:2");
        headers.add("c:1");

        // Build the headers from the input set.
        HeaderMap h1 = new HeaderMap();
        for (String header : headers) {
            String[] parts = header.split(":");
            h1.add(parts[0], parts[1]);
        }

        // Now iterate through the headers, removing them from the original set.
        for (Map.Entry<CharSequence, CharSequence> entry : h1) {
            assertTrue(headers
                    .remove(entry.getKey().toString() + ':' + entry.getValue().toString()));
        }

        // Make sure we removed them all.
        assertTrue(headers.isEmpty());
    }

    @Test
    public void getAndRemoveShouldReturnFirstEntry() {
        HeaderMap h1 = new HeaderMap();
        h1.add("foo", "goo");
        h1.add("foo", "goo2");
        assertEquals("goo", h1.getAndRemove("foo"));
        assertEquals(0, h1.size());
        List<CharSequence> values = h1.getAll("foo");
        assertEquals(0, values.size());
    }

    private static byte[] randomBytes() {
        byte[] data = new byte[100];
        new Random().nextBytes(data);
        return data;
    }

    private String as(byte[] bytes) {
        return new String(bytes);
    }
}
