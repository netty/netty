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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BufferSearchTest extends BufferTestSupport {
    @ParameterizedTest
    @MethodSource("allocators")
    public void bytesBeforeByteMustThrowOnInaccessibleBuffer(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator()) {
            Buffer buffer = allocator.allocate(8);
            buffer.writeLong(0x0102030405060708L);
            assertThat(buffer.bytesBefore((byte) 0x03)).isEqualTo(2);
            var send = buffer.send();
            assertThrows(IllegalStateException.class, () -> buffer.bytesBefore((byte) 0));
            Buffer received = send.receive();
            assertThat(received.bytesBefore((byte) 0x03)).isEqualTo(2);
            received.close();
            assertThrows(IllegalStateException.class, () -> received.bytesBefore((byte) 0));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void bytesBeforeBufferMustThrowOnInaccessibleBuffer(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer needle = allocator.copyOf(new byte[] { 0x03, 0x04 })) {
            Buffer buffer = allocator.allocate(8);
            buffer.writeLong(0x0102030405060708L);
            assertThat(buffer.bytesBefore(needle)).isEqualTo(2);
            var send = buffer.send();
            assertThrows(IllegalStateException.class, () -> buffer.bytesBefore((byte) 0));
            Buffer received = send.receive();
            assertThat(received.bytesBefore(needle)).isEqualTo(2);
            received.close();
            assertThrows(IllegalStateException.class, () -> received.bytesBefore((byte) 0));
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void bytesBeforeByteMustFindNeedleAtStart(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(128)) {
            fillBuffer(buf);
            byte needle = (byte) 0xA5;
            buf.setByte(3, needle);
            buf.skipReadable(3);
            assertThat(buf.bytesBefore(needle))
                    .as("bytesBefore(%X) should be 0", needle)
                    .isEqualTo(0);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void bytesBeforeBufferMustFindNeedleAtStart(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(128);
             Buffer needle = allocator.allocate(3).writeMedium(0xA5A5A5)) {
            fillBuffer(buf);
            buf.setMedium(3, needle.getMedium(0));
            buf.skipReadable(3);
            assertThat(buf.bytesBefore(needle))
                    .as("bytesBefore(Buffer(%X)) should be 0", needle.getMedium(0))
                    .isEqualTo(0);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void bytesBeforeByteMustFindNeedleAfterStart(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(128)) {
            fillBuffer(buf);
            byte needle = (byte) 0xA5;
            buf.setByte(3, needle);
            buf.skipReadable(2);
            assertThat(buf.bytesBefore(needle))
                    .as("bytesBefore(%X) should be 0", needle)
                    .isEqualTo(1);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void bytesBeforeBufferMustFindNeedleAfterStart(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(128);
             Buffer needle = allocator.allocate(3).writeMedium(0xA5A5A5)) {
            fillBuffer(buf);
            buf.setMedium(3, needle.getMedium(0));
            buf.skipReadable(2);
            assertThat(buf.bytesBefore(needle))
                    .as("bytesBefore(Buffer(%X)) should be 0", needle.getMedium(0))
                    .isEqualTo(1);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void bytesBeforeByteMustFindNeedleCloseToEndOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(128)) {
            fillBuffer(buf);
            byte needle = (byte) 0xA5;
            int offset = buf.capacity() - 2;
            buf.setByte(offset, needle);
            while (buf.readableBytes() > 1) {
                assertThat(buf.bytesBefore(needle))
                        .as("bytesBefore(%X)", needle)
                        .isEqualTo(offset);
                offset--;
                buf.skipReadable(1);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void bytesBeforeBufferMustFindNeedleCloseToEndOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(128);
             Buffer needle = allocator.allocate(3).writeMedium(0xA5A5A5)) {
            fillBuffer(buf);
            int offset = buf.capacity() - 5;
            buf.setMedium(offset, needle.getMedium(0));
            while (buf.readableBytes() > 3) {
                assertThat(buf.bytesBefore(needle))
                        .as("bytesBefore(Buffer(%X))", needle.getMedium(0))
                        .isEqualTo(offset);
                offset--;
                buf.skipReadable(1);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void bytesBeforeByteMustFindNeedlePriorToEndOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(128)) {
            fillBuffer(buf);
            byte needle = (byte) 0xA5;
            int offset = buf.capacity() - 1;
            buf.setByte(offset, needle);
            while (buf.readableBytes() > 1) {
                assertThat(buf.bytesBefore(needle))
                        .as("bytesBefore(%X)", needle)
                        .isEqualTo(offset);
                offset--;
                buf.skipReadable(1);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void bytesBeforeBufferMustFindNeedlePriorToEndOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(128);
             Buffer needle = allocator.allocate(3).writeMedium(0xA5A5A5)) {
            fillBuffer(buf);
            int offset = buf.capacity() - 3;
            buf.setMedium(offset, needle.getMedium(0));
            while (buf.readableBytes() > 3) {
                assertThat(buf.bytesBefore(needle))
                        .as("bytesBefore(Buffer(%X))", needle.getMedium(0))
                        .isEqualTo(offset);
                offset--;
                buf.skipReadable(1);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void bytesBeforeByteMustNotFindNeedleOutsideReadableRange(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(128)) {
            fillBuffer(buf);
            byte needle = (byte) 0xA5;
            int offset = buf.capacity() - 1;
            buf.setByte(offset, needle);
            // Pull the write-offset down by one, leaving needle just outside readable range.
            buf.writerOffset(buf.writerOffset() - 1);
            while (buf.readableBytes() > 1) {
                assertThat(buf.bytesBefore(needle))
                        .as("bytesBefore(%X)", needle)
                        .isEqualTo(-1);
                buf.skipReadable(1);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void bytesBeforeBufferMustNotFindNeedleOutsideReadableRange(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(128);
             Buffer needle = allocator.allocate(3).writeMedium(0xA5A5A5)) {
            fillBuffer(buf);
            int offset = buf.capacity() - 3;
            buf.setMedium(offset, needle.getMedium(0));
            // Pull the write-offset down by one, leaving needle just outside readable range.
            buf.writerOffset(buf.writerOffset() - 1);
            while (buf.readableBytes() > 1) {
                assertThat(buf.bytesBefore(needle))
                        .as("bytesBefore(Buffer(%X))", needle.getMedium(0))
                        .isEqualTo(-1);
                buf.skipReadable(1);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void bytesBeforeByteOnEmptyBufferMustNotFindAnything(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(0)) {
            assertThat(buf.bytesBefore((byte) 0)).isEqualTo(-1);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void bytesBeforeBufferOnHaystackSmallerThanNeedleMustNotFindAnything(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer needle = allocator.allocate(3).writeMedium(0)) {
            try (Buffer buffer = allocator.allocate(0)) {
                assertThat(buffer.bytesBefore(needle)).isEqualTo(-1);
            }
            try (Buffer buffer = allocator.allocate(1).writerOffset(1)) {
                assertThat(buffer.bytesBefore(needle)).isEqualTo(-1);
            }
            try (Buffer buffer = allocator.allocate(2).writerOffset(2)) {
                assertThat(buffer.bytesBefore(needle)).isEqualTo(-1);
            }
            try (Buffer buffer = allocator.allocate(3).writerOffset(3)) {
                assertThat(buffer.bytesBefore(needle)).isEqualTo(0);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void bytesBeforeMatchingBufferNeedles(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
            Buffer haystack = allocator.copyOf("abc123", StandardCharsets.UTF_8)) {

            try (Buffer needle = allocator.copyOf("a", StandardCharsets.UTF_8)) {
                assertEquals(0, haystack.bytesBefore(needle));
            }
            try (Buffer needle = allocator.copyOf("bc", StandardCharsets.UTF_8)) {
                assertEquals(1, haystack.bytesBefore(needle));
            }
            try (Buffer needle = allocator.copyOf("c", StandardCharsets.UTF_8)) {
                assertEquals(2, haystack.bytesBefore(needle));
            }
            try (Buffer needle = allocator.copyOf("abc12", StandardCharsets.UTF_8)) {
                assertEquals(0, haystack.bytesBefore(needle));
            }
            try (Buffer needle = allocator.copyOf("abcdef", StandardCharsets.UTF_8)) {
                assertEquals(-1, haystack.bytesBefore(needle));
            }
            try (Buffer needle = allocator.copyOf("abc12x", StandardCharsets.UTF_8)) {
                assertEquals(-1, haystack.bytesBefore(needle));
            }
            try (Buffer needle = allocator.copyOf("abc123def", StandardCharsets.UTF_8)) {
                assertEquals(-1, haystack.bytesBefore(needle));
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
