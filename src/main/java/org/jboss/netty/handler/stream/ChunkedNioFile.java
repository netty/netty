/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.handler.stream;

import static org.jboss.netty.buffer.ChannelBuffers.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.jboss.netty.channel.FileRegion;

/**
 * A {@link ChunkedInput} that fetches data from a file chunk by chunk using
 * NIO {@link FileChannel}.
 * <p>
 * If your operating system supports
 * <a href="http://en.wikipedia.org/wiki/Zero-copy">zero-copy file transfer</a>
 * such as {@code sendfile()}, you might want to use {@link FileRegion} instead.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @author Frederic Bregier
 * @version $Rev: 2243 $, $Date: 2010-04-16 14:01:55 +0900 (Fri, 16 Apr 2010) $
 */
public class ChunkedNioFile implements ChunkedInput {

    private final FileChannel in;
    private long startOffset;
    private final long endOffset;
    private final int chunkSize;
    private volatile long offset;

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
     *                  {@link #nextChunk()} call
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
     *                  {@link #nextChunk()} call
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
     *                  {@link #nextChunk()} call
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
    public long getStartOffset() {
        return startOffset;
    }

    /**
     * Returns the offset in the file where the transfer will end.
     */
    public long getEndOffset() {
        return endOffset;
    }

    /**
     * Returns the offset in the file where the transfer is happening currently.
     */
    public long getCurrentOffset() {
        return offset;
    }

    public boolean hasNextChunk() throws Exception {
        return offset < endOffset && in.isOpen();
    }

    public boolean isEndOfInput() throws Exception {
        return !hasNextChunk();
    }

    public void close() throws Exception {
        in.close();
    }

    public Object nextChunk() throws Exception {
        long offset = this.offset;
        if (offset >= endOffset) {
            return null;
        }

        int chunkSize = (int) Math.min(this.chunkSize, endOffset - offset);
        byte[] chunkArray = new byte[chunkSize];
        ByteBuffer chunk = ByteBuffer.wrap(chunkArray);
        int readBytes = 0;
        for (;;) {
            int localReadBytes = in.read(chunk);
            if (localReadBytes < 0) {
                break;
            }
            readBytes += localReadBytes;
            if (readBytes == chunkSize) {
                break;
            }
        }

        this.offset += readBytes;
        return wrappedBuffer(chunkArray);
    }
}
