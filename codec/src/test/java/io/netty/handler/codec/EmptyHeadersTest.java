/*
 * Copyright 2018 The Netty Project
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EmptyHeadersTest {

    private static final TestEmptyHeaders HEADERS = new TestEmptyHeaders();

    @Test
    public void testAddStringValue() {
        assertThrows(UnsupportedOperationException.class, new Executable() {
            @Override
            public void execute() {
                HEADERS.add("name", "value");
            }
        });
    }

    @Test
    public void testAddStringValues() {
        assertThrows(UnsupportedOperationException.class, new Executable() {
            @Override
            public void execute() {
                HEADERS.add("name", "value1", "value2");
            }
        });
    }

    @Test
    public void testAddStringValuesIterable() {
        assertThrows(UnsupportedOperationException.class, new Executable() {
            @Override
            public void execute() {
                HEADERS.add("name", Arrays.asList("value1", "value2"));
            }
        });
    }

    @Test
    public void testAddBoolean() {
        assertThrows(UnsupportedOperationException.class, new Executable() {
            @Override
            public void execute() {
                HEADERS.addBoolean("name", true);
            }
        });
    }

    @Test
    public void testAddByte() {
        assertThrows(UnsupportedOperationException.class, new Executable() {
            @Override
            public void execute() {
                HEADERS.addByte("name", (byte) 1);
            }
        });
    }

    @Test
    public void testAddChar() {
        assertThrows(UnsupportedOperationException.class, new Executable() {
            @Override
            public void execute() {
                HEADERS.addChar("name", 'a');
            }
        });
    }

    @Test
    public void testAddDouble() {
        assertThrows(UnsupportedOperationException.class, new Executable() {
            @Override
            public void execute() {
                HEADERS.addDouble("name", 0);
            }
        });
    }

    @Test
    public void testAddFloat() {
        assertThrows(UnsupportedOperationException.class, new Executable() {
            @Override
            public void execute() {
                HEADERS.addFloat("name", 0);
            }
        });
    }

    @Test
    public void testAddInt() {
        assertThrows(UnsupportedOperationException.class, new Executable() {
            @Override
            public void execute() {
                HEADERS.addInt("name", 0);
            }
        });
    }

    @Test
    public void testAddLong() {
        assertThrows(UnsupportedOperationException.class, new Executable() {
            @Override
            public void execute() {
                HEADERS.addLong("name", 0);
            }
        });
    }

    @Test
    public void testAddShort() {
        assertThrows(UnsupportedOperationException.class, new Executable() {
            @Override
            public void execute() {
                HEADERS.addShort("name", (short) 0);
            }
        });
    }

    @Test
    public void testAddTimeMillis() {
        assertThrows(UnsupportedOperationException.class, new Executable() {
            @Override
            public void execute() {
                HEADERS.addTimeMillis("name", 0);
            }
        });
    }

    @Test
    public void testSetStringValue() {
        assertThrows(UnsupportedOperationException.class, new Executable() {
            @Override
            public void execute() {
                HEADERS.set("name", "value");
            }
        });
    }

    @Test
    public void testSetStringValues() {
        assertThrows(UnsupportedOperationException.class, new Executable() {
            @Override
            public void execute() {
                HEADERS.set("name", "value1", "value2");
            }
        });
    }

    @Test
    public void testSetStringValuesIterable() {
        assertThrows(UnsupportedOperationException.class, new Executable() {
            @Override
            public void execute() {
                HEADERS.set("name", Arrays.asList("value1", "value2"));
            }
        });
    }

    @Test
    public void testSetBoolean() {
        assertThrows(UnsupportedOperationException.class, new Executable() {
            @Override
            public void execute() {
                HEADERS.setBoolean("name", true);
            }
        });
    }

    @Test
    public void testSetByte() {
        assertThrows(UnsupportedOperationException.class, new Executable() {
            @Override
            public void execute() {
                HEADERS.setByte("name", (byte) 1);
            }
        });
    }

    @Test
    public void testSetChar() {
        assertThrows(UnsupportedOperationException.class, new Executable() {
            @Override
            public void execute() {
                HEADERS.setChar("name", 'a');
            }
        });
    }

    @Test
    public void testSetDouble() {
        assertThrows(UnsupportedOperationException.class, new Executable() {
            @Override
            public void execute() {
                HEADERS.setDouble("name", 0);
            }
        });
    }

    @Test
    public void testSetFloat() {
        assertThrows(UnsupportedOperationException.class, new Executable() {
            @Override
            public void execute() {
                HEADERS.setFloat("name", 0);
            }
        });
    }

    @Test
    public void testSetInt() {
        assertThrows(UnsupportedOperationException.class, new Executable() {
            @Override
            public void execute() {
                HEADERS.setInt("name", 0);
            }
        });
    }

    @Test
    public void testSetLong() {
        assertThrows(UnsupportedOperationException.class, new Executable() {
            @Override
            public void execute() {
                HEADERS.setLong("name", 0);
            }
        });
    }

    @Test
    public void testSetShort() {
        assertThrows(UnsupportedOperationException.class, new Executable() {
            @Override
            public void execute() {
                HEADERS.setShort("name", (short) 0);
            }
        });
    }

    @Test
    public void testSetTimeMillis() {
        assertThrows(UnsupportedOperationException.class, new Executable() {
            @Override
            public void execute() {
                HEADERS.setTimeMillis("name", 0);
            }
        });
    }

    @Test
    public void testSetAll() {
        assertThrows(UnsupportedOperationException.class, new Executable() {
            @Override
            public void execute() {
                HEADERS.setAll(new TestEmptyHeaders());
            }
        });
    }

    @Test
    public void testSet() {
        assertThrows(UnsupportedOperationException.class, new Executable() {
            @Override
            public void execute() {
                HEADERS.set(new TestEmptyHeaders());
            }
        });
    }

    @Test
    public void testGet() {
        assertNull(HEADERS.get("name1"));
    }

    @Test
    public void testGetDefault() {
        assertEquals("default", HEADERS.get("name1", "default"));
    }

    @Test
    public void testGetAndRemove() {
        assertNull(HEADERS.getAndRemove("name1"));
    }

    @Test
    public void testGetAndRemoveDefault() {
        assertEquals("default", HEADERS.getAndRemove("name1", "default"));
    }

    @Test
    public void testGetAll() {
        assertEquals(Collections.emptyList(), HEADERS.getAll("name1"));
    }

    @Test
    public void testGetAllAndRemove() {
        assertEquals(Collections.emptyList(), HEADERS.getAllAndRemove("name1"));
    }

    @Test
    public void testGetBoolean() {
        assertNull(HEADERS.getBoolean("name1"));
    }

    @Test
    public void testGetBooleanDefault() {
        assertTrue(HEADERS.getBoolean("name1", true));
    }

    @Test
    public void testGetBooleanAndRemove() {
        assertNull(HEADERS.getBooleanAndRemove("name1"));
    }

    @Test
    public void testGetBooleanAndRemoveDefault() {
        assertTrue(HEADERS.getBooleanAndRemove("name1", true));
    }

    @Test
    public void testGetByte() {
        assertNull(HEADERS.getByte("name1"));
    }

    @Test
    public void testGetByteDefault() {
        assertEquals((byte) 0, HEADERS.getByte("name1", (byte) 0));
    }

    @Test
    public void testGetByteAndRemove() {
        assertNull(HEADERS.getByteAndRemove("name1"));
    }

    @Test
    public void testGetByteAndRemoveDefault() {
        assertEquals((byte) 0, HEADERS.getByteAndRemove("name1", (byte) 0));
    }

    @Test
    public void testGetChar() {
        assertNull(HEADERS.getChar("name1"));
    }

    @Test
    public void testGetCharDefault() {
        assertEquals('x', HEADERS.getChar("name1", 'x'));
    }

    @Test
    public void testGetCharAndRemove() {
        assertNull(HEADERS.getCharAndRemove("name1"));
    }

    @Test
    public void testGetCharAndRemoveDefault() {
        assertEquals('x', HEADERS.getCharAndRemove("name1", 'x'));
    }

    @Test
    public void testGetDouble() {
        assertNull(HEADERS.getDouble("name1"));
    }

    @Test
    public void testGetDoubleDefault() {
        assertEquals(1, HEADERS.getDouble("name1", 1), 0);
    }

    @Test
    public void testGetDoubleAndRemove() {
        assertNull(HEADERS.getDoubleAndRemove("name1"));
    }

    @Test
    public void testGetDoubleAndRemoveDefault() {
        assertEquals(1, HEADERS.getDoubleAndRemove("name1", 1), 0);
    }

    @Test
    public void testGetFloat() {
        assertNull(HEADERS.getFloat("name1"));
    }

    @Test
    public void testGetFloatDefault() {
        assertEquals(1, HEADERS.getFloat("name1", 1), 0);
    }

    @Test
    public void testGetFloatAndRemove() {
        assertNull(HEADERS.getFloatAndRemove("name1"));
    }

    @Test
    public void testGetFloatAndRemoveDefault() {
        assertEquals(1, HEADERS.getFloatAndRemove("name1", 1), 0);
    }

    @Test
    public void testGetInt() {
        assertNull(HEADERS.getInt("name1"));
    }

    @Test
    public void testGetIntDefault() {
        assertEquals(1, HEADERS.getInt("name1", 1));
    }

    @Test
    public void testGetIntAndRemove() {
        assertNull(HEADERS.getIntAndRemove("name1"));
    }

    @Test
    public void testGetIntAndRemoveDefault() {
        assertEquals(1, HEADERS.getIntAndRemove("name1", 1));
    }

    @Test
    public void testGetLong() {
        assertNull(HEADERS.getLong("name1"));
    }

    @Test
    public void testGetLongDefault() {
        assertEquals(1, HEADERS.getLong("name1", 1));
    }

    @Test
    public void testGetLongAndRemove() {
        assertNull(HEADERS.getLongAndRemove("name1"));
    }

    @Test
    public void testGetLongAndRemoveDefault() {
        assertEquals(1, HEADERS.getLongAndRemove("name1", 1));
    }

    @Test
    public void testGetShort() {
        assertNull(HEADERS.getShort("name1"));
    }

    @Test
    public void testGetShortDefault() {
        assertEquals(1, HEADERS.getShort("name1", (short) 1));
    }

    @Test
    public void testGetShortAndRemove() {
        assertNull(HEADERS.getShortAndRemove("name1"));
    }

    @Test
    public void testGetShortAndRemoveDefault() {
        assertEquals(1, HEADERS.getShortAndRemove("name1", (short) 1));
    }

    @Test
    public void testGetTimeMillis() {
        assertNull(HEADERS.getTimeMillis("name1"));
    }

    @Test
    public void testGetTimeMillisDefault() {
        assertEquals(1, HEADERS.getTimeMillis("name1", 1));
    }

    @Test
    public void testGetTimeMillisAndRemove() {
        assertNull(HEADERS.getTimeMillisAndRemove("name1"));
    }

    @Test
    public void testGetTimeMillisAndRemoveDefault() {
        assertEquals(1, HEADERS.getTimeMillisAndRemove("name1", 1));
    }

    @Test
    public void testContains() {
        assertFalse(HEADERS.contains("name1"));
    }

    @Test
    public void testContainsWithValue() {
        assertFalse(HEADERS.contains("name1", "value1"));
    }

    @Test
    public void testContainsBoolean() {
        assertFalse(HEADERS.containsBoolean("name1", false));
    }

    @Test
    public void testContainsByte() {
        assertFalse(HEADERS.containsByte("name1", (byte) 'x'));
    }

    @Test
    public void testContainsChar() {
        assertFalse(HEADERS.containsChar("name1", 'x'));
    }

    @Test
    public void testContainsDouble() {
        assertFalse(HEADERS.containsDouble("name1", 1));
    }

    @Test
    public void testContainsFloat() {
        assertFalse(HEADERS.containsFloat("name1", 1));
    }

    @Test
    public void testContainsInt() {
        assertFalse(HEADERS.containsInt("name1", 1));
    }

    @Test
    public void testContainsLong() {
        assertFalse(HEADERS.containsLong("name1", 1));
    }

    @Test
    public void testContainsShort() {
        assertFalse(HEADERS.containsShort("name1", (short) 1));
    }

    @Test
    public void testContainsTimeMillis() {
        assertFalse(HEADERS.containsTimeMillis("name1", 1));
    }

    @Test
    public void testContainsObject() {
        assertFalse(HEADERS.containsObject("name1", ""));
    }

    @Test
    public void testIsEmpty() {
        assertTrue(HEADERS.isEmpty());
    }

    @Test
    public void testClear() {
        assertSame(HEADERS, HEADERS.clear());
    }

    @Test
    public void testSize() {
        assertEquals(0, HEADERS.size());
    }

    @Test
    public void testValueIterator() {
        assertFalse(HEADERS.valueIterator("name1").hasNext());
    }

    private static final class TestEmptyHeaders extends EmptyHeaders<String, String, TestEmptyHeaders> { }
}
