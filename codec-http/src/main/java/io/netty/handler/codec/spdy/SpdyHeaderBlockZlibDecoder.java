/*
 * Copyright 2014 The Netty Project
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
package io.netty.handler.codec.spdy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import static io.netty.handler.codec.spdy.SpdyCodecUtil.*;

final class SpdyHeaderBlockZlibDecoder extends SpdyHeaderBlockRawDecoder {

    private static final int DEFAULT_BUFFER_CAPACITY = 4096;
    private static final SpdyProtocolException INVALID_HEADER_BLOCK =
            new SpdyProtocolException("Invalid Header Block");

    // Legitimate header blocks are bounded by maxHeaderSize, but framing overhead (the header
    // count and the per-header name/value length prefixes) means the decompressed size of a
    // wire-legitimate header block can exceed maxHeaderSize itself. A generous multiplier is used
    // instead of an equal cap so this never rejects well-formed header blocks, while still
    // preventing a maliciously compressible header block from forcing an effectively unbounded
    // number of Inflater#inflate() calls on the calling (event-loop) thread.
    private static final int MAX_DECOMPRESSED_SIZE_MULTIPLIER = 16;

    private final Inflater decompressor = new Inflater();
    private final long maxDecompressedSize;

    private ByteBuf decompressed;
    private long totalInflated;

    SpdyHeaderBlockZlibDecoder(SpdyVersion spdyVersion, int maxHeaderSize) {
        super(spdyVersion, maxHeaderSize);
        maxDecompressedSize = calculateMaxDecompressedSizePerBlock(maxHeaderSize);
    }

    static long calculateMaxDecompressedSizePerBlock(int maxHeaderSize) {
        return (long) maxHeaderSize * MAX_DECOMPRESSED_SIZE_MULTIPLIER;
    }

    @Override
    void decode(ByteBufAllocator alloc, ByteBuf headerBlock, SpdyHeadersFrame frame) throws Exception {
        int len = setInput(headerBlock);

        int numBytes;
        do {
            numBytes = decompress(alloc, frame);
            totalInflated += numBytes;
            if (totalInflated > maxDecompressedSize) {
                throw new SpdyProtocolException(
                        "Decompressed header block exceeds " + maxDecompressedSize + " bytes");
            }
        } while (numBytes > 0);

        // z_stream has an internal 64-bit hold buffer
        // it is always capable of consuming the entire input
        if (decompressor.getRemaining() != 0) {
            // we reached the end of the deflate stream
            throw INVALID_HEADER_BLOCK;
        }

        headerBlock.skipBytes(len);
    }

    private int setInput(ByteBuf compressed) {
        int len = compressed.readableBytes();

        if (compressed.hasArray()) {
            decompressor.setInput(compressed.array(), compressed.arrayOffset() + compressed.readerIndex(), len);
        } else {
            byte[] in = new byte[len];
            compressed.getBytes(compressed.readerIndex(), in);
            decompressor.setInput(in, 0, in.length);
        }

        return len;
    }

    private int decompress(ByteBufAllocator alloc, SpdyHeadersFrame frame) throws Exception {
        ensureBuffer(alloc);
        byte[] out = decompressed.array();
        int off = decompressed.arrayOffset() + decompressed.writerIndex();
        try {
            int numBytes = decompressor.inflate(out, off, decompressed.writableBytes());
            if (numBytes == 0 && decompressor.needsDictionary()) {
                try {
                    decompressor.setDictionary(SPDY_DICT);
                } catch (IllegalArgumentException ignored) {
                    throw INVALID_HEADER_BLOCK;
                }
                numBytes = decompressor.inflate(out, off, decompressed.writableBytes());
            }
            if (frame != null) {
                decompressed.writerIndex(decompressed.writerIndex() + numBytes);
                decodeHeaderBlock(decompressed, frame);
                decompressed.discardReadBytes();
            }

            return numBytes;
        } catch (DataFormatException e) {
            throw new SpdyProtocolException("Received invalid header block", e);
        }
    }

    private void ensureBuffer(ByteBufAllocator alloc) {
        if (decompressed == null) {
            decompressed = alloc.heapBuffer(DEFAULT_BUFFER_CAPACITY);
        }
        decompressed.ensureWritable(1);
    }

    @Override
    void endHeaderBlock(SpdyHeadersFrame frame) throws Exception {
        super.endHeaderBlock(frame);
        releaseBuffer();
        totalInflated = 0;
    }

    @Override
    public void end() {
        super.end();
        releaseBuffer();
        decompressor.end();
    }

    private void releaseBuffer() {
        if (decompressed != null) {
            decompressed.release();
            decompressed = null;
        }
    }
}
