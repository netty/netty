/*
 * Copyright 2021 The Netty Project
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

import com.github.luben.zstd.Zstd;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.PlatformDependent;
import java.nio.ByteBuffer;
import java.util.List;

import static io.netty.handler.codec.compression.ZstdConstants.DEFAULT_MAX_BLOCK_SIZE;
import static io.netty.handler.codec.compression.ZstdConstants.MAX_DECOMPRESS_SIZE;

/**
 * Decompresses a {@link ByteBuf} using the Zstandard algorithm.
 * See <a href="https://facebook.github.io/zstd">Zstandard</a>.
 */
public final class ZstdDecoder extends ByteToMessageDecoder {

     private final int maxBlockSize;

    /**
     * Creates a new Zstd decoder.
     *
     * Please note that if you use the default constructor, the MAX_BLOCK_SIZE
     * will be used. If you want to specify MAX_BLOCK_SIZE yourself,
     * please use {@link ZstdDecoder(int)} constructor
     */
     public ZstdDecoder() {
         this(DEFAULT_MAX_BLOCK_SIZE);
     }

    /**
     * Creates a new Zstd decoder.
     *  @param  maxBlockSize
     *            specifies the max block size
     */
     public ZstdDecoder(int maxBlockSize) {
         this.maxBlockSize = ObjectUtil.checkPositive(maxBlockSize, "maxBlockSize");
     }

    /**
     * Current state of stream.
     */
    private enum State {
        DECOMPRESS_DATA,
        FINISHED,
        CORRUPTED
    }

    private volatile State currentState = State.DECOMPRESS_DATA;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        try {
            while (in.isReadable()) {
                switch (currentState) {
                    case DECOMPRESS_DATA:
                        ByteBuf uncompressed = null;
                        int compressedLength = in.readableBytes();
                        if (compressedLength > maxBlockSize) {
                            in.skipBytes(compressedLength);
                            throw new TooLongFrameException("too large message: " + compressedLength + " bytes");
                        }
                        try {
                            ByteBuffer src =  CompressionUtil.safeNioBuffer(in, in.readerIndex(), compressedLength);
                            int bufferSize = compressedLength << 2;
                            if (in.isDirect()) {
                                int decompressedSize = (int) Zstd.decompressedSize(src);
                                // ZstdOutputStream compressed data using Zstd.decompressedSize() will return 0,
                                // so compressedLength can only be used to determine the size of the allocated memory
                                if (decompressedSize > 0) {
                                    bufferSize = decompressedSize < MAX_DECOMPRESS_SIZE ?
                                            decompressedSize : decompressedSize << 6;
                                }
                                uncompressed = ctx.alloc().directBuffer(bufferSize);
                                ByteBuffer dst = CompressionUtil.safeNioBuffer(uncompressed, 0,
                                        bufferSize);
                                int srcSize = Zstd.decompress(dst, src);
                                // Update the writerIndex now to reflect what we decompressed.
                                uncompressed.writerIndex(uncompressed.writerIndex() + srcSize);
                            } else {
                                byte[] srcBytes = ByteBufUtil.getBytes(in);
                                int decompressedSize = (int) Zstd.decompressedSize(srcBytes);
                                if (decompressedSize > 0) {
                                    bufferSize = decompressedSize;
                                }
                                byte[] dstBytes = PlatformDependent.allocateUninitializedArray(bufferSize);
                                int srcSize = (int) Zstd.decompress(dstBytes, srcBytes);
                                uncompressed = Unpooled.wrappedBuffer(dstBytes);
                                uncompressed.writerIndex(srcSize);
                            }

                            // Skip inbound bytes after we processed them.
                            in.skipBytes(compressedLength);

                            out.add(uncompressed);
                            uncompressed = null;
                            currentState = State.FINISHED;
                        } catch (Exception e) {
                            throw new DecompressionException(e);
                        } finally {
                            if (uncompressed != null) {
                                uncompressed.release();
                            }
                        }
                        break;
                    case FINISHED:
                    case CORRUPTED:
                        in.skipBytes(in.readableBytes());
                        break;
                    default:
                        throw new IllegalStateException();
                }
            }
        } catch (Exception e) {
            currentState = State.CORRUPTED;
            throw e;
        }
    }

    public boolean isClosed() {
        return currentState == State.FINISHED;
    }

}
