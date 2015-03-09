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
package io.netty.util.collection;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Tests for {@link IntObjectHashMap}.
 */
public class IntObjectHashMapTest {

    private static class Value {
        private final String name;

        Value(String name) {
            this.name = name;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + (name == null ? 0 : name.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            Value other = (Value) obj;
            if (name == null) {
                if (other.name != null) {
                    return false;
                }
            } else if (!name.equals(other.name)) {
                return false;
            }
            return true;
        }
    }

    private IntObjectHashMap<Value> map;

    @Before
    public void setup() {
        map = new IntObjectHashMap<Value>();
    }

    @Test
    public void putNewMappingShouldSucceed() {
        Value v = new Value("v");
        assertNull(map.put(1, v));
        assertEquals(1, map.size());
        assertTrue(map.containsKey(1));
        assertTrue(map.containsValue(v));
        assertEquals(v, map.get(1));
    }

    @Test
    public void putShouldReplaceValue() {
        Value v1 = new Value("v1");
        assertNull(map.put(1, v1));

        // Replace the value.
        Value v2 = new Value("v2");
        assertSame(v1, map.put(1, v2));

        assertEquals(1, map.size());
        assertTrue(map.containsKey(1));
        assertTrue(map.containsValue(v2));
        assertEquals(v2, map.get(1));
    }

    @Test
    public void putShouldGrowMap() {
        for (int i = 0; i < 10000; ++i) {
            Value v = new Value(Integer.toString(i));
            assertNull(map.put(i, v));
            assertEquals(i + 1, map.size());
            assertTrue(map.containsKey(i));
            assertTrue(map.containsValue(v));
            assertEquals(v, map.get(i));
        }
    }

    @Test
    public void negativeKeyShouldSucceed() {
        Value v = new Value("v");
        map.put(-3, v);
        assertEquals(1, map.size());
        assertEquals(v, map.get(-3));
    }

    @Test
    public void removeMissingValueShouldReturnNull() {
        assertNull(map.remove(1));
        assertEquals(0, map.size());
    }

    @Test
    public void removeShouldReturnPreviousValue() {
        Value v = new Value("v");
        map.put(1, v);
        assertSame(v, map.remove(1));
    }

    /**
     * This test is a bit internal-centric. We're just forcing a rehash to occur based on no longer
     * having any FREE slots available. We do this by adding and then removing several keys up to
     * the capacity, so that no rehash is done. We then add one more, which will cause the rehash
     * due to a lack of free slots and verify that everything is still behaving properly
     */
    @Test
    public void noFreeSlotsShouldRehash() {
        for (int i = 0; i < 10; ++i) {
            map.put(i, new Value(Integer.toString(i)));
            // Now mark it as REMOVED so that size won't cause the rehash.
            map.remove(i);
            assertEquals(0, map.size());
        }

        // Now add an entry to force the rehash since no FREE slots are available in the map.
        Value v = new Value("v");
        map.put(1, v);
        assertEquals(1, map.size());
        assertSame(v, map.get(1));
    }

    @Test
    public void putAllShouldSucceed() {
        Value v1 = new Value("v1");
        Value v2 = new Value("v2");
        Value v3 = new Value("v3");
        map.put(1, v1);
        map.put(2, v2);
        map.put(3, v3);

        IntObjectHashMap<Value> map2 = new IntObjectHashMap<Value>();
        map2.putAll(map);
        assertEquals(3, map2.size());
        assertSame(v1, map2.get(1));
        assertSame(v2, map2.get(2));
        assertSame(v3, map2.get(3));
    }

    @Test
    public void clearShouldSucceed() {
        Value v1 = new Value("v1");
        Value v2 = new Value("v2");
        Value v3 = new Value("v3");
        map.put(1, v1);
        map.put(2, v2);
        map.put(3, v3);
        map.clear();
        assertEquals(0, map.size());
        assertTrue(map.isEmpty());
    }

    @Test
    public void containsValueShouldFindNull() {
        map.put(1, new Value("v1"));
        map.put(2, null);
        map.put(3, new Value("v2"));
        assertTrue(map.containsValue(null));
    }

    @Test
    public void containsValueShouldFindInstance() {
        Value v = new Value("v1");
        map.put(1, new Value("v2"));
        map.put(2, new Value("v3"));
        map.put(3, v);
        assertTrue(map.containsValue(v));
    }

    @Test
    public void containsValueShouldFindEquivalentValue() {
        map.put(1, new Value("v1"));
        map.put(2, new Value("v2"));
        map.put(3, new Value("v3"));
        assertTrue(map.containsValue(new Value("v2")));
    }

    @Test
    public void containsValueNotFindMissingValue() {
        map.put(1, new Value("v1"));
        map.put(2, new Value("v2"));
        map.put(3, new Value("v3"));
        assertFalse(map.containsValue(new Value("v4")));
    }

    @Test
    public void iteratorShouldTraverseEntries() {
        map.put(1, new Value("v1"));
        map.put(2, new Value("v2"));
        map.put(3, new Value("v3"));

        // Add and then immediately remove another entry.
        map.put(4, new Value("v4"));
        map.remove(4);

        Set<Integer> found = new HashSet<Integer>();
        for (IntObjectMap.Entry<Value> entry : map.entries()) {
            assertTrue(found.add(entry.key()));
        }
        assertEquals(3, found.size());
        assertTrue(found.contains(1));
        assertTrue(found.contains(2));
        assertTrue(found.contains(3));
    }

    @Test
    public void keysShouldBeReturned() {
        map.put(1, new Value("v1"));
        map.put(2, new Value("v2"));
        map.put(3, new Value("v3"));

        // Add and then immediately remove another entry.
        map.put(4, new Value("v4"));
        map.remove(4);

        int[] keys = map.keys();
        assertEquals(3, keys.length);

        Set<Integer> expected = new HashSet<Integer>();
        expected.add(1);
        expected.add(2);
        expected.add(3);

        Set<Integer> found = new HashSet<Integer>();
        for (int key : keys) {
            assertTrue(found.add(key));
        }
        assertEquals(expected, found);
    }

    @Test
    public void valuesShouldBeReturned() {
        Value v1 = new Value("v1");
        Value v2 = new Value("v2");
        Value v3 = new Value("v3");
        map.put(1, v1);
        map.put(2, v2);
        map.put(3, v3);

        // Add and then immediately remove another entry.
        map.put(4, new Value("v4"));
        map.remove(4);

        // Ensure values() return all values.
        Set<Value> expected = new HashSet<Value>();
        Set<Value> actual = new HashSet<Value>();

        expected.add(v1);
        expected.add(v2);
        expected.add(v3);

        Value[] valueArray = map.values(Value.class);
        assertEquals(3, valueArray.length);
        for (Value value : valueArray) {
            assertTrue(actual.add(value));
        }
        assertEquals(expected, actual);
        actual.clear();

        Collection<Value> valueCollection = map.values();
        assertEquals(3, valueCollection.size());
        for (Value value : valueCollection) {
            assertTrue(actual.add(value));
        }
        assertEquals(expected, actual);
    }

    @Test
    public void mapShouldSupportHashingConflicts() {
        for (int mod = 0; mod < 10; ++mod) {
            for (int sz = 1; sz <= 101; sz += 2) {
                IntObjectHashMap<String> map = new IntObjectHashMap<String>(sz);
                for (int i = 0; i < 100; ++i) {
                    map.put(i * mod, "");
                }
            }
        }
    }

    @Test
    public void hashcodeEqualsTest() {
        IntObjectHashMap<Integer> map1 = new IntObjectHashMap<Integer>();
        IntObjectHashMap<Integer> map2 = new IntObjectHashMap<Integer>();
        Random rnd = new Random(0);
        while (map1.size() < 100) {
            int key = rnd.nextInt(100);
            map1.put(key, key);
            map2.put(key, key);
        }
        assertEquals(map1.hashCode(), map2.hashCode());
        assertEquals(map1, map2);
        // Remove one "middle" element, maps should now be non-equals.
        int[] keys = map1.keys();
        map2.remove(keys[50]);
        assertFalse(map1.equals(map2));
        // Put it back; will likely be in a different position, but maps will be equal again.
        map2.put(keys[50], map1.keys()[50]);
        assertEquals(map1, map2);
        assertEquals(map1.hashCode(), map2.hashCode());
        // Make map2 have one extra element, will be non-equal.
        map2.put(1000, 1000);
        assertFalse(map1.equals(map2));
        // Rebuild map2 with elements in a different order, again the maps should be equal.
        // (These tests with same elements in different order also show that the hashCode
        // function does not depend on the internal ordering of entries.)
        map2.clear();
        Arrays.sort(keys);
        for (int key : keys) {
            map2.put(key, key);
        }
        assertEquals(map1.hashCode(), map2.hashCode());
        assertEquals(map1, map2);
    }

    @Test
    public void fuzzTest() {
        // This test is so extremely internals-dependent that I'm not even trying to
        // minimize that. Any internal changes will not fail the test (so it's not flaky per se)
        // but will possibly make it less effective (not test interesting scenarios anymore).

        // The RNG algorithm is specified and stable, so this will cause the same exact dataset
        // to be used in every run and every JVM implementation.
        Random rnd = new Random(0);

        int baseSize = 1000;
        // Empirically-determined size to expand the capacity exactly once, and before
        // the step that creates the long conflict chain. We need to test rehash(),
        // but also control when rehash happens because it cleans up the REMOVED entries.
        // This size is also chosen so after the single rehash, the map will be densely
        // populated, getting close to a second rehash but not triggering it.
        int startTableSize = 1105;
        IntObjectHashMap<Integer> map = new IntObjectHashMap<Integer>(startTableSize);
        // Reference map which implementation we trust to be correct, will mirror all operations.
        HashMap<Integer, Integer> goodMap = new HashMap<Integer, Integer>();

        // Add initial population.
        for (int i = 0; i < baseSize / 4; ++i) {
            int key = rnd.nextInt(baseSize);
            assertEquals(goodMap.put(key, key), map.put(key, key));
            // 50% elements are multiple of a divisor of startTableSize => more conflicts.
            key = rnd.nextInt(baseSize) * 17;
            assertEquals(goodMap.put(key, key), map.put(key, key));
        }

        // Now do some mixed adds and removes for further fuzzing
        // Rehash will happen here, but only once, and the final size will be closer to max.
        for (int i = 0; i < baseSize * 1000; ++i) {
            int key = rnd.nextInt(baseSize);
            if (rnd.nextDouble() >= 0.2) {
                assertEquals(goodMap.put(key, key), map.put(key, key));
            } else {
                assertEquals(goodMap.remove(key), map.remove(key));
            }
        }

        // Final batch of fuzzing, only searches and removes.
        int removeSize = map.size() / 2;
        while (removeSize > 0) {
            int key = rnd.nextInt(baseSize);
            boolean found = goodMap.containsKey(key);
            assertEquals(found, map.containsKey(key));
            assertEquals(goodMap.remove(key), map.remove(key));
            if (found) {
                --removeSize;
            }
        }

        // Now gotta write some code to compare the final maps, as equals() won't work.
        assertEquals(goodMap.size(), map.size());
        Integer[] goodKeys = goodMap.keySet().toArray(new Integer[goodMap.size()]);
        Arrays.sort(goodKeys);
        int [] keys = map.keys();
        Arrays.sort(keys);
        for (int i = 0; i < goodKeys.length; ++i) {
            assertEquals((int) goodKeys[i], keys[i]);
        }

        // Finally drain the map.
        for (int key : map.keys()) {
            assertEquals(goodMap.remove(key), map.remove(key));
        }
        assertTrue(map.isEmpty());
    }
}
