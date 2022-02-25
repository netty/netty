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
import io.netty5.buffer.api.BufferReadOnlyException;
import io.netty5.buffer.api.MemoryManager;
import io.netty5.buffer.api.internal.ResourceSupport;
import io.netty5.util.internal.logging.InternalLogger;
import io.netty5.util.internal.logging.InternalLoggerFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceConfigurationError;

import static io.netty5.buffer.api.internal.Statics.isOwned;
import static io.netty5.buffer.api.tests.BufferTestSupport.assertEquals;
import static io.netty5.buffer.api.tests.BufferTestSupport.verifyWriteInaccessible;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MemoryManagerTest {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(MemoryManagerTest.class);
    private static final Memoize<MemoryManager[]> MANAGERS = new Memoize<>(() -> {
        List<Throwable> failedManagers = new ArrayList<>();
        List<MemoryManager> loadableManagers = new ArrayList<>();
        MemoryManager.availableManagers().forEach(provider -> {
            try {
                loadableManagers.add(provider.get());
            } catch (ServiceConfigurationError | Exception e) {
                logger.debug("Could not load implementation for testing", e);
                failedManagers.add(e);
            }
        });
        if (loadableManagers.isEmpty()) {
            AssertionError error = new AssertionError("Failed to load any memory managers implementations.");
            for (Throwable failure : failedManagers) {
                error.addSuppressed(failure);
            }
            throw error;
        }
        return loadableManagers.toArray(MemoryManager[]::new);
    });

    static MemoryManager[] managers() {
        return MANAGERS.get();
    }

    @ParameterizedTest
    @MethodSource("managers")
    public void wrappingBufferMustHaveArrayContents(MemoryManager manager) {
        try (Buffer buffer = wrap(manager, new byte[] {1, 2, 3, 4, 5, 6, 7, 8})) {
            assertThat(buffer.capacity()).isEqualTo(8);
            assertThat(buffer.readableBytes()).isEqualTo(8);
            assertEquals(0x0102030405060708L, buffer.readLong());
        }
    }

    @ParameterizedTest
    @MethodSource("managers")
    public void wrappingBufferIsReadOnly(MemoryManager manager) {
        try (Buffer buffer = wrap(manager, new byte[] { 1, 2, 3, 4 })) {
            assertTrue(buffer.readOnly());
            verifyWriteInaccessible(buffer, BufferReadOnlyException.class);
        }
    }

    @ParameterizedTest
    @MethodSource("managers")
    public void changesToArrayMustBeReflectedInWrappingBuffer(MemoryManager manager) {
        byte[] bytes = { 1, 2, 3, 4, 5, 6, 7, 8 };
        try (Buffer buffer = wrap(manager, bytes)) {
            assertEquals(0x0102030405060708L, buffer.getLong(0));
            bytes[1] = (byte) 0xFF;
            assertEquals(0x01FF030405060708L, buffer.getLong(0));
        }
    }

    @ParameterizedTest
    @MethodSource("managers")
    public void closedWrappingBuffersAreNotReadOnly(MemoryManager manager) {
        Buffer buffer = wrap(manager, new byte[] {1, 2, 3, 4});
        assertTrue(buffer.readOnly());
        buffer.close();
        assertFalse(buffer.readOnly());
        assertFalse(buffer.isAccessible());
    }

    @ParameterizedTest
    @MethodSource("managers")
    public void wrappingBuffersMustStayReadOnlyAfterSend(MemoryManager manager) {
        try (Buffer buffer = wrap(manager, new byte[] { 1, 2, 3, 4 })) {
            try (Buffer received = buffer.send().receive()) {
                assertTrue(received.readOnly());
                verifyWriteInaccessible(received, BufferReadOnlyException.class);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("managers")
    public void compactOnWrappingBufferMustThrow(MemoryManager manager) {
        try (Buffer buffer = wrap(manager, new byte[] { 1, 2, 3, 4 })) {
            assertThrows(BufferReadOnlyException.class, () -> buffer.compact());
        }
    }

    @ParameterizedTest
    @MethodSource("managers")
    public void ensureWritableOnWrappingBufferMustThrow(MemoryManager manager) {
        try (Buffer buffer = wrap(manager, new byte[] { 1, 2, 3, 4 })) {
            assertThrows(BufferReadOnlyException.class, () -> buffer.ensureWritable(1));
        }
    }

    @ParameterizedTest
    @MethodSource("managers")
    public void copyIntoWrappingBufferMustThrow(MemoryManager manager) {
        try (Buffer dest = wrap(manager, new byte[] { 1, 2, 3, 4, 5, 6, 7, 8 })) {
            try (Buffer src = BufferAllocator.onHeapUnpooled().allocate(8)) {
                assertThrows(BufferReadOnlyException.class, () -> src.copyInto(0, dest, 0, 1));
                assertThrows(BufferReadOnlyException.class, () -> src.copyInto(0, dest, 0, 0));
                assertEquals(0x0102030405060708L, dest.readLong());
            }
        }
    }

    @ParameterizedTest
    @MethodSource("managers")
    public void wrappingBufferCannotChangeWriteOffset(MemoryManager manager) {
        try (Buffer buffer = wrap(manager, new byte[] { 1, 2, 3, 4 })) {
            assertThrows(BufferReadOnlyException.class, () -> buffer.writerOffset(0));
            assertThrows(BufferReadOnlyException.class, () -> buffer.writerOffset(2));
        }
    }

    @ParameterizedTest
    @MethodSource("managers")
    public void wrappingBufferCanBeSplit(MemoryManager manager) {
        try (Buffer a = wrap(manager, new byte[16]);
             Buffer b = a.split(8)) {
            assertTrue(a.readOnly());
            assertTrue(b.readOnly());
            assertTrue(isOwned((ResourceSupport<?, ?>) a));
            assertTrue(isOwned((ResourceSupport<?, ?>) b));
            assertThat(a.capacity()).isEqualTo(8);
            assertThat(b.capacity()).isEqualTo(8);
            try (Buffer c = b.copy()) {
                assertFalse(c.readOnly()); // Buffer copies are never read-only.
                assertTrue(isOwned((ResourceSupport<?, ?>) c));
                assertTrue(isOwned((ResourceSupport<?, ?>) b));
                assertThat(c.capacity()).isEqualTo(8);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("managers")
    public void copyOfWrappingBufferMustNotBeReadOnly(MemoryManager manager) {
        try (Buffer buf = wrap(manager, new byte[] { 1, 2, 3, 4, 5, 6, 7, 8 });
             Buffer copy = buf.copy()) {
            assertFalse(copy.readOnly());
            Assertions.assertEquals(buf, copy);
            assertEquals(0, copy.readerOffset());
            copy.setLong(0, 0xA1A2A3A4A5A6A7A8L);
            assertEquals(0xA1A2A3A4A5A6A7A8L, copy.getLong(0));
        }
    }

    @ParameterizedTest
    @MethodSource("managers")
    public void resetOffsetsOfWrappingBufferOnlyChangesReadOffset(MemoryManager manager) {
        try (Buffer buf = wrap(manager, new byte[] {1, 2, 3, 4})) {
            assertEquals(4, buf.readableBytes());
            assertEquals(0x01020304, buf.readInt());
            assertEquals(0, buf.readableBytes());
            buf.resetOffsets();
            assertEquals(4, buf.readableBytes());
            assertEquals(0x01020304, buf.readInt());
        }
    }

    private static Buffer wrap(MemoryManager manager, byte[] bytes) {
        return MemoryManager.using(manager, () -> {
            return MemoryManager.unsafeWrap(bytes);
        });
    }
}
