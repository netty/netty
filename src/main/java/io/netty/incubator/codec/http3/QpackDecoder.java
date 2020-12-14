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

import java.util.function.BiConsumer;

final class QpackDecoder {

    private static final QpackException HEADER_ILLEGAL_INDEX_VALUE =
            QpackException.newStatic(QpackDecoder.class, "getIndexedHeader(...)", "QPACK - illegal index value");
    private static final QpackException NAME_ILLEGAL_INDEX_VALUE =
            QpackException.newStatic(QpackDecoder.class, "getIndexedName(...)", "QPACK - illegal index value");

    private static final QpackException UNKNOWN_TYPE =
            QpackException.newStatic(QpackDecoder.class, "decode(...)", "QPACK - unknown type");

    private final QpackHuffmanDecoder huffmanDecoder = new QpackHuffmanDecoder();

    /**
     * Decode the header block and add these to the {@link BiConsumer}.
     * This method assumes the entire header block is contained in {@code in}.
     */
    public void decode(ByteBuf in, BiConsumer<CharSequence, CharSequence> sink) throws QpackException {
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
            } else {
                throw UNKNOWN_TYPE;
            }
        }
    }

    private static long decodePrefixedInteger(ByteBuf in, int prefixLength) {
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

    private static void decodeIndexed(ByteBuf in, BiConsumer<CharSequence, CharSequence> sink) throws QpackException {
        if ((in.getByte(in.readerIndex()) & 0x40) == 0x40) {
            final int staticIndex = (int) decodePrefixedInteger(in, 6);
            final QpackHeaderField field = getIndexedHeader(staticIndex);
            sink.accept(field.name, field.value);
        } else {
            throw new UnsupportedOperationException("dynamic table is not implemented");
        }
    }

    private void decodeLiteralWithNameReference(ByteBuf in, BiConsumer<CharSequence, CharSequence> sink)
            throws QpackException {
        if ((in.getByte(in.readerIndex()) & 0x10) == 0x10) {
            final int staticNameIndex = (int) decodePrefixedInteger(in, 4);
            final CharSequence name = getIndexedName(staticNameIndex);
            final CharSequence value = decodePrefixedStringLiteral(in);
            sink.accept(name, value);
        } else {
            throw new UnsupportedOperationException("dynamic table is not implemented");
        }
    }

    private void decodeLiteral(ByteBuf in, BiConsumer<CharSequence, CharSequence> sink) throws QpackException {
        final CharSequence name = decodePrefixedStringLiteral(in, (byte) 0x8, 3);
        final CharSequence value = decodePrefixedStringLiteral(in);
        sink.accept(name, value);
    }

    private CharSequence decodePrefixedStringLiteral(ByteBuf in) throws QpackException {
        return decodePrefixedStringLiteral(in, (byte) 0x80, 7);
    }

    private CharSequence decodePrefixedStringLiteral(ByteBuf in, byte mask, int prefix) throws QpackException {
        final boolean huffmanEncoded = (in.getByte(in.readerIndex()) & mask) == mask;
        final int length = (int) decodePrefixedInteger(in, prefix);
        return decodeStringLiteral(in, length, huffmanEncoded);
    }

    private CharSequence decodeStringLiteral(ByteBuf in, int length, boolean huffmanEncoded) throws QpackException {
        if (huffmanEncoded) {
            return huffmanDecoder.decode(in, length);
        }
        byte[] buf = new byte[length];
        in.readBytes(buf);
        return new AsciiString(buf, false);
    }

    private static CharSequence getIndexedName(int index) throws QpackException {
        if (index <= QpackStaticTable.length) {
            final QpackHeaderField field = QpackStaticTable.getField(index);
            return field.name;
        }
        throw NAME_ILLEGAL_INDEX_VALUE;
    }

    private static QpackHeaderField getIndexedHeader(int index) throws QpackException {
        if (index <= QpackStaticTable.length) {
            return QpackStaticTable.getField(index);
        }
        throw HEADER_ILLEGAL_INDEX_VALUE;
    }
}
