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

import java.io.InputStream;
import java.io.PushbackInputStream;

/**
 * A {@link ChunkedInput} that fetches data from an {@link InputStream} chunk by
 * chunk.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev: 2236 $, $Date: 2010-04-12 19:22:51 +0900 (Mon, 12 Apr 2010) $
 */
public class ChunkedStream implements ChunkedInput {

    static final int DEFAULT_CHUNK_SIZE = 8192;

    private final PushbackInputStream in;
    private final int chunkSize;
    private volatile long offset;

    /**
     * Creates a new instance that fetches data from the specified stream.
     */
    public ChunkedStream(InputStream in) {
        this(in, DEFAULT_CHUNK_SIZE);
    }

    /**
     * Creates a new instance that fetches data from the specified stream.
     *
     * @param chunkSize the number of bytes to fetch on each
     *                  {@link #nextChunk()} call
     */
    public ChunkedStream(InputStream in, int chunkSize) {
        if (in == null) {
            throw new NullPointerException("in");
        }
        if (chunkSize <= 0) {
            throw new IllegalArgumentException(
                    "chunkSize: " + chunkSize +
                    " (expected: a positive integer)");
        }

        if (in instanceof PushbackInputStream) {
            this.in = (PushbackInputStream) in;
        } else {
            this.in = new PushbackInputStream(in);
        }
        this.chunkSize = chunkSize;
    }

    /**
     * Returns the number of transferred bytes.
     */
    public long getTransferredBytes() {
        return offset;
    }

    public boolean hasNextChunk() throws Exception {
        int b = in.read();
        if (b < 0) {
            return false;
        } else {
            in.unread(b);
            return true;
        }
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

        final int availableBytes = in.available();
        final int chunkSize;
        if (availableBytes <= 0) {
            chunkSize = this.chunkSize;
        } else {
            chunkSize = Math.min(this.chunkSize, in.available());
        }
        final byte[] chunk = new byte[chunkSize];
        int readBytes = 0;
        for (;;) {
            int localReadBytes = in.read(chunk, readBytes, chunkSize - readBytes);
            if (localReadBytes < 0) {
                break;
            }
            readBytes += localReadBytes;
            offset += localReadBytes;

            if (readBytes == chunkSize) {
                break;
            }
        }

        return wrappedBuffer(chunk, 0, readBytes);
    }
}
