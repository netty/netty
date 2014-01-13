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

import java.io.InputStream;
import java.io.PushbackInputStream;

/**
 * A {@link ChunkedInput} that fetches data from an {@link InputStream} chunk by chunk for use with HTTP chunked
 * transfers.
 * <p>
 * Each chunk read from the file will be wrapped within a {@link DefaultHttpContent}. At the end of the stream,
 * {@link DefaultLastHttpContent} will be sent.
 * <p>
 * Ensure that your HTTP response header contains {@code Transfer-Encoding: chunked}.
 * <p>
 * Please note that the {@link InputStream} instance that feeds data into {@link ChunkedStream} must implement
 * {@link InputStream#available()} as accurately as possible, rather than using the default implementation. Otherwise,
 * {@link HttpChunkedStream} will generate many too small chunks or block unnecessarily often.
 */
public class HttpChunkedStream implements ChunkedInput<HttpContent> {

    static final int DEFAULT_CHUNK_SIZE = 8192;

    private final PushbackInputStream in;
    private final int chunkSize;
    private long offset;
    private volatile boolean sentLastChunk;

    /**
     * Creates a new instance that fetches data from the specified stream.
     */
    public HttpChunkedStream(InputStream in) {
        this(in, DEFAULT_CHUNK_SIZE);
    }

    /**
     * Creates a new instance that fetches data from the specified stream.
     * @param chunkSize
     *            the number of bytes to fetch on each {@link #readChunk(ChannelHandlerContext)} call
     */
    public HttpChunkedStream(InputStream in, int chunkSize) {
        if (in == null) {
            throw new NullPointerException("in");
        }
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize: " + chunkSize + " (expected: a positive integer)");
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

    private boolean isEndOfInputRawData() throws Exception {
        int b = in.read();
        if (b < 0) {
            return true;
        } else {
            in.unread(b);
            return false;
        }
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

        final int availableBytes = in.available();
        final int chunkSize;
        if (availableBytes <= 0) {
            chunkSize = this.chunkSize;
        } else {
            chunkSize = Math.min(this.chunkSize, in.available());
        }

        boolean release = true;
        ByteBuf buffer = ctx.alloc().buffer(chunkSize);
        try {
            // transfer to buffer
            offset += buffer.writeBytes(in, chunkSize);
            release = false;
            return new DefaultHttpContent(buffer);
        } finally {
            if (release) {
                buffer.release();
            }
        }
    }
}
