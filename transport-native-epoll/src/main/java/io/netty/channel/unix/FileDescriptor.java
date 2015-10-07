/*
 * Copyright 2015 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.unix;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import static io.netty.channel.unix.Errors.CONNECTION_RESET_EXCEPTION_READ;
import static io.netty.channel.unix.Errors.CONNECTION_RESET_EXCEPTION_WRITE;
import static io.netty.channel.unix.Errors.CONNECTION_RESET_EXCEPTION_WRITEV;
import static io.netty.channel.unix.Errors.ioResult;
import static io.netty.channel.unix.Errors.newIOException;
import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * Native {@link FileDescriptor} implementation which allows to wrap an {@code int} and provide a
 * {@link FileDescriptor} for it.
 */
public class FileDescriptor {
    private final int fd;
    private volatile boolean open = true;

    public FileDescriptor(int fd) {
        if (fd < 0) {
            throw new IllegalArgumentException("fd must be >= 0");
        }
        this.fd = fd;
    }

    /**
     * Return the int value of the filedescriptor.
     */
    public int intValue() {
        return fd;
    }

    /**
     * Close the file descriptor.
     */
    public void close() throws IOException {
        open = false;
        int res = close(fd);
        if (res < 0) {
            throw newIOException("close", res);
        }
    }

    /**
     * Returns {@code true} if the file descriptor is open.
     */
    public boolean isOpen() {
        return open;
    }

    public final int write(ByteBuffer buf, int pos, int limit) throws IOException {
        int res = write(fd, buf, pos, limit);
        if (res >= 0) {
            return res;
        }
        return ioResult("write", res, CONNECTION_RESET_EXCEPTION_WRITE);
    }

    public final int writeAddress(long address, int pos, int limit) throws IOException {
        int res = writeAddress(fd, address, pos, limit);
        if (res >= 0) {
            return res;
        }
        return ioResult("writeAddress", res, CONNECTION_RESET_EXCEPTION_WRITE);
    }

    public final long writev(ByteBuffer[] buffers, int offset, int length) throws IOException {
        long res = writev(fd, buffers, offset, length);
        if (res >= 0) {
            return res;
        }
        return ioResult("writev", (int) res, CONNECTION_RESET_EXCEPTION_WRITEV);
    }

    public final long writevAddresses(long memoryAddress, int length) throws IOException {
        long res = writevAddresses(fd, memoryAddress, length);
        if (res >= 0) {
            return res;
        }
        return ioResult("writevAddresses", (int) res, CONNECTION_RESET_EXCEPTION_WRITEV);
    }

    public final int read(ByteBuffer buf, int pos, int limit) throws IOException {
        int res = read(fd, buf, pos, limit);
        if (res > 0) {
            return res;
        }
        if (res == 0) {
            return -1;
        }
        return ioResult("read", res, CONNECTION_RESET_EXCEPTION_READ);
    }

    public final int readAddress(long address, int pos, int limit) throws IOException {
        int res = readAddress(fd, address, pos, limit);
        if (res > 0) {
            return res;
        }
        if (res == 0) {
            return -1;
        }
        return ioResult("readAddress", res, CONNECTION_RESET_EXCEPTION_READ);
    }

    @Override
    public String toString() {
        return "FileDescriptor{" +
                "fd=" + fd +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FileDescriptor)) {
            return false;
        }

        return fd == ((FileDescriptor) o).fd;
    }

    @Override
    public int hashCode() {
        return fd;
    }

    /**
     * Open a new {@link FileDescriptor} for the given path.
     */
    public static FileDescriptor from(String path) throws IOException {
        checkNotNull(path, "path");
        int res = open(path);
        if (res < 0) {
            throw newIOException("open", res);
        }
        return new FileDescriptor(res);
    }

    /**
     * Open a new {@link FileDescriptor} for the given {@link File}.
     */
    public static FileDescriptor from(File file) throws IOException {
        return from(checkNotNull(file, "file").getPath());
    }

    /**
     * @return [0] = read end, [1] = write end
     */
    public static FileDescriptor[] pipe() throws IOException {
        long res = newPipe();
        if (res < 0) {
            throw newIOException("newPipe", (int) res);
        }
        return new FileDescriptor[]{new FileDescriptor((int) (res >>> 32)), new FileDescriptor((int) res)};
    }

    private static native int open(String path);
    private static native int close(int fd);

    private static native int write(int fd, ByteBuffer buf, int pos, int limit);
    private static native int writeAddress(int fd, long address, int pos, int limit);
    private static native long writev(int fd, ByteBuffer[] buffers, int offset, int length);
    private static native long writevAddresses(int fd, long memoryAddress, int length);

    private static native int read(int fd, ByteBuffer buf, int pos, int limit);
    private static native int readAddress(int fd, long address, int pos, int limit);

    private static native long newPipe();
}
