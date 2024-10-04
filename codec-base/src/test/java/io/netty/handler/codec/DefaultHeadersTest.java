/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec;

import io.netty.util.AsciiString;
import io.netty.util.HashingStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;

import static io.netty.util.AsciiString.of;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests for {@link DefaultHeaders}.
 */
public class DefaultHeadersTest {

    private static final class TestDefaultHeaders extends
            DefaultHeaders<CharSequence, CharSequence, TestDefaultHeaders> {
        TestDefaultHeaders() {
            this(CharSequenceValueConverter.INSTANCE);
        }

        TestDefaultHeaders(ValueConverter<CharSequence> converter) {
            super(converter);
        }

        TestDefaultHeaders(HashingStrategy<CharSequence> nameHashingStrategy) {
            super(nameHashingStrategy, CharSequenceValueConverter.INSTANCE);
        }
    }

    private static TestDefaultHeaders newInstance() {
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
    public void multipleValuesPerNameIteratorWithOtherNames() {
        TestDefaultHeaders headers = newInstance();
        headers.add(of("name1"), of("value1"));
        headers.add(of("name1"), of("value2"));
        headers.add(of("name2"), of("value4"));
        headers.add(of("name1"), of("value3"));
        assertEquals(4, headers.size());

        List<CharSequence> values = new ArrayList<CharSequence>();
        Iterator<CharSequence> itr = headers.valueIterator(of("name1"));
        while (itr.hasNext()) {
            values.add(itr.next());
            itr.remove();
        }
        assertEquals(3, values.size());
        assertEquals(1, headers.size());
        assertFalse(headers.isEmpty());
        assertTrue(values.containsAll(asList(of("value1"), of("value2"), of("value3"))));
        itr = headers.valueIterator(of("name1"));
        assertFalse(itr.hasNext());
        itr = headers.valueIterator(of("name2"));
        assertTrue(itr.hasNext());
        assertEquals(of("value4"), itr.next());
        assertFalse(itr.hasNext());
    }

    @Test
    public void multipleValuesPerNameIterator() {
        TestDefaultHeaders headers = newInstance();
        headers.add(of("name1"), of("value1"));
        headers.add(of("name1"), of("value2"));
        assertEquals(2, headers.size());

        List<CharSequence> values = new ArrayList<CharSequence>();
        Iterator<CharSequence> itr = headers.valueIterator(of("name1"));
        while (itr.hasNext()) {
            values.add(itr.next());
            itr.remove();
        }
        assertEquals(2, values.size());
        assertEquals(0, headers.size());
        assertTrue(headers.isEmpty());
        assertTrue(values.containsAll(asList(of("value1"), of("value2"))));
        itr = headers.valueIterator(of("name1"));
        assertFalse(itr.hasNext());
    }

    @Test
    public void valuesItrRemoveThrowsWhenEmpty() {
        TestDefaultHeaders headers = newInstance();
        assertEquals(0, headers.size());
        assertTrue(headers.isEmpty());
        final Iterator<CharSequence> itr = headers.valueIterator(of("name"));
        assertThrows(IllegalStateException.class, new Executable() {
            @Override
            public void execute() {
                itr.remove();
            }
        });
    }

    @Test
    public void valuesItrRemoveThrowsAfterLastElement() {
        TestDefaultHeaders headers = newInstance();
        headers.add(of("name"), of("value1"));
        assertEquals(1, headers.size());

        List<CharSequence> values = new ArrayList<CharSequence>();
        Iterator<CharSequence> itr = headers.valueIterator(of("name"));
        while (itr.hasNext()) {
            values.add(itr.next());
            itr.remove();
        }
        assertEquals(1, values.size());
        assertEquals(0, headers.size());
        assertTrue(headers.isEmpty());
        assertTrue(values.contains(of("value1")));
        try {
            itr.remove();
            fail();
        } catch (IllegalStateException ignored) {
            // ignored
        }
    }

    @Test
    public void multipleValuesPerNameIteratorEmpty() {
        TestDefaultHeaders headers = newInstance();

        List<CharSequence> values = new ArrayList<CharSequence>();
        Iterator<CharSequence> itr = headers.valueIterator(of("name"));
        while (itr.hasNext()) {
            values.add(itr.next());
        }
        assertEquals(0, values.size());
        try {
            itr.next();
            fail();
        } catch (NoSuchElementException ignored) {
            // ignored
        }
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

    @Test
    public void iterateEmptyHeadersShouldThrow() {
        final Iterator<Map.Entry<CharSequence, CharSequence>> iterator = newInstance().iterator();
        assertFalse(iterator.hasNext());
        assertThrows(NoSuchElementException.class, new Executable() {
            @Override
            public void execute() {
                iterator.next();
            }
        });
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
    public void testEntryEquals() {
        Map.Entry<CharSequence, CharSequence> same1 = newInstance().add("name", "value").iterator().next();
        Map.Entry<CharSequence, CharSequence> same2 = newInstance().add("name", "value").iterator().next();
        assertEquals(same1, same2);
        assertEquals(same1.hashCode(), same2.hashCode());

        Map.Entry<CharSequence, CharSequence> nameDifferent1 = newInstance().add("name1", "value").iterator().next();
        Map.Entry<CharSequence, CharSequence> nameDifferent2 = newInstance().add("name2", "value").iterator().next();
        assertNotEquals(nameDifferent1, nameDifferent2);
        assertNotEquals(nameDifferent1.hashCode(), nameDifferent2.hashCode());

        Map.Entry<CharSequence, CharSequence> valueDifferent1 = newInstance().add("name", "value1").iterator().next();
        Map.Entry<CharSequence, CharSequence> valueDifferent2 = newInstance().add("name", "value2").iterator().next();
        assertNotEquals(valueDifferent1, valueDifferent2);
        assertNotEquals(valueDifferent1.hashCode(), valueDifferent2.hashCode());
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

    @Test
    public void testAddSelf() {
        final TestDefaultHeaders headers = newInstance();
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                headers.add(headers);
            }
        });
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

    @Test
    public void testNotThrowWhenConvertFails() {
        TestDefaultHeaders headers = new TestDefaultHeaders(new ValueConverter<CharSequence>() {
            @Override
            public CharSequence convertObject(Object value) {
                throw new IllegalArgumentException();
            }

            @Override
            public CharSequence convertBoolean(boolean value) {
                throw new IllegalArgumentException();
            }

            @Override
            public boolean convertToBoolean(CharSequence value) {
                throw new IllegalArgumentException();
            }

            @Override
            public CharSequence convertByte(byte value) {
                throw new IllegalArgumentException();
            }

            @Override
            public byte convertToByte(CharSequence value) {
                throw new IllegalArgumentException();
            }

            @Override
            public CharSequence convertChar(char value) {
                throw new IllegalArgumentException();
            }

            @Override
            public char convertToChar(CharSequence value) {
                throw new IllegalArgumentException();
            }

            @Override
            public CharSequence convertShort(short value) {
                throw new IllegalArgumentException();
            }

            @Override
            public short convertToShort(CharSequence value) {
                throw new IllegalArgumentException();
            }

            @Override
            public CharSequence convertInt(int value) {
                throw new IllegalArgumentException();
            }

            @Override
            public int convertToInt(CharSequence value) {
                throw new IllegalArgumentException();
            }

            @Override
            public CharSequence convertLong(long value) {
                throw new IllegalArgumentException();
            }

            @Override
            public long convertToLong(CharSequence value) {
                throw new IllegalArgumentException();
            }

            @Override
            public CharSequence convertTimeMillis(long value) {
                throw new IllegalArgumentException();
            }

            @Override
            public long convertToTimeMillis(CharSequence value) {
                throw new IllegalArgumentException();
            }

            @Override
            public CharSequence convertFloat(float value) {
                throw new IllegalArgumentException();
            }

            @Override
            public float convertToFloat(CharSequence value) {
                throw new IllegalArgumentException();
            }

            @Override
            public CharSequence convertDouble(double value) {
                throw new IllegalArgumentException();
            }

            @Override
            public double convertToDouble(CharSequence value) {
                throw new IllegalArgumentException();
            }
        });
        headers.set("name1", "");
        assertNull(headers.getInt("name1"));
        assertEquals(1, headers.getInt("name1", 1));

        assertNull(headers.getBoolean(""));
        assertFalse(headers.getBoolean("name1", false));

        assertNull(headers.getByte("name1"));
        assertEquals(1, headers.getByte("name1", (byte) 1));

        assertNull(headers.getChar("name"));
        assertEquals('n', headers.getChar("name1", 'n'));

        assertNull(headers.getDouble("name"));
        assertEquals(1, headers.getDouble("name1", 1), 0);

        assertNull(headers.getFloat("name"));
        assertEquals(Float.MAX_VALUE, headers.getFloat("name1", Float.MAX_VALUE), 0);

        assertNull(headers.getLong("name"));
        assertEquals(Long.MAX_VALUE, headers.getLong("name1", Long.MAX_VALUE));

        assertNull(headers.getShort("name"));
        assertEquals(Short.MAX_VALUE, headers.getShort("name1", Short.MAX_VALUE));

        assertNull(headers.getTimeMillis("name"));
        assertEquals(Long.MAX_VALUE, headers.getTimeMillis("name1", Long.MAX_VALUE));
    }

    @Test
    public void testGetBooleanInvalidValue() {
        TestDefaultHeaders headers = newInstance();
        headers.set("name1", "invalid");
        headers.set("name2", new AsciiString("invalid"));
        headers.set("name3", new StringBuilder("invalid"));

        assertFalse(headers.getBoolean("name1", false));
        assertFalse(headers.getBoolean("name2", false));
        assertFalse(headers.getBoolean("name3", false));
    }

    @Test
    public void testGetBooleanFalseValue() {
        TestDefaultHeaders headers = newInstance();
        headers.set("name1", "false");
        headers.set("name2", new AsciiString("false"));
        headers.set("name3", new StringBuilder("false"));

        assertFalse(headers.getBoolean("name1", true));
        assertFalse(headers.getBoolean("name2", true));
        assertFalse(headers.getBoolean("name3", true));
    }

    @Test
    public void testGetBooleanTrueValue() {
        TestDefaultHeaders headers = newInstance();
        headers.set("name1", "true");
        headers.set("name2", new AsciiString("true"));
        headers.set("name3", new StringBuilder("true"));

        assertTrue(headers.getBoolean("name1", false));
        assertTrue(headers.getBoolean("name2", false));
        assertTrue(headers.getBoolean("name3", false));
    }

    @Test
    public void handlingOfHeaderNameHashCollisions() {
        TestDefaultHeaders headers = new TestDefaultHeaders(new HashingStrategy<CharSequence>() {
            @Override
            public int hashCode(CharSequence obj) {
                return 0; // Degenerate hashing strategy to enforce collisions.
            }

            @Override
            public boolean equals(CharSequence a, CharSequence b) {
                return a.equals(b);
            }
        });

        headers.add("Cookie", "a=b; c=d; e=f");
        headers.add("other", "text/plain");  // Add another header which will be saved in the same entries[index]

        simulateCookieSplitting(headers);
        List<CharSequence> cookies = headers.getAll("Cookie");

        assertThat(cookies, hasSize(3));
        assertThat(cookies, containsInAnyOrder((CharSequence) "a=b", "c=d", "e=f"));
    }

    /**
     * Split up cookies into individual cookie crumb headers.
     */
    static void simulateCookieSplitting(TestDefaultHeaders headers) {
        Iterator<CharSequence> cookieItr = headers.valueIterator("Cookie");
        if (!cookieItr.hasNext()) {
            return;
        }
        // We want to avoid "concurrent modifications" of the headers while we are iterating. So we insert crumbs
        // into an intermediate collection and insert them after the split process concludes.
        List<CharSequence> cookiesToAdd = new ArrayList<CharSequence>();
        while (cookieItr.hasNext()) {
            //noinspection DynamicRegexReplaceableByCompiledPattern
            String[] cookies = cookieItr.next().toString().split("; ");
            cookiesToAdd.addAll(asList(cookies));
            cookieItr.remove();
        }
        for (CharSequence crumb : cookiesToAdd) {
            headers.add("Cookie", crumb);
        }
    }
}
