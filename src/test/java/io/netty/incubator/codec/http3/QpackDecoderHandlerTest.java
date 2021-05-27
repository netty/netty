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
package io.netty.incubator.codec.http3;

import io.netty.buffer.ByteBuf;
import io.netty.incubator.codec.quic.QuicStreamType;
import org.junit.After;
import org.junit.Test;

import java.util.function.Consumer;

import static io.netty.incubator.codec.http3.Http3.setQpackAttributes;
import static io.netty.incubator.codec.http3.Http3ErrorCode.QPACK_DECODER_STREAM_ERROR;
import static io.netty.incubator.codec.http3.Http3SettingsFrame.HTTP3_SETTINGS_QPACK_MAX_TABLE_CAPACITY;
import static io.netty.incubator.codec.http3.QpackUtil.encodePrefixedInteger;
import static java.lang.Math.floorDiv;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

public class QpackDecoderHandlerTest {
    private static final QpackHeaderField fooBar = new QpackHeaderField("foo", "bar");
    private final QpackEncoderDynamicTable dynamicTable = new QpackEncoderDynamicTable();
    private EmbeddedQuicChannel parent;
    private QpackEncoder encoder;
    private EmbeddedQuicStreamChannel decoderStream;
    private EmbeddedQuicStreamChannel encoderStream;
    private int maxEntries;
    private QpackAttributes attributes;

    @After
    public void tearDown() {
        assertFalse(encoderStream.finish());
        assertFalse(decoderStream.finish());
    }

    @Test
    public void sectionAckNoIncrement() throws Exception {
        setup(128L);
        encodeHeaders(headers -> headers.add(fooBar.name, fooBar.value));

        try {
            sendAckForStreamId(decoderStream.streamId());
            fail();
        } catch (Http3Exception e) {
            // expected
            assertThat(e.getCause(), instanceOf(QpackException.class));
        }
        Http3TestUtils.verifyClose(QPACK_DECODER_STREAM_ERROR, parent);
        finishStreams();
    }

    @Test
    public void sectionAck() throws Exception {
        setup(128L);
        encodeHeaders(headers -> headers.add(fooBar.name, fooBar.value));
        verifyRequiredInsertCount(1);
        sendInsertCountIncrement(1);
        verifyKnownReceivedCount(1);

        // Refer now to dynamic table
        encodeHeaders(headers -> headers.add(fooBar.name, fooBar.value));
        sendAckForStreamId(decoderStream.streamId());

        finishStreams();

        verifyRequiredInsertCount(1);
        verifyKnownReceivedCount(1);
    }

    @Test
    public void sectionAckUnknownStream() throws Exception {
        setup(128);
        try {
            sendAckForStreamId(1);
            fail();
        } catch (Http3Exception e) {
            // exepected
            assertThat(e.getCause(), instanceOf(QpackException.class));
        }
        Http3TestUtils.verifyClose(QPACK_DECODER_STREAM_ERROR, parent);
        finishStreams();
    }

    @Test
    public void sectionAckAlreadyAcked() throws Exception {
        setup(128);
        encodeHeaders(headers -> headers.add(fooBar.name, fooBar.value));
        sendInsertCountIncrement(1);
        // Refer now to dynamic table
        encodeHeaders(headers -> headers.add(fooBar.name, fooBar.value));
        sendAckForStreamId(decoderStream.streamId());

        try {
            sendAckForStreamId(decoderStream.streamId());
            fail();
        } catch (Http3Exception e) {
            // expected
            assertThat(e.getCause(), instanceOf(QpackException.class));
        }

        Http3TestUtils.verifyClose(QPACK_DECODER_STREAM_ERROR, parent);
        finishStreams();

        verifyRequiredInsertCount(1);
        verifyKnownReceivedCount(1);
    }

    @Test
    public void sectionAckMultiPending() throws Exception {
        setup(128L);
        encodeHeaders(headers -> headers.add(fooBar.name, fooBar.value));
        sendInsertCountIncrement(1);
        // Refer now to dynamic table
        encodeHeaders(headers -> headers.add(fooBar.name, fooBar.value));
        encodeHeaders(headers -> headers.add(fooBar.name, fooBar.value));

        sendAckForStreamId(decoderStream.streamId());
        sendAckForStreamId(decoderStream.streamId());

        finishStreams();

        verifyRequiredInsertCount(1);
        verifyKnownReceivedCount(1);
    }

    @Test
    public void sectionAckMultiPostAck() throws Exception {
        setup(128L);
        encodeHeaders(headers -> headers.add(fooBar.name, fooBar.value));
        sendInsertCountIncrement(1);
        // Refer now to dynamic table
        encodeHeaders(headers -> headers.add(fooBar.name, fooBar.value));
        sendAckForStreamId(decoderStream.streamId());

        encodeHeaders(headers -> headers.add(fooBar.name, fooBar.value));
        sendAckForStreamId(decoderStream.streamId());

        finishStreams();

        verifyRequiredInsertCount(1);
        verifyKnownReceivedCount(1);
    }

    @Test
    public void sectionAckCancelledStream() throws Exception {
        setup(128L);
        encodeHeaders(headers -> headers.add(fooBar.name, fooBar.value));
        sendInsertCountIncrement(1);
        // Refer now to dynamic table
        encodeHeaders(headers -> headers.add(fooBar.name, fooBar.value));

        sendStreamCancellation(decoderStream.streamId());

        try {
            sendAckForStreamId(decoderStream.streamId());
            fail();
        } catch (Http3Exception e) {
            // expected
            assertThat(e.getCause(), instanceOf(QpackException.class));
        }
        Http3TestUtils.verifyClose(QPACK_DECODER_STREAM_ERROR, parent);
        finishStreams();
    }

    @Test
    public void splitBufferForSectionAck() throws Exception {
        setup(128);
        encodeHeaders(headers -> headers.add(fooBar.name, fooBar.value));
        verifyRequiredInsertCount(1);
        sendInsertCountIncrement(1);
        verifyKnownReceivedCount(1);

        // Refer now to dynamic table
        encodeHeaders(headers -> headers.add(fooBar.name, fooBar.value));
        final ByteBuf buf = encodeSectionAck(decoderStream.streamId());
        try {
            while (buf.isReadable()) {
                assertFalse(decoderStream.writeInbound(buf.readBytes(1)));
            }
        } finally {
            buf.release();
        }
        finishStreams();
    }

    @Test
    public void splitBufferForInsertCountIncrement() throws Exception {
        setup(128);
        encodeHeaders(headers -> headers.add(fooBar.name, fooBar.value));
        verifyRequiredInsertCount(1);
        final ByteBuf buf = encodeInsertCountIncrement(1);
        try {
            while (buf.isReadable()) {
                assertFalse(decoderStream.writeInbound(buf.readBytes(1)));
            }
        } finally {
            buf.release();
        }
        verifyKnownReceivedCount(1);
        finishStreams();
    }

    @Test
    public void splitBufferForStreamCancellation() throws Exception {
        setup(128);
        encodeHeaders(headers -> headers.add(fooBar.name, fooBar.value));
        verifyRequiredInsertCount(1);
        final ByteBuf buf = encodeStreamCancellation(decoderStream.streamId());
        try {
            while (buf.isReadable()) {
                assertFalse(decoderStream.writeInbound(buf.readBytes(1)));
            }
        } finally {
            buf.release();
        }
        finishStreams();
    }

    @Test
    public void streamCancel() throws Exception {
        setup(128);
        encodeHeaders(headers -> headers.add(fooBar.name, fooBar.value));
        verifyRequiredInsertCount(1);
        sendInsertCountIncrement(1);
        verifyKnownReceivedCount(1);

        // Refer now to dynamic table
        encodeHeaders(headers -> headers.add(fooBar.name, fooBar.value));
        sendStreamCancellation(decoderStream.streamId());
        verifyRequiredInsertCount(1);
        verifyKnownReceivedCount(1);
        finishStreams();
    }

    @Test
    public void streamCancelUnknownStream() throws Exception {
        setup(128);
        sendStreamCancellation(decoderStream.streamId());
        verifyRequiredInsertCount(0);
        verifyKnownReceivedCount(0);
        finishStreams();
    }

    @Test
    public void invalidIncrement() throws Exception {
        setup(128);
        try {
            sendInsertCountIncrement(2);
            fail();
        } catch (Http3Exception e) {
            // expected
            assertThat(e.getCause(), instanceOf(QpackException.class));
        }
        Http3TestUtils.verifyClose(QPACK_DECODER_STREAM_ERROR, parent);
        finishStreams();
    }

    private void sendAckForStreamId(long streamId) throws Http3Exception {
        assertFalse(decoderStream.writeInbound(encodeSectionAck(streamId)));
    }

    private ByteBuf encodeSectionAck(long streamId) {
        final ByteBuf ack = decoderStream.alloc().buffer();
        // https://quicwg.org/base-drafts/draft-ietf-quic-qpack.html#name-section-acknowledgment
        //   0   1   2   3   4   5   6   7
        // +---+---+---+---+---+---+---+---+
        // | 1 |      Stream ID (7+)       |
        // +---+---------------------------+
        encodePrefixedInteger(ack, (byte) 0b1000_0000, 7, streamId);
        return ack;
    }

    private void sendInsertCountIncrement(long increment) throws Http3Exception {
        assertFalse(decoderStream.writeInbound(encodeInsertCountIncrement(increment)));
    }

    private ByteBuf encodeInsertCountIncrement(long increment) {
        final ByteBuf incr = decoderStream.alloc().buffer();
        // https://quicwg.org/base-drafts/draft-ietf-quic-qpack.html#name-insert-count-increment
        //   0   1   2   3   4   5   6   7
        // +---+---+---+---+---+---+---+---+
        // | 0 | 0 |     Increment (6+)    |
        // +---+---+-----------------------+
        encodePrefixedInteger(incr, (byte) 0b0000_0000, 6, increment);
        return incr;
    }

    private void sendStreamCancellation(long streamId) {
        assertFalse(decoderStream.writeInbound(encodeStreamCancellation(streamId)));
    }

    private ByteBuf encodeStreamCancellation(long streamId) {
        final ByteBuf incr = decoderStream.alloc().buffer();
        // https://quicwg.org/base-drafts/draft-ietf-quic-qpack.html#name-stream-cancellation
        //   0   1   2   3   4   5   6   7
        // +---+---+---+---+---+---+---+---+
        // | 0 | 1 |     Stream ID (6+)    |
        // +---+---+-----------------------+
        encodePrefixedInteger(incr, (byte) 0b0100_0000, 6, streamId);
        return incr;
    }

    private void encodeHeaders(Consumer<Http3Headers> headersUpdater) {
        Http3Headers headers = new DefaultHttp3Headers();
        headersUpdater.accept(headers);
        final ByteBuf buf = decoderStream.alloc().buffer();
        try {
            encoder.encodeHeaders(attributes, buf, decoderStream.alloc(), decoderStream.streamId(), headers);
        } finally {
            buf.release();
        }
    }

    private void setup(long tableCapacity) throws Exception {
        maxEntries = Math.toIntExact(floorDiv(tableCapacity, 32));
        parent = new EmbeddedQuicChannel();
        attributes = new QpackAttributes(parent, false);
        setQpackAttributes(parent, attributes);
        Http3SettingsFrame settings = new DefaultHttp3SettingsFrame();
        settings.put(HTTP3_SETTINGS_QPACK_MAX_TABLE_CAPACITY, tableCapacity);
        QpackDecoder decoder = new QpackDecoder(settings);
        encoderStream = (EmbeddedQuicStreamChannel) parent.createStream(QuicStreamType.UNIDIRECTIONAL,
                new QpackEncoderHandler(tableCapacity, decoder)).get();
        attributes.encoderStream(encoderStream);
        encoder = new QpackEncoder(dynamicTable);
        encoder.configureDynamicTable(attributes, tableCapacity, 0);
        decoderStream = (EmbeddedQuicStreamChannel) parent.createStream(QuicStreamType.UNIDIRECTIONAL,
                new QpackDecoderHandler(encoder)).get();
        attributes.decoderStream(decoderStream);
    }

    private void finishStreams() {
        assertThat("Unexpected decoder stream message", decoderStream.finishAndReleaseAll(), is(false));
        assertThat("Unexpected encoder stream message", encoderStream.finishAndReleaseAll(), is(true));
        assertThat("Unexpected parent stream message", parent.finishAndReleaseAll(), is(false));
    }

    private void verifyRequiredInsertCount(int insertCount) {
        assertThat("Unexpected dynamic table insert count.", dynamicTable.requiredInsertCount(),
                is(insertCount == 0 ? 0 : insertCount % maxEntries + 1));
    }

    private void verifyKnownReceivedCount(int receivedCount) {
        assertThat("Unexpected dynamic table known received count.", dynamicTable.knownReceivedCount(),
                is(receivedCount == 0 ? 0 : receivedCount % maxEntries + 1));
    }
}
