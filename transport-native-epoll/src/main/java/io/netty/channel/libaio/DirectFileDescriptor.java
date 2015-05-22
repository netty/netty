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
package io.netty.channel.libaio;

import io.netty.channel.epoll.Native;
import io.netty.channel.unix.FileDescriptor;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * This is an extension to {@link FileDescriptor} where the file is open with O_DIRECT.
 */
public final class DirectFileDescriptor extends FileDescriptor {
    static {
        Native.loadLibrary();
    }

    /**
     * This represents a structure allocated on the native
     * this is a io_context_t
     */
    private ByteBuffer libaioContext;

    private DirectFileDescriptor(int fd, ByteBuffer libaioContext) {
        super(fd);
        this.libaioContext = libaioContext;
    }
    /**
     * Open a new {@link FileDescriptor} for the given path.
     */
    static DirectFileDescriptor from(String path, ByteBuffer ioContext) throws IOException {
        checkNotNull(path, "path");
        checkNotNull(ioContext, "ioContext");

        if (ioContext == null) {
            throw new IOException("Could not initialize libaio context");
        }

        int res = DirectFileDescriptorController.open(path);
        if (res < 0) {
            throw Native.newIOException("open", res);
        }

        return new DirectFileDescriptor(res, ioContext);
    }

    /**
     * Open a new {@link FileDescriptor} for the given {@link File}.
     */
    public static DirectFileDescriptor from(File file, ByteBuffer context) throws IOException {
        return from(checkNotNull(file, "file").getPath(), context);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws IOException {
        open = false;
        DirectFileDescriptorController.close(fd);
    }

    /**
     * It will submit a write to the queue. The callback sent here will be received on the
     *   {@link DirectFileDescriptorController#poll(java.nio.ByteBuffer, Object[], int, int).

     * Notice: this won't hold a global reference on buffer, callback should hold a reference towards bufferWrite.
     *         And don't free the buffer until the callback was called as this could crash the VM.
     *
     * @param position The position on the file to write. Notice this has to be a multiple of 512.
     * @param size The size of the buffer to use while writing.
     * @param buffer a Native buffer allocated by {@link #newBuffer(int)}.
     * @param callback A callback to be returned on the poll method.
     * @throws IOException
     *
     * @see DirectFileDescriptorController#poll(ByteBuffer, Object[], int, int)
     */
    public void write(long position, int size, ByteBuffer buffer, Object callback) throws IOException {
        DirectFileDescriptorController.submitWrite(this.fd, libaioContext, position, size, buffer, callback);
    }

    /**
     * It will submit a read to the queue. The callback sent here will be received on the
     *   {@link DirectFileDescriptorController#poll(java.nio.ByteBuffer, Object[], int, int)}.
     *
     * Notice: this won't hold a global reference on buffer, callback should hold a reference towards bufferWrite.
     *         And don't free the buffer until the callback was called as this could crash the VM.
     * *
     * @param position The position on the file to read. Notice this has to be a multiple of 512.
     * @param size The size of the buffer to use while reading.
     * @param buffer a Native buffer allocated by {@link #newBuffer(int)}.
     * @param callback A callback to be returned on the poll method.
     * @throws IOException
     *
     * @see DirectFileDescriptorController#poll(ByteBuffer, Object[], int, int)
     */
    public void read(long position, int size, ByteBuffer buffer, Object callback)  throws IOException {
        DirectFileDescriptorController.submitRead(this.fd, libaioContext, position, size, buffer, callback);
    }

    /**
     * It will allocate a buffer to be used on libaio operations.
     * Buffers here are allocated with posix_memalign.
     *
     * You need to explicitly free the buffer created from here using the
     *          {@link DirectFileDescriptorController#freeBuffer(ByteBuffer)}.
     *
     * @param size the size of the buffer.
     * @return the buffer allocated.
     */
    public ByteBuffer newBuffer(int size) {
        // TODO: do I need to use the alignment of the file itself or not?
        return DirectFileDescriptorController.newAlignedBuffer(size, 512);
    }
}
