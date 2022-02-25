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
package io.netty5.handler.codec.compression;

import io.netty5.buffer.ByteBuf;
import io.netty5.buffer.ByteBufAllocator;
import io.netty5.buffer.Unpooled;

import java.util.function.Supplier;

import static io.netty5.handler.codec.compression.Snappy.*;

/**
 * Compresses a {@link ByteBuf} using the Snappy framing format.
 *
 * See <a href="https://github.com/google/snappy/blob/master/framing_format.txt">Snappy framing format</a>.
 */
public final class SnappyCompressor implements Compressor {
    private enum State {
        Init,
        Started,
        Finished,
        Closed
    }

    /**
     * The minimum amount that we'll consider actually attempting to compress.
     * This value is preamble + the minimum length our Snappy service will
     * compress (instead of just emitting a literal).
     */
    private static final int MIN_COMPRESSIBLE_LENGTH = 18;

    /**
     * All streams should start with the "Stream identifier", containing chunk
     * type 0xff, a length field of 0x6, and 'sNaPpY' in ASCII.
     */
    private static final byte[] STREAM_START = {
        (byte) 0xff, 0x06, 0x00, 0x00, 0x73, 0x4e, 0x61, 0x50, 0x70, 0x59
    };

    private final Snappy snappy = new Snappy();
    private State state = State.Init;

    private SnappyCompressor() { }

    /**
     * Creates a new snappy compressor factory.
     *
     * @return  the new instance.
     */
    public static Supplier<SnappyCompressor> newFactory() {
        return SnappyCompressor::new;
    }

    @Override
    public ByteBuf compress(ByteBuf in, ByteBufAllocator allocator) throws CompressionException {
        switch (state) {
            case Finished:
                return Unpooled.EMPTY_BUFFER;
            case Closed:
                throw new CompressionException("Compressor closed");
            default:
                // TODO: Make some smart decision about the initial capacity.
                ByteBuf out = allocator.buffer();
                try {
                    if (state == State.Init) {
                        state = State.Started;
                        out.writeBytes(STREAM_START);
                    } else if (state != State.Started) {
                        throw new IllegalStateException();
                    }

                    int dataLength = in.readableBytes();
                    if (dataLength > MIN_COMPRESSIBLE_LENGTH) {
                        for (;;) {
                            final int lengthIdx = out.writerIndex() + 1;
                            if (dataLength < MIN_COMPRESSIBLE_LENGTH) {
                                ByteBuf slice = in.readSlice(dataLength);
                                writeUnencodedChunk(slice, out, dataLength);
                                break;
                            }

                            out.writeInt(0);
                            if (dataLength > Short.MAX_VALUE) {
                                ByteBuf slice = in.readSlice(Short.MAX_VALUE);
                                calculateAndWriteChecksum(slice, out);
                                snappy.encode(slice, out, Short.MAX_VALUE);
                                setChunkLength(out, lengthIdx);
                                dataLength -= Short.MAX_VALUE;
                            } else {
                                ByteBuf slice = in.readSlice(dataLength);
                                calculateAndWriteChecksum(slice, out);
                                snappy.encode(slice, out, dataLength);
                                setChunkLength(out, lengthIdx);
                                break;
                            }
                        }
                    } else {
                        writeUnencodedChunk(in, out, dataLength);
                    }
                    return out;
                } catch (Throwable cause) {
                    out.release();
                    throw cause;
                }
        }
    }

    @Override
    public ByteBuf finish(ByteBufAllocator allocator) {
        switch (state) {
            case Closed:
                throw new CompressionException("Compressor closed");
            case Finished:
            case Init:
            case Started:
                state = State.Finished;
                return Unpooled.EMPTY_BUFFER;
            default:
                throw new IllegalStateException();
        }
    }

    @Override
    public boolean isFinished() {
        switch (state) {
            case Finished:
            case Closed:
                return true;
            default:
                return false;
        }
    }

    @Override
    public boolean isClosed() {
        return state == State.Closed;
    }

    @Override
    public void close() {
        state = State.Closed;
    }

    private static void writeUnencodedChunk(ByteBuf in, ByteBuf out, int dataLength) {
        out.writeByte(1);
        writeChunkLength(out, dataLength + 4);
        calculateAndWriteChecksum(in, out);
        out.writeBytes(in, dataLength);
    }

    private static void setChunkLength(ByteBuf out, int lengthIdx) {
        int chunkLength = out.writerIndex() - lengthIdx - 3;
        if (chunkLength >>> 24 != 0) {
            throw new CompressionException("compressed data too large: " + chunkLength);
        }
        out.setMediumLE(lengthIdx, chunkLength);
    }

    /**
     * Writes the 2-byte chunk length to the output buffer.
     *
     * @param out The buffer to write to
     * @param chunkLength The length to write
     */
    private static void writeChunkLength(ByteBuf out, int chunkLength) {
        out.writeMediumLE(chunkLength);
    }

    /**
     * Calculates and writes the 4-byte checksum to the output buffer
     *
     * @param slice The data to calculate the checksum for
     * @param out The output buffer to write the checksum to
     */
    private static void calculateAndWriteChecksum(ByteBuf slice, ByteBuf out) {
        out.writeIntLE(calculateChecksum(slice));
    }
}
