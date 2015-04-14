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

import io.netty.channel.epoll.Native;

import java.io.File;
import java.io.IOException;

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
        close(fd);
    }

    /**
     * Returns {@code true} if the file descriptor is open.
     */
    public boolean isOpen() {
        return open;
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

    private static native int close(int fd);

    /**
     * Open a new {@link FileDescriptor} for the given path.
     */
    public static FileDescriptor from(String path) throws IOException {
        checkNotNull(path, "path");
        int res = open(path);
        if (res < 0) {
            throw Native.newIOException("open", res);
        }
        return new FileDescriptor(res);
    }

    /**
     * Open a new {@link FileDescriptor} for the given {@link File}.
     */
    public static FileDescriptor from(File file) throws IOException {
        return from(checkNotNull(file, "file").getPath());
    }

    private static native int open(String path);
}
