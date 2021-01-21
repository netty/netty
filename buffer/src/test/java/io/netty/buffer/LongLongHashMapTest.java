/*
 * Copyright 2020 The Netty Project
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
package io.netty.buffer;

import io.netty.util.internal.ThreadLocalRandom;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.PrimitiveIterator.OfLong;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LongLongHashMapTest {
    @Test
    public void zeroPutGetAndRemove() {
        LongLongHashMap map = new LongLongHashMap(-1);
        assertThat(map.put(0, 42)).isEqualTo(-1);
        assertThat(map.get(0)).isEqualTo(42);
        assertThat(map.put(0, 24)).isEqualTo(42);
        assertThat(map.get(0)).isEqualTo(24);
        map.remove(0);
        assertThat(map.get(0)).isEqualTo(-1);
    }

    @Test
    public void mustHandleCollisions() {
        LongLongHashMap map = new LongLongHashMap(-1);
        Set<Long> set = new HashSet<Long>();
        long v = 1;
        for (int i = 0; i < 63; i++) {
            assertThat(map.put(v, v)).isEqualTo(-1);
            set.add(v);
            v <<= 1;
        }
        for (Long value : set) {
            assertThat(map.get(value)).isEqualTo(value);
            assertThat(map.put(value, -value)).isEqualTo(value);
            assertThat(map.get(value)).isEqualTo(-value);
            map.remove(value);
            assertThat(map.get(value)).isEqualTo(-1);
        }
    }

    @Test
    public void randomOperations() {
        int operations = 6000;
        ThreadLocalRandom tlr = ThreadLocalRandom.current();
        Map<Long, Long> expected = new HashMap<Long, Long>();
        LongLongHashMap actual = new LongLongHashMap(-1);
        OfLong itr = tlr.longs(0, operations).limit(operations * 50).iterator();
        while (itr.hasNext()) {
            long value = itr.nextLong();
            if (expected.containsKey(value)) {
                assertThat(actual.get(value)).isEqualTo(expected.get(value));
                if (tlr.nextBoolean()) {
                    actual.remove(value);
                    expected.remove(value);
                    assertThat(actual.get(value)).isEqualTo(-1);
                } else {
                    long v = expected.get(value);
                    assertThat(actual.put(value, -v)).isEqualTo(expected.put(value, -v));
                }
            } else {
                assertThat(actual.get(value)).isEqualTo(-1);
                assertThat(actual.put(value, value)).isEqualTo(-1);
                expected.put(value, value);
            }
        }
    }
}
