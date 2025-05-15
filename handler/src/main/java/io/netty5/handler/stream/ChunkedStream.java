/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty5.handler.stream;

import io.netty5.buffer.Buffer;
import io.netty5.buffer.BufferAllocator;

import java.io.InputStream;
import java.io.PushbackInputStream;

import static java.util.Objects.requireNonNull;

/**
 * A {@link ChunkedInput} that fetches data from an {@link InputStream} chunk by
 * chunk.
 * <p>
 * Please note that the {@link InputStream} instance that feeds data into
 * {@link ChunkedStream} must implement {@link InputStream#available()} as
 * accurately as possible, rather than using the default implementation.
 * Otherwise, {@link ChunkedStream} will generate many too small chunks or
 * block unnecessarily often.
 */
public class ChunkedStream implements ChunkedInput<Buffer> {
    static final int DEFAULT_CHUNK_SIZE = 8192;

    private final PushbackInputStream in;
    private final int chunkSize;
    private long offset;
    private boolean closed;
    private byte[] cachedArray;

    /**
     * Creates a new instance that fetches data from the specified stream.
     */
    public ChunkedStream(InputStream in) {
        this(in, DEFAULT_CHUNK_SIZE);
    }

    /**
     * Creates a new instance that fetches data from the specified stream.
     *
     * @param chunkSize the number of bytes to fetch on each {@link #readChunk(BufferAllocator)} call.
     */
    public ChunkedStream(InputStream in, int chunkSize) {
        requireNonNull(in, "in");
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
    public long transferredBytes() {
        return offset;
    }

    @Override
    public boolean isEndOfInput() throws Exception {
        if (closed) {
            return true;
        }
        if (in.available() > 0) {
            return false;
        }

        int b = in.read();
        if (b < 0) {
            return true;
        } else {
            in.unread(b);
            return false;
        }
    }

    @Override
    public void close() throws Exception {
        closed = true;
        in.close();
    }

    @Override
    public Buffer readChunk(BufferAllocator allocator) throws Exception {
        if (isEndOfInput()) {
            return null;
        }

        final int availableBytes = in.available();
        final int chunkSize;
        if (availableBytes <= 0) {
            chunkSize = this.chunkSize;
        } else {
            chunkSize = Math.min(this.chunkSize, in.available());
        }

        boolean release = true;
        Buffer buffer = allocator.allocate(chunkSize);
        try {
            // transfer to buffer
            int written;
            try (var iter = buffer.forEachComponent()) {
                var component = iter.firstWritable();
                if (component.hasWritableArray()) {
                    written = in.read(component.writableArray(),
                            component.writableArrayOffset(),
                            component.writableArrayLength());
                    if (written > 0) {
                        component.skipWritableBytes(written);
                    }
                } else {
                    int size = Math.min(component.writableBytes(), chunkSize);
                    if (cachedArray == null || cachedArray.length < size) {
                        cachedArray = new byte[size];
                    }
                    written = in.read(cachedArray, 0, size);
                    if (written > 0) {
                        buffer.writeBytes(cachedArray, 0, written);
                    }
                }
            }
            if (written < 0) {
                return null;
            }
            offset += written;
            release = false;
            return buffer;
        } finally {
            if (release) {
                buffer.close();
            }
        }
    }

    @Override
    public long length() {
        return -1;
    }

    @Override
    public long progress() {
        return offset;
    }
}
