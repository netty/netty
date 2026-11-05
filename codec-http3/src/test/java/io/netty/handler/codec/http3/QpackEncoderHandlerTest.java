/*
 * Copyright 2026 The Netty Project
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
import io.netty.handler.codec.quic.QuicStreamType;
import io.netty.util.AsciiString;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static io.netty.handler.codec.http3.Http3.setQpackAttributes;
import static io.netty.handler.codec.http3.Http3ErrorCode.QPACK_ENCODER_STREAM_ERROR;
import static io.netty.handler.codec.http3.QpackUtil.encodePrefixedInteger;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class QpackEncoderHandlerTest {

    private EmbeddedQuicChannel parent;
    private EmbeddedQuicStreamChannel encoderStream;
    private EmbeddedQuicStreamChannel decoderStream;
    private QpackDecoderDynamicTable dynamicTable;

    @AfterEach
    public void tearDown() {
        assertFalse(parent.finishAndReleaseAll());
    }

    // A peer must never be able to make us buffer more than maxTableCapacity bytes for a single string literal
    // as no such literal could ever be inserted into the dynamic table anyway.
    @Test
    public void insertWithLiteralNameRejectsNameLengthExceedingMaxTableCapacity() throws Exception {
        setup(64);

        // Declare a name length that is way larger than what could ever fit into the dynamic table, and only
        // trickle a few bytes of the (bogus) payload. Prior to the fix this would be buffered indefinitely by the
        // ByteToMessageDecoder cumulator, potentially growing towards ~2 GiB.
        ByteBuf buf = encoderStream.alloc().buffer();
        encodePrefixedInteger(buf, (byte) 0b0100_0000, 5, Integer.MAX_VALUE - 1);
        buf.writeByte('a');

        Http3Exception e = assertThrows(Http3Exception.class, () -> encoderStream.writeInbound(buf));
        assertThat(e.errorCode(), is(QPACK_ENCODER_STREAM_ERROR));
        assertThat(e.getCause(), instanceOf(QpackException.class));

        Http3TestUtils.verifyClose(QPACK_ENCODER_STREAM_ERROR, parent);
        assertThat(dynamicTable.insertCount(), is(0));

        finishStreams(false);
    }

    @Test
    public void insertWithNameReferenceRejectsValueLengthExceedingMaxTableCapacity() throws Exception {
        setup(64);

        ByteBuf buf = encoderStream.alloc().buffer();
        // T = 1 (static table reference), Name Index = 0.
        encodePrefixedInteger(buf, (byte) 0b1100_0000, 6, 0);
        // Declare a huge value length but never actually send that many bytes.
        encodePrefixedInteger(buf, (byte) 0b0000_0000, 7, Integer.MAX_VALUE - 1);
        buf.writeByte('a');

        Http3Exception e = assertThrows(Http3Exception.class, () -> encoderStream.writeInbound(buf));
        assertThat(e.errorCode(), is(QPACK_ENCODER_STREAM_ERROR));
        assertThat(e.getCause(), instanceOf(QpackException.class));

        Http3TestUtils.verifyClose(QPACK_ENCODER_STREAM_ERROR, parent);
        assertThat(dynamicTable.insertCount(), is(0));

        finishStreams(false);
    }

    @Test
    public void insertWithLiteralNameWithinMaxTableCapacityIsAccepted() throws Exception {
        setup(64);

        setDynamicTableCapacity(64);
        assertFalse(encoderStream.writeInbound(encodeInsertWithLiteralName("foo", "bar")));

        assertThat(dynamicTable.insertCount(), is(1));
        assertThat(dynamicTable.size(), is((long) ("foo".length() + "bar".length() + 32)));

        finishStreams(true);
    }

    @Test
    public void insertWithLiteralNameRejectsNameLengthOneByteOverMaxTableCapacity() throws Exception {
        setup(64);

        // Declare a name length that is just one byte larger than maxTableCapacity, and don't send any payload
        // bytes at all: rejection must be based purely on the declared length, before waiting for (or buffering)
        // any of the string data.
        ByteBuf buf = encoderStream.alloc().buffer();
        encodePrefixedInteger(buf, (byte) 0b0100_0000, 5, 65);

        Http3Exception e = assertThrows(Http3Exception.class, () -> encoderStream.writeInbound(buf));
        assertThat(e.errorCode(), is(QPACK_ENCODER_STREAM_ERROR));

        Http3TestUtils.verifyClose(QPACK_ENCODER_STREAM_ERROR, parent);
        assertThat(dynamicTable.insertCount(), is(0));

        finishStreams(false);
    }

    // The value of an "Insert With Literal Name" instruction may arrive in many small fragments. The name must be
    // decoded exactly once, and not once per fragment, otherwise a peer can make us burn CPU (and allocate) in
    // O(fragments * nameLength).
    @Test
    public void insertWithLiteralNameDecodesHuffmanNameOnlyOnceWhenValueIsFragmented() throws Exception {
        AtomicInteger huffmanDecodes = new AtomicInteger();
        setup(4096, new QpackHuffmanDecoder() {
            @Override
            public AsciiString decode(ByteBuf buf, int length) throws QpackException {
                huffmanDecodes.incrementAndGet();
                return super.decode(buf, length);
            }
        });
        setDynamicTableCapacity(4096);

        String name = "x-" + repeat("name", 250);
        String value = repeat("value", 100);

        ByteBuf wire = encoderStream.alloc().buffer();
        ByteBuf huffmanName = encoderStream.alloc().buffer();
        new QpackHuffmanEncoder().encode(huffmanName, name);
        // H = 1, so the name is Huffman encoded.
        encodePrefixedInteger(wire, (byte) 0b0110_0000, 5, huffmanName.readableBytes());
        wire.writeBytes(huffmanName);
        huffmanName.release();
        encodePrefixedInteger(wire, (byte) 0b0000_0000, 7, value.length());
        wire.writeCharSequence(value, CharsetUtil.US_ASCII);

        // Dribble the whole instruction, one byte per read.
        int length = wire.readableBytes();
        for (int i = 0; i < length; i++) {
            assertFalse(encoderStream.writeInbound(wire.readRetainedSlice(1)));
            if (i < length - 1) {
                assertThat(dynamicTable.insertCount(), is(0));
            }
        }
        wire.release();

        assertThat(dynamicTable.insertCount(), is(1));
        assertThat(dynamicTable.size(), is((long) (name.length() + value.length() + 32)));
        assertThat(huffmanDecodes.get(), is(1));

        finishStreams(true);
    }

    @Test
    public void insertWithLiteralNameRejectsValueLengthExceedingMaxTableCapacityAfterName() throws Exception {
        setup(64);
        setDynamicTableCapacity(64);

        ByteBuf buf = encoderStream.alloc().buffer();
        encodePrefixedInteger(buf, (byte) 0b0100_0000, 5, 3);
        buf.writeCharSequence("foo", CharsetUtil.US_ASCII);
        // The name is complete, the value declares a length that can never fit.
        encodePrefixedInteger(buf, (byte) 0b0000_0000, 7, 65);

        Http3Exception e = assertThrows(Http3Exception.class, () -> encoderStream.writeInbound(buf));
        assertThat(e.errorCode(), is(QPACK_ENCODER_STREAM_ERROR));
        assertThat(e.getCause(), instanceOf(QpackException.class));

        Http3TestUtils.verifyClose(QPACK_ENCODER_STREAM_ERROR, parent);
        assertThat(dynamicTable.insertCount(), is(0));

        finishStreams(false);
    }

    @Test
    public void insertWithLiteralNameCanBeFollowedByAnotherInstructionInSameRead() throws Exception {
        setup(64);
        setDynamicTableCapacity(64);

        ByteBuf first = encodeInsertWithLiteralName("foo", "bar");
        ByteBuf second = encodeInsertWithLiteralName("baz", "qux");
        // First instruction complete, second one split right after its name.
        ByteBuf part1 = encoderStream.alloc().buffer().writeBytes(first).writeBytes(second, 0, 4);
        ByteBuf part2 = second.readerIndex(4).retainedSlice();
        first.release();
        second.release();

        assertFalse(encoderStream.writeInbound(part1));
        assertThat(dynamicTable.insertCount(), is(1));
        assertFalse(encoderStream.writeInbound(part2));
        assertThat(dynamicTable.insertCount(), is(2));

        finishStreams(true);
    }

    private static String repeat(String s, int times) {
        StringBuilder builder = new StringBuilder(s.length() * times);
        for (int i = 0; i < times; i++) {
            builder.append(s);
        }
        return builder.toString();
    }

    private void setDynamicTableCapacity(int capacity) {
        ByteBuf buf = encoderStream.alloc().buffer();
        encodePrefixedInteger(buf, (byte) 0b0010_0000, 5, capacity);
        assertFalse(encoderStream.writeInbound(buf));
    }

    private ByteBuf encodeInsertWithLiteralName(String name, String value) {
        ByteBuf buf = encoderStream.alloc().buffer();
        encodePrefixedInteger(buf, (byte) 0b0100_0000, 5, name.length());
        buf.writeCharSequence(name, CharsetUtil.US_ASCII);
        encodePrefixedInteger(buf, (byte) 0b0000_0000, 7, value.length());
        buf.writeCharSequence(value, CharsetUtil.US_ASCII);
        return buf;
    }

    private void setup(long maxTableCapacity) throws Exception {
        setup(maxTableCapacity, new QpackHuffmanDecoder());
    }

    private void setup(long maxTableCapacity, QpackHuffmanDecoder huffmanDecoder) throws Exception {
        parent = new EmbeddedQuicChannel(true);
        QpackAttributes attributes = new QpackAttributes(parent, false);
        setQpackAttributes(parent, attributes);

        dynamicTable = new QpackDecoderDynamicTable();
        QpackDecoder decoder = new QpackDecoder(maxTableCapacity, 0, dynamicTable,
                QpackDecoderStateSyncStrategy.ackEachInsert());

        encoderStream = (EmbeddedQuicStreamChannel) parent.createStream(QuicStreamType.UNIDIRECTIONAL,
                new QpackEncoderHandler(maxTableCapacity, decoder, huffmanDecoder)).get();
        attributes.encoderStream(encoderStream);

        decoderStream = (EmbeddedQuicStreamChannel) parent.createStream(QuicStreamType.UNIDIRECTIONAL,
                new QpackDecoderHandler(new QpackEncoder(QpackSensitivityDetector.NEVER_SENSITIVE))).get();
        attributes.decoderStream(decoderStream);
    }

    private void finishStreams(boolean decoderPendingMessage) {
        assertThat("Unexpected decoder stream message", decoderStream.finishAndReleaseAll(),
                is(decoderPendingMessage));
        assertThat("Unexpected encoder stream message", encoderStream.finishAndReleaseAll(), is(false));
    }
}
