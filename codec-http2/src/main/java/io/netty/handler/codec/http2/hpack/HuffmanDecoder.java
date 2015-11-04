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

import java.io.ByteArrayOutputStream;
import java.io.IOException;

final class HuffmanDecoder {

    private static final IOException EOS_DECODED = new IOException("EOS Decoded");
    private static final IOException INVALID_PADDING = new IOException("Invalid Padding");

    private final Node root;

    /**
     * Creates a new Huffman decoder with the specified Huffman coding.
     *
     * @param codes the Huffman codes indexed by symbol
     * @param lengths the length of each Huffman code
     */
    HuffmanDecoder(int[] codes, byte[] lengths) {
        if (codes.length != 257 || codes.length != lengths.length) {
            throw new IllegalArgumentException("invalid Huffman coding");
        }
        root = buildTree(codes, lengths);
    }

    /**
     * Decompresses the given Huffman coded string literal.
     *
     * @param buf the string literal to be decoded
     * @return the output stream for the compressed data
     * @throws IOException if an I/O error occurs. In particular, an <code>IOException</code> may be
     * thrown if the output stream has been closed.
     */
    public byte[] decode(byte[] buf) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        Node node = root;
        int current = 0;
        int bits = 0;
        for (int i = 0; i < buf.length; i++) {
            int b = buf[i] & 0xFF;
            current = (current << 8) | b;
            bits += 8;
            while (bits >= 8) {
                int c = (current >>> (bits - 8)) & 0xFF;
                node = node.children[c];
                bits -= node.bits;
                if (node.isTerminal()) {
                    if (node.symbol == HpackUtil.HUFFMAN_EOS) {
                        throw EOS_DECODED;
                    }
                    baos.write(node.symbol);
                    node = root;
                }
            }
        }

        while (bits > 0) {
            int c = (current << (8 - bits)) & 0xFF;
            node = node.children[c];
            if (node.isTerminal() && node.bits <= bits) {
                bits -= node.bits;
                baos.write(node.symbol);
                node = root;
            } else {
                break;
            }
        }

        // Section 5.2. String Literal Representation
        // Padding not corresponding to the most significant bits of the code
        // for the EOS symbol (0xFF) MUST be treated as a decoding error.
        int mask = (1 << bits) - 1;
        if ((current & mask) != mask) {
            throw INVALID_PADDING;
        }

        return baos.toByteArray();
    }

    private static final class Node {

        private final int symbol;      // terminal nodes have a symbol
        private final int bits;        // number of bits matched by the node
        private final Node[] children; // internal nodes have children

        /**
         * Construct an internal node
         */
        private Node() {
            symbol = 0;
            bits = 8;
            children = new Node[256];
        }

        /**
         * Construct a terminal node
         *
         * @param symbol the symbol the node represents
         * @param bits the number of bits matched by this node
         */
        private Node(int symbol, int bits) {
            assert bits > 0 && bits <= 8;
            this.symbol = symbol;
            this.bits = bits;
            children = null;
        }

        private boolean isTerminal() {
            return children == null;
        }
    }

    private static Node buildTree(int[] codes, byte[] lengths) {
        Node root = new Node();
        for (int i = 0; i < codes.length; i++) {
            insert(root, i, codes[i], lengths[i]);
        }
        return root;
    }

    private static void insert(Node root, int symbol, int code, byte length) {
        // traverse tree using the most significant bytes of code
        Node current = root;
        while (length > 8) {
            if (current.isTerminal()) {
                throw new IllegalStateException("invalid Huffman code: prefix not unique");
            }
            length -= 8;
            int i = (code >>> length) & 0xFF;
            if (current.children[i] == null) {
                current.children[i] = new Node();
            }
            current = current.children[i];
        }

        Node terminal = new Node(symbol, length);
        int shift = 8 - length;
        int start = (code << shift) & 0xFF;
        int end = 1 << shift;
        for (int i = start; i < start + end; i++) {
            current.children[i] = terminal;
        }
    }
}
