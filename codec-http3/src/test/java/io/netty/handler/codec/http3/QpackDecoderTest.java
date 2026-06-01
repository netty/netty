/*
 * Copyright 2021 The Netty Project
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

package io.netty.handler.codec.http3;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collection;
import java.util.function.BiConsumer;

import static io.netty.handler.codec.http3.Http3SettingsFrame.HTTP3_SETTINGS_QPACK_MAX_TABLE_CAPACITY;
import static io.netty.handler.codec.http3.QpackDecoderStateSyncStrategy.ackEachInsert;
import static io.netty.handler.codec.http3.QpackUtil.MAX_UNSIGNED_INT;
import static java.lang.Math.toIntExact;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class QpackDecoderTest {
    private static final String FOO = "foo";
    private static final String BAR = "bar";
    private QpackDecoderDynamicTable table;
    private EmbeddedQuicStreamChannel decoderStream;

    private QpackDecoder decoder;
    private int inserted;
    private int maxEntries;
    private QpackAttributes attributes;

    public static Collection<Object[]> data() {
        int capacity = 128; // maxEntries = 128/32 = 4, maxIndex = 2*4 = 8
        return asList(
                new Object[]{capacity, 0},
                new Object[]{capacity, 1},
                new Object[]{capacity, 5},
                new Object[]{capacity, 8},
                new Object[]{capacity, 16},
                new Object[]{capacity, 25},
                new Object[]{capacity, 64},
                new Object[]{capacity, 89}
        );
    }

    @ParameterizedTest(name = "capacity: {0}, inserts: {1}")
    @MethodSource("data")
    public void requiredInsertCountAsInserted(int capacity, int insertionCount) throws Exception {
        setup(capacity);

        insertLiterals(insertionCount);
        encodeDecodeVerifyRequiredInsertCount(inserted);
    }

    @ParameterizedTest(name = "capacity: {0}, inserts: {1}")
    @MethodSource("data")
    public void requiredInsertCountLessThanInserted(int capacity, int insertionCount) throws Exception {
        setup(capacity);
        assumeTrue(insertionCount > 0);

        insertLiterals(insertionCount);
        encodeDecodeVerifyRequiredInsertCount(insertionCount - 1);
    }

    @ParameterizedTest(name = "capacity: {0}, inserts: {1}")
    @MethodSource("data")
    public void requiredInsertCountBehindMax(int capacity, int insertionCount) throws Exception {
        setup(capacity);
        assumeTrue(insertionCount > maxEntries);

        insertLiterals(insertionCount);
        encodeDecodeVerifyRequiredInsertCount(insertionCount - maxEntries + 1);
    }

    @ParameterizedTest(name = "capacity: {0}, inserts: {1}")
    @MethodSource("data")
    public void getWithRelativeIndex(int capacity, int insertionCount) throws Exception {
        setup(capacity);
        assumeTrue(insertionCount > 3);

        insertLiterals(insertionCount);
        int requiredInsertCount = encodeDecodeRequiredInsertCount(insertionCount);
        int base = encodeDecodeDeltaBase(requiredInsertCount, false, 1);
        int relativeIndex = 1;
        final QpackHeaderField entry = table.getEntryRelativeEncodedField(base - relativeIndex);
        verifyField(entry, insertionCount - 2);
    }

    @ParameterizedTest(name = "capacity: {0}, inserts: {1}")
    @MethodSource("data")
    public void getWithPostBaseRelativeIndex(int capacity, int insertionCount) throws Exception {
        setup(capacity);
        assumeTrue(insertionCount > 2);

        insertLiterals(insertionCount);
        int requiredInsertCount = encodeDecodeRequiredInsertCount(insertionCount - 1);
        int base = encodeDecodeDeltaBase(requiredInsertCount, true, 0);
        int relativeIndex = 1;
        final QpackHeaderField entry = table.getEntryRelativeEncodedField(base - relativeIndex);
        verifyField(entry, insertionCount - 1);
    }

    @Test
    public void zeroMaxBlockedStreamsThrowsOnBlockedStream() throws Exception {
        setup(128);
        ByteBuf in = encodeBlockingFrame(1);
        try {
            assertThrows(QpackException.class,
                    () -> decoder.decode(attributes, 0L, in, in.readableBytes(), (n, v) -> { }, () -> { }));
        } finally {
            in.release();
        }
    }

    @Test
    public void maxBlockedStreamsLimitEnforced() throws Exception {
        int maxBlockedStreams = 3;
        setup(128, maxBlockedStreams);
        BiConsumer<CharSequence, CharSequence> sink = (n, v) -> { };

        for (long streamId = 0; streamId < maxBlockedStreams; streamId++) {
            ByteBuf in = encodeBlockingFrame(1);
            try {
                assertFalse(decoder.decode(attributes, streamId, in, in.readableBytes(), sink, () -> { }),
                        "Stream " + streamId + " should be blocked");
            } finally {
                in.release();
            }
        }

        ByteBuf overflow = encodeBlockingFrame(1);
        try {
            assertThrows(QpackException.class,
                    () -> decoder.decode(attributes, 99L, overflow, overflow.readableBytes(), sink, () -> { }));
        } finally {
            overflow.release();
        }
    }

    @Test
    public void blockedStreamsCountReleasedAfterUnblocking() throws Exception {
        int maxBlockedStreams = 2;
        setup(128, maxBlockedStreams);
        BiConsumer<CharSequence, CharSequence> sink = (n, v) -> { };

        for (long streamId = 0; streamId < maxBlockedStreams; streamId++) {
            ByteBuf in = encodeBlockingFrame(1);
            try {
                assertFalse(decoder.decode(attributes, streamId, in, in.readableBytes(), sink, () -> { }),
                        "Stream " + streamId + " should be blocked");
            } finally {
                in.release();
            }
        }

        insertLiterals(1);

        for (long streamId = 100; streamId < 100 + maxBlockedStreams; streamId++) {
            ByteBuf in = encodeBlockingFrame(2);
            try {
                assertFalse(decoder.decode(attributes, streamId, in, in.readableBytes(), sink, () -> { }),
                        "Stream " + streamId + " should be blocked after counter was released");
            } finally {
                in.release();
            }
        }
    }

    /**
     * Encodes a 2-byte QPACK header block prefix (Required Insert Count + Base) that will cause
     * a stream to block until the dynamic table reaches {@code requiredInsertCount}.
     */
    private ByteBuf encodeBlockingFrame(int requiredInsertCount) {
        ByteBuf buf = Unpooled.buffer(2);
        QpackUtil.encodePrefixedInteger(buf, (byte) 0b0, 8, requiredInsertCount % (2L * maxEntries) + 1);
        QpackUtil.encodePrefixedInteger(buf, (byte) 0b0, 7, 0);
        return buf;
    }

    private void setup(long capacity) throws QpackException {
        setup(capacity, 0);
    }

    private void setup(long capacity, int maxBlockedStreams) throws QpackException {
        long maxTableCapacity = MAX_UNSIGNED_INT;
        inserted = 0;
        this.maxEntries = toIntExact(QpackUtil.maxEntries(maxTableCapacity));
        final DefaultHttp3SettingsFrame settings = new DefaultHttp3SettingsFrame();
        settings.put(HTTP3_SETTINGS_QPACK_MAX_TABLE_CAPACITY, maxTableCapacity);
        table = new QpackDecoderDynamicTable();
        EmbeddedQuicChannel parent = new EmbeddedQuicChannel(true);
        attributes = new QpackAttributes(parent, false);
        decoderStream = new EmbeddedQuicStreamChannel();
        attributes.decoderStream(decoderStream);
        decoder = new QpackDecoder(maxTableCapacity, maxBlockedStreams, table, ackEachInsert());
        decoder.setDynamicTableCapacity(capacity);
    }

    private void encodeDecodeVerifyRequiredInsertCount(int count) throws QpackException {
        final int ric = encodeDecodeRequiredInsertCount(count);
        assertThat(ric, is(count));
    }

    private int encodeDecodeDeltaBase(int requiredInsertCount, boolean postBase, int deltaBase) throws QpackException {
        final ByteBuf buf = Unpooled.buffer();
        QpackUtil.encodePrefixedInteger(buf, (byte) (postBase ? 0b0 : 0b1000_0000), 8, deltaBase);
        try {
            return decoder.decodeBase(buf, requiredInsertCount);
        } finally {
            buf.release();
        }
    }

    private int encodeDecodeRequiredInsertCount(int count) throws QpackException {
        final ByteBuf buf = Unpooled.buffer();
        QpackUtil.encodePrefixedInteger(buf, (byte) 0b0, 8, count == 0 ? 0 : count % (2L * maxEntries) + 1);
        try {
            return decoder.decodeRequiredInsertCount(attributes, buf);
        } finally {
            buf.release();
        }
    }

    private void insertLiterals(int count) throws QpackException {
        for (int i = 1; i <= count; i++) {
            inserted++;
            decoder.insertLiteral(decoderStream, FOO + i, BAR + i);
        }
        assertThat(decoderStream.finishAndReleaseAll(), is(count > 0));
    }

    private void verifyField(QpackHeaderField field, int fieldIndexWhenInserted) {
        assertThat(field.name, is(FOO + fieldIndexWhenInserted));
        assertThat(field.value, is(BAR + fieldIndexWhenInserted));
    }
}
