/*
 * Copyright 2025 The Netty Project
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

import io.netty.util.IntSupplier;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MpscIntQueueTest {
    @ParameterizedTest
    @ValueSource(ints = {1, 7, 8, 15, 16, 17})
    void mustFillWithSpecifiedEmptyEntry(int size) throws Exception {
        MpscIntQueue queue = new MpscAtomicIntegerArrayQueue(size, -1);
        int filled = queue.fill(size, new IntSupplier() {
            @Override
            public int get() throws Exception {
                return 42;
            }
        });
        assertEquals(size, filled);
        for (int i = 0; i < size; i++) {
            assertEquals(42, queue.poll());
        }
        assertEquals(-1, queue.poll());
        assertTrue(queue.isEmpty());
    }
}
