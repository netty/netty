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
package io.netty.channel.uring;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PendingOpMapTest {

    @Test
    public void testRegisterAndFind() {
        PendingOpMap map = new PendingOpMap(4);
        long token = map.nextToken();

        map.registerNormal(token, 42, (byte) 7, Long.MAX_VALUE);

        int slot = map.findSlot(token);
        assertTrue(slot >= 0);
        assertEquals(42, map.registrationId(slot));
        assertEquals((byte) 7, map.op(slot));
        assertEquals(Long.MAX_VALUE, map.userData(slot));
        assertEquals(3, PendingOpMap.tokenSequence(token));
    }

    @Test
    public void testExpandPreservesEntries() {
        PendingOpMap map = new PendingOpMap(2);
        long[] tokens = new long[32];

        for (int i = 0; i < tokens.length; i++) {
            long token = map.nextToken();
            tokens[i] = token;
            map.registerNormal(token, i + 1, (byte) i, i * 100L);
        }

        for (int i = 0; i < tokens.length; i++) {
            int slot = map.findSlot(tokens[i]);
            assertTrue(slot >= 0);
            assertEquals(i + 1, map.registrationId(slot));
            assertEquals((byte) i, map.op(slot));
            assertEquals(i * 100L, map.userData(slot));
        }
    }

    @Test
    public void testReleaseSkipsTombstonesOnRehash() {
        PendingOpMap map = new PendingOpMap(8);
        long first = register(map, 1);
        long second = register(map, 2);
        long third = register(map, 3);
        long live = register(map, 4);

        // The third release leaves one live entry and three tombstones, which triggers rehash.
        map.release(map.findSlot(first));
        map.release(map.findSlot(second));
        map.release(map.findSlot(third));

        assertEquals(-1, map.findSlot(first));
        assertEquals(-1, map.findSlot(second));
        assertEquals(-1, map.findSlot(third));

        int slot = map.findSlot(live);
        assertTrue(slot >= 0);
        assertEquals(4, map.registrationId(slot));
        assertEquals((byte) 4, map.op(slot));
        assertEquals(4L, map.userData(slot));
    }

    @Test
    public void testRegisterAfterRelease() {
        PendingOpMap map = new PendingOpMap(4);
        long released = register(map, 1);
        long live = register(map, 2);

        map.release(map.findSlot(released));
        long next = register(map, 3);

        assertEquals(-1, map.findSlot(released));

        int liveSlot = map.findSlot(live);
        assertTrue(liveSlot >= 0);
        assertEquals(2, map.registrationId(liveSlot));

        int nextSlot = map.findSlot(next);
        assertTrue(nextSlot >= 0);
        assertEquals(3, map.registrationId(nextSlot));
        assertEquals((byte) 3, map.op(nextSlot));
        assertEquals(3L, map.userData(nextSlot));
    }

    private static long register(PendingOpMap map, int value) {
        long token = map.nextToken();
        map.registerNormal(token, value, (byte) value, value);
        return token;
    }
}
