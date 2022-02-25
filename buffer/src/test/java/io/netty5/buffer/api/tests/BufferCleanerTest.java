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
 */
package io.netty5.buffer.api.tests;

import io.netty5.buffer.api.MemoryManager;
import io.netty5.buffer.api.internal.Statics;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.netty5.buffer.api.MemoryManager.using;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class BufferCleanerTest extends BufferTestSupport {
    @SuppressWarnings("unused")
    private static volatile int sink;

    static Fixture[] unsafeAllocators() {
        Optional<MemoryManager> maybeManager = MemoryManager.lookupImplementation("Unsafe");
        assumeTrue(maybeManager.isPresent());
        MemoryManager manager = maybeManager.get();
        List<Fixture> initFixtures = initialAllocators().stream().flatMap(f -> {
            Stream.Builder<Fixture> builder = Stream.builder();
            builder.add(new Fixture(f + "/" + manager, () -> using(manager, f), f.getProperties()));
            return builder.build();
        }).collect(Collectors.toList());
        return fixtureCombinations(initFixtures).filter(f -> f.isDirect()).toArray(Fixture[]::new);
    }

    @ParameterizedTest
    @MethodSource("unsafeAllocators")
    public void bufferMustBeClosedByCleaner(Fixture fixture) throws InterruptedException {
        var initial = Statics.MEM_USAGE_NATIVE.sum();
        int allocationSize = 1024;
        allocateAndForget(fixture, allocationSize);
        long sum = 0;
        for (int i = 0; i < 15; i++) {
            System.gc();
            System.runFinalization();
            sum = Statics.MEM_USAGE_NATIVE.sum() - initial;
            if (sum < allocationSize) {
                // The memory must have been cleaned.
                return;
            }
            produceGarbage(); // Produce a large amount of garbage to give the GC something to work on.
        }
        assertThat(sum).isLessThan(allocationSize);
    }

    private static void allocateAndForget(Fixture fixture, int size) {
        var allocator = fixture.createAllocator();
        allocator.allocate(size);
        allocator.close();
    }

    private static void produceGarbage() {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int j = 0; j < 1000; j++) {
            final int size;
            switch (rng.nextInt(0, 2)) {
            case 0: size = 1000; break;
            case 1: size = 10_000; break;
            default: size = 50_000; break;
            }
            sink = System.identityHashCode(new byte[size]);
        }
    }
}
