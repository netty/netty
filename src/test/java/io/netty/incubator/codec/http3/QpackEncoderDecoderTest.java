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
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.util.AsciiString;
import org.junit.After;
import org.junit.Test;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;

import static io.netty.buffer.UnpooledByteBufAllocator.DEFAULT;
import static io.netty.incubator.codec.http3.Http3SettingsFrame.HTTP3_SETTINGS_QPACK_BLOCKED_STREAMS;
import static io.netty.incubator.codec.http3.Http3SettingsFrame.HTTP3_SETTINGS_QPACK_MAX_TABLE_CAPACITY;
import static io.netty.incubator.codec.quic.QuicStreamType.UNIDIRECTIONAL;
import static java.lang.Math.floorDiv;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class QpackEncoderDecoderTest {

    private QpackEncoder encoder;
    private QpackDecoder decoder;
    private boolean stateSyncStrategyAckNextInsert = true;
    private int headersAdded;
    private int maxEntries;
    private QpackEncoderDynamicTable encDynamicTable;
    private QpackDecoderDynamicTable decDynamicTable;
    private BlockingQueue<Callable<Void>> suspendedEncoderInstructions;

    private final QpackDecoderStateSyncStrategy syncStrategy = mock(QpackDecoderStateSyncStrategy.class);
    private final Http3Headers encHeaders = new DefaultHttp3Headers();
    private final Http3Headers decHeaders = new DefaultHttp3Headers();
    private final ByteBuf out = Unpooled.buffer();
    private final EmbeddedQuicChannel parent = new EmbeddedQuicChannel(true);
    private QpackAttributes attributes;

    @After
    public void tearDown() {
        out.release();
    }

    @Test
    public void dynamicIndexed() throws Exception {
        setup(128, 0);
        headersAdded++;
        testDynamicTableIndexed("foo", "bar");
    }

    @Test
    public void dynamicIndexedWithBlockedStreams() throws Exception {
        setup(128, 100);
        headersAdded++;
        testDynamicTableIndexedWithBlockedStreams("foo", "bar");
    }

    @Test
    public void dynamicIndexedWithStaticTableNameRef() throws Exception {
        setup(128, 0);
        headersAdded++;
        testDynamicTableIndexed(":authority", "netty.quic");
    }

    @Test
    public void dynamicIndexedWithStaticTableNameRefWithBlockedStreams() throws Exception {
        setup(128, 100);
        headersAdded++;
        testDynamicTableIndexedWithBlockedStreams(":authority", "netty.quic");
    }

    @Test
    public void dynamicIndexedWithNameRef() throws Exception {
        setup(128, 0);

        headersAdded++;
        testDynamicTableIndexed("foo", "bar");

        resetState();

        headersAdded++;
        testDynamicTableIndexed("foo", "bar2");

        resetState();

        testDynamicTableIndexed("foo", "bar");
    }

    @Test
    public void dynamicIndexedWithNameRefWithBlockedStream() throws Exception {
        setup(128, 100);

        headersAdded++;
        testDynamicTableIndexedWithBlockedStreams("foo", "bar");

        resetState();

        headersAdded++;
        testDynamicTableIndexedWithBlockedStreams("foo", "bar2");
    }

    @Test
    public void indexWrapAround() throws Exception {
        setup(128, 0); // maxEntries => 128/32 = 4, full range = 2*4 = 8

        addEncodeHeader("foo", "bar", 3);
        encode(out, encHeaders);
        verifyRequiredInsertCount(3);
        verifyKnownReceivedCount(3);
        decode(out, decHeaders);
        assertThat(decDynamicTable.insertCount(), is(3));
        assertThat(decHeaders.size(), is(3));
        verifyDecodedHeaders("foo", "bar", 3);

        resetState();
        addEncodeHeader("boo", "far", 3);
        encode(out, encHeaders);
        verifyRequiredInsertCount(6);
        verifyKnownReceivedCount(6);
        decode(out, decHeaders);
        assertThat(decDynamicTable.insertCount(), is(6));
        assertThat(decHeaders.size(), is(3));
        verifyDecodedHeaders("boo", "far", 3);

        resetState();
        addEncodeHeader("zoo", "gar", 3);
        encode(out, encHeaders);
        verifyRequiredInsertCount(9);
        verifyKnownReceivedCount(9);

        decode(out, decHeaders);
        assertThat(decDynamicTable.insertCount(), is(9));
        assertThat(decHeaders.size(), is(3));
        verifyDecodedHeaders("zoo", "gar", 3);

        // Now reuse the headers for encode to use dynamic table.
        resetState();
        assertThat("Header not found in encoder dynamic table.",
                encDynamicTable.getEntryIndex("zoo1", "gar"), greaterThanOrEqualTo(0));
        assertThat("Header not found in encoder dynamic table.",
                encDynamicTable.getEntryIndex("zoo2", "gar"), greaterThanOrEqualTo(0));
        encHeaders.add("zoo1", "gar");
        encHeaders.add("zoo2", "gar");
        encode(out, encHeaders);
        verifyRequiredInsertCount(9); // No new inserts
        verifyKnownReceivedCount(9); // No new inserts
        decode(out, decHeaders);
        assertThat(decDynamicTable.insertCount(), is(9));
        assertThat(decHeaders.size(), is(2));
        verifyDecodedHeader("zoo1", "gar");
        verifyDecodedHeader("zoo2", "gar");
    }

    @Test
    public void indexWrapAroundWithBlockedStreams() throws Exception {
        setup(128, 100); // maxEntries => 128/32 = 4, full range = 2*4 = 8

        addEncodeHeader("foo", "bar", 3);
        encode(out, encHeaders);
        verifyRequiredInsertCount(3);
        verifyKnownReceivedCount(0);
        assertThat(decDynamicTable.insertCount(), is(0));

        drainNextSuspendedEncoderInstruction();
        decode(out, decHeaders);
        drainAllSuspendedEncoderInstructions();
        assertThat(decDynamicTable.insertCount(), is(3));
        verifyKnownReceivedCount(3);
        assertThat(decHeaders.size(), is(3));
        verifyDecodedHeaders("foo", "bar", 3);

        resetState();
        addEncodeHeader("boo", "far", 3);
        encode(out, encHeaders);
        verifyRequiredInsertCount(6);
        verifyKnownReceivedCount(0); // All acknowledged entries were removed.

        decode(out, decHeaders);
        drainAllSuspendedEncoderInstructions();
        assertThat(decDynamicTable.insertCount(), is(6));
        verifyKnownReceivedCount(6);
        assertThat(decHeaders.size(), is(3));
        verifyDecodedHeaders("boo", "far", 3);

        resetState();
        addEncodeHeader("zoo", "gar", 3);
        encode(out, encHeaders);
        verifyRequiredInsertCount(9);
        verifyKnownReceivedCount(0); // All acknowledged entries were removed.

        decode(out, decHeaders);
        drainAllSuspendedEncoderInstructions();
        verifyKnownReceivedCount(9);
        assertThat(decDynamicTable.insertCount(), is(9));
        assertThat(decHeaders.size(), is(3));
        verifyDecodedHeaders("zoo", "gar", 3);

        // Now reuse the headers for encode to use dynamic table.
        resetState();
        assertThat("Header not found in encoder dynamic table.",
                encDynamicTable.getEntryIndex("zoo1", "gar"), greaterThanOrEqualTo(0));
        assertThat("Header not found in encoder dynamic table.",
                encDynamicTable.getEntryIndex("zoo2", "gar"), greaterThanOrEqualTo(0));
        encHeaders.add("zoo1", "gar");
        encHeaders.add("zoo2", "gar");
        encode(out, encHeaders);
        verifyRequiredInsertCount(9); // No new inserts
        verifyKnownReceivedCount(9); // No new inserts
        decode(out, decHeaders);
        assertThat(decDynamicTable.insertCount(), is(9));
        assertThat(decHeaders.size(), is(2));
        verifyDecodedHeader("zoo1", "gar");
        verifyDecodedHeader("zoo2", "gar");
    }

    @Test
    public void duplicate() throws Exception {
        setup(256, 0, 50);
        // Do not ack any add so entries are not evicted from the table just marked for eviction hence leveraging
        // duplicate path
        stateSyncStrategyAckNextInsert = false;

        addEncodeHeader("foo", "bar", 5);
        QpackHeaderField oldEntry = new QpackHeaderField("foo0", "bar");
        assertThat(encHeaders.get(oldEntry.name, oldEntry.value), is(notNullValue()));

        ByteBuf spareEncode = Unpooled.buffer();
        try {
            encode(spareEncode, encHeaders);
        } finally {
            spareEncode.release();
        }
        verifyRequiredInsertCount(5);
        verifyKnownReceivedCount(0);

        final int idx = encDynamicTable.getEntryIndex(oldEntry.name, oldEntry.value);
        assertThat(idx, greaterThanOrEqualTo(0));
        assertThat(encDynamicTable.requiresDuplication(idx, oldEntry.size()), is(true));

        resetState();
        stateSyncStrategyAckNextInsert = true;

        encHeaders.add(oldEntry.name, oldEntry.value);
        encode(out, encHeaders); // duplicate but not add to the header block
        verifyRequiredInsertCount(6);
        decode(out, decHeaders);
        verifyKnownReceivedCount(6);

        assertThat(decDynamicTable.insertCount(), is(6));
        assertThat(decHeaders.size(), is(1));
        verifyDecodedHeader(oldEntry.name, oldEntry.value);

        // Now encode again to refer to the duplicated entry
        resetState();

        encHeaders.add(oldEntry.name, oldEntry.value);
        encode(out, encHeaders);
        verifyRequiredInsertCount(6);
        decode(out, decHeaders);
        verifyKnownReceivedCount(6);

        assertThat(decDynamicTable.insertCount(), is(6));
        assertThat(decHeaders.size(), is(1));
        verifyDecodedHeader(oldEntry.name, oldEntry.value);
    }

    @Test
    public void duplicateWithBlockedStreams() throws Exception {
        setup(256, 100, 50);
        // Do not ack any add so entries are not evicted from the table just marked for eviction hence leveraging
        // duplicate path
        stateSyncStrategyAckNextInsert = false;

        addEncodeHeader("foo", "bar", 5);
        QpackHeaderField oldEntry = new QpackHeaderField("foo0", "bar");
        assertThat(encHeaders.get(oldEntry.name, oldEntry.value), is(notNullValue()));

        ByteBuf spareEncode = Unpooled.buffer();
        try {
            encode(spareEncode, encHeaders);
        } finally {
            spareEncode.release();
        }
        verifyRequiredInsertCount(5);
        verifyKnownReceivedCount(0);

        final int idx = encDynamicTable.getEntryIndex(oldEntry.name, oldEntry.value);
        assertThat(idx, greaterThanOrEqualTo(0));
        assertThat(encDynamicTable.requiresDuplication(idx, oldEntry.size()), is(true));

        resetState();
        stateSyncStrategyAckNextInsert = true;

        encHeaders.add(oldEntry.name, oldEntry.value);
        encode(out, encHeaders);
        verifyRequiredInsertCount(6);

        drainNextSuspendedEncoderInstruction();
        decode(out, decHeaders);
        assertThat(decDynamicTable.insertCount(), is(0));
        verifyKnownReceivedCount(0);

        drainAllSuspendedEncoderInstructions();
        assertThat(decDynamicTable.insertCount(), is(6));
        verifyKnownReceivedCount(6);
        assertThat(decHeaders.size(), is(1));
        verifyDecodedHeader(oldEntry.name, oldEntry.value);
    }

    private void testDynamicTableIndexed(CharSequence name, CharSequence value) throws Exception {
        encHeaders.add(name, value);
        encode(out, encHeaders);
        verifyRequiredInsertCount(headersAdded);
        verifyKnownReceivedCount(headersAdded);

        decode(out, decHeaders);
        assertThat(decDynamicTable.insertCount(), is(headersAdded));
        assertThat(decHeaders.size(), is(1));
        verifyDecodedHeader(name, value);

        // Encode again to refer to dynamic table
        out.clear();
        decHeaders.clear();

        encode(out, encHeaders);
        verifyRequiredInsertCount(headersAdded);
        verifyKnownReceivedCount(headersAdded);

        decode(out, decHeaders);
        assertThat(decDynamicTable.insertCount(), is(headersAdded));
        assertThat(decHeaders.size(), is(1));
        verifyDecodedHeader(name, value);
    }

    private void testDynamicTableIndexedWithBlockedStreams(CharSequence name, CharSequence value) throws Exception {
        encHeaders.add(name, value);
        encode(out, encHeaders);
        verifyRequiredInsertCount(headersAdded);

        verifyKnownReceivedCount(headersAdded - 1);
        assertThat(decDynamicTable.insertCount(), is(headersAdded - 1));

        drainNextSuspendedEncoderInstruction();

        decode(out, decHeaders);
        drainAllSuspendedEncoderInstructions();
        assertThat(decDynamicTable.insertCount(), is(headersAdded));
        verifyKnownReceivedCount(headersAdded);

        assertThat(decHeaders.size(), is(1));
        verifyDecodedHeader(name, value);
    }

    @Test
    public void staticTableOnly() throws Exception {
        setup(0, 0);

        encHeaders.add(":authority", "netty.quic"); // name only
        encHeaders.add(":path", "/"); // name & value
        encHeaders.add(":method", "GET"); // name & value with few options per name
        encHeaders.add(":status", "417"); // name & multiple values but value is missing
        encHeaders.add("x-qpack-draft", "19");

        encode(out, encHeaders);
        decode(out, decHeaders);

        assertThat(decHeaders.size(), is(5));
        verifyDecodedHeader(":authority", "netty.quic");
        verifyDecodedHeader(":path", "/");
        verifyDecodedHeader(":method", "GET");
        verifyDecodedHeader(":status", "417");
        verifyDecodedHeader("x-qpack-draft", "19");
    }

    @Test
    public void decoderThrowsOnInvalidInput() throws Exception {
        setup(0, 0);

        encHeaders.add(":authority", "netty.quic"); // name only
        encode(out, encHeaders);
        // Add empty byte to the end of the buffer. This should trigger an exception in the decoder.
        out.writeByte(0);

        try {
            decode(out, decHeaders);
            fail();
        } catch (QpackException exception) {
            // expected
        }
    }

    private void resetState() {
        out.clear();
        encHeaders.clear();
        decHeaders.clear();
    }

    private void encode(ByteBuf buf, Http3Headers headers) {
        encoder.encodeHeaders(attributes, buf, DEFAULT, 1, headers);
        assertThat("Parent channel closed.", parent.isActive(), is(true));
    }

    private void decode(ByteBuf buf, Http3Headers headers) throws QpackException {
        decoder.decode(attributes, 1, buf, buf.readableBytes(),
                new Http3HeadersSink(headers, 1024, false, false), () -> {
                    try {
                        decoder.decode(attributes, 1, buf, buf.readableBytes(),
                                new Http3HeadersSink(headers, 1024, false, false),
                                () -> {
                                    throw new IllegalStateException("Decode resumption suspended.");
                                });
                    } catch (QpackException e) {
                        throw new AssertionError("Decode failed.", e);
                    }
                });
        assertThat("Parent channel closed.", parent.isActive(), is(true));
    }

    private void verifyDecodedHeader(CharSequence name, CharSequence value) {
        assertThat(decHeaders.get(name), is(new AsciiString(value)));
    }

    private void drainAllSuspendedEncoderInstructions() throws Exception {
        Callable<Void> next;
        for (next = suspendedEncoderInstructions.poll(); next != null; next = suspendedEncoderInstructions.poll()) {
            next.call();
        }
    }

    private void drainNextSuspendedEncoderInstruction() throws Exception {
        Callable<Void> next = suspendedEncoderInstructions.poll();
        assertThat(next, is(notNullValue())); // dynamic table size instruction
        next.call();
    }

    private void setup(long dynamicTableSize, int maxBlockedStreams) throws Exception {
        setup(dynamicTableSize, maxBlockedStreams, 10);
    }

    private void setup(long dynamicTableSize, int maxBlockedStreams, int expectedTableFreePercentage) throws Exception {
        attributes = new QpackAttributes(parent, false);
        Http3.setQpackAttributes(parent, attributes);
        maxEntries = Math.toIntExact(floorDiv(dynamicTableSize, 32));
        DefaultHttp3SettingsFrame localSettings = new DefaultHttp3SettingsFrame();
        localSettings.put(HTTP3_SETTINGS_QPACK_MAX_TABLE_CAPACITY, dynamicTableSize);
        localSettings.put(HTTP3_SETTINGS_QPACK_BLOCKED_STREAMS, (long) maxBlockedStreams);
        if (maxBlockedStreams > 0) {
            // section acknowledgment will implicitly ack insert count.
            stateSyncStrategyAckNextInsert = false;
        }
        when(syncStrategy.entryAdded(anyInt())).thenAnswer(__ -> stateSyncStrategyAckNextInsert);
        encDynamicTable = new QpackEncoderDynamicTable(16, expectedTableFreePercentage);
        decDynamicTable = new QpackDecoderDynamicTable();
        decoder = new QpackDecoder(localSettings, decDynamicTable, syncStrategy);
        encoder = new QpackEncoder(encDynamicTable);
        if (maxBlockedStreams > 0) {
            suspendedEncoderInstructions = new LinkedBlockingQueue<>();
        }
        EmbeddedQuicStreamChannel encoderStream = (EmbeddedQuicStreamChannel) parent.createStream(UNIDIRECTIONAL,
                new ForwardWriteToReadOnOtherHandler(new QpackEncoderHandler(dynamicTableSize, decoder),
                        suspendedEncoderInstructions)).get();
        EmbeddedQuicStreamChannel decoderStream = (EmbeddedQuicStreamChannel) parent.createStream(UNIDIRECTIONAL,
                new ForwardWriteToReadOnOtherHandler(new QpackDecoderHandler(encoder))).get();
        attributes.encoderStream(encoderStream);
        attributes.decoderStream(decoderStream);
        encoder.configureDynamicTable(attributes, dynamicTableSize, maxBlockedStreams);
    }

    private void addEncodeHeader(String namePrefix, String value, int times) {
        for (int i = 0; i < times; i++) {
            encHeaders.add(namePrefix + i, value);
        }
    }

    private void verifyDecodedHeaders(String namePrefix, String value, int times) {
        for (int i = 0; i < times; i++) {
            verifyDecodedHeader(namePrefix + i, value);
        }
    }

    private void verifyRequiredInsertCount(int insertCount) {
        assertThat("Unexpected dynamic table insert count.", encDynamicTable.requiredInsertCount(),
                is(insertCount == 0 ? 0 : insertCount % (2 * maxEntries) + 1));
    }

    private void verifyKnownReceivedCount(int receivedCount) {
        assertThat("Unexpected dynamic table known received count.", encDynamicTable.knownReceivedCount(),
                is(receivedCount == 0 ? 0 : receivedCount % (2 * maxEntries) + 1));
    }

    private static final class ForwardWriteToReadOnOtherHandler extends ChannelOutboundHandlerAdapter {

        private final ChannelInboundHandler other;
        private final BlockingQueue<Callable<Void>> suspendQueue;

        ForwardWriteToReadOnOtherHandler(ChannelInboundHandler other) {
            this(other, null);
        }

        ForwardWriteToReadOnOtherHandler(ChannelInboundHandler other, BlockingQueue<Callable<Void>> suspendQueue) {
            this.other = other;
            this.suspendQueue = suspendQueue;
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (msg instanceof ByteBuf) {
                if (suspendQueue != null) {
                    suspendQueue.offer(() -> {
                        other.channelRead(ctx, msg);
                        return null;
                    });
                } else {
                    other.channelRead(ctx, msg);
                }
            } else {
                super.write(ctx, msg, promise);
            }
        }
    }
}
