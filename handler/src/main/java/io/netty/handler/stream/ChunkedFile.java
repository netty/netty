/*
 * Copyright 2012 The Netty Project
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
package io.netty.handler.stream;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.FileRegion;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * A {@link ChunkedInput} that fetches data from a file chunk by chunk.
 * <p>
 * If your operating system supports
 * <a href="http://en.wikipedia.org/wiki/Zero-copy">zero-copy file transfer</a>
 * such as {@code sendfile()}, you might want to use {@link FileRegion} instead.
 */
public class ChunkedFile implements ChunkedInput<ByteBuf> {

    private final RandomAccessFile file;
    private final long startOffset;
    private final long endOffset;
    private final int chunkSize;
    private long offset;

    /**
     * Creates a new instance that fetches data from the specified file.
     */
    public ChunkedFile(File file) throws IOException {
        this(file, ChunkedStream.DEFAULT_CHUNK_SIZE);
    }

    /**
     * Creates a new instance that fetches data from the specified file.
     *
     * @param chunkSize the number of bytes to fetch on each
     *                  {@link #readChunk(ChannelHandlerContext)} call
     */
    public ChunkedFile(File file, int chunkSize) throws IOException {
        this(new RandomAccessFile(file, "r"), chunkSize);
    }

    /**
     * Creates a new instance that fetches data from the specified file.
     */
    public ChunkedFile(RandomAccessFile file) throws IOException {
        this(file, ChunkedStream.DEFAULT_CHUNK_SIZE);
    }

    /**
     * Creates a new instance that fetches data from the specified file.
     *
     * @param chunkSize the number of bytes to fetch on each
     *                  {@link #readChunk(ChannelHandlerContext)} call
     */
    public ChunkedFile(RandomAccessFile file, int chunkSize) throws IOException {
        this(file, 0, file.length(), chunkSize);
    }

    /**
     * Creates a new instance that fetches data from the specified file.
     *
     * @param offset the offset of the file where the transfer begins
     * @param length the number of bytes to transfer
     * @param chunkSize the number of bytes to fetch on each
     *                  {@link #readChunk(ChannelHandlerContext)} call
     */
    public ChunkedFile(RandomAccessFile file, long offset, long length, int chunkSize) throws IOException {
        if (file == null) {
            throw new NullPointerException("file");
        }
        if (offset < 0) {
            throw new IllegalArgumentException(
                    "offset: " + offset + " (expected: 0 or greater)");
        }
        if (length < 0) {
            throw new IllegalArgumentException(
                    "length: " + length + " (expected: 0 or greater)");
        }
        if (chunkSize <= 0) {
            throw new IllegalArgumentException(
                    "chunkSize: " + chunkSize +
                    " (expected: a positive integer)");
        }

        this.file = file;
        this.offset = startOffset = offset;
        endOffset = offset + length;
        this.chunkSize = chunkSize;

        file.seek(offset);
    }

    /**
     * Returns the offset in the file where the transfer began.
     */
    public long startOffset() {
        return startOffset;
    }

    /**
     * Returns the offset in the file where the transfer will end.
     */
    public long endOffset() {
        return endOffset;
    }

    /**
     * Returns the offset in the file where the transfer is happening currently.
     */
    public long currentOffset() {
        return offset;
    }

    @Override
    public boolean isEndOfInput() throws Exception {
        return !(offset < endOffset && file.getChannel().isOpen());
    }

    @Override
    public void close() throws Exception {
        file.close();
    }

    @Deprecated
    @Override
    public ByteBuf readChunk(ChannelHandlerContext ctx) throws Exception {
        return readChunk(ctx.alloc());
    }

    @Override
    public ByteBuf readChunk(ByteBufAllocator allocator) throws Exception {
        long offset = this.offset;
        if (offset >= endOffset) {
            return null;
        }

        int chunkSize = (int) Math.min(this.chunkSize, endOffset - offset);
        // Check if the buffer is backed by an byte array. If so we can optimize it a bit an safe a copy

        ByteBuf buf = allocator.heapBuffer(chunkSize);
        boolean release = true;
        try {
            file.readFully(buf.array(), buf.arrayOffset(), chunkSize);
            buf.writerIndex(chunkSize);
            this.offset = offset + chunkSize;
            release = false;
            return buf;
        } finally {
            if (release) {
                buf.release();
            }
        }
    }

    @Override
    public long length() {
        return endOffset - startOffset;
    }

    @Override
    public long progress() {
        return offset - startOffset;
    }
}
