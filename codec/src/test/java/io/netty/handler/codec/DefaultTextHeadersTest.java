/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec;

import static org.junit.Assert.assertEquals;
import static io.netty.util.internal.StringUtil.COMMA;
import static io.netty.util.internal.StringUtil.DOUBLE_QUOTE;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class DefaultTextHeadersTest {

    private static final String HEADER_NAME = "testHeader";

    @Test
    public void addCharSequences() {
        final TextHeaders headers = newDefaultTextHeaders();
        headers.add(HEADER_NAME, HeaderValue.THREE.asArray());
        assertDefaultValues(headers, HeaderValue.THREE);
    }

    @Test
    public void addCharSequencesCsv() {
        final TextHeaders headers = newCsvTextHeaders();
        headers.add(HEADER_NAME, HeaderValue.THREE.asArray());
        assertCsvValues(headers, HeaderValue.THREE);
    }

    @Test
    public void addCharSequencesCsvWithExistingHeader() {
        final TextHeaders headers = newCsvTextHeaders();
        headers.add(HEADER_NAME, HeaderValue.THREE.asArray());
        headers.add(HEADER_NAME, HeaderValue.FIVE.subset(4));
        assertCsvValues(headers, HeaderValue.FIVE);
    }

    @Test
    public void addCharSequencesCsvWithValueContainingComma() {
        final TextHeaders headers = newCsvTextHeaders();
        headers.add(HEADER_NAME, HeaderValue.SIX_QUOTED.subset(4));
        assertEquals(HeaderValue.SIX_QUOTED.subsetAsCsvString(4), headers.getAndConvert(HEADER_NAME));
        assertEquals(HeaderValue.SIX_QUOTED.subsetAsCsvString(4), headers.getAllAndConvert(HEADER_NAME).get(0));
    }

    @Test
    public void addCharSequencesCsvWithValueContainingCommas() {
        final TextHeaders headers = newCsvTextHeaders();
        headers.add(HEADER_NAME, HeaderValue.EIGHT.subset(6));
        assertEquals(HeaderValue.EIGHT.subsetAsCsvString(6), headers.getAndConvert(HEADER_NAME));
        assertEquals(HeaderValue.EIGHT.subsetAsCsvString(6), headers.getAllAndConvert(HEADER_NAME).get(0));
    }

    @Test (expected = NullPointerException.class)
    public void addCharSequencesCsvNullValue() {
        final TextHeaders headers = newCsvTextHeaders();
        final String value = null;
        headers.add(HEADER_NAME, value);
    }

    @Test
    public void addCharSequencesCsvMultipleTimes() {
        final TextHeaders headers = newCsvTextHeaders();
        for (int i = 0; i < 5; ++i) {
            headers.add(HEADER_NAME, "value");
        }
        assertEquals("value,value,value,value,value", headers.getAndConvert(HEADER_NAME));
    }

    @Test
    public void addCharSequenceCsv() {
        final TextHeaders headers = newCsvTextHeaders();
        addValues(headers, HeaderValue.ONE, HeaderValue.TWO, HeaderValue.THREE);
        assertCsvValues(headers, HeaderValue.THREE);
    }

    @Test
    public void addCharSequenceCsvSingleValue() {
        final TextHeaders headers = newCsvTextHeaders();
        addValues(headers, HeaderValue.ONE);
        assertCsvValue(headers, HeaderValue.ONE);
    }

    @Test
    public void addIterable() {
        final TextHeaders headers = newDefaultTextHeaders();
        headers.add(HEADER_NAME, HeaderValue.THREE.asList());
        assertDefaultValues(headers, HeaderValue.THREE);
    }

    @Test
    public void addIterableCsv() {
        final TextHeaders headers = newCsvTextHeaders();
        headers.add(HEADER_NAME, HeaderValue.THREE.asList());
        assertCsvValues(headers, HeaderValue.THREE);
    }

    @Test
    public void addIterableCsvWithExistingHeader() {
        final TextHeaders headers = newCsvTextHeaders();
        headers.add(HEADER_NAME, HeaderValue.THREE.asArray());
        headers.add(HEADER_NAME, HeaderValue.FIVE.subset(4));
        assertCsvValues(headers, HeaderValue.FIVE);
    }

    @Test
    public void addIterableCsvSingleValue() {
        final TextHeaders headers = newCsvTextHeaders();
        headers.add(HEADER_NAME, HeaderValue.ONE.asList());
        assertCsvValue(headers, HeaderValue.ONE);
    }

    @Test
    public void addIterableCsvEmtpy() {
        final TextHeaders headers = newCsvTextHeaders();
        headers.add(HEADER_NAME, Collections.<CharSequence>emptyList());
        assertEquals("", headers.getAllAndConvert(HEADER_NAME).get(0));
    }

    @Test
    public void addObjectCsv() {
        final TextHeaders headers = newCsvTextHeaders();
        addObjectValues(headers, HeaderValue.ONE, HeaderValue.TWO, HeaderValue.THREE);
        assertCsvValues(headers, HeaderValue.THREE);
    }

    @Test
    public void addObjects() {
        final TextHeaders headers = newDefaultTextHeaders();
        headers.addObject(HEADER_NAME, HeaderValue.THREE.asArray());
        assertDefaultValues(headers, HeaderValue.THREE);
    }

    @Test
    public void addObjectsCsv() {
        final TextHeaders headers = newCsvTextHeaders();
        headers.addObject(HEADER_NAME, HeaderValue.THREE.asArray());
        assertCsvValues(headers, HeaderValue.THREE);
    }

    @Test
    public void addObjectsIterableCsv() {
        final TextHeaders headers = newCsvTextHeaders();
        headers.addObject(HEADER_NAME, HeaderValue.THREE.asList());
        assertCsvValues(headers, HeaderValue.THREE);
    }

    @Test
    public void addObjectsCsvWithExistingHeader() {
        final TextHeaders headers = newCsvTextHeaders();
        headers.addObject(HEADER_NAME, HeaderValue.THREE.asArray());
        headers.addObject(HEADER_NAME, HeaderValue.FIVE.subset(4));
        assertCsvValues(headers, HeaderValue.FIVE);
    }

    @Test
    public void setCharSequences() {
        final TextHeaders headers = newDefaultTextHeaders();
        headers.set(HEADER_NAME, HeaderValue.THREE.asArray());
        assertDefaultValues(headers, HeaderValue.THREE);
    }

    @Test
    public void setCharSequenceCsv() {
        final TextHeaders headers = newCsvTextHeaders();
        headers.set(HEADER_NAME, HeaderValue.THREE.asArray());
        assertCsvValues(headers, HeaderValue.THREE);
    }

    @Test
    public void setIterable() {
        final TextHeaders headers = newDefaultTextHeaders();
        headers.set(HEADER_NAME, HeaderValue.THREE.asList());
        assertDefaultValues(headers, HeaderValue.THREE);
    }

    @Test
    public void setIterableCsv() {
        final TextHeaders headers = newCsvTextHeaders();
        headers.set(HEADER_NAME, HeaderValue.THREE.asList());
        assertCsvValues(headers, HeaderValue.THREE);
    }

    @Test
    public void setObjectObjects() {
        final TextHeaders headers = newDefaultTextHeaders();
        headers.setObject(HEADER_NAME, HeaderValue.THREE.asArray());
        assertDefaultValues(headers, HeaderValue.THREE);
    }

    @Test
    public void setObjectObjectsCsv() {
        final TextHeaders headers = newCsvTextHeaders();
        headers.setObject(HEADER_NAME, HeaderValue.THREE.asArray());
        assertCsvValues(headers, HeaderValue.THREE);
    }

    @Test
    public void setObjectIterable() {
        final TextHeaders headers = newDefaultTextHeaders();
        headers.setObject(HEADER_NAME, HeaderValue.THREE.asList());
        assertDefaultValues(headers, HeaderValue.THREE);
    }

    @Test
    public void setObjectIterableCsv() {
        final TextHeaders headers = newCsvTextHeaders();
        headers.setObject(HEADER_NAME, HeaderValue.THREE.asList());
        assertCsvValues(headers, HeaderValue.THREE);
    }

    private static void assertDefaultValues(final TextHeaders headers, final HeaderValue headerValue) {
        assertEquals(headerValue.asArray()[0], headers.get(HEADER_NAME));
        assertEquals(headerValue.asList(), headers.getAll(HEADER_NAME));
    }

    private static void assertCsvValues(final TextHeaders headers, final HeaderValue headerValue) {
        assertEquals(headerValue.asCsv(), headers.getAndConvert(HEADER_NAME));
        assertEquals(headerValue.asCsv(), headers.getAllAndConvert(HEADER_NAME).get(0));
    }

    private static void assertCsvValue(final TextHeaders headers, final HeaderValue headerValue) {
        assertEquals(headerValue.toString(), headers.getAndConvert(HEADER_NAME));
        assertEquals(headerValue.toString(), headers.getAllAndConvert(HEADER_NAME).get(0));
    }

    private static TextHeaders newDefaultTextHeaders() {
        return new DefaultTextHeaders(false);
    }

    private static TextHeaders newCsvTextHeaders() {
        return new DefaultTextHeaders(true);
    }

    private static void addValues(final TextHeaders headers, HeaderValue... headerValues) {
        for (HeaderValue v: headerValues) {
            headers.add(HEADER_NAME, v.toString());
        }
    }

    private static void addObjectValues(final TextHeaders headers, HeaderValue... headerValues) {
        for (HeaderValue v: headerValues) {
            headers.addObject(HEADER_NAME, v.toString());
        }
    }

    private enum HeaderValue {
        UNKNOWN("unknown", 0),
        ONE("one", 1),
        TWO("two", 2),
        THREE("three", 3),
        FOUR("four", 4),
        FIVE("five", 5),
        SIX_QUOTED("six,", 6),
        SEVEN_QUOTED("seven; , GMT", 7),
        EIGHT("eight", 8);

        private final int nr;
        private final String value;
        private String[] array;
        private static final String DOUBLE_QUOTE_STRING = String.valueOf(DOUBLE_QUOTE);

        HeaderValue(final String value, final int nr) {
            this.nr = nr;
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }

        public String[] asArray() {
            if (array == null) {
                final String[] arr = new String[nr];
                for (int i = 1, y = 0; i <= nr; i++, y++) {
                    arr[y] = of(i).toString();
                }
                array = arr;
            }
            return array;
        }

        public String[] subset(final int from) {
            final int size = from - 1;
            final String[] arr = new String[nr - size];
            System.arraycopy(asArray(), size, arr, 0, arr.length);
            return arr;
        }

        public String subsetAsCsvString(final int from) {
            final String[] subset = subset(from);
            return asCsv(subset);
        }

        public List<CharSequence> asList() {
            return Arrays.<CharSequence>asList(asArray());
        }

        public String asCsv(final String[] arr) {
            final StringBuilder sb = new StringBuilder();
            int end = arr.length - 1;
            for (int i = 0; i < end; i++) {
                final String value = arr[i];
                quoted(sb, value).append(COMMA);
            }
            quoted(sb, arr[end]);
            return sb.toString();
        }

        public String asCsv() {
            return asCsv(asArray());
        }

        private static StringBuilder quoted(final StringBuilder sb, final String value) {
            if (value.contains(String.valueOf(COMMA)) && !value.contains(DOUBLE_QUOTE_STRING)) {
                return sb.append(DOUBLE_QUOTE).append(value).append(DOUBLE_QUOTE);
            }
            return sb.append(value);
        }

        public static String quoted(final String value) {
            return quoted(new StringBuilder(), value).toString();
        }

        private static final Map<Integer, HeaderValue> MAP;

        static {
            final Map<Integer, HeaderValue> map = new HashMap<Integer, HeaderValue>();
            for (HeaderValue v : values()) {
                final int nr = v.nr;
                map.put(Integer.valueOf(nr), v);
            }
            MAP = map;
        }

        public static HeaderValue of(final int nr) {
            final HeaderValue v = MAP.get(Integer.valueOf(nr));
            return v == null ? UNKNOWN : v;
        }
    }
}
