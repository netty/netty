/*
 * Copyright 2021 The Netty Project
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
 *
 */
package io.netty5.buffer.pool;

import io.netty5.buffer.Buffer;
import io.netty5.buffer.BufferAllocator;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

class PooledBufferAllocatorTest {

    public static volatile int sink;

    static {
        System.setProperty("io.netty5.leakDetection.level", "paranoid");
        System.setProperty("io.netty5.leakDetection.targetRecords", "32");
        System.setProperty("io.netty5.buffer.lifecycleTracingEnabled", "true");
        System.setProperty("io.netty5.buffer.leakDetectionEnabled", "true");
        System.setProperty("io.netty5.buffer.alwaysAttachCleaner", "true");
        System.setProperty("io.netty5.allocator.smallCacheSize", "0");
        System.setProperty("io.netty5.allocator.normalCacheSize", "0");

    }

    @Test
    public void test() {
        BufferAllocator bufferAllocator = BufferAllocator.onHeapPooled();
        Buffer buffer = bufferAllocator.allocate(8);

        buffer.writeLong(10L);

        Buffer splittedBuffer = buffer.split();
        splittedBuffer.close();
        produceGarbage();

        buffer.writeLong(11L);
        buffer = null;
        produceGarbage();
    }


    private static void produceGarbage() {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int j = 0; j < 100000; j++) {
            final int size;
            switch (rng.nextInt(0, 2)) {
            case 0:
                size = 1000;
                break;
            case 1:
                size = 10_000;
                break;
            default:
                size = 50_000;
                break;
            }
            sink = System.identityHashCode(new byte[size]);
        }
    }
}
