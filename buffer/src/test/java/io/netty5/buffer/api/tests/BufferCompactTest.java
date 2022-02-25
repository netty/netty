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

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.buffer.api.internal.ResourceSupport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static io.netty5.buffer.api.internal.Statics.acquire;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BufferCompactTest extends BufferTestSupport {

    @ParameterizedTest
    @MethodSource("allocators")
    public void compactMustDiscardReadBytes(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(16)) {
            buf.writeLong(0x0102030405060708L).writeInt(0x090A0B0C);
            assertEquals(0x01020304, buf.readInt());
            assertEquals(12, buf.writerOffset());
            assertEquals(4, buf.readerOffset());
            assertEquals(4, buf.writableBytes());
            assertEquals(8, buf.readableBytes());
            assertEquals(16, buf.capacity());
            buf.compact();
            assertEquals(8, buf.writerOffset());
            assertEquals(0, buf.readerOffset());
            assertEquals(8, buf.writableBytes());
            assertEquals(8, buf.readableBytes());
            assertEquals(16, buf.capacity());
            assertEquals(0x05060708090A0B0CL, buf.readLong());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void compactMustThrowForUnownedBuffer(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            buf.writeLong(0x0102030405060708L);
            assertEquals((byte) 0x01, buf.readByte());
            try (Buffer ignore = acquire((ResourceSupport<?, ?>) buf)) {
                assertThrows(IllegalStateException.class, () -> buf.compact());
                assertEquals(1, buf.readerOffset());
            }
            assertEquals((byte) 0x02, buf.readByte());
        }
    }
}
