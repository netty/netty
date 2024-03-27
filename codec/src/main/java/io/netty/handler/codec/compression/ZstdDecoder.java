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
package io.netty.handler.codec.compression;

import com.github.luben.zstd.BaseZstdBufferDecompressingStreamNoFinalizer;
import com.github.luben.zstd.ZstdBufferDecompressingStreamNoFinalizer;
import com.github.luben.zstd.ZstdDirectBufferDecompressingStreamNoFinalizer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * Decompresses a compressed block {@link ByteBuf} using the Zstandard algorithm.
 * See <a href="https://facebook.github.io/zstd">Zstandard</a>.
 */
public final class ZstdDecoder extends ByteToMessageDecoder {
    // Don't use static here as we want to still allow to load the classes.
    {
        try {
            Zstd.ensureAvailability();
            outCapacity = ZstdBufferDecompressingStreamNoFinalizer.recommendedTargetBufferSize();
        } catch (Throwable throwable) {
            throw new ExceptionInInitializerError(throwable);
        }
    }
    private final int outCapacity;

    private State currentState = State.DECOMPRESS_DATA;
    private ZstdStream stream;

    /**
     * Current state of stream.
     */
    private enum State {
        DECOMPRESS_DATA,
        CORRUPTED
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        try {
            if (currentState == State.CORRUPTED) {
                in.skipBytes(in.readableBytes());
                return;
            }
            final int compressedLength = in.readableBytes();
            if (compressedLength == 0) {
                // Nothing to decompress, try again later.
                return;
            }
            if (stream == null) {
                // We assume that if the first buffer is direct the next buffer will also most likely be direct.
                stream = new ZstdStream(in.isDirect(), outCapacity);
            }

            do  {
                ByteBuf decompressed = stream.decompress(ctx.alloc(), in);
                if (decompressed == null) {
                    return;
                }
                out.add(decompressed);
            } while (in.isReadable());
        } catch (DecompressionException e) {
            currentState = State.CORRUPTED;
            throw e;
        }
    }

    @Override
    protected void handlerRemoved0(ChannelHandlerContext ctx) throws Exception {
        try {
            if (stream != null) {
                stream.close();
                stream = null;
            }
        } finally {
            super.handlerRemoved0(ctx);
        }
    }

    private static final class ZstdStream {
        private static final ByteBuffer EMPTY_HEAP_BUFFER = ByteBuffer.allocate(0);
        private static final ByteBuffer EMPTY_DIRECT_BUFFER = ByteBuffer.allocateDirect(0);

        private final boolean direct;
        private final int outCapacity;
        private final BaseZstdBufferDecompressingStreamNoFinalizer decompressingStream;
        private ByteBuffer current;

        ZstdStream(boolean direct, int outCapacity) {
            this.direct = direct;
            this.outCapacity = outCapacity;
            if (direct) {
                decompressingStream = new ZstdDirectBufferDecompressingStreamNoFinalizer(EMPTY_DIRECT_BUFFER) {
                    @Override
                    protected ByteBuffer refill(ByteBuffer toRefill) {
                        return ZstdStream.this.refill(toRefill);
                    }
                };
            } else {
                decompressingStream = new ZstdBufferDecompressingStreamNoFinalizer(EMPTY_HEAP_BUFFER) {
                    @Override
                    protected ByteBuffer refill(ByteBuffer toRefill) {
                        return ZstdStream.this.refill(toRefill);
                    }
                };
            }
        }

        ByteBuf decompress(ByteBufAllocator alloc, ByteBuf in) throws DecompressionException {
            final ByteBuf source;
            // Ensure we use the correct input buffer type.
            if (direct && !in.isDirect()) {
                source = alloc.directBuffer(in.readableBytes());
                source.writeBytes(in, in.readerIndex(), in.readableBytes());
            } else if (!direct && !in.hasArray()) {
                source = alloc.heapBuffer(in.readableBytes());
                source.writeBytes(in, in.readerIndex(), in.readableBytes());
            } else {
                source = in;
            }
            int inPosition = -1;
            ByteBuf outBuffer = null;
            try {
                ByteBuffer inNioBuffer = CompressionUtil.safeNioBuffer(
                        source, source.readerIndex(), source.readableBytes());
                inPosition = inNioBuffer.position();
                assert inNioBuffer.hasRemaining();
                current = inNioBuffer;

                // allocate the outBuffer based on what we expect from the decompressingStream.
                if (direct) {
                    outBuffer = alloc.directBuffer(outCapacity);
                } else {
                    outBuffer = alloc.heapBuffer(outCapacity);
                }
                ByteBuffer target = outBuffer.internalNioBuffer(outBuffer.writerIndex(), outBuffer.writableBytes());
                int position = target.position();
                do {
                    do {
                        if (decompressingStream.read(target) == 0) {
                            break;
                        }
                    } while (decompressingStream.hasRemaining() && target.hasRemaining() && current.hasRemaining());
                    int written = target.position() - position;
                    if (written > 0) {
                        outBuffer.writerIndex(outBuffer.writerIndex() + written);
                        ByteBuf out = outBuffer;
                        outBuffer = null;
                        return out;
                    }
                } while (decompressingStream.hasRemaining() && current.hasRemaining());
            } catch (IOException e) {
                throw new DecompressionException(e);
            } finally {
                if (outBuffer != null) {
                    outBuffer.release();
                }
                // Release in case of copy
                if (source != in) {
                    source.release();
                }
                ByteBuffer buffer = current;
                current = null;
                if (inPosition != -1) {
                    int read = buffer.position() - inPosition;
                    if (read > 0) {
                        in.skipBytes(read);
                    }
                }
            }
            return null;
        }

        private ByteBuffer refill(@SuppressWarnings("unused") ByteBuffer toRefill) {
            return current;
        }

        void close() {
            decompressingStream.close();
        }
    }
}
