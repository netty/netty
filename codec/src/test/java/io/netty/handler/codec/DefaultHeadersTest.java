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

import io.netty.util.AsciiString;

/**
 * Tests for {@link DefaultHeaders}.
 */
public class DefaultHeadersTest {

    private Headers<AsciiString> newInstance() {
        return new DefaultHeaders<AsciiString>(AsciiStringValueConverter.INSTANCE);
    }

    @Test
    public void addShouldIncreaseAndRemoveShouldDecreaseTheSize() {
        Headers<AsciiString> headers = newInstance();
        assertEquals(0, headers.size());
        headers.add(as("name1"), as("value1"), as("value2"));
        assertEquals(2, headers.size());
        headers.add(as("name2"), as("value3"), as("value4"));
        assertEquals(4, headers.size());
        headers.add(as("name3"), as("value5"));
        assertEquals(5, headers.size());

        headers.remove(as("name3"));
        assertEquals(4, headers.size());
        headers.remove(as("name1"));
        assertEquals(2, headers.size());
        headers.remove(as("name2"));
        assertEquals(0, headers.size());
        assertTrue(headers.isEmpty());
    }

    @Test
    public void afterClearHeadersShouldBeEmpty() {
        Headers<AsciiString> headers = newInstance();
        headers.add(as("name1"), as("value1"));
        headers.add(as("name2"), as("value2"));
        assertEquals(2, headers.size());
        headers.clear();
        assertEquals(0, headers.size());
        assertTrue(headers.isEmpty());
        assertFalse(headers.contains(as("name1")));
        assertFalse(headers.contains(as("name2")));
    }

    @Test
    public void removingANameForASecondTimeShouldReturnFalse() {
        Headers<AsciiString> headers = newInstance();
        headers.add(as("name1"), as("value1"));
        headers.add(as("name2"), as("value2"));
        assertTrue(headers.remove(as("name2")));
        assertFalse(headers.remove(as("name2")));
    }

    @Test
    public void multipleValuesPerNameShouldBeAllowed() {
        Headers<AsciiString> headers = newInstance();
        headers.add(as("name"), as("value1"));
        headers.add(as("name"), as("value2"));
        headers.add(as("name"), as("value3"));
        assertEquals(3, headers.size());

        List<AsciiString> values = headers.getAll(as("name"));
        assertEquals(3, values.size());
        assertTrue(values.containsAll(asList(as("value1"), as("value2"), as("value3"))));
    }

    @Test
    public void testContains() {
        Headers<AsciiString> headers = newInstance();
        headers.addBoolean(as("boolean"), true);
        assertTrue(headers.containsBoolean(as("boolean"), true));
        assertFalse(headers.containsBoolean(as("boolean"), false));

        headers.addLong(as("long"), Long.MAX_VALUE);
        assertTrue(headers.containsLong(as("long"), Long.MAX_VALUE));
        assertFalse(headers.containsLong(as("long"), Long.MIN_VALUE));

        headers.addInt(as("int"), Integer.MIN_VALUE);
        assertTrue(headers.containsInt(as("int"), Integer.MIN_VALUE));
        assertFalse(headers.containsInt(as("int"), Integer.MAX_VALUE));

        headers.addShort(as("short"), Short.MAX_VALUE);
        assertTrue(headers.containsShort(as("short"), Short.MAX_VALUE));
        assertFalse(headers.containsShort(as("short"), Short.MIN_VALUE));

        headers.addChar(as("char"), Character.MAX_VALUE);
        assertTrue(headers.containsChar(as("char"), Character.MAX_VALUE));
        assertFalse(headers.containsChar(as("char"), Character.MIN_VALUE));

        headers.addByte(as("byte"), Byte.MAX_VALUE);
        assertTrue(headers.containsByte(as("byte"), Byte.MAX_VALUE));
        assertFalse(headers.containsLong(as("byte"), Byte.MIN_VALUE));

        headers.addDouble(as("double"), Double.MAX_VALUE);
        assertTrue(headers.containsDouble(as("double"), Double.MAX_VALUE));
        assertFalse(headers.containsDouble(as("double"), Double.MIN_VALUE));

        headers.addFloat(as("float"), Float.MAX_VALUE);
        assertTrue(headers.containsFloat(as("float"), Float.MAX_VALUE));
        assertFalse(headers.containsFloat(as("float"), Float.MIN_VALUE));

        long millis = System.currentTimeMillis();
        headers.addTimeMillis(as("millis"), millis);
        assertTrue(headers.containsTimeMillis(as("millis"), millis));
        // This test doesn't work on midnight, January 1, 1970 UTC
        assertFalse(headers.containsTimeMillis(as("millis"), 0));

        headers.addObject(as("object"), "Hello World");
        assertTrue(headers.containsObject(as("object"), "Hello World"));
        assertFalse(headers.containsObject(as("object"), ""));

        headers.add(as("name"), as("value"));
        assertTrue(headers.contains(as("name"), as("value")));
        assertFalse(headers.contains(as("name"), as("value1")));
    }

    @Test
    public void canMixConvertedAndNormalValues() {
        Headers<AsciiString> headers = newInstance();
        headers.add(as("name"), as("value"));
        headers.addInt(as("name"), 100);
        headers.addBoolean(as("name"), false);

        assertEquals(3, headers.size());
        assertTrue(headers.contains(as("name")));
        assertTrue(headers.contains(as("name"), as("value")));
        assertTrue(headers.containsInt(as("name"), 100));
        assertTrue(headers.containsBoolean(as("name"), false));
    }

    @Test
    public void testGetAndRemove() {
        Headers<AsciiString> headers = newInstance();
        headers.add(as("name1"), as("value1"));
        headers.add(as("name2"), as("value2"), as("value3"));
        headers.add(as("name3"), as("value4"), as("value5"), as("value6"));

        assertEquals(as("value1"), headers.getAndRemove(as("name1"), as("defaultvalue")));
        assertEquals(as("value2"), headers.getAndRemove(as("name2")));
        assertNull(headers.getAndRemove(as("name2")));
        assertEquals(asList(as("value4"), as("value5"), as("value6")), headers.getAllAndRemove(as("name3")));
        assertEquals(0, headers.size());
        assertNull(headers.getAndRemove(as("noname")));
        assertEquals(as("defaultvalue"), headers.getAndRemove(as("noname"), as("defaultvalue")));
    }

    @Test
    public void whenNameContainsMultipleValuesGetShouldReturnTheFirst() {
        Headers<AsciiString> headers = newInstance();
        headers.add(as("name1"), as("value1"), as("value2"));
        assertEquals(as("value1"), headers.get(as("name1")));
    }

    @Test
    public void getWithDefaultValueWorks() {
        Headers<AsciiString> headers = newInstance();
        headers.add(as("name1"), as("value1"));

        assertEquals(as("value1"), headers.get(as("name1"), as("defaultvalue")));
        assertEquals(as("defaultvalue"), headers.get(as("noname"), as("defaultvalue")));
    }

    @Test
    public void setShouldOverWritePreviousValue() {
        Headers<AsciiString> headers = newInstance();
        headers.set(as("name"), as("value1"));
        headers.set(as("name"), as("value2"));
        assertEquals(1, headers.size());
        assertEquals(1, headers.getAll(as("name")).size());
        assertEquals(as("value2"), headers.getAll(as("name")).get(0));
        assertEquals(as("value2"), headers.get(as("name")));
    }

    @Test
    public void setAllShouldOverwriteSomeAndLeaveOthersUntouched() {
        Headers<AsciiString> h1 = newInstance();

        h1.add(as("name1"), as("value1"));
        h1.add(as("name2"), as("value2"));
        h1.add(as("name2"), as("value3"));
        h1.add(as("name3"), as("value4"));

        Headers<AsciiString> h2 = newInstance();
        h2.add(as("name1"), as("value5"));
        h2.add(as("name2"), as("value6"));
        h2.add(as("name1"), as("value7"));

        Headers<AsciiString> expected = newInstance();
        expected.add(as("name1"), as("value5"));
        expected.add(as("name2"), as("value6"));
        expected.add(as("name1"), as("value7"));
        expected.add(as("name3"), as("value4"));

        h1.setAll(h2);

        assertEquals(expected, h1);
    }

    @Test
    public void headersWithSameNamesAndValuesShouldBeEquivalent() {
        Headers<AsciiString> headers1 = newInstance();
        headers1.add(as("name1"), as("value1"));
        headers1.add(as("name2"), as("value2"));
        headers1.add(as("name2"), as("value3"));

        Headers<AsciiString> headers2 = newInstance();
        headers2.add(as("name1"), as("value1"));
        headers2.add(as("name2"), as("value2"));
        headers2.add(as("name2"), as("value3"));

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
        Headers<AsciiString> headers1 = newInstance();
        Headers<AsciiString> headers2 = newInstance();
        assertNotSame(headers1, headers2);
        assertEquals(headers1, headers2);
        assertEquals(headers1.hashCode(), headers2.hashCode());
    }

    @Test
    public void headersWithSameNamesButDifferentValuesShouldNotBeEquivalent() {
        Headers<AsciiString> headers1 = newInstance();
        headers1.add(as("name1"), as("value1"));
        Headers<AsciiString> headers2 = newInstance();
        headers1.add(as("name1"), as("value2"));
        assertNotEquals(headers1, headers2);
    }

    @Test
    public void subsetOfHeadersShouldNotBeEquivalent() {
        Headers<AsciiString> headers1 = newInstance();
        headers1.add(as("name1"), as("value1"));
        headers1.add(as("name2"), as("value2"));
        Headers<AsciiString> headers2 = newInstance();
        headers1.add(as("name1"), as("value1"));
        assertNotEquals(headers1, headers2);
    }

    @Test
    public void headersWithDifferentNamesAndValuesShouldNotBeEquivalent() {
        Headers<AsciiString> h1 = newInstance();
        h1.set(as("name1"), as("value1"));
        Headers<AsciiString> h2 = newInstance();
        h2.set(as("name2"), as("value2"));
        assertNotEquals(h1, h2);
        assertNotEquals(h2, h1);
        assertEquals(h1, h1);
        assertEquals(h2, h2);
    }

    @Test(expected = NoSuchElementException.class)
    public void iterateEmptyHeadersShouldThrow() {
        Iterator<Map.Entry<AsciiString, AsciiString>> iterator = newInstance().iterator();
        assertFalse(iterator.hasNext());
        iterator.next();
    }

    @Test
    public void iteratorShouldReturnAllNameValuePairs() {
        Headers<AsciiString> headers1 = newInstance();
        headers1.add(as("name1"), as("value1"), as("value2"));
        headers1.add(as("name2"), as("value3"));
        headers1.add(as("name3"), as("value4"), as("value5"), as("value6"));
        headers1.add(as("name1"), as("value7"), as("value8"));
        assertEquals(8, headers1.size());

        Headers<AsciiString> headers2 = newInstance();
        for (Entry<AsciiString, AsciiString> entry : headers1) {
            headers2.add(entry.getKey(), entry.getValue());
        }

        assertEquals(headers1, headers2);
    }

    @Test
    public void iteratorSetValueShouldChangeHeaderValue() {
        Headers<AsciiString> headers = newInstance();
        headers.add(as("name1"), as("value1"), as("value2"), as("value3"));
        headers.add(as("name2"), as("value4"));
        assertEquals(4, headers.size());

        Iterator<Entry<AsciiString, AsciiString>> iter = headers.iterator();
        while (iter.hasNext()) {
            Entry<AsciiString, AsciiString> header = iter.next();
            if (as("name1").equals(header.getKey()) && as("value2").equals(header.getValue())) {
                header.setValue(as("updatedvalue2"));
                assertEquals(as("updatedvalue2"), header.getValue());
            }
            if (as("name1").equals(header.getKey()) && as("value3").equals(header.getValue())) {
                header.setValue(as("updatedvalue3"));
                assertEquals(as("updatedvalue3"), header.getValue());
            }
        }

        assertEquals(4, headers.size());
        assertTrue(headers.contains(as("name1"), as("updatedvalue2")));
        assertFalse(headers.contains(as("name1"), as("value2")));
        assertTrue(headers.contains(as("name1"), as("updatedvalue3")));
        assertFalse(headers.contains(as("name1"), as("value3")));
    }

    @Test
    public void getAllReturnsEmptyListForUnknownName() {
        Headers<AsciiString> headers = newInstance();
        assertEquals(0, headers.getAll(as("noname")).size());
    }

    @Test
    public void setHeadersShouldClearAndOverwrite() {
        Headers<AsciiString> headers1 = newInstance();
        headers1.add(as("name"), as("value"));

        Headers<AsciiString> headers2 = newInstance();
        headers2.add(as("name"), as("newvalue"));
        headers2.add(as("name1"), as("value1"));

        headers1.set(headers2);
        assertEquals(headers1, headers2);
    }

    @Test
    public void setAllHeadersShouldOnlyOverwriteHeaders() {
        Headers<AsciiString> headers1 = newInstance();
        headers1.add(as("name"), as("value"));
        headers1.add(as("name1"), as("value1"));

        Headers<AsciiString> headers2 = newInstance();
        headers2.add(as("name"), as("newvalue"));
        headers2.add(as("name2"), as("value2"));

        Headers<AsciiString> expected = newInstance();
        expected.add(as("name"), as("newvalue"));
        expected.add(as("name1"), as("value1"));
        expected.add(as("name2"), as("value2"));

        headers1.setAll(headers2);
        assertEquals(headers1, expected);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddSelf() {
        Headers<AsciiString> headers = newInstance();
        headers.add(headers);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetSelf() {
        Headers<AsciiString> headers = newInstance();
        headers.set(headers);
    }

    private AsciiString as(String value) {
        return new AsciiString(value);
    }
}
