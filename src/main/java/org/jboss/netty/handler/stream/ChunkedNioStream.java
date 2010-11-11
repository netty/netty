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

import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

import org.jboss.netty.buffer.ChannelBuffer;

/**
 * A {@link ChunkedInput} that fetches data from a {@link ReadableByteChannel}
 * chunk by chunk.  Please note that the {@link ReadableByteChannel} must
 * operate in blocking mode.  Non-blocking mode channels are not supported.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @author Frederic Bregier
 * @version $Rev: 2236 $, $Date: 2010-04-12 19:22:51 +0900 (Mon, 12 Apr 2010) $
 */
public class ChunkedNioStream implements ChunkedInput {

    private final ReadableByteChannel in;

    private final int chunkSize;
    private volatile long offset;

    /**
     * Associated ByteBuffer
     */
    private final ByteBuffer byteBuffer;

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
        byteBuffer = ByteBuffer.allocate(chunkSize);
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

    public boolean isEndOfInput() throws Exception {
        return !hasNextChunk();
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
