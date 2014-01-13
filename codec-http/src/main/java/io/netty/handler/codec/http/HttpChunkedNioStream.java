/*
 * Copyright 2014 The Netty Project
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
package io.netty.handler.codec.http;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.stream.ChunkedInput;

import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

/**
 * A {@link ChunkedInput} that fetches data from a {@link ReadableByteChannel} for use with HTTP chunked transfers.
 * Please note that the {@link ReadableByteChannel} must operate in blocking mode. Non-blocking mode channels are not
 * supported.
 * <p>
 * Each chunk read from the file will be wrapped within a {@link DefaultHttpContent}. At the end of the file,
 * {@link DefaultLastHttpContent} will be sent.
 * <p>
 * Ensure that your HTTP response header contains {@code Transfer-Encoding: chunked}.
 */
public class HttpChunkedNioStream implements ChunkedInput<HttpContent> {

    private final ReadableByteChannel in;

    private final int chunkSize;
    private long offset;
    private volatile boolean sentLastChunk;

    /**
     * Associated ByteBuffer
     */
    private final ByteBuffer byteBuffer;

    /**
     * Creates a new instance that fetches data from the specified channel.
     */
    public HttpChunkedNioStream(ReadableByteChannel in) {
        this(in, HttpChunkedStream.DEFAULT_CHUNK_SIZE);
    }

    /**
     * Creates a new instance that fetches data from the specified channel.
     * @param chunkSize
     *            the number of bytes to fetch on each {@link #readChunk(ChannelHandlerContext)} call
     */
    public HttpChunkedNioStream(ReadableByteChannel in, int chunkSize) {
        if (in == null) {
            throw new NullPointerException("in");
        }
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize: " + chunkSize + " (expected: a positive integer)");
        }
        this.in = in;
        offset = 0;
        this.chunkSize = chunkSize;
        byteBuffer = ByteBuffer.allocate(chunkSize);
    }

    /**
     * Returns the number of transferred bytes.
     */
    public long transferredBytes() {
        return offset;
    }

    private boolean isEndOfInputRawData() throws Exception {
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
    public boolean isEndOfInput() throws Exception {
        if (isEndOfInputRawData()) {
            return sentLastChunk;
        } else {
            return false;
        }
    }

    @Override
    public void close() throws Exception {
        in.close();
    }

    @Override
    public HttpContent readChunk(ChannelHandlerContext ctx) throws Exception {
        if (isEndOfInputRawData()) {
            if (sentLastChunk) {
                return null;
            } else {
                // Send last chunk for this file
                sentLastChunk = true;
                return new DefaultLastHttpContent();
            }
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
        ByteBuf buffer = ctx.alloc().buffer(byteBuffer.remaining());
        try {
            buffer.writeBytes(byteBuffer);
            byteBuffer.clear();
            release = false;
            return new DefaultHttpContent(buffer);
        } finally {
            if (release) {
                buffer.release();
            }
        }
    }
}
