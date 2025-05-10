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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdaptivePoolingAllocatorTest implements Supplier<String> {
    private int i;

    @BeforeEach
    void setUp() {
        i = 0;
    }

    @Override
    public String get() {
        return "i = " + i;
    }

    @Test
    void sizeBucketComputations() throws Exception {
        assertSizeBucket(0, 256);
        assertSizeBucket(1, 512);
        assertSizeBucket(2, 1024);
        assertSizeBucket(3, 2048);
        assertSizeBucket(4, 4096);
        assertSizeBucket(5, 8 * 1024);
        assertSizeBucket(6, 16 * 1024);
        assertSizeBucket(7, 32 * 1024);
        assertSizeBucket(8, 64 * 1024);
        assertSizeBucket(9, 128 * 1024);
        assertSizeBucket(10, 256 * 1024);
        assertSizeBucket(11, 512 * 1024);
        assertSizeBucket(12, 1024 * 1024);
        // The sizeBucket function will be used for sizes up to 8 MiB
        assertSizeBucket(13, 2 * 1024 * 1024);
        assertSizeBucket(14, 3 * 1024 * 1024);
        assertSizeBucket(14, 4 * 1024 * 1024);
        assertSizeBucket(15, 5 * 1024 * 1024);
        assertSizeBucket(15, 6 * 1024 * 1024);
        assertSizeBucket(15, 7 * 1024 * 1024);
        assertSizeBucket(15, 8 * 1024 * 1024);
    }

    private void assertSizeBucket(int expectedSizeBucket, int maxSizeIncluded) {
        for (; i <= maxSizeIncluded; i++) {
            assertEquals(expectedSizeBucket, AdaptivePoolingAllocator.sizeBucket(i), this);
        }
    }
}
