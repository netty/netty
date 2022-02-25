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

import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BufferSearchTest extends BufferTestSupport {
    @ParameterizedTest
    @MethodSource("allocators")
    public void bytesBeforeMustThrowOnInaccessibleBuffer(Fixture fixture) {
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
    public void bytesBeforeMustFindNeedleAtStart(Fixture fixture) {
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
    public void bytesBeforeMustFindNeedleAfterStart(Fixture fixture) {
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
    public void bytesBeforeMustFindNeedleCloseToEndOffset(Fixture fixture) {
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
    public void bytesBeforeMustFindNeedlePriorToEndOffset(Fixture fixture) {
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
    public void bytesBeforeMustNotFindNeedleOutsideReadableRange(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(128)) {
            fillBuffer(buf);
            byte needle = (byte) 0xA5;
            int offset = buf.capacity() - 1;
            buf.setByte(offset, needle);
            buf.skipWritable(-1); // Pull the write-offset down by one, leaving needle just outside readable range.
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
    public void bytesBeforeOnEmptyBufferMustNotFindAnything(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(0)) {
            assertThat(buf.bytesBefore((byte) 0)).isEqualTo(-1);
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
