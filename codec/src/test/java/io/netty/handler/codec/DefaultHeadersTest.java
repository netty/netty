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

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;

import org.junit.Test;

import io.netty.util.ByteString;
import io.netty.util.CharsetUtil;

/**
 * Tests for {@link DefaultHeaders}.
 */
public class DefaultHeadersTest {

    private Headers<ByteString> newInstance() {
        return new DefaultHeaders<ByteString>(ByteStringValueConverter.INSTANCE);
    }

    @Test
    public void addShouldIncreaseAndRemoveShouldDecreaseTheSize() {
        Headers<ByteString> headers = newInstance();
        assertEquals(0, headers.size());
        headers.add(bs("name1"), bs("value1"), bs("value2"));
        assertEquals(2, headers.size());
        headers.add(bs("name2"), bs("value3"), bs("value4"));
        assertEquals(4, headers.size());
        headers.add(bs("name3"), bs("value5"));
        assertEquals(5, headers.size());

        headers.remove(bs("name3"));
        assertEquals(4, headers.size());
        headers.remove(bs("name1"));
        assertEquals(2, headers.size());
        headers.remove(bs("name2"));
        assertEquals(0, headers.size());
        assertTrue(headers.isEmpty());
    }

    @Test
    public void afterClearHeadersShouldBeEmpty() {
        Headers<ByteString> headers = newInstance();
        headers.add(bs("name1"), bs("value1"));
        headers.add(bs("name2"), bs("value2"));
        assertEquals(2, headers.size());
        headers.clear();
        assertEquals(0, headers.size());
        assertTrue(headers.isEmpty());
        assertFalse(headers.contains(bs("name1")));
        assertFalse(headers.contains(bs("name2")));
    }

    @Test
    public void removingANameForASecondTimeShouldReturnFalse() {
        Headers<ByteString> headers = newInstance();
        headers.add(bs("name1"), bs("value1"));
        headers.add(bs("name2"), bs("value2"));
        assertTrue(headers.remove(bs("name2")));
        assertFalse(headers.remove(bs("name2")));
    }

    @Test
    public void multipleValuesPerNameShouldBeAllowed() {
        Headers<ByteString> headers = newInstance();
        headers.add(bs("name"), bs("value1"));
        headers.add(bs("name"), bs("value2"));
        headers.add(bs("name"), bs("value3"));
        assertEquals(3, headers.size());

        List<ByteString> values = headers.getAll(bs("name"));
        assertEquals(3, values.size());
        assertTrue(values.containsAll(asList(bs("value1"), bs("value2"), bs("value3"))));
    }

    @Test
    public void testContains() {
        Headers<ByteString> headers = newInstance();
        headers.addBoolean(bs("boolean"), true);
        assertTrue(headers.containsBoolean(bs("boolean"), true));
        assertFalse(headers.containsBoolean(bs("boolean"), false));

        headers.addLong(bs("long"), Long.MAX_VALUE);
        assertTrue(headers.containsLong(bs("long"), Long.MAX_VALUE));
        assertFalse(headers.containsLong(bs("long"), Long.MIN_VALUE));

        headers.addInt(bs("int"), Integer.MIN_VALUE);
        assertTrue(headers.containsInt(bs("int"), Integer.MIN_VALUE));
        assertFalse(headers.containsInt(bs("int"), Integer.MAX_VALUE));

        headers.addShort(bs("short"), Short.MAX_VALUE);
        assertTrue(headers.containsShort(bs("short"), Short.MAX_VALUE));
        assertFalse(headers.containsShort(bs("short"), Short.MIN_VALUE));

        headers.addChar(bs("char"), Character.MAX_VALUE);
        assertTrue(headers.containsChar(bs("char"), Character.MAX_VALUE));
        assertFalse(headers.containsChar(bs("char"), Character.MIN_VALUE));

        headers.addByte(bs("byte"), Byte.MAX_VALUE);
        assertTrue(headers.containsByte(bs("byte"), Byte.MAX_VALUE));
        assertFalse(headers.containsLong(bs("byte"), Byte.MIN_VALUE));

        headers.addDouble(bs("double"), Double.MAX_VALUE);
        assertTrue(headers.containsDouble(bs("double"), Double.MAX_VALUE));
        assertFalse(headers.containsDouble(bs("double"), Double.MIN_VALUE));

        headers.addFloat(bs("float"), Float.MAX_VALUE);
        assertTrue(headers.containsFloat(bs("float"), Float.MAX_VALUE));
        assertFalse(headers.containsFloat(bs("float"), Float.MIN_VALUE));

        long millis = System.currentTimeMillis();
        headers.addTimeMillis(bs("millis"), millis);
        assertTrue(headers.containsTimeMillis(bs("millis"), millis));
        // This test doesn't work on midnight, January 1, 1970 UTC
        assertFalse(headers.containsTimeMillis(bs("millis"), 0));

        headers.addObject(bs("object"), "Hello World");
        assertTrue(headers.containsObject(bs("object"), "Hello World"));
        assertFalse(headers.containsObject(bs("object"), ""));

        headers.add(bs("name"), bs("value"));
        assertTrue(headers.contains(bs("name"), bs("value")));
        assertFalse(headers.contains(bs("name"), bs("value1")));
    }

    @Test
    public void canMixConvertedAndNormalValues() {
        Headers<ByteString> headers = newInstance();
        headers.add(bs("name"), bs("value"));
        headers.addInt(bs("name"), 100);
        headers.addBoolean(bs("name"), false);

        assertEquals(3, headers.size());
        assertTrue(headers.contains(bs("name")));
        assertTrue(headers.contains(bs("name"), bs("value")));
        assertTrue(headers.containsInt(bs("name"), 100));
        assertTrue(headers.containsBoolean(bs("name"), false));
    }

    @Test
    public void testGetAndRemove() {
        Headers<ByteString> headers = newInstance();
        headers.add(bs("name1"), bs("value1"));
        headers.add(bs("name2"), bs("value2"), bs("value3"));
        headers.add(bs("name3"), bs("value4"), bs("value5"), bs("value6"));

        assertEquals(bs("value1"), headers.getAndRemove(bs("name1"), bs("defaultvalue")));
        assertEquals(bs("value2"), headers.getAndRemove(bs("name2")));
        assertNull(headers.getAndRemove(bs("name2")));
        assertEquals(asList(bs("value4"), bs("value5"), bs("value6")), headers.getAllAndRemove(bs("name3")));
        assertEquals(0, headers.size());
        assertNull(headers.getAndRemove(bs("noname")));
        assertEquals(bs("defaultvalue"), headers.getAndRemove(bs("noname"), bs("defaultvalue")));
    }

    @Test
    public void whenNameContainsMultipleValuesGetShouldReturnTheFirst() {
        Headers<ByteString> headers = newInstance();
        headers.add(bs("name1"), bs("value1"), bs("value2"));
        assertEquals(bs("value1"), headers.get(bs("name1")));
    }

    @Test
    public void getWithDefaultValueWorks() {
        Headers<ByteString> headers = newInstance();
        headers.add(bs("name1"), bs("value1"));

        assertEquals(bs("value1"), headers.get(bs("name1"), bs("defaultvalue")));
        assertEquals(bs("defaultvalue"), headers.get(bs("noname"), bs("defaultvalue")));
    }

    @Test
    public void setShouldOverWritePreviousValue() {
        Headers<ByteString> headers = newInstance();
        headers.set(bs("name"), bs("value1"));
        headers.set(bs("name"), bs("value2"));
        assertEquals(1, headers.size());
        assertEquals(1, headers.getAll(bs("name")).size());
        assertEquals(bs("value2"), headers.getAll(bs("name")).get(0));
        assertEquals(bs("value2"), headers.get(bs("name")));
    }

    @Test
    public void setAllShouldOverwriteSomeAndLeaveOthersUntouched() {
        Headers<ByteString> h1 = newInstance();

        h1.add(bs("name1"), bs("value1"));
        h1.add(bs("name2"), bs("value2"));
        h1.add(bs("name2"), bs("value3"));
        h1.add(bs("name3"), bs("value4"));

        Headers<ByteString> h2 = newInstance();
        h2.add(bs("name1"), bs("value5"));
        h2.add(bs("name2"), bs("value6"));
        h2.add(bs("name1"), bs("value7"));

        Headers<ByteString> expected = newInstance();
        expected.add(bs("name1"), bs("value5"));
        expected.add(bs("name2"), bs("value6"));
        expected.add(bs("name1"), bs("value7"));
        expected.add(bs("name3"), bs("value4"));

        h1.setAll(h2);

        assertEquals(expected, h1);
    }

    @Test
    public void headersWithSameNamesAndValuesShouldBeEquivalent() {
        Headers<ByteString> headers1 = newInstance();
        headers1.add(bs("name1"), bs("value1"));
        headers1.add(bs("name2"), bs("value2"));
        headers1.add(bs("name2"), bs("value3"));

        Headers<ByteString> headers2 = newInstance();
        headers2.add(bs("name1"), bs("value1"));
        headers2.add(bs("name2"), bs("value2"));
        headers2.add(bs("name2"), bs("value3"));

        assertEquals(headers1, headers2);
        assertEquals(headers2, headers1);
        assertEquals(headers1, headers1);
        assertEquals(headers2, headers2);
        assertEquals(headers1.hashCode(), headers2.hashCode());
        assertEquals(headers1.hashCode(), headers1.hashCode());
        assertEquals(headers2.hashCode(), headers2.hashCode());
    }

    @Test
    public void emptyHeadersShouldBeEqual() {
        Headers<ByteString> headers1 = newInstance();
        Headers<ByteString> headers2 = newInstance();
        assertNotSame(headers1, headers2);
        assertEquals(headers1, headers2);
        assertEquals(headers1.hashCode(), headers2.hashCode());
    }

    @Test
    public void headersWithSameNamesButDifferentValuesShouldNotBeEquivalent() {
        Headers<ByteString> headers1 = newInstance();
        headers1.add(bs("name1"), bs("value1"));
        Headers<ByteString> headers2 = newInstance();
        headers1.add(bs("name1"), bs("value2"));
        assertNotEquals(headers1, headers2);
    }

    @Test
    public void subsetOfHeadersShouldNotBeEquivalent() {
        Headers<ByteString> headers1 = newInstance();
        headers1.add(bs("name1"), bs("value1"));
        headers1.add(bs("name2"), bs("value2"));
        Headers<ByteString> headers2 = newInstance();
        headers1.add(bs("name1"), bs("value1"));
        assertNotEquals(headers1, headers2);
    }

    @Test
    public void headersWithDifferentNamesAndValuesShouldNotBeEquivalent() {
        Headers<ByteString> h1 = newInstance();
        h1.set(bs("name1"), bs("value1"));
        Headers<ByteString> h2 = newInstance();
        h2.set(bs("name2"), bs("value2"));
        assertNotEquals(h1, h2);
        assertNotEquals(h2, h1);
        assertEquals(h1, h1);
        assertEquals(h2, h2);
    }

    @Test(expected = NoSuchElementException.class)
    public void iterateEmptyHeadersShouldThrow() {
        Iterator<Map.Entry<ByteString, ByteString>> iterator = newInstance().iterator();
        assertFalse(iterator.hasNext());
        iterator.next();
    }

    @Test
    public void iteratorShouldReturnAllNameValuePairs() {
        Headers<ByteString> headers1 = newInstance();
        headers1.add(bs("name1"), bs("value1"), bs("value2"));
        headers1.add(bs("name2"), bs("value3"));
        headers1.add(bs("name3"), bs("value4"), bs("value5"), bs("value6"));
        headers1.add(bs("name1"), bs("value7"), bs("value8"));
        assertEquals(8, headers1.size());

        Headers<ByteString> headers2 = newInstance();
        for (Entry<ByteString, ByteString> entry : headers1) {
            headers2.add(entry.getKey(), entry.getValue());
        }

        assertEquals(headers1, headers2);
    }

    @Test
    public void iteratorSetValueShouldChangeHeaderValue() {
        Headers<ByteString> headers = newInstance();
        headers.add(bs("name1"), bs("value1"), bs("value2"), bs("value3"));
        headers.add(bs("name2"), bs("value4"));
        assertEquals(4, headers.size());

        Iterator<Entry<ByteString, ByteString>> iter = headers.iterator();
        while (iter.hasNext()) {
            Entry<ByteString, ByteString> header = iter.next();
            if (bs("name1").equals(header.getKey()) && bs("value2").equals(header.getValue())) {
                header.setValue(bs("updatedvalue2"));
                assertEquals(bs("updatedvalue2"), header.getValue());
            }
            if (bs("name1").equals(header.getKey()) && bs("value3").equals(header.getValue())) {
                header.setValue(bs("updatedvalue3"));
                assertEquals(bs("updatedvalue3"), header.getValue());
            }
        }

        assertEquals(4, headers.size());
        assertTrue(headers.contains(bs("name1"), bs("updatedvalue2")));
        assertFalse(headers.contains(bs("name1"), bs("value2")));
        assertTrue(headers.contains(bs("name1"), bs("updatedvalue3")));
        assertFalse(headers.contains(bs("name1"), bs("value3")));
    }

    @Test
    public void getAllReturnsEmptyListForUnknownName() {
        Headers<ByteString> headers = newInstance();
        assertEquals(0, headers.getAll(bs("noname")).size());
    }

    @Test
    public void setHeadersShouldClearAndOverwrite() {
        Headers<ByteString> headers1 = newInstance();
        headers1.add(bs("name"), bs("value"));

        Headers<ByteString> headers2 = newInstance();
        headers2.add(bs("name"), bs("newvalue"));
        headers2.add(bs("name1"), bs("value1"));

        headers1.set(headers2);
        assertEquals(headers1, headers2);
    }

    @Test
    public void setAllHeadersShouldOnlyOverwriteHeaders() {
        Headers<ByteString> headers1 = newInstance();
        headers1.add(bs("name"), bs("value"));
        headers1.add(bs("name1"), bs("value1"));

        Headers<ByteString> headers2 = newInstance();
        headers2.add(bs("name"), bs("newvalue"));
        headers2.add(bs("name2"), bs("value2"));

        Headers<ByteString> expected = newInstance();
        expected.add(bs("name"), bs("newvalue"));
        expected.add(bs("name1"), bs("value1"));
        expected.add(bs("name2"), bs("value2"));

        headers1.setAll(headers2);
        assertEquals(headers1, expected);
    }

    private ByteString bs(String value) {
        return new ByteString(value, CharsetUtil.US_ASCII);
    }
}
