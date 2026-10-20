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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CleanerJava24LinkerTest {
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 4096})
    void allocateAndClean(int capacity) {
        // A restricted call can enable native access after this cleaner was initialized as unavailable.
        // Use its cached availability rather than the module's current native-access state.
        assumeTrue(CleanerJava24Linker.isSupported());
        Cleaner cleaner = new CleanerJava24Linker();
        CleanableDirectBuffer allocation = cleaner.allocate(capacity);
        try {
            ByteBuffer buffer = allocation.buffer();
            assertTrue(buffer.isDirect());
            assertEquals(capacity, buffer.capacity());
            assertTrue(allocation.hasMemoryAddress());
            assertNotEquals(0, allocation.memoryAddress());
            for (int i = 0; i < capacity; i++) {
                buffer.put(i, (byte) i);
            }
            for (int i = 0; i < capacity; i++) {
                assertEquals((byte) i, buffer.get(i));
            }
        } finally {
            allocation.clean();
        }
    }

    @Test
    void unavailableWithoutNativeAccess() {
        assumeTrue(!CleanerJava24Linker.class.getModule().isNativeAccessEnabled());
        assertFalse(CleanerJava24Linker.isSupported());
    }
}
