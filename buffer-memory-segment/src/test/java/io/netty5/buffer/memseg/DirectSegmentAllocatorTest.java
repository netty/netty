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
 *
 */

package io.netty5.buffer.memseg;

import io.netty5.buffer.memseg.DirectSegmentAllocator.SegmentHolder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.stream.Stream;

@EnabledIf("isSupported")
public class DirectSegmentAllocatorTest {

    static boolean isSupported() {
        return DirectSegmentAllocator.class.getModule().isNativeAccessEnabled();
    }

    static Stream<DirectSegmentAllocator> allocators() {
        return Stream.of(new JdkSegmentAllocator(), new NativeSegmnentAllocator());
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void testDirectSegmentAllocator(DirectSegmentAllocator allocator) {
        final SegmentHolder holder = Assertions.assertDoesNotThrow(() -> allocator.allocate(10));
        final MemorySegment segment = holder.segment();

        Assertions.assertNotNull(segment);
        Assertions.assertEquals(10, segment.byteSize());
        Assertions.assertTrue(segment.isNative());
        Assertions.assertTrue(segment.isAccessibleBy(new Thread()));

        segment.set(ValueLayout.JAVA_LONG_UNALIGNED, 2, 0x123456789ABCDEF0L);
        Assertions.assertEquals(0x123456789ABCDEF0L, segment.get(ValueLayout.JAVA_LONG_UNALIGNED, 2));

        Assertions.assertDoesNotThrow(() -> holder.free());
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void testThrowsOutOfMemoryErrorIfRequestedByteCountCannotBeAllocated(DirectSegmentAllocator allocator) {
        Assertions.assertThrows(OutOfMemoryError.class, () -> allocator.allocate(Long.MAX_VALUE));
    }
}
