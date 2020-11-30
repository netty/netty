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

import java.util.Map;

import io.netty.buffer.ByteBuf;

/**
 * A QPACK encoder.
 */
final class QpackEncoder {

    private final QpackHuffmanEncoder huffmanEncoder = new QpackHuffmanEncoder();

    /**
     * Creates a new encoder.
     */
    QpackEncoder() {
    }

    /**
     * Encode the header field into the header block.
     *
     * TODO: do we need to support sensitivity detector?
     */
    public void encodeHeaders(ByteBuf out, Http3Headers headers) {
        // Required Insert Count
        // https://tools.ietf.org/html/draft-ietf-quic-qpack-19#section-4.5.1.1
        encodePrefixedInteger(out, (byte) 0x00, 8, 0);

        // Delta Base
        // https://tools.ietf.org/html/draft-ietf-quic-qpack-19#section-4.5.1.2
        encodePrefixedInteger(out, (byte) 0x00, 7, 0);

        for (Map.Entry<CharSequence, CharSequence> header : headers) {
            CharSequence name = header.getKey();
            CharSequence value = header.getValue();
            encodeHeader(out, name, value);
        }
    }

    /**
     * Encode the header field into the header block.
     *
     * TODO: implement dynamic table
     */
    private void encodeHeader(ByteBuf out, CharSequence name, CharSequence value) {
        int index = QpackStaticTable.findFieldIndex(name, value);
        if (index == QpackStaticTable.NOT_FOUND) {
            encodeLiteral(out, name, value);
        } else if ((index & QpackStaticTable.MASK_NAME_REF) == QpackStaticTable.MASK_NAME_REF) {
            encodeLiteralWithNameRef(out, name, value, index ^ QpackStaticTable.MASK_NAME_REF);
        } else {
            encodeIndexed(out, index);
        }
        return;
    }

    private void encodeIndexed(ByteBuf out, int index) {
        // TODO: mask will be different for static & dynamic tables
        // 1Txxxxxx pattern, forcing T to 1
        encodePrefixedInteger(out, (byte) 0xc0, 6, index);
    }

    private void encodeLiteralWithNameRef(ByteBuf out, CharSequence name, CharSequence value, int nameIndex) {
        // TODO: mask will be different for static & dynamic tables
        // 01NTxxxx pattern, forcing N to 0 and T to 1
        encodePrefixedInteger(out, (byte) 0x50, 4, nameIndex);
        encodeStringLiteral(out, value);
    }

    private void encodeLiteral(ByteBuf out, CharSequence name, CharSequence value) {
        encodeStringLiteral(out, (byte) (0x20 | 0x8), 3, name);
        encodeStringLiteral(out, value);
    }

    /**
     * Encode string literal according to Section 5.2.
     * <a href="https://tools.ietf.org/html/rfc7541#section-5.2">Section 5.2</a>.
     */
    private void encodeStringLiteral(ByteBuf out, CharSequence value) {
        encodeStringLiteral(out, (byte) 0x80, 7, value);
    }

    /**
     * Encode string literal according to Section 5.2.
     * <a href="https://tools.ietf.org/html/rfc7541#section-5.2">Section 5.2</a>.
     */
    private void encodeStringLiteral(ByteBuf out, byte mask, int prefix, CharSequence value) {
        int huffmanLength = huffmanEncoder.getEncodedLength(value);
        encodePrefixedInteger(out, mask, prefix, huffmanLength);
        huffmanEncoder.encode(out, value);
    }

    /**
     * Encode integer according to
     * <a href="https://tools.ietf.org/html/rfc7541#section-5.1">Section 5.1</a>.
     */
    private static void encodePrefixedInteger(ByteBuf out, byte mask, int prefixLength, int i) {
        int nbits = (1 << prefixLength) - 1;
        if (i < nbits) {
            out.writeByte((byte) (mask | i));
        } else {
            out.writeByte((byte) (mask | nbits));
            int remainder = i - nbits;
            while (remainder > 128) {
                byte next = (byte) ((remainder % 128) | 0x80);
                out.writeByte(next);
                remainder = remainder / 128;
            }
            out.writeByte((byte) remainder);
        }
    }
}
