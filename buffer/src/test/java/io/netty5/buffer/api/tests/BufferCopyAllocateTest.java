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
import io.netty5.util.internal.EmptyArrays;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.ByteBuffer;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BufferCopyAllocateTest extends BufferTestSupport {
    @ParameterizedTest
    @MethodSource("allocators")
    public void copyOfByteArrayMustContainContents(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator()) {
            byte[] array = new byte[23];
            ThreadLocalRandom.current().nextBytes(array);
            try (Buffer buffer = allocator.copyOf(array)) {
                assertThat(buffer.capacity()).isEqualTo(array.length);
                assertThat(buffer.readableBytes()).isEqualTo(array.length);
                for (int i = 0; i < array.length; i++) {
                    byte b = array[i];
                    byte a = buffer.readByte();
                    if (b != a) {
                        fail("Wrong contents at offset %s. Expected %s but was %s.", i, b, a);
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void copyOfByteArrayMustNotBeReadOnly(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator()) {
            byte[] array = new byte[23];
            ThreadLocalRandom.current().nextBytes(array);
            try (Buffer buffer = allocator.copyOf(array)) {
                assertFalse(buffer.readOnly());
                buffer.ensureWritable(Long.BYTES);
                buffer.writeLong(0x0102030405060708L);
                assertThat(buffer.capacity()).isEqualTo(array.length + Long.BYTES);
                assertThat(buffer.readableBytes()).isEqualTo(array.length + Long.BYTES);
                for (int i = 0; i < array.length; i++) {
                    byte b = array[i];
                    byte a = buffer.readByte();
                    if (b != a) {
                        fail("Wrong contents at offset %s. Expected %s but was %s.", i, b, a);
                    }
                }
                assertEquals(0x0102030405060708L, buffer.readLong());
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void copyOfByteArrayMustNotReflectChangesToArray(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator()) {
            byte[] array = { 1, 1, 1, 1, 1, 1, 1, 1 };
            try (Buffer buffer = allocator.copyOf(array)) {
                array[2] = 2; // Change to array should not be reflected in buffer.
                assertEquals(0x0101010101010101L, buffer.readLong());
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void copyOfEmptyByteArrayMustProduceEmptyBuffer(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buffer = allocator.copyOf(EmptyArrays.EMPTY_BYTES)) {
            assertThat(buffer.capacity()).isZero();
            assertTrue(buffer.isAccessible());
            buffer.ensureWritable(4);
            assertThat(buffer.capacity()).isEqualTo(4);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void copyOfHeapByteBufferMustContainContents(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator()) {
            byte[] array = new byte[23];
            ByteBuffer byteBuffer = ByteBuffer.wrap(array);
            ThreadLocalRandom.current().nextBytes(byteBuffer.array());
            byteBuffer.position(1 + byteBuffer.position());
            final int pos = byteBuffer.position();
            final int lim = byteBuffer.limit();
            final int cap = byteBuffer.capacity();
            final int rem = byteBuffer.remaining();

            try (Buffer buffer = allocator.copyOf(byteBuffer)) {
                assertThat(buffer.capacity()).isEqualTo(rem);
                assertThat(buffer.readableBytes()).isEqualTo(rem);
                assertThat(byteBuffer.position()).isEqualTo(pos);
                assertThat(byteBuffer.limit()).isEqualTo(lim);
                assertThat(byteBuffer.capacity()).isEqualTo(cap);
                while (byteBuffer.hasRemaining()) {
                    byte b = byteBuffer.get();
                    byte a = buffer.readByte();
                    int position = byteBuffer.position();
                    if (b != a) {
                        fail("Wrong contents at offset %s. Expected %s but was %s.", position - 1, b, a);
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void copyOfHeapByteBufferMustNotBeReadOnly(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator()) {
            ByteBuffer byteBuffer = ByteBuffer.wrap(new byte[23]);
            ThreadLocalRandom.current().nextBytes(byteBuffer.array());
            try (Buffer buffer = allocator.copyOf(byteBuffer)) {
                assertFalse(buffer.readOnly());
                buffer.ensureWritable(Long.BYTES);
                buffer.writeLong(0x0102030405060708L);
                assertThat(buffer.capacity()).isEqualTo(byteBuffer.capacity() + Long.BYTES);
                assertThat(buffer.readableBytes()).isEqualTo(byteBuffer.capacity() + Long.BYTES);
                while (byteBuffer.hasRemaining()) {
                    byte b = byteBuffer.get();
                    byte a = buffer.readByte();
                    if (b != a) {
                        fail("Wrong contents at offset %s. Expected %s but was %s.", byteBuffer.position() - 1, b, a);
                    }
                }
                assertEquals(0x0102030405060708L, buffer.readLong());
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void copyOfHeapByteBufferMustNotReflectChangesToArray(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator()) {
            ByteBuffer byteBuffer = ByteBuffer.wrap(new byte[] { 1, 1, 1, 1, 1, 1, 1, 1 });
            try (Buffer buffer = allocator.copyOf(byteBuffer)) {
                byteBuffer.put(2, (byte) 2); // Change to array should not be reflected in buffer.
                assertEquals(0x0101010101010101L, buffer.readLong());
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void copyOfEmptyHeapByteBufferMustProduceEmptyBuffer(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buffer = allocator.copyOf(ByteBuffer.allocate(0))) {
            assertThat(buffer.capacity()).isZero();
            assertTrue(buffer.isAccessible());
            buffer.ensureWritable(4);
            assertThat(buffer.capacity()).isEqualTo(4);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void copyOfConsumedHeapByteBufferMustProduceEmptyBuffer(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buffer = allocator.copyOf(ByteBuffer.allocate(Long.BYTES).putLong(0))) {
            assertThat(buffer.capacity()).isZero();
            assertTrue(buffer.isAccessible());
            buffer.ensureWritable(4);
            assertThat(buffer.capacity()).isEqualTo(4);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void copyOfDirectByteBufferMustContainContents(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator()) {
            byte[] array = new byte[23];
            ThreadLocalRandom.current().nextBytes(array);
            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(array.length);
            for (byte b : array) {
                byteBuffer.put(b);
            }
            byteBuffer.flip();
            final int pos = byteBuffer.position();
            final int lim = byteBuffer.limit();
            final int cap = byteBuffer.capacity();

            try (Buffer buffer = allocator.copyOf(byteBuffer)) {
                assertThat(buffer.capacity()).isEqualTo(byteBuffer.capacity());
                assertThat(buffer.readableBytes()).isEqualTo(byteBuffer.capacity());
                assertThat(byteBuffer.position()).isEqualTo(pos);
                assertThat(byteBuffer.limit()).isEqualTo(lim);
                assertThat(byteBuffer.capacity()).isEqualTo(cap);
                while (byteBuffer.hasRemaining()) {
                    byte b = byteBuffer.get();
                    byte a = buffer.readByte();
                    if (b != a) {
                        fail("Wrong contents at offset %s. Expected %s but was %s.", byteBuffer.position() - 1, b, a);
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void copyOfDirectByteBufferMustNotBeReadOnly(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator()) {
            byte[] array = new byte[23];
            ThreadLocalRandom.current().nextBytes(array);
            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(array.length);
            for (byte b : array) {
                byteBuffer.put(b);
            }
            byteBuffer.flip();

            try (Buffer buffer = allocator.copyOf(byteBuffer)) {
                assertFalse(buffer.readOnly());
                buffer.ensureWritable(Long.BYTES);
                buffer.writeLong(0x0102030405060708L);
                assertThat(buffer.capacity()).isEqualTo(byteBuffer.capacity() + Long.BYTES);
                assertThat(buffer.readableBytes()).isEqualTo(byteBuffer.capacity() + Long.BYTES);
                while (byteBuffer.hasRemaining()) {
                    byte b = byteBuffer.get();
                    byte a = buffer.readByte();
                    if (b != a) {
                        fail("Wrong contents at offset %s. Expected %s but was %s.", byteBuffer.position() - 1, b, a);
                    }
                }
                assertEquals(0x0102030405060708L, buffer.readLong());
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void copyOfDirectByteBufferMustNotReflectChangesToArray(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator()) {
            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(Long.BYTES);
            byteBuffer.putLong(0x0101010101010101L);
            byteBuffer.flip();

            try (Buffer buffer = allocator.copyOf(byteBuffer)) {
                byteBuffer.put(2, (byte) 2); // Change to array should not be reflected in buffer.
                assertEquals(0x0101010101010101L, buffer.readLong());
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void copyOfEmptyDirectByteBufferMustProduceEmptyBuffer(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buffer = allocator.copyOf(ByteBuffer.allocateDirect(0))) {
            assertThat(buffer.capacity()).isZero();
            assertTrue(buffer.isAccessible());
            buffer.ensureWritable(4);
            assertThat(buffer.capacity()).isEqualTo(4);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void copyOfConsumedDirectByteBufferMustProduceEmptyBuffer(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buffer = allocator.copyOf(ByteBuffer.allocateDirect(Long.BYTES).putLong(0))) {
            assertThat(buffer.capacity()).isZero();
            assertTrue(buffer.isAccessible());
            buffer.ensureWritable(4);
            assertThat(buffer.capacity()).isEqualTo(4);
        }
    }
}
