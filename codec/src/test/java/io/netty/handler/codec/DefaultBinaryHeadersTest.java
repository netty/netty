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
import io.netty.util.AsciiString;
import io.netty.util.ByteString;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Set;

import org.junit.Test;

/**
 * Tests for {@link DefaultBinaryHeaders}.
 */
public class DefaultBinaryHeadersTest {

    @Test
    public void binaryHeadersWithSameValuesShouldBeEquivalent() {
        byte[] key1 = randomBytes();
        byte[] value1 = randomBytes();
        byte[] key2 = randomBytes();
        byte[] value2 = randomBytes();

        DefaultBinaryHeaders h1 = new DefaultBinaryHeaders();
        h1.set(as(key1), as(value1));
        h1.set(as(key2), as(value2));

        DefaultBinaryHeaders h2 = new DefaultBinaryHeaders();
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

        DefaultBinaryHeaders h1 = new DefaultBinaryHeaders();
        h1.set(as(k1), as(v1));
        h1.set(as(k2), as(v2));
        h1.add(as(k2), as(v3));
        h1.add(as(k1), as(v4));

        DefaultBinaryHeaders h2 = new DefaultBinaryHeaders();
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

        DefaultBinaryHeaders h1 = new DefaultBinaryHeaders();
        h1.set(as(k1), as(v1));
        h1.set(as(k2), as(v2));
        h1.add(as(k2), as(v3));
        h1.add(as(k1), as(v4));

        DefaultBinaryHeaders h2 = new DefaultBinaryHeaders();
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

        DefaultBinaryHeaders h1 = new DefaultBinaryHeaders();
        h1.set(as(k1), as(v1));
        h1.set(as(k2), as(v2));
        h1.add(as(k2), as(v3));
        h1.add(as(k1), as(v4));

        DefaultBinaryHeaders h2 = new DefaultBinaryHeaders();
        h2.set(as(k1), as(v1));
        h2.set(as(k2), as(v2));
        h2.add(as(k1), as(v4));

        DefaultBinaryHeaders expected = new DefaultBinaryHeaders();
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

        DefaultBinaryHeaders h1 = new DefaultBinaryHeaders();
        h1.add(as(k1), as(v1));
        h1.add(as(k1), as(v2));
        assertEquals(2, h1.size());

        h1.set(as(k1), as(v3));
        assertEquals(1, h1.size());
        List<ByteString> list = h1.getAll(as(k1));
        assertEquals(1, list.size());
        assertEquals(as(v3), list.get(0));
    }

    @Test
    public void headersWithSameValuesShouldBeEquivalent() {
        DefaultBinaryHeaders h1 = new DefaultBinaryHeaders();
        h1.set(as("foo"), as("goo"));
        h1.set(as("foo2"), as("goo2"));

        DefaultBinaryHeaders h2 = new DefaultBinaryHeaders();
        h2.set(as("foo"), as("goo"));
        h2.set(as("foo2"), as("goo2"));

        assertTrue(h1.equals(h2));
        assertTrue(h2.equals(h1));
        assertTrue(h2.equals(h2));
        assertTrue(h1.equals(h1));
    }

    @Test
    public void headersWithSameDuplicateValuesShouldBeEquivalent() {
        DefaultBinaryHeaders h1 = new DefaultBinaryHeaders();
        h1.set(as("foo"), as("goo"));
        h1.set(as("foo2"), as("goo2"));
        h1.add(as("foo2"), as("goo3"));
        h1.add(as("foo"), as("goo4"));

        DefaultBinaryHeaders h2 = new DefaultBinaryHeaders();
        h2.set(as("foo"), as("goo"));
        h2.set(as("foo2"), as("goo2"));
        h2.add(as("foo"), as("goo4"));
        h2.add(as("foo2"), as("goo3"));

        assertTrue(h1.equals(h2));
        assertTrue(h2.equals(h1));
        assertTrue(h2.equals(h2));
        assertTrue(h1.equals(h1));
    }

    @Test
    public void headersWithDifferentValuesShouldNotBeEquivalent() {
        DefaultBinaryHeaders h1 = new DefaultBinaryHeaders();
        h1.set(as("foo"), as("goo"));
        h1.set(as("foo2"), as("goo2"));
        h1.add(as("foo2"), as("goo3"));
        h1.add(as("foo"), as("goo4"));

        DefaultBinaryHeaders h2 = new DefaultBinaryHeaders();
        h2.set(as("foo"), as("goo"));
        h2.set(as("foo2"), as("goo2"));
        h2.add(as("foo"), as("goo4"));

        assertFalse(h1.equals(h2));
        assertFalse(h2.equals(h1));
        assertTrue(h2.equals(h2));
        assertTrue(h1.equals(h1));
    }

    @Test
    public void setAllShouldMergeHeaders() {
        DefaultBinaryHeaders h1 = new DefaultBinaryHeaders();
        h1.set(as("foo"), as("goo"));
        h1.set(as("foo2"), as("goo2"));
        h1.add(as("foo2"), as("goo3"));
        h1.add(as("foo"), as("goo4"));

        DefaultBinaryHeaders h2 = new DefaultBinaryHeaders();
        h2.set(as("foo"), as("goo"));
        h2.set(as("foo2"), as("goo2"));
        h2.add(as("foo"), as("goo4"));

        DefaultBinaryHeaders expected = new DefaultBinaryHeaders();
        expected.set(as("foo"), as("goo"));
        expected.set(as("foo2"), as("goo2"));
        expected.add(as("foo2"), as("goo3"));
        expected.add(as("foo"), as("goo4"));
        expected.set(as("foo"), as("goo"));
        expected.set(as("foo2"), as("goo2"));
        expected.set(as("foo"), as("goo4"));

        h1.setAll(h2);

        assertEquals(expected, h1);
    }

    @Test
    public void setShouldReplacePreviousValues() {
        DefaultBinaryHeaders h1 = new DefaultBinaryHeaders();
        h1.add(as("foo"), as("goo"));
        h1.add(as("foo"), as("goo2"));
        assertEquals(2, h1.size());

        h1.set(as("foo"), as("goo3"));
        assertEquals(1, h1.size());
        List<ByteString> list = h1.getAll(as("foo"));
        assertEquals(1, list.size());
        assertEquals(as("goo3"), list.get(0));
    }

    @Test(expected = NoSuchElementException.class)
    public void iterateEmptyHeadersShouldThrow() {
        Iterator<Map.Entry<ByteString, ByteString>> iterator = new DefaultBinaryHeaders().iterator();
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
        DefaultBinaryHeaders h1 = new DefaultBinaryHeaders();
        for (String header : headers) {
            String[] parts = header.split(":");
            h1.add(as(parts[0]), as(parts[1]));
        }

        // Now iterate through the headers, removing them from the original set.
        for (Map.Entry<ByteString, ByteString> entry : h1) {
            assertTrue(headers.remove(entry.getKey().toString() + ':' + entry.getValue().toString()));
        }

        // Make sure we removed them all.
        assertTrue(headers.isEmpty());
    }

    @Test
    public void getAndRemoveShouldReturnFirstEntry() {
        DefaultBinaryHeaders h1 = new DefaultBinaryHeaders();
        h1.add(as("foo"), as("goo"));
        h1.add(as("foo"), as("goo2"));
        assertEquals(as("goo"), h1.getAndRemove(as("foo")));
        assertEquals(0, h1.size());
        List<ByteString> values = h1.getAll(as("foo"));
        assertEquals(0, values.size());
    }

    private static byte[] randomBytes() {
        byte[] data = new byte[100];
        new Random().nextBytes(data);
        return data;
    }

    private AsciiString as(byte[] bytes) {
        return new AsciiString(bytes);
    }

    private AsciiString as(String value) {
        return new AsciiString(value);
    }
}
