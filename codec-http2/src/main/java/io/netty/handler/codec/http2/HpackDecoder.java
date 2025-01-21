/*
 * Copyright 2015 The Netty Project
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

/*
 * Copyright 2014 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.netty.handler.codec.http2;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpHeaderValidationUtil;
import io.netty.handler.codec.http2.HpackUtil.IndexType;
import io.netty.util.AsciiString;

import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_HEADER_TABLE_SIZE;
import static io.netty.handler.codec.http2.Http2CodecUtil.MAX_HEADER_LIST_SIZE;
import static io.netty.handler.codec.http2.Http2CodecUtil.MAX_HEADER_TABLE_SIZE;
import static io.netty.handler.codec.http2.Http2CodecUtil.MIN_HEADER_LIST_SIZE;
import static io.netty.handler.codec.http2.Http2CodecUtil.MIN_HEADER_TABLE_SIZE;
import static io.netty.handler.codec.http2.Http2CodecUtil.headerListSizeExceeded;
import static io.netty.handler.codec.http2.Http2Error.COMPRESSION_ERROR;
import static io.netty.handler.codec.http2.Http2Error.PROTOCOL_ERROR;
import static io.netty.handler.codec.http2.Http2Exception.connectionError;
import static io.netty.handler.codec.http2.Http2Exception.streamError;
import static io.netty.handler.codec.http2.Http2Headers.PseudoHeaderName.getPseudoHeader;
import static io.netty.handler.codec.http2.Http2Headers.PseudoHeaderName.hasPseudoHeaderFormat;
import static io.netty.util.AsciiString.EMPTY_STRING;
import static io.netty.util.internal.ObjectUtil.checkPositive;

final class HpackDecoder {
    private static final Http2Exception DECODE_ULE_128_DECOMPRESSION_EXCEPTION =
            Http2Exception.newStatic(COMPRESSION_ERROR, "HPACK - decompression failure",
                    Http2Exception.ShutdownHint.HARD_SHUTDOWN, HpackDecoder.class,
            "decodeULE128(..)");
    private static final Http2Exception DECODE_ULE_128_TO_LONG_DECOMPRESSION_EXCEPTION =
            Http2Exception.newStatic(COMPRESSION_ERROR, "HPACK - long overflow",
                    Http2Exception.ShutdownHint.HARD_SHUTDOWN, HpackDecoder.class, "decodeULE128(..)");
    private static final Http2Exception DECODE_ULE_128_TO_INT_DECOMPRESSION_EXCEPTION =
            Http2Exception.newStatic(COMPRESSION_ERROR, "HPACK - int overflow",
                    Http2Exception.ShutdownHint.HARD_SHUTDOWN, HpackDecoder.class, "decodeULE128ToInt(..)");
    private static final Http2Exception DECODE_ILLEGAL_INDEX_VALUE =
            Http2Exception.newStatic(COMPRESSION_ERROR, "HPACK - illegal index value",
                    Http2Exception.ShutdownHint.HARD_SHUTDOWN, HpackDecoder.class, "decode(..)");
    private static final Http2Exception INDEX_HEADER_ILLEGAL_INDEX_VALUE =
            Http2Exception.newStatic(COMPRESSION_ERROR, "HPACK - illegal index value",
                    Http2Exception.ShutdownHint.HARD_SHUTDOWN, HpackDecoder.class, "indexHeader(..)");
    private static final Http2Exception READ_NAME_ILLEGAL_INDEX_VALUE =
            Http2Exception.newStatic(COMPRESSION_ERROR, "HPACK - illegal index value",
                    Http2Exception.ShutdownHint.HARD_SHUTDOWN, HpackDecoder.class, "readName(..)");
    private static final Http2Exception INVALID_MAX_DYNAMIC_TABLE_SIZE =
            Http2Exception.newStatic(COMPRESSION_ERROR, "HPACK - invalid max dynamic table size",
                    Http2Exception.ShutdownHint.HARD_SHUTDOWN, HpackDecoder.class,
            "setDynamicTableSize(..)");
    private static final Http2Exception MAX_DYNAMIC_TABLE_SIZE_CHANGE_REQUIRED =
            Http2Exception.newStatic(COMPRESSION_ERROR, "HPACK - max dynamic table size change required",
                    Http2Exception.ShutdownHint.HARD_SHUTDOWN, HpackDecoder.class, "decode(..)");
    private static final byte READ_HEADER_REPRESENTATION = 0;
    private static final byte READ_INDEXED_HEADER = 1;
    private static final byte READ_INDEXED_HEADER_NAME = 2;
    private static final byte READ_LITERAL_HEADER_NAME_LENGTH_PREFIX = 3;
    private static final byte READ_LITERAL_HEADER_NAME_LENGTH = 4;
    private static final byte READ_LITERAL_HEADER_NAME = 5;
    private static final byte READ_LITERAL_HEADER_VALUE_LENGTH_PREFIX = 6;
    private static final byte READ_LITERAL_HEADER_VALUE_LENGTH = 7;
    private static final byte READ_LITERAL_HEADER_VALUE = 8;

    private final HpackHuffmanDecoder huffmanDecoder = new HpackHuffmanDecoder();
    private final HpackDynamicTable hpackDynamicTable;
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
     */
    HpackDecoder(long maxHeaderListSize) {
        this(maxHeaderListSize, DEFAULT_HEADER_TABLE_SIZE);
    }

    /**
     * Exposed Used for testing only! Default values used in the initial settings frame are overridden intentionally
     * for testing but violate the RFC if used outside the scope of testing.
     */
    HpackDecoder(long maxHeaderListSize, int maxHeaderTableSize) {
        this.maxHeaderListSize = checkPositive(maxHeaderListSize, "maxHeaderListSize");

        maxDynamicTableSize = encoderMaxDynamicTableSize = maxHeaderTableSize;
        maxDynamicTableSizeChangeRequired = false;
        hpackDynamicTable = new HpackDynamicTable(maxHeaderTableSize);
    }

    /**
     * Decode the header block into header fields.
     * <p>
     * This method assumes the entire header block is contained in {@code in}.
     */
    void decode(int streamId, ByteBuf in, Http2Headers headers, boolean validateHeaders) throws Http2Exception {
        Http2HeadersSink sink = new Http2HeadersSink(
                streamId, headers, maxHeaderListSize, validateHeaders);
        // Check for dynamic table size updates, which must occur at the beginning:
        // https://www.rfc-editor.org/rfc/rfc7541.html#section-4.2
        decodeDynamicTableSizeUpdates(in);
        decode(in, sink);

        // Now that we've read all of our headers we can perform the validation steps. We must
        // delay throwing until this point to prevent dynamic table corruption.
        sink.finish();
    }

    private void decodeDynamicTableSizeUpdates(ByteBuf in) throws Http2Exception {
        byte b;
        while (in.isReadable() && ((b = in.getByte(in.readerIndex())) & 0x20) == 0x20 && ((b & 0xC0) == 0x00)) {
            in.readByte();
            int index = b & 0x1F;
            if (index == 0x1F) {
                setDynamicTableSize(decodeULE128(in, (long) index));
            } else {
                setDynamicTableSize(index);
            }
        }
    }

    private void decode(ByteBuf in, Http2HeadersSink sink) throws Http2Exception {
        int index = 0;
        int nameLength = 0;
        int valueLength = 0;
        byte state = READ_HEADER_REPRESENTATION;
        boolean huffmanEncoded = false;
        AsciiString name = null;
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
                                sink.appendToHeaderList(
                                        (AsciiString) indexedHeader.name,
                                        (AsciiString) indexedHeader.value);
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
                                nameLength = name.length();
                                state = READ_LITERAL_HEADER_VALUE_LENGTH_PREFIX;
                        }
                    } else if ((b & 0x20) == 0x20) {
                        // Dynamic Table Size Update
                        // See https://www.rfc-editor.org/rfc/rfc7541.html#section-4.2
                        throw connectionError(COMPRESSION_ERROR, "Dynamic table size update must happen " +
                            "at the beginning of the header block");
                    } else {
                        // Literal Header Field without Indexing / never Indexed
                        indexType = (b & 0x10) == 0x10 ? IndexType.NEVER : IndexType.NONE;
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
                                nameLength = name.length();
                                state = READ_LITERAL_HEADER_VALUE_LENGTH_PREFIX;
                        }
                    }
                    break;

                case READ_INDEXED_HEADER:
                    HpackHeaderField indexedHeader = getIndexedHeader(decodeULE128(in, index));
                    sink.appendToHeaderList(
                            (AsciiString) indexedHeader.name,
                            (AsciiString) indexedHeader.value);
                    state = READ_HEADER_REPRESENTATION;
                    break;

                case READ_INDEXED_HEADER_NAME:
                    // Header Name matches an entry in the Header Table
                    name = readName(decodeULE128(in, index));
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
                        nameLength = index;
                        state = READ_LITERAL_HEADER_NAME;
                    }
                    break;

                case READ_LITERAL_HEADER_NAME_LENGTH:
                    // Header Name is a Literal String
                    nameLength = decodeULE128(in, index);

                    state = READ_LITERAL_HEADER_NAME;
                    break;

                case READ_LITERAL_HEADER_NAME:
                    // Wait until entire name is readable
                    if (in.readableBytes() < nameLength) {
                        throw notEnoughDataException(in);
                    }

                    name = readStringLiteral(in, nameLength, huffmanEncoded);

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
                            insertHeader(sink, name, EMPTY_STRING, indexType);
                            state = READ_HEADER_REPRESENTATION;
                            break;
                        default:
                            valueLength = index;
                            state = READ_LITERAL_HEADER_VALUE;
                    }

                    break;

                case READ_LITERAL_HEADER_VALUE_LENGTH:
                    // Header Value is a Literal String
                    valueLength = decodeULE128(in, index);

                    state = READ_LITERAL_HEADER_VALUE;
                    break;

                case READ_LITERAL_HEADER_VALUE:
                    // Wait until entire value is readable
                    if (in.readableBytes() < valueLength) {
                        throw notEnoughDataException(in);
                    }

                    AsciiString value = readStringLiteral(in, valueLength, huffmanEncoded);
                    insertHeader(sink, name, value, indexType);
                    state = READ_HEADER_REPRESENTATION;
                    break;

                default:
                    throw new Error("should not reach here state: " + state);
            }
        }

        if (state != READ_HEADER_REPRESENTATION) {
            throw connectionError(COMPRESSION_ERROR, "Incomplete header block fragment.");
        }
    }

    /**
     * Set the maximum table size. If this is below the maximum size of the dynamic table used by
     * the encoder, the beginning of the next header block MUST signal this change.
     */
    void setMaxHeaderTableSize(long maxHeaderTableSize) throws Http2Exception {
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

    void setMaxHeaderListSize(long maxHeaderListSize) throws Http2Exception {
        if (maxHeaderListSize < MIN_HEADER_LIST_SIZE || maxHeaderListSize > MAX_HEADER_LIST_SIZE) {
            throw connectionError(PROTOCOL_ERROR, "Header List Size must be >= %d and <= %d but was %d",
                    MIN_HEADER_TABLE_SIZE, MAX_HEADER_TABLE_SIZE, maxHeaderListSize);
        }
        this.maxHeaderListSize = maxHeaderListSize;
    }

    long getMaxHeaderListSize() {
        return maxHeaderListSize;
    }

    /**
     * Return the maximum table size. This is the maximum size allowed by both the encoder and the
     * decoder.
     */
    long getMaxHeaderTableSize() {
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

    private static HeaderType validateHeader(int streamId, AsciiString name, CharSequence value,
            HeaderType previousHeaderType) throws Http2Exception {
        if (hasPseudoHeaderFormat(name)) {
            if (previousHeaderType == HeaderType.REGULAR_HEADER) {
                throw streamError(streamId, PROTOCOL_ERROR,
                        "Pseudo-header field '%s' found after regular header.", name);
            }
            final Http2Headers.PseudoHeaderName pseudoHeader = getPseudoHeader(name);
            final HeaderType currentHeaderType = pseudoHeader.isRequestOnly() ?
                    HeaderType.REQUEST_PSEUDO_HEADER : HeaderType.RESPONSE_PSEUDO_HEADER;
            if (previousHeaderType != null && currentHeaderType != previousHeaderType) {
                throw streamError(streamId, PROTOCOL_ERROR, "Mix of request and response pseudo-headers.");
            }
            return currentHeaderType;
        }
        if (HttpHeaderValidationUtil.isConnectionHeader(name, true)) {
            throw streamError(streamId, PROTOCOL_ERROR, "Illegal connection-specific header '%s' encountered.", name);
        }
        if (HttpHeaderValidationUtil.isTeNotTrailers(name, value)) {
            throw streamError(streamId, PROTOCOL_ERROR,
                    "Illegal value specified for the 'TE' header (only 'trailers' is allowed).");
        }

        return HeaderType.REGULAR_HEADER;
    }

    private AsciiString readName(int index) throws Http2Exception {
        if (index <= HpackStaticTable.length) {
            HpackHeaderField hpackHeaderField = HpackStaticTable.getEntry(index);
            return (AsciiString) hpackHeaderField.name;
        }
        if (index - HpackStaticTable.length <= hpackDynamicTable.length()) {
            HpackHeaderField hpackHeaderField = hpackDynamicTable.getEntry(index - HpackStaticTable.length);
            return (AsciiString) hpackHeaderField.name;
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

    private void insertHeader(Http2HeadersSink sink, AsciiString name, AsciiString value, IndexType indexType) {
        sink.appendToHeaderList(name, value);

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
    }

    private AsciiString readStringLiteral(ByteBuf in, int length, boolean huffmanEncoded) throws Http2Exception {
        if (huffmanEncoded) {
            return huffmanDecoder.decode(in, length);
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

    private static final class Http2HeadersSink {
        private final Http2Headers headers;
        private final long maxHeaderListSize;
        private final int streamId;
        private final boolean validateHeaders;
        private long headersLength;
        private boolean exceededMaxLength;
        private HeaderType previousType;
        private Http2Exception validationException;

        Http2HeadersSink(int streamId, Http2Headers headers, long maxHeaderListSize, boolean validateHeaders) {
            this.headers = headers;
            this.maxHeaderListSize = maxHeaderListSize;
            this.streamId = streamId;
            this.validateHeaders = validateHeaders;
        }

        void finish() throws Http2Exception {
            if (exceededMaxLength) {
                headerListSizeExceeded(streamId, maxHeaderListSize, true);
            } else if (validationException != null) {
                throw validationException;
            }
        }

        void appendToHeaderList(AsciiString name, AsciiString value) {
            headersLength += HpackHeaderField.sizeOf(name, value);
            exceededMaxLength |= headersLength > maxHeaderListSize;

            if (exceededMaxLength || validationException != null) {
                // We don't store the header since we've already failed validation requirements.
                return;
            }

            try {
                headers.add(name, value);
                if (validateHeaders) {
                    previousType = validateHeader(streamId, name, value, previousType);
                }
            } catch (IllegalArgumentException ex) {
                validationException = streamError(streamId, PROTOCOL_ERROR, ex,
                        "Validation failed for header '%s': %s", name, ex.getMessage());
            } catch (Http2Exception ex) {
                validationException = streamError(streamId, PROTOCOL_ERROR, ex, ex.getMessage());
            }
        }
    }
}
