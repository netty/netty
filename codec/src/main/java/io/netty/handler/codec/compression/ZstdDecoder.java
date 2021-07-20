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
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.nio.ByteBuffer;
import java.util.List;

import static io.netty.handler.codec.compression.ZstdConstants.BUFFER_SIZE;

/**
 * Decompresses a {@link ByteBuf} using the Zstandard algorithm.
 * See <a href="https://facebook.github.io/zstd">Zstandard</a>.
 */
public final class ZstdDecoder extends ByteToMessageDecoder {

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
                        try {
                            ByteBuffer src =  CompressionUtil.safeNioBuffer(in, in.readerIndex(), compressedLength);
                            int bufferSize = Math.max(compressedLength, BUFFER_SIZE);
                            uncompressed = ctx.alloc().directBuffer(bufferSize);
                            ByteBuffer dst = uncompressed.internalNioBuffer(0, bufferSize);
                            int srcSize = Zstd.decompress(dst, src);
                            // Update the writerIndex now to reflect what we decompressed.
                            uncompressed.writerIndex(uncompressed.writerIndex() + srcSize);

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
