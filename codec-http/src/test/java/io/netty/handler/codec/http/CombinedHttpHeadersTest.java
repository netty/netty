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
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;

import static io.netty.util.AsciiString.contentEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CombinedHttpHeadersTest {
    private static final CharSequence HEADER_NAME = "testHeader";

    @Test
    public void addCharSequencesCsv() {
        final CombinedHttpHeaders headers = newCombinedHttpHeaders();
        headers.add(HEADER_NAME, HeaderValue.THREE.asList());
        assertCsvValues(headers, HeaderValue.THREE);
    }

    @Test
    public void addCharSequencesCsvWithExistingHeader() {
        final CombinedHttpHeaders headers = newCombinedHttpHeaders();
        headers.add(HEADER_NAME, HeaderValue.THREE.asList());
        headers.add(HEADER_NAME, HeaderValue.FIVE.subset(4));
        assertCsvValues(headers, HeaderValue.FIVE);
    }

    @Test
    public void addCombinedHeadersWhenEmpty() {
        final CombinedHttpHeaders headers = newCombinedHttpHeaders();
        final CombinedHttpHeaders otherHeaders = newCombinedHttpHeaders();
        otherHeaders.add(HEADER_NAME, "a");
        otherHeaders.add(HEADER_NAME, "b");
        headers.add(otherHeaders);
        assertEquals("a,b", headers.get(HEADER_NAME).toString());
    }

    @Test
    public void addCombinedHeadersWhenNotEmpty() {
        final CombinedHttpHeaders headers = newCombinedHttpHeaders();
        headers.add(HEADER_NAME, "a");
        final CombinedHttpHeaders otherHeaders = newCombinedHttpHeaders();
        otherHeaders.add(HEADER_NAME, "b");
        otherHeaders.add(HEADER_NAME, "c");
        headers.add(otherHeaders);
        assertEquals("a,b,c", headers.get(HEADER_NAME).toString());
    }

    @Test
    public void setCombinedHeadersWhenNotEmpty() {
        final CombinedHttpHeaders headers = newCombinedHttpHeaders();
        headers.add(HEADER_NAME, "a");
        final CombinedHttpHeaders otherHeaders = newCombinedHttpHeaders();
        otherHeaders.add(HEADER_NAME, "b");
        otherHeaders.add(HEADER_NAME, "c");
        headers.set(otherHeaders);
        assertEquals("b,c", headers.get(HEADER_NAME).toString());
    }

    @Test
    public void addUncombinedHeaders() {
        final CombinedHttpHeaders headers = newCombinedHttpHeaders();
        headers.add(HEADER_NAME, "a");
        final DefaultHttpHeaders otherHeaders = new DefaultHttpHeaders();
        otherHeaders.add(HEADER_NAME, "b");
        otherHeaders.add(HEADER_NAME, "c");
        headers.add(otherHeaders);
        assertEquals("a,b,c", headers.get(HEADER_NAME).toString());
    }

    @Test
    public void setUncombinedHeaders() {
        final CombinedHttpHeaders headers = newCombinedHttpHeaders();
        headers.add(HEADER_NAME, "a");
        final DefaultHttpHeaders otherHeaders = new DefaultHttpHeaders();
        otherHeaders.add(HEADER_NAME, "b");
        otherHeaders.add(HEADER_NAME, "c");
        headers.set(otherHeaders);
        assertEquals("b,c", headers.get(HEADER_NAME).toString());
    }

    @Test
    public void addCharSequencesCsvWithValueContainingComma() {
        final CombinedHttpHeaders headers = newCombinedHttpHeaders();
        headers.add(HEADER_NAME, HeaderValue.SIX_QUOTED.subset(4));
        assertTrue(contentEquals(HeaderValue.SIX_QUOTED.subsetAsCsvString(4), headers.get(HEADER_NAME)));
        assertEquals(HeaderValue.SIX_QUOTED.subset(4), headers.getAll(HEADER_NAME));
    }

    @Test
    public void addCharSequencesCsvWithValueContainingCommas() {
        final CombinedHttpHeaders headers = newCombinedHttpHeaders();
        headers.add(HEADER_NAME, HeaderValue.EIGHT.subset(6));
        assertTrue(contentEquals(HeaderValue.EIGHT.subsetAsCsvString(6), headers.get(HEADER_NAME)));
        assertEquals(HeaderValue.EIGHT.subset(6), headers.getAll(HEADER_NAME));
    }

    @Test (expected = NullPointerException.class)
    public void addCharSequencesCsvNullValue() {
        final CombinedHttpHeaders headers = newCombinedHttpHeaders();
        final String value = null;
        headers.add(HEADER_NAME, value);
    }

    @Test
    public void addCharSequencesCsvMultipleTimes() {
        final CombinedHttpHeaders headers = newCombinedHttpHeaders();
        for (int i = 0; i < 5; ++i) {
            headers.add(HEADER_NAME, "value");
        }
        assertTrue(contentEquals("value,value,value,value,value", headers.get(HEADER_NAME)));
    }

    @Test
    public void addCharSequenceCsv() {
        final CombinedHttpHeaders headers = newCombinedHttpHeaders();
        addValues(headers, HeaderValue.ONE, HeaderValue.TWO, HeaderValue.THREE);
        assertCsvValues(headers, HeaderValue.THREE);
    }

    @Test
    public void addCharSequenceCsvSingleValue() {
        final CombinedHttpHeaders headers = newCombinedHttpHeaders();
        addValues(headers, HeaderValue.ONE);
        assertCsvValue(headers, HeaderValue.ONE);
    }

    @Test
    public void addIterableCsv() {
        final CombinedHttpHeaders headers = newCombinedHttpHeaders();
        headers.add(HEADER_NAME, HeaderValue.THREE.asList());
        assertCsvValues(headers, HeaderValue.THREE);
    }

    @Test
    public void addIterableCsvWithExistingHeader() {
        final CombinedHttpHeaders headers = newCombinedHttpHeaders();
        headers.add(HEADER_NAME, HeaderValue.THREE.asList());
        headers.add(HEADER_NAME, HeaderValue.FIVE.subset(4));
        assertCsvValues(headers, HeaderValue.FIVE);
    }

    @Test
    public void addIterableCsvSingleValue() {
        final CombinedHttpHeaders headers = newCombinedHttpHeaders();
        headers.add(HEADER_NAME, HeaderValue.ONE.asList());
        assertCsvValue(headers, HeaderValue.ONE);
    }

    @Test
    public void addIterableCsvEmpty() {
        final CombinedHttpHeaders headers = newCombinedHttpHeaders();
        headers.add(HEADER_NAME, Collections.<CharSequence>emptyList());
        assertEquals(Arrays.asList(""), headers.getAll(HEADER_NAME));
    }

    @Test
    public void addObjectCsv() {
        final CombinedHttpHeaders headers = newCombinedHttpHeaders();
        addObjectValues(headers, HeaderValue.ONE, HeaderValue.TWO, HeaderValue.THREE);
        assertCsvValues(headers, HeaderValue.THREE);
    }

    @Test
    public void addObjectsCsv() {
        final CombinedHttpHeaders headers = newCombinedHttpHeaders();
        headers.add(HEADER_NAME, HeaderValue.THREE.asList());
        assertCsvValues(headers, HeaderValue.THREE);
    }

    @Test
    public void addObjectsIterableCsv() {
        final CombinedHttpHeaders headers = newCombinedHttpHeaders();
        headers.add(HEADER_NAME, HeaderValue.THREE.asList());
        assertCsvValues(headers, HeaderValue.THREE);
    }

    @Test
    public void addObjectsCsvWithExistingHeader() {
        final CombinedHttpHeaders headers = newCombinedHttpHeaders();
        headers.add(HEADER_NAME, HeaderValue.THREE.asList());
        headers.add(HEADER_NAME, HeaderValue.FIVE.subset(4));
        assertCsvValues(headers, HeaderValue.FIVE);
    }

    @Test
    public void setCharSequenceCsv() {
        final CombinedHttpHeaders headers = newCombinedHttpHeaders();
        headers.set(HEADER_NAME, HeaderValue.THREE.asList());
        assertCsvValues(headers, HeaderValue.THREE);
    }

    @Test
    public void setIterableCsv() {
        final CombinedHttpHeaders headers = newCombinedHttpHeaders();
        headers.set(HEADER_NAME, HeaderValue.THREE.asList());
        assertCsvValues(headers, HeaderValue.THREE);
    }

    @Test
    public void setObjectObjectsCsv() {
        final CombinedHttpHeaders headers = newCombinedHttpHeaders();
        headers.set(HEADER_NAME, HeaderValue.THREE.asList());
        assertCsvValues(headers, HeaderValue.THREE);
    }

    @Test
    public void setObjectIterableCsv() {
        final CombinedHttpHeaders headers = newCombinedHttpHeaders();
        headers.set(HEADER_NAME, HeaderValue.THREE.asList());
        assertCsvValues(headers, HeaderValue.THREE);
    }

    private static CombinedHttpHeaders newCombinedHttpHeaders() {
        return new CombinedHttpHeaders(true);
    }

    private static void assertCsvValues(final CombinedHttpHeaders headers, final HeaderValue headerValue) {
        assertTrue(contentEquals(headerValue.asCsv(), headers.get(HEADER_NAME)));
        assertEquals(headerValue.asList(), headers.getAll(HEADER_NAME));
    }

    private static void assertCsvValue(final CombinedHttpHeaders headers, final HeaderValue headerValue) {
        assertTrue(contentEquals(headerValue.toString(), headers.get(HEADER_NAME)));
        assertTrue(contentEquals(headerValue.toString(), headers.getAll(HEADER_NAME).get(0)));
    }

    private static void addValues(final CombinedHttpHeaders headers, HeaderValue... headerValues) {
        for (HeaderValue v: headerValues) {
            headers.add(HEADER_NAME, v.toString());
        }
    }

    private static void addObjectValues(final CombinedHttpHeaders headers, HeaderValue... headerValues) {
        for (HeaderValue v: headerValues) {
            headers.add(HEADER_NAME, v.toString());
        }
    }

    @Test
    public void testGetAll() {
        final CombinedHttpHeaders headers = newCombinedHttpHeaders();
        headers.set(HEADER_NAME, Arrays.asList("a", "b", "c"));
        assertEquals(Arrays.asList("a", "b", "c"), headers.getAll(HEADER_NAME));
        headers.set(HEADER_NAME, Arrays.asList("a,", "b,", "c,"));
        assertEquals(Arrays.asList("a,", "b,", "c,"), headers.getAll(HEADER_NAME));
        headers.set(HEADER_NAME, Arrays.asList("a\"", "b\"", "c\""));
        assertEquals(Arrays.asList("a\"", "b\"", "c\""), headers.getAll(HEADER_NAME));
        headers.set(HEADER_NAME, Arrays.asList("\"a\"", "\"b\"", "\"c\""));
        assertEquals(Arrays.asList("a", "b", "c"), headers.getAll(HEADER_NAME));
        headers.set(HEADER_NAME, "a,b,c");
        assertEquals(Arrays.asList("a,b,c"), headers.getAll(HEADER_NAME));
        headers.set(HEADER_NAME, "\"a,b,c\"");
        assertEquals(Arrays.asList("a,b,c"), headers.getAll(HEADER_NAME));
    }

    @Test
    public void owsTrimming() {
        final CombinedHttpHeaders headers = newCombinedHttpHeaders();
        headers.set(HEADER_NAME, Arrays.asList("\ta", "   ", "  b ", "\t \t"));
        headers.add(HEADER_NAME, " c, d \t");

        assertEquals(Arrays.asList("a", "", "b", "", "c, d"), headers.getAll(HEADER_NAME));
        assertEquals("a,,b,,\"c, d\"", headers.get(HEADER_NAME));

        assertTrue(headers.containsValue(HEADER_NAME, "a", true));
        assertTrue(headers.containsValue(HEADER_NAME, " a ", true));
        assertTrue(headers.containsValue(HEADER_NAME, "a", true));
        assertFalse(headers.containsValue(HEADER_NAME, "a,b", true));

        assertFalse(headers.containsValue(HEADER_NAME, " c, d ", true));
        assertFalse(headers.containsValue(HEADER_NAME, "c, d", true));
        assertTrue(headers.containsValue(HEADER_NAME, " c ", true));
        assertTrue(headers.containsValue(HEADER_NAME, "d", true));

        assertTrue(headers.containsValue(HEADER_NAME, "\t", true));
        assertTrue(headers.containsValue(HEADER_NAME, "", true));

        assertFalse(headers.containsValue(HEADER_NAME, "e", true));

        HttpHeaders copiedHeaders = newCombinedHttpHeaders().add(headers);
        assertEquals(Arrays.asList("a", "", "b", "", "c, d"), copiedHeaders.getAll(HEADER_NAME));
    }

    @Test
    public void valueIterator() {
        final CombinedHttpHeaders headers = newCombinedHttpHeaders();
        headers.set(HEADER_NAME, Arrays.asList("\ta", "   ", "  b ", "\t \t"));
        headers.add(HEADER_NAME, " c, d \t");

        assertFalse(headers.valueStringIterator("foo").hasNext());
        assertValueIterator(headers.valueStringIterator(HEADER_NAME));
        assertFalse(headers.valueCharSequenceIterator("foo").hasNext());
        assertValueIterator(headers.valueCharSequenceIterator(HEADER_NAME));
    }

    private static void assertValueIterator(Iterator<? extends CharSequence> strItr) {
        assertTrue(strItr.hasNext());
        assertEquals("a", strItr.next());
        assertTrue(strItr.hasNext());
        assertEquals("", strItr.next());
        assertTrue(strItr.hasNext());
        assertEquals("b", strItr.next());
        assertTrue(strItr.hasNext());
        assertEquals("", strItr.next());
        assertTrue(strItr.hasNext());
        assertEquals("c, d", strItr.next());
        assertFalse(strItr.hasNext());
    }
}
