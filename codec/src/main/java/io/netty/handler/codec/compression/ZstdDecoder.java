/*
 * Copyright 2023 The Netty Project
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
import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdInputStreamNoFinalizer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.util.internal.ObjectUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.List;
import static io.netty.handler.codec.compression.ZstdConstants.DEFAULT_MAX_BLOCK_SIZE;

/**
 * Decompresses a compressed block {@link ByteBuf} using the Zstandard algorithm.
 * See <a href="https://facebook.github.io/zstd">Zstandard</a>.
 */
public final class ZstdDecoder extends ByteToMessageDecoder {

    private static final long RECOMMENDED_OUT_SIZE =  ZstdInputStream.recommendedDOutSize();
    private static final int COPY_BUF_SIZE = 8024;
    private final int maxBlockSize;
    private InputStream is;
    private ZstdInputStreamNoFinalizer zstdIs;
    private ByteArrayOutputStream bos;
    private ByteBuf buffer;
    private volatile State currentState = State.DECOMPRESS_DATA;

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
        NEED_MORE_DATA,
        FINISHED,
        CORRUPTED
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        try {
            while (in.isReadable()) {
                switch (currentState) {
                    case DECOMPRESS_DATA:
                        decompressData(ctx, in, out);
                        break;
                    case NEED_MORE_DATA:
                        buffer.writeBytes(in);
                        decompressData(ctx, buffer, out);
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

    private boolean consumeAndDecompress(ChannelHandlerContext ctx, int decompressedSize,
                                         ByteBuf in, List<Object> out) throws IOException {
        int readerIndex = in.readerIndex();
        // Bytebuf cannot release here because ByteToMessageDecoder will release it
        is = new ByteBufInputStream(in, false);
        zstdIs = new ZstdInputStreamNoFinalizer(is);
        // setContinuous to true so that ZstdInputStreamNoFinalizer.read() will
        // return -1 when decompression is not completed
        zstdIs.setContinuous(true);
        bos = new ByteArrayOutputStream();
        copyStream(zstdIs, bos);
        final byte[] decompressed = bos.toByteArray();
        final int decompressedLength = decompressed.length;
        // Check the decompression status since we use ZstdInputStream as an indicator
        // to determine whether the decompression is completed
        // TODO: Use Zstd to check the compressed data when zstd library support this
        if (decompressedLength == 0 || (decompressedSize > 0 && decompressedSize != decompressedLength)) {
            in.readerIndex(readerIndex);
            if (currentState == State.DECOMPRESS_DATA) {
                if (buffer == null) {
                    if (RECOMMENDED_OUT_SIZE < Integer.MIN_VALUE || RECOMMENDED_OUT_SIZE > Integer.MAX_VALUE) {
                        throw new IllegalStateException("Invalid recommendedOutSize");
                    }
                    buffer = ctx.alloc().buffer((int) RECOMMENDED_OUT_SIZE);
                }
                buffer.writeBytes(in);
                currentState = State.NEED_MORE_DATA;
            }
            return false;
        }
        final ByteBuf uncompressed = Unpooled.wrappedBuffer(decompressed);
        out.add(uncompressed);
        return true;
    }

    private void copyStream(final InputStream input, final OutputStream output) throws IOException {
        final byte[] copyBuffer = new byte[COPY_BUF_SIZE];
        int n = 0;
        while ((n = input.read(copyBuffer)) != -1) {
            output.write(copyBuffer, 0, n);
        }
    }

    private void decompressData(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws IOException {
        final int compressedLength = in.readableBytes();
        if (compressedLength > maxBlockSize) {
            in.skipBytes(compressedLength);
            throw new TooLongFrameException("too large message: " + compressedLength + " bytes");
        }
        try {
            final ByteBuffer src =  CompressionUtil.safeNioBuffer(in, in.readerIndex(), compressedLength);
            long decompressedSize = Zstd.decompressedSize(src);
            if (decompressedSize < Integer.MIN_VALUE || decompressedSize > Integer.MAX_VALUE) {
                throw new CorruptedFrameException("Invalid decompressedSize");
            }
            boolean completed = consumeAndDecompress(ctx, (int) decompressedSize, in, out);

            if (!completed) {
                return;
            }

            if (buffer != null) {
                buffer.release();
                buffer = null;
            }
            currentState = State.FINISHED;
        } catch (Exception e) {
            throw new DecompressionException(e);
        } finally {
            closeAllStreams();
        }
    }

    private void closeAllStreams() throws IOException {
        if (zstdIs != null) {
            zstdIs.close();
        } else {
            is.close();
        }

        if (bos != null) {
            bos.close();
        }
    }

    @Override
    protected void handlerRemoved0(ChannelHandlerContext ctx) throws Exception {
        try {
            closeAllStreams();
        } finally {
            super.handlerRemoved0(ctx);
        }
    }

    /**
     * Returns {@code true} if and only if the end of the compressed stream
     * has been reached.
     */
    public boolean isClosed() {
        return currentState == State.FINISHED;
    }

}
