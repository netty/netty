/*
 * Copyright 2015 The Netty Project
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

/*
 * Copyright 2014 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.netty.handler.codec.http2;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http2.HpackUtil.IndexType;
import io.netty.util.AsciiString;

import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_HEADER_TABLE_SIZE;
import static io.netty.handler.codec.http2.Http2CodecUtil.MAX_HEADER_LIST_SIZE;
import static io.netty.handler.codec.http2.Http2CodecUtil.MAX_HEADER_TABLE_SIZE;
import static io.netty.handler.codec.http2.Http2CodecUtil.MIN_HEADER_LIST_SIZE;
import static io.netty.handler.codec.http2.Http2CodecUtil.MIN_HEADER_TABLE_SIZE;
import static io.netty.handler.codec.http2.Http2CodecUtil.headerListSizeExceeded;
import static io.netty.handler.codec.http2.Http2Error.COMPRESSION_ERROR;
import static io.netty.handler.codec.http2.Http2Error.INTERNAL_ERROR;
import static io.netty.handler.codec.http2.Http2Error.PROTOCOL_ERROR;
import static io.netty.handler.codec.http2.Http2Exception.connectionError;
import static io.netty.handler.codec.http2.Http2Headers.PseudoHeaderName.getPseudoHeader;
import static io.netty.handler.codec.http2.Http2Headers.PseudoHeaderName.hasPseudoHeaderFormat;
import static io.netty.util.AsciiString.EMPTY_STRING;
import static io.netty.util.internal.ObjectUtil.checkPositive;
import static io.netty.util.internal.ThrowableUtil.unknownStackTrace;

final class HpackDecoder {
    private static final Http2Exception DECODE_ULE_128_DECOMPRESSION_EXCEPTION = unknownStackTrace(
            connectionError(COMPRESSION_ERROR, "HPACK - decompression failure"), HpackDecoder.class,
            "decodeULE128(..)");
    private static final Http2Exception DECODE_ULE_128_TO_LONG_DECOMPRESSION_EXCEPTION = unknownStackTrace(
            connectionError(COMPRESSION_ERROR, "HPACK - long overflow"), HpackDecoder.class, "decodeULE128(..)");
    private static final Http2Exception DECODE_ULE_128_TO_INT_DECOMPRESSION_EXCEPTION = unknownStackTrace(
            connectionError(COMPRESSION_ERROR, "HPACK - int overflow"), HpackDecoder.class, "decodeULE128ToInt(..)");
    private static final Http2Exception DECODE_ILLEGAL_INDEX_VALUE = unknownStackTrace(
            connectionError(COMPRESSION_ERROR, "HPACK - illegal index value"), HpackDecoder.class, "decode(..)");
    private static final Http2Exception INDEX_HEADER_ILLEGAL_INDEX_VALUE = unknownStackTrace(
            connectionError(COMPRESSION_ERROR, "HPACK - illegal index value"), HpackDecoder.class, "indexHeader(..)");
    private static final Http2Exception READ_NAME_ILLEGAL_INDEX_VALUE = unknownStackTrace(
            connectionError(COMPRESSION_ERROR, "HPACK - illegal index value"), HpackDecoder.class, "readName(..)");
    private static final Http2Exception INVALID_MAX_DYNAMIC_TABLE_SIZE = unknownStackTrace(
            connectionError(COMPRESSION_ERROR, "HPACK - invalid max dynamic table size"), HpackDecoder.class,
            "setDynamicTableSize(..)");
    private static final Http2Exception MAX_DYNAMIC_TABLE_SIZE_CHANGE_REQUIRED = unknownStackTrace(
            connectionError(COMPRESSION_ERROR, "HPACK - max dynamic table size change required"), HpackDecoder.class,
            "decode(..)");
    private static final byte READ_HEADER_REPRESENTATION = 0;
    private static final byte READ_MAX_DYNAMIC_TABLE_SIZE = 1;
    private static final byte READ_INDEXED_HEADER = 2;
    private static final byte READ_INDEXED_HEADER_NAME = 3;
    private static final byte READ_LITERAL_HEADER_NAME_LENGTH_PREFIX = 4;
    private static final byte READ_LITERAL_HEADER_NAME_LENGTH = 5;
    private static final byte READ_LITERAL_HEADER_NAME = 6;
    private static final byte READ_LITERAL_HEADER_VALUE_LENGTH_PREFIX = 7;
    private static final byte READ_LITERAL_HEADER_VALUE_LENGTH = 8;
    private static final byte READ_LITERAL_HEADER_VALUE = 9;

    private final HpackDynamicTable hpackDynamicTable;
    private final HpackHuffmanDecoder hpackHuffmanDecoder;
    private long maxHeaderListSizeGoAway;
    private long maxHeaderListSize;
    private long maxDynamicTableSize;
    private long encoderMaxDynamicTableSize;
    private boolean maxDynamicTableSizeChangeRequired;

    /**
     * Create a new instance.
     * @param maxHeaderListSize This is the only setting that can be configured before notifying the peer.
     *  This is because <a href="https://tools.ietf.org/html/rfc7540#section-6.5.1">SETTINGS_MAX_HEADER_LIST_SIZE</a>
     *  allows a lower than advertised limit from being enforced, and the default limit is unlimited
     *  (which is dangerous).
     * @param initialHuffmanDecodeCapacity Size of an intermediate buffer used during huffman decode.
     */
    HpackDecoder(long maxHeaderListSize, int initialHuffmanDecodeCapacity) {
        this(maxHeaderListSize, initialHuffmanDecodeCapacity, DEFAULT_HEADER_TABLE_SIZE);
    }

    /**
     * Exposed Used for testing only! Default values used in the initial settings frame are overridden intentionally
     * for testing but violate the RFC if used outside the scope of testing.
     */
    HpackDecoder(long maxHeaderListSize, int initialHuffmanDecodeCapacity, int maxHeaderTableSize) {
        this.maxHeaderListSize = checkPositive(maxHeaderListSize, "maxHeaderListSize");
        this.maxHeaderListSizeGoAway = Http2CodecUtil.calculateMaxHeaderListSizeGoAway(maxHeaderListSize);

        maxDynamicTableSize = encoderMaxDynamicTableSize = maxHeaderTableSize;
        maxDynamicTableSizeChangeRequired = false;
        hpackDynamicTable = new HpackDynamicTable(maxHeaderTableSize);
        hpackHuffmanDecoder = new HpackHuffmanDecoder(initialHuffmanDecodeCapacity);
    }

    /**
     * Decode the header block into header fields.
     * <p>
     * This method assumes the entire header block is contained in {@code in}.
     */
    public void decode(int streamId, ByteBuf in, Http2Headers headers, boolean validateHeaders) throws Http2Exception {
        int index = 0;
        long headersLength = 0;
        int nameLength = 0;
        int valueLength = 0;
        byte state = READ_HEADER_REPRESENTATION;
        boolean huffmanEncoded = false;
        CharSequence name = null;
        HeaderType headerType = null;
        IndexType indexType = IndexType.NONE;
        while (in.isReadable()) {
            switch (state) {
                case READ_HEADER_REPRESENTATION:
                    byte b = in.readByte();
                    if (maxDynamicTableSizeChangeRequired && (b & 0xE0) != 0x20) {
                        // HpackEncoder MUST signal maximum dynamic table size change
                        throw MAX_DYNAMIC_TABLE_SIZE_CHANGE_REQUIRED;
                    }
                    if (b < 0) {
                        // Indexed Header Field
                        index = b & 0x7F;
                        switch (index) {
                            case 0:
                                throw DECODE_ILLEGAL_INDEX_VALUE;
                            case 0x7F:
                                state = READ_INDEXED_HEADER;
                                break;
                            default:
                                HpackHeaderField indexedHeader = getIndexedHeader(index);
                                headerType = validate(indexedHeader.name, headerType, validateHeaders);
                                headersLength = addHeader(headers, indexedHeader.name, indexedHeader.value,
                                        headersLength);
                        }
                    } else if ((b & 0x40) == 0x40) {
                        // Literal Header Field with Incremental Indexing
                        indexType = IndexType.INCREMENTAL;
                        index = b & 0x3F;
                        switch (index) {
                            case 0:
                                state = READ_LITERAL_HEADER_NAME_LENGTH_PREFIX;
                                break;
                            case 0x3F:
                                state = READ_INDEXED_HEADER_NAME;
                                break;
                            default:
                                // Index was stored as the prefix
                                name = readName(index);
                                headerType = validate(name, headerType, validateHeaders);
                                nameLength = name.length();
                                state = READ_LITERAL_HEADER_VALUE_LENGTH_PREFIX;
                        }
                    } else if ((b & 0x20) == 0x20) {
                        // Dynamic Table Size Update
                        index = b & 0x1F;
                        if (index == 0x1F) {
                            state = READ_MAX_DYNAMIC_TABLE_SIZE;
                        } else {
                            setDynamicTableSize(index);
                            state = READ_HEADER_REPRESENTATION;
                        }
                    } else {
                        // Literal Header Field without Indexing / never Indexed
                        indexType = ((b & 0x10) == 0x10) ? IndexType.NEVER : IndexType.NONE;
                        index = b & 0x0F;
                        switch (index) {
                            case 0:
                                state = READ_LITERAL_HEADER_NAME_LENGTH_PREFIX;
                                break;
                            case 0x0F:
                                state = READ_INDEXED_HEADER_NAME;
                                break;
                            default:
                            // Index was stored as the prefix
                            name = readName(index);
                                headerType = validate(name, headerType, validateHeaders);
                            nameLength = name.length();
                            state = READ_LITERAL_HEADER_VALUE_LENGTH_PREFIX;
                        }
                    }
                    break;

                case READ_MAX_DYNAMIC_TABLE_SIZE:
                    setDynamicTableSize(decodeULE128(in, (long) index));
                    state = READ_HEADER_REPRESENTATION;
                    break;

                case READ_INDEXED_HEADER:
                    HpackHeaderField indexedHeader = getIndexedHeader(decodeULE128(in, index));
                    headerType = validate(indexedHeader.name, headerType, validateHeaders);
                    headersLength = addHeader(headers, indexedHeader.name, indexedHeader.value, headersLength);
                    state = READ_HEADER_REPRESENTATION;
                    break;

                case READ_INDEXED_HEADER_NAME:
                    // Header Name matches an entry in the Header Table
                    name = readName(decodeULE128(in, index));
                    headerType = validate(name, headerType, validateHeaders);
                    nameLength = name.length();
                    state = READ_LITERAL_HEADER_VALUE_LENGTH_PREFIX;
                    break;

                case READ_LITERAL_HEADER_NAME_LENGTH_PREFIX:
                    b = in.readByte();
                    huffmanEncoded = (b & 0x80) == 0x80;
                    index = b & 0x7F;
                    if (index == 0x7f) {
                        state = READ_LITERAL_HEADER_NAME_LENGTH;
                    } else {
                        if (index > maxHeaderListSizeGoAway - headersLength) {
                            headerListSizeExceeded(maxHeaderListSizeGoAway);
                        }
                        nameLength = index;
                        state = READ_LITERAL_HEADER_NAME;
                    }
                    break;

                case READ_LITERAL_HEADER_NAME_LENGTH:
                    // Header Name is a Literal String
                    nameLength = decodeULE128(in, index);

                    if (nameLength > maxHeaderListSizeGoAway - headersLength) {
                        headerListSizeExceeded(maxHeaderListSizeGoAway);
                    }
                    state = READ_LITERAL_HEADER_NAME;
                    break;

                case READ_LITERAL_HEADER_NAME:
                    // Wait until entire name is readable
                    if (in.readableBytes() < nameLength) {
                        throw notEnoughDataException(in);
                    }

                    name = readStringLiteral(in, nameLength, huffmanEncoded);
                    headerType = validate(name, headerType, validateHeaders);

                    state = READ_LITERAL_HEADER_VALUE_LENGTH_PREFIX;
                    break;

                case READ_LITERAL_HEADER_VALUE_LENGTH_PREFIX:
                    b = in.readByte();
                    huffmanEncoded = (b & 0x80) == 0x80;
                    index = b & 0x7F;
                    switch (index) {
                        case 0x7f:
                            state = READ_LITERAL_HEADER_VALUE_LENGTH;
                            break;
                        case 0:
                            headerType = validate(name, headerType, validateHeaders);
                            headersLength = insertHeader(headers, name, EMPTY_STRING, indexType, headersLength);
                            state = READ_HEADER_REPRESENTATION;
                            break;
                        default:
                            // Check new header size against max header size
                            if ((long) index + nameLength > maxHeaderListSizeGoAway - headersLength) {
                                headerListSizeExceeded(maxHeaderListSizeGoAway);
                            }
                            valueLength = index;
                            state = READ_LITERAL_HEADER_VALUE;
                    }

                    break;

                case READ_LITERAL_HEADER_VALUE_LENGTH:
                    // Header Value is a Literal String
                    valueLength = decodeULE128(in, index);

                    // Check new header size against max header size
                    if ((long) valueLength + nameLength > maxHeaderListSizeGoAway - headersLength) {
                        headerListSizeExceeded(maxHeaderListSizeGoAway);
                    }
                    state = READ_LITERAL_HEADER_VALUE;
                    break;

                case READ_LITERAL_HEADER_VALUE:
                    // Wait until entire value is readable
                    if (in.readableBytes() < valueLength) {
                        throw notEnoughDataException(in);
                    }

                    CharSequence value = readStringLiteral(in, valueLength, huffmanEncoded);
                    headerType = validate(name, headerType, validateHeaders);
                    headersLength = insertHeader(headers, name, value, indexType, headersLength);
                    state = READ_HEADER_REPRESENTATION;
                    break;

                default:
                    throw new Error("should not reach here state: " + state);
            }
        }

        // we have read all of our headers, and not exceeded maxHeaderListSizeGoAway see if we have
        // exceeded our actual maxHeaderListSize. This must be done here to prevent dynamic table
        // corruption
        if (headersLength > maxHeaderListSize) {
            headerListSizeExceeded(streamId, maxHeaderListSize, true);
        }

        if (state != READ_HEADER_REPRESENTATION) {
            throw connectionError(COMPRESSION_ERROR, "Incomplete header block fragment.");
        }
    }

    /**
     * Set the maximum table size. If this is below the maximum size of the dynamic table used by
     * the encoder, the beginning of the next header block MUST signal this change.
     */
    public void setMaxHeaderTableSize(long maxHeaderTableSize) throws Http2Exception {
        if (maxHeaderTableSize < MIN_HEADER_TABLE_SIZE || maxHeaderTableSize > MAX_HEADER_TABLE_SIZE) {
            throw connectionError(PROTOCOL_ERROR, "Header Table Size must be >= %d and <= %d but was %d",
                    MIN_HEADER_TABLE_SIZE, MAX_HEADER_TABLE_SIZE, maxHeaderTableSize);
        }
        maxDynamicTableSize = maxHeaderTableSize;
        if (maxDynamicTableSize < encoderMaxDynamicTableSize) {
            // decoder requires less space than encoder
            // encoder MUST signal this change
            maxDynamicTableSizeChangeRequired = true;
            hpackDynamicTable.setCapacity(maxDynamicTableSize);
        }
    }

    public void setMaxHeaderListSize(long maxHeaderListSize, long maxHeaderListSizeGoAway) throws Http2Exception {
        if (maxHeaderListSizeGoAway < maxHeaderListSize || maxHeaderListSizeGoAway < 0) {
            throw connectionError(INTERNAL_ERROR, "Header List Size GO_AWAY %d must be positive and >= %d",
                    maxHeaderListSizeGoAway, maxHeaderListSize);
        }
        if (maxHeaderListSize < MIN_HEADER_LIST_SIZE || maxHeaderListSize > MAX_HEADER_LIST_SIZE) {
            throw connectionError(PROTOCOL_ERROR, "Header List Size must be >= %d and <= %d but was %d",
                    MIN_HEADER_TABLE_SIZE, MAX_HEADER_TABLE_SIZE, maxHeaderListSize);
        }
        this.maxHeaderListSize = maxHeaderListSize;
        this.maxHeaderListSizeGoAway = maxHeaderListSizeGoAway;
    }

    public long getMaxHeaderListSize() {
        return maxHeaderListSize;
    }

    public long getMaxHeaderListSizeGoAway() {
        return maxHeaderListSizeGoAway;
    }

    /**
     * Return the maximum table size. This is the maximum size allowed by both the encoder and the
     * decoder.
     */
    public long getMaxHeaderTableSize() {
        return hpackDynamicTable.capacity();
    }

    /**
     * Return the number of header fields in the dynamic table. Exposed for testing.
     */
    int length() {
        return hpackDynamicTable.length();
    }

    /**
     * Return the size of the dynamic table. Exposed for testing.
     */
    long size() {
        return hpackDynamicTable.size();
    }

    /**
     * Return the header field at the given index. Exposed for testing.
     */
    HpackHeaderField getHeaderField(int index) {
        return hpackDynamicTable.getEntry(index + 1);
    }

    private void setDynamicTableSize(long dynamicTableSize) throws Http2Exception {
        if (dynamicTableSize > maxDynamicTableSize) {
            throw INVALID_MAX_DYNAMIC_TABLE_SIZE;
        }
        encoderMaxDynamicTableSize = dynamicTableSize;
        maxDynamicTableSizeChangeRequired = false;
        hpackDynamicTable.setCapacity(dynamicTableSize);
    }

    private HeaderType validate(CharSequence name, HeaderType previousHeaderType,
                                final boolean validateHeaders) throws Http2Exception {
        if (!validateHeaders) {
            return null;
        }

        if (hasPseudoHeaderFormat(name)) {
            if (previousHeaderType == HeaderType.REGULAR_HEADER) {
                throw connectionError(PROTOCOL_ERROR, "Pseudo-header field '%s' found after regular header.", name);
            }

            final Http2Headers.PseudoHeaderName pseudoHeader = getPseudoHeader(name);
            if (pseudoHeader == null) {
                throw connectionError(PROTOCOL_ERROR, "Invalid HTTP/2 pseudo-header '%s' encountered.", name);
            }

            final HeaderType currentHeaderType = pseudoHeader.isRequestOnly() ?
                    HeaderType.REQUEST_PSEUDO_HEADER : HeaderType.RESPONSE_PSEUDO_HEADER;
            if (previousHeaderType != null && currentHeaderType != previousHeaderType) {
                throw connectionError(PROTOCOL_ERROR, "Mix of request and response pseudo-headers.");
            }

            return currentHeaderType;
        }

        return HeaderType.REGULAR_HEADER;
    }

    private CharSequence readName(int index) throws Http2Exception {
        if (index <= HpackStaticTable.length) {
            HpackHeaderField hpackHeaderField = HpackStaticTable.getEntry(index);
            return hpackHeaderField.name;
        }
        if (index - HpackStaticTable.length <= hpackDynamicTable.length()) {
            HpackHeaderField hpackHeaderField = hpackDynamicTable.getEntry(index - HpackStaticTable.length);
            return hpackHeaderField.name;
        }
        throw READ_NAME_ILLEGAL_INDEX_VALUE;
    }

    private HpackHeaderField getIndexedHeader(int index) throws Http2Exception {
        if (index <= HpackStaticTable.length) {
            return HpackStaticTable.getEntry(index);
        }
        if (index - HpackStaticTable.length <= hpackDynamicTable.length()) {
            return hpackDynamicTable.getEntry(index - HpackStaticTable.length);
        }
        throw INDEX_HEADER_ILLEGAL_INDEX_VALUE;
    }

    private long insertHeader(Http2Headers headers, CharSequence name, CharSequence value,
                              IndexType indexType, long headerSize) throws Http2Exception {
        headerSize = addHeader(headers, name, value, headerSize);

        switch (indexType) {
            case NONE:
            case NEVER:
                break;

            case INCREMENTAL:
                hpackDynamicTable.add(new HpackHeaderField(name, value));
                break;

            default:
                throw new Error("should not reach here");
        }

        return headerSize;
    }

    private long addHeader(Http2Headers headers, CharSequence name, CharSequence value, long headersLength)
            throws Http2Exception {
        headersLength += HpackHeaderField.sizeOf(name, value);
        if (headersLength > maxHeaderListSizeGoAway) {
            headerListSizeExceeded(maxHeaderListSizeGoAway);
        }
        headers.add(name, value);
        return headersLength;
    }

    private CharSequence readStringLiteral(ByteBuf in, int length, boolean huffmanEncoded) throws Http2Exception {
        if (huffmanEncoded) {
            return hpackHuffmanDecoder.decode(in, length);
        }
        byte[] buf = new byte[length];
        in.readBytes(buf);
        return new AsciiString(buf, false);
    }

    private static IllegalArgumentException notEnoughDataException(ByteBuf in) {
        return new IllegalArgumentException("decode only works with an entire header block! " + in);
    }

    /**
     * Unsigned Little Endian Base 128 Variable-Length Integer Encoding
     * <p>
     * Visible for testing only!
     */
    static int decodeULE128(ByteBuf in, int result) throws Http2Exception {
        final int readerIndex = in.readerIndex();
        final long v = decodeULE128(in, (long) result);
        if (v > Integer.MAX_VALUE) {
            // the maximum value that can be represented by a signed 32 bit number is:
            // [0x1,0x7f] + 0x7f + (0x7f << 7) + (0x7f << 14) + (0x7f << 21) + (0x6 << 28)
            // OR
            // 0x0 + 0x7f + (0x7f << 7) + (0x7f << 14) + (0x7f << 21) + (0x7 << 28)
            // we should reset the readerIndex if we overflowed the int type.
            in.readerIndex(readerIndex);
            throw DECODE_ULE_128_TO_INT_DECOMPRESSION_EXCEPTION;
        }
        return (int) v;
    }

    /**
     * Unsigned Little Endian Base 128 Variable-Length Integer Encoding
     * <p>
     * Visible for testing only!
     */
    static long decodeULE128(ByteBuf in, long result) throws Http2Exception {
        assert result <= 0x7f && result >= 0;
        final boolean resultStartedAtZero = result == 0;
        final int writerIndex = in.writerIndex();
        for (int readerIndex = in.readerIndex(), shift = 0; readerIndex < writerIndex; ++readerIndex, shift += 7) {
            byte b = in.getByte(readerIndex);
            if (shift == 56 && ((b & 0x80) != 0 || b == 0x7F && !resultStartedAtZero)) {
                // the maximum value that can be represented by a signed 64 bit number is:
                // [0x01L, 0x7fL] + 0x7fL + (0x7fL << 7) + (0x7fL << 14) + (0x7fL << 21) + (0x7fL << 28) + (0x7fL << 35)
                // + (0x7fL << 42) + (0x7fL << 49) + (0x7eL << 56)
                // OR
                // 0x0L + 0x7fL + (0x7fL << 7) + (0x7fL << 14) + (0x7fL << 21) + (0x7fL << 28) + (0x7fL << 35) +
                // (0x7fL << 42) + (0x7fL << 49) + (0x7fL << 56)
                // this means any more shifts will result in overflow so we should break out and throw an error.
                throw DECODE_ULE_128_TO_LONG_DECOMPRESSION_EXCEPTION;
            }

            if ((b & 0x80) == 0) {
                in.readerIndex(readerIndex + 1);
                return result + ((b & 0x7FL) << shift);
            }
            result += (b & 0x7FL) << shift;
        }

        throw DECODE_ULE_128_DECOMPRESSION_EXCEPTION;
    }

    /**
     * HTTP/2 header types.
     */
    private enum HeaderType {
        REGULAR_HEADER,
        REQUEST_PSEUDO_HEADER,
        RESPONSE_PSEUDO_HEADER
    }
}
