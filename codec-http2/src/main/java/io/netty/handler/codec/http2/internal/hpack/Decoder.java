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
package io.netty.handler.codec.http2.internal.hpack;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.internal.hpack.HpackUtil.IndexType;
import io.netty.util.AsciiString;

import static io.netty.handler.codec.http2.Http2Error.COMPRESSION_ERROR;
import static io.netty.handler.codec.http2.Http2Error.ENHANCE_YOUR_CALM;
import static io.netty.handler.codec.http2.Http2Exception.connectionError;
import static io.netty.util.AsciiString.EMPTY_STRING;
import static io.netty.util.internal.ThrowableUtil.unknownStackTrace;

public final class Decoder {
    private static final Http2Exception DECODE_DECOMPRESSION_EXCEPTION = unknownStackTrace(
            connectionError(COMPRESSION_ERROR, "HPACK - decompression failure"), Decoder.class, "decode(...)");
    private static final Http2Exception DECODE_ULE_128_DECOMPRESSION_EXCEPTION = unknownStackTrace(
            connectionError(COMPRESSION_ERROR, "HPACK - decompression failure"), Decoder.class, "decodeULE128(...)");
    private static final Http2Exception DECODE_ILLEGAL_INDEX_VALUE = unknownStackTrace(
            connectionError(COMPRESSION_ERROR, "HPACK - illegal index value"), Decoder.class, "decode(...)");
    private static final Http2Exception INDEX_HEADER_ILLEGAL_INDEX_VALUE = unknownStackTrace(
            connectionError(COMPRESSION_ERROR, "HPACK - illegal index value"), Decoder.class, "indexHeader(...)");
    private static final Http2Exception READ_NAME_ILLEGAL_INDEX_VALUE = unknownStackTrace(
            connectionError(COMPRESSION_ERROR, "HPACK - illegal index value"), Decoder.class, "readName(...)");
    private static final Http2Exception INVALID_MAX_DYNAMIC_TABLE_SIZE = unknownStackTrace(
            connectionError(COMPRESSION_ERROR, "HPACK - invalid max dynamic table size"), Decoder.class,
            "setDynamicTableSize(...)");
    private static final Http2Exception MAX_DYNAMIC_TABLE_SIZE_CHANGE_REQUIRED = unknownStackTrace(
            connectionError(COMPRESSION_ERROR, "HPACK - max dynamic table size change required"), Decoder.class,
            "decode(...)");
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

    private final DynamicTable dynamicTable;
    private final HuffmanDecoder huffmanDecoder;
    private final int maxHeadersLength;
    private int maxDynamicTableSize;
    private int encoderMaxDynamicTableSize;
    private boolean maxDynamicTableSizeChangeRequired;

    /**
     * Create a new instance.
     * @param maxHeadersLength The maximum size (in bytes) that is allowed for a single header decode operation.
     * @param maxHeaderTableSize
     * <a href="https://tools.ietf.org/html/rfc7540#section-6.5.2">SETTINGS_HEADER_TABLE_SIZE</a>.
     * @param initialHuffmanDecodeCapacity The initial size of the byte array used to do huffman decoding.
     */
    public Decoder(int maxHeadersLength, int maxHeaderTableSize, int initialHuffmanDecodeCapacity) {
        dynamicTable = new DynamicTable(maxHeaderTableSize);
        this.maxHeadersLength = maxHeadersLength;
        maxDynamicTableSize = maxHeaderTableSize;
        encoderMaxDynamicTableSize = maxHeaderTableSize;
        maxDynamicTableSizeChangeRequired = false;
        huffmanDecoder = new HuffmanDecoder(initialHuffmanDecodeCapacity);
    }

    /**
     * Decode the header block into header fields.
     * <p>
     * This method assumes the entire header block is contained in {@code in}.
     */
    public void decode(ByteBuf in, Http2Headers headers) throws Http2Exception {
        int index = 0;
        int headersLength = 0;
        int nameLength = 0;
        int valueLength = 0;
        byte state = READ_HEADER_REPRESENTATION;
        boolean huffmanEncoded = false;
        CharSequence name = null;
        IndexType indexType = IndexType.NONE;
        while (in.isReadable()) {
            switch (state) {
                case READ_HEADER_REPRESENTATION:
                    byte b = in.readByte();
                    if (maxDynamicTableSizeChangeRequired && (b & 0xE0) != 0x20) {
                        // Encoder MUST signal maximum dynamic table size change
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
                                headersLength = indexHeader(index, headers, headersLength);
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
                            state = READ_LITERAL_HEADER_VALUE_LENGTH_PREFIX;
                        }
                    }
                    break;

                case READ_MAX_DYNAMIC_TABLE_SIZE:
                    int maxSize = decodeULE128(in) + index;

                    if (maxSize < 0) { // Check for numerical overflow
                        throw DECODE_DECOMPRESSION_EXCEPTION;
                    }

                    setDynamicTableSize(maxSize);
                    state = READ_HEADER_REPRESENTATION;
                    break;

                case READ_INDEXED_HEADER:
                    int headerIndex = decodeULE128(in) + index;

                    if (headerIndex < 0) { // Check for numerical overflow
                        throw DECODE_DECOMPRESSION_EXCEPTION;
                    }

                    headersLength = indexHeader(headerIndex, headers, headersLength);
                    state = READ_HEADER_REPRESENTATION;
                    break;

                case READ_INDEXED_HEADER_NAME:
                    // Header Name matches an entry in the Header Table
                    int nameIndex = decodeULE128(in) + index;

                    if (nameIndex < 0) { // Check for numerical overflow
                        throw DECODE_DECOMPRESSION_EXCEPTION;
                    }

                    name = readName(nameIndex);
                    state = READ_LITERAL_HEADER_VALUE_LENGTH_PREFIX;
                    break;

                case READ_LITERAL_HEADER_NAME_LENGTH_PREFIX:
                    b = in.readByte();
                    huffmanEncoded = (b & 0x80) == 0x80;
                    index = b & 0x7F;
                    if (index == 0x7f) {
                        state = READ_LITERAL_HEADER_NAME_LENGTH;
                    } else {
                        if (index > maxHeadersLength - headersLength) {
                            maxHeaderSizeExceeded();
                        }
                        nameLength = index;
                        state = READ_LITERAL_HEADER_NAME;
                    }
                    break;

                case READ_LITERAL_HEADER_NAME_LENGTH:
                    // Header Name is a Literal String
                    nameLength = decodeULE128(in) + index;

                    if (nameLength < 0) { // Check for numerical overflow
                        throw DECODE_DECOMPRESSION_EXCEPTION;
                    }

                    if (nameLength > maxHeadersLength - headersLength) {
                        maxHeaderSizeExceeded();
                    }
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
                            headersLength = insertHeader(headers, name, EMPTY_STRING, indexType, headersLength);
                            state = READ_HEADER_REPRESENTATION;
                            break;
                        default:
                            // Check new header size against max header size
                            if ((long) index + nameLength > maxHeadersLength - headersLength) {
                                maxHeaderSizeExceeded();
                            }
                            valueLength = index;
                            state = READ_LITERAL_HEADER_VALUE;
                    }

                    break;

                case READ_LITERAL_HEADER_VALUE_LENGTH:
                    // Header Value is a Literal String
                    valueLength = decodeULE128(in) + index;

                    if (valueLength < 0) { // Check for numerical overflow
                        throw DECODE_DECOMPRESSION_EXCEPTION;
                    }

                    // Check new header size against max header size
                    if ((long) valueLength + nameLength > maxHeadersLength - headersLength) {
                        maxHeaderSizeExceeded();
                    }
                    state = READ_LITERAL_HEADER_VALUE;
                    break;

                case READ_LITERAL_HEADER_VALUE:
                    // Wait until entire value is readable
                    if (in.readableBytes() < valueLength) {
                        throw notEnoughDataException(in);
                    }

                    CharSequence value = readStringLiteral(in, valueLength, huffmanEncoded);
                    headersLength = insertHeader(headers, name, value, indexType, headersLength);
                    state = READ_HEADER_REPRESENTATION;
                    break;

                default:
                    throw new Error("should not reach here state: " + state);
            }
        }
    }

    /**
     * Set the maximum table size. If this is below the maximum size of the dynamic table used by
     * the encoder, the beginning of the next header block MUST signal this change.
     */
    public void setMaxHeaderTableSize(int maxHeaderTableSize) {
        maxDynamicTableSize = maxHeaderTableSize;
        if (maxDynamicTableSize < encoderMaxDynamicTableSize) {
            // decoder requires less space than encoder
            // encoder MUST signal this change
            maxDynamicTableSizeChangeRequired = true;
            dynamicTable.setCapacity(maxDynamicTableSize);
        }
    }

    /**
     * Return the maximum table size. This is the maximum size allowed by both the encoder and the
     * decoder.
     */
    public int getMaxHeaderTableSize() {
        return dynamicTable.capacity();
    }

    /**
     * Return the number of header fields in the dynamic table. Exposed for testing.
     */
    int length() {
        return dynamicTable.length();
    }

    /**
     * Return the size of the dynamic table. Exposed for testing.
     */
    int size() {
        return dynamicTable.size();
    }

    /**
     * Return the header field at the given index. Exposed for testing.
     */
    HeaderField getHeaderField(int index) {
        return dynamicTable.getEntry(index + 1);
    }

    private void setDynamicTableSize(int dynamicTableSize) throws Http2Exception {
        if (dynamicTableSize > maxDynamicTableSize) {
            throw INVALID_MAX_DYNAMIC_TABLE_SIZE;
        }
        encoderMaxDynamicTableSize = dynamicTableSize;
        maxDynamicTableSizeChangeRequired = false;
        dynamicTable.setCapacity(dynamicTableSize);
    }

    private CharSequence readName(int index) throws Http2Exception {
        if (index <= StaticTable.length) {
            HeaderField headerField = StaticTable.getEntry(index);
            return headerField.name;
        }
        if (index - StaticTable.length <= dynamicTable.length()) {
            HeaderField headerField = dynamicTable.getEntry(index - StaticTable.length);
            return headerField.name;
        }
        throw READ_NAME_ILLEGAL_INDEX_VALUE;
    }

    private int indexHeader(int index, Http2Headers headers, int headersLength) throws Http2Exception {
        if (index <= StaticTable.length) {
            HeaderField headerField = StaticTable.getEntry(index);
            return addHeader(headers, headerField.name, headerField.value, headersLength);
        }
        if (index - StaticTable.length <= dynamicTable.length()) {
            HeaderField headerField = dynamicTable.getEntry(index - StaticTable.length);
            return addHeader(headers, headerField.name, headerField.value, headersLength);
        }
        throw INDEX_HEADER_ILLEGAL_INDEX_VALUE;
    }

    private int insertHeader(Http2Headers headers, CharSequence name, CharSequence value, IndexType indexType,
                             int headerSize) throws Http2Exception {
        headerSize = addHeader(headers, name, value, headerSize);

        switch (indexType) {
            case NONE:
            case NEVER:
                break;

            case INCREMENTAL:
                dynamicTable.add(new HeaderField(name, value));
                break;

            default:
                throw new Error("should not reach here");
        }

        return headerSize;
    }

    private int addHeader(Http2Headers headers, CharSequence name, CharSequence value, int headersLength)
            throws Http2Exception {
        long newHeadersLength = (long) headersLength + name.length() + value.length();
        if (newHeadersLength > maxHeadersLength) {
            maxHeaderSizeExceeded();
        }
        headers.add(name, value);
        return (int) newHeadersLength;
    }

    /**
     * Respond to headers block resulting in the maximum header size being exceeded.
     * @throws Http2Exception If we can not recover from the truncation.
     */
    private void maxHeaderSizeExceeded() throws Http2Exception {
        throw connectionError(ENHANCE_YOUR_CALM, "Header size exceeded max allowed bytes (%d)", maxHeadersLength);
    }

    private CharSequence readStringLiteral(ByteBuf in, int length, boolean huffmanEncoded) throws Http2Exception {
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

    // Unsigned Little Endian Base 128 Variable-Length Integer Encoding
    private static int decodeULE128(ByteBuf in) throws Http2Exception {
        final int writerIndex = in.writerIndex();
        for (int readerIndex = in.readerIndex(), shift = 0, result = 0;
             readerIndex < writerIndex; ++readerIndex, shift += 7) {
            byte b = in.getByte(readerIndex);
            if (shift == 28 && (b & 0xF8) != 0) {
                in.readerIndex(readerIndex + 1);
                break;
            }

            if ((b & 0x80) == 0) {
                in.readerIndex(readerIndex + 1);
                return result | ((b & 0x7F) << shift);
            }
            result |= (b & 0x7F) << shift;
        }

        throw DECODE_ULE_128_DECOMPRESSION_EXCEPTION;
    }
}
