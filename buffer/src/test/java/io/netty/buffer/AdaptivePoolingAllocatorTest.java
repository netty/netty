/*
 * Copyright 2024 The Netty Project
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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdaptivePoolingAllocatorTest {
    @Test
    void sizeClassComputations() throws Exception {
        final int[] sizeClasses = AdaptivePoolingAllocator.getSizeClasses();
        for (int sizeClassIndex = 0; sizeClassIndex < sizeClasses.length; sizeClassIndex++) {
            final int previousSizeIncluded = sizeClassIndex == 0? 0 : sizeClasses[sizeClassIndex - 1] + 1;
            assertSizeClassOf(sizeClassIndex, previousSizeIncluded, sizeClasses[sizeClassIndex]);
        }
        // beyond the last size class, we return the size class array's length
        assertSizeClassOf(sizeClasses.length, sizeClasses[sizeClasses.length - 1] + 1,
                          sizeClasses[sizeClasses.length - 1] + 1);
    }

    private static void assertSizeClassOf(int expectedSizeClass, int previousSizeIncluded, int maxSizeIncluded) {
        for (int size = previousSizeIncluded; size <= maxSizeIncluded; size++) {
            assertEquals(expectedSizeClass, AdaptivePoolingAllocator.sizeClassIndexOf(size), "size = " + size);
        }
    }
}
