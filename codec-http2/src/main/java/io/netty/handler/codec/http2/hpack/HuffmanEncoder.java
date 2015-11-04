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
package io.netty.handler.codec.http2.hpack;

import java.io.IOException;
import java.io.OutputStream;

final class HuffmanEncoder {

    private final int[] codes;
    private final byte[] lengths;

    /**
     * Creates a new Huffman encoder with the specified Huffman coding.
     *
     * @param codes the Huffman codes indexed by symbol
     * @param lengths the length of each Huffman code
     */
    HuffmanEncoder(int[] codes, byte[] lengths) {
        this.codes = codes;
        this.lengths = lengths;
    }

    /**
     * Compresses the input string literal using the Huffman coding.
     *
     * @param out the output stream for the compressed data
     * @param data the string literal to be Huffman encoded
     * @throws IOException if an I/O error occurs.
     * @see HuffmanEncoder#encode(OutputStream, byte[], int, int)
     */
    public void encode(OutputStream out, byte[] data) throws IOException {
        encode(out, data, 0, data.length);
    }

    /**
     * Compresses the input string literal using the Huffman coding.
     *
     * @param out the output stream for the compressed data
     * @param data the string literal to be Huffman encoded
     * @param off the start offset in the data
     * @param len the number of bytes to encode
     * @throws IOException if an I/O error occurs. In particular, an <code>IOException</code> may be
     * thrown if the output stream has been closed.
     */
    public void encode(OutputStream out, byte[] data, int off, int len) throws IOException {
        if (out == null) {
            throw new NullPointerException("out");
        } else if (data == null) {
            throw new NullPointerException("data");
        } else if (off < 0 || len < 0 || (off + len) < 0 || off > data.length ||
                (off + len) > data.length) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return;
        }

        long current = 0;
        int n = 0;

        for (int i = 0; i < len; i++) {
            int b = data[off + i] & 0xFF;
            int code = codes[b];
            int nbits = lengths[b];

            current <<= nbits;
            current |= code;
            n += nbits;

            while (n >= 8) {
                n -= 8;
                out.write((int) (current >> n));
            }
        }

        if (n > 0) {
            current <<= 8 - n;
            current |= 0xFF >>> n; // this should be EOS symbol
            out.write((int) current);
        }
    }

    /**
     * Returns the number of bytes required to Huffman encode the input string literal.
     *
     * @param data the string literal to be Huffman encoded
     * @return the number of bytes required to Huffman encode <code>data</code>
     */
    public int getEncodedLength(byte[] data) {
        if (data == null) {
            throw new NullPointerException("data");
        }
        long len = 0;
        for (byte b : data) {
            len += lengths[b & 0xFF];
        }
        return (int) ((len + 7) >> 3);
    }
}
