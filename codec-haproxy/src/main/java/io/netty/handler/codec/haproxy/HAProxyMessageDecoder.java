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
package io.netty.handler.codec.haproxy;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.ProtocolDetectionResult;
import io.netty.util.CharsetUtil;

import java.util.List;

import static io.netty.handler.codec.haproxy.HAProxyConstants.*;

/**
 * Decodes an HAProxy proxy protocol header
 *
 * @see <a href="http://haproxy.1wt.eu/download/1.5/doc/proxy-protocol.txt">Proxy Protocol Specification</a>
 */
public class HAProxyMessageDecoder extends ByteToMessageDecoder {
    /**
     * Maximum possible length of a v1 proxy protocol header per spec
     */
    private static final int V1_MAX_LENGTH = 108;

    /**
     * Maximum possible length of a v2 proxy protocol header (fixed 16 bytes + max unsigned short)
     */
    private static final int V2_MAX_LENGTH = 16 + 65535;

    /**
     * Minimum possible length of a fully functioning v2 proxy protocol header (fixed 16 bytes + v2 address info space)
     */
    private static final int V2_MIN_LENGTH = 16 + 216;

    /**
     * Maximum possible length for v2 additional TLV data (max unsigned short - max v2 address info space)
     */
    private static final int V2_MAX_TLV = 65535 - 216;

    /**
     * Binary header prefix length
     */
    private static final int BINARY_PREFIX_LENGTH = BINARY_PREFIX.length;

    /**
     * {@link ProtocolDetectionResult} for {@link HAProxyProtocolVersion#V1}.
     */
    private static final ProtocolDetectionResult<HAProxyProtocolVersion> DETECTION_RESULT_V1 =
            ProtocolDetectionResult.detected(HAProxyProtocolVersion.V1);

    /**
     * {@link ProtocolDetectionResult} for {@link HAProxyProtocolVersion#V2}.
     */
    private static final ProtocolDetectionResult<HAProxyProtocolVersion> DETECTION_RESULT_V2 =
            ProtocolDetectionResult.detected(HAProxyProtocolVersion.V2);

    /**
     * Used to extract a header frame out of the {@link ByteBuf} and return it.
     */
    private HeaderExtractor headerExtractor;

    /**
     * {@code true} if we're discarding input because we're already over maxLength
     */
    private boolean discarding;

    /**
     * Number of discarded bytes
     */
    private int discardedBytes;

    /**
     * Whether or not to throw an exception as soon as we exceed maxLength.
     */
    private final boolean failFast;

    /**
     * {@code true} if we're finished decoding the proxy protocol header
     */
    private boolean finished;

    /**
     * Protocol specification version
     */
    private int version = -1;

    /**
     * The latest v2 spec (2014/05/18) allows for additional data to be sent in the proxy protocol header beyond the
     * address information block so now we need a configurable max header size
     */
    private final int v2MaxHeaderSize;

    /**
     * Creates a new decoder with no additional data (TLV) restrictions, and should throw an exception as soon as
     * we exceed maxLength.
     */
    public HAProxyMessageDecoder() {
        this(true);
    }

    /**
     * Creates a new decoder with no additional data (TLV) restrictions, whether or not to throw an exception as soon
     * as we exceed maxLength.
     *
     * @param failFast Whether or not to throw an exception as soon as we exceed maxLength
     */
    public HAProxyMessageDecoder(boolean failFast) {
        v2MaxHeaderSize = V2_MAX_LENGTH;
        this.failFast = failFast;
    }

    /**
     * Creates a new decoder with restricted additional data (TLV) size, and should throw an exception as soon as
     * we exceed maxLength.
     * <p>
     * <b>Note:</b> limiting TLV size only affects processing of v2, binary headers. Also, as allowed by the 1.5 spec
     * TLV data is currently ignored. For maximum performance it would be best to configure your upstream proxy host to
     * <b>NOT</b> send TLV data and instantiate with a max TLV size of {@code 0}.
     * </p>
     *
     * @param maxTlvSize maximum number of bytes allowed for additional data (Type-Length-Value vectors) in a v2 header
     */
    public HAProxyMessageDecoder(int maxTlvSize) {
        this(maxTlvSize, true);
    }

    /**
     * Creates a new decoder with restricted additional data (TLV) size, whether or not to throw an exception as soon
     * as we exceed maxLength.
     *
     * @param maxTlvSize maximum number of bytes allowed for additional data (Type-Length-Value vectors) in a v2 header
     * @param failFast Whether or not to throw an exception as soon as we exceed maxLength
     */
    public HAProxyMessageDecoder(int maxTlvSize, boolean failFast) {
        if (maxTlvSize < 1) {
            v2MaxHeaderSize = V2_MIN_LENGTH;
        } else if (maxTlvSize > V2_MAX_TLV) {
            v2MaxHeaderSize = V2_MAX_LENGTH;
        } else {
            int calcMax = maxTlvSize + V2_MIN_LENGTH;
            if (calcMax > V2_MAX_LENGTH) {
                v2MaxHeaderSize = V2_MAX_LENGTH;
            } else {
                v2MaxHeaderSize = calcMax;
            }
        }
        this.failFast = failFast;
    }

    /**
     * Returns the proxy protocol specification version in the buffer if the version is found.
     * Returns -1 if no version was found in the buffer.
     */
    private static int findVersion(final ByteBuf buffer) {
        final int n = buffer.readableBytes();
        // per spec, the version number is found in the 13th byte
        if (n < 13) {
            return -1;
        }

        int idx = buffer.readerIndex();
        return match(BINARY_PREFIX, buffer, idx) ? buffer.getByte(idx + BINARY_PREFIX_LENGTH) : 1;
    }

    /**
     * Returns the index in the buffer of the end of header if found.
     * Returns -1 if no end of header was found in the buffer.
     */
    private static int findEndOfHeader(final ByteBuf buffer) {
        final int n = buffer.readableBytes();

        // per spec, the 15th and 16th bytes contain the address length in bytes
        if (n < 16) {
            return -1;
        }

        int offset = buffer.readerIndex() + 14;

        // the total header length will be a fixed 16 byte sequence + the dynamic address information block
        int totalHeaderBytes = 16 + buffer.getUnsignedShort(offset);

        // ensure we actually have the full header available
        if (n >= totalHeaderBytes) {
            return totalHeaderBytes;
        } else {
            return -1;
        }
    }

    /**
     * Returns the index in the buffer of the end of line found.
     * Returns -1 if no end of line was found in the buffer.
     */
    private static int findEndOfLine(final ByteBuf buffer) {
        final int n = buffer.writerIndex();
        for (int i = buffer.readerIndex(); i < n; i++) {
            final byte b = buffer.getByte(i);
            if (b == '\r' && i < n - 1 && buffer.getByte(i + 1) == '\n') {
                return i;  // \r\n
            }
        }
        return -1;  // Not found.
    }

    @Override
    public boolean isSingleDecode() {
        // ByteToMessageDecoder uses this method to optionally break out of the decoding loop after each unit of work.
        // Since we only ever want to decode a single header we always return true to save a bit of work here.
        return true;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        super.channelRead(ctx, msg);
        if (finished) {
            ctx.pipeline().remove(this);
        }
    }

    @Override
    protected final void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // determine the specification version
        if (version == -1) {
            if ((version = findVersion(in)) == -1) {
                return;
            }
        }

        ByteBuf decoded;

        if (version == 1) {
            decoded = decodeLine(ctx, in);
        } else {
            decoded = decodeStruct(ctx, in);
        }

        if (decoded != null) {
            finished = true;
            try {
                if (version == 1) {
                    out.add(HAProxyMessage.decodeHeader(decoded.toString(CharsetUtil.US_ASCII)));
                } else {
                    out.add(HAProxyMessage.decodeHeader(decoded));
                }
            } catch (HAProxyProtocolException e) {
                fail(ctx, null, e);
            }
        }
    }

    /**
     * Create a frame out of the {@link ByteBuf} and return it.
     *
     * @param ctx     the {@link ChannelHandlerContext} which this {@link HAProxyMessageDecoder} belongs to
     * @param buffer  the {@link ByteBuf} from which to read data
     * @return frame  the {@link ByteBuf} which represent the frame or {@code null} if no frame could
     *                be created
     */
    private ByteBuf decodeStruct(ChannelHandlerContext ctx, ByteBuf buffer) throws Exception {
        if (headerExtractor == null) {
            headerExtractor = new StructHeaderExtractor(v2MaxHeaderSize);
        }
        return headerExtractor.extract(ctx, buffer);
    }

    /**
     * Create a frame out of the {@link ByteBuf} and return it.
     *
     * @param ctx     the {@link ChannelHandlerContext} which this {@link HAProxyMessageDecoder} belongs to
     * @param buffer  the {@link ByteBuf} from which to read data
     * @return frame  the {@link ByteBuf} which represent the frame or {@code null} if no frame could
     *                be created
     */
    private ByteBuf decodeLine(ChannelHandlerContext ctx, ByteBuf buffer) throws Exception {
        if (headerExtractor == null) {
            headerExtractor = new LineHeaderExtractor(V1_MAX_LENGTH);
        }
        return headerExtractor.extract(ctx, buffer);
    }

    private void failOverLimit(final ChannelHandlerContext ctx, int length) {
        failOverLimit(ctx, String.valueOf(length));
    }

    private void failOverLimit(final ChannelHandlerContext ctx, String length) {
        int maxLength = version == 1 ? V1_MAX_LENGTH : v2MaxHeaderSize;
        fail(ctx, "header length (" + length + ") exceeds the allowed maximum (" + maxLength + ')', null);
    }

    private void fail(final ChannelHandlerContext ctx, String errMsg, Exception e) {
        finished = true;
        ctx.close(); // drop connection immediately per spec
        HAProxyProtocolException ppex;
        if (errMsg != null && e != null) {
            ppex = new HAProxyProtocolException(errMsg, e);
        } else if (errMsg != null) {
            ppex = new HAProxyProtocolException(errMsg);
        } else if (e != null) {
            ppex = new HAProxyProtocolException(e);
        } else {
            ppex = new HAProxyProtocolException();
        }
        throw ppex;
    }

    /**
     * Returns the {@link ProtocolDetectionResult} for the given {@link ByteBuf}.
     */
    public static ProtocolDetectionResult<HAProxyProtocolVersion> detectProtocol(ByteBuf buffer) {
        if (buffer.readableBytes() < 12) {
            return ProtocolDetectionResult.needsMoreData();
        }

        int idx = buffer.readerIndex();

        if (match(BINARY_PREFIX, buffer, idx)) {
            return DETECTION_RESULT_V2;
        }
        if (match(TEXT_PREFIX, buffer, idx)) {
            return DETECTION_RESULT_V1;
        }
        return ProtocolDetectionResult.invalid();
    }

    private static boolean match(byte[] prefix, ByteBuf buffer, int idx) {
        for (int i = 0; i < prefix.length; i++) {
            final byte b = buffer.getByte(idx + i);
            if (b != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * HeaderExtractor create a header frame out of the {@link ByteBuf}.
     */
    private abstract class HeaderExtractor {
        /** Header max size */
        private final int maxHeaderSize;

        protected HeaderExtractor(int maxHeaderSize) {
            this.maxHeaderSize = maxHeaderSize;
        }

        /**
         * Create a frame out of the {@link ByteBuf} and return it.
         *
         * @param ctx     the {@link ChannelHandlerContext} which this {@link HAProxyMessageDecoder} belongs to
         * @param buffer  the {@link ByteBuf} from which to read data
         * @return frame  the {@link ByteBuf} which represent the frame or {@code null} if no frame could
         *                be created
         * @throws Exception if exceed maxLength
         */
        public ByteBuf extract(ChannelHandlerContext ctx, ByteBuf buffer) throws Exception {
            final int eoh = findEndOfHeader(buffer);
            if (!discarding) {
                if (eoh >= 0) {
                    final int length = eoh - buffer.readerIndex();
                    if (length > maxHeaderSize) {
                        buffer.readerIndex(eoh + delimiterLength(buffer, eoh));
                        failOverLimit(ctx, length);
                        return null;
                    }
                    ByteBuf frame = buffer.readSlice(length);
                    buffer.skipBytes(delimiterLength(buffer, eoh));
                    return frame;
                } else {
                    final int length = buffer.readableBytes();
                    if (length > maxHeaderSize) {
                        discardedBytes = length;
                        buffer.skipBytes(length);
                        discarding = true;
                        if (failFast) {
                            failOverLimit(ctx, "over " + discardedBytes);
                        }
                    }
                    return null;
                }
            } else {
                if (eoh >= 0) {
                    final int length = discardedBytes + eoh - buffer.readerIndex();
                    buffer.readerIndex(eoh + delimiterLength(buffer, eoh));
                    discardedBytes = 0;
                    discarding = false;
                    if (!failFast) {
                        failOverLimit(ctx, "over " + length);
                    }
                } else {
                    discardedBytes += buffer.readableBytes();
                    buffer.skipBytes(buffer.readableBytes());
                }
                return null;
            }
        }

        /**
         * Find the end of the header from the given {@link ByteBuf}，the end may be a CRLF, or the length given by the
         * header.
         *
         * @param buffer the buffer to be searched
         * @return {@code -1} if can not find the end, otherwise return the buffer index of end
         */
        protected abstract int findEndOfHeader(ByteBuf buffer);

        /**
         * Get the length of the header delimiter.
         *
         * @param buffer the buffer where delimiter is located
         * @param eoh index of delimiter
         * @return length of the delimiter
         */
        protected abstract int delimiterLength(ByteBuf buffer, int eoh);
    }

    private final class LineHeaderExtractor extends HeaderExtractor {

        LineHeaderExtractor(int maxHeaderSize) {
            super(maxHeaderSize);
        }

        @Override
        protected int findEndOfHeader(ByteBuf buffer) {
            return findEndOfLine(buffer);
        }

        @Override
        protected int delimiterLength(ByteBuf buffer, int eoh) {
            return buffer.getByte(eoh) == '\r' ? 2 : 1;
        }
    }

    private final class StructHeaderExtractor extends HeaderExtractor {

        StructHeaderExtractor(int maxHeaderSize) {
            super(maxHeaderSize);
        }

        @Override
        protected int findEndOfHeader(ByteBuf buffer) {
            return HAProxyMessageDecoder.findEndOfHeader(buffer);
        }

        @Override
        protected int delimiterLength(ByteBuf buffer, int eoh) {
            return 0;
        }
    }
}
