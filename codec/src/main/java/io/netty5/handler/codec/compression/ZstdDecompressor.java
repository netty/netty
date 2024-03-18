/*
 * Copyright 2024 The Netty Project
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

import com.github.luben.zstd.ZstdInputStreamNoFinalizer;
import io.netty5.buffer.Buffer;
import io.netty5.buffer.BufferAllocator;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.function.Supplier;

public class ZstdDecompressor implements Decompressor {
    {
        try {
            io.netty5.handler.codec.compression.Zstd.ensureAvailability();
        } catch (Throwable throwable) {
            throw new ExceptionInInitializerError(throwable);
        }
    }

    /**
     * Creates a new Zstd decompressor factory.
     *
     * @return the factory.
     */
    public static Supplier<ZstdDecompressor> newFactory() {
        return ZstdDecompressor::new;
    }

    private final MutableBufferInputStream inputStream = new MutableBufferInputStream();
    private ReadableByteChannel byteChannel;
    private State currentState = State.DECOMPRESS_DATA;

    /**
     * Current state of stream.
     */
    private enum State {
        DECOMPRESS_DATA,
        CORRUPTED,
        CLOSED
    }

    @Override
    public Buffer decompress(Buffer in, BufferAllocator allocator) throws DecompressionException {
        switch (currentState) {
            case CLOSED:
                throw new DecompressionException("Decompressor closed");
            case CORRUPTED:
                in.skipReadableBytes(in.readableBytes());
                return null;
            case DECOMPRESS_DATA:
                try {
                    if (byteChannel == null) {
                        ZstdInputStreamNoFinalizer zstdIs = new ZstdInputStreamNoFinalizer(inputStream);
                        zstdIs.setContinuous(true);
                        byteChannel = Channels.newChannel(zstdIs);
                    }

                    final int compressedLength = in.readableBytes();
                    if (compressedLength == 0) {
                        return null;
                    }

                    inputStream.current = in;

                    Buffer outBuffer = null;
                    try {
                        // Let's start with the compressedLength * 2 as often we will not have everything
                        // we need in the in buffer and don't want to reserve too much memory.
                        outBuffer = allocator.allocate(compressedLength * 2);
                        for (;;) {
                            int w = outBuffer.transferFrom(byteChannel, outBuffer.writableBytes());
                            if (w == -1) {
                                if (outBuffer.readableBytes() > 0) {
                                    Buffer out = outBuffer;
                                    outBuffer = null;
                                    return out;
                                } else {
                                    break;
                                }
                            } else if (outBuffer.writableBytes() == 0) {
                                // make some more room by growing the buffer.
                                outBuffer.ensureWritable(outBuffer.capacity() + compressedLength);
                            }
                        }
                    } finally {
                        if (outBuffer != null) {
                            outBuffer.close();
                        }
                    }
                    return null;
                } catch (Exception e) {
                    currentState = State.CORRUPTED;
                    throw new DecompressionException(e);
                } finally {
                    inputStream.current = null;
                }
            default:
                throw new IllegalStateException();
        }
    }

    @Override
    public boolean isFinished() {
        return currentState == State.CLOSED;
    }

    @Override
    public boolean isClosed() {
        return currentState == State.CLOSED;
    }

    @Override
    public void close() {
        currentState = State.CLOSED;
        closeSilently(byteChannel);
    }

    private static void closeSilently(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException ignore) {
                // ignore
            }
        }
    }

    private static final class MutableBufferInputStream extends InputStream {
        Buffer current;

        @Override
        public int read() {
            if (current == null || current.readableBytes() == 0) {
                return -1;
            }
            return current.readByte() & 0xff;
        }

        @Override
        public int read(byte[] b, int off, int len) {
            int available = available();
            if (available == 0) {
                return -1;
            }

            len = Math.min(available, len);
            current.readBytes(b, off, len);
            return len;
        }

        @Override
        public int available() {
            return current == null ? 0 : current.readableBytes();
        }
    }
}
