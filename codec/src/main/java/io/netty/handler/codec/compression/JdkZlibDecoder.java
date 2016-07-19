/*
 * Copyright 2013 The Netty Project
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

import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Decompress a {@link ByteBuf} using the inflate algorithm.
 */
public class JdkZlibDecoder extends ZlibDecoder {
    private static final int FHCRC = 0x02;
    private static final int FEXTRA = 0x04;
    private static final int FNAME = 0x08;
    private static final int FCOMMENT = 0x10;
    private static final int FRESERVED = 0xE0;

    private Inflater inflater;
    private final byte[] dictionary;

    // GZIP related
    private final ByteBufChecksum crc;

    private enum GzipState {
        HEADER_START,
        HEADER_END,
        FLG_READ,
        XLEN_READ,
        SKIP_FNAME,
        SKIP_COMMENT,
        PROCESS_FHCRC,
        FOOTER_START,
    }

    private GzipState gzipState = GzipState.HEADER_START;
    private int flags = -1;
    private int xlen = -1;

    private volatile boolean finished;

    private boolean decideZlibOrNone;

    /**
     * Creates a new instance with the default wrapper ({@link ZlibWrapper#ZLIB}).
     */
    public JdkZlibDecoder() {
        this(ZlibWrapper.ZLIB, null);
    }

    /**
     * Creates a new instance with the specified preset dictionary. The wrapper
     * is always {@link ZlibWrapper#ZLIB} because it is the only format that
     * supports the preset dictionary.
     */
    public JdkZlibDecoder(byte[] dictionary) {
        this(ZlibWrapper.ZLIB, dictionary);
    }

    /**
     * Creates a new instance with the specified wrapper.
     * Be aware that only {@link ZlibWrapper#GZIP}, {@link ZlibWrapper#ZLIB} and {@link ZlibWrapper#NONE} are
     * supported atm.
     */
    public JdkZlibDecoder(ZlibWrapper wrapper) {
        this(wrapper, null);
    }

    private JdkZlibDecoder(ZlibWrapper wrapper, byte[] dictionary) {
        if (wrapper == null) {
            throw new NullPointerException("wrapper");
        }
        switch (wrapper) {
            case GZIP:
                inflater = new Inflater(true);
                crc = ByteBufChecksum.wrapChecksum(new CRC32());
                break;
            case NONE:
                inflater = new Inflater(true);
                crc = null;
                break;
            case ZLIB:
                inflater = new Inflater();
                crc = null;
                break;
            case ZLIB_OR_NONE:
                // Postpone the decision until decode(...) is called.
                decideZlibOrNone = true;
                crc = null;
                break;
            default:
                throw new IllegalArgumentException("Only GZIP or ZLIB is supported, but you used " + wrapper);
        }
        this.dictionary = dictionary;
    }

    @Override
    public boolean isClosed() {
        return finished;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (finished) {
            // Skip data received after finished.
            in.skipBytes(in.readableBytes());
            return;
        }

        int readableBytes = in.readableBytes();
        if (readableBytes == 0) {
            return;
        }

        if (decideZlibOrNone) {
            // First two bytes are needed to decide if it's a ZLIB stream.
            if (readableBytes < 2) {
                return;
            }

            boolean nowrap = !looksLikeZlib(in.getShort(in.readerIndex()));
            inflater = new Inflater(nowrap);
            decideZlibOrNone = false;
        }

        if (crc != null) {
            switch (gzipState) {
                case FOOTER_START:
                    if (readGZIPFooter(in)) {
                        finished = true;
                    }
                    return;
                default:
                    if (gzipState != GzipState.HEADER_END) {
                        if (!readGZIPHeader(in)) {
                            return;
                        }
                    }
            }
            // Some bytes may have been consumed, and so we must re-set the number of readable bytes.
            readableBytes = in.readableBytes();
        }

        if (in.hasArray()) {
            inflater.setInput(in.array(), in.arrayOffset() + in.readerIndex(), readableBytes);
        } else {
            byte[] array = new byte[readableBytes];
            in.getBytes(in.readerIndex(), array);
            inflater.setInput(array);
        }

        int maxOutputLength = inflater.getRemaining() << 1;
        ByteBuf decompressed = ctx.alloc().heapBuffer(maxOutputLength);
        try {
            boolean readFooter = false;
            byte[] outArray = decompressed.array();
            while (!inflater.needsInput()) {
                int writerIndex = decompressed.writerIndex();
                int outIndex = decompressed.arrayOffset() + writerIndex;
                int length = decompressed.writableBytes();

                if (length == 0) {
                    // completely filled the buffer allocate a new one and start to fill it
                    out.add(decompressed);
                    decompressed = ctx.alloc().heapBuffer(maxOutputLength);
                    outArray = decompressed.array();
                    continue;
                }

                int outputLength = inflater.inflate(outArray, outIndex, length);
                if (outputLength > 0) {
                    decompressed.writerIndex(writerIndex + outputLength);
                    if (crc != null) {
                        crc.update(outArray, outIndex, outputLength);
                    }
                } else {
                    if (inflater.needsDictionary()) {
                        if (dictionary == null) {
                            throw new DecompressionException(
                                    "decompression failure, unable to set dictionary as non was specified");
                        }
                        inflater.setDictionary(dictionary);
                    }
                }

                if (inflater.finished()) {
                    if (crc == null) {
                        finished = true; // Do not decode anymore.
                    } else {
                        readFooter = true;
                    }
                    break;
                }
            }

            in.skipBytes(readableBytes - inflater.getRemaining());

            if (readFooter) {
                gzipState = GzipState.FOOTER_START;
                if (readGZIPFooter(in)) {
                    finished = true;
                }
            }
        } catch (DataFormatException e) {
            throw new DecompressionException("decompression failure", e);
        } finally {

            if (decompressed.isReadable()) {
                out.add(decompressed);
            } else {
                decompressed.release();
            }
        }
    }

    @Override
    protected void handlerRemoved0(ChannelHandlerContext ctx) throws Exception {
        super.handlerRemoved0(ctx);
        if (inflater != null) {
            inflater.end();
        }
    }

    private boolean readGZIPHeader(ByteBuf in) {
        switch (gzipState) {
            case HEADER_START:
                if (in.readableBytes() < 10) {
                    return false;
                }
                // read magic numbers
                int magic0 = in.readByte();
                int magic1 = in.readByte();

                if (magic0 != 31) {
                    throw new DecompressionException("Input is not in the GZIP format");
                }
                crc.update(magic0);
                crc.update(magic1);

                int method = in.readUnsignedByte();
                if (method != Deflater.DEFLATED) {
                    throw new DecompressionException("Unsupported compression method "
                            + method + " in the GZIP header");
                }
                crc.update(method);

                flags = in.readUnsignedByte();
                crc.update(flags);

                if ((flags & FRESERVED) != 0) {
                    throw new DecompressionException(
                            "Reserved flags are set in the GZIP header");
                }

                // mtime (int)
                crc.update(in, in.readerIndex(), 4);
                in.skipBytes(4);

                crc.update(in.readUnsignedByte()); // extra flags
                crc.update(in.readUnsignedByte()); // operating system

                gzipState = GzipState.FLG_READ;
            case FLG_READ:
                if ((flags & FEXTRA) != 0) {
                    if (in.readableBytes() < 2) {
                        return false;
                    }
                    int xlen1 = in.readUnsignedByte();
                    int xlen2 = in.readUnsignedByte();
                    crc.update(xlen1);
                    crc.update(xlen2);

                    xlen |= xlen1 << 8 | xlen2;
                }
                gzipState = GzipState.XLEN_READ;
            case XLEN_READ:
                if (xlen != -1) {
                    if (in.readableBytes() < xlen) {
                        return false;
                    }
                    crc.update(in, in.readerIndex(), xlen);
                    in.skipBytes(xlen);
                }
                gzipState = GzipState.SKIP_FNAME;
            case SKIP_FNAME:
                if ((flags & FNAME) != 0) {
                    if (!in.isReadable()) {
                        return false;
                    }
                    do {
                        int b = in.readUnsignedByte();
                        crc.update(b);
                        if (b == 0x00) {
                            break;
                        }
                    } while (in.isReadable());
                }
                gzipState = GzipState.SKIP_COMMENT;
            case SKIP_COMMENT:
                if ((flags & FCOMMENT) != 0) {
                    if (!in.isReadable()) {
                        return false;
                    }
                    do {
                        int b = in.readUnsignedByte();
                        crc.update(b);
                        if (b == 0x00) {
                            break;
                        }
                    } while (in.isReadable());
                }
                gzipState = GzipState.PROCESS_FHCRC;
            case PROCESS_FHCRC:
                if ((flags & FHCRC) != 0) {
                    if (in.readableBytes() < 4) {
                        return false;
                    }
                    verifyCrc(in);
                }
                crc.reset();
                gzipState = GzipState.HEADER_END;
            case HEADER_END:
                return true;
            default:
                throw new IllegalStateException();
        }
    }

    private boolean readGZIPFooter(ByteBuf buf) {
        if (buf.readableBytes() < 8) {
            return false;
        }

        verifyCrc(buf);

        // read ISIZE and verify
        int dataLength = 0;
        for (int i = 0; i < 4; ++i) {
            dataLength |= buf.readUnsignedByte() << i * 8;
        }
        int readLength = inflater.getTotalOut();
        if (dataLength != readLength) {
            throw new DecompressionException(
                    "Number of bytes mismatch. Expected: " + dataLength + ", Got: " + readLength);
        }
        return true;
    }

    private void verifyCrc(ByteBuf in) {
        long crcValue = 0;
        for (int i = 0; i < 4; ++i) {
            crcValue |= (long) in.readUnsignedByte() << i * 8;
        }
        long readCrc = crc.getValue();
        if (crcValue != readCrc) {
            throw new DecompressionException(
                    "CRC value missmatch. Expected: " + crcValue + ", Got: " + readCrc);
        }
    }

    /*
     * Returns true if the cmf_flg parameter (think: first two bytes of a zlib stream)
     * indicates that this is a zlib stream.
     * <p>
     * You can lookup the details in the ZLIB RFC:
     * <a href="http://tools.ietf.org/html/rfc1950#section-2.2">RFC 1950</a>.
     */
    private static boolean looksLikeZlib(short cmf_flg) {
        return (cmf_flg & 0x7800) == 0x7800 &&
                cmf_flg % 31 == 0;
    }
}
