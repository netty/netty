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
package io.netty5.buffer.tests;

import io.netty5.buffer.Buffer;
import io.netty5.buffer.BufferAllocator;
import io.netty5.buffer.BufferClosedException;
import io.netty5.buffer.ByteCursor;
import io.netty5.buffer.internal.InternalBufferUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.ByteOrder.BIG_ENDIAN;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BufferComponentIterationTest extends BufferTestSupport {
    @ParameterizedTest
    @MethodSource("nonCompositeAllocators")
    public void componentCountOfNonCompositeBufferMustBeOne(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThat(buf.countComponents()).isOne();
        }
    }

    @ParameterizedTest
    @MethodSource("nonCompositeAllocators")
    public void readableComponentCountMustBeOneIfThereAreReadableBytes(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThat(buf.countReadableComponents()).isZero();
            buf.writeByte((byte) 1);
            assertThat(buf.countReadableComponents()).isOne();
        }
    }

    @ParameterizedTest
    @MethodSource("nonCompositeAllocators")
    public void writableComponentCountMustBeOneIfThereAreWritableBytes(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThat(buf.countWritableComponents()).isOne();
            buf.writeLong(1);
            assertThat(buf.countWritableComponents()).isZero();
        }
    }

    @Test
    public void compositeBufferComponentCountMustBeTransitiveSum() {
        try (BufferAllocator allocator = BufferAllocator.onHeapUnpooled()) {
            try (Buffer a = allocator.allocate(8);
                 Buffer b = allocator.allocate(8);
                 Buffer c = allocator.allocate(8);
                 Buffer x = allocator.compose(asList(b, c));
                 Buffer buf = allocator.compose(asList(a, x))) {
                assertThat(buf.countComponents()).isEqualTo(3);
                assertThat(buf.countReadableComponents()).isZero();
                assertThat(buf.countWritableComponents()).isEqualTo(3);
                buf.writeInt(1);
                assertThat(buf.countReadableComponents()).isOne();
                assertThat(buf.countWritableComponents()).isEqualTo(3);
                buf.writeInt(1);
                assertThat(buf.countReadableComponents()).isOne();
                assertThat(buf.countWritableComponents()).isEqualTo(2);
                buf.writeInt(1);
                assertThat(buf.countReadableComponents()).isEqualTo(2);
                assertThat(buf.countWritableComponents()).isEqualTo(2);
                buf.writeInt(1);
                assertThat(buf.countReadableComponents()).isEqualTo(2);
                assertThat(buf.countWritableComponents()).isOne();
                buf.writeInt(1);
                assertThat(buf.countReadableComponents()).isEqualTo(3);
                assertThat(buf.countWritableComponents()).isOne();
                buf.writeInt(1);
                assertThat(buf.countReadableComponents()).isEqualTo(3);
                assertThat(buf.countWritableComponents()).isZero();
            }
        }
    }

    @ParameterizedTest
    @MethodSource("nonCompositeAllocators")
    public void forEachComponentMustVisitBuffer(Fixture fixture) {
        long value = 0x0102030405060708L;
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer bufBERW = allocator.allocate(8).writeLong(value);
             Buffer bufBERO = allocator.allocate(8).writeLong(value).makeReadOnly()) {
            verifyReadingForEachSingleComponent(fixture, bufBERW);
            verifyReadingForEachSingleComponent(fixture, bufBERO);
        }
    }

    @Test
    public void forEachComponentMustVisitAllReadableConstituentBuffersInOrder() {
        try (BufferAllocator allocator = BufferAllocator.onHeapUnpooled();
             Buffer a = allocator.allocate(4).writeInt(1);
             Buffer b = allocator.allocate(4).writeInt(2);
             Buffer c = allocator.allocate(4).writeInt(3);
             Buffer composite = allocator.compose(asList(a, b, c))) {
            var list = new LinkedList<Integer>(List.of(1, 2, 3));
            int index = 0;
            try (var iterator = composite.forEachComponent()) {
                for (var component = iterator.first(); component != null; component = component.next()) {
                    var buffer = component.readableBuffer();
                    int bufferValue = buffer.getInt();
                    int expectedValue = list.pollFirst().intValue();
                    assertEquals(expectedValue, bufferValue);
                    assertEquals(bufferValue, index + 1);
                    assertThrows(ReadOnlyBufferException.class, () -> buffer.put(0, (byte) 0xFF));
                    var writableBuffer = InternalBufferUtils.tryGetWritableBufferFromReadableComponent(component);
                    if (writableBuffer != null) {
                        int pos = writableBuffer.position();
                        bufferValue = writableBuffer.getInt();
                        assertEquals(expectedValue, bufferValue);
                        assertEquals(bufferValue, index + 1);
                        writableBuffer.put(pos, (byte) 0xFF);
                        assertEquals((byte) 0xFF, writableBuffer.get(pos));
                    }
                    index++;
                }
            }
            assertEquals(3, index);
            assertThat(list).isEmpty();
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void forEachComponentOnClosedBufferMustThrow(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator()) {
            var writableBuffer = allocator.allocate(8);
            writableBuffer.close();
            assertThrows(BufferClosedException.class, () -> writableBuffer.forEachComponent());

            var readableBuffer = allocator.allocate(8);
            readableBuffer.writeLong(0);
            readableBuffer.close();
            assertThrows(BufferClosedException.class, () -> readableBuffer.forEachComponent());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void forEachComponentMustAllowCollectingBuffersInArray(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator()) {
            try (Buffer composite = allocator.compose(asList(
                    allocator.allocate(4),
                    allocator.allocate(4),
                    allocator.allocate(4)))) {
                int i = 1;
                while (composite.writableBytes() > 0) {
                    composite.writeByte((byte) i++);
                }
                ByteBuffer[] buffers = new ByteBuffer[composite.countComponents()];
                try (var iterator = composite.forEachComponent()) {
                    int index = 0;
                    for (var component = iterator.first(); component != null; component = component.next()) {
                        buffers[index] = component.readableBuffer();
                        index++;
                    }
                }
                i = 1;
                assertThat(buffers.length).isGreaterThanOrEqualTo(1);
                for (ByteBuffer buffer : buffers) {
                    while (buffer.hasRemaining()) {
                        assertEquals((byte) i++, buffer.get());
                    }
                }
            }

            try (Buffer single = allocator.allocate(8)) {
                ByteBuffer[] buffers = new ByteBuffer[single.countComponents()];
                try (var iterator = single.forEachComponent()) {
                    int index = 0;
                    for (var component = iterator.first(); component != null; component = component.next()) {
                        buffers[index] = component.writableBuffer();
                        index++;
                    }
                }
                assertThat(buffers.length).isGreaterThanOrEqualTo(1);
                int i = 1;
                for (ByteBuffer buffer : buffers) {
                    while (buffer.hasRemaining()) {
                        buffer.put((byte) i++);
                    }
                }
                single.writerOffset(single.capacity());
                i = 1;
                while (single.readableBytes() > 0) {
                    assertEquals((byte) i++, single.readByte());
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void forEachComponentMustExposeByteCursors(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(20)) {
            buf.writeLong(0x0102030405060708L);
            buf.writeLong(0x1112131415161718L);
            assertEquals(0x01020304, buf.readInt());
            try (Buffer actualData = allocator.allocate(buf.readableBytes());
                 Buffer expectedData = allocator.allocate(12)) {
                expectedData.writeInt(0x05060708);
                expectedData.writeInt(0x11121314);
                expectedData.writeInt(0x15161718);

                try (var iterator = buf.forEachComponent()) {
                    for (var component = iterator.first(); component != null; component = component.next()) {
                        ByteCursor forward = component.openCursor();
                        while (forward.readByte()) {
                            actualData.writeByte(forward.getByte());
                        }
                    }
                }

                assertEquals(expectedData.readableBytes(), actualData.readableBytes());
                while (expectedData.readableBytes() > 0) {
                    assertEquals(expectedData.readByte(), actualData.readByte());
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void forEachComponentMustExposeByteCursorsPartial(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(32)) {
            buf.writeLong(0x0102030405060708L);
            buf.writeLong(0x1112131415161718L);
            assertEquals(0x01020304, buf.readInt());
            try (Buffer actualData = allocator.allocate(buf.readableBytes());
                 Buffer expectedData = allocator.allocate(12)) {
                expectedData.writeInt(0x05060708);
                expectedData.writeInt(0x11121314);
                expectedData.writeInt(0x15161718);

                try (var iterator = buf.forEachComponent()) {
                    for (var component = iterator.first(); component != null; component = component.next()) {
                        ByteCursor forward = component.openCursor();
                        while (forward.readByte()) {
                            actualData.writeByte(forward.getByte());
                        }
                    }
                }

                assertEquals(expectedData.readableBytes(), actualData.readableBytes());
                while (expectedData.readableBytes() > 0) {
                    assertEquals(expectedData.readByte(), actualData.readByte());
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void forEachComponentMustReturnNullFirstWhenNotReadable(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(0)) {
            try (var iterator = buf.forEachComponent()) {
                // First component may or may not be null.
                var component = iterator.first();
                if (component != null) {
                    assertThat(component.readableBytes()).isZero();
                    assertThat(component.readableArrayLength()).isZero();
                    ByteBuffer byteBuffer = component.readableBuffer();
                    assertThat(byteBuffer.remaining()).isZero();
                    assertThat(byteBuffer.capacity()).isZero();
                    assertNull(component.next());
                }
            }
            try (var iterator = buf.forEachComponent()) {
                // First *readable* component is definitely null.
                assertNull(iterator.firstReadable());
            }
        }
    }

    @ParameterizedTest
    @MethodSource("nonCompositeAllocators")
    public void forEachWritableMustVisitBuffer(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer bufBERW = allocator.allocate(8)) {
            verifyWritingForEachSingleComponent(fixture, bufBERW);
        }
    }

    @Test
    public void forEachComponentMustVisitAllWritableConstituentBuffersInOrder() {
        try (BufferAllocator allocator = BufferAllocator.onHeapUnpooled()) {
            try (Buffer a = allocator.allocate(8);
                 Buffer b = allocator.allocate(8);
                 Buffer c = allocator.allocate(8);
                 Buffer buf = allocator.compose(asList(a, b, c))) {
                try (var iterator = buf.forEachComponent()) {
                    int index = 0;
                    for (var component = iterator.first(); component != null; component = component.next()) {
                        component.writableBuffer().putLong(0x0102030405060708L + 0x1010101010101010L * index);
                        index++;
                    }
                }
                buf.writerOffset(3 * 8);
                assertEquals(0x0102030405060708L, buf.readLong());
                assertEquals(0x1112131415161718L, buf.readLong());
                assertEquals(0x2122232425262728L, buf.readLong());
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void forEachComponentChangesMadeToByteBufferComponentMustBeReflectedInBuffer(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(9)) {
            buf.writeByte((byte) 0xFF);
            AtomicInteger writtenCounter = new AtomicInteger();
            try (var iterator = buf.forEachComponent()) {
                for (var component = iterator.first(); component != null; component = component.next()) {
                    ByteBuffer buffer = component.writableBuffer();
                    while (buffer.hasRemaining()) {
                        buffer.put((byte) writtenCounter.incrementAndGet());
                    }
                }
            }
            buf.writerOffset(9);
            assertEquals((byte) 0xFF, buf.readByte());
            assertEquals(0x0102030405060708L, buf.readLong());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void forEachComponentMustHaveZeroWritableBytesWhenNotWritable(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(0)) {
            try (var iterator = buf.forEachComponent()) {
                // First component may or may not be null.
                var component = iterator.first();
                if (component != null) {
                    assertThat(component.writableBytes()).isZero();
                    assertThat(component.writableArrayLength()).isZero();
                    ByteBuffer byteBuffer = component.writableBuffer();
                    assertThat(byteBuffer.remaining()).isZero();
                    assertThat(byteBuffer.capacity()).isZero();
                    assertNull(component.next());
                }
            }
            try (var iterator = buf.forEachComponent()) {
                // First *writable* component is definitely null.
                assertNull(iterator.firstWritable());
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void changesMadeToByteBufferComponentsInIterationShouldBeReflectedInBuffer(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            AtomicInteger counter = new AtomicInteger();
            try (var iterator = buf.forEachComponent()) {
                for (var component = iterator.first(); component != null; component = component.next()) {
                    ByteBuffer buffer = component.writableBuffer();
                    while (buffer.hasRemaining()) {
                        buffer.put((byte) counter.incrementAndGet());
                    }
                }
            }
            buf.writerOffset(buf.capacity());
            for (int i = 0; i < 8; i++) {
                assertEquals((byte) i + 1, buf.getByte(i));
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void componentsOfReadOnlyBufferMustNotHaveAnyWritableBytes(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8).makeReadOnly()) {
            try (var iteration = buf.forEachComponent()) {
                for (var c = iteration.first(); c != null; c = c.next()) {
                    assertThat(c.writableBytes()).isZero();
                    assertThat(c.writableArrayLength()).isZero();
                    ByteBuffer byteBuffer = c.writableBuffer();
                    assertThat(byteBuffer.remaining()).isZero();
                    assertThat(byteBuffer.capacity()).isZero();
                }
            }
            try (var iteration = buf.forEachComponent()) {
                assertNull(iteration.firstWritable());
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void forEachComponentMustBeAbleToIncrementReaderOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8);
             Buffer target = allocator.allocate(5)) {
            buf.writeLong(0x0102030405060708L);
            try (var iterator = buf.forEachComponent()) {
                for (var component = iterator.first(); component != null; component = component.next()) {
                    while (target.writableBytes() > 0 && component.readableBytes() > 0) {
                        ByteBuffer byteBuffer = component.readableBuffer();
                        byte value = byteBuffer.get();
                        byteBuffer.clear();
                        target.writeByte(value);
                        var cmp = component; // Capture for lambda.
                        assertThrows(IndexOutOfBoundsException.class, () -> cmp.skipReadableBytes(9));
                        component.skipReadableBytes(0);
                        component.skipReadableBytes(1);
                    }
                }
            }
            assertThat(buf.readerOffset()).isEqualTo(5);
            assertThat(buf.readableBytes()).isEqualTo(3);
            assertThat(target.readableBytes()).isEqualTo(5);
            try (Buffer expected = allocator.copyOf(new byte[] { 0x01, 0x02, 0x03, 0x04, 0x05 })) {
                assertThat(target).isEqualTo(expected);
            }
            try (Buffer expected = allocator.copyOf(new byte[] { 0x06, 0x07, 0x08 })) {
                assertThat(buf).isEqualTo(expected);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void forEachComponentMustBeAbleToIncrementWriterOffset(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8).writeLong(0x0102030405060708L);
             Buffer target = buf.copy()) {
            buf.writerOffset(0); // Prime the buffer with data, but leave the write-offset at zero.
            try (var iterator = buf.forEachComponent()) {
                for (var component = iterator.first(); component != null; component = component.next()) {
                    while (component.writableBytes() > 0) {
                        ByteBuffer byteBuffer = component.writableBuffer();
                        byte value = byteBuffer.get();
                        byteBuffer.clear();
                        assertThat(value).isEqualTo(target.readByte());
                        var cmp = component; // Capture for lambda.
                        assertThrows(IndexOutOfBoundsException.class, () -> cmp.skipWritableBytes(9));
                        component.skipWritableBytes(0);
                        component.skipWritableBytes(1);
                    }
                }
            }
            assertThat(buf.writerOffset()).isEqualTo(8);
            assertThat(target.readerOffset()).isEqualTo(8);
            target.readerOffset(0);
            assertThat(buf).isEqualTo(target);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void negativeSkipReadableOnReadableComponentMustThrow(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            buf.writeLong(0x0102030405060708L);
            assertThat(buf.readInt()).isEqualTo(0x01020304);
            try (var iterator = buf.forEachComponent()) {
                for (var component = iterator.first(); component != null; component = component.next()) {
                    var cmp = component; // Capture for lambda.
                    assertThrows(IllegalArgumentException.class, () -> cmp.skipReadableBytes(-1));
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void negativeSkipWritableOnWritableComponentMustThrow(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            try (var iterator = buf.forEachComponent()) {
                for (var component = iterator.first(); component != null; component = component.next()) {
                    var cmp = component; // Capture for lambda.
                    assertThrows(IllegalArgumentException.class, () -> cmp.skipWritableBytes(-1));
                }
            }
        }
    }

    public static void verifyReadingForEachSingleComponent(Fixture fixture, Buffer buf) {
        try (var iterator = buf.forEachComponent()) {
            for (var component = iterator.first(); component != null; component = component.next()) {
                ByteBuffer buffer = component.readableBuffer();
                assertThat(buffer.position()).isZero();
                assertThat(buffer.limit()).isEqualTo(8);
                assertThat(buffer.capacity()).isEqualTo(8);
                assertEquals(0x0102030405060708L, buffer.getLong());

                if (fixture.isDirect()) {
                    assertThat(component.readableNativeAddress()).isNotZero();
                } else {
                    assertThat(component.readableNativeAddress()).isZero();
                }

                if (component.hasReadableArray()) {
                    byte[] array = component.readableArray();
                    byte[] arrayCopy = new byte[component.readableArrayLength()];
                    System.arraycopy(array, component.readableArrayOffset(), arrayCopy, 0, arrayCopy.length);
                    if (buffer.order() == BIG_ENDIAN) {
                        assertThat(arrayCopy).containsExactly(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08);
                    } else {
                        assertThat(arrayCopy).containsExactly(0x08, 0x07, 0x06, 0x05, 0x04, 0x03, 0x02, 0x01);
                    }
                }

                assertThrows(ReadOnlyBufferException.class, () -> buffer.put(0, (byte) 0xFF));
            }
        }
    }

    public static void verifyWritingForEachSingleComponent(Fixture fixture, Buffer buf) {
        buf.fill((byte) 0);
        try (var iterator = buf.forEachComponent()) {
            for (var component = iterator.first(); component != null; component = component.next()) {
                ByteBuffer buffer = component.writableBuffer();
                assertThat(buffer.position()).isZero();
                assertThat(buffer.limit()).isEqualTo(8);
                assertThat(buffer.capacity()).isEqualTo(8);
                buffer.putLong(0x0102030405060708L);
                buffer.flip();
                assertEquals(0x0102030405060708L, buffer.getLong());
                buf.writerOffset(8);
                assertEquals(0x0102030405060708L, buf.getLong(0));

                if (fixture.isDirect()) {
                    assertThat(component.writableNativeAddress()).isNotZero();
                } else {
                    assertThat(component.writableNativeAddress()).isZero();
                }

                buf.writerOffset(0);
                if (component.hasWritableArray()) {
                    byte[] array = component.writableArray();
                    int offset = component.writableArrayOffset();
                    byte[] arrayCopy = new byte[component.writableArrayLength()];
                    System.arraycopy(array, offset, arrayCopy, 0, arrayCopy.length);
                    if (buffer.order() == BIG_ENDIAN) {
                        assertThat(arrayCopy).containsExactly(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08);
                    } else {
                        assertThat(arrayCopy).containsExactly(0x08, 0x07, 0x06, 0x05, 0x04, 0x03, 0x02, 0x01);
                    }
                }

                buffer.put(0, (byte) 0xFF);
                assertEquals((byte) 0xFF, buffer.get(0));
                assertEquals((byte) 0xFF, buf.getByte(0));
            }
        }
    }
}
