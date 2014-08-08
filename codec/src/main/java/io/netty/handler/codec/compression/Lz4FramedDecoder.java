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
package io.netty.handler.codec.compression;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;
import net.jpountz.xxhash.XXHashFactory;

import java.util.List;
import java.util.zip.Checksum;

import static io.netty.handler.codec.compression.Lz4Constants.*;

/**
 * Uncompresses a {@link ByteBuf} encoded with the LZ4 format.
 *
 * See original <a href="http://code.google.com/p/lz4/">LZ4 website</a>
 * and <a href="http://fastcompression.blogspot.ru/2011/05/lz4-explained.html">LZ4 block format</a>
 * for full description.
 *
 * Since the original LZ4 block format does not contains size of compressed block and size of original data
 * this encoder uses format like <a href="https://github.com/idelpivnitskiy/lz4-java">LZ4 Java</a> library
 * written by Adrien Grand and approved by Yann Collet (author of original LZ4 library).
 *
 *  * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *     * * * * * * * * * *
 *  * Magic * Token *  Compressed *  Decompressed *  Checksum *  +  *  LZ4 compressed *
 *  *       *       *    length   *     length    *           *     *      block      *
 *  * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *     * * * * * * * * * *
 */
public class Lz4FramedDecoder extends ByteToMessageDecoder {
    /**
     * Underlying decompressor in use.
     */
    private LZ4FastDecompressor decompressor;

    /**
     * Underlying checksum calculator in use.
     */
    private Checksum checksum;

    /**
     * Indicates if the end of the compressed stream has been reached.
     */
    private boolean finished;

    /**
     * Creates the fastest LZ4 decoder.
     *
     * Note that by default, validation of the checksum header in each chunk is
     * DISABLED for performance improvements. If performance is less of an issue,
     * or if you would prefer the safety that checksum validation brings, please
     * use the {@link #Lz4FramedDecoder(boolean)} constructor with the argument
     * set to {@code true}.
     */
    public Lz4FramedDecoder() {
        this(false);
    }

    /**
     * Creates a LZ4 decoder with fastest decoder instance available on your machine.
     *
     * @param validateChecksums  if {@code true}, the checksum field will be validated against the actual
     *                           uncompressed data, and if the checksums do not match, a suitable
     *                           {@link DecompressionException} will be thrown
     */
    public Lz4FramedDecoder(boolean validateChecksums) {
        this(LZ4Factory.fastestInstance(), validateChecksums);
    }

    /**
     * Creates a new LZ4 decoder with customizable implementation.
     *
     * @param factory            user customizable {@link net.jpountz.lz4.LZ4Factory} instance
     *                           which may be JNI bindings to the original C implementation, a pure Java implementation
     *                           or a Java implementation that uses the {@link sun.misc.Unsafe}
     * @param validateChecksums  if {@code true}, the checksum field will be validated against the actual
     *                           uncompressed data, and if the checksums do not match, a suitable
     *                           {@link DecompressionException} will be thrown. In this case encoder will use
     *                           xxhash hashing for Java, based on Yann Collet's work available at
     *                           <a href="http://code.google.com/p/xxhash/">Google Code</a>.
     */
    public Lz4FramedDecoder(LZ4Factory factory, boolean validateChecksums) {
        this(factory, validateChecksums ?
                XXHashFactory.fastestInstance().newStreamingHash32(DEFAULT_SEED).asChecksum()
              : null);
    }

    /**
     * Creates a new customizable LZ4 decoder.
     *
     * @param factory   user customizable {@link net.jpountz.lz4.LZ4Factory} instance
     *                  which may be JNI bindings to the original C implementation, a pure Java implementation
     *                  or a Java implementation that uses the {@link sun.misc.Unsafe}
     * @param checksum  the {@link Checksum} instance to use to check data for integrity.
     *                  You may set {@code null} if you do not want to validate checksum of each block
     */
    public Lz4FramedDecoder(LZ4Factory factory, Checksum checksum) {
        if (factory == null) {
            throw new NullPointerException("factory");
        }
        decompressor = factory.fastDecompressor();
        this.checksum = checksum;
        finished = false;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        for (;;) {
            if (finished) {
                in.skipBytes(in.readableBytes());
                return;
            }

            if (in.readableBytes() < HEADER_LENGTH) {
                return;
            }
            final int idx = in.readerIndex();

            final long magic = in.getLong(idx);
            if (magic != MAGIC_NUMBER) {
                throw new DecompressionException("Unexpected block identifier");
            }

            final int compressedLength = Integer.reverseBytes(in.getInt(idx + COMPRESSED_LENGTH_OFFSET));
            if (compressedLength < 0) {
                throw new DecompressionException("Unexpected compressedLength: " + compressedLength
                        + " (not expected negative value)");
            }

            if (in.readableBytes() < HEADER_LENGTH + compressedLength) {
                return;
            }

            final int token = in.getByte(idx + TOKEN_OFFSET);
            final int blockType = token & 0xF0;
            final int compressionLevel = (token & 0x0F) + COMPRESSION_LEVEL_BASE;

            final int decompressedLength = Integer.reverseBytes(in.getInt(idx + DECOMPRESSED_LENGTH_OFFSET));
            final int maxDecompressedLength = 1 << compressionLevel;
            if (decompressedLength > maxDecompressedLength || decompressedLength < 0) {
                throw new DecompressionException("Unexpected decompressedLength: " + decompressedLength
                        + " (expected: " + 0 + '-' + maxDecompressedLength + ')');
            }
            if (decompressedLength == 0 && compressedLength != 0
                    || decompressedLength != 0 && compressedLength == 0
                    || blockType == BLOCK_TYPE_NON_COMPRESSED && decompressedLength != compressedLength) {
                throw new DecompressionException("Stream is corrupted: " +
                        "decompressedLength and compressedLength do not match");
            }

            final int check = Integer.reverseBytes(in.getInt(idx + CHECKSUM_OFFSET));
            if (decompressedLength == 0 && compressedLength == 0) {
                if (check != 0) {
                    throw new DecompressionException("Stream is corrupted: checksum error");
                }
                in.skipBytes(HEADER_LENGTH);
                finished = true;
                decompressor = null;
                checksum = null;
                break;
            }

            ByteBuf uncompressed = ctx.alloc().heapBuffer(decompressedLength, decompressedLength);
            final byte[] dest = uncompressed.array();
            final int destOff = uncompressed.arrayOffset() + uncompressed.writerIndex();

            boolean success = false;
            try {
                switch (blockType) {
                    case BLOCK_TYPE_NON_COMPRESSED: {
                        in.getBytes(idx + HEADER_LENGTH, dest, destOff, decompressedLength);
                        break;
                    }
                    case BLOCK_TYPE_COMPRESSED: {
                        final byte[] src;
                        final int srcOff;
                        if (in.hasArray()) {
                            src = in.array();
                            srcOff = in.arrayOffset() + idx + HEADER_LENGTH;
                        } else {
                            src = new byte[compressedLength];
                            in.getBytes(idx + HEADER_LENGTH, src, 0, compressedLength);
                            srcOff = 0;
                        }

                        final int readed = decompressor.decompress(src, srcOff, dest, destOff, decompressedLength);
                        if (compressedLength != readed) {
                            throw new DecompressionException("Stream is corrupted: compressedLength != readed");
                        }
                        break;
                    }
                    default:
                        throw new DecompressionException("Unexpected blockType: " + blockType
                                + " (expected: " + BLOCK_TYPE_NON_COMPRESSED + " or " + BLOCK_TYPE_COMPRESSED + ')');
                }

                final Checksum checksum = this.checksum;
                if (checksum != null) {
                    checksum.reset();
                    checksum.update(dest, destOff, decompressedLength);
                    if ((int) checksum.getValue() != check) {
                        throw new DecompressionException("Stream is corrupted: checksum error");
                    }
                }
                uncompressed.writerIndex(uncompressed.writerIndex() + decompressedLength);
                out.add(uncompressed);
                in.skipBytes(HEADER_LENGTH + compressedLength);
                success = true;
            } finally {
                if (!success) {
                    uncompressed.release();
                }
            }
        }
    }

    /**
     * Returns {@code true} if and only if the end of the compressed stream
     * has been reached.
     */
    public boolean isClosed() {
        return finished;
    }
}
