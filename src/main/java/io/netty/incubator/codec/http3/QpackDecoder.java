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
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.util.AsciiString;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import static io.netty.incubator.codec.http3.Http3CodecUtils.closeOnFailure;
import static io.netty.incubator.codec.http3.Http3SettingsFrame.HTTP3_SETTINGS_QPACK_BLOCKED_STREAMS;
import static io.netty.incubator.codec.http3.Http3SettingsFrame.HTTP3_SETTINGS_QPACK_MAX_TABLE_CAPACITY;
import static io.netty.incubator.codec.http3.QpackDecoderStateSyncStrategy.ackEachInsert;
import static io.netty.incubator.codec.http3.QpackUtil.decodePrefixedIntegerAsInt;
import static io.netty.incubator.codec.http3.QpackUtil.encodePrefixedInteger;
import static io.netty.incubator.codec.http3.QpackUtil.firstByteEquals;
import static io.netty.incubator.codec.http3.QpackUtil.toIntOrThrow;
import static java.lang.Math.floorDiv;
import static java.lang.Math.toIntExact;

final class QpackDecoder {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(QpackDecoder.class);
    private static final QpackException DYNAMIC_TABLE_CAPACITY_EXCEEDS_MAX =
            QpackException.newStatic(QpackDecoder.class, "setDynamicTableCapacity(...)",
                    "QPACK - decoder dynamic table capacity exceeds max capacity.");
    private static final QpackException HEADER_ILLEGAL_INDEX_VALUE =
            QpackException.newStatic(QpackDecoder.class, "decodeIndexed(...)", "QPACK - illegal index value");
    private static final QpackException NAME_ILLEGAL_INDEX_VALUE =
            QpackException.newStatic(QpackDecoder.class, "decodeLiteralWithNameRef(...)",
                    "QPACK - illegal name index value");
    private static final QpackException INVALID_REQUIRED_INSERT_COUNT =
            QpackException.newStatic(QpackDecoder.class, "decodeRequiredInsertCount(...)",
                    "QPACK - invalid required insert count");
    private static final QpackException MAX_BLOCKED_STREAMS_EXCEEDED =
            QpackException.newStatic(QpackDecoder.class, "shouldWaitForDynamicTableUpdates(...)",
                    "QPACK - exceeded max blocked streams");
    private static final QpackException BLOCKED_STREAM_RESUMPTION_FAILED =
            QpackException.newStatic(QpackDecoder.class, "sendInsertCountIncrementIfRequired(...)",
                    "QPACK - failed to resume a blocked stream");

    private static final QpackException UNKNOWN_TYPE =
            QpackException.newStatic(QpackDecoder.class, "decode(...)", "QPACK - unknown type");

    private final QpackHuffmanDecoder huffmanDecoder;
    private final QpackDecoderDynamicTable dynamicTable;
    private final long maxTableCapacity;
    private final int maxBlockedStreams;
    private final QpackDecoderStateSyncStrategy stateSyncStrategy;
    /**
     * Hashmap with key as the required insert count to unblock the stream and the value a {@link List} of
     * {@link Runnable} to invoke when the stream can be unblocked.
     */
    private final IntObjectHashMap<List<Runnable>> blockedStreams;

    private long maxEntries;
    private long fullRange;
    private int blockedStreamsCount;
    private long lastAckInsertCount;

    QpackDecoder(Http3SettingsFrame localSettings) {
        this(localSettings, new QpackDecoderDynamicTable(), ackEachInsert());
    }

    QpackDecoder(Http3SettingsFrame localSettings,
                 QpackDecoderDynamicTable dynamicTable, QpackDecoderStateSyncStrategy stateSyncStrategy) {
        huffmanDecoder = new QpackHuffmanDecoder();
        maxTableCapacity = localSettings.getOrDefault(HTTP3_SETTINGS_QPACK_MAX_TABLE_CAPACITY, 0);
        maxBlockedStreams = toIntExact(localSettings.getOrDefault(HTTP3_SETTINGS_QPACK_BLOCKED_STREAMS, 0));
        this.stateSyncStrategy = stateSyncStrategy;
        blockedStreams = new IntObjectHashMap<>(Math.min(16, maxBlockedStreams));
        this.dynamicTable = dynamicTable;
    }

    /**
     * Decode the header block and add these to the {@link BiConsumer}. This method assumes the entire header block is
     * contained in {@code in}. However, this method may not be able to decode the header block if the QPACK dynamic
     * table does not contain all entries required to decode the header block.
     * See <a href="https://quicwg.org/base-drafts/draft-ietf-quic-qpack.html#name-blocked-streams">blocked streams</a>.
     * In such a case, this method will return {@code false} and would invoke {@code whenDecoded} when the stream is
     * unblocked and the header block is completely decoded.
     *
     * @param qpackAttributes {@link QpackAttributes} for the channel.
     * @param streamId for the stream on which this header block was received.
     * @param in {@link ByteBuf} containing the header block.
     * @param length Number of bytes to be read from {@code in}
     * @param sink {@link BiConsumer} to
     * @param whenDecoded {@link Runnable} to invoke when a blocked decode finishes decoding.
     * @return {@code true} if the headers were decoded.
     */
    public boolean decode(QpackAttributes qpackAttributes, long streamId, ByteBuf in,
                          int length, BiConsumer<CharSequence, CharSequence> sink, Runnable whenDecoded)
            throws QpackException {
        final int initialReaderIdx = in.readerIndex();
        final int requiredInsertCount = decodeRequiredInsertCount(qpackAttributes, in);
        if (shouldWaitForDynamicTableUpdates(requiredInsertCount)) {
            blockedStreamsCount++;
            blockedStreams.computeIfAbsent(requiredInsertCount, __ -> new ArrayList<>(2)).add(whenDecoded);
            in.readerIndex(initialReaderIdx);
            return false;
        }

        in = in.readSlice(length - (in.readerIndex() - initialReaderIdx));
        final int base = decodeBase(in, requiredInsertCount);

        while (in.isReadable()) {
            byte b = in.getByte(in.readerIndex());
            if (isIndexed(b)) {
                decodeIndexed(in, sink, base);
            } else if (isIndexedWithPostBase(b)) {
                decodeIndexedWithPostBase(in, sink, base);
            } else if (isLiteralWithNameRef(b)) {
                decodeLiteralWithNameRef(in, sink, base);
            } else if (isLiteralWithPostBaseNameRef(b)) {
                decodeLiteralWithPostBaseNameRef(in, sink, base);
            } else if (isLiteral(b)) {
                decodeLiteral(in, sink);
            } else {
                throw UNKNOWN_TYPE;
            }
        }
        if (requiredInsertCount > 0) {
            assert !qpackAttributes.dynamicTableDisabled();
            assert qpackAttributes.decoderStreamAvailable();

            stateSyncStrategy.sectionAcknowledged(requiredInsertCount);
            final ByteBuf sectionAck = qpackAttributes.decoderStream().alloc().buffer(8);
            encodePrefixedInteger(sectionAck, (byte) 0b1000_0000, 7, streamId);
            closeOnFailure(qpackAttributes.decoderStream().writeAndFlush(sectionAck));
        }
        return true;
    }

    /**
     * Updates dynamic table capacity corresponding to the
     * <a href="https://quicwg.org/base-drafts/draft-ietf-quic-qpack.html#name-set-dynamic-table-capacity">
     *     encoder instruction.</a>
     *
     * @param capacity New capacity.
     * @throws  QpackException If the capacity update fails.
     */
    void setDynamicTableCapacity(long capacity) throws QpackException {
        if (capacity > maxTableCapacity) {
            throw DYNAMIC_TABLE_CAPACITY_EXCEEDS_MAX;
        }
        dynamicTable.setCapacity(capacity);
        maxEntries = floorDiv(capacity, 32);
        fullRange = toIntOrThrow(2 * maxEntries);
    }

    /**
     * Inserts a header field with a name reference corresponding to the
     * <a href="https://quicwg.org/base-drafts/draft-ietf-quic-qpack.html#name-insert-with-name-reference">
     *     encoder instruction.</a>
     *
     *  @param qpackDecoderStream {@link QuicStreamChannel} for the QPACK decoder stream.
     *  @param staticTableRef {@code true} if the name reference is to the static table, {@code false} if the reference
     * is to the dynamic table.
     * @param nameIdx Index of the name in the table.
     * @param value Literal value.
     * @throws QpackException if the insertion fails.
     */
    void insertWithNameReference(QuicStreamChannel qpackDecoderStream, boolean staticTableRef, int nameIdx,
                                 CharSequence value) throws QpackException {
        final QpackHeaderField entryForName;
        if (staticTableRef) {
            entryForName = QpackStaticTable.getField(nameIdx);
        } else {
            entryForName = dynamicTable.getEntryRelativeEncoderInstructions(nameIdx);
        }
        dynamicTable.add(new QpackHeaderField(entryForName.name, value));
        sendInsertCountIncrementIfRequired(qpackDecoderStream);
    }

    /**
     * Inserts a header field with a literal name corresponding to the
     * <a href="https://quicwg.org/base-drafts/draft-ietf-quic-qpack.html#name-insert-with-literal-name">
     *     encoder instruction.</a>
     *
     * @param qpackDecoderStream {@link QuicStreamChannel} for the QPACK decoder stream.
     * @param name of the field.
     * @param value of the field.
     * @throws QpackException if the insertion fails.
     */
    void insertLiteral(QuicStreamChannel qpackDecoderStream, CharSequence name, CharSequence value)
            throws QpackException {
        dynamicTable.add(new QpackHeaderField(name, value));
        sendInsertCountIncrementIfRequired(qpackDecoderStream);
    }

    /**
     * Duplicates a previous entry corresponding to the
     * <a href="https://quicwg.org/base-drafts/draft-ietf-quic-qpack.html#name-insert-with-literal-name">
     *     encoder instruction.</a>
     *
     * @param qpackDecoderStream {@link QuicStreamChannel} for the QPACK decoder stream.
     * @param index which is duplicated.
     * @throws QpackException if duplication fails.
     */
    void duplicate(QuicStreamChannel qpackDecoderStream, int index)
            throws QpackException {
        dynamicTable.add(dynamicTable.getEntryRelativeEncoderInstructions(index));
        sendInsertCountIncrementIfRequired(qpackDecoderStream);
    }

    /**
     * Callback when a bi-directional stream is
     * <a href="https://quicwg.org/base-drafts/draft-ietf-quic-qpack.html#name-abandonment-of-a-stream"> abandoned</a>
     *
     * @param qpackDecoderStream {@link QuicStreamChannel} for the QPACK decoder stream.
     * @param streamId which is abandoned.
     */
    void streamAbandoned(QuicStreamChannel qpackDecoderStream, long streamId) {
        if (maxTableCapacity == 0) {
            return;
        }
        // https://quicwg.org/base-drafts/draft-ietf-quic-qpack.html#name-stream-cancellation
        //   0   1   2   3   4   5   6   7
        // +---+---+---+---+---+---+---+---+
        // | 0 | 1 |     Stream ID (6+)    |
        // +---+---+-----------------------+
        final ByteBuf cancel = qpackDecoderStream.alloc().buffer(8);
        encodePrefixedInteger(cancel, (byte) 0b0100_0000, 6, streamId);
        closeOnFailure(qpackDecoderStream.writeAndFlush(cancel));
    }

    private static boolean isIndexed(byte b) {
        // https://quicwg.org/base-drafts/draft-ietf-quic-qpack.html#name-indexed-field-line
        //   0   1   2   3   4   5   6   7
        // +---+---+---+---+---+---+---+---+
        // | 1 | T |      Index (6+)       |
        // +---+---+-----------------------+
        return (b & 0b1000_0000) == 0b1000_0000;
    }

    private static boolean isLiteralWithNameRef(byte b) {
        // https://quicwg.org/base-drafts/draft-ietf-quic-qpack.html#name-literal-field-line-with-nam
        //  0   1   2   3   4   5   6   7
        // +---+---+---+---+---+---+---+---+
        // | 0 | 1 | N | T |Name Index (4+)|
        // +---+---+---+---+---------------+
        return (b & 0b1100_0000) == 0b0100_0000;
    }

    private static boolean isLiteral(byte b) {
        // https://quicwg.org/base-drafts/draft-ietf-quic-qpack.html#name-literal-field-line-with-lit
        //  0   1   2   3   4   5   6   7
        // +---+---+---+---+---+---+---+---+
        // | 0 | 0 | 1 | N | H |NameLen(3+)|
        // +---+---+---+---+---+-----------+
        return (b & 0b1110_0000) == 0b0010_0000;
    }

    private static boolean isIndexedWithPostBase(byte b) {
        // https://quicwg.org/base-drafts/draft-ietf-quic-qpack.html#name-indexed-field-line-with-pos
        //   0   1   2   3   4   5   6   7
        // +---+---+---+---+---+---+---+---+
        // | 0 | 0 | 0 | 1 |  Index (4+)   |
        // +---+---+---+---+---------------+
        return (b & 0b1111_0000) == 0b0001_0000;
    }

    private static boolean isLiteralWithPostBaseNameRef(byte b) {
        // https://quicwg.org/base-drafts/draft-ietf-quic-qpack.html#name-literal-field-line-with-pos
        //  0   1   2   3   4   5   6   7
        // +---+---+---+---+---+---+---+---+
        // | 0 | 0 | 0 | 0 | N |NameIdx(3+)|
        // +---+---+---+---+---+-----------+
        return (b & 0b1111_0000) == 0b0000_0000;
    }

    private void decodeIndexed(ByteBuf in, BiConsumer<CharSequence, CharSequence> sink, int base)
            throws QpackException {
        // https://quicwg.org/base-drafts/draft-ietf-quic-qpack.html#name-indexed-field-line
        //   0   1   2   3   4   5   6   7
        // +---+---+---+---+---+---+---+---+
        // | 1 | T |      Index (6+)       |
        // +---+---+-----------------------+
        //
        // T == 1 implies static table
        final QpackHeaderField field;
        if (firstByteEquals(in, (byte) 0b1100_0000)) {
            final int idx = decodePrefixedIntegerAsInt(in, 6);
            assert idx >= 0;
            if (idx >= QpackStaticTable.length) {
                throw HEADER_ILLEGAL_INDEX_VALUE;
            }
            field = QpackStaticTable.getField(idx);
        } else {
            final int idx = decodePrefixedIntegerAsInt(in, 6);
            assert idx >= 0;
            field = dynamicTable.getEntryRelativeEncodedField(base - idx);
        }
        sink.accept(field.name, field.value);
    }

    private void decodeIndexedWithPostBase(ByteBuf in, BiConsumer<CharSequence, CharSequence> sink, int base)
            throws QpackException {
        // https://quicwg.org/base-drafts/draft-ietf-quic-qpack.html#name-indexed-field-line-with-pos
        //   0   1   2   3   4   5   6   7
        // +---+---+---+---+---+---+---+---+
        // | 0 | 0 | 0 | 1 |  Index (4+)   |
        // +---+---+---+---+---------------+
        final int idx = decodePrefixedIntegerAsInt(in, 4);
        assert idx >= 0;
        QpackHeaderField field = dynamicTable.getEntryRelativeEncodedField(base + idx);
        sink.accept(field.name, field.value);
    }

    private void decodeLiteralWithNameRef(ByteBuf in, BiConsumer<CharSequence, CharSequence> sink, int base)
            throws QpackException {
        final CharSequence name;
        // https://quicwg.org/base-drafts/draft-ietf-quic-qpack.html#name-literal-field-line-with-nam
        //    0   1   2   3   4   5   6   7
        // +---+---+---+---+---+---+---+---+
        // | 0 | 1 | N | T |Name Index (4+)|
        // +---+---+---+---+---------------+
        // | H |     Value Length (7+)     |
        // +---+---------------------------+
        // |  Value String (Length bytes)  |
        // +-------------------------------+
        //
        // T == 1 implies static table
        if (firstByteEquals(in, (byte) 0b0001_0000)) {
            final int idx = decodePrefixedIntegerAsInt(in, 4);
            assert idx >= 0;
            if (idx >= QpackStaticTable.length) {
                throw NAME_ILLEGAL_INDEX_VALUE;
            }
            name = QpackStaticTable.getField(idx).name;
        } else {
            final int idx = decodePrefixedIntegerAsInt(in, 4);
            assert idx >= 0;
            name = dynamicTable.getEntryRelativeEncodedField(base - idx).name;
        }
        final CharSequence value = decodeHuffmanEncodedLiteral(in, 7);
        sink.accept(name, value);
    }

    private void decodeLiteralWithPostBaseNameRef(ByteBuf in, BiConsumer<CharSequence, CharSequence> sink, int base)
            throws QpackException {
        // https://quicwg.org/base-drafts/draft-ietf-quic-qpack.html#name-literal-field-line-with-pos
        //   0   1   2   3   4   5   6   7
        // +---+---+---+---+---+---+---+---+
        // | 0 | 0 | 0 | 0 | N |NameIdx(3+)|
        // +---+---+---+---+---+-----------+
        // | H |     Value Length (7+)     |
        // +---+---------------------------+
        // |  Value String (Length bytes)  |
        // +-------------------------------+
        final int idx = decodePrefixedIntegerAsInt(in, 3);
        assert idx >= 0;
        CharSequence name = dynamicTable.getEntryRelativeEncodedField(base + idx).name;
        final CharSequence value = decodeHuffmanEncodedLiteral(in, 7);
        sink.accept(name, value);
    }

    private void decodeLiteral(ByteBuf in, BiConsumer<CharSequence, CharSequence> sink) throws QpackException {
        // https://quicwg.org/base-drafts/draft-ietf-quic-qpack.html#name-literal-field-line-with-lit
        //   0   1   2   3   4   5   6   7
        // +---+---+---+---+---+---+---+---+
        // | 0 | 0 | 1 | N | H |NameLen(3+)|
        // +---+---+---+---+---+-----------+
        // |  Name String (Length bytes)   |
        // +---+---------------------------+
        // | H |     Value Length (7+)     |
        // +---+---------------------------+
        // |  Value String (Length bytes)  |
        // +-------------------------------+
        final CharSequence name = decodeHuffmanEncodedLiteral(in, 3);
        final CharSequence value = decodeHuffmanEncodedLiteral(in, 7);
        sink.accept(name, value);
    }

    private CharSequence decodeHuffmanEncodedLiteral(ByteBuf in, int prefix) throws QpackException {
        assert prefix < 8;
        final boolean huffmanEncoded = firstByteEquals(in, (byte) (1 << prefix));
        final int length = decodePrefixedIntegerAsInt(in, prefix);
        assert length >= 0;
        if (huffmanEncoded) {
            return huffmanDecoder.decode(in, length);
        }
        byte[] buf = new byte[length];
        in.readBytes(buf);
        return new AsciiString(buf, false);
    }

    // Visible for testing
    int decodeRequiredInsertCount(QpackAttributes qpackAttributes, ByteBuf buf) throws QpackException {
        final long encodedInsertCount = QpackUtil.decodePrefixedInteger(buf, 8);
        assert encodedInsertCount >= 0;
        // https://quicwg.org/base-drafts/draft-ietf-quic-qpack.html#name-required-insert-count
        // FullRange = 2 * MaxEntries
        //   if EncodedInsertCount == 0:
        //      ReqInsertCount = 0
        //   else:
        //      if EncodedInsertCount > FullRange:
        //         Error
        //      MaxValue = TotalNumberOfInserts + MaxEntries
        //
        //      # MaxWrapped is the largest possible value of
        //      # ReqInsertCount that is 0 mod 2 * MaxEntries
        //      MaxWrapped = floor(MaxValue / FullRange) * FullRange
        //      ReqInsertCount = MaxWrapped + EncodedInsertCount - 1
        //
        //      # If ReqInsertCount exceeds MaxValue, the Encoder's value
        //      # must have wrapped one fewer time
        //      if ReqInsertCount > MaxValue:
        //         if ReqInsertCount <= FullRange:
        //            Error
        //         ReqInsertCount -= FullRange
        //
        //      # Value of 0 must be encoded as 0.
        //      if ReqInsertCount == 0:
        //         Error
        if (encodedInsertCount == 0) {
            return 0;
        }
        if (qpackAttributes.dynamicTableDisabled() || encodedInsertCount > fullRange) {
            throw INVALID_REQUIRED_INSERT_COUNT;
        }

        final long maxValue = dynamicTable.insertCount() + maxEntries;
        final long maxWrapped = floorDiv(maxValue, fullRange) * fullRange;
        long requiredInsertCount = maxWrapped + encodedInsertCount - 1;

        if (requiredInsertCount > maxValue) {
            if (requiredInsertCount <= fullRange) {
                throw INVALID_REQUIRED_INSERT_COUNT;
            }
            requiredInsertCount -= fullRange;
        }
        // requiredInsertCount can not be negative as encodedInsertCount read from the buffer can not be negative.
        if (requiredInsertCount == 0) {
            throw INVALID_REQUIRED_INSERT_COUNT;
        }
        return toIntOrThrow(requiredInsertCount);
    }

    // Visible for testing
    int decodeBase(ByteBuf buf, int requiredInsertCount) throws QpackException {
        // https://quicwg.org/base-drafts/draft-ietf-quic-qpack.html#section-4.5.1
        //   0   1   2   3   4   5   6   7
        // +---+---------------------------+
        // | S |      Delta Base (7+)      |
        // +---+---------------------------+
        final boolean s = (buf.getByte(buf.readerIndex()) & 0b1000_0000) == 0b1000_0000;
        final int deltaBase = decodePrefixedIntegerAsInt(buf, 7);
        assert deltaBase >= 0;
        // https://quicwg.org/base-drafts/draft-ietf-quic-qpack.html#name-base
        //    if S == 0:
        //      Base = ReqInsertCount + DeltaBase
        //   else:
        //      Base = ReqInsertCount - DeltaBase - 1
        return s ? requiredInsertCount - deltaBase - 1 : requiredInsertCount + deltaBase;
    }

    private boolean shouldWaitForDynamicTableUpdates(int requiredInsertCount) throws QpackException {
        if (requiredInsertCount > dynamicTable.insertCount()) {
            if (blockedStreamsCount == maxBlockedStreams - 1) {
                throw MAX_BLOCKED_STREAMS_EXCEEDED;
            }
            return true;
        }
        return false;
    }

    private void sendInsertCountIncrementIfRequired(QuicStreamChannel qpackDecoderStream) throws QpackException {
        final int insertCount = dynamicTable.insertCount();
        final List<Runnable> runnables = this.blockedStreams.get(insertCount);
        if (runnables != null) {
            boolean failed = false;
            for (Runnable runnable : runnables) {
                try {
                    runnable.run();
                } catch (Exception e) {
                    failed = true;
                    logger.error("Failed to resume a blocked stream {}.", runnable, e);
                }
            }
            if (failed) {
                throw BLOCKED_STREAM_RESUMPTION_FAILED;
            }
        }
        if (stateSyncStrategy.entryAdded(insertCount)) {
            // https://quicwg.org/base-drafts/draft-ietf-quic-qpack.html#name-insert-count-increment
            //   0   1   2   3   4   5   6   7
            // +---+---+---+---+---+---+---+---+
            // | 0 | 0 |     Increment (6+)    |
            // +---+---+-----------------------+
            final ByteBuf incr = qpackDecoderStream.alloc().buffer(8);
            encodePrefixedInteger(incr, (byte) 0b0, 6, insertCount - lastAckInsertCount);
            lastAckInsertCount = insertCount;
            closeOnFailure(qpackDecoderStream.writeAndFlush(incr));
        }
    }
}
