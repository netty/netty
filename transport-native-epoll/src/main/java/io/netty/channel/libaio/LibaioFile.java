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

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * This is an extension to {@link FileDescriptor} where the file is open with O_DIRECT.
 */
public final class LibaioFile extends FileDescriptor {
    static {
        Native.loadLibrary();
    }

    /**
     * This represents a structure allocated on the native
     * this is a io_context_t
     */
    private ByteBuffer ioContext;

    LibaioFile(int fd, ByteBuffer libaioContext) {
        super(fd);
        this.ioContext = libaioContext;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws IOException {
        open = false;
        LibaioContext.close(fd);
    }

    /**
     * It will submit a write to the queue. The callback sent here will be received on the
     *   {@link LibaioContext#poll(java.nio.ByteBuffer, Object[], int, int).
     * In case of the libaio queue is full (e.g. returning E_AGAIN) this method will return false.

     * Notice: this won't hold a global reference on buffer, callback should hold a reference towards bufferWrite.
     *         And don't free the buffer until the callback was called as this could crash the VM.
     *
     * @param position The position on the file to write. Notice this has to be a multiple of 512.
     * @param size The size of the buffer to use while writing.
     * @param buffer if you are using O_DIRECT the buffer here needs to be allocated by {@link #newBuffer(int)}.
     * @param callback A callback to be returned on the poll method.
     * @return true if successful, false if the queue was full on that case poll and try again
     * @throws IOException in case of error
     *
     * @see DirectFileDescriptorController#poll(ByteBuffer, Object[], int, int)
     */
    public boolean write(long position, int size, ByteBuffer buffer, Object callback) throws IOException {
        return LibaioContext.submitWrite(this.fd, ioContext, position, size, buffer, callback);
    }

    /**
     * It will submit a read to the queue. The callback sent here will be received on the
     *   {@link LibaioContext#poll(java.nio.ByteBuffer, Object[], int, int)}.
     * In case of the libaio queue is full (e.g. returning E_AGAIN) this method will return false.
     *
     * Notice: this won't hold a global reference on buffer, callback should hold a reference towards bufferWrite.
     *         And don't free the buffer until the callback was called as this could crash the VM.
     * *
     * @param position The position on the file to read. Notice this has to be a multiple of 512.
     * @param size The size of the buffer to use while reading.
     * @param buffer if you are using O_DIRECT the buffer here needs to be allocated by {@link #newBuffer(int)}.
     * @param callback A callback to be returned on the poll method.
     * @return true if successful, false if the queue was full on that case poll and try again
     * @throws IOException
     *
     * @see LibaioContext#poll(ByteBuffer, Object[], int, int)
     */
    public boolean read(long position, int size, ByteBuffer buffer, Object callback)  throws IOException {
        return LibaioContext.submitRead(this.fd, ioContext, position, size, buffer, callback);
    }

    /**
     * It will allocate a buffer to be used on libaio operations.
     * Buffers here are allocated with posix_memalign.
     *
     * You need to explicitly free the buffer created from here using the
     *          {@link LibaioContext#freeBuffer(ByteBuffer)}.
     *
     * @param size the size of the buffer.
     * @return the buffer allocated.
     */
    public ByteBuffer newBuffer(int size) {
        return LibaioContext.newAlignedBuffer(size, 512);
    }
}
