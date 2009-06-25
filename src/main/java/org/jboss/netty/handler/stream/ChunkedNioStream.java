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

import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

import org.jboss.netty.buffer.ChannelBuffer;

/**
 * A {@link ChunkedInput} that fetches data from a {@link ReadableByteChannel}
 * chunk by chunk.  Please note that the {@link ReadableByteChannel} must
 * operate in blocking mode.  Non-blocking mode channels are not supported.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @author Frederic Bregier
 * @version $Rev$, $Date$
 */
public class ChunkedNioStream implements ChunkedInput {

    private final ReadableByteChannel in;

    private final int chunkSize;
    private volatile long offset;

    /**
     * Associated ByteBuffer
     */
    private ByteBuffer byteBuffer = null;

    /**
     * Creates a new instance that fetches data from the specified channel.
     */
    public ChunkedNioStream(ReadableByteChannel in) {
        this(in, ChunkedStream.DEFAULT_CHUNK_SIZE);
    }

    /**
     * Creates a new instance that fetches data from the specified channel.
     *
     * @param chunkSize the number of bytes to fetch on each
     *                  {@link #nextChunk()} call
     */
    public ChunkedNioStream(ReadableByteChannel in, int chunkSize) {
        if (in == null) {
            throw new NullPointerException("in");
        }
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize: " + chunkSize +
                    " (expected: a positive integer)");
        }
        this.in = in;
        offset = 0;
        this.chunkSize = chunkSize;
        if (byteBuffer != null) {
            if (byteBuffer.capacity() != chunkSize) {
                byteBuffer = null;
                byteBuffer = ByteBuffer.allocate(chunkSize);
            }
        } else {
            byteBuffer = ByteBuffer.allocate(chunkSize);
        }
    }

    /**
     * Returns the number of transferred bytes.
     */
    public long getTransferredBytes() {
        return offset;
    }

    public boolean hasNextChunk() throws Exception {
        if (byteBuffer.position() > 0) {
            // A previous read was not over, so there is a next chunk in the buffer at least
            return true;
        }
        if (in.isOpen()) {
            // Try to read a new part, and keep this part (no rewind)
            int b = in.read(byteBuffer);
            if (b < 0) {
                return false;
            } else {
                offset += b;
                return true;
            }
        }
        return false;
    }

    public void close() throws Exception {
        in.close();
    }

    public Object nextChunk() throws Exception {
        if (!hasNextChunk()) {
            return null;
        }
        // buffer cannot be not be empty from there
        int readBytes = byteBuffer.position();
        for (;;) {
            int localReadBytes = in.read(byteBuffer);
            if (localReadBytes < 0) {
                break;
            }
            readBytes += localReadBytes;
            offset += localReadBytes;

            if (readBytes == chunkSize) {
                break;
            }
        }
        byteBuffer.flip();
        // copy since buffer is keeped for next usage
        ChannelBuffer buffer = copiedBuffer(byteBuffer);
        byteBuffer.clear();
        return buffer;
    }
}
