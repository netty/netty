/*
 * Copyright 2015 The Netty Project
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
package io.netty.channel.unix;


import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static io.netty.channel.unix.Errors.ioResult;
import static io.netty.channel.unix.Errors.newIOException;
import static io.netty.channel.unix.Limits.IOV_MAX;
import static io.netty.util.internal.ObjectUtil.checkNotNull;
import static io.netty.util.internal.ObjectUtil.checkPositiveOrZero;
import static java.lang.Math.min;

/**
 * Native {@link FileDescriptor} implementation which allows to wrap an {@code int} and provide a
 * {@link FileDescriptor} for it.
 */
public class FileDescriptor {

    private static final AtomicIntegerFieldUpdater<FileDescriptor> stateUpdater =
            AtomicIntegerFieldUpdater.newUpdater(FileDescriptor.class, "state");

    private static final int STATE_CLOSED_MASK = 1;
    private static final int STATE_INPUT_SHUTDOWN_MASK = 1 << 1;
    private static final int STATE_OUTPUT_SHUTDOWN_MASK = 1 << 2;
    private static final int STATE_ALL_MASK = STATE_CLOSED_MASK |
                                              STATE_INPUT_SHUTDOWN_MASK |
                                              STATE_OUTPUT_SHUTDOWN_MASK;

    /**
     * Bit map = [Output Shutdown | Input Shutdown | Closed]
     */
    volatile int state;
    final int fd;

    public FileDescriptor(int fd) {
        Unix.ensureAvailability();
        checkPositiveOrZero(fd, "fd");
        this.fd = fd;
    }

    /**
     * Return the int value of the filedescriptor.
     */
    public final int intValue() {
        return fd;
    }

    protected boolean markClosed() {
        for (;;) {
            int state = this.state;
            if (isClosed(state)) {
                return false;
            }
            // Once a close operation happens, the channel is considered shutdown.
            if (casState(state, state | STATE_ALL_MASK)) {
                return true;
            }
        }
    }

    /**
     * Close the file descriptor.
     */
    public void close() throws IOException {
        if (markClosed()) {
            int res = close(fd);
            if (res < 0) {
                throw newIOException("close", res);
            }
        }
    }

    /**
     * Returns {@code true} if the file descriptor is open.
     */
    public boolean isOpen() {
        return !isClosed(state);
    }

    public final int write(ByteBuffer buf, int pos, int limit) throws IOException {
        int res = write(fd, buf, pos, limit);
        if (res >= 0) {
            return res;
        }
        return ioResult("write", res);
    }

    public final int writeAddress(long address, int pos, int limit) throws IOException {
        int res = writeAddress(fd, address, pos, limit);
        if (res >= 0) {
            return res;
        }
        return ioResult("writeAddress", res);
    }

    public final long writev(ByteBuffer[] buffers, int offset, int length, long maxBytesToWrite) throws IOException {
        long res = writev(fd, buffers, offset, min(IOV_MAX, length), maxBytesToWrite);
        if (res >= 0) {
            return res;
        }
        return ioResult("writev", (int) res);
    }

    public final long writevAddresses(long memoryAddress, int length) throws IOException {
        long res = writevAddresses(fd, memoryAddress, length);
        if (res >= 0) {
            return res;
        }
        return ioResult("writevAddresses", (int) res);
    }

    public final int read(ByteBuffer buf, int pos, int limit) throws IOException {
        int res = read(fd, buf, pos, limit);
        if (res > 0) {
            return res;
        }
        if (res == 0) {
            return -1;
        }
        return ioResult("read", res);
    }

    public final int readAddress(long address, int pos, int limit) throws IOException {
        int res = readAddress(fd, address, pos, limit);
        if (res > 0) {
            return res;
        }
        if (res == 0) {
            return -1;
        }
        return ioResult("readAddress", res);
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
        int res = open(checkNotNull(path, "path"));
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

    final boolean casState(int expected, int update) {
        return stateUpdater.compareAndSet(this, expected, update);
    }

    static boolean isClosed(int state) {
        return (state & STATE_CLOSED_MASK) != 0;
    }

    static boolean isInputShutdown(int state) {
        return (state & STATE_INPUT_SHUTDOWN_MASK) != 0;
    }

    static boolean isOutputShutdown(int state) {
        return (state & STATE_OUTPUT_SHUTDOWN_MASK) != 0;
    }

    static int inputShutdown(int state) {
        return state | STATE_INPUT_SHUTDOWN_MASK;
    }

    static int outputShutdown(int state) {
        return state | STATE_OUTPUT_SHUTDOWN_MASK;
    }

    private static native int open(String path);
    private static native int close(int fd);

    private static native int write(int fd, ByteBuffer buf, int pos, int limit);
    private static native int writeAddress(int fd, long address, int pos, int limit);
    private static native long writev(int fd, ByteBuffer[] buffers, int offset, int length, long maxBytesToWrite);
    private static native long writevAddresses(int fd, long memoryAddress, int length);

    private static native int read(int fd, ByteBuffer buf, int pos, int limit);
    private static native int readAddress(int fd, long address, int pos, int limit);

    private static native long newPipe();
}
