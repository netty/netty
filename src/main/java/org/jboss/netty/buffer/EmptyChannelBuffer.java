/*
 * Copyright 2013 The Netty Project
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

package org.jboss.netty.buffer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;

/**
 * An immutable empty buffer implementation. Typically used as a singleton via
 * {@link ChannelBuffers#EMPTY_BUFFER} and returned by {@link ChannelBuffers#buffer(int)} etc when
 * an empty buffer is requested.
 *
 * <p>Note: For backwards compatibility, this class extends {@link BigEndianHeapChannelBuffer}.
 * However, it never makes any writes to the reader and writer indices, which avoids contention
 * when the singleton instance is used concurrently.
 */
public class EmptyChannelBuffer extends BigEndianHeapChannelBuffer {

    private static final byte[] BUFFER = {};

    EmptyChannelBuffer() {
        super(BUFFER);
    }

    @Override
    public void clear() {
    }

    @Override
    public void readerIndex(int readerIndex) {
        if (readerIndex != 0) {
            throw new IndexOutOfBoundsException("Invalid readerIndex: "
                    + readerIndex + " - Maximum is 0");
        }
    }

    @Override
    public void writerIndex(int writerIndex) {
        if (writerIndex != 0) {
            throw new IndexOutOfBoundsException("Invalid writerIndex: "
                    + writerIndex + " - Maximum is 0");
        }
    }

    @Override
    public void setIndex(int readerIndex, int writerIndex) {
        if (writerIndex != 0 || readerIndex != 0) {
            throw new IndexOutOfBoundsException("Invalid writerIndex: "
                    + writerIndex + " - Maximum is " + readerIndex + " or "
                    + capacity());
        }
    }

    @Override
    public void markReaderIndex() {
    }

    @Override
    public void resetReaderIndex() {
    }

    @Override
    public void markWriterIndex() {
    }

    @Override
    public void resetWriterIndex() {
    }

    @Override
    public void discardReadBytes() {
    }

    @Override
    public ChannelBuffer readBytes(int length) {
        checkReadableBytes(length);
        return this;
    }

    @Override
    public ChannelBuffer readSlice(int length) {
        checkReadableBytes(length);
        return this;
    }

    @Override
    public void readBytes(byte[] dst, int dstIndex, int length) {
        checkReadableBytes(length);
    }

    @Override
    public void readBytes(byte[] dst) {
        checkReadableBytes(dst.length);
    }

    @Override
    public void readBytes(ChannelBuffer dst) {
        checkReadableBytes(dst.writableBytes());
    }

    @Override
    public void readBytes(ChannelBuffer dst, int length) {
        checkReadableBytes(length);
    }

    @Override
    public void readBytes(ChannelBuffer dst, int dstIndex, int length) {
        checkReadableBytes(length);
    }

    @Override
    public void readBytes(ByteBuffer dst) {
        checkReadableBytes(dst.remaining());
    }

    @Override
    public int readBytes(GatheringByteChannel out, int length) throws IOException {
        checkReadableBytes(length);
        return 0;
    }

    @Override
    public void readBytes(OutputStream out, int length) throws IOException {
        checkReadableBytes(length);
    }

    @Override
    public void skipBytes(int length) {
        checkReadableBytes(length);
    }

    @Override
    public void writeBytes(byte[] src, int srcIndex, int length) {
        checkWritableBytes(length);
    }

    @Override
    public void writeBytes(ChannelBuffer src, int length) {
        checkWritableBytes(length);
    }

    @Override
    public void writeBytes(ChannelBuffer src, int srcIndex, int length) {
        checkWritableBytes(length);
    }

    @Override
    public void writeBytes(ByteBuffer src) {
        checkWritableBytes(src.remaining());
    }

    @Override
    public int writeBytes(InputStream in, int length) throws IOException {
        checkWritableBytes(length);
        return 0;
    }

    @Override
    public int writeBytes(ScatteringByteChannel in, int length) throws IOException {
        checkWritableBytes(length);
        return 0;
    }

    @Override
    public void writeZero(int length) {
        checkWritableBytes(length);
    }

    /**
     * Throws an {@link IndexOutOfBoundsException} the length is not 0.
     */
    private void checkWritableBytes(int length) {
        if (length == 0) {
            return;
        }
        if (length > 0) {
            throw new IndexOutOfBoundsException("Writable bytes exceeded - Need "
                    + length + ", maximum is " + 0);
        } else {
            throw new IndexOutOfBoundsException("length < 0");
        }
    }

    /**
     * Throws an {@link IndexOutOfBoundsException} the length is not 0.
     */
    protected void checkReadableBytes(int length) {
        if (length == 0) {
            return;
        }
        if (length > 0) {
            throw new IndexOutOfBoundsException("Not enough readable bytes - Need "
                    + length + ", maximum is " + readableBytes());
        } else {
            throw new IndexOutOfBoundsException("length < 0");
        }
    }
}
