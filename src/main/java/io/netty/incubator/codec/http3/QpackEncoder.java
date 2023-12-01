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
import io.netty.buffer.ByteBufAllocator;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.collection.LongObjectHashMap;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Map;
import java.util.Queue;

import static io.netty.incubator.codec.http3.Http3CodecUtils.closeOnFailure;
import static io.netty.incubator.codec.http3.QpackHeaderField.sizeOf;
import static io.netty.incubator.codec.http3.QpackUtil.encodePrefixedInteger;

/**
 * A QPACK encoder.
 */
final class QpackEncoder {
    private static final QpackException INVALID_SECTION_ACKNOWLEDGMENT =
            QpackException.newStatic(QpackDecoder.class, "sectionAcknowledgment(...)",
                    "QPACK - section acknowledgment received for unknown stream.");
    private static final int DYNAMIC_TABLE_ENCODE_NOT_DONE = -1;
    private static final int DYNAMIC_TABLE_ENCODE_NOT_POSSIBLE = -2;

    private final QpackHuffmanEncoder huffmanEncoder;
    private final QpackEncoderDynamicTable dynamicTable;
    private int maxBlockedStreams;
    private int blockedStreams;
    private LongObjectHashMap<Queue<Indices>> streamSectionTrackers;

    QpackEncoder() {
        this(new QpackEncoderDynamicTable());
    }

    QpackEncoder(QpackEncoderDynamicTable dynamicTable) {
        huffmanEncoder = new QpackHuffmanEncoder();
        this.dynamicTable = dynamicTable;
    }

    /**
     * Encode the header field into the header block.
     *
     * TODO: do we need to support sensitivity detector?
     */
    void encodeHeaders(QpackAttributes qpackAttributes, ByteBuf out, ByteBufAllocator allocator, long streamId,
                       Http3Headers headers) {
        final int base = dynamicTable.insertCount();
        // Allocate a new buffer as we have to go back and write a variable length base and required insert count
        // later.
        ByteBuf tmp = allocator.buffer();
        try {
            int maxDynamicTblIdx = -1;
            int requiredInsertCount = 0;
            Indices dynamicTableIndices = null;
            for (Map.Entry<CharSequence, CharSequence> header : headers) {
                CharSequence name = header.getKey();
                CharSequence value = header.getValue();
                int dynamicTblIdx = encodeHeader(qpackAttributes, tmp, base, name, value);
                if (dynamicTblIdx >= 0) {
                    int req = dynamicTable.addReferenceToEntry(name, value, dynamicTblIdx);
                    if (dynamicTblIdx > maxDynamicTblIdx) {
                        maxDynamicTblIdx = dynamicTblIdx;
                        requiredInsertCount = req;
                    }
                    if (dynamicTableIndices == null) {
                        dynamicTableIndices = new Indices();
                    }
                    dynamicTableIndices.add(dynamicTblIdx);
                }
            }

            // Track all the indices that we need to ack later.
            if (dynamicTableIndices != null) {
                assert streamSectionTrackers != null;
                streamSectionTrackers.computeIfAbsent(streamId, __ -> new ArrayDeque<>())
                        .add(dynamicTableIndices);
            }

            // https://www.rfc-editor.org/rfc/rfc9204.html#name-encoded-field-section-prefi
            //   0   1   2   3   4   5   6   7
            // +---+---+---+---+---+---+---+---+
            // |   Required Insert Count (8+)  |
            // +---+---------------------------+
            // | S |      Delta Base (7+)      |
            // +---+---------------------------+
            encodePrefixedInteger(out, (byte) 0b0, 8, dynamicTable.encodedRequiredInsertCount(requiredInsertCount));
            if (base >= requiredInsertCount) {
                encodePrefixedInteger(out, (byte) 0b0, 7, base - requiredInsertCount);
            } else {
                encodePrefixedInteger(out, (byte) 0b1000_0000, 7, requiredInsertCount - base - 1);
            }
            out.writeBytes(tmp);
        } finally {
            tmp.release();
        }
    }

    void configureDynamicTable(QpackAttributes attributes, long maxTableCapacity, int blockedStreams)
            throws QpackException {
        if (maxTableCapacity > 0) {
            assert attributes.encoderStreamAvailable();
            final QuicStreamChannel encoderStream = attributes.encoderStream();
            dynamicTable.maxTableCapacity(maxTableCapacity);
            final ByteBuf tableCapacity = encoderStream.alloc().buffer(8);
            // https://www.rfc-editor.org/rfc/rfc9204.html#name-set-dynamic-table-capacity
            //  0   1   2   3   4   5   6   7
            // +---+---+---+---+---+---+---+---+
            // | 0 | 0 | 1 |   Capacity (5+)   |
            // +---+---+---+-------------------+
            encodePrefixedInteger(tableCapacity, (byte) 0b0010_0000, 5, maxTableCapacity);
            closeOnFailure(encoderStream.writeAndFlush(tableCapacity));

            streamSectionTrackers = new LongObjectHashMap<>();
            maxBlockedStreams = blockedStreams;
        }
    }

    /**
     * <a href="https://quicwg.org/base-drafts/draft-ietf-quic-qpack.html#name-section-acknowledgment">
     *     Section acknowledgment</a> for the passed {@code streamId}.
     *
     * @param streamId For which the header fields section is acknowledged.
     */
    void sectionAcknowledgment(long streamId) throws QpackException {
        assert streamSectionTrackers != null;
        final Queue<Indices> tracker = streamSectionTrackers.get(streamId);
        if (tracker == null) {
            throw INVALID_SECTION_ACKNOWLEDGMENT;
        }

        Indices dynamicTableIndices = tracker.poll();

        if (tracker.isEmpty()) {
            streamSectionTrackers.remove(streamId);
        }
        if (dynamicTableIndices != null) {
            dynamicTableIndices.forEach(dynamicTable::acknowledgeInsertCount);
        }
    }

    /**
     * <a href="https://quicwg.org/base-drafts/draft-ietf-quic-qpack.html#name-stream-cancellation">
     *     Stream cancellation</a> for the passed {@code streamId}.
     *
     * @param streamId which is cancelled.
     */
    void streamCancellation(long streamId) throws QpackException {
        // If a configureDynamicTable(...) was called with a maxTableCapacity of 0 we will have not instanced
        // streamSectionTrackers. The remote peer might still send a stream cancellation for a stream, while it
        // is optional. See https://www.rfc-editor.org/rfc/rfc9204.html#section-2.2.2.2
        if (streamSectionTrackers == null) {
            return;
        }
        final Queue<Indices> tracker = streamSectionTrackers.remove(streamId);
        if (tracker != null) {
            for (;;) {
                Indices dynamicTableIndices = tracker.poll();
                if (dynamicTableIndices == null) {
                    break;
                }
                dynamicTableIndices.forEach(dynamicTable::acknowledgeInsertCount);
            }
        }
    }

    /**
     * <a href="https://quicwg.org/base-drafts/draft-ietf-quic-qpack.html#name-insert-count-increment">
     *     Insert count increment</a>.
     *
     * @param increment for the known received count.
     */
    void insertCountIncrement(int increment) throws QpackException {
        dynamicTable.incrementKnownReceivedCount(increment);
    }

    /**
     * Encode the header field into the header block.
     * @param qpackAttributes {@link QpackAttributes} for the channel.
     * @param out {@link ByteBuf} to which encoded header field is to be written.
     * @param base Base for the dynamic table index.
     * @param name for the header field.
     * @param value for the header field.
     * @return Index in the dynamic table if the header field was encoded as a reference to the dynamic table,
     * {@link #DYNAMIC_TABLE_ENCODE_NOT_DONE } otherwise.
     */
    private int encodeHeader(QpackAttributes qpackAttributes, ByteBuf out, int base, CharSequence name,
                             CharSequence value) {
        int index = QpackStaticTable.findFieldIndex(name, value);
        if (index == QpackStaticTable.NOT_FOUND) {
            if (qpackAttributes.dynamicTableDisabled()) {
                encodeLiteral(out, name, value);
                return DYNAMIC_TABLE_ENCODE_NOT_POSSIBLE;
            }
            return encodeWithDynamicTable(qpackAttributes, out, base, name, value);
        } else if ((index & QpackStaticTable.MASK_NAME_REF) == QpackStaticTable.MASK_NAME_REF) {
            int dynamicTblIdx = tryEncodeWithDynamicTable(qpackAttributes, out, base, name, value);
            if (dynamicTblIdx >= 0) {
                return dynamicTblIdx;
            }
            final int nameIdx = index ^ QpackStaticTable.MASK_NAME_REF;
            dynamicTblIdx = tryAddToDynamicTable(qpackAttributes, true, nameIdx, name, value);
            if (dynamicTblIdx >= 0) {
                if (dynamicTblIdx >= base) {
                    encodePostBaseIndexed(out, base, dynamicTblIdx);
                } else {
                    encodeIndexedDynamicTable(out, base, dynamicTblIdx);
                }
                return dynamicTblIdx;
            }
            encodeLiteralWithNameRefStaticTable(out, nameIdx, value);
        } else {
            encodeIndexedStaticTable(out, index);
        }
        return qpackAttributes.dynamicTableDisabled() ? DYNAMIC_TABLE_ENCODE_NOT_POSSIBLE :
                DYNAMIC_TABLE_ENCODE_NOT_DONE;
    }

    /**
     * Encode the header field using dynamic table, if possible.
     *
     * @param qpackAttributes {@link QpackAttributes} for the channel.
     * @param out {@link ByteBuf} to which encoded header field is to be written.
     * @param base Base for the dynamic table index.
     * @param name for the header field.
     * @param value for the header field.
     * @return Index in the dynamic table if the header field was encoded as a reference to the dynamic table,
     * {@link #DYNAMIC_TABLE_ENCODE_NOT_DONE } otherwise.
     */
    private int encodeWithDynamicTable(QpackAttributes qpackAttributes, ByteBuf out, int base, CharSequence name,
                                       CharSequence value) {
        int idx = tryEncodeWithDynamicTable(qpackAttributes, out, base, name, value);
        if (idx >= 0) {
            return idx;
        }

        if (idx == DYNAMIC_TABLE_ENCODE_NOT_DONE) {
            idx = tryAddToDynamicTable(qpackAttributes, false, -1, name, value);
            if (idx >= 0) {
                if (idx >= base) {
                    encodePostBaseIndexed(out, base, idx);
                } else {
                    encodeIndexedDynamicTable(out, base, idx);
                }
                return idx;
            }
        }
        encodeLiteral(out, name, value);
        return idx;
    }

    /**
     * Try to encode the header field using dynamic table, otherwise do not encode.
     *
     * @param qpackAttributes {@link QpackAttributes} for the channel.
     * @param out {@link ByteBuf} to which encoded header field is to be written.
     * @param base Base for the dynamic table index.
     * @param name for the header field.
     * @param value for the header field.
     * @return Index in the dynamic table if the header field was encoded as a reference to the dynamic table.
     * {@link #DYNAMIC_TABLE_ENCODE_NOT_DONE } if encoding was not done. {@link #DYNAMIC_TABLE_ENCODE_NOT_POSSIBLE }
     * if dynamic table encoding is not possible (size constraint) and hence should not be tried for this header.
     */
    private int tryEncodeWithDynamicTable(QpackAttributes qpackAttributes, ByteBuf out, int base, CharSequence name,
                                          CharSequence value) {
        if (qpackAttributes.dynamicTableDisabled()) {
            return DYNAMIC_TABLE_ENCODE_NOT_POSSIBLE;
        }
        assert qpackAttributes.encoderStreamAvailable();
        final QuicStreamChannel encoderStream = qpackAttributes.encoderStream();

        int idx = dynamicTable.getEntryIndex(name, value);
        if (idx == QpackEncoderDynamicTable.NOT_FOUND) {
            return DYNAMIC_TABLE_ENCODE_NOT_DONE;
        }
        if (idx >= 0) {
            if (dynamicTable.requiresDuplication(idx, sizeOf(name, value))) {
                idx = dynamicTable.add(name, value, sizeOf(name, value));
                assert idx >= 0;
                // https://www.rfc-editor.org/rfc/rfc9204.html#section-4.3.4
                //  0   1   2   3   4   5   6   7
                // +---+---+---+---+---+---+---+---+
                // | 0 | 0 | 0 |    Index (5+)     |
                // +---+---+---+-------------------+
                ByteBuf duplicate = encoderStream.alloc().buffer(8);
                encodePrefixedInteger(duplicate, (byte) 0b0000_0000, 5,
                        dynamicTable.relativeIndexForEncoderInstructions(idx));
                closeOnFailure(encoderStream.writeAndFlush(duplicate));
                if (mayNotBlockStream()) {
                    // Add to the table but do not use the entry in the header block to avoid blocking.
                    return DYNAMIC_TABLE_ENCODE_NOT_POSSIBLE;
                }
            }
            if (idx >= base) {
                encodePostBaseIndexed(out, base, idx);
            } else {
                encodeIndexedDynamicTable(out, base, idx);
            }
        } else { // name match
            idx = -(idx + 1);
            int addIdx = tryAddToDynamicTable(qpackAttributes, false,
                    dynamicTable.relativeIndexForEncoderInstructions(idx), name, value);
            if (addIdx < 0) {
                return DYNAMIC_TABLE_ENCODE_NOT_POSSIBLE;
            }
            idx = addIdx;

            if (idx >= base) {
                encodeLiteralWithPostBaseNameRef(out, base, idx, value);
            } else {
                encodeLiteralWithNameRefDynamicTable(out, base, idx, value);
            }
        }
        return idx;
    }

    /**
     * Try adding the header field to the dynamic table.
     *
     * @param qpackAttributes {@link QpackAttributes} for the channel.
     * @param staticTableNameRef if {@code nameIdx} is an index in the static table.
     * @param nameIdx Index of the name if {@code > 0}.
     * @param name for the header field.
     * @param value for the header field.
     * @return Index in the dynamic table if the header field was encoded as a reference to the dynamic table,
     * {@link #DYNAMIC_TABLE_ENCODE_NOT_DONE} otherwise.
     */
    private int tryAddToDynamicTable(QpackAttributes qpackAttributes, boolean staticTableNameRef, int nameIdx,
                                     CharSequence name, CharSequence value) {
        if (qpackAttributes.dynamicTableDisabled()) {
            return DYNAMIC_TABLE_ENCODE_NOT_POSSIBLE;
        }
        assert qpackAttributes.encoderStreamAvailable();
        final QuicStreamChannel encoderStream = qpackAttributes.encoderStream();

        int idx = dynamicTable.add(name, value, sizeOf(name, value));
        if (idx >= 0) {
            ByteBuf insert = null;
            try {
                if (nameIdx >= 0) {
                    // 2 prefixed integers (name index and value length) each requires a maximum of 8 bytes
                    insert = encoderStream.alloc().buffer(value.length() + 16);
                    // https://www.rfc-editor.org/rfc/rfc9204.html#name-insert-with-name-reference
                    //    0   1   2   3   4   5   6   7
                    // +---+---+---+---+---+---+---+---+
                    // | 1 | T |    Name Index (6+)    |
                    // +---+---+-----------------------+
                    encodePrefixedInteger(insert, (byte) (staticTableNameRef ? 0b1100_0000 : 0b1000_0000), 6, nameIdx);
                } else {
                    // 2 prefixed integers (name and value length) each requires a maximum of 8 bytes
                    insert = encoderStream.alloc().buffer(name.length() + value.length() + 16);
                    // https://www.rfc-editor.org/rfc/rfc9204.html#name-insert-with-literal-name
                    //     0   1   2   3   4   5   6   7
                    //   +---+---+---+---+---+---+---+---+
                    //   | 0 | 1 | H | Name Length (5+)  |
                    //   +---+---+---+-------------------+
                    //   |  Name String (Length bytes)   |
                    //   +---+---------------------------+
                    // TODO: Force H = 1 till we support sensitivity detector
                    encodeLengthPrefixedHuffmanEncodedLiteral(insert, (byte) 0b0110_0000, 5, name);
                }
                //    0   1   2   3   4   5   6   7
                // +---+---+-----------------------+
                // | H |     Value Length (7+)     |
                // +---+---------------------------+
                // |  Value String (Length bytes)  |
                // +-------------------------------+
                encodeStringLiteral(insert, value);
            } catch (Exception e) {
                ReferenceCountUtil.release(insert);
                return DYNAMIC_TABLE_ENCODE_NOT_DONE;
            }
            closeOnFailure(encoderStream.writeAndFlush(insert));
            if (mayNotBlockStream()) {
                // Add to the table but do not use the entry in the header block to avoid blocking.
                return DYNAMIC_TABLE_ENCODE_NOT_DONE;
            }
            blockedStreams++;
        }
        return idx;
    }

    private void encodeIndexedStaticTable(ByteBuf out, int index) {
        // https://www.rfc-editor.org/rfc/rfc9204.html#name-indexed-field-line
        //   0   1   2   3   4   5   6   7
        // +---+---+---+---+---+---+---+---+
        // | 1 | T |      Index (6+)       |
        // +---+---+-----------------------+
        encodePrefixedInteger(out, (byte) 0b1100_0000, 6, index);
    }

    private void encodeIndexedDynamicTable(ByteBuf out, int base, int index) {
        // https://www.rfc-editor.org/rfc/rfc9204.html#name-indexed-field-line
        //   0   1   2   3   4   5   6   7
        // +---+---+---+---+---+---+---+---+
        // | 1 | T |      Index (6+)       |
        // +---+---+-----------------------+
        encodePrefixedInteger(out, (byte) 0b1000_0000, 6, base - index - 1);
    }

    private void encodePostBaseIndexed(ByteBuf out, int base, int index) {
        // https://www.rfc-editor.org/rfc/rfc9204.html#name-indexed-field-line-with-pos
        //   0   1   2   3   4   5   6   7
        // +---+---+---+---+---+---+---+---+
        // | 0 | 0 | 0 | 1 |  Index (4+)   |
        // +---+---+---+---+---------------+
        encodePrefixedInteger(out, (byte) 0b0001_0000, 4, index - base);
    }

    private void encodeLiteralWithNameRefStaticTable(ByteBuf out, int nameIndex, CharSequence value) {
        // https://www.rfc-editor.org/rfc/rfc9204.html#name-literal-field-line-with-nam
        //     0   1   2   3   4   5   6   7
        //   +---+---+---+---+---+---+---+---+
        //   | 0 | 1 | N | T |Name Index (4+)|
        //   +---+---+---+---+---------------+
        //   | H |     Value Length (7+)     |
        //   +---+---------------------------+
        //   |  Value String (Length bytes)  |
        //   +-------------------------------+
        // TODO: Force N = 0 till we support sensitivity detector
        encodePrefixedInteger(out, (byte) 0b0101_0000, 4, nameIndex);
        encodeStringLiteral(out, value);
    }

    private void encodeLiteralWithNameRefDynamicTable(ByteBuf out, int base, int nameIndex, CharSequence value) {
        // https://www.rfc-editor.org/rfc/rfc9204.html#name-literal-field-line-with-nam
        //     0   1   2   3   4   5   6   7
        //   +---+---+---+---+---+---+---+---+
        //   | 0 | 1 | N | T |Name Index (4+)|
        //   +---+---+---+---+---------------+
        //   | H |     Value Length (7+)     |
        //   +---+---------------------------+
        //   |  Value String (Length bytes)  |
        //   +-------------------------------+
        // TODO: Force N = 0 till we support sensitivity detector
        encodePrefixedInteger(out, (byte) 0b0101_0000, 4, base - nameIndex - 1);
        encodeStringLiteral(out, value);
    }

    private void encodeLiteralWithPostBaseNameRef(ByteBuf out, int base, int nameIndex, CharSequence value) {
        // https://www.rfc-editor.org/rfc/rfc9204.html#name-literal-field-line-with-pos
        //    0   1   2   3   4   5   6   7
        //   +---+---+---+---+---+---+---+---+
        //   | 0 | 0 | 0 | 0 | N |NameIdx(3+)|
        //   +---+---+---+---+---+-----------+
        //   | H |     Value Length (7+)     |
        //   +---+---------------------------+
        //   |  Value String (Length bytes)  |
        //   +-------------------------------+
        // TODO: Force N = 0 till we support sensitivity detector
        encodePrefixedInteger(out, (byte) 0b0000_0000, 4, nameIndex - base);
        encodeStringLiteral(out, value);
    }

    private void encodeLiteral(ByteBuf out, CharSequence name, CharSequence value) {
        // https://www.rfc-editor.org/rfc/rfc9204.html#name-literal-field-line-with-lit
        //   0   1   2   3   4   5   6   7
        //   +---+---+---+---+---+---+---+---+
        //   | 0 | 0 | 1 | N | H |NameLen(3+)|
        //   +---+---+---+---+---+-----------+
        //   |  Name String (Length bytes)   |
        //   +---+---------------------------+
        //   | H |     Value Length (7+)     |
        //   +---+---------------------------+
        //   |  Value String (Length bytes)  |
        //   +-------------------------------+
        // TODO: Force N = 0 & H = 1 till we support sensitivity detector
        encodeLengthPrefixedHuffmanEncodedLiteral(out, (byte) 0b0010_1000, 3, name);
        encodeStringLiteral(out, value);
    }

    /**
     * Encode string literal according to Section 5.2.
     * <a href="https://tools.ietf.org/html/rfc7541#section-5.2">Section 5.2</a>.
     */
    private void encodeStringLiteral(ByteBuf out, CharSequence value) {
        //    0   1   2   3   4   5   6   7
        // +---+---+---+---+---+---+---+---+
        // | H |    String Length (7+)     |
        // +---+---------------------------+
        // |  String Data (Length octets)  |
        // +-------------------------------+
        // TODO: Force H = 1 till we support sensitivity detector
        encodeLengthPrefixedHuffmanEncodedLiteral(out, (byte) 0b1000_0000, 7, value);
    }

    /**
     * Encode a string literal.
     */
    private void encodeLengthPrefixedHuffmanEncodedLiteral(ByteBuf out, byte mask, int prefix, CharSequence value) {
        int huffmanLength = huffmanEncoder.getEncodedLength(value);
        encodePrefixedInteger(out, mask, prefix, huffmanLength);
        huffmanEncoder.encode(out, value);
    }

    private boolean mayNotBlockStream() {
        return blockedStreams >= maxBlockedStreams - 1;
    }

    private static final class Indices {
        private int idx;
        // Let's just assume 4 indices for now that we will store here as max.
        private int[] array = new int[4];

        void add(int index) {
            if (idx == array.length) {
                // Double it if needed.
                array = Arrays.copyOf(array, array.length << 1);
            }
            array[idx++] = index;
        }

        void forEach(IndexConsumer consumer) throws QpackException {
            for (int i = 0; i < idx; i++) {
                consumer.accept(array[i]);
            }
        }

        @FunctionalInterface
        interface IndexConsumer {
            void accept(int idx) throws QpackException;
        }
    }
}
