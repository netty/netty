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
package io.netty.buffer.api.tests;

import io.netty.buffer.api.Buffer;
import io.netty.buffer.api.BufferAllocator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BufferSearchTest extends BufferTestSupport {
    @ParameterizedTest
    @MethodSource("allocators")
    public void fromOffsetCannotBeLessThanZero(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(IndexOutOfBoundsException.class, () -> buf.firstOffsetOf(-1, 1, (byte) 0));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void lengthCannotBeLessThanZero(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(IllegalArgumentException.class, () -> buf.firstOffsetOf(1, -1, (byte) 0));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void firstOffsetOfMustThrowOnInaccessibleBuffer(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator()) {
            Buffer buffer = allocator.allocate(8);
            buffer.writeLong(0x0102030405060708L);
            assertThat(buffer.firstOffsetOf(0, 8, (byte) 0x03)).isEqualTo(2);
            var send = buffer.send();
            assertThrows(IllegalStateException.class, () -> buffer.firstOffsetOf(0, 1, (byte) 0));
            Buffer received = send.receive();
            assertThat(received.firstOffsetOf(0, 8, (byte) 0x03)).isEqualTo(2);
            received.close();
            assertThrows(IllegalStateException.class, () -> received.firstOffsetOf(0, 1, (byte) 0));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void firstOffsetOfMustThrowWhenRangeIsOutOfBounds(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buffer = allocator.allocate(8)) {
            buffer.writeLong(0x0102030405060708L);
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.firstOffsetOf(1, 8, (byte) 9));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void firstOffsetOfMustFindNeedleAtStartOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(128)) {
            fillBuffer(buf);
            byte needle = (byte) 0xA5;
            buf.setByte(3, needle);
            int maxLen = buf.readableBytes() - 3;
            for (int len = 1; len < maxLen; len++) {
                assertThat(buf.firstOffsetOf(3, len, needle))
                        .as("firstOffsetOf(3, %s, %X) should be 3", len, needle)
                        .isEqualTo(3);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void firstOffsetOfMustFindNeedleAfterStartOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(128)) {
            fillBuffer(buf);
            byte needle = (byte) 0xA5;
            buf.setByte(3, needle);
            int maxLen = buf.readableBytes() - 3;
            for (int len = 4; len < maxLen; len++) {
                assertThat(buf.firstOffsetOf(2, len, needle))
                        .as("firstOffsetOf(2, %s, %X) should be 3", len, needle)
                        .isEqualTo(3);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void firstOffsetOfMustFindNeedleCloseToEndOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(128)) {
            fillBuffer(buf);
            byte needle = (byte) 0xA5;
            int offset = buf.capacity() - 2;
            buf.setByte(offset, needle);
            while (buf.readableBytes() > 1) {
                assertThat(buf.firstOffsetOf(buf.readerOffset(), buf.readableBytes(), needle))
                        .as("firstOffsetOf(%s, %s, %X)", buf.readerOffset(), buf.readableBytes(), needle)
                        .isEqualTo(offset);
                buf.skipReadable(1);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void firstOffsetOfMustFindNeedlePriorToEndOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(128)) {
            fillBuffer(buf);
            byte needle = (byte) 0xA5;
            int offset = buf.capacity() - 1;
            buf.setByte(offset, needle);
            while (buf.readableBytes() > 0) {
                int fromOffsetInclusive = buf.readerOffset();
                int length = buf.readableBytes();
                int actual = buf.firstOffsetOf(fromOffsetInclusive, length, needle);
                assertThat(actual)
                        .as("firstOffsetOf(%s, %s, %X)", fromOffsetInclusive, length, needle)
                        .isEqualTo(offset);
                buf.skipReadable(1);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void firstOffsetOfMustNotFindNeedleAtEndOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(128)) {
            fillBuffer(buf);
            byte needle = (byte) 0xA5;
            int offset = buf.capacity() - 1;
            buf.setByte(offset, needle);
            while (buf.readableBytes() - 1 > 0) {
                assertThat(buf.firstOffsetOf(buf.readerOffset(), buf.readableBytes() - 1, needle))
                        .as("firstOffsetOf(%s, %s, %X)", buf.readerOffset(), buf.readableBytes() - 1, needle)
                        .isEqualTo(-1);
                buf.skipReadable(1);
            }
        }
    }

    private static void fillBuffer(Buffer buf) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int len = buf.capacity() / Long.BYTES;
        for (int i = 0; i < len; i++) {
            // Random bytes, but lower nibble is zeroed to avoid accidentally matching our needle.
            buf.writeLong(rng.nextLong() & 0xF0F0F0F0_F0F0F0F0L);
        }
    }
}
