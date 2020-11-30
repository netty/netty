/*
 * Copyright 2020 The Netty Project
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
package io.netty.incubator.codec.http3;

import io.netty.buffer.ByteBuf;
import io.netty.util.AsciiString;

import static io.netty.util.internal.ObjectUtil.checkPositive;
import static io.netty.incubator.codec.http3.Http3Headers.PseudoHeaderName.getPseudoHeader;
import static io.netty.incubator.codec.http3.Http3Headers.PseudoHeaderName.hasPseudoHeaderFormat;

final class QpackDecoder {

    private static final Http3Exception HEADER_ILLEGAL_INDEX_VALUE =
        new Http3Exception("QPACK - illegal index value");
    private static final Http3Exception NAME_ILLEGAL_INDEX_VALUE =
        new Http3Exception("QPACK - illegal index value");

    private static final long DEFAULT_MAX_HEADER_LIST_SIZE = 0xffffffffL;

    private final QpackHuffmanDecoder huffmanDecoder = new QpackHuffmanDecoder();

    private long maxHeaderListSize;

    /**
     * Create a new instance.
     */
    QpackDecoder() {
        this(DEFAULT_MAX_HEADER_LIST_SIZE);
    }

    /**
     * Create a new instance.
     */
    QpackDecoder(long maxHeaderListSize) {
        this.maxHeaderListSize = checkPositive(maxHeaderListSize, "maxHeaderListSize");
    }

    /**
     * Decode the header block into header fields.
     * <p>
     * This method assumes the entire header block is contained in {@code in}.
     */
    public void decodeHeaders(ByteBuf in, Http3Headers headers, boolean validateHeaders) throws Http3Exception {
        Http3HeadersSink sink = new Http3HeadersSink(headers, maxHeaderListSize, validateHeaders);
        decode(in, sink);

        // Throws exception if detected any problem so far
        sink.finish();
    }

    private void decode(ByteBuf in, Sink sink) throws Http3Exception {
        // Required Insert Count
        // https://tools.ietf.org/html/draft-ietf-quic-qpack-19#section-4.5.1.1
        decodePrefixedInteger(in, 8);

        // Delta Base
        // https://tools.ietf.org/html/draft-ietf-quic-qpack-19#section-4.5.1.2
        decodePrefixedInteger(in, 7);

        while (in.isReadable()) {
            byte b = in.getByte(in.readerIndex());
            if ((b & 0x80) == 0x80) {
                // 1xxxxxxx
                decodeIndexed(in, sink);
            } else if ((b & 0xc0) == 0x40) {
                // 01xxxxxx
                decodeLiteralWithNameReference(in, sink);
            } else if ((b & 0xe0) == 0x20) {
                // 001xxxxx
                decodeLiteral(in, sink);
            }
        }
    }

    private long decodePrefixedInteger(ByteBuf in, int prefixLength) {
        int nbits = (1 << prefixLength) - 1;
        int first = in.readByte() & nbits;
        if (first < nbits) {
            return first;
        }

        long i = first;
        int factor = 0;
        byte next;
        do {
            next = in.readByte();
            i += (next & 0x7f) << factor;
            factor += 7;
        } while ((next & 0x80) == 0x80);

        return i;
    }

    private void decodeIndexed(ByteBuf in, Sink sink) throws Http3Exception {
        if ((in.getByte(in.readerIndex()) & 0x40) == 0x40) {
            final int staticIndex = (int) decodePrefixedInteger(in, 6);
            final QpackHeaderField field = getIndexedHeader(staticIndex);
            sink.appendToHeaderList(field.name, field.value);
        } else {
            throw new UnsupportedOperationException("dynamic table is not implemented");
        }
    }

    private void decodeLiteralWithNameReference(ByteBuf in, Sink sink) throws Http3Exception {
        if ((in.getByte(in.readerIndex()) & 0x10) == 0x10) {
            final int staticNameIndex = (int) decodePrefixedInteger(in, 4);
            final CharSequence name = getIndexedName(staticNameIndex);
            final CharSequence value = decodePrefixedStringLiteral(in);
            sink.appendToHeaderList(name, value);
        } else {
            throw new UnsupportedOperationException("dynamic table is not implemented");
        }
    }

    private void decodeLiteral(ByteBuf in, Sink sink) throws Http3Exception {
        final CharSequence name = decodePrefixedStringLiteral(in, (byte) 0x8, 3);
        final CharSequence value = decodePrefixedStringLiteral(in);
        sink.appendToHeaderList(name, value);
    }

    private CharSequence decodePrefixedStringLiteral(ByteBuf in) throws Http3Exception {
        return decodePrefixedStringLiteral(in, (byte) 0x80, 7);
    }

    private CharSequence decodePrefixedStringLiteral(ByteBuf in, byte mask, int prefix) throws Http3Exception {
        final boolean huffmanEncoded = (in.getByte(in.readerIndex()) & mask) == mask;
        final int length = (int) decodePrefixedInteger(in, prefix);
        return decodeStringLiteral(in, length, huffmanEncoded);
    }

    private CharSequence decodeStringLiteral(ByteBuf in, int length, boolean huffmanEncoded) throws Http3Exception {
        if (huffmanEncoded) {
            return huffmanDecoder.decode(in, length);
        }
        byte[] buf = new byte[length];
        in.readBytes(buf);
        return new AsciiString(buf, false);
    }

    private CharSequence getIndexedName(int index) throws Http3Exception {
        if (index <= QpackStaticTable.length) {
            final QpackHeaderField field = QpackStaticTable.getField(index);
            return field.name;
        }
        throw NAME_ILLEGAL_INDEX_VALUE;
    }

    private QpackHeaderField getIndexedHeader(int index) throws Http3Exception {
        if (index <= QpackStaticTable.length) {
            return QpackStaticTable.getField(index);
        }
        throw HEADER_ILLEGAL_INDEX_VALUE;
    }

    private static HeaderType validate(CharSequence name, HeaderType previousHeaderType) throws Http3Exception {
        if (hasPseudoHeaderFormat(name)) {
            if (previousHeaderType == HeaderType.REGULAR_HEADER) {
                throw new Http3Exception(String.format("Pseudo-header field '%s' found after regular header.", name));
            }

            final Http3Headers.PseudoHeaderName pseudoHeader = getPseudoHeader(name);
            if (pseudoHeader == null) {
                throw new Http3Exception(String.format("Invalid HTTP/3 pseudo-header '%s' encountered.", name));
            }

            final HeaderType currentHeaderType = pseudoHeader.isRequestOnly() ?
                    HeaderType.REQUEST_PSEUDO_HEADER : HeaderType.RESPONSE_PSEUDO_HEADER;
            if (previousHeaderType != null && currentHeaderType != previousHeaderType) {
                throw new Http3Exception(String.format("Mix of request and response pseudo-headers."));
            }

            return currentHeaderType;
        }

        return HeaderType.REGULAR_HEADER;
    }

    private enum HeaderType {
        REGULAR_HEADER,
        REQUEST_PSEUDO_HEADER,
        RESPONSE_PSEUDO_HEADER
    }

    private interface Sink {
        void appendToHeaderList(CharSequence name, CharSequence value);
        void finish() throws Http3Exception;
    }

    private static final class Http3HeadersSink implements Sink {
        private final Http3Headers headers;
        private final long maxHeaderListSize;
        private final boolean validate;
        private long headersLength;
        private boolean exceededMaxLength;
        private Http3Exception validationException;
        private HeaderType previousType;

        Http3HeadersSink(Http3Headers headers, long maxHeaderListSize, boolean validate) {
            this.headers = headers;
            this.maxHeaderListSize = maxHeaderListSize;
            this.validate = validate;
        }

        @Override
        public void finish() throws Http3Exception {
            if (exceededMaxLength) {
                throw new Http3Exception(
                    String.format("Header size exceeded max allowed size (%d)", maxHeaderListSize));
            } else if (validationException != null) {
                throw validationException;
            }
        }

        @Override
        public void appendToHeaderList(CharSequence name, CharSequence value) {
            headersLength += QpackHeaderField.sizeOf(name, value);
            exceededMaxLength |= headersLength > maxHeaderListSize;

            if (exceededMaxLength || validationException != null) {
                // We don't store the header since we've already failed validation requirements.
                return;
            }

            if (validate) {
                try {
                    previousType = QpackDecoder.validate(name, previousType);
                } catch (Http3Exception ex) {
                    validationException = ex;
                    return;
                }
            }

            headers.add(name, value);
        }
    }
}
