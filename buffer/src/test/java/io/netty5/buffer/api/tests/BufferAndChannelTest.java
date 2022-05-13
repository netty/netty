/*
 * Copyright 2022 The Netty Project
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
import io.netty5.buffer.api.DefaultBufferAllocators;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ThreadLocalRandom;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.DELETE_ON_CLOSE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BufferAndChannelTest extends BufferTestSupport {
    private static FileChannel closedChannel;
    private static FileChannel channel;

    @BeforeAll
    static void setUpChannels(@TempDir Path parentDirectory) throws IOException {
        closedChannel = tempFileChannel(parentDirectory);
        closedChannel.close();
        channel = tempFileChannel(parentDirectory);
    }

    @AfterAll
    static void tearDownChannels() throws IOException {
        channel.close();
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void transferToMustThrowIfBufferIsClosed(Fixture fixture) throws IOException {
        try (BufferAllocator allocator = fixture.createAllocator()) {
            long position = channel.position();
            long size = channel.size();
            Buffer empty = allocator.allocate(8);
            empty.close();
            assertThrows(BufferClosedException.class, () -> empty.transferTo(channel, 8));
            assertThat(channel.position()).isEqualTo(position);
            assertThat(channel.size()).isEqualTo(size);
            Buffer withData = allocator.allocate(8);
            withData.writeLong(0x0102030405060708L);
            withData.close();
            assertThrows(BufferClosedException.class, () -> withData.transferTo(channel, 8));
            assertThat(channel.position()).isEqualTo(position);
            assertThat(channel.size()).isEqualTo(size);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void transferToMustCapAtReadableBytes(Fixture fixture) throws IOException {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            buf.writeLong(0x0102030405060708L);
            buf.writerOffset(buf.writerOffset() - 5);
            long position = channel.position();
            long size = channel.size();
            int bytesWritten = buf.transferTo(channel, 8);
            assertThat(bytesWritten).isEqualTo(3);
            assertThat(channel.position()).isEqualTo(3 + position);
            assertThat(channel.size()).isEqualTo(3 + size);
            assertThat(buf.writableBytes()).isEqualTo(5);
            assertThat(buf.readableBytes()).isZero();
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void transferToMustCapAtLength(Fixture fixture) throws IOException {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            buf.writeLong(0x0102030405060708L);
            long position = channel.position();
            long size = channel.size();
            int bytesWritten = buf.transferTo(channel, 3);
            assertThat(bytesWritten).isEqualTo(3);
            assertThat(channel.position()).isEqualTo(3 + position);
            assertThat(channel.size()).isEqualTo(3 + size);
            assertThat(buf.readableBytes()).isEqualTo(5);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void transferToMustThrowIfChannelIsClosed(Fixture fixture) throws IOException {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            buf.writeLong(0x0102030405060708L);
            assertThrows(ClosedChannelException.class, () -> buf.transferTo(closedChannel, 8));
            assertTrue(buf.isAccessible());
            assertThat(buf.readableBytes()).isEqualTo(8);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void transferToMustThrowIfChannelIsNull(Fixture fixture) throws IOException {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            buf.writeLong(0x0102030405060708L);
            assertThrows(NullPointerException.class, () -> buf.transferTo(null, 8));
            assertTrue(buf.isAccessible());
            assertThat(buf.readableBytes()).isEqualTo(8);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void transferToMustThrowIfLengthIsNegative(Fixture fixture) throws IOException {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            buf.writeLong(0x0102030405060708L);
            assertThrows(IllegalArgumentException.class, () -> buf.transferTo(channel, -1));
            assertThat(buf.readableBytes()).isEqualTo(8);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void transferToMustIgnoreZeroLengthOperations(Fixture fixture) throws IOException {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            long position = channel.position();
            long size = channel.size();
            buf.writeLong(0x0102030405060708L);
            int bytesWritten = buf.transferTo(channel, 0);
            assertThat(bytesWritten).isZero();
            assertThat(buf.readableBytes()).isEqualTo(8);
            assertThat(channel.position()).isEqualTo(position);
            assertThat(channel.size()).isEqualTo(size);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void transferToMustMoveDataToChannel(Fixture fixture) throws IOException {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            long value = ThreadLocalRandom.current().nextLong();
            buf.writeLong(value);
            long position = channel.position();
            int bytesWritten = buf.transferTo(channel, 8);
            assertThat(bytesWritten).isEqualTo(8);
            ByteBuffer buffer = ByteBuffer.allocate(8);
            int bytesRead = channel.read(buffer, position);
            assertThat(bytesRead).isEqualTo(8);
            buffer.flip();
            assertEquals(value, buffer.getLong());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void transferToMustMoveReadOnlyDataToChannel(Fixture fixture) throws IOException {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            long value = ThreadLocalRandom.current().nextLong();
            buf.writeLong(value).makeReadOnly();
            long position = channel.position();
            int bytesWritten = buf.transferTo(channel, 8);
            assertThat(bytesWritten).isEqualTo(8);
            ByteBuffer buffer = ByteBuffer.allocate(8);
            int bytesRead = channel.read(buffer, position);
            assertThat(bytesRead).isEqualTo(8);
            buffer.flip();
            assertEquals(value, buffer.getLong());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void transferToZeroBytesMustNotThrowOnClosedChannel(Fixture fixture) throws IOException {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer empty = allocator.allocate(0);
             Buffer notEmpty = allocator.allocate(4).writeInt(42)) {
            empty.transferTo(closedChannel, 4);
            notEmpty.transferTo(closedChannel, 0);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void transferFromMustThrowIfBufferIsClosed(Fixture fixture) throws IOException {
        doTransferFromMustThrowIfBufferIsClosed(fixture, false);
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void transferFromWithPositionMustThrowIfBufferIsClosed(Fixture fixture) throws IOException {
        doTransferFromMustThrowIfBufferIsClosed(fixture, true);
    }

    private static void doTransferFromMustThrowIfBufferIsClosed(Fixture fixture, boolean withPosition)
            throws IOException {
        try (BufferAllocator allocator = fixture.createAllocator()) {
            ByteBuffer data = ByteBuffer.allocate(8).putLong(0x0102030405060708L).flip();
            long position = channel.position();
            assertThat(channel.write(data, position)).isEqualTo(8);
            long size = channel.size();
            Buffer empty = allocator.allocate(0);
            empty.close();
            assertThrows(BufferClosedException.class, () -> {
                if (withPosition) {
                    empty.transferFrom(channel, 4 + position, 8);
                } else {
                    empty.transferFrom(channel, 8);
                }
            });
            assertThat(channel.position()).isEqualTo(position);
            assertThat(channel.size()).isEqualTo(size);
            Buffer withAvailableSpace = allocator.allocate(8);
            withAvailableSpace.close();
            assertThrows(BufferClosedException.class, () -> {
                if (withPosition) {
                    withAvailableSpace.transferFrom(channel, 4 + position, 8);
                } else {
                    withAvailableSpace.transferFrom(channel, 8);
                }
            });
            assertThat(channel.position()).isEqualTo(position);
            assertThat(channel.size()).isEqualTo(size);
        } finally {
            channel.position(channel.size());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void transferFromMustCapAtWritableBytes(Fixture fixture) throws IOException {
        doTransferFromMustCapAtWritableBytes(fixture, false);
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void transferFromWithPositionMustCapAtWritableBytes(Fixture fixture) throws IOException {
        doTransferFromMustCapAtWritableBytes(fixture, true);
    }

    private static void doTransferFromMustCapAtWritableBytes(Fixture fixture, boolean withPosition) throws IOException {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(3)) {
            ByteBuffer data = ByteBuffer.allocate(8).putLong(0x0102030405060708L).flip();
            long position = channel.position();
            assertThat(channel.write(data, position)).isEqualTo(8);
            long size = channel.size();
            int bytesRead = withPosition ? buf.transferFrom(channel, 4 + position, 8) : buf.transferFrom(channel, 8);
            assertThat(bytesRead).isEqualTo(3);
            assertThat(channel.position()).isEqualTo(withPosition ? position : 3 + position);
            assertThat(channel.size()).isEqualTo(size);
            assertThat(buf.writableBytes()).isZero();
            assertThat(buf.readableBytes()).isEqualTo(3);
        } finally {
            channel.position(channel.size());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void transferFromMustCapAtLength(Fixture fixture) throws IOException {
        doTransferFromMustCapAtLength(fixture, false);
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void transferFromWithPositionMustCapAtLength(Fixture fixture) throws IOException {
        doTransferFromMustCapAtLength(fixture, true);
    }

    private static void doTransferFromMustCapAtLength(Fixture fixture, boolean withPosition) throws IOException {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            ByteBuffer data = ByteBuffer.allocate(8).putLong(0x0102030405060708L).flip();
            long position = channel.position();
            assertThat(channel.write(data, position)).isEqualTo(8);
            long size = channel.size();
            int bytesRead = withPosition ? buf.transferFrom(channel, 4 + position, 3) : buf.transferFrom(channel, 3);
            assertThat(bytesRead).isEqualTo(3);
            assertThat(channel.position()).isEqualTo(withPosition ? position : 3 + position);
            assertThat(channel.size()).isEqualTo(size);
            assertThat(buf.writableBytes()).isEqualTo(5);
            assertThat(buf.readableBytes()).isEqualTo(3);
        } finally {
            channel.position(channel.size());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void transferFromMustThrowIfChannelIsClosed(Fixture fixture) {
        doTransferFromMustThrowIfChannelIsClosed(fixture, false);
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void transferFromWithPositionMustThrowIfChannelIsClosed(Fixture fixture) {
        doTransferFromMustThrowIfChannelIsClosed(fixture, true);
    }

    private static void doTransferFromMustThrowIfChannelIsClosed(Fixture fixture, boolean withPosition) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(ClosedChannelException.class, () -> {
                if (withPosition) {
                    buf.transferFrom(closedChannel, 4, 8);
                } else {
                    buf.transferFrom(closedChannel, 8);
                }
            });
            assertTrue(buf.isAccessible());
            assertThat(buf.writableBytes()).isEqualTo(8);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void transferFromMustThrowIfChannelIsNull(Fixture fixture) {
        doTransferFromMustThrowIfChannelIsNull(fixture, false);
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void transferFromWithPositionMustThrowIfChannelIsNull(Fixture fixture) {
        doTransferFromMustThrowIfChannelIsNull(fixture, true);
    }

    private static void doTransferFromMustThrowIfChannelIsNull(Fixture fixture, boolean withPosition) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(NullPointerException.class, () -> {
                if (withPosition) {
                    buf.transferFrom(null, 4, 8);
                } else {
                    buf.transferFrom(null, 8);
                }
            });
            assertTrue(buf.isAccessible());
            assertThat(buf.writableBytes()).isEqualTo(8);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void transferFromMustThrowIfLengthIsNegative(Fixture fixture) throws IOException {
        doTransferFromMustThrowIfLengthIsNegative(fixture, false);
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void transferFromWithPositionMustThrowIfLengthIsNegative(Fixture fixture) throws IOException {
        doTransferFromMustThrowIfLengthIsNegative(fixture, true);
    }

    private static void doTransferFromMustThrowIfLengthIsNegative(Fixture fixture, boolean withPosition)
            throws IOException {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            long position = channel.position();
            ByteBuffer data = ByteBuffer.allocate(8).putLong(0x0102030405060708L).flip();
            assertThat(channel.write(data, position)).isEqualTo(8);
            long size = channel.size();
            assertThrows(IllegalArgumentException.class, () -> {
                if (withPosition) {
                    buf.transferFrom(channel, 4 + position, -1);
                } else {
                    buf.transferFrom(channel, -1);
                }
            });
            assertTrue(buf.isAccessible());
            assertThat(buf.writableBytes()).isEqualTo(8);
            assertThat(channel.position()).isEqualTo(position);
            assertThat(channel.size()).isEqualTo(size);
        } finally {
            channel.position(channel.size());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void transferFromMustIgnoreZeroLengthOperations(Fixture fixture) throws IOException {
        doTransferFromMustIgnoreZeroLengthOperations(fixture, false);
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void transferFromWithPositionMustIgnoreZeroLengthOperations(Fixture fixture) throws IOException {
        doTransferFromMustIgnoreZeroLengthOperations(fixture, true);
    }

    private static void doTransferFromMustIgnoreZeroLengthOperations(Fixture fixture, boolean withPosition)
            throws IOException {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            long position = channel.position();
            ByteBuffer data = ByteBuffer.allocate(8).putLong(0x0102030405060708L).flip();
            assertThat(channel.write(data, position)).isEqualTo(8);
            long size = channel.size();
            int bytesRead = withPosition ? buf.transferFrom(channel, 4 + position, 0) : buf.transferFrom(channel, 0);
            assertThat(bytesRead).isEqualTo(0);
            assertThat(buf.readableBytes()).isZero();
            assertThat(buf.writableBytes()).isEqualTo(8);
            assertThat(channel.position()).isEqualTo(position);
            assertThat(channel.size()).isEqualTo(size);
        } finally {
            channel.position(channel.size());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void transferFromMustMoveDataFromChannel(Fixture fixture) throws IOException {
        doTransferFromMustMoveDataFromChannel(fixture, false);
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void transferFromWithPositionMustMoveDataFromChannel(Fixture fixture) throws IOException {
        doTransferFromMustMoveDataFromChannel(fixture, true);
    }

    private static void doTransferFromMustMoveDataFromChannel(Fixture fixture, boolean withPosition)
            throws IOException {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            long value = ThreadLocalRandom.current().nextLong();
            ByteBuffer data = ByteBuffer.allocate(8).putLong(value).flip();
            long position = channel.position();
            assertThat(channel.write(data, position)).isEqualTo(8);
            long size = channel.size();
            int bytesRead = withPosition ? buf.transferFrom(channel, position, 8) : buf.transferFrom(channel, 8);
            assertThat(bytesRead).isEqualTo(8);
            assertThat(channel.position()).isEqualTo(withPosition ? position : 8 + position);
            assertThat(channel.size()).isEqualTo(size);
            assertThat(buf.readableBytes()).isEqualTo(8);
            assertEquals(value, buf.readLong());
        } finally {
            channel.position(channel.size());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void transferFromMustNotReadBeyondEndOfChannel(Fixture fixture) throws IOException {
        doTransferFromMustNotReadBeyondEndOfChannel(fixture, false);
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void transferFromWithPositionMustNotReadBeyondEndOfChannel(Fixture fixture) throws IOException {
        doTransferFromMustNotReadBeyondEndOfChannel(fixture, true);
    }

    private static void doTransferFromMustNotReadBeyondEndOfChannel(Fixture fixture, boolean withPosition)
            throws IOException {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            ByteBuffer data = ByteBuffer.allocate(8).putInt(0x01020304).flip();
            long position = channel.position();
            assertThat(channel.write(data, position)).isEqualTo(4);
            long size = channel.size();
            int bytesRead = withPosition ? buf.transferFrom(channel, position, 8) : buf.transferFrom(channel, 8);
            assertThat(bytesRead).isEqualTo(4);
            assertThat(buf.readableBytes()).isEqualTo(4);
            assertThat(channel.position()).isEqualTo(withPosition ? position : 4 + position);
            assertThat(channel.size()).isEqualTo(size);
        } finally {
            channel.position(channel.size());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void transferFromMustReturnMinusOneForEndOfStream(Fixture fixture) throws IOException {
        doTransferFromMustReturnMinusOneForEndOfStream(fixture, false);
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void transferFromWithPositionMustReturnMinusOneForEndOfStream(Fixture fixture) throws IOException {
        doTransferFromMustReturnMinusOneForEndOfStream(fixture, true);
    }

    private static void doTransferFromMustReturnMinusOneForEndOfStream(Fixture fixture, boolean withPosition)
            throws IOException {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            long position = channel.position();
            long size = channel.size();
            int bytesRead = withPosition ? buf.transferFrom(channel, position, 8) : buf.transferFrom(channel, 8);
            assertThat(bytesRead).isEqualTo(-1);
            assertThat(buf.readableBytes()).isEqualTo(0);
            assertThat(channel.position()).isEqualTo(position);
            assertThat(channel.size()).isEqualTo(size);
        } finally {
            channel.position(channel.size());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void transferFromMustReturnMinusOneForEndOfStreamNonScattering(Fixture fixture) throws IOException {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            long position = channel.position();
            long size = channel.size();
            ReadableByteChannel nonScatteringChannel = new ReadableByteChannel() {
                @Override
                public int read(ByteBuffer dst) throws IOException {
                    return channel.read(dst);
                }

                @Override
                public boolean isOpen() {
                    return channel.isOpen();
                }

                @Override
                public void close() throws IOException {
                    channel.close();
                }
            };
            int bytesRead = buf.transferFrom(nonScatteringChannel, 8);
            assertThat(bytesRead).isEqualTo(-1);
            assertThat(buf.readableBytes()).isEqualTo(0);
            assertThat(channel.position()).isEqualTo(position);
            assertThat(channel.size()).isEqualTo(size);
        } finally {
            channel.position(channel.size());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void transferFromMustStartFromWritableOffset(Fixture fixture) throws IOException {
        doTransferFromMustStartFromWritableOffset(fixture, false);
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void transferFromWithPositionMustStartFromWritableOffset(Fixture fixture) throws IOException {
        doTransferFromMustStartFromWritableOffset(fixture, true);
    }

    private static void doTransferFromMustStartFromWritableOffset(Fixture fixture, boolean withPosition)
            throws IOException {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(4)) {
            ByteBuffer data = ByteBuffer.allocate(4).putInt(0x01020304).flip();
            long position = channel.position();
            assertThat(channel.write(data, position)).isEqualTo(4);
            long size = channel.size();
            int bytesRead = withPosition ? buf.transferFrom(channel, position, 2) : buf.transferFrom(channel, 2);
            bytesRead += withPosition ? buf.transferFrom(channel, 2 + position, 2) : buf.transferFrom(channel, 2);
            assertThat(bytesRead).isEqualTo(4);
            assertThat(buf.readableBytes()).isEqualTo(4);
            assertThat(channel.position()).isEqualTo(withPosition ? position : 4 + position);
            assertThat(channel.size()).isEqualTo(size);
            for (int i = 0; i < buf.readableBytes(); i++) {
                assertThat(buf.readByte()).isEqualTo(data.get(i));
            }
        } finally {
            channel.position(channel.size());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void transferFromMustThrowIfBufferIsReadOnly(Fixture fixture) throws IOException {
        doTransferFromMustThrowIfBufferIsReadOnly(fixture, false);
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void transferFromWithPositionMustThrowIfBufferIsReadOnly(Fixture fixture) throws IOException {
        doTransferFromMustThrowIfBufferIsReadOnly(fixture, true);
    }

    private static void doTransferFromMustThrowIfBufferIsReadOnly(Fixture fixture, boolean withPosition)
            throws IOException {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8).writeLong(0x0102030405060708L).makeReadOnly()) {
            long position = channel.position();
            long size = channel.size();
            assertThrows(BufferReadOnlyException.class, () -> {
                if (withPosition) {
                    buf.transferFrom(channel, 4 + position, 8);
                } else {
                    buf.transferFrom(channel, 8);
                }
            });
            assertThat(buf.readableBytes()).isEqualTo(8);
            assertThat(channel.position()).isEqualTo(position);
            assertThat(channel.size()).isEqualTo(size);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void transferFromZeroBytesMustNotThrowOnClosedChannel(Fixture fixture) throws IOException {
        doTransferFromZeroBytesMustNotThrowOnClosedChannel(fixture, false);
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void transferFromWithPositionZeroBytesMustNotThrowOnClosedChannel(Fixture fixture) throws IOException {
        doTransferFromZeroBytesMustNotThrowOnClosedChannel(fixture, true);
    }

    private static void doTransferFromZeroBytesMustNotThrowOnClosedChannel(Fixture fixture, boolean withPosition)
            throws IOException {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer empty = allocator.allocate(0);
             Buffer notEmpty = allocator.allocate(4)) {
            if (withPosition) {
                empty.transferFrom(closedChannel, 4, 4);
                notEmpty.transferFrom(closedChannel, 4, 0);
            } else {
                empty.transferFrom(closedChannel, 4);
                notEmpty.transferFrom(closedChannel, 0);
            }
        }
    }

    @Test
    public void partialFailureOfTransferToMustKeepChannelAndBufferPositionsInSync(@TempDir Path parentDir)
            throws IOException {
        BufferAllocator allocator = DefaultBufferAllocators.onHeapAllocator();
        Path path = parentDir.resolve("transferTo");
        try (FileChannel channel = FileChannel.open(path, READ, WRITE, CREATE);
             Buffer buf = allocator.compose(asList(
                        allocator.allocate(4).writeInt(0x01020304).send(),
                        allocator.allocate(4).writeInt(0x05060708).send()))) {
            WritableByteChannel channelWrapper = new WritableByteChannel() {
                private boolean pastFirstCall;

                @Override
                public int write(ByteBuffer src) throws IOException {
                    if (pastFirstCall) {
                        throw new IOException("boom");
                    }
                    pastFirstCall = true;
                    return channel.write(src);
                }

                @Override
                public boolean isOpen() {
                    return channel.isOpen();
                }

                @Override
                public void close() throws IOException {
                    channel.close();
                }
            };

            long position = channel.position();
            long size = channel.size();
            var e = assertThrows(IOException.class, () -> buf.transferTo(channelWrapper, 8));
            assertThat(e).hasMessage("boom");
            assertThat(channel.position()).isEqualTo(4 + position);
            assertThat(channel.size()).isEqualTo(4 + size);
            assertThat(buf.readableBytes()).isEqualTo(4);
            assertThat(buf.readerOffset()).isEqualTo(4);
        }
    }

    @Test
    public void partialFailureOfTransferFromMustKeepChannelAndBufferPositionsInSync(@TempDir Path parentDir)
            throws IOException {
        BufferAllocator allocator = DefaultBufferAllocators.onHeapAllocator();
        Path path = parentDir.resolve("transferFrom");
        try (FileChannel channel = FileChannel.open(path, READ, WRITE, CREATE);
             Buffer buf = allocator.compose(asList(
                allocator.allocate(4).send(),
                allocator.allocate(4).send()))) {
            ByteBuffer byteBuffer = ByteBuffer.allocate(8).putLong(0x0102030405060708L).flip();
            assertThat(channel.write(byteBuffer)).isEqualTo(8);
            channel.position(0);

            ReadableByteChannel channelWrapper = new ReadableByteChannel() {
                private boolean pastFirstCall;

                @Override
                public int read(ByteBuffer dst) throws IOException {
                    if (pastFirstCall) {
                        throw new IOException("boom");
                    }
                    pastFirstCall = true;
                    return channel.read(dst);
                }

                @Override
                public boolean isOpen() {
                    return channel.isOpen();
                }

                @Override
                public void close() throws IOException {
                    channel.close();
                }
            };

            long position = channel.position();
            long size = channel.size();
            var e = assertThrows(IOException.class, () -> buf.transferFrom(channelWrapper, 8));
            assertThat(e).hasMessage("boom");
            assertThat(channel.position()).isEqualTo(4 + position);
            assertThat(channel.size()).isEqualTo(size);
            assertThat(buf.readableBytes()).isEqualTo(4);
            assertThat(buf.writerOffset()).isEqualTo(4);
        }
    }

    private static FileChannel tempFileChannel(Path parentDirectory) throws IOException {
        Path path = Files.createTempFile(parentDirectory, "BufferAndChannelTest", "txt");
        return FileChannel.open(path, READ, WRITE, DELETE_ON_CLOSE);
    }
}
