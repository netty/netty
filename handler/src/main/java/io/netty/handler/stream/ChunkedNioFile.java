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
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.FileRegion;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

/**
 * A {@link ChunkedInput} that fetches data from a file chunk by chunk using
 * NIO {@link FileChannel}.
 * <p>
 * If your operating system supports
 * <a href="http://en.wikipedia.org/wiki/Zero-copy">zero-copy file transfer</a>
 * such as {@code sendfile()}, you might want to use {@link FileRegion} instead.
 */
public class ChunkedNioFile implements ChunkedInput<ByteBuf> {

    private final FileChannel in;
    private final long startOffset;
    private final long endOffset;
    private final int chunkSize;
    private long offset;

    /**
     * Creates a new instance that fetches data from the specified file.
     */
    public ChunkedNioFile(File in) throws IOException {
        this(new FileInputStream(in).getChannel());
    }

    /**
     * Creates a new instance that fetches data from the specified file.
     *
     * @param chunkSize the number of bytes to fetch on each
     *                  {@link #readChunk(ChannelHandlerContext)} call
     */
    public ChunkedNioFile(File in, int chunkSize) throws IOException {
        this(new FileInputStream(in).getChannel(), chunkSize);
    }

    /**
     * Creates a new instance that fetches data from the specified file.
     */
    public ChunkedNioFile(FileChannel in) throws IOException {
        this(in, ChunkedStream.DEFAULT_CHUNK_SIZE);
    }

    /**
     * Creates a new instance that fetches data from the specified file.
     *
     * @param chunkSize the number of bytes to fetch on each
     *                  {@link #readChunk(ChannelHandlerContext)} call
     */
    public ChunkedNioFile(FileChannel in, int chunkSize) throws IOException {
        this(in, 0, in.size(), chunkSize);
    }

    /**
     * Creates a new instance that fetches data from the specified file.
     *
     * @param offset the offset of the file where the transfer begins
     * @param length the number of bytes to transfer
     * @param chunkSize the number of bytes to fetch on each
     *                  {@link #readChunk(ChannelHandlerContext)} call
     */
    public ChunkedNioFile(FileChannel in, long offset, long length, int chunkSize)
            throws IOException {
        if (in == null) {
            throw new NullPointerException("in");
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

        if (offset != 0) {
            in.position(offset);
        }
        this.in = in;
        this.chunkSize = chunkSize;
        this.offset = startOffset = offset;
        endOffset = offset + length;
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
        return !(offset < endOffset && in.isOpen());
    }

    @Override
    public void close() throws Exception {
        in.close();
    }

    @Override
    public ByteBuf readChunk(ChannelHandlerContext ctx) throws Exception {
        long offset = this.offset;
        if (offset >= endOffset) {
            return null;
        }

        int chunkSize = (int) Math.min(this.chunkSize, endOffset - offset);
        ByteBuf buffer = ctx.alloc().buffer(chunkSize);
        boolean release = true;
        try {
            int readBytes = 0;
            for (;;) {
                int localReadBytes = buffer.writeBytes(in, chunkSize - readBytes);
                if (localReadBytes < 0) {
                    break;
                }
                readBytes += localReadBytes;
                if (readBytes == chunkSize) {
                    break;
                }
            }
            this.offset += readBytes;
            release = false;
            return buffer;
        } finally {
            if (release) {
                buffer.release();
            }
        }
    }
}
