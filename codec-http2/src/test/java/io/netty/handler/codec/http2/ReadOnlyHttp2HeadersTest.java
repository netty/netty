/*
 * Copyright 2016 The Netty Project
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
package io.netty.handler.codec.http2;

import io.netty.util.AsciiString;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.*;

import static io.netty.handler.codec.http2.DefaultHttp2HeadersTest.*;
import static org.junit.jupiter.api.Assertions.*;

public class ReadOnlyHttp2HeadersTest {
    @Test
    public void notKeyValuePairThrows() {
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                ReadOnlyHttp2Headers.trailers(false, new AsciiString[]{ null });
            }
        });
    }

    @Test
    public void nullTrailersNotAllowed() {
        assertThrows(NullPointerException.class, new Executable() {
            @Override
            public void execute() {
                ReadOnlyHttp2Headers.trailers(false, (AsciiString[]) null);
            }
        });
    }

    @Test
    public void nullHeaderNameNotChecked() {
        ReadOnlyHttp2Headers.trailers(false, null, null);
    }

    @Test
    public void nullHeaderNameValidated() {
        assertThrows(Http2Exception.class, new Executable() {
            @Override
            public void execute() {
                ReadOnlyHttp2Headers.trailers(true, null, new AsciiString("foo"));
            }
        });
    }

    @Test
    public void pseudoHeaderNotAllowedAfterNonPseudoHeaders() {
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                ReadOnlyHttp2Headers.trailers(true, new AsciiString(":scheme"), new AsciiString("foo"),
                        new AsciiString("othername"), new AsciiString("goo"),
                        new AsciiString(":path"), new AsciiString("val"));
            }
        });
    }

    @Test
    public void nullValuesAreNotAllowed() {
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                ReadOnlyHttp2Headers.trailers(true, new AsciiString("foo"), null);
            }
        });
    }

    @Test
    public void emptyHeaderNameAllowed() {
        ReadOnlyHttp2Headers.trailers(false, AsciiString.EMPTY_STRING, new AsciiString("foo"));
    }

    @Test
    public void testPseudoHeadersMustComeFirstWhenIteratingServer() {
        Http2Headers headers = newServerHeaders();
        verifyPseudoHeadersFirst(headers);
    }

    @Test
    public void testPseudoHeadersMustComeFirstWhenIteratingClient() {
        Http2Headers headers = newClientHeaders();
        verifyPseudoHeadersFirst(headers);
    }

    @Test
    public void testIteratorReadOnlyClient() {
        assertThrows(UnsupportedOperationException.class, new Executable() {
            @Override
            public void execute() {
                testIteratorReadOnly(newClientHeaders());
            }
        });
    }

    @Test
    public void testIteratorReadOnlyServer() {
        assertThrows(UnsupportedOperationException.class, new Executable() {
            @Override
            public void execute() {
                testIteratorReadOnly(newServerHeaders());
            }
        });
    }

    @Test
    public void testIteratorReadOnlyTrailers() {
        assertThrows(UnsupportedOperationException.class, new Executable() {
            @Override
            public void execute() {
                testIteratorReadOnly(newTrailers());
            }
        });
    }

    @Test
    public void testIteratorEntryReadOnlyClient() {
        assertThrows(UnsupportedOperationException.class, new Executable() {
            @Override
            public void execute() {
                testIteratorEntryReadOnly(newClientHeaders());
            }
        });
    }

    @Test
    public void testIteratorEntryReadOnlyServer() {
        assertThrows(UnsupportedOperationException.class, new Executable() {
            @Override
            public void execute() {
                testIteratorEntryReadOnly(newServerHeaders());
            }
        });
    }

    @Test
    public void testIteratorEntryReadOnlyTrailers() {
        assertThrows(UnsupportedOperationException.class, new Executable() {
            @Override
            public void execute() {
                testIteratorEntryReadOnly(newTrailers());
            }
        });
    }

    @Test
    public void testSize() {
        Http2Headers headers = newTrailers();
        assertEquals(otherHeaders().length / 2, headers.size());
    }

    @Test
    public void testIsNotEmpty() {
        Http2Headers headers = newTrailers();
        assertFalse(headers.isEmpty());
    }

    @Test
    public void testIsEmpty() {
        Http2Headers headers = ReadOnlyHttp2Headers.trailers(false);
        assertTrue(headers.isEmpty());
    }

    @Test
    public void testContainsName() {
        Http2Headers headers = newClientHeaders();
        assertTrue(headers.contains("Name1"));
        assertTrue(headers.contains(Http2Headers.PseudoHeaderName.PATH.value()));
        assertFalse(headers.contains(Http2Headers.PseudoHeaderName.STATUS.value()));
        assertFalse(headers.contains("a missing header"));
    }

    @Test
    public void testContainsNameAndValue() {
        Http2Headers headers = newClientHeaders();
        assertTrue(headers.contains("Name1", "value1"));
        assertFalse(headers.contains("Name1", "Value1"));
        assertTrue(headers.contains("name2", "Value2", true));
        assertFalse(headers.contains("name2", "Value2", false));
        assertTrue(headers.contains(Http2Headers.PseudoHeaderName.PATH.value(), "/foo"));
        assertFalse(headers.contains(Http2Headers.PseudoHeaderName.STATUS.value(), "200"));
        assertFalse(headers.contains("a missing header", "a missing value"));
    }

    @Test
    public void testGet() {
        Http2Headers headers = newClientHeaders();
        assertTrue(AsciiString.contentEqualsIgnoreCase("value1", headers.get("Name1")));
        assertTrue(AsciiString.contentEqualsIgnoreCase("/foo",
                   headers.get(Http2Headers.PseudoHeaderName.PATH.value())));
        assertNull(headers.get(Http2Headers.PseudoHeaderName.STATUS.value()));
        assertNull(headers.get("a missing header"));
    }

    @Test
    public void testClientOtherValueIterator() {
        testValueIteratorSingleValue(newClientHeaders(), "name2", "value2");
    }

    @Test
    public void testClientPsuedoValueIterator() {
        testValueIteratorSingleValue(newClientHeaders(), ":path", "/foo");
    }

    @Test
    public void testServerPsuedoValueIterator() {
        testValueIteratorSingleValue(newServerHeaders(), ":status", "200");
    }

    @Test
    public void testEmptyValueIterator() {
        Http2Headers headers = newServerHeaders();
        final Iterator<CharSequence> itr = headers.valueIterator("foo");
        assertFalse(itr.hasNext());
        assertThrows(NoSuchElementException.class, new Executable() {
            @Override
            public void execute() {
                itr.next();
            }
        });
    }

    @Test
    public void testIteratorMultipleValues() {
        Http2Headers headers = ReadOnlyHttp2Headers.serverHeaders(false, new AsciiString("200"),
                new AsciiString("name2"), new AsciiString("value1"),
                new AsciiString("name1"), new AsciiString("value2"),
                new AsciiString("name2"), new AsciiString("value3"));
        Iterator<CharSequence> itr = headers.valueIterator("name2");
        assertTrue(itr.hasNext());
        assertTrue(AsciiString.contentEqualsIgnoreCase("value1", itr.next()));
        assertTrue(itr.hasNext());
        assertTrue(AsciiString.contentEqualsIgnoreCase("value3", itr.next()));
        assertFalse(itr.hasNext());
    }

    private static void testValueIteratorSingleValue(Http2Headers headers, CharSequence name, CharSequence value) {
        Iterator<CharSequence> itr = headers.valueIterator(name);
        assertTrue(itr.hasNext());
        assertTrue(AsciiString.contentEqualsIgnoreCase(value, itr.next()));
        assertFalse(itr.hasNext());
    }

    private static void testIteratorReadOnly(Http2Headers headers) {
        Iterator<Map.Entry<CharSequence, CharSequence>> itr = headers.iterator();
        assertTrue(itr.hasNext());
        itr.remove();
    }

    private static void testIteratorEntryReadOnly(Http2Headers headers) {
        Iterator<Map.Entry<CharSequence, CharSequence>> itr = headers.iterator();
        assertTrue(itr.hasNext());
        itr.next().setValue("foo");
    }

    private static ReadOnlyHttp2Headers newServerHeaders() {
        return ReadOnlyHttp2Headers.serverHeaders(false, new AsciiString("200"), otherHeaders());
    }

    private static ReadOnlyHttp2Headers newClientHeaders() {
        return ReadOnlyHttp2Headers.clientHeaders(false, new AsciiString("meth"), new AsciiString("/foo"),
                new AsciiString("schemer"), new AsciiString("respect_my_authority"), otherHeaders());
    }

    private static ReadOnlyHttp2Headers newTrailers() {
        return ReadOnlyHttp2Headers.trailers(false, otherHeaders());
    }

    private static AsciiString[] otherHeaders() {
        return new AsciiString[] {
                new AsciiString("name1"), new AsciiString("value1"),
                new AsciiString("name2"), new AsciiString("value2"),
                new AsciiString("name3"), new AsciiString("value3")
        };
    }

    // ============================================
    // Edge Cases for Header Creation
    // ============================================

    @Test
    public void testDuplicateHeaderNames() {
        Http2Headers headers = ReadOnlyHttp2Headers.trailers(false,
                new AsciiString("name1"), new AsciiString("value1"),
                new AsciiString("name1"), new AsciiString("value2"),
                new AsciiString("name1"), new AsciiString("value3"));

        assertEquals(3, headers.size());
        List<CharSequence> values = headers.getAll("name1");
        assertEquals(3, values.size());
        assertEquals("value1", values.get(0).toString());
        assertEquals("value2", values.get(1).toString());
        assertEquals("value3", values.get(2).toString());
    }

    @Test
    public void testHeaderWithEmptyValue() {
        Http2Headers headers = ReadOnlyHttp2Headers.trailers(false,
                new AsciiString("empty"), AsciiString.EMPTY_STRING);

        assertTrue(headers.contains("empty"));
        assertEquals("", headers.get("empty").toString());
    }

    @Test
    public void testSpecialCharactersInValues() {
        Http2Headers headers = ReadOnlyHttp2Headers.trailers(false,
                new AsciiString("name"), new AsciiString("value with spaces"),
                new AsciiString("name2"), new AsciiString("value:with:colons"),
                new AsciiString("name3"), new AsciiString("value,with,commas"),
                new AsciiString("name4"), new AsciiString("value;with;semicolons"));

        assertEquals("value with spaces", headers.get("name").toString());
        assertEquals("value:with:colons", headers.get("name2").toString());
        assertEquals("value,with,commas", headers.get("name3").toString());
        assertEquals("value;with;semicolons", headers.get("name4").toString());
    }

    @Test
    public void testVeryLongHeaderValue() {
        StringBuilder longValue = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            longValue.append("x");
        }

        Http2Headers headers = ReadOnlyHttp2Headers.trailers(false,
                new AsciiString("long"), new AsciiString(longValue.toString()));

        assertEquals(10000, headers.get("long").length());
    }

    @Test
    public void testLargeNumberOfHeaders() {
        AsciiString[] headers = new AsciiString[200]; // 100 key-value pairs
        for (int i = 0; i < 100; i++) {
            headers[i * 2] = new AsciiString("name" + i);
            headers[i * 2 + 1] = new AsciiString("value" + i);
        }

        Http2Headers http2Headers = ReadOnlyHttp2Headers.trailers(false, headers);
        assertEquals(100, http2Headers.size());
        assertEquals("value0", http2Headers.get("name0").toString());
        assertEquals("value99", http2Headers.get("name99").toString());
    }

    // ============================================
    // Get Methods with Default Values
    // ============================================

    @Test
    public void testGetWithDefaultValue() {
        Http2Headers headers = ReadOnlyHttp2Headers.trailers(false,
                new AsciiString("name1"), new AsciiString("value1"));

        assertEquals("value1", headers.get("name1", "default").toString());
        assertEquals("default", headers.get("nonexistent", "default"));
    }

    @Test
    public void testGetAllNonExistent() {
        Http2Headers headers = ReadOnlyHttp2Headers.trailers(false,
                new AsciiString("name1"), new AsciiString("value1"));

        List<CharSequence> values = headers.getAll("nonexistent");
        assertTrue(values.isEmpty());
    }

    // ============================================
    // Type Conversion Tests - Boundary Values
    // ============================================

    @Test
    public void testGetIntBoundaryValues() {
        Http2Headers headers = ReadOnlyHttp2Headers.trailers(false,
                new AsciiString("maxInt"), new AsciiString(String.valueOf(Integer.MAX_VALUE)),
                new AsciiString("minInt"), new AsciiString(String.valueOf(Integer.MIN_VALUE)),
                new AsciiString("zero"), new AsciiString("0"),
                new AsciiString("negative"), new AsciiString("-123"));

        assertEquals(Integer.MAX_VALUE, headers.getInt("maxInt").intValue());
        assertEquals(Integer.MIN_VALUE, headers.getInt("minInt").intValue());
        assertEquals(0, headers.getInt("zero").intValue());
        assertEquals(-123, headers.getInt("negative").intValue());
        assertEquals(100, headers.getInt("nonexistent", 100));
    }

    @Test
    public void testGetLongBoundaryValues() {
        Http2Headers headers = ReadOnlyHttp2Headers.trailers(false,
                new AsciiString("maxLong"), new AsciiString(String.valueOf(Long.MAX_VALUE)),
                new AsciiString("minLong"), new AsciiString(String.valueOf(Long.MIN_VALUE)),
                new AsciiString("zero"), new AsciiString("0"));

        assertEquals(Long.MAX_VALUE, headers.getLong("maxLong").longValue());
        assertEquals(Long.MIN_VALUE, headers.getLong("minLong").longValue());
        assertEquals(0L, headers.getLong("zero").longValue());
        assertEquals(100L, headers.getLong("nonexistent", 100L));
    }

    @Test
    public void testGetByteBoundaryValues() {
        Http2Headers headers = ReadOnlyHttp2Headers.trailers(false,
                new AsciiString("maxByte"), new AsciiString("127"),
                new AsciiString("minByte"), new AsciiString("-128"),
                new AsciiString("zero"), new AsciiString("0"));

        assertEquals(127, headers.getByte("maxByte").byteValue());
        assertEquals(-128, headers.getByte("minByte").byteValue());
        assertEquals(0, headers.getByte("zero").byteValue());
        assertEquals((byte) 10, headers.getByte("nonexistent", (byte) 10));
    }

    @Test
    public void testGetShortBoundaryValues() {
        Http2Headers headers = ReadOnlyHttp2Headers.trailers(false,
                new AsciiString("maxShort"), new AsciiString("32767"),
                new AsciiString("minShort"), new AsciiString("-32768"),
                new AsciiString("zero"), new AsciiString("0"));

        assertEquals(32767, headers.getShort("maxShort").shortValue());
        assertEquals(-32768, headers.getShort("minShort").shortValue());
        assertEquals(0, headers.getShort("zero").shortValue());
        assertEquals((short) 100, headers.getShort("nonexistent", (short) 100));
    }

    @Test
    public void testGetFloatValid() {
        Http2Headers headers = ReadOnlyHttp2Headers.trailers(false,
                new AsciiString("float"), new AsciiString("3.14"),
                new AsciiString("negative"), new AsciiString("-2.5"),
                new AsciiString("zero"), new AsciiString("0.0"));

        assertEquals(3.14f, headers.getFloat("float"), 0.001);
        assertEquals(-2.5f, headers.getFloat("negative"), 0.001);
        assertEquals(0.0f, headers.getFloat("zero"), 0.001);
        assertEquals(1.5f, headers.getFloat("nonexistent", 1.5f), 0.001);
    }

    @Test
    public void testGetDoubleValid() {
        Http2Headers headers = ReadOnlyHttp2Headers.trailers(false,
                new AsciiString("double"), new AsciiString("3.14159"),
                new AsciiString("negative"), new AsciiString("-9.876"),
                new AsciiString("zero"), new AsciiString("0.0"));

        assertEquals(3.14159, headers.getDouble("double"), 0.00001);
        assertEquals(-9.876, headers.getDouble("negative"), 0.00001);
        assertEquals(0.0, headers.getDouble("zero"), 0.00001);
        assertEquals(2.5, headers.getDouble("nonexistent", 2.5), 0.00001);
    }

    @Test
    public void testGetBooleanAllVariants() {
        Http2Headers headers = ReadOnlyHttp2Headers.trailers(false,
                new AsciiString("true1"), new AsciiString("true"),
                new AsciiString("true2"), new AsciiString("TRUE"),
                new AsciiString("false1"), new AsciiString("false"),
                new AsciiString("false2"), new AsciiString("FALSE"));

        assertTrue(headers.getBoolean("true1"));
        assertTrue(headers.getBoolean("true2"));
        assertFalse(headers.getBoolean("false1"));
        assertFalse(headers.getBoolean("false2"));
        assertTrue(headers.getBoolean("nonexistent", true));
        assertFalse(headers.getBoolean("nonexistent", false));
    }

    @Test
    public void testGetCharValid() {
        Http2Headers headers = ReadOnlyHttp2Headers.trailers(false,
                new AsciiString("char"), new AsciiString("A"));

        assertEquals('A', headers.getChar("char").charValue());
        assertEquals('X', headers.getChar("nonexistent", 'X'));
    }

    @Test
    public void testGetTimeMillisValid() {
        long currentTime = System.currentTimeMillis();
        Http2Headers headers = ReadOnlyHttp2Headers.trailers(false,
                new AsciiString("time"), new AsciiString(String.valueOf(currentTime)));


        assertNotNull(headers.getTimeMillis("time"));
        assertEquals(12345L, headers.getTimeMillis("nonexistent", 12345L));
    }

    // ============================================
    // Contains Methods - All Variants
    // ============================================

    @Test
    public void testContainsWithValueCaseInsensitive() {
        Http2Headers headers = ReadOnlyHttp2Headers.trailers(false,
                new AsciiString("name"), new AsciiString("Value"));

        assertTrue(headers.contains("name", "value", true));
        assertTrue(headers.contains("name", "VALUE", true));
        assertTrue(headers.contains("name", "Value", true));
        assertFalse(headers.contains("name", "value", false));
        assertTrue(headers.contains("name", "Value", false));
    }

    @Test
    public void testContainsObject() {
        Http2Headers headers = ReadOnlyHttp2Headers.trailers(false,
                new AsciiString("name"), new AsciiString("value"));

        assertTrue(headers.containsObject("name", "value"));
        assertTrue(headers.containsObject("name", new AsciiString("value")));
        assertFalse(headers.containsObject("name", "wrongValue"));
    }

    @Test
    public void testContainsBooleanTrue() {
        Http2Headers headers = ReadOnlyHttp2Headers.trailers(false,
                new AsciiString("bool"), new AsciiString("true"));

        assertTrue(headers.containsBoolean("bool", true));
        assertFalse(headers.containsBoolean("bool", false));
    }

    @Test
    public void testContainsBooleanFalse() {
        Http2Headers headers = ReadOnlyHttp2Headers.trailers(false,
                new AsciiString("bool"), new AsciiString("false"));

        assertTrue(headers.containsBoolean("bool", false));
        assertFalse(headers.containsBoolean("bool", true));
    }

    @Test
    public void testContainsByte() {
        Http2Headers headers = ReadOnlyHttp2Headers.trailers(false,
                new AsciiString("byte"), new AsciiString("42"));

        assertTrue(headers.containsByte("byte", (byte) 42));
        assertFalse(headers.containsByte("byte", (byte) 43));
    }

    @Test
    public void testContainsChar() {
        Http2Headers headers = ReadOnlyHttp2Headers.trailers(false,
                new AsciiString("char"), new AsciiString("A"));

        assertTrue(headers.containsChar("char", 'A'));
        assertFalse(headers.containsChar("char", 'B'));
    }

    @Test
    public void testContainsShort() {
        Http2Headers headers = ReadOnlyHttp2Headers.trailers(false,
                new AsciiString("short"), new AsciiString("1000"));

        assertTrue(headers.containsShort("short", (short) 1000));
        assertFalse(headers.containsShort("short", (short) 1001));
    }

    @Test
    public void testContainsIntPositiveAndNegative() {
        Http2Headers headers = ReadOnlyHttp2Headers.trailers(false,
                new AsciiString("positive"), new AsciiString("42"),
                new AsciiString("negative"), new AsciiString("-100"));

        assertTrue(headers.containsInt("positive", 42));
        assertFalse(headers.containsInt("positive", 43));
        assertTrue(headers.containsInt("negative", -100));
        assertFalse(headers.containsInt("negative", 100));
    }

    @Test
    public void testContainsLong() {
        Http2Headers headers = ReadOnlyHttp2Headers.trailers(false,
                new AsciiString("long"), new AsciiString("9223372036854775807"));

        assertTrue(headers.containsLong("long", 9223372036854775807L));
        assertFalse(headers.containsLong("long", 100L));
    }

    @Test
    public void testContainsFloatAlwaysFalse() {
        Http2Headers headers = ReadOnlyHttp2Headers.trailers(false,
                new AsciiString("float"), new AsciiString("3.14"));

        // Implementation always returns false for containsFloat
        assertFalse(headers.containsFloat("float", 3.14f));
    }

    @Test
    public void testContainsDouble() {
        Http2Headers headers = ReadOnlyHttp2Headers.trailers(false,
                new AsciiString("double"), new AsciiString("3.14"));

        assertTrue(headers.containsDouble("double", 3.14));
        assertFalse(headers.containsDouble("double", 2.5));
    }

    @Test
    public void testContainsTimeMillis() {
        Http2Headers headers = ReadOnlyHttp2Headers.trailers(false,
                new AsciiString("time"), new AsciiString("1234567890"));

        assertTrue(headers.containsTimeMillis("time", 1234567890L));
        assertFalse(headers.containsTimeMillis("time", 9876543210L));
    }

    // ============================================
    // Iterator Edge Cases
    // ============================================

    @Test
    public void testIteratorMultipleIterations() {
        Http2Headers headers = ReadOnlyHttp2Headers.trailers(false,
                new AsciiString("name1"), new AsciiString("value1"),
                new AsciiString("name2"), new AsciiString("value2"));

        int count1 = 0;
        for (Map.Entry<CharSequence, CharSequence> entry : headers) {
            count1++;
        }

        int count2 = 0;
        for (Map.Entry<CharSequence, CharSequence> entry : headers) {
            count2++;
        }

        assertEquals(count1, count2);
        assertEquals(2, count1);
    }

    @Test
    public void testIteratorHasNextMultipleCalls() {
        Http2Headers headers = ReadOnlyHttp2Headers.trailers(false,
                new AsciiString("name1"), new AsciiString("value1"));

        Iterator<Map.Entry<CharSequence, CharSequence>> iter = headers.iterator();
        assertTrue(iter.hasNext());
        assertTrue(iter.hasNext());
        assertTrue(iter.hasNext());

        iter.next();
        assertFalse(iter.hasNext());
        assertFalse(iter.hasNext());
    }

    @Test
    public void testIteratorNextWithoutHasNext() {
        Http2Headers headers = ReadOnlyHttp2Headers.trailers(false,
                new AsciiString("name1"), new AsciiString("value1"));

        Iterator<Map.Entry<CharSequence, CharSequence>> iter = headers.iterator();
        Map.Entry<CharSequence, CharSequence> entry = iter.next();
        assertEquals("name1", entry.getKey().toString());
        assertEquals("value1", entry.getValue().toString());
    }

    @Test
    public void testIteratorOnEmptyHeaders() {
        Http2Headers headers = ReadOnlyHttp2Headers.trailers(false);

        Iterator<Map.Entry<CharSequence, CharSequence>> iter = headers.iterator();
        assertFalse(iter.hasNext());
    }

    @Test
    public void testIteratorNextPastEnd() {
        final Http2Headers headers = ReadOnlyHttp2Headers.trailers(false,
                new AsciiString("name1"), new AsciiString("value1"));

        final Iterator<Map.Entry<CharSequence, CharSequence>> iter = headers.iterator();
        iter.next();

        assertThrows(NoSuchElementException.class, new Executable() {
            @Override
            public void execute() {
                iter.next();
            }
        });
    }

    @Test
    public void testValueIteratorMultipleValues() {
        Http2Headers headers = ReadOnlyHttp2Headers.trailers(false,
                new AsciiString("multi"), new AsciiString("value1"),
                new AsciiString("other"), new AsciiString("value2"),
                new AsciiString("multi"), new AsciiString("value3"),
                new AsciiString("multi"), new AsciiString("value4"));

        Iterator<CharSequence> iter = headers.valueIterator("multi");
        int count = 0;
        while (iter.hasNext()) {
            iter.next();
            count++;
        }

        assertEquals(3, count);
    }

    @Test
    public void testValueIteratorHasNextMultipleCalls() {
        Http2Headers headers = ReadOnlyHttp2Headers.trailers(false,
                new AsciiString("name1"), new AsciiString("value1"));

        Iterator<CharSequence> iter = headers.valueIterator("name1");
        assertTrue(iter.hasNext());
        assertTrue(iter.hasNext());
        assertTrue(iter.hasNext());

        iter.next();
        assertFalse(iter.hasNext());
        assertFalse(iter.hasNext());
    }

    // ============================================
    // Pseudo Header Tests
    // ============================================

    @Test
    public void testClientHeadersWithRegularHeaders() {
        Http2Headers headers = ReadOnlyHttp2Headers.clientHeaders(false,
                new AsciiString("GET"), new AsciiString("/"),
                new AsciiString("https"), new AsciiString("example.com"),
                new AsciiString("content-type"), new AsciiString("application/json"),
                new AsciiString("accept"), new AsciiString("*/*"));

        assertEquals(6, headers.size());
        assertEquals("GET", headers.method().toString());
        assertEquals("application/json", headers.get("content-type").toString());
    }

    @Test
    public void testServerHeadersWithRegularHeaders() {
        Http2Headers headers = ReadOnlyHttp2Headers.serverHeaders(false,
                new AsciiString("200"),
                new AsciiString("content-type"), new AsciiString("text/html"),
                new AsciiString("content-length"), new AsciiString("1234"));

        assertEquals(3, headers.size());
        assertEquals("200", headers.status().toString());
        assertEquals("text/html", headers.get("content-type").toString());
    }

    // ============================================
    // Names Method Tests
    // ============================================

    @Test
    public void testNamesWithDuplicates() {
        Http2Headers headers = ReadOnlyHttp2Headers.trailers(false,
                new AsciiString("name1"), new AsciiString("value1"),
                new AsciiString("name1"), new AsciiString("value2"),
                new AsciiString("name2"), new AsciiString("value3"));

        Set<CharSequence> names = headers.names();
        assertEquals(2, names.size());
        assertTrue(names.contains(new AsciiString("name1")));
        assertTrue(names.contains(new AsciiString("name2")));
    }

    @Test
    public void testNamesIncludesPseudoHeaders() {
        Http2Headers headers = ReadOnlyHttp2Headers.clientHeaders(false,
                new AsciiString("GET"), new AsciiString("/"),
                new AsciiString("https"), new AsciiString("example.com"),
                new AsciiString("custom"), new AsciiString("value"));

        Set<CharSequence> names = headers.names();
        assertEquals(5, names.size());
        assertTrue(names.contains(Http2Headers.PseudoHeaderName.METHOD.value()));
        assertTrue(names.contains(Http2Headers.PseudoHeaderName.PATH.value()));
        assertTrue(names.contains(Http2Headers.PseudoHeaderName.SCHEME.value()));
        assertTrue(names.contains(Http2Headers.PseudoHeaderName.AUTHORITY.value()));
        assertTrue(names.contains(new AsciiString("custom")));
    }

    // ============================================
    // toString Tests
    // ============================================

    @Test
    public void testToStringWithPseudoHeaders() {
        Http2Headers headers = ReadOnlyHttp2Headers.serverHeaders(false,
                new AsciiString("200"),
                new AsciiString("content-type"), new AsciiString("application/json"));

        String str = headers.toString();
        assertTrue(str.contains(":status: 200"));
        assertTrue(str.contains("content-type: application/json"));
    }

    // ============================================
    // All Mutation Methods Should Throw
    // ============================================

    @Test
    public void testAddIterableThrows() {
        final Http2Headers headers = ReadOnlyHttp2Headers.trailers(false);
        final List<CharSequence> values = java.util.Arrays.asList(new AsciiString("v1"));

        assertThrows(UnsupportedOperationException.class, new Executable() {
            @Override
            public void execute() {
                headers.add("name", values);
            }
        });
    }

    @Test
    public void testAddVarargsThrows() {
        final Http2Headers headers = ReadOnlyHttp2Headers.trailers(false);

        assertThrows(UnsupportedOperationException.class, new Executable() {
            @Override
            public void execute() {
                headers.add("name", "v1", "v2");
            }
        });
    }

    @Test
    public void testAddObjectThrows() {
        final Http2Headers headers = ReadOnlyHttp2Headers.trailers(false);

        assertThrows(UnsupportedOperationException.class, new Executable() {
            @Override
            public void execute() {
                headers.addObject("name", "value");
            }
        });
    }

    @Test
    public void testAddBooleanThrows() {
        final Http2Headers headers = ReadOnlyHttp2Headers.trailers(false);

        assertThrows(UnsupportedOperationException.class, new Executable() {
            @Override
            public void execute() {
                headers.addBoolean("name", true);
            }
        });
    }

    @Test
    public void testAddIntThrows() {
        final Http2Headers headers = ReadOnlyHttp2Headers.trailers(false);

        assertThrows(UnsupportedOperationException.class, new Executable() {
            @Override
            public void execute() {
                headers.addInt("name", 42);
            }
        });
    }

    @Test
    public void testAddLongThrows() {
        final Http2Headers headers = ReadOnlyHttp2Headers.trailers(false);

        assertThrows(UnsupportedOperationException.class, new Executable() {
            @Override
            public void execute() {
                headers.addLong("name", 123L);
            }
        });
    }

    @Test
    public void testSetObjectThrows() {
        final Http2Headers headers = ReadOnlyHttp2Headers.trailers(false);

        assertThrows(UnsupportedOperationException.class, new Executable() {
            @Override
            public void execute() {
                headers.setObject("name", "value");
            }
        });
    }

    @Test
    public void testSetBooleanThrows() {
        final Http2Headers headers = ReadOnlyHttp2Headers.trailers(false);

        assertThrows(UnsupportedOperationException.class, new Executable() {
            @Override
            public void execute() {
                headers.setBoolean("name", false);
            }
        });
    }

    @Test
    public void testSetIntThrows() {
        final Http2Headers headers = ReadOnlyHttp2Headers.trailers(false);

        assertThrows(UnsupportedOperationException.class, new Executable() {
            @Override
            public void execute() {
                headers.setInt("name", 42);
            }
        });
    }

    @Test
    public void testGetAllAndRemoveThrows() {
        final Http2Headers headers = ReadOnlyHttp2Headers.trailers(false,
                new AsciiString("name"), new AsciiString("value"));

        assertThrows(UnsupportedOperationException.class, new Executable() {
            @Override
            public void execute() {
                headers.getAllAndRemove("name");
            }
        });
    }

    @Test
    public void testGetBooleanAndRemoveThrows() {
        final Http2Headers headers = ReadOnlyHttp2Headers.trailers(false,
                new AsciiString("bool"), new AsciiString("true"));

        assertThrows(UnsupportedOperationException.class, new Executable() {
            @Override
            public void execute() {
                headers.getBooleanAndRemove("bool");
            }
        });
    }

    @Test
    public void testGetLongAndRemoveThrows() {
        final Http2Headers headers = ReadOnlyHttp2Headers.trailers(false,
                new AsciiString("long"), new AsciiString("123"));

        assertThrows(UnsupportedOperationException.class, new Executable() {
            @Override
            public void execute() {
                headers.getLongAndRemove("long");
            }
        });
    }

    // ============================================
    // Size Calculation Tests
    // ============================================

    @Test
    public void testSizeWithDuplicates() {
        Http2Headers headers = ReadOnlyHttp2Headers.trailers(false,
                new AsciiString("name"), new AsciiString("value1"),
                new AsciiString("name"), new AsciiString("value2"),
                new AsciiString("name"), new AsciiString("value3"));

        assertEquals(3, headers.size());
    }

    @Test
    public void testSizeCalculationMixed() {
        Http2Headers headers = ReadOnlyHttp2Headers.clientHeaders(false,
                new AsciiString("GET"), new AsciiString("/"),
                new AsciiString("https"), new AsciiString("example.com"),
                new AsciiString("h1"), new AsciiString("v1"),
                new AsciiString("h2"), new AsciiString("v2"),
                new AsciiString("h3"), new AsciiString("v3"));

        assertEquals(7, headers.size());
        assertFalse(headers.isEmpty());
    }

    // ============================================
    // Iterator Ordering Tests
    // ============================================

    @Test
    public void testIteratorOrderPseudoHeadersFirst() {
        Http2Headers headers = ReadOnlyHttp2Headers.clientHeaders(false,
                new AsciiString("GET"), new AsciiString("/"),
                new AsciiString("https"), new AsciiString("example.com"),
                new AsciiString("custom1"), new AsciiString("value1"),
                new AsciiString("custom2"), new AsciiString("value2"));

        Iterator<Map.Entry<CharSequence, CharSequence>> iter = headers.iterator();

        // First 4 should be pseudo headers
        for (int i = 0; i < 4; i++) {
            assertTrue(iter.hasNext());
            Map.Entry<CharSequence, CharSequence> entry = iter.next();
            assertTrue(entry.getKey().toString().startsWith(":"));
        }

        // Next 2 should be regular headers
        for (int i = 0; i < 2; i++) {
            assertTrue(iter.hasNext());
            Map.Entry<CharSequence, CharSequence> entry = iter.next();
            assertFalse(entry.getKey().toString().startsWith(":"));
        }

        assertFalse(iter.hasNext());
    }
}
