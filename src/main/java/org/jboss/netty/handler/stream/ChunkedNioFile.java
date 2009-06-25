/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.handler.stream;

import static org.jboss.netty.buffer.ChannelBuffers.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * A {@link ChunkedInput} that fetches data from a file chunk by chunk using
 * NIO {@link FileChannel}.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @author Frederic Bregier
 * @version $Rev$, $Date$
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
