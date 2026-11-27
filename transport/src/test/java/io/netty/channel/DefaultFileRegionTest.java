/*
 * Copyright 2019 The Netty Project
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
package io.netty.channel;

import io.netty.util.internal.PlatformDependent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DefaultFileRegionTest {

    private static final byte[] data = new byte[1048576 * 10];

    static {
        ThreadLocalRandom.current().nextBytes(data);
    }

    private static File newFile() throws IOException {
        File file = PlatformDependent.createTempFile("netty-", ".tmp", null);
        file.deleteOnExit();

        final FileOutputStream out = new FileOutputStream(file);
        out.write(data);
        out.close();
        return file;
    }

    @Test
    public void testCreateFromFile() throws IOException  {
        File file = newFile();
        try {
            testFileRegion(new DefaultFileRegion(file, 0, data.length));
        } finally {
            file.delete();
        }
    }

    @Test
    public void testCreateFromFileChannel() throws IOException  {
        File file = newFile();
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r")) {
            testFileRegion(new DefaultFileRegion(randomAccessFile.getChannel(), 0, data.length));
        } finally {
            file.delete();
        }
    }

    private static void testFileRegion(FileRegion region) throws IOException  {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try (WritableByteChannel channel = Channels.newChannel(outputStream)) {
            assertEquals(data.length, region.count());
            assertEquals(0, region.transferred());
            assertEquals(data.length, region.transferTo(channel, 0));
            assertEquals(data.length, region.count());
            assertEquals(data.length, region.transferred());
            assertArrayEquals(data, outputStream.toByteArray());
        }
    }

    @Test
    public void testTruncated() throws IOException  {
        File file = newFile();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try (WritableByteChannel channel = Channels.newChannel(outputStream);
             RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw")) {
            FileRegion region = new DefaultFileRegion(randomAccessFile.getChannel(), 0, data.length);

            randomAccessFile.getChannel().truncate(data.length - 1024);

            assertEquals(data.length, region.count());
            assertEquals(0, region.transferred());

            assertEquals(data.length - 1024, region.transferTo(channel, 0));
            assertEquals(data.length, region.count());
            assertEquals(data.length - 1024, region.transferred());
            assertThrows(IOException.class, () -> region.transferTo(channel, data.length - 1024));
        } finally {
            file.delete();
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    public void testFileEndsBeforeReportedSize() throws IOException {
        File file = newFile();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final int position = 1024;
        // Like files in sysfs, size() reports more than the file holds.
        final long size = data.length + 4096;

        try (WritableByteChannel channel = Channels.newChannel(outputStream);
             RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r")) {
            FileChannel fileChannel = new DelegatingFileChannel(randomAccessFile.getChannel()) {
                @Override
                public long size() {
                    return size;
                }
            };
            final FileRegion region = new DefaultFileRegion(fileChannel, position, size - position);
            try {
                assertThat(region.transferTo(channel, 0)).isEqualTo(data.length - position);
                assertThat(region.transferred()).isEqualTo(data.length - position);
                assertThat(outputStream.toByteArray()).isEqualTo(Arrays.copyOfRange(data, position, data.length));

                // The transfer returns 0 at the end of the file. The first 0 may be a full socket and is retried, the
                // second one at the same offset must fail instead of being retried forever.
                assertThat(region.transferTo(channel, data.length - position)).isZero();
                assertThatThrownBy(() -> region.transferTo(channel, data.length - position))
                        .isInstanceOf(EOFException.class)
                        .hasMessage("Underlying file ended at offset " + data.length + " before the requested count " +
                                (size - position) + " was transferred (size() reported " + size + ')');
            } finally {
                region.release();
            }
        } finally {
            file.delete();
        }
    }

    @Test
    public void testTargetNotAcceptingBytesIsNotEndOfFile() throws IOException {
        File file = newFile();
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r")) {
            FileRegion region = new DefaultFileRegion(randomAccessFile.getChannel(), 0, data.length);
            try {
                // Like a socket whose send buffer is full, the transfer returns 0 and is retried later. The second 0
                // at the same offset checks for the end of the file, and finds more bytes.
                assertThat(region.transferTo(new FullChannel(), 0)).isZero();
                assertThat(region.transferTo(new FullChannel(), 0)).isZero();
                assertThat(region.transferred()).isZero();
            } finally {
                region.release();
            }
        } finally {
            file.delete();
        }
    }

    @Test
    public void testTargetNotAcceptingBytesWhenEndOfFileCanNotBeChecked() throws IOException {
        File file = newFile();
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r")) {
            // Like a FileChannel opened with ExtendedOpenOption.DIRECT, which only allows aligned reads.
            FileChannel fileChannel = new DelegatingFileChannel(randomAccessFile.getChannel()) {
                @Override
                public int read(ByteBuffer dst, long position) throws IOException {
                    throw new IOException("Number of remaining bytes (" + dst.remaining() +
                            ") is not a multiple of the block size (4096)");
                }
            };
            FileRegion region = new DefaultFileRegion(fileChannel, 0, data.length);
            try {
                // The second 0 at the same offset tries the read, which throws.
                assertThat(region.transferTo(new FullChannel(), 0)).isZero();
                assertThat(region.transferTo(new FullChannel(), 0)).isZero();
                assertThat(region.transferred()).isZero();
            } finally {
                region.release();
            }
        } finally {
            file.delete();
        }
    }

    @Test
    public void testOnlyARepeatedTransferOfZeroReadsTheFile() throws IOException {
        File file = newFile();
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r")) {
            final AtomicInteger reads = new AtomicInteger();
            FileChannel fileChannel = new DelegatingFileChannel(randomAccessFile.getChannel()) {
                @Override
                public int read(ByteBuffer dst, long position) throws IOException {
                    reads.incrementAndGet();
                    return super.read(dst, position);
                }
            };
            FileRegion region = new DefaultFileRegion(fileChannel, 0, data.length);
            try {
                // Like a full socket: the first 0 is retried without reading the file.
                assertThat(region.transferTo(new FullChannel(), 0)).isZero();
                assertThat(reads).hasValue(0);

                // The socket took bytes again, so the next 0 is at another offset.
                assertThat(region.transferTo(new TenBytesChannel(), 0)).isEqualTo(10);
                assertThat(region.transferTo(new FullChannel(), 10)).isZero();
                assertThat(reads).hasValue(0);

                // A second 0 at the same offset reads one byte, which is there.
                assertThat(region.transferTo(new FullChannel(), 10)).isZero();
                assertThat(reads).hasValue(1);
                assertThat(region.transferred()).isEqualTo(10);
            } finally {
                region.release();
            }
        } finally {
            file.delete();
        }
    }

    // Like a socket with room for ten more bytes.
    private static final class TenBytesChannel implements WritableByteChannel {
        @Override
        public int write(ByteBuffer src) {
            int written = Math.min(10, src.remaining());
            src.position(src.position() + written);
            return written;
        }

        @Override
        public boolean isOpen() {
            return true;
        }

        @Override
        public void close() {
            // NOOP
        }
    }

    private static final class FullChannel implements WritableByteChannel {
        @Override
        public int write(ByteBuffer src) {
            return 0;
        }

        @Override
        public boolean isOpen() {
            return true;
        }

        @Override
        public void close() {
            // NOOP
        }
    }

    private static class DelegatingFileChannel extends FileChannel {
        private final FileChannel delegate;

        DelegatingFileChannel(FileChannel delegate) {
            this.delegate = delegate;
        }

        @Override
        public long size() throws IOException {
            return delegate.size();
        }

        @Override
        public int read(ByteBuffer dst) throws IOException {
            return delegate.read(dst);
        }

        @Override
        public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
            return delegate.read(dsts, offset, length);
        }

        @Override
        public int write(ByteBuffer src) throws IOException {
            return delegate.write(src);
        }

        @Override
        public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
            return delegate.write(srcs, offset, length);
        }

        @Override
        public long position() throws IOException {
            return delegate.position();
        }

        @Override
        public FileChannel position(long newPosition) throws IOException {
            delegate.position(newPosition);
            return this;
        }

        @Override
        public FileChannel truncate(long size) throws IOException {
            delegate.truncate(size);
            return this;
        }

        @Override
        public void force(boolean metaData) throws IOException {
            delegate.force(metaData);
        }

        @Override
        public long transferTo(long position, long count, WritableByteChannel target) throws IOException {
            return delegate.transferTo(position, count, target);
        }

        @Override
        public long transferFrom(ReadableByteChannel src, long position, long count) throws IOException {
            return delegate.transferFrom(src, position, count);
        }

        @Override
        public int read(ByteBuffer dst, long position) throws IOException {
            return delegate.read(dst, position);
        }

        @Override
        public int write(ByteBuffer src, long position) throws IOException {
            return delegate.write(src, position);
        }

        @Override
        public MappedByteBuffer map(MapMode mode, long position, long size) throws IOException {
            return delegate.map(mode, position, size);
        }

        @Override
        public FileLock lock(long position, long size, boolean shared) throws IOException {
            return delegate.lock(position, size, shared);
        }

        @Override
        public FileLock tryLock(long position, long size, boolean shared) throws IOException {
            return delegate.tryLock(position, size, shared);
        }

        @Override
        protected void implCloseChannel() throws IOException {
            delegate.close();
        }
    }
}
