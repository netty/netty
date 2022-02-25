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
import io.netty5.buffer.api.BufferClosedException;
import io.netty5.buffer.api.BufferReadOnlyException;
import io.netty5.buffer.api.CompositeBuffer;
import io.netty5.buffer.api.Drop;
import io.netty5.buffer.api.Send;
import io.netty5.buffer.api.internal.ResourceSupport;
import io.netty5.buffer.api.internal.Statics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static io.netty5.buffer.api.internal.Statics.acquire;
import static io.netty5.buffer.api.internal.Statics.isOwned;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BufferCompositionTest extends BufferTestSupport {
    @Test
    public void compositeBuffersCannotHaveDuplicateComponents() {
        try (BufferAllocator allocator = BufferAllocator.onHeapUnpooled()) {
            Send<Buffer> a = allocator.allocate(4).send();
            var e = assertThrows(IllegalStateException.class, () -> CompositeBuffer.compose(allocator, a, a));
            assertThat(e).hasMessageContaining("already been received");

            Send<Buffer> b = allocator.allocate(4).send();
            try (CompositeBuffer composite = CompositeBuffer.compose(allocator, b)) {
                e = assertThrows(IllegalStateException.class, () -> composite.extendWith(b));
                assertThat(e).hasMessageContaining("already been received");
            }
        }
    }

    @Test
    public void compositeBufferFromSends() {
        try (BufferAllocator allocator = BufferAllocator.onHeapUnpooled();
             Buffer composite = CompositeBuffer.compose(allocator,
                     allocator.allocate(8).send(),
                     allocator.allocate(8).send(),
                     allocator.allocate(8).send())) {
            assertEquals(24, composite.capacity());
            assertTrue(isOwned((ResourceSupport<?, ?>) composite));
        }
    }

    @Test
    public void compositeBufferMustNotBeAllowedToContainThemselves() {
        try (BufferAllocator allocator = BufferAllocator.onHeapUnpooled()) {
            CompositeBuffer bufA = CompositeBuffer.compose(allocator, allocator.allocate(4).send());
            Send<Buffer> sendA = bufA.send();
            try {
                assertThrows(BufferClosedException.class, () -> bufA.extendWith(sendA));
            } finally {
                sendA.close();
            }

            CompositeBuffer bufB = CompositeBuffer.compose(allocator, allocator.allocate(4).send());
            Send<Buffer> sendB = bufB.send();
            try (CompositeBuffer compositeBuffer = CompositeBuffer.compose(allocator, sendB)) {
                assertThrows(IllegalStateException.class, () -> compositeBuffer.extendWith(sendB));
            } finally {
                sendB.close();
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void ensureWritableOnCompositeBuffersMustRespectExistingBigEndianByteOrder(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator()) {
            Buffer composite;
            try (Buffer a = allocator.allocate(4)) {
                composite = CompositeBuffer.compose(allocator, a.send());
            }
            try (composite) {
                composite.writeInt(0x01020304);
                composite.ensureWritable(4);
                composite.writeInt(0x05060708);
                assertEquals(0x0102030405060708L, composite.readLong());
            }
        }
    }

    @Test
    public void extendOnNonCompositeBufferMustThrow() {
        try (BufferAllocator allocator = BufferAllocator.onHeapUnpooled();
             Buffer a = allocator.allocate(8);
             Buffer b = allocator.allocate(8)) {
            assertThrows(ClassCastException.class, () -> ((CompositeBuffer) a).extendWith(b.send()));
        }
    }

    @Test
    public void extendingNonOwnedCompositeBufferMustThrow() {
        try (BufferAllocator allocator = BufferAllocator.onHeapUnpooled();
             Buffer a = allocator.allocate(8);
             Buffer b = allocator.allocate(8);
             CompositeBuffer composed = CompositeBuffer.compose(allocator, a.send())) {
            try (Buffer ignore = acquire((ResourceSupport<?, ?>) composed)) {
                var exc = assertThrows(IllegalStateException.class, () -> composed.extendWith(b.send()));
                assertThat(exc).hasMessageContaining("owned");
            }
        }
    }

    @Test
    public void extendingCompositeBufferWithItselfMustThrow() {
        try (BufferAllocator allocator = BufferAllocator.onHeapUnpooled()) {
            CompositeBuffer composite;
            try (Buffer a = allocator.allocate(8)) {
                composite = CompositeBuffer.compose(allocator, a.send());
            }
            try (composite) {
                assertThrows(BufferClosedException.class, () -> composite.extendWith(composite.send()));
            }
        }
    }

    @Test
    public void extendingWithZeroCapacityBufferHasNoEffect() {
        try (BufferAllocator allocator = BufferAllocator.onHeapUnpooled();
             CompositeBuffer composite = CompositeBuffer.compose(allocator)) {
            composite.extendWith(CompositeBuffer.compose(allocator).send());
            assertThat(composite.capacity()).isZero();
            assertThat(composite.countComponents()).isZero();
        }
        try (BufferAllocator allocator = BufferAllocator.onHeapUnpooled()) {
            Buffer a = allocator.allocate(1);
            CompositeBuffer composite = CompositeBuffer.compose(allocator, a.send());
            assertTrue(isOwned((ResourceSupport<?, ?>) composite));
            assertThat(composite.capacity()).isOne();
            assertThat(composite.countComponents()).isOne();
            try (Buffer b = CompositeBuffer.compose(allocator)) {
                composite.extendWith(b.send());
            }
            assertTrue(isOwned((ResourceSupport<?, ?>) composite));
            assertThat(composite.capacity()).isOne();
            assertThat(composite.countComponents()).isOne();
        }
    }

    @Test
    public void extendingCompositeBufferWithNullMustThrow() {
        try (BufferAllocator allocator = BufferAllocator.onHeapUnpooled();
             CompositeBuffer composite = CompositeBuffer.compose(allocator)) {
            assertThrows(NullPointerException.class, () -> composite.extendWith(null));
        }
    }

    @Test
    public void extendingCompositeBufferMustIncreaseCapacityByGivenBuffer() {
        try (BufferAllocator allocator = BufferAllocator.onHeapUnpooled();
             CompositeBuffer composite = CompositeBuffer.compose(allocator)) {
            assertThat(composite.capacity()).isZero();
            try (Buffer buf = allocator.allocate(8)) {
                composite.extendWith(buf.send());
            }
            assertThat(composite.capacity()).isEqualTo(8);
            composite.writeLong(0x0102030405060708L);
            assertThat(composite.readLong()).isEqualTo(0x0102030405060708L);
        }
    }

    @Test
    public void emptyCompositeBufferMustAllowExtendingWithBuffer() {
        try (BufferAllocator allocator = BufferAllocator.onHeapUnpooled()) {
            try (CompositeBuffer composite = CompositeBuffer.compose(allocator)) {
                try (Buffer b = allocator.allocate(8)) {
                    composite.extendWith(b.send());
                    assertThat(composite.capacity()).isEqualTo(8);
                }
            }
        }
    }

    @Test
    public void emptyCompositeBufferMustAllowExtendingWithReadOnlyBuffer() {
        try (BufferAllocator allocator = BufferAllocator.onHeapUnpooled()) {
            try (CompositeBuffer composite = CompositeBuffer.compose(allocator)) {
                try (Buffer b = allocator.allocate(8).makeReadOnly()) {
                    composite.extendWith(b.send());
                    assertTrue(composite.readOnly());
                }
            }
        }
    }

    @Test
    public void whenExtendingCompositeBufferWithWriteOffsetAtCapacityExtensionWriteOffsetCanBeNonZero() {
        try (BufferAllocator allocator = BufferAllocator.onHeapUnpooled()) {
            CompositeBuffer composite;
            try (Buffer a = allocator.allocate(8)) {
                composite = CompositeBuffer.compose(allocator, a.send());
            }
            try (composite) {
                composite.writeLong(0);
                try (Buffer b = allocator.allocate(8)) {
                    b.writeInt(1);
                    composite.extendWith(b.send());
                    assertThat(composite.capacity()).isEqualTo(16);
                    assertThat(composite.writerOffset()).isEqualTo(12);
                }
            }
        }
    }

    @Test
    public void whenExtendingCompositeBufferWithWriteOffsetLessThanCapacityExtensionWriteOffsetMustZero() {
        try (BufferAllocator allocator = BufferAllocator.onHeapUnpooled()) {
            CompositeBuffer composite;
            try (Buffer a = allocator.allocate(8)) {
                composite = CompositeBuffer.compose(allocator, a.send());
            }
            try (composite) {
                composite.writeInt(0);
                try (Buffer b = allocator.allocate(8)) {
                    b.writeInt(1);
                    var exc = assertThrows(IllegalArgumentException.class,
                            () -> composite.extendWith(b.send()));
                    assertThat(exc).hasMessageContaining("unwritten gap");
                }
                try (Buffer b = allocator.allocate(8)) {
                    b.setInt(0, 1);
                    composite.extendWith(b.send());
                    assertThat(composite.capacity()).isEqualTo(16);
                    assertThat(composite.writerOffset()).isEqualTo(4);
                }
            }
        }
    }

    @Test
    public void whenExtendingCompositeBufferWithReadOffsetAtCapacityExtensionReadOffsetCanBeNonZero() {
        try (BufferAllocator allocator = BufferAllocator.onHeapUnpooled()) {
            CompositeBuffer composite;
            try (Buffer a = allocator.allocate(8)) {
                composite = CompositeBuffer.compose(allocator, a.send());
            }
            try (composite) {
                composite.writeLong(0);
                composite.readLong();
                try (Buffer b = allocator.allocate(8)) {
                    b.writeInt(1);
                    b.readInt();
                    composite.extendWith(b.send());
                    assertThat(composite.capacity()).isEqualTo(16);
                    assertThat(composite.writerOffset()).isEqualTo(12);
                }
            }
        }
    }

    @Test
    public void whenExtendingCompositeBufferWithReadOffsetLessThanCapacityExtensionReadOffsetMustZero() {
        try (BufferAllocator allocator = BufferAllocator.onHeapUnpooled();
             CompositeBuffer composite = CompositeBuffer.compose(allocator, allocator.allocate(8).send())) {
            composite.writeLong(0);
            composite.readInt();

            Buffer b = allocator.allocate(8);
            b.writeInt(1);
            b.readInt();
            var exc = assertThrows(IllegalArgumentException.class,
                    () -> composite.extendWith(b.send()));
            assertThat(exc).hasMessageContaining("unread gap");
            assertThat(composite.capacity()).isEqualTo(8);
            assertThat(composite.writerOffset()).isEqualTo(8);
            assertThat(composite.readerOffset()).isEqualTo(4);

            composite.extendWith(allocator.allocate(8).writeInt(1).send());
            assertThat(composite.capacity()).isEqualTo(16);
            assertThat(composite.writerOffset()).isEqualTo(12);
            assertThat(composite.readerOffset()).isEqualTo(4);
        }
    }

    @Test
    public void composingReadOnlyBuffersMustCreateReadOnlyCompositeBuffer() {
        try (BufferAllocator allocator = BufferAllocator.onHeapUnpooled();
             Buffer a = allocator.allocate(4).makeReadOnly();
             Buffer b = allocator.allocate(4).makeReadOnly();
             Buffer composite = CompositeBuffer.compose(allocator, a.send(), b.send())) {
            assertTrue(composite.readOnly());
            verifyWriteInaccessible(composite, BufferReadOnlyException.class);
        }
    }

    @Test
    public void composingReadOnlyAndWritableBuffersMustThrow() {
        try (BufferAllocator allocator = BufferAllocator.onHeapUnpooled()) {
            try (Buffer a = allocator.allocate(8).makeReadOnly();
                 Buffer b = allocator.allocate(8)) {
                assertThrows(IllegalArgumentException.class,
                             () -> CompositeBuffer.compose(allocator, a.send(), b.send()));
            }
            try (Buffer a = allocator.allocate(8).makeReadOnly();
                 Buffer b = allocator.allocate(8)) {
                assertThrows(IllegalArgumentException.class,
                             () -> CompositeBuffer.compose(allocator, b.send(), a.send()));
            }
            try (Buffer a = allocator.allocate(8).makeReadOnly();
                 Buffer b = allocator.allocate(8);
                 Buffer c = allocator.allocate(8).makeReadOnly()) {
                assertThrows(IllegalArgumentException.class,
                        () -> CompositeBuffer.compose(allocator, a.send(), b.send(), c.send()));
            }
            try (Buffer a = allocator.allocate(8).makeReadOnly();
                 Buffer b = allocator.allocate(8);
                 Buffer c = allocator.allocate(8)) {
                assertThrows(IllegalArgumentException.class,
                        () -> CompositeBuffer.compose(allocator, b.send(), a.send(), c.send()));
            }
        }
    }

    @Test
    public void compositeWritableBufferCannotBeExtendedWithReadOnlyBuffer() {
        try (BufferAllocator allocator = BufferAllocator.onHeapUnpooled()) {
            CompositeBuffer composite;
            try (Buffer a = allocator.allocate(8)) {
                composite = CompositeBuffer.compose(allocator, a.send());
            }
            try (composite; Buffer b = allocator.allocate(8).makeReadOnly()) {
                assertThrows(IllegalArgumentException.class, () -> composite.extendWith(b.send()));
            }
        }
    }

    @Test
    public void compositeReadOnlyBufferCannotBeExtendedWithWritableBuffer() {
        try (BufferAllocator allocator = BufferAllocator.onHeapUnpooled()) {
            CompositeBuffer composite;
            try (Buffer a = allocator.allocate(8).makeReadOnly()) {
                composite = CompositeBuffer.compose(allocator, a.send());
            }
            try (composite; Buffer b = allocator.allocate(8)) {
                assertThrows(IllegalArgumentException.class, () -> composite.extendWith(b.send()));
            }
        }
    }

    @Test
    public void splitComponentsFloorMustThrowOnOutOfBounds() {
        try (BufferAllocator allocator = BufferAllocator.onHeapUnpooled();
             CompositeBuffer composite = CompositeBuffer.compose(allocator,
                     allocator.allocate(8).send(),
                     allocator.allocate(8).send())) {
            assertThrows(IllegalArgumentException.class, () -> composite.splitComponentsFloor(-1));
            assertThrows(IllegalArgumentException.class, () -> composite.splitComponentsFloor(17));
            try (CompositeBuffer split = composite.splitComponentsFloor(16)) {
                assertThat(split.capacity()).isEqualTo(16);
                assertThat(composite.capacity()).isZero();
            }
        }
    }

    @Test
    public void splitComponentsCeilMustThrowOnOutOfBounds() {
        try (BufferAllocator allocator = BufferAllocator.onHeapUnpooled();
             CompositeBuffer composite = CompositeBuffer.compose(allocator,
                     allocator.allocate(8).send(),
                     allocator.allocate(8).send())) {
            assertThrows(IllegalArgumentException.class, () -> composite.splitComponentsCeil(-1));
            assertThrows(IllegalArgumentException.class, () -> composite.splitComponentsCeil(17));
            try (CompositeBuffer split = composite.splitComponentsCeil(16)) {
                assertThat(split.capacity()).isEqualTo(16);
                assertThat(composite.capacity()).isZero();
            }
        }
    }

    @Test
    public void splitComponentsFloorMustGiveEmptyBufferForOffsetInFirstComponent() {
        try (BufferAllocator allocator = BufferAllocator.onHeapUnpooled();
             CompositeBuffer composite = CompositeBuffer.compose(allocator,
                     allocator.allocate(8).send(),
                     allocator.allocate(8).send())) {
            try (CompositeBuffer split = composite.splitComponentsFloor(4)) {
                assertTrue(isOwned((ResourceSupport<?, ?>) split));
                assertTrue(split.isAccessible());
                assertThat(split.capacity()).isZero();

                assertTrue(isOwned((ResourceSupport<?, ?>) composite));
                assertTrue(composite.isAccessible());
                assertThat(composite.capacity()).isEqualTo(16);
            }
        }
    }

    @Test
    public void splitComponentsFloorMustGiveEmptyBufferForOffsetLastByteInFirstComponent() {
        try (BufferAllocator allocator = BufferAllocator.onHeapUnpooled();
             CompositeBuffer composite = CompositeBuffer.compose(allocator,
                     allocator.allocate(8).send(),
                     allocator.allocate(8).send())) {
            try (CompositeBuffer split = composite.splitComponentsFloor(7)) {
                assertTrue(isOwned((ResourceSupport<?, ?>) split));
                assertTrue(split.isAccessible());
                assertThat(split.capacity()).isZero();

                assertTrue(isOwned((ResourceSupport<?, ?>) composite));
                assertTrue(composite.isAccessible());
                assertThat(composite.capacity()).isEqualTo(16);
            }
        }
    }

    @Test
    public void splitComponentsFloorMustGiveBufferWithFirstComponentForOffsetInSecondComponent() {
        try (BufferAllocator allocator = BufferAllocator.onHeapUnpooled();
             CompositeBuffer composite = CompositeBuffer.compose(allocator,
                     allocator.allocate(8).send(),
                     allocator.allocate(8).send())) {
            try (CompositeBuffer split = composite.splitComponentsFloor(12)) {
                assertTrue(isOwned((ResourceSupport<?, ?>) split));
                assertTrue(split.isAccessible());
                assertThat(split.capacity()).isEqualTo(8);

                assertTrue(isOwned((ResourceSupport<?, ?>) composite));
                assertTrue(composite.isAccessible());
                assertThat(composite.capacity()).isEqualTo(8);
            }
        }
    }

    @Test
    public void splitComponentsFloorMustGiveBufferWithFirstComponentForOffsetOnFirstByteInSecondComponent() {
        try (BufferAllocator allocator = BufferAllocator.onHeapUnpooled();
             CompositeBuffer composite = CompositeBuffer.compose(allocator,
                     allocator.allocate(8).send(),
                     allocator.allocate(8).send())) {
            try (CompositeBuffer split = composite.splitComponentsFloor(8)) {
                assertTrue(isOwned((ResourceSupport<?, ?>) split));
                assertTrue(split.isAccessible());
                assertThat(split.capacity()).isEqualTo(8);

                assertTrue(isOwned((ResourceSupport<?, ?>) composite));
                assertTrue(composite.isAccessible());
                assertThat(composite.capacity()).isEqualTo(8);
            }
        }
    }

    @Test
    public void splitComponentsCeilMustGiveBufferWithFirstComponentForOffsetInFirstComponent() {
        try (BufferAllocator allocator = BufferAllocator.onHeapUnpooled();
             CompositeBuffer composite = CompositeBuffer.compose(allocator,
                     allocator.allocate(8).send(),
                     allocator.allocate(8).send())) {
            try (CompositeBuffer split = composite.splitComponentsCeil(4)) {
                assertTrue(isOwned((ResourceSupport<?, ?>) split));
                assertTrue(split.isAccessible());
                assertThat(split.capacity()).isEqualTo(8);

                assertTrue(isOwned((ResourceSupport<?, ?>) composite));
                assertTrue(composite.isAccessible());
                assertThat(composite.capacity()).isEqualTo(8);
            }
        }
    }

    @Test
    public void splitComponentsCeilMustGiveBufferWithFirstComponentFofOffsetOnLastByteInFirstComponent() {
        try (BufferAllocator allocator = BufferAllocator.onHeapUnpooled();
             CompositeBuffer composite = CompositeBuffer.compose(allocator,
                     allocator.allocate(8).send(),
                     allocator.allocate(8).send())) {
            try (CompositeBuffer split = composite.splitComponentsCeil(7)) {
                assertTrue(isOwned((ResourceSupport<?, ?>) split));
                assertTrue(split.isAccessible());
                assertThat(split.capacity()).isEqualTo(8);

                assertTrue(isOwned((ResourceSupport<?, ?>) composite));
                assertTrue(composite.isAccessible());
                assertThat(composite.capacity()).isEqualTo(8);
            }
        }
    }

    @Test
    public void splitComponentsCeilMustGiveBufferWithFirstAndSecondComponentForOffsetInSecondComponent() {
        try (BufferAllocator allocator = BufferAllocator.onHeapUnpooled();
             CompositeBuffer composite = CompositeBuffer.compose(allocator,
                     allocator.allocate(8).send(),
                     allocator.allocate(8).send())) {
            try (CompositeBuffer split = composite.splitComponentsCeil(12)) {
                assertTrue(isOwned((ResourceSupport<?, ?>) split));
                assertTrue(split.isAccessible());
                assertThat(split.capacity()).isEqualTo(16);

                assertTrue(isOwned((ResourceSupport<?, ?>) composite));
                assertTrue(composite.isAccessible());
                assertThat(composite.capacity()).isEqualTo(0);
            }
        }

        try (BufferAllocator allocator = BufferAllocator.onHeapUnpooled();
             CompositeBuffer composite = CompositeBuffer.compose(allocator,
                     allocator.allocate(8).send(),
                     allocator.allocate(8).send(),
                     allocator.allocate(8).send())) {
            try (CompositeBuffer split = composite.splitComponentsCeil(12)) {
                assertTrue(isOwned((ResourceSupport<?, ?>) split));
                assertTrue(split.isAccessible());
                assertThat(split.capacity()).isEqualTo(16);

                assertTrue(isOwned((ResourceSupport<?, ?>) composite));
                assertTrue(composite.isAccessible());
                assertThat(composite.capacity()).isEqualTo(8);
            }
        }
    }

    @Test
    public void splitComponentsCeilMustGiveBufferWithFirstComponentForOffsetOnFirstByteInSecondComponent() {
        try (BufferAllocator allocator = BufferAllocator.onHeapUnpooled();
             CompositeBuffer composite = CompositeBuffer.compose(allocator,
                     allocator.allocate(8).send(),
                     allocator.allocate(8).send())) {
            try (CompositeBuffer split = composite.splitComponentsCeil(7)) {
                assertTrue(isOwned((ResourceSupport<?, ?>) split));
                assertTrue(split.isAccessible());
                assertThat(split.capacity()).isEqualTo(8);

                assertTrue(isOwned((ResourceSupport<?, ?>) composite));
                assertTrue(composite.isAccessible());
                assertThat(composite.capacity()).isEqualTo(8);
            }
        }
    }

    @Test
    public void splitComponentsCeilMustGiveEmptyBufferForOffsetOnFirstByteInFirstComponent() {
        try (BufferAllocator allocator = BufferAllocator.onHeapUnpooled();
             CompositeBuffer composite = CompositeBuffer.compose(allocator,
                     allocator.allocate(8).send(),
                     allocator.allocate(8).send())) {
            try (CompositeBuffer split = composite.splitComponentsCeil(0)) {
                assertTrue(isOwned((ResourceSupport<?, ?>) split));
                assertTrue(split.isAccessible());
                assertThat(split.capacity()).isZero();

                assertTrue(isOwned((ResourceSupport<?, ?>) composite));
                assertTrue(composite.isAccessible());
                assertThat(composite.capacity()).isEqualTo(16);
            }
        }
    }

    @Test
    public void decomposeOfEmptyBufferMustGiveEmptyArray() {
        CompositeBuffer composite = CompositeBuffer.compose(BufferAllocator.onHeapUnpooled());
        Buffer[] components = composite.decomposeBuffer();
        assertThat(components.length).isZero();
        verifyInaccessible(composite);
        assertThrows(IllegalStateException.class, () -> composite.close());
    }

    @Test
    public void decomposeOfCompositeBufferMustGiveComponentArray() {
        try (BufferAllocator allocator = BufferAllocator.onHeapUnpooled()) {
            CompositeBuffer composite = CompositeBuffer.compose(
                    allocator,
                    allocator.allocate(3).send(),
                    allocator.allocate(3).send(),
                    allocator.allocate(2).send());
            composite.writeLong(0x0102030405060708L);
            assertThat(composite.readInt()).isEqualTo(0x01020304);
            Buffer[] components = composite.decomposeBuffer();
            assertThat(components.length).isEqualTo(3);
            verifyInaccessible(composite);
            assertThat(components[0].readableBytes()).isZero();
            assertThat(components[0].writableBytes()).isZero();
            assertThat(components[1].readableBytes()).isEqualTo(2);
            assertThat(components[1].writableBytes()).isZero();
            assertThat(components[2].readableBytes()).isEqualTo(2);
            assertThat(components[2].writableBytes()).isZero();
            assertThat(components[1].readShort()).isEqualTo((short) 0x0506);
            assertThat(components[2].readShort()).isEqualTo((short) 0x0708);
        }
    }

    @Test
    public void failureInDecomposeMustCloseConstituentBuffers() {
        try (BufferAllocator allocator = BufferAllocator.onHeapUnpooled()) {
            CompositeBuffer composite = CompositeBuffer.compose(
                    allocator,
                    allocator.allocate(3).send(),
                    allocator.allocate(3).send(),
                    allocator.allocate(2).send());
            Drop<Object> throwingDrop = new Drop<>() {
                @Override
                public void drop(Object obj) {
                    throw new RuntimeException("Expected.");
                }

                @Override
                public Drop<Object> fork() {
                    return this;
                }

                @Override
                public void attach(Object obj) {
                }
            };
            try {
                Statics.unsafeSetDrop((ResourceSupport<?, ?>) composite, throwingDrop);
            } catch (Exception e) {
                composite.close();
                throw e;
            }
            Buffer[] inners  = new Buffer[3];
            assertThat(composite.countWritableComponents()).isEqualTo(3);
            int count = composite.forEachWritable(0, (i, component) -> {
                inners[i] = (Buffer) component;
                return true;
            });
            assertThat(count).isEqualTo(3);
            var re = assertThrows(RuntimeException.class, () -> composite.decomposeBuffer());
            assertThat(re.getMessage()).isEqualTo("Expected.");
            // The failure to decompose the buffer should have closed the inner buffers we extracted earlier.
            for (Buffer inner : inners) {
                assertFalse(inner.isAccessible());
            }
        }
    }
}
