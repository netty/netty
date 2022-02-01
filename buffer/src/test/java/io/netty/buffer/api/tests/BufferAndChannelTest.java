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
package io.netty.buffer.api.tests;

import io.netty.buffer.api.Buffer;
import io.netty.buffer.api.BufferAllocator;
import io.netty.buffer.api.BufferClosedException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BufferAndChannelTest extends BufferTestSupport {
    private static FileChannel closedChannel;
    private static FileChannel channel;

    @BeforeAll
    static void setUpChannels() throws IOException {
        closedChannel = tempFileChannel();
        closedChannel.close();
        channel = tempFileChannel();
    }

    @AfterAll
    static void tearDownChannels() throws IOException {
        channel.close();
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void readMustThrowIfBufferIsClosed(Fixture fixture) throws IOException {
        try (BufferAllocator allocator = fixture.createAllocator()) {
            long position = channel.position();
            long size = channel.size();
            Buffer empty = allocator.allocate(8);
            empty.close();
            assertThrows(BufferClosedException.class, () -> empty.readIntoChannelWrite(channel, 8));
            assertThat(channel.position()).isEqualTo(position);
            assertThat(channel.size()).isEqualTo(size);
            Buffer withData = allocator.allocate(8);
            withData.writeLong(0x0102030405060708L);
            withData.close();
            assertThrows(BufferClosedException.class, () -> withData.readIntoChannelWrite(channel, 8));
            assertThat(channel.position()).isEqualTo(position);
            assertThat(channel.size()).isEqualTo(size);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void readMustCapAtReadableBytes(Fixture fixture) throws IOException {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            buf.writeLong(0x0102030405060708L);
            buf.skipWritable(-5);
            long position = channel.position();
            long size = channel.size();
            int bytesWritten = buf.readIntoChannelWrite(channel, 8);
            assertThat(bytesWritten).isEqualTo(3);
            assertThat(channel.position()).isEqualTo(3 + position);
            assertThat(channel.size()).isEqualTo(3 + size);
            assertThat(buf.writableBytes()).isEqualTo(5);
            assertThat(buf.readableBytes()).isZero();
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void readMustCapAtLength(Fixture fixture) throws IOException {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            buf.writeLong(0x0102030405060708L);
            long position = channel.position();
            long size = channel.size();
            int bytesWritten = buf.readIntoChannelWrite(channel, 3);
            assertThat(bytesWritten).isEqualTo(3);
            assertThat(channel.position()).isEqualTo(3 + position);
            assertThat(channel.size()).isEqualTo(3 + size);
            assertThat(buf.readableBytes()).isEqualTo(5);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void readMustThrowIfChannelIsClosed(Fixture fixture) throws IOException {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            buf.writeLong(0x0102030405060708L);
            assertThrows(ClosedChannelException.class, () -> buf.readIntoChannelWrite(closedChannel, 8));
            assertTrue(buf.isAccessible());
            assertThat(buf.readableBytes()).isEqualTo(8);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void readMustThrowIfChannelIsNull(Fixture fixture) throws IOException {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            buf.writeLong(0x0102030405060708L);
            assertThrows(NullPointerException.class, () -> buf.readIntoChannelWrite(null, 8));
            assertTrue(buf.isAccessible());
            assertThat(buf.readableBytes()).isEqualTo(8);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void readMustThrowIfLengthIsNegative(Fixture fixture) throws IOException {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            buf.writeLong(0x0102030405060708L);
            assertThrows(IllegalArgumentException.class, () -> buf.readIntoChannelWrite(channel, -1));
            assertThat(buf.readableBytes()).isEqualTo(8);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void readMustIgnoreZeroLengthOperations(Fixture fixture) throws IOException {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            long position = channel.position();
            long size = channel.size();
            buf.writeLong(0x0102030405060708L);
            int bytesWritten = buf.readIntoChannelWrite(channel, 0);
            assertThat(bytesWritten).isZero();
            assertThat(buf.readableBytes()).isEqualTo(8);
            assertThat(channel.position()).isEqualTo(position);
            assertThat(channel.size()).isEqualTo(size);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void readMustMoveDataToChannel(Fixture fixture) throws IOException {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            long value = ThreadLocalRandom.current().nextLong();
            buf.writeLong(value);
            long position = channel.position();
            int bytesWritten = buf.readIntoChannelWrite(channel, 8);
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
    public void writeMustThrowIfBufferIsClosed(Fixture fixture) throws IOException {
        try (BufferAllocator allocator = fixture.createAllocator()) {
            ByteBuffer data = ByteBuffer.allocate(8).putLong(0x0102030405060708L).flip();
            long position = channel.position();
            assertThat(channel.write(data, position)).isEqualTo(8);
            long size = channel.size();
            Buffer empty = allocator.allocate(0);
            empty.close();
            assertThrows(BufferClosedException.class, () -> empty.writeFromChannelRead(channel, 8));
            assertThat(channel.position()).isEqualTo(position);
            assertThat(channel.size()).isEqualTo(size);
            Buffer withAvailableSpace = allocator.allocate(8);
            withAvailableSpace.close();
            assertThrows(BufferClosedException.class, () -> withAvailableSpace.writeFromChannelRead(channel, 8));
            assertThat(channel.position()).isEqualTo(position);
            assertThat(channel.size()).isEqualTo(size);
        } finally {
            channel.position(channel.size()); // Maintain invariants; avoid test isolation failures.
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void writeMustCapAtWritableBytes(Fixture fixture) throws IOException {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(3)) {
            ByteBuffer data = ByteBuffer.allocate(8).putLong(0x0102030405060708L).flip();
            long position = channel.position();
            assertThat(channel.write(data, position)).isEqualTo(8);
            long size = channel.size();
            int bytesRead = buf.writeFromChannelRead(channel, 8);
            assertThat(bytesRead).isEqualTo(3);
            assertThat(channel.position()).isEqualTo(3 + position);
            assertThat(channel.size()).isEqualTo(size);
            assertThat(buf.writableBytes()).isZero();
            assertThat(buf.readableBytes()).isEqualTo(3);
        } finally {
            channel.position(channel.size());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void writeMustCapAtLength(Fixture fixture) throws IOException {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            ByteBuffer data = ByteBuffer.allocate(8).putLong(0x0102030405060708L).flip();
            long position = channel.position();
            assertThat(channel.write(data, position)).isEqualTo(8);
            long size = channel.size();
            int bytesRead = buf.writeFromChannelRead(channel, 3);
            assertThat(bytesRead).isEqualTo(3);
            assertThat(channel.position()).isEqualTo(3 + position);
            assertThat(channel.size()).isEqualTo(size);
            assertThat(buf.writableBytes()).isEqualTo(5);
            assertThat(buf.readableBytes()).isEqualTo(3);
        } finally {
            channel.position(channel.size());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void writeMustThrowIfChannelIsClosed(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(ClosedChannelException.class, () -> buf.writeFromChannelRead(closedChannel, 8));
            assertTrue(buf.isAccessible());
            assertThat(buf.writableBytes()).isEqualTo(8);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void writeMustThrowIfChannelIsNull(Fixture fixture) {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            assertThrows(NullPointerException.class, () -> buf.writeFromChannelRead(null, 8));
            assertTrue(buf.isAccessible());
            assertThat(buf.writableBytes()).isEqualTo(8);
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void writeMustThrowIfLengthIsNegative(Fixture fixture) throws IOException {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            long position = channel.position();
            ByteBuffer data = ByteBuffer.allocate(8).putLong(0x0102030405060708L).flip();
            assertThat(channel.write(data, position)).isEqualTo(8);
            long size = channel.size();
            assertThrows(IllegalArgumentException.class, () -> buf.writeFromChannelRead(channel, -1));
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
    public void writeMustIgnoreZeroLengthOperations(Fixture fixture) throws IOException {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            long position = channel.position();
            ByteBuffer data = ByteBuffer.allocate(8).putLong(0x0102030405060708L).flip();
            assertThat(channel.write(data, position)).isEqualTo(8);
            long size = channel.size();
            int bytesRead = buf.writeFromChannelRead(channel, 0);
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
    public void writeMustMoveDataFromChannel(Fixture fixture) throws IOException {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            long value = ThreadLocalRandom.current().nextLong();
            ByteBuffer data = ByteBuffer.allocate(8).putLong(value).flip();
            long position = channel.position();
            assertThat(channel.write(data, position)).isEqualTo(8);
            long size = channel.size();
            int bytesRead = buf.writeFromChannelRead(channel, 8);
            assertThat(bytesRead).isEqualTo(8);
            assertThat(channel.position()).isEqualTo(8 + position);
            assertThat(channel.size()).isEqualTo(size);
            assertThat(buf.readableBytes()).isEqualTo(8);
            assertEquals(value, buf.readLong());
        } finally {
            channel.position(channel.size());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void writeMustNotReadBeyondEndOfChannel(Fixture fixture) throws IOException {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            ByteBuffer data = ByteBuffer.allocate(8).putInt(0x01020304).flip();
            long position = channel.position();
            assertThat(channel.write(data, position)).isEqualTo(4);
            long size = channel.size();
            int bytesRead = buf.writeFromChannelRead(channel, 8);
            assertThat(bytesRead).isEqualTo(4);
            assertThat(buf.readableBytes()).isEqualTo(4);
            assertThat(channel.position()).isEqualTo(4 + position);
            assertThat(channel.size()).isEqualTo(size);
        } finally {
            channel.position(channel.size());
        }
    }

    @ParameterizedTest
    @MethodSource("allocators")
    public void writeMustReturnMinusOneForEndOfStream(Fixture fixture) throws IOException {
        try (BufferAllocator allocator = fixture.createAllocator();
             Buffer buf = allocator.allocate(8)) {
            long position = channel.position();
            long size = channel.size();
            int bytesRead = buf.writeFromChannelRead(channel, 8);
            assertThat(bytesRead).isEqualTo(-1);
            assertThat(buf.readableBytes()).isEqualTo(0);
            assertThat(channel.position()).isEqualTo(position);
            assertThat(channel.size()).isEqualTo(size);
        } finally {
            channel.position(channel.size());
        }
    }

    private static FileChannel tempFileChannel() throws IOException {
        Path path = Files.createTempFile("BufferAndChannelTest", "txt");
        return FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE);
    }
}
