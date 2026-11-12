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
package io.netty.util.internal;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Run in a separate thread, so that a non-terminating loop (e.g. an iterator that never advances) fails the test
// instead of hanging the build.
@Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class WeakIdentityHashMapTest {

    /**
     * All instances are equal to each other and have the same hash code, so any accidental use of
     * {@link Object#equals(Object)} or {@link Object#hashCode()} instead of identity is detected.
     */
    private static final class EqualKey {
        @Override
        public boolean equals(Object obj) {
            return obj instanceof EqualKey;
        }

        @Override
        public int hashCode() {
            return 42;
        }

        @Override
        public String toString() {
            return "EqualKey@" + Integer.toHexString(System.identityHashCode(this));
        }
    }

    @Test
    public void newMapIsEmpty() {
        WeakIdentityHashMap<Object, Object> map = new WeakIdentityHashMap<>();
        assertEquals(0, map.size());
        assertTrue(map.isEmpty());
        assertFalse(map.entrySet().iterator().hasNext());
    }

    @Test
    public void putGetContainsKeyAndRemove() {
        WeakIdentityHashMap<Object, Object> map = new WeakIdentityHashMap<>();
        Object key = new Object();
        Object value1 = new Object();
        Object value2 = new Object();

        assertNull(map.put(key, value1));
        assertSame(value1, map.get(key));
        assertTrue(map.containsKey(key));
        assertEquals(1, map.size());

        assertSame(value1, map.put(key, value2));
        assertSame(value2, map.get(key));
        assertEquals(1, map.size());

        assertSame(value2, map.remove(key));
        assertNull(map.get(key));
        assertFalse(map.containsKey(key));
        assertEquals(0, map.size());
        assertTrue(map.isEmpty());
    }

    @Test
    public void removeOfAbsentKeyReturnsNull() {
        WeakIdentityHashMap<Object, Object> map = new WeakIdentityHashMap<>();
        List<Object> keys = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            Object key = new Object();
            keys.add(key);
            map.put(key, new Object());
        }
        for (int i = 0; i < 1000; i++) {
            assertNull(map.remove(new Object()));
        }
        assertEquals(keys.size(), map.size());
    }

    @Test
    public void keysAreComparedByIdentity() {
        WeakIdentityHashMap<Object, Object> map = new WeakIdentityHashMap<>();
        EqualKey a = new EqualKey();
        EqualKey b = new EqualKey();
        assertEquals(a, b);

        map.put(a, "a");
        assertNull(map.get(b));
        assertFalse(map.containsKey(b));

        map.put(b, "b");
        assertEquals(2, map.size());
        assertEquals("a", map.get(a));
        assertEquals("b", map.get(b));

        assertEquals("a", map.remove(a));
        assertNull(map.get(a));
        assertEquals("b", map.get(b));
    }

    @Test
    public void nullKeysAndValuesAreRejected() {
        WeakIdentityHashMap<Object, Object> map = new WeakIdentityHashMap<>();
        Object key = new Object();
        assertThrows(NullPointerException.class, () -> map.put(null, "value"));
        assertThrows(NullPointerException.class, () -> map.put(key, null));
        assertThrows(NullPointerException.class, () -> map.get(null));
        assertThrows(NullPointerException.class, () -> map.containsKey(null));
        assertThrows(NullPointerException.class, () -> map.remove(null));
        assertTrue(map.isEmpty());
    }

    @Test
    public void growsBeyondInitialCapacity() {
        WeakIdentityHashMap<Object, Object> map = new WeakIdentityHashMap<>();
        IdentityHashMap<Object, Object> oracle = new IdentityHashMap<>();
        for (int i = 0; i < 5000; i++) {
            Object key = new EqualKey();
            Object value = new Object();
            assertNull(map.put(key, value));
            oracle.put(key, value);
            assertEquals(oracle.size(), map.size(), "size after " + (i + 1) + " puts");
        }
        for (Map.Entry<Object, Object> entry : oracle.entrySet()) {
            assertSame(entry.getValue(), map.get(entry.getKey()));
        }
        assertEquals(oracle, snapshot(map, oracle.size(), "after growth"));
    }

    @Test
    public void entrySetIteratesAllEntriesOnce() {
        WeakIdentityHashMap<Object, Object> map = new WeakIdentityHashMap<>();
        IdentityHashMap<Object, Object> oracle = new IdentityHashMap<>();
        for (int i = 0; i < 20; i++) {
            Object key = new EqualKey();
            Object value = new Object();
            map.put(key, value);
            oracle.put(key, value);
        }
        assertEquals(oracle, snapshot(map, oracle.size(), "iteration"));
        assertEquals(oracle.size(), map.entrySet().size());
    }

    @Test
    public void entrySetIteratorThrowsWhenExhausted() {
        WeakIdentityHashMap<Object, Object> map = new WeakIdentityHashMap<>();
        Object key = new Object();
        map.put(key, "value");
        Iterator<Map.Entry<Object, Object>> iterator = map.entrySet().iterator();
        assertTrue(iterator.hasNext());
        Map.Entry<Object, Object> entry = iterator.next();
        assertSame(key, entry.getKey());
        assertEquals("value", entry.getValue());
        assertFalse(iterator.hasNext());
        assertThrows(NoSuchElementException.class, iterator::next);
    }

    @Test
    public void entrySetValueWritesThrough() {
        WeakIdentityHashMap<Object, Object> map = new WeakIdentityHashMap<>();
        Object key = new Object();
        map.put(key, "old");
        Map.Entry<Object, Object> entry = map.entrySet().iterator().next();
        assertEquals("old", entry.setValue("new"));
        assertEquals("new", entry.getValue());
        assertEquals("new", map.get(key));
        assertThrows(NullPointerException.class, () -> entry.setValue(null));
    }

    @Test
    public void containsValue() {
        WeakIdentityHashMap<Object, Object> map = new WeakIdentityHashMap<>();
        Object key = new Object();
        Object value = new Object();
        assertFalse(map.containsValue(value));
        map.put(key, value);
        assertTrue(map.containsValue(value));
        assertFalse(map.containsValue(new Object()));
    }

    @Test
    public void clearRemovesAllEntries() {
        WeakIdentityHashMap<Object, Object> map = new WeakIdentityHashMap<>();
        List<Object> keys = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            Object key = new Object();
            keys.add(key);
            map.put(key, new Object());
        }
        map.clear();
        assertTrue(map.isEmpty());
        for (Object key : keys) {
            assertFalse(map.containsKey(key));
        }
    }

    @Test
    public void computeIfAbsentOnlyComputesOnce() {
        // This is how SecureObjectIdentityHash uses the map.
        WeakIdentityHashMap<Object, Object> map = new WeakIdentityHashMap<>();
        Object key = new Object();
        Object first = map.computeIfAbsent(key, k -> new Object());
        Object second = map.computeIfAbsent(key, k -> {
            throw new AssertionError("must not be called for a present key");
        });
        assertSame(first, second);
        assertEquals(1, map.size());
    }

    @Test
    public void iteratorRemoveRemovesLastReturnedEntry() {
        WeakIdentityHashMap<Object, Object> map = new WeakIdentityHashMap<>();
        IdentityHashMap<Object, Object> oracle = new IdentityHashMap<>();
        for (int i = 0; i < 100; i++) {
            Object key = new EqualKey();
            Object value = new Object();
            map.put(key, value);
            oracle.put(key, value);
        }
        Iterator<Map.Entry<Object, Object>> iterator = map.entrySet().iterator();
        int seen = 0;
        while (iterator.hasNext()) {
            Map.Entry<Object, Object> entry = iterator.next();
            seen++;
            Object key = entry.getKey();
            if (seen % 3 == 0) {
                iterator.remove();
                assertSame(entry.getValue(), oracle.remove(key));
                assertFalse(map.containsKey(key));
                assertNull(map.get(key));
            } else {
                assertSame(oracle.get(key), map.get(key));
            }
            assertEquals(oracle.size(), map.size());
        }
        assertEquals(100, seen);
        assertEquals(oracle, snapshot(map, oracle.size(), "after iterator removal"));
    }

    @Test
    public void iteratorRemoveCanEmptyTheMap() {
        WeakIdentityHashMap<Object, Object> map = new WeakIdentityHashMap<>();
        List<Object> keys = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            Object key = new Object();
            keys.add(key);
            map.put(key, new Object());
        }
        int removed = 0;
        for (Iterator<Map.Entry<Object, Object>> it = map.entrySet().iterator(); it.hasNext();) {
            it.next();
            it.remove();
            removed++;
        }
        assertEquals(keys.size(), removed);
        assertTrue(map.isEmpty());
        assertFalse(map.entrySet().iterator().hasNext());
        for (Object key : keys) {
            assertFalse(map.containsKey(key));
        }
    }

    @Test
    public void iteratorRemoveWithoutNextThrows() {
        WeakIdentityHashMap<Object, Object> map = new WeakIdentityHashMap<>();
        map.put(new Object(), "value");
        Iterator<Map.Entry<Object, Object>> iterator = map.entrySet().iterator();
        assertThrows(IllegalStateException.class, iterator::remove);
        assertEquals(1, map.size());
    }

    @Test
    public void iteratorRemoveTwiceThrows() {
        WeakIdentityHashMap<Object, Object> map = new WeakIdentityHashMap<>();
        Object key1 = new Object();
        Object key2 = new Object();
        map.put(key1, "value1");
        map.put(key2, "value2");
        Iterator<Map.Entry<Object, Object>> iterator = map.entrySet().iterator();
        iterator.next();
        iterator.remove();
        assertThrows(IllegalStateException.class, iterator::remove);
        assertEquals(1, map.size());
    }

    @Test
    public void iteratorRemoveOfEntryAlreadyRemovedFromMapDoesNotChangeSize() {
        WeakIdentityHashMap<Object, Object> map = new WeakIdentityHashMap<>();
        List<Object> keys = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Object key = new Object();
            keys.add(key);
            map.put(key, new Object());
        }
        Iterator<Map.Entry<Object, Object>> iterator = map.entrySet().iterator();
        Object key = iterator.next().getKey();
        assertNotNull(map.remove(key));
        assertEquals(9, map.size());
        iterator.remove();
        assertEquals(9, map.size());
    }

    @Test
    public void viewsRemoveIfBehavesLikeIdentityHashMap() {
        WeakIdentityHashMap<Object, Object> map = new WeakIdentityHashMap<>();
        IdentityHashMap<Object, Object> oracle = new IdentityHashMap<>();
        List<Object> keys = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            values.add(new Object());
        }
        for (int i = 0; i < 300; i++) {
            Object key = new EqualKey();
            Object value = values.get(i % values.size());
            keys.add(key);
            map.put(key, value);
            oracle.put(key, value);
        }

        Set<Object> removedKeys = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int i = 0; i < keys.size(); i += 7) {
            removedKeys.add(keys.get(i));
        }
        assertEquals(oracle.keySet().removeIf(removedKeys::contains), map.keySet().removeIf(removedKeys::contains));
        assertEquals(oracle, snapshot(map, oracle.size(), "keySet().removeIf"));

        Object removedValue = values.get(3);
        assertEquals(oracle.values().removeIf(v -> v == removedValue),
                map.values().removeIf(v -> v == removedValue));
        assertEquals(oracle, snapshot(map, oracle.size(), "values().removeIf"));

        Object otherRemovedValue = values.get(5);
        assertEquals(oracle.entrySet().removeIf(e -> e.getValue() == otherRemovedValue),
                map.entrySet().removeIf(e -> e.getValue() == otherRemovedValue));
        assertEquals(oracle, snapshot(map, oracle.size(), "entrySet().removeIf"));

        assertFalse(map.keySet().removeIf(k -> false));
        assertEquals(oracle.size(), map.size());
    }

    @Test
    public void viewsClearEmptiesTheMap() {
        for (int view = 0; view < 3; view++) {
            WeakIdentityHashMap<Object, Object> map = new WeakIdentityHashMap<>();
            List<Object> keys = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                Object key = new Object();
                keys.add(key);
                map.put(key, new Object());
            }
            if (view == 0) {
                map.entrySet().clear();
            } else if (view == 1) {
                map.keySet().clear();
            } else {
                map.values().clear();
            }
            assertTrue(map.isEmpty(), "view " + view);
            for (Object key : keys) {
                assertFalse(map.containsKey(key), "view " + view);
            }
        }
    }

    @Test
    public void viewRemovalUsesIdentity() {
        WeakIdentityHashMap<Object, Object> map = new WeakIdentityHashMap<>();
        IdentityHashMap<Object, Object> oracle = new IdentityHashMap<>();
        EqualKey key = new EqualKey();
        EqualKey equalKey = new EqualKey();
        Object value = new Object();
        map.put(key, value);
        oracle.put(key, value);

        // IdentityHashMap compares keys by identity in its views too, so an equal, but different, key must not
        // remove anything.
        assertEquals(oracle.keySet().remove(equalKey),
            map.keySet().remove(equalKey),
            "keySet().remove");
        assertEquals(oracle.entrySet().remove(new AbstractMap.SimpleEntry<>(equalKey, value)),
                map.entrySet().remove(new AbstractMap.SimpleEntry<>(equalKey, value)), "entrySet().remove");
        assertSame(value, map.get(key));

        assertTrue(map.keySet().remove(key));
        assertTrue(map.isEmpty());
    }

    @RepeatedTest(50)
    public void behavesLikeIdentityHashMap() {
        long seed = ThreadLocalRandom.current().nextLong();
        Random random = new Random(seed);
        WeakIdentityHashMap<Object, Object> map = new WeakIdentityHashMap<>();
        IdentityHashMap<Object, Object> oracle = new IdentityHashMap<>();

        // The pools keep all keys strongly reachable, so no entry can be collected while the test runs.
        List<Object> keys = new ArrayList<>();
        int keyCount = 1 + random.nextInt(500);
        for (int i = 0; i < keyCount; i++) {
            keys.add(random.nextBoolean() ? new EqualKey() : new Object());
        }
        List<Object> values = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            values.add(new Object());
        }

        int operations = 5000;
        for (int op = 0; op < operations; op++) {
            Object key = keys.get(random.nextInt(keys.size()));
            Object value = values.get(random.nextInt(values.size()));
            String ctx = "seed=" + seed + ", op=" + op;
            switch (random.nextInt(10)) {
                case 0:
                case 1:
                case 2:
                    assertSame(oracle.put(key, value), map.put(key, value), ctx + ": put");
                    break;
                case 3:
                    assertSame(oracle.get(key), map.get(key), ctx + ": get");
                    break;
                case 4:
                    assertEquals(oracle.containsKey(key), map.containsKey(key), ctx + ": containsKey");
                    break;
                case 5:
                case 6:
                    assertSame(oracle.remove(key), map.remove(key), ctx + ": remove");
                    break;
                case 7:
                    assertSame(oracle.putIfAbsent(key, value), map.putIfAbsent(key, value), ctx + ": putIfAbsent");
                    break;
                case 8:
                    assertSame(oracle.computeIfAbsent(key, k -> value), map.computeIfAbsent(key, k -> value),
                            ctx + ": computeIfAbsent");
                    break;
                case 9: {
                    // Remove a random subset of the entries through the iterator.
                    int expectedEntries = oracle.size();
                    int seen = 0;
                    Iterator<Map.Entry<Object, Object>> it = map.entrySet().iterator();
                    while (it.hasNext()) {
                        seen++;
                        assertThat(seen).as(ctx + ": iterator returned more entries than expected")
                                .isLessThanOrEqualTo(expectedEntries);
                        Map.Entry<Object, Object> entry = it.next();
                        Object entryKey = entry.getKey();
                        assertNotNull(entryKey, ctx + ": iterator returned an entry with a collected key");
                        assertSame(oracle.get(entryKey), entry.getValue(), ctx + ": iterator value");
                        if (random.nextInt(20) == 0) {
                            it.remove();
                            oracle.remove(entryKey);
                        }
                    }
                    assertEquals(expectedEntries, seen, ctx + ": iterator entry count");
                    break;
                }
                default:
                    throw new AssertionError();
            }
            assertEquals(oracle.size(), map.size(), ctx + ": size");
            assertEquals(oracle.isEmpty(), map.isEmpty(), ctx + ": isEmpty");
            if (op % 500 == 0) {
                assertEquals(oracle, snapshot(map, oracle.size(), ctx), ctx + ": entrySet");
            }
        }
        String ctx = "seed=" + seed + ", end";
        assertEquals(oracle, snapshot(map, oracle.size(), ctx), ctx + ": entrySet");
        for (Object key : keys) {
            assertSame(oracle.get(key), map.get(key), ctx + ": get");
        }
    }

    @Test
    public void keysAreWeaklyHeld() throws InterruptedException {
        WeakIdentityHashMap<Object, Object> map = new WeakIdentityHashMap<>();
        Object strongKey = new Object();
        Object strongValue = new Object();
        map.put(strongKey, strongValue);
        for (int i = 0; i < 100; i++) {
            putUnreachableKey(map);
        }
        assertEquals(101, map.size());

        awaitGc(() -> map.size() == 1);

        assertEquals(1, map.size());
        assertSame(strongValue, map.get(strongKey));
        assertEquals(1, snapshot(map, 1, "after gc").size());
    }

    @Test
    public void collectedKeysDoNotBreakGrowth() throws InterruptedException {
        WeakIdentityHashMap<Object, Object> map = new WeakIdentityHashMap<>();
        for (int i = 0; i < 1000; i++) {
            putUnreachableKey(map);
        }
        awaitGc(() -> map.size() == 0);

        // Collected keys may still be in the table, if they were not yet enqueued when the map last cleaned up.
        // Growing the table must cope with them.
        List<Object> keys = new ArrayList<>();
        for (int round = 0; round < 10; round++) {
            for (int i = 0; i < 1000; i++) {
                putUnreachableKey(map);
            }
            System.gc();
            for (int i = 0; i < 1000; i++) {
                Object key = new Object();
                keys.add(key);
                map.put(key, "strong");
            }
        }
        for (Object key : keys) {
            assertEquals("strong", map.get(key));
        }
        awaitGc(() -> map.size() == keys.size());
    }

    // In a separate method, so the key is guaranteed to be unreachable once this method returns.
    private static void putUnreachableKey(Map<Object, Object> map) {
        map.put(new Object(), "weak");
    }

    private static void awaitGc(BooleanSupplier condition) throws InterruptedException {
        while (!condition.getAsBoolean()) {
            System.gc();
            Thread.sleep(10);
        }
    }

    /**
     * Copy the contents of the map by iterating its entry set, while guarding against iterators that never end or
     * return entries more than once.
     */
    private static IdentityHashMap<Object, Object> snapshot(
            WeakIdentityHashMap<Object, Object> map, int expectedSize, String ctx) {
        IdentityHashMap<Object, Object> copy = new IdentityHashMap<>();
        int count = 0;
        for (Iterator<Map.Entry<Object, Object>> it = map.entrySet().iterator(); it.hasNext();) {
            count++;
            assertThat(count).as(ctx + ": iterator returned more entries than expected").isLessThanOrEqualTo(
                    expectedSize);
            Map.Entry<Object, Object> entry = it.next();
            assertNotNull(entry.getKey(), ctx + ": iterator returned an entry with a collected key");
            assertNull(copy.put(entry.getKey(), entry.getValue()), ctx + ": iterator returned a key twice");
        }
        return copy;
    }
}
