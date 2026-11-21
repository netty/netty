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
package io.netty.channel.kqueue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class KQueueEventArrayTest {

    private KQueueEventArray array;

    @BeforeEach
    public void setUp() {
        KQueue.ensureAvailability();
    }

    @AfterEach
    public void tearDown() {
        if (array != null) {
            array.free();
        }
    }

    @Test
    public void testEvSetGrowsAndKeepsAllEvents() {
        final int initialCapacity = 4;
        final int events = 100;
        array = new KQueueEventArray(initialCapacity);

        for (int i = 0; i < events; i++) {
            array.evSet(i, Native.EVFILT_READ, Native.EV_ADD_ENABLE, 0, i * 2L, i * 3L);
        }

        assertEquals(events, array.size());
        assertTrue(array.capacity() >= events, "capacity " + array.capacity() + " < " + events);
        for (int i = 0; i < events; i++) {
            assertEquals(i, array.ident(i));
            assertEquals(Native.EVFILT_READ, array.filter(i));
            assertEquals(i * 2L, array.data(i));
            assertEquals(i * 3L, array.udata(i));
        }
    }

    @Test
    public void testReallocGrowsCapacityAndKeepsContent() {
        array = new KQueueEventArray(2);
        array.evSet(1, Native.EVFILT_READ, Native.EV_ADD_ENABLE, 0, 10, 100);
        array.evSet(2, Native.EVFILT_READ, Native.EV_ADD_ENABLE, 0, 20, 200);

        array.realloc(true);

        assertEquals(4, array.capacity());
        assertEquals(2, array.size());
        for (int i = 0; i < 2; i++) {
            assertEquals(i + 1, array.ident(i));
            assertEquals((i + 1) * 10L, array.data(i));
            assertEquals((i + 1) * 100L, array.udata(i));
        }
    }
}
