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
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import lzma.sdk.lzma.Base;
import lzma.sdk.lzma.Encoder;

import java.io.InputStream;

import static lzma.sdk.lzma.Encoder.*;

/**
 * Compresses a {@link ByteBuf} using the LZMA algorithm.
 *
 * See <a href="http://en.wikipedia.org/wiki/Lempel%E2%80%93Ziv%E2%80%93Markov_chain_algorithm">LZMA</a>
 * and <a href="http://svn.python.org/projects/external/xz-5.0.5/doc/lzma-file-format.txt">LZMA format</a>
 * or documents in <a href="http://www.7-zip.org/sdk.html">LZMA SDK</a> archive.
 */
public class LzmaFrameEncoder extends MessageToByteEncoder<ByteBuf> {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(LzmaFrameEncoder.class);

    private static final int MEDIUM_DICTIONARY_SIZE = 1 << 16;

    private static final int MIN_FAST_BYTES = 5;
    private static final int MEDIUM_FAST_BYTES = 0x20;
    private static final int MAX_FAST_BYTES = Base.kMatchMaxLen;

    private static final int DEFAULT_MATCH_FINDER = EMatchFinderTypeBT4;

    private static final int DEFAULT_LC = 3;
    private static final int DEFAULT_LP = 0;
    private static final int DEFAULT_PB = 2;

    /**
     * Underlying LZMA encoder in use.
     */
    private final Encoder encoder;

    /**
     * The Properties field contains three properties which are encoded using the following formula:
     *
     * <p>{@code Properties = (pb * 5 + lp) * 9 + lc}</p>
     *
     * The field consists of
     *  <ol>
     *      <li>the number of literal context bits (lc, [0, 8]);</li>
     *      <li>the number of literal position bits (lp, [0, 4]);</li>
     *      <li>the number of position bits (pb, [0, 4]).</li>
     *  </ol>
     */
    private final byte properties;

    /**
     * Dictionary Size is stored as an unsigned 32-bit little endian integer.
     */
    private final int littleEndianDictionarySize;

    /**
     * For log warning only once.
     */
    private static boolean warningLogged;

    /**
     * Creates LZMA encoder with default settings.
     */
    public LzmaFrameEncoder() {
        this(MEDIUM_DICTIONARY_SIZE);
    }

    /**
     * Creates LZMA encoder with specified {@code lc}, {@code lp}, {@code pb}
     * values and the medium dictionary size of {@value #MEDIUM_DICTIONARY_SIZE}.
     */
    public LzmaFrameEncoder(int lc, int lp, int pb) {
        this(lc, lp, pb, MEDIUM_DICTIONARY_SIZE);
    }

    /**
     * Creates LZMA encoder with specified dictionary size and default values of
     * {@code lc} = {@value #DEFAULT_LC},
     * {@code lp} = {@value #DEFAULT_LP},
     * {@code pb} = {@value #DEFAULT_PB}.
     */
    public LzmaFrameEncoder(int dictionarySize) {
        this(DEFAULT_LC, DEFAULT_LP, DEFAULT_PB, dictionarySize);
    }

    /**
     * Creates LZMA encoder with specified {@code lc}, {@code lp}, {@code pb} values and custom dictionary size.
     */
    public LzmaFrameEncoder(int lc, int lp, int pb, int dictionarySize) {
        this(lc, lp, pb, dictionarySize, false, MEDIUM_FAST_BYTES);
    }

    /**
     * Creates LZMA encoder with specified settings.
     *
     * @param lc
     *        the number of "literal context" bits, available values [0, 8], default value {@value #DEFAULT_LC}.
     * @param lp
     *        the number of "literal position" bits, available values [0, 4], default value {@value #DEFAULT_LP}.
     * @param pb
     *        the number of "position" bits, available values [0, 4], default value {@value #DEFAULT_PB}.
     * @param dictionarySize
     *        available values [0, {@link java.lang.Integer#MAX_VALUE}],
     *        default value is {@value #MEDIUM_DICTIONARY_SIZE}.
     * @param endMarkerMode
     *        indicates should {@link LzmaFrameEncoder} use end of stream marker or not.
     *        Note, that {@link LzmaFrameEncoder} always sets size of uncompressed data
     *        in LZMA header, so EOS marker is unnecessary. But you may use it for
     *        better portability. For full description see "LZMA Decoding modes" section
     *        of LZMA-Specification.txt in official LZMA SDK.
     * @param numFastBytes
     *        available values [{@value #MIN_FAST_BYTES}, {@value #MAX_FAST_BYTES}].
     */
    public LzmaFrameEncoder(int lc, int lp, int pb, int dictionarySize, boolean endMarkerMode, int numFastBytes) {
        if (lc < 0 || lc > 8) {
            throw new IllegalArgumentException("lc: " + lc + " (expected: 0-8)");
        }
        if (lp < 0 || lp > 4) {
            throw new IllegalArgumentException("lp: " + lp + " (expected: 0-4)");
        }
        if (pb < 0 || pb > 4) {
            throw new IllegalArgumentException("pb: " + pb + " (expected: 0-4)");
        }
        if (lc + lp > 4) {
            if (!warningLogged) {
                logger.warn("The latest versions of LZMA libraries (for example, XZ Utils) " +
                        "has an additional requirement: lc + lp <= 4. Data which don't follow " +
                        "this requirement cannot be decompressed with this libraries.");
                warningLogged = true;
            }
        }
        if (dictionarySize < 0) {
            throw new IllegalArgumentException("dictionarySize: " + dictionarySize + " (expected: 0+)");
        }
        if (numFastBytes < MIN_FAST_BYTES || numFastBytes > MAX_FAST_BYTES) {
            throw new IllegalArgumentException(String.format(
                    "numFastBytes: %d (expected: %d-%d)", numFastBytes, MIN_FAST_BYTES, MAX_FAST_BYTES
            ));
        }

        encoder = new Encoder();
        encoder.setDictionarySize(dictionarySize);
        encoder.setEndMarkerMode(endMarkerMode);
        encoder.setMatchFinder(DEFAULT_MATCH_FINDER);
        encoder.setNumFastBytes(numFastBytes);
        encoder.setLcLpPb(lc, lp, pb);

        properties = (byte) ((pb * 5 + lp) * 9 + lc);
        littleEndianDictionarySize = Integer.reverseBytes(dictionarySize);
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf in, ByteBuf out) throws Exception {
        final int length = in.readableBytes();
        InputStream bbIn = null;
        ByteBufOutputStream bbOut = null;
        try {
            bbIn = new ByteBufInputStream(in);
            bbOut = new ByteBufOutputStream(out);
            bbOut.writeByte(properties);
            bbOut.writeInt(littleEndianDictionarySize);
            bbOut.writeLong(Long.reverseBytes(length));
            encoder.code(bbIn, bbOut, -1, -1, null);
        } finally {
            if (bbIn != null) {
                bbIn.close();
            }
            if (bbOut != null) {
                bbOut.close();
            }
        }
    }

    @Override
    protected ByteBuf allocateBuffer(ChannelHandlerContext ctx, ByteBuf in, boolean preferDirect) throws Exception {
        final int length = in.readableBytes();
        final int maxOutputLength = maxOutputBufferLength(length);
        return ctx.alloc().ioBuffer(maxOutputLength);
    }

    /**
     * Calculates maximum possible size of output buffer for not compressible data.
     */
    private static int maxOutputBufferLength(int inputLength) {
        double factor;
        if (inputLength < 200) {
            factor = 1.5;
        } else if (inputLength < 500) {
            factor = 1.2;
        } else if (inputLength < 1000) {
            factor = 1.1;
        } else if (inputLength < 10000) {
            factor = 1.05;
        } else {
            factor = 1.02;
        }
        return 13 + (int) (inputLength * factor);
    }
}
