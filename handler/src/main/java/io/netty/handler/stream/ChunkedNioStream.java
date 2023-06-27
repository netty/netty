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
package io.netty.handler.stream;

import static io.netty.util.internal.ObjectUtil.checkPositive;
import static io.netty.util.internal.ObjectUtil.checkNotNull;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;

import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

/**
 * A {@link ChunkedInput} that fetches data from a {@link ReadableByteChannel}
 * chunk by chunk.  Please note that the {@link ReadableByteChannel} must
 * operate in blocking mode.  Non-blocking mode channels are not supported.
 */
public class ChunkedNioStream implements ChunkedInput<ByteBuf> {

    private final ReadableByteChannel in;

    private final int chunkSize;
    private long offset;

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
     *                  {@link #readChunk(ChannelHandlerContext)} call
     */
    public ChunkedNioStream(ReadableByteChannel in, int chunkSize) {
        this.in = checkNotNull(in, "in");
        this.chunkSize = checkPositive(chunkSize, "chunkSize");
        byteBuffer = ByteBuffer.allocate(chunkSize);
    }

    /**
     * Returns the number of transferred bytes.
     */
    public long transferredBytes() {
        return offset;
    }

    @Override
    public boolean isEndOfInput() throws Exception {
        if (byteBuffer.position() > 0) {
            // A previous read was not over, so there is a next chunk in the buffer at least
            return false;
        }
        if (in.isOpen()) {
            // Try to read a new part, and keep this part (no rewind)
            int b = in.read(byteBuffer);
            if (b < 0) {
                return true;
            } else {
                offset += b;
                return false;
            }
        }
        return true;
    }

    @Override
    public void close() throws Exception {
        in.close();
    }

    @Deprecated
    @Override
    public ByteBuf readChunk(ChannelHandlerContext ctx) throws Exception {
        return readChunk(ctx.alloc());
    }

    @Override
    public ByteBuf readChunk(ByteBufAllocator allocator) throws Exception {
        if (isEndOfInput()) {
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
        boolean release = true;
        ByteBuf buffer = allocator.buffer(byteBuffer.remaining());
        try {
            buffer.writeBytes(byteBuffer);
            byteBuffer.clear();
            release = false;
            return buffer;
        } finally {
            if (release) {
                buffer.release();
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
