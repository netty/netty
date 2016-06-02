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

import org.junit.Test;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;

import static io.netty.util.AsciiString.of;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link DefaultHeaders}.
 */
public class DefaultHeadersTest {

    private static final class TestDefaultHeaders extends
            DefaultHeaders<CharSequence, CharSequence, TestDefaultHeaders> {
        public TestDefaultHeaders() {
            super(CharSequenceValueConverter.INSTANCE);
        }
    }

    private TestDefaultHeaders newInstance() {
        return new TestDefaultHeaders();
    }

    @Test
    public void addShouldIncreaseAndRemoveShouldDecreaseTheSize() {
        TestDefaultHeaders headers = newInstance();
        assertEquals(0, headers.size());
        headers.add(of("name1"), of("value1"), of("value2"));
        assertEquals(2, headers.size());
        headers.add(of("name2"), of("value3"), of("value4"));
        assertEquals(4, headers.size());
        headers.add(of("name3"), of("value5"));
        assertEquals(5, headers.size());

        headers.remove(of("name3"));
        assertEquals(4, headers.size());
        headers.remove(of("name1"));
        assertEquals(2, headers.size());
        headers.remove(of("name2"));
        assertEquals(0, headers.size());
        assertTrue(headers.isEmpty());
    }

    @Test
    public void afterClearHeadersShouldBeEmpty() {
        TestDefaultHeaders headers = newInstance();
        headers.add(of("name1"), of("value1"));
        headers.add(of("name2"), of("value2"));
        assertEquals(2, headers.size());
        headers.clear();
        assertEquals(0, headers.size());
        assertTrue(headers.isEmpty());
        assertFalse(headers.contains(of("name1")));
        assertFalse(headers.contains(of("name2")));
    }

    @Test
    public void removingANameForASecondTimeShouldReturnFalse() {
        TestDefaultHeaders headers = newInstance();
        headers.add(of("name1"), of("value1"));
        headers.add(of("name2"), of("value2"));
        assertTrue(headers.remove(of("name2")));
        assertFalse(headers.remove(of("name2")));
    }

    @Test
    public void multipleValuesPerNameShouldBeAllowed() {
        TestDefaultHeaders headers = newInstance();
        headers.add(of("name"), of("value1"));
        headers.add(of("name"), of("value2"));
        headers.add(of("name"), of("value3"));
        assertEquals(3, headers.size());

        List<CharSequence> values = headers.getAll(of("name"));
        assertEquals(3, values.size());
        assertTrue(values.containsAll(asList(of("value1"), of("value2"), of("value3"))));
    }

    @Test
    public void testContains() {
        TestDefaultHeaders headers = newInstance();
        headers.addBoolean(of("boolean"), true);
        assertTrue(headers.containsBoolean(of("boolean"), true));
        assertFalse(headers.containsBoolean(of("boolean"), false));

        headers.addLong(of("long"), Long.MAX_VALUE);
        assertTrue(headers.containsLong(of("long"), Long.MAX_VALUE));
        assertFalse(headers.containsLong(of("long"), Long.MIN_VALUE));

        headers.addInt(of("int"), Integer.MIN_VALUE);
        assertTrue(headers.containsInt(of("int"), Integer.MIN_VALUE));
        assertFalse(headers.containsInt(of("int"), Integer.MAX_VALUE));

        headers.addShort(of("short"), Short.MAX_VALUE);
        assertTrue(headers.containsShort(of("short"), Short.MAX_VALUE));
        assertFalse(headers.containsShort(of("short"), Short.MIN_VALUE));

        headers.addChar(of("char"), Character.MAX_VALUE);
        assertTrue(headers.containsChar(of("char"), Character.MAX_VALUE));
        assertFalse(headers.containsChar(of("char"), Character.MIN_VALUE));

        headers.addByte(of("byte"), Byte.MAX_VALUE);
        assertTrue(headers.containsByte(of("byte"), Byte.MAX_VALUE));
        assertFalse(headers.containsLong(of("byte"), Byte.MIN_VALUE));

        headers.addDouble(of("double"), Double.MAX_VALUE);
        assertTrue(headers.containsDouble(of("double"), Double.MAX_VALUE));
        assertFalse(headers.containsDouble(of("double"), Double.MIN_VALUE));

        headers.addFloat(of("float"), Float.MAX_VALUE);
        assertTrue(headers.containsFloat(of("float"), Float.MAX_VALUE));
        assertFalse(headers.containsFloat(of("float"), Float.MIN_VALUE));

        long millis = System.currentTimeMillis();
        headers.addTimeMillis(of("millis"), millis);
        assertTrue(headers.containsTimeMillis(of("millis"), millis));
        // This test doesn't work on midnight, January 1, 1970 UTC
        assertFalse(headers.containsTimeMillis(of("millis"), 0));

        headers.addObject(of("object"), "Hello World");
        assertTrue(headers.containsObject(of("object"), "Hello World"));
        assertFalse(headers.containsObject(of("object"), ""));

        headers.add(of("name"), of("value"));
        assertTrue(headers.contains(of("name"), of("value")));
        assertFalse(headers.contains(of("name"), of("value1")));
    }

    @Test
    public void testCopy() throws Exception {
        TestDefaultHeaders headers = newInstance();
        headers.addBoolean(of("boolean"), true);
        headers.addLong(of("long"), Long.MAX_VALUE);
        headers.addInt(of("int"), Integer.MIN_VALUE);
        headers.addShort(of("short"), Short.MAX_VALUE);
        headers.addChar(of("char"), Character.MAX_VALUE);
        headers.addByte(of("byte"), Byte.MAX_VALUE);
        headers.addDouble(of("double"), Double.MAX_VALUE);
        headers.addFloat(of("float"), Float.MAX_VALUE);
        long millis = System.currentTimeMillis();
        headers.addTimeMillis(of("millis"), millis);
        headers.addObject(of("object"), "Hello World");
        headers.add(of("name"), of("value"));

        headers = newInstance().add(headers);

        assertTrue(headers.containsBoolean(of("boolean"), true));
        assertFalse(headers.containsBoolean(of("boolean"), false));

        assertTrue(headers.containsLong(of("long"), Long.MAX_VALUE));
        assertFalse(headers.containsLong(of("long"), Long.MIN_VALUE));

        assertTrue(headers.containsInt(of("int"), Integer.MIN_VALUE));
        assertFalse(headers.containsInt(of("int"), Integer.MAX_VALUE));

        assertTrue(headers.containsShort(of("short"), Short.MAX_VALUE));
        assertFalse(headers.containsShort(of("short"), Short.MIN_VALUE));

        assertTrue(headers.containsChar(of("char"), Character.MAX_VALUE));
        assertFalse(headers.containsChar(of("char"), Character.MIN_VALUE));

        assertTrue(headers.containsByte(of("byte"), Byte.MAX_VALUE));
        assertFalse(headers.containsLong(of("byte"), Byte.MIN_VALUE));

        assertTrue(headers.containsDouble(of("double"), Double.MAX_VALUE));
        assertFalse(headers.containsDouble(of("double"), Double.MIN_VALUE));

        assertTrue(headers.containsFloat(of("float"), Float.MAX_VALUE));
        assertFalse(headers.containsFloat(of("float"), Float.MIN_VALUE));

        assertTrue(headers.containsTimeMillis(of("millis"), millis));
        // This test doesn't work on midnight, January 1, 1970 UTC
        assertFalse(headers.containsTimeMillis(of("millis"), 0));

        assertTrue(headers.containsObject(of("object"), "Hello World"));
        assertFalse(headers.containsObject(of("object"), ""));

        assertTrue(headers.contains(of("name"), of("value")));
        assertFalse(headers.contains(of("name"), of("value1")));
    }

    @Test
    public void canMixConvertedAndNormalValues() {
        TestDefaultHeaders headers = newInstance();
        headers.add(of("name"), of("value"));
        headers.addInt(of("name"), 100);
        headers.addBoolean(of("name"), false);

        assertEquals(3, headers.size());
        assertTrue(headers.contains(of("name")));
        assertTrue(headers.contains(of("name"), of("value")));
        assertTrue(headers.containsInt(of("name"), 100));
        assertTrue(headers.containsBoolean(of("name"), false));
    }

    @Test
    public void testGetAndRemove() {
        TestDefaultHeaders headers = newInstance();
        headers.add(of("name1"), of("value1"));
        headers.add(of("name2"), of("value2"), of("value3"));
        headers.add(of("name3"), of("value4"), of("value5"), of("value6"));

        assertEquals(of("value1"), headers.getAndRemove(of("name1"), of("defaultvalue")));
        assertEquals(of("value2"), headers.getAndRemove(of("name2")));
        assertNull(headers.getAndRemove(of("name2")));
        assertEquals(asList(of("value4"), of("value5"), of("value6")), headers.getAllAndRemove(of("name3")));
        assertEquals(0, headers.size());
        assertNull(headers.getAndRemove(of("noname")));
        assertEquals(of("defaultvalue"), headers.getAndRemove(of("noname"), of("defaultvalue")));
    }

    @Test
    public void whenNameContainsMultipleValuesGetShouldReturnTheFirst() {
        TestDefaultHeaders headers = newInstance();
        headers.add(of("name1"), of("value1"), of("value2"));
        assertEquals(of("value1"), headers.get(of("name1")));
    }

    @Test
    public void getWithDefaultValueWorks() {
        TestDefaultHeaders headers = newInstance();
        headers.add(of("name1"), of("value1"));

        assertEquals(of("value1"), headers.get(of("name1"), of("defaultvalue")));
        assertEquals(of("defaultvalue"), headers.get(of("noname"), of("defaultvalue")));
    }

    @Test
    public void setShouldOverWritePreviousValue() {
        TestDefaultHeaders headers = newInstance();
        headers.set(of("name"), of("value1"));
        headers.set(of("name"), of("value2"));
        assertEquals(1, headers.size());
        assertEquals(1, headers.getAll(of("name")).size());
        assertEquals(of("value2"), headers.getAll(of("name")).get(0));
        assertEquals(of("value2"), headers.get(of("name")));
    }

    @Test
    public void setAllShouldOverwriteSomeAndLeaveOthersUntouched() {
        TestDefaultHeaders h1 = newInstance();

        h1.add(of("name1"), of("value1"));
        h1.add(of("name2"), of("value2"));
        h1.add(of("name2"), of("value3"));
        h1.add(of("name3"), of("value4"));

        TestDefaultHeaders h2 = newInstance();
        h2.add(of("name1"), of("value5"));
        h2.add(of("name2"), of("value6"));
        h2.add(of("name1"), of("value7"));

        TestDefaultHeaders expected = newInstance();
        expected.add(of("name1"), of("value5"));
        expected.add(of("name2"), of("value6"));
        expected.add(of("name1"), of("value7"));
        expected.add(of("name3"), of("value4"));

        h1.setAll(h2);

        assertEquals(expected, h1);
    }

    @Test
    public void headersWithSameNamesAndValuesShouldBeEquivalent() {
        TestDefaultHeaders headers1 = newInstance();
        headers1.add(of("name1"), of("value1"));
        headers1.add(of("name2"), of("value2"));
        headers1.add(of("name2"), of("value3"));

        TestDefaultHeaders headers2 = newInstance();
        headers2.add(of("name1"), of("value1"));
        headers2.add(of("name2"), of("value2"));
        headers2.add(of("name2"), of("value3"));

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
        TestDefaultHeaders headers1 = newInstance();
        TestDefaultHeaders headers2 = newInstance();
        assertNotSame(headers1, headers2);
        assertEquals(headers1, headers2);
        assertEquals(headers1.hashCode(), headers2.hashCode());
    }

    @Test
    public void headersWithSameNamesButDifferentValuesShouldNotBeEquivalent() {
        TestDefaultHeaders headers1 = newInstance();
        headers1.add(of("name1"), of("value1"));
        TestDefaultHeaders headers2 = newInstance();
        headers1.add(of("name1"), of("value2"));
        assertNotEquals(headers1, headers2);
    }

    @Test
    public void subsetOfHeadersShouldNotBeEquivalent() {
        TestDefaultHeaders headers1 = newInstance();
        headers1.add(of("name1"), of("value1"));
        headers1.add(of("name2"), of("value2"));
        TestDefaultHeaders headers2 = newInstance();
        headers1.add(of("name1"), of("value1"));
        assertNotEquals(headers1, headers2);
    }

    @Test
    public void headersWithDifferentNamesAndValuesShouldNotBeEquivalent() {
        TestDefaultHeaders h1 = newInstance();
        h1.set(of("name1"), of("value1"));
        TestDefaultHeaders h2 = newInstance();
        h2.set(of("name2"), of("value2"));
        assertNotEquals(h1, h2);
        assertNotEquals(h2, h1);
        assertEquals(h1, h1);
        assertEquals(h2, h2);
    }

    @Test(expected = NoSuchElementException.class)
    public void iterateEmptyHeadersShouldThrow() {
        Iterator<Map.Entry<CharSequence, CharSequence>> iterator = newInstance().iterator();
        assertFalse(iterator.hasNext());
        iterator.next();
    }

    @Test
    public void iteratorShouldReturnAllNameValuePairs() {
        TestDefaultHeaders headers1 = newInstance();
        headers1.add(of("name1"), of("value1"), of("value2"));
        headers1.add(of("name2"), of("value3"));
        headers1.add(of("name3"), of("value4"), of("value5"), of("value6"));
        headers1.add(of("name1"), of("value7"), of("value8"));
        assertEquals(8, headers1.size());

        TestDefaultHeaders headers2 = newInstance();
        for (Entry<CharSequence, CharSequence> entry : headers1) {
            headers2.add(entry.getKey(), entry.getValue());
        }

        assertEquals(headers1, headers2);
    }

    @Test
    public void iteratorSetValueShouldChangeHeaderValue() {
        TestDefaultHeaders headers = newInstance();
        headers.add(of("name1"), of("value1"), of("value2"), of("value3"));
        headers.add(of("name2"), of("value4"));
        assertEquals(4, headers.size());

        Iterator<Entry<CharSequence, CharSequence>> iter = headers.iterator();
        while (iter.hasNext()) {
            Entry<CharSequence, CharSequence> header = iter.next();
            if (of("name1").equals(header.getKey()) && of("value2").equals(header.getValue())) {
                header.setValue(of("updatedvalue2"));
                assertEquals(of("updatedvalue2"), header.getValue());
            }
            if (of("name1").equals(header.getKey()) && of("value3").equals(header.getValue())) {
                header.setValue(of("updatedvalue3"));
                assertEquals(of("updatedvalue3"), header.getValue());
            }
        }

        assertEquals(4, headers.size());
        assertTrue(headers.contains(of("name1"), of("updatedvalue2")));
        assertFalse(headers.contains(of("name1"), of("value2")));
        assertTrue(headers.contains(of("name1"), of("updatedvalue3")));
        assertFalse(headers.contains(of("name1"), of("value3")));
    }

    @Test
    public void getAllReturnsEmptyListForUnknownName() {
        TestDefaultHeaders headers = newInstance();
        assertEquals(0, headers.getAll(of("noname")).size());
    }

    @Test
    public void setHeadersShouldClearAndOverwrite() {
        TestDefaultHeaders headers1 = newInstance();
        headers1.add(of("name"), of("value"));

        TestDefaultHeaders headers2 = newInstance();
        headers2.add(of("name"), of("newvalue"));
        headers2.add(of("name1"), of("value1"));

        headers1.set(headers2);
        assertEquals(headers1, headers2);
    }

    @Test
    public void setAllHeadersShouldOnlyOverwriteHeaders() {
        TestDefaultHeaders headers1 = newInstance();
        headers1.add(of("name"), of("value"));
        headers1.add(of("name1"), of("value1"));

        TestDefaultHeaders headers2 = newInstance();
        headers2.add(of("name"), of("newvalue"));
        headers2.add(of("name2"), of("value2"));

        TestDefaultHeaders expected = newInstance();
        expected.add(of("name"), of("newvalue"));
        expected.add(of("name1"), of("value1"));
        expected.add(of("name2"), of("value2"));

        headers1.setAll(headers2);
        assertEquals(headers1, expected);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddSelf() {
        TestDefaultHeaders headers = newInstance();
        headers.add(headers);
    }

    @Test
    public void testSetSelfIsNoOp() {
        TestDefaultHeaders headers = newInstance();
        headers.add("name", "value");
        headers.set(headers);
        assertEquals(1, headers.size());
    }

    @Test
    public void testToString() {
        TestDefaultHeaders headers = newInstance();
        headers.add(of("name1"), of("value1"));
        headers.add(of("name1"), of("value2"));
        headers.add(of("name2"), of("value3"));
        assertEquals("TestDefaultHeaders[name1: value1, name1: value2, name2: value3]", headers.toString());

        headers = newInstance();
        headers.add(of("name1"), of("value1"));
        headers.add(of("name2"), of("value2"));
        headers.add(of("name3"), of("value3"));
        assertEquals("TestDefaultHeaders[name1: value1, name2: value2, name3: value3]", headers.toString());

        headers = newInstance();
        headers.add(of("name1"), of("value1"));
        assertEquals("TestDefaultHeaders[name1: value1]", headers.toString());

        headers = newInstance();
        assertEquals("TestDefaultHeaders[]", headers.toString());
    }
}
