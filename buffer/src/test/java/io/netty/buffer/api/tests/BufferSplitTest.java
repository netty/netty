/*
 * Copyright 2021 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.buffer.api.tests;

import io.netty.buffer.api.Buffer;
import io.netty.buffer.api.BufferAllocator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.concurrent.ThreadLocalRandom;

public class BufferSplitTest extends BufferTestSupport {
    @ParameterizedTest
    @MethodSource("allocators")
    void splitPostFull(Fixture fixture) {
        splitPostFullOrRead(fixture, false);
    }

    @ParameterizedTest
    @MethodSource("allocators")
    void splitPostFullAndRead(Fixture fixture) {
        splitPostFullOrRead(fixture, true);
    }

    private static void splitPostFullOrRead(Fixture fixture, boolean read) {
        try (BufferAllocator allocator = fixture.createAllocator()) {
            final int capacity = 3;
            try (Buffer buf = allocator.allocate(capacity)) {
                byte[] data = new byte[capacity];
                ThreadLocalRandom.current().nextBytes(data);
                buf.writeBytes(data);
                assertEquals(buf.capacity(), buf.writerOffset());
                if (read) {
                    for (int i = 0; i < capacity; i++) {
                        buf.readByte();
                    }
                }
                assertEquals(read ? buf.capacity() : 0, buf.readerOffset());

                try (Buffer split = buf.split()) {
                    assertEquals(capacity, split.capacity());
                    assertEquals(split.capacity(), split.writerOffset());
                    assertEquals(read ? split.capacity() : 0, split.readerOffset());

                    assertEquals(0, buf.capacity());
                    assertEquals(0, buf.writerOffset());
                    assertEquals(0, buf.readerOffset());
                }
            }
        }
    }
}
