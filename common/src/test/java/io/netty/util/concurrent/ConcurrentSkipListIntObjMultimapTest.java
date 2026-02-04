/*
 * Copyright 2026 The Netty Project
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
package io.netty.util.concurrent;

import io.netty.util.concurrent.ConcurrentSkipListIntObjMultimap.IntEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcurrentSkipListIntObjMultimapTest {
    private ConcurrentSkipListIntObjMultimap<String> map;
    private int noKey;

    @BeforeEach
    void setUp() {
        noKey = -1;
        map = new ConcurrentSkipListIntObjMultimap<String>(noKey);
    }

    @Test
    void addIterateAndRemoveEntries() throws Exception {
        assertFalse(map.iterator().hasNext());
        map.put(1, "a");
        map.put(2, "b");
        assertFalse(map.isEmpty());
        assertEquals(2, map.size());
        IntEntry<String> entry;
        Iterator<IntEntry<String>> itr = map.iterator();
        assertTrue(itr.hasNext());
        entry = itr.next();
        itr.remove();
        assertEquals(new IntEntry<String>(1, "a"), entry);
        assertTrue(itr.hasNext());
        entry = itr.next();
        itr.remove();
        assertEquals(new IntEntry<String>(2, "b"), entry);
        assertFalse(itr.hasNext());
        assertTrue(map.isEmpty());
        assertEquals(0, map.size());
    }

    @Test
    void clearMustRemoveAllEntries() throws Exception {
        map.put(2, "b");
        map.put(1, "a");
        map.put(3, "c");
        assertEquals(3, map.size());
        map.clear();
        assertEquals(0, map.size());
        assertFalse(map.iterator().hasNext());
        assertTrue(map.isEmpty());
    }

    @Test
    void pollingFirstEntryOfUniqueKeys() throws Exception {
        map.put(2, "b");
        map.put(1, "a");
        map.put(3, "c");
        assertEquals(new IntEntry<String>(1, "a"), map.pollFirstEntry());
        assertEquals(new IntEntry<String>(2, "b"), map.pollFirstEntry());
        assertEquals(new IntEntry<String>(3, "c"), map.pollFirstEntry());
        assertTrue(map.isEmpty());
        assertEquals(0, map.size());
        assertFalse(map.iterator().hasNext());
    }

    @Test
    void pollingLastEntryOfUniqueKeys() throws Exception {
        map.put(2, "b");
        map.put(1, "a");
        map.put(3, "c");
        assertEquals(new IntEntry<String>(3, "c"), map.pollLastEntry());
        assertEquals(new IntEntry<String>(2, "b"), map.pollLastEntry());
        assertEquals(new IntEntry<String>(1, "a"), map.pollLastEntry());
        assertTrue(map.isEmpty());
        assertEquals(0, map.size());
        assertFalse(map.iterator().hasNext());
    }

    @Test
    void addMultipleEntriesForSameKey() throws Exception {
        map.put(2, "b1");
        map.put(1, "a");
        map.put(2, "b2"); // second entry for the 2 key
        map.put(3, "c");
        assertEquals(4, map.size());

        IntEntry<String> entry;
        Iterator<IntEntry<String>> itr = map.iterator();
        assertTrue(itr.hasNext());
        entry = itr.next();
        itr.remove();
        assertEquals(new IntEntry<String>(1, "a"), entry);
        assertTrue(itr.hasNext());
        entry = itr.next();
        IntEntry<String> otherB = entry;
        itr.remove();
        assertThat(entry).isIn(new IntEntry<String>(2, "b1"), new IntEntry<String>(2, "b2"));
        assertTrue(itr.hasNext());
        entry = itr.next();
        itr.remove();
        assertThat(entry).isIn(new IntEntry<String>(2, "b1"), new IntEntry<String>(2, "b2"));
        assertNotEquals(otherB, entry);
        assertTrue(itr.hasNext());
        entry = itr.next();
        itr.remove();
        assertEquals(new IntEntry<String>(3, "c"), entry);
        assertFalse(itr.hasNext());
        assertTrue(map.isEmpty());
        assertEquals(0, map.size());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void iteratorRemoveSecondOfMultiMappedEntry(boolean withPriorRemoval) throws Exception {
        map.put(1, "a");
        map.put(1, "b");

        Iterator<IntEntry<String>> itr = map.iterator();
        itr.next();
        IntEntry<String> entry = itr.next();
        if (withPriorRemoval) {
            map.remove(entry.getKey(), entry.getValue());
        }
        itr.remove();
        assertEquals(1, map.size());
        if (entry.equals(new IntEntry<>(1, "a"))) {
            assertEquals(new IntEntry<>(1, "b"), map.pollFirstEntry());
        } else {
            assertEquals(new IntEntry<>(1, "a"), map.pollFirstEntry());
        }
    }

    @Test
    void firstKeyOrEntry() throws Exception {
        assertEquals(noKey, map.firstKey());
        assertNull(map.firstEntry());
        map.put(2, "b");
        assertEquals(2, map.firstKey());
        assertEquals(new IntEntry<>(2, "b"), map.firstEntry());
        map.put(3, "c");
        assertEquals(2, map.firstKey());
        assertEquals(new IntEntry<>(2, "b"), map.firstEntry());
        map.put(2, "b2");
        assertEquals(2, map.firstKey());
        assertThat(map.firstEntry()).isIn(new IntEntry<>(2, "b"), new IntEntry<>(2, "b2"));
        map.put(1, "a");
        assertEquals(1, map.firstKey());
        assertEquals(new IntEntry<>(1, "a"), map.firstEntry());
        map.put(2, "b3");
        assertEquals(1, map.firstKey());
        assertEquals(new IntEntry<>(1, "a"), map.firstEntry());
        map.pollFirstEntry();
        assertEquals(2, map.firstKey());
        assertThat(map.firstEntry()).isIn(new IntEntry<>(2, "b"), new IntEntry<>(2, "b2"), new IntEntry<>(2, "b3"));
    }

    @Test
    void lastKeyOrEntry() throws Exception {
        assertEquals(noKey, map.lastKey());
        assertNull(map.lastEntry());
        map.put(2, "b");
        assertEquals(2, map.lastKey());
        assertEquals(new IntEntry<>(2, "b"), map.lastEntry());
        map.put(1, "a");
        assertEquals(2, map.lastKey());
        assertEquals(new IntEntry<>(2, "b"), map.lastEntry());
        map.put(2, "b2");
        assertEquals(2, map.lastKey());
        assertThat(map.lastEntry()).isIn(new IntEntry<>(2, "b"), new IntEntry<>(2, "b2"));
        map.put(3, "c");
        assertEquals(3, map.lastKey());
        assertEquals(new IntEntry<>(3, "c"), map.lastEntry());
        map.put(2, "b3");
        assertEquals(3, map.lastKey());
        assertEquals(new IntEntry<>(3, "c"), map.lastEntry());
        map.pollLastEntry();
        assertEquals(2, map.lastKey());
        assertThat(map.lastEntry()).isIn(new IntEntry<>(2, "b"), new IntEntry<>(2, "b2"), new IntEntry<>(2, "b3"));
    }

    @RepeatedTest(100)
    void firstLastKeyOrEntry() throws Exception {
        int[] xs = new int[50];
        for (int i = 0; i < xs.length; i++) {
            int key = ThreadLocalRandom.current().nextInt(50);
            map.put(key, "a");
            xs[i] = key;
        }
        Arrays.sort(xs);
        assertEquals(xs[0], map.firstKey());
        assertEquals(new IntEntry<>(xs[0], "a"), map.firstEntry());
        assertEquals(xs[xs.length - 1], map.lastKey());
        assertEquals(new IntEntry<>(xs[xs.length - 1], "a"), map.lastEntry());
    }

    @SuppressWarnings("unchecked")
    @RepeatedTest(100)
    void lowerEntryOrKey() {
        IntEntry<String>[] xs = new IntEntry[50];
        for (int i = 0; i < xs.length; i++) {
            int key = ThreadLocalRandom.current().nextInt(50);
            xs[i] = new IntEntry<>(key, String.valueOf(key));
            map.put(key, xs[i].getValue());
        }
        Arrays.sort(xs);
        for (int i = 0; i < 10; i++) {
            IntEntry<String> target = xs[ThreadLocalRandom.current().nextInt(xs.length)];
            IntEntry<String> expected = null;
            for (IntEntry<String> x : xs) {
                if (x.compareTo(target) < 0) {
                    expected = x;
                } else {
                    break;
                }
            }
            assertEquals(expected, map.lowerEntry(target.getKey()));
            assertEquals(expected == null ? noKey : expected.getKey(), map.lowerKey(target.getKey()));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void lowerEntryOrKeyMismatch(boolean multiMapped) throws Exception {
        map.put(1, "a");
        map.put(3, "b");
        map.put(4, "c");
        if (multiMapped) {
            map.put(1, "a");
            map.put(3, "b");
            map.put(4, "c");
        }
        assertEquals(1, map.lowerKey(3));
        assertEquals(new IntEntry<>(1, "a"), map.lowerEntry(3));
        assertEquals(3, map.lowerKey(4));
        assertEquals(new IntEntry<>(3, "b"), map.lowerEntry(4));
        assertEquals(noKey, map.lowerKey(1));
        assertNull(map.lowerEntry(1));
    }

    @SuppressWarnings("unchecked")
    @RepeatedTest(100)
    void floorEntryOrKey() {
        IntEntry<String>[] xs = new IntEntry[50];
        for (int i = 0; i < xs.length; i++) {
            int key = ThreadLocalRandom.current().nextInt(50);
            xs[i] = new IntEntry<>(key, String.valueOf(key));
            map.put(key, xs[i].getValue());
        }
        Arrays.sort(xs);
        for (int i = 0; i < 10; i++) {
            IntEntry<String> target = xs[ThreadLocalRandom.current().nextInt(xs.length)];
            IntEntry<String> expected = null;
            for (IntEntry<String> x : xs) {
                if (x.compareTo(target) <= 0) {
                    expected = x;
                } else {
                    break;
                }
            }
            assertEquals(expected, map.floorEntry(target.getKey()));
            assertEquals(expected == null ? noKey : expected.getKey(), map.floorKey(target.getKey()));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void floorEntryOrKeyMismatch(boolean multiMapped) throws Exception {
        map.put(1, "a");
        map.put(3, "b");
        map.put(4, "c");
        if (multiMapped) {
            map.put(1, "a");
            map.put(3, "b");
            map.put(4, "c");
        }
        assertEquals(1, map.floorKey(2));
        assertEquals(new IntEntry<>(1, "a"), map.floorEntry(2));
        assertEquals(3, map.floorKey(3));
        assertEquals(new IntEntry<>(3, "b"), map.floorEntry(3));
    }

    @SuppressWarnings("unchecked")
    @RepeatedTest(100)
    void ceilEntryOrKey() {
        IntEntry<String>[] xs = new IntEntry[50];
        for (int i = 0; i < xs.length; i++) {
            int key = ThreadLocalRandom.current().nextInt(50);
            xs[i] = new IntEntry<>(key, String.valueOf(key));
            map.put(key, xs[i].getValue());
        }
        Arrays.sort(xs);
        for (int i = 0; i < 10; i++) {
            IntEntry<String> target = xs[ThreadLocalRandom.current().nextInt(xs.length)];
            IntEntry<String> expected = null;
            for (IntEntry<String> x : xs) {
                if (x.compareTo(target) >= 0) {
                    expected = x;
                    break;
                }
            }
            assertEquals(expected, map.ceilingEntry(target.getKey()));
            assertEquals(expected == null ? noKey : expected.getKey(), map.ceilingKey(target.getKey()));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void ceilEntryOrKeyMismatch(boolean multiMapped) throws Exception {
        map.put(1, "a");
        map.put(2, "b");
        map.put(4, "c");
        if (multiMapped) {
            map.put(1, "a");
            map.put(2, "b");
            map.put(4, "c");
        }
        assertEquals(2, map.ceilingKey(2));
        assertEquals(new IntEntry<>(2, "b"), map.ceilingEntry(2));
        assertEquals(4, map.ceilingKey(3));
        assertEquals(new IntEntry<>(4, "c"), map.ceilingEntry(3));
    }

    @SuppressWarnings("unchecked")
    @RepeatedTest(100)
    void higherEntryOrKey() {
        IntEntry<String>[] xs = new IntEntry[50];
        for (int i = 0; i < xs.length; i++) {
            int key = ThreadLocalRandom.current().nextInt(50);
            xs[i] = new IntEntry<>(key, String.valueOf(key));
            map.put(key, xs[i].getValue());
        }
        Arrays.sort(xs);
        for (int i = 0; i < 10; i++) {
            IntEntry<String> target = xs[ThreadLocalRandom.current().nextInt(xs.length)];
            IntEntry<String> expected = null;
            for (IntEntry<String> x : xs) {
                if (x.compareTo(target) > 0) {
                    expected = x;
                    break;
                }
            }
            assertEquals(expected, map.higherEntry(target.getKey()));
            assertEquals(expected == null ? noKey : expected.getKey(), map.higherKey(target.getKey()));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void higherEntryOrKeyMismatch(boolean multiMapped) throws Exception {
        map.put(1, "a");
        map.put(2, "b");
        map.put(4, "c");
        if (multiMapped) {
            map.put(1, "a");
            map.put(2, "b");
            map.put(4, "c");
        }
        assertEquals(4, map.higherKey(2));
        assertEquals(new IntEntry<>(4, "c"), map.higherEntry(2));
        assertEquals(4, map.higherKey(3));
        assertEquals(new IntEntry<>(4, "c"), map.higherEntry(3));
        assertEquals(noKey, map.higherKey(4));
        assertNull(map.higherEntry(4));
    }

    @Test
    void pollingFirstEntryOfMultiMappedKeys() throws Exception {
        map.put(2, "b");
        map.put(1, "a");
        map.put(2, "b");
        map.put(3, "c");
        assertEquals(new IntEntry<String>(1, "a"), map.pollFirstEntry());
        assertEquals(new IntEntry<String>(2, "b"), map.pollFirstEntry());
        assertEquals(new IntEntry<String>(2, "b"), map.pollFirstEntry());
        assertEquals(new IntEntry<String>(3, "c"), map.pollFirstEntry());
        assertTrue(map.isEmpty());
        assertEquals(0, map.size());
        assertFalse(map.iterator().hasNext());
    }

    @Test
    void pollingLastEntryOfMultiMappedKeys() throws Exception {
        map.put(2, "b");
        map.put(1, "a");
        map.put(2, "b");
        map.put(3, "c");
        assertEquals(new IntEntry<String>(3, "c"), map.pollLastEntry());
        assertEquals(new IntEntry<String>(2, "b"), map.pollLastEntry());
        assertEquals(new IntEntry<String>(2, "b"), map.pollLastEntry());
        assertEquals(new IntEntry<String>(1, "a"), map.pollLastEntry());
        assertTrue(map.isEmpty());
        assertEquals(0, map.size());
        assertFalse(map.iterator().hasNext());
    }

    @Test
    void pollCeilingEntry() throws Exception {
        map.put(1, "a");
        map.put(2, "b");
        map.put(2, "b");
        map.put(3, "c");
        map.put(4, "d");
        map.put(4, "d");
        assertEquals(new IntEntry<>(2, "b"), map.pollCeilingEntry(2));
        assertEquals(new IntEntry<>(2, "b"), map.pollCeilingEntry(2));
        assertEquals(new IntEntry<>(3, "c"), map.pollCeilingEntry(2));
        assertEquals(new IntEntry<>(4, "d"), map.pollCeilingEntry(2));
        assertEquals(new IntEntry<>(4, "d"), map.pollCeilingEntry(2));
        assertNull(map.pollCeilingEntry(2));
        assertFalse(map.isEmpty());
        assertEquals(1, map.size());
    }
}
