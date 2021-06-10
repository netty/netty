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
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.incubator.codec.quic.QuicStreamType;
import io.netty.util.ReferenceCountUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;

import static io.netty.incubator.codec.http3.Http3CodecUtils.HTTP3_CANCEL_PUSH_FRAME_MAX_LEN;
import static io.netty.incubator.codec.http3.Http3CodecUtils.HTTP3_CANCEL_PUSH_FRAME_TYPE;
import static io.netty.incubator.codec.http3.Http3CodecUtils.HTTP3_GO_AWAY_FRAME_MAX_LEN;
import static io.netty.incubator.codec.http3.Http3CodecUtils.HTTP3_GO_AWAY_FRAME_TYPE;
import static io.netty.incubator.codec.http3.Http3CodecUtils.HTTP3_HEADERS_FRAME_TYPE;
import static io.netty.incubator.codec.http3.Http3CodecUtils.HTTP3_MAX_PUSH_ID_FRAME_TYPE;
import static io.netty.incubator.codec.http3.Http3CodecUtils.HTTP3_PUSH_PROMISE_FRAME_TYPE;
import static io.netty.incubator.codec.http3.Http3CodecUtils.HTTP3_SETTINGS_FRAME_MAX_LEN;
import static io.netty.incubator.codec.http3.Http3CodecUtils.HTTP3_SETTINGS_FRAME_TYPE;
import static io.netty.incubator.codec.http3.Http3CodecUtils.writeVariableLengthInteger;
import static io.netty.incubator.codec.http3.Http3TestUtils.assertException;
import static io.netty.incubator.codec.http3.Http3TestUtils.verifyClose;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(Parameterized.class)
public class Http3FrameCodecTest {
    private static final int MAX_HEADER_SIZE = 1024;
    private QpackEncoder encoder;
    private QpackDecoder decoder;
    private EmbeddedQuicStreamChannel encoderStream;
    private EmbeddedQuicStreamChannel decoderStream;
    private EmbeddedQuicChannel parent;
    private QpackEncoderHandler qpackEncoderHandler;
    private QpackDecoderHandler qpackDecoderHandler;

    @Parameterized.Parameters(name = "{index}: fragmented = {0}, maxBlockedStreams = {1}")
    public static Collection<Object[]> data() {
        return asList(
                new Object[]{true, 0},
                new Object[]{true, 100},
                new Object[]{false, 0},
                new Object[]{false, 100}
        );
    }

    private final boolean fragmented;
    private EmbeddedQuicStreamChannel codecChannel;
    private final int maxBlockedStreams;

    public Http3FrameCodecTest(boolean fragmented, int maxBlockedStreams) {
        this.fragmented = fragmented;
        this.maxBlockedStreams = maxBlockedStreams;
    }

    @Before
    public void setUp() throws Exception {
        parent = new EmbeddedQuicChannel();
        QpackAttributes qpackAttributes = new QpackAttributes(parent, false);
        Http3.setQpackAttributes(parent, qpackAttributes);
        final Http3SettingsFrame settings = new DefaultHttp3SettingsFrame();
        final long maxTableCapacity = 1024L;
        settings.put(Http3SettingsFrame.HTTP3_SETTINGS_QPACK_MAX_TABLE_CAPACITY, maxTableCapacity);
        settings.put(Http3SettingsFrame.HTTP3_SETTINGS_QPACK_BLOCKED_STREAMS, (long) maxBlockedStreams);
        decoder = new QpackDecoder(settings);
        decoder.setDynamicTableCapacity(1024);
        qpackEncoderHandler = new QpackEncoderHandler(maxTableCapacity, decoder);
        encoderStream = (EmbeddedQuicStreamChannel) parent.createStream(QuicStreamType.UNIDIRECTIONAL,
                new ChannelOutboundHandlerAdapter()).get();
        qpackAttributes.encoderStream(encoderStream);
        encoder = new QpackEncoder();
        encoder.configureDynamicTable(qpackAttributes, maxTableCapacity, maxBlockedStreams);
        final Object written = encoderStream.readOutbound();
        assertNotNull(written);
        ReferenceCountUtil.release(written);
        qpackDecoderHandler = new QpackDecoderHandler(encoder);
        decoderStream = (EmbeddedQuicStreamChannel) parent.createStream(QuicStreamType.UNIDIRECTIONAL,
                new ChannelOutboundHandlerAdapter()).get();
        qpackAttributes.decoderStream(decoderStream);
        codecChannel = (EmbeddedQuicStreamChannel) parent.createStream(QuicStreamType.BIDIRECTIONAL,
                new ChannelInitializer<QuicStreamChannel>() {
                    @Override
                    protected void initChannel(QuicStreamChannel ch) {
                        Http3RequestStreamEncodeStateValidator encStateValidator =
                                new Http3RequestStreamEncodeStateValidator();
                        Http3RequestStreamDecodeStateValidator decStateValidator =
                                new Http3RequestStreamDecodeStateValidator();
                        ch.pipeline().addLast(new Http3FrameCodec(Http3FrameTypeValidator.NO_VALIDATION, decoder,
                                MAX_HEADER_SIZE, encoder, encStateValidator, decStateValidator));
                        ch.pipeline().addLast(encStateValidator);
                        ch.pipeline().addLast(decStateValidator);
                    }
                }).get();
    }

    @After
    public void tearDown() {
        assertFalse(codecChannel.finish());
        assertFalse(decoderStream.finish());
        assertFalse(encoderStream.finish());
        assertFalse(parent.finish());
    }

    @Test
    public void testHttp3CancelPushFrame_63() throws Exception {
        testFrameEncodedAndDecoded(new DefaultHttp3CancelPushFrame(63));
    }

    @Test
    public void testHttp3CancelPushFrame_16383() throws Exception {
        testFrameEncodedAndDecoded(new DefaultHttp3CancelPushFrame(16383));
    }

    @Test
    public void testHttp3CancelPushFrame_1073741823() throws Exception {
        testFrameEncodedAndDecoded(new DefaultHttp3CancelPushFrame(1073741823));
    }

    @Test
    public void testHttp3CancelPushFrame_4611686018427387903() throws Exception {
        testFrameEncodedAndDecoded(new DefaultHttp3CancelPushFrame(4611686018427387903L));
    }

    @Test
    public void testHttp3DataFrame() throws Exception {
        byte[] bytes = new byte[1024];
        ThreadLocalRandom.current().nextBytes(bytes);
        final DefaultHttp3HeadersFrame headersFrame = new DefaultHttp3HeadersFrame(new DefaultHttp3Headers());
        addRequestHeaders(headersFrame.headers());
        testFrameEncodedAndDecoded(headersFrame);
        testFrameEncodedAndDecoded(new DefaultHttp3DataFrame(Unpooled.wrappedBuffer(bytes)));
    }

    @Test
    public void testHttp3GoAwayFrame_63() throws Exception {
        testFrameEncodedAndDecoded(new DefaultHttp3GoAwayFrame(63));
    }

    @Test
    public void testHttp3GoAwayFrame_16383() throws Exception {
        testFrameEncodedAndDecoded(new DefaultHttp3GoAwayFrame(16383));
    }

    @Test
    public void testHttp3GoAwayFrame_1073741823() throws Exception {
        testFrameEncodedAndDecoded(new DefaultHttp3GoAwayFrame(1073741823));
    }

    @Test
    public void testHttp3MaxPushIdFrame_63() throws Exception {
        testFrameEncodedAndDecoded(new DefaultHttp3MaxPushIdFrame(63));
    }

    @Test
    public void testHttp3MaxPushIdFrame_16383() throws Exception {
        testFrameEncodedAndDecoded(new DefaultHttp3MaxPushIdFrame(16383));
    }

    @Test
    public void testHttp3MaxPushIdFrame_1073741823() throws Exception {
        testFrameEncodedAndDecoded(new DefaultHttp3MaxPushIdFrame(1073741823));
    }

    @Test
    public void testHttp3SettingsFrame() throws Exception {
        Http3SettingsFrame settingsFrame = new DefaultHttp3SettingsFrame();
        settingsFrame.put(Http3SettingsFrame.HTTP3_SETTINGS_QPACK_MAX_TABLE_CAPACITY, 100L);
        settingsFrame.put(Http3SettingsFrame.HTTP3_SETTINGS_QPACK_BLOCKED_STREAMS, 1L);
        settingsFrame.put(Http3SettingsFrame.HTTP3_SETTINGS_MAX_FIELD_SECTION_SIZE, 128L);
        // Ensure we can encode and decode all sizes correctly.
        settingsFrame.put(63, 63L);
        settingsFrame.put(16383, 16383L);
        settingsFrame.put(1073741823, 1073741823L);
        settingsFrame.put(4611686018427387903L, 4611686018427387903L);
        testFrameEncodedAndDecoded(settingsFrame);
    }

    @Test
    public void testHttp3HeadersFrame() throws Exception {
        Http3HeadersFrame headersFrame = new DefaultHttp3HeadersFrame();
        addRequestHeaders(headersFrame.headers());
        testFrameEncodedAndDecoded(headersFrame);
    }

    @Test
    public void testHttp3HeadersFrameWithTrailers() throws Exception {
        Http3HeadersFrame headersFrame = new DefaultHttp3HeadersFrame();
        addRequestHeaders(headersFrame.headers());
        testFrameEncodedAndDecoded(headersFrame);

        final DefaultHttp3HeadersFrame trailers = new DefaultHttp3HeadersFrame();
        // add an extra header to block decoding if dynamic table enabled.
        trailers.headers().add("foo", "bar");
        testFrameEncodedAndDecoded(trailers);
    }

    @Test
    public void testHttp3HeadersFrameWithInvalidTrailers() throws Exception {
        Http3HeadersFrame headersFrame = new DefaultHttp3HeadersFrame();
        addRequestHeaders(headersFrame.headers());
        testFrameEncodedAndDecoded(headersFrame);

        final DefaultHttp3HeadersFrame trailer = new DefaultHttp3HeadersFrame();
        trailer.headers().add(":method", "GET");
        assertThrows(Http3HeadersValidationException.class, () -> testFrameEncodedAndDecoded(trailer));
    }

    @Test
    public void testHttp3PushPromiseFrame() throws Exception {
        Http3PushPromiseFrame pushPromiseFrame = new DefaultHttp3PushPromiseFrame(9);
        addRequestHeaders(pushPromiseFrame.headers());
        testFrameEncodedAndDecoded(pushPromiseFrame);
    }

    @Test
    public void testMultipleHttp3PushPromiseFrame() throws Exception {
        Http3PushPromiseFrame pushPromiseFrame = new DefaultHttp3PushPromiseFrame(9);
        addRequestHeaders(pushPromiseFrame.headers());
        testFrameEncodedAndDecoded(pushPromiseFrame);

        Http3PushPromiseFrame pushPromiseFrame2 = new DefaultHttp3PushPromiseFrame(10);
        addRequestHeaders(pushPromiseFrame2.headers());
        // add an extra header to block decoding if dynamic table enabled.
        pushPromiseFrame2.headers().add("foo", "bar");
        testFrameEncodedAndDecoded(pushPromiseFrame2);
    }

    @Test
    public void testMultipleHttp3PushPromiseFrameWithInvalidHeaders() throws Exception {
        Http3PushPromiseFrame pushPromiseFrame = new DefaultHttp3PushPromiseFrame(9);
        addRequestHeaders(pushPromiseFrame.headers());
        testFrameEncodedAndDecoded(pushPromiseFrame);

        Http3PushPromiseFrame pushPromiseFrame2 = new DefaultHttp3PushPromiseFrame(10);
        assertThrows(Http3HeadersValidationException.class, () -> testFrameEncodedAndDecoded(pushPromiseFrame2));
    }

    @Test
    public void testHttp3UnknownFrame() throws Exception {
        testFrameEncodedAndDecoded(new DefaultHttp3UnknownFrame(Http3CodecUtils.MIN_RESERVED_FRAME_TYPE,
                Unpooled.buffer().writeLong(8)));
    }

    // Reserved types that were used in HTTP/2 and should close the connection with an error
    @Test
    public void testDecodeReservedFrameType0x2() {
        testDecodeReservedFrameType(0x2);
    }

    @Test
    public void testDecodeReservedFrameType0x6() {
        testDecodeReservedFrameType(0x6);
    }

    @Test
    public void testDecodeReservedFrameType0x8() {
        testDecodeReservedFrameType(0x8);
    }

    @Test
    public void testDecodeReservedFrameType0x9() {
        testDecodeReservedFrameType(0x9);
    }

    private void testDecodeReservedFrameType(long type) {
        ByteBuf buffer = Unpooled.buffer();
        Http3CodecUtils.writeVariableLengthInteger(buffer, type);

        try {
            codecChannel.writeInbound(buffer);
        } catch (Exception e) {
            assertException(Http3ErrorCode.H3_FRAME_UNEXPECTED, e);
        }
        verifyClose(Http3ErrorCode.H3_FRAME_UNEXPECTED, parent);
        assertEquals(0, buffer.refCnt());
    }

    @Test
    public void testEncodeReservedFrameType0x2() {
        testEncodeReservedFrameType(0x2);
    }

    @Test
    public void testEncodeReservedFrameType0x6() {
        testEncodeReservedFrameType(0x6);
    }

    @Test
    public void testEncodeReservedFrameType0x8() {
        testEncodeReservedFrameType(0x8);
    }

    @Test
    public void testEncodeReservedFrameType0x9() {
        testEncodeReservedFrameType(0x9);
    }

    private void testEncodeReservedFrameType(long type) {
        Http3UnknownFrame frame = mock(Http3UnknownFrame.class);
        when(frame.type()).thenReturn(type);
        when(frame.touch()).thenReturn(frame);
        when(frame.touch(any())).thenReturn(frame);
        try {
            codecChannel.writeOutbound(frame);
        } catch (Exception e) {
            assertException(Http3ErrorCode.H3_FRAME_UNEXPECTED, e);
        }
        // should have released the frame as well
        verify(frame, times(1)).release();
        verifyClose(Http3ErrorCode.H3_FRAME_UNEXPECTED, parent);
    }

    // Reserved types that were used in HTTP/2 and should close the connection with an error
    @Test
    public void testDecodeReservedSettingsKey0x2() {
        testDecodeReservedSettingsKey(0x2);
    }

    @Test
    public void testDecodeReservedSettingsKey0x3() {
        testDecodeReservedSettingsKey(0x3);
    }

    @Test
    public void testDecodeReservedSettingsKey0x4() {
        testDecodeReservedSettingsKey(0x4);
    }

    @Test
    public void testDecodeReservedSettingsKey0x5() {
        testDecodeReservedSettingsKey(0x5);
    }

    private void testDecodeReservedSettingsKey(long key) {
        ByteBuf buffer = Unpooled.buffer();
        Http3CodecUtils.writeVariableLengthInteger(buffer, Http3CodecUtils.HTTP3_SETTINGS_FRAME_TYPE);
        Http3CodecUtils.writeVariableLengthInteger(buffer, 2);
        // Write the key and some random value... Both should be only 1 byte long each.
        Http3CodecUtils.writeVariableLengthInteger(buffer, key);
        Http3CodecUtils.writeVariableLengthInteger(buffer, 1);
        testDecodeInvalidSettings(buffer);
    }

    @Test
    public void testDecodeSettingsWithSameKey() {
        ByteBuf buffer = Unpooled.buffer();
        Http3CodecUtils.writeVariableLengthInteger(buffer, Http3CodecUtils.HTTP3_SETTINGS_FRAME_TYPE);
        Http3CodecUtils.writeVariableLengthInteger(buffer, 4);
        // Write the key and some random value... Both should be only 1 byte long each.
        Http3CodecUtils.writeVariableLengthInteger(buffer, Http3SettingsFrame.HTTP3_SETTINGS_MAX_FIELD_SECTION_SIZE);
        Http3CodecUtils.writeVariableLengthInteger(buffer, 1);
        Http3CodecUtils.writeVariableLengthInteger(buffer, Http3SettingsFrame.HTTP3_SETTINGS_MAX_FIELD_SECTION_SIZE);
        Http3CodecUtils.writeVariableLengthInteger(buffer, 1);

        testDecodeInvalidSettings(buffer);
    }

    private void testDecodeInvalidSettings(ByteBuf buffer) {
        try {
            codecChannel.writeInbound(buffer);
        } catch (Exception e) {
            assertException(Http3ErrorCode.H3_SETTINGS_ERROR, e);
        }
        verifyClose(Http3ErrorCode.H3_SETTINGS_ERROR, parent);
        assertEquals(0, buffer.refCnt());
    }

    @Test
    public void testEncodeReservedSettingsKey0x2() {
        testEncodeReservedSettingsKey(0x2);
    }

    @Test
    public void testEncodeReservedSettingsKey0x3() {
        testEncodeReservedSettingsKey(0x3);
    }

    @Test
    public void testEncodeReservedSettingsKey0x4() {
        testEncodeReservedSettingsKey(0x4);
    }

    @Test
    public void testEncodeReservedSettingsKey0x5() {
        testEncodeReservedSettingsKey(0x5);
    }

    private void testEncodeReservedSettingsKey(long key) {
        Http3SettingsFrame frame = mock(Http3SettingsFrame.class);
        when(frame.iterator()).thenReturn(Collections.singletonMap(key, 0L).entrySet().iterator());
        try {
            codecChannel.writeOutbound(frame);
        } catch (Exception e) {
            assertException(Http3ErrorCode.H3_SETTINGS_ERROR, e);
        }
        verifyClose(Http3ErrorCode.H3_SETTINGS_ERROR, parent);
    }

    private static void addPseudoRequestHeaders(Http3Headers headers) {
        headers.add(":authority", "netty.quic"); // name only
        headers.add(":path", "/"); // name & value
        headers.add(":method", "GET"); // name & value with few options per name
        headers.add(":scheme", "https");
    }

    private static void addRequestHeaders(Http3Headers headers) {
        addPseudoRequestHeaders(headers);
        headers.add("x-qpack-draft", "19");
    }

    private void testFrameEncodedAndDecoded(Http3Frame frame) throws Exception {
        boolean isDataFrame = frame instanceof Http3DataFrame;
        assertTrue(codecChannel.writeOutbound(retainAndDuplicate(frame)));
        ByteBuf buffer = codecChannel.readOutbound();
        if (fragmented) {
            try {
                do {
                    ByteBuf slice = buffer.readRetainedSlice(
                            ThreadLocalRandom.current().nextInt(buffer.readableBytes() + 1));
                    boolean producedData = codecChannel.writeInbound(slice);
                    if (!isDataFrame) {
                        if (buffer.isReadable() || (frameContainsHeaders(frame) && maxBlockedStreams > 0)) {
                            assertFalse(producedData);
                        } else {
                            assertTrue(producedData);
                        }
                    }
                } while (buffer.isReadable());
            } catch (Exception e) {
                if (isDataFrame) {
                    ReferenceCountUtil.release(frame);
                }
                throw e;
            } finally {
                buffer.release();
            }
        } else {
            if (maxBlockedStreams > 0 && frameContainsHeaders(frame)) {
                assertFalse(codecChannel.writeInbound(buffer));
            } else {
                assertTrue(codecChannel.writeInbound(buffer));
            }
        }

        relayQPACKEncoderInstructions();

        final Http3Frame actualFrame;
        if (isDataFrame) {
            CompositeByteBuf composite = Unpooled.compositeBuffer();
            for (;;) {
                Http3DataFrame dataFrame = codecChannel.readInbound();
                if (dataFrame == null) {
                    break;
                }
                composite.addComponent(true, dataFrame.content());
            }
            actualFrame = new DefaultHttp3DataFrame(composite);
        } else {
            actualFrame = codecChannel.readInbound();
        }
        Http3TestUtils.assertFrameEquals(frame, actualFrame);
    }

    private static Http3Frame retainAndDuplicate(Http3Frame frame) {
        if (frame instanceof ByteBufHolder) {
            return (Http3Frame) ((ByteBufHolder) frame).retainedDuplicate();
        }
        return frame;
    }

    @Test
    public void testMultipleFramesEncodedAndDecodedInOneBufferHeaders() throws Exception {
        Http3HeadersFrame headersFrame = new DefaultHttp3HeadersFrame();
        addRequestHeaders(headersFrame.headers());
        testMultipleFramesEncodedAndDecodedInOneBuffer(headersFrame,
                new DefaultHttp3DataFrame(Unpooled.buffer().writeLong(1)));
    }

    @Test
    public void testMultipleFramesEncodedAndDecodedInOneBufferPushPromise() throws Exception {
        final DefaultHttp3HeadersFrame headersFrame = new DefaultHttp3HeadersFrame(new DefaultHttp3Headers());
        addPseudoRequestHeaders(headersFrame.headers());
        testFrameEncodedAndDecoded(headersFrame);

        Http3PushPromiseFrame pushPromiseFrame = new DefaultHttp3PushPromiseFrame(9);
        addRequestHeaders(pushPromiseFrame.headers());
        testMultipleFramesEncodedAndDecodedInOneBuffer(pushPromiseFrame,
                new DefaultHttp3DataFrame(Unpooled.buffer().writeLong(1)));
    }

    private void testMultipleFramesEncodedAndDecodedInOneBuffer(Http3Frame first, Http3Frame second) throws Exception {
        assertTrue(codecChannel.writeOutbound(retainAndDuplicate(first)));
        assertTrue(codecChannel.writeOutbound(retainAndDuplicate(second)));

        ByteBuf mergedBuffer = Unpooled.buffer();
        for (;;) {
            ByteBuf buffer = codecChannel.readOutbound();
            if (buffer == null) {
                break;
            }
            mergedBuffer.writeBytes(buffer);
            buffer.release();
        }

        if (maxBlockedStreams > 0 && (frameContainsHeaders(first) || frameContainsHeaders(second))) {
            assertFalse(codecChannel.writeInbound(mergedBuffer));
        } else {
            assertTrue(codecChannel.writeInbound(mergedBuffer));
        }
        relayQPACKEncoderInstructions();

        Http3Frame readFrame = codecChannel.readInbound();
        Http3TestUtils.assertFrameEquals(first, readFrame);
        readFrame = codecChannel.readInbound();
        Http3TestUtils.assertFrameEquals(second, readFrame);

        assertFalse(codecChannel.finish());
    }

    @Test
    public void testInvalidHttp3MaxPushIdFrame() {
        testInvalidHttp3Frame0(HTTP3_MAX_PUSH_ID_FRAME_TYPE,
                HTTP3_CANCEL_PUSH_FRAME_MAX_LEN + 1, Http3ErrorCode.H3_FRAME_ERROR);
    }

    @Test
    public void testInvalidHttp3GoAwayFrame() {
        testInvalidHttp3Frame0(HTTP3_GO_AWAY_FRAME_TYPE,
                HTTP3_GO_AWAY_FRAME_MAX_LEN + 1, Http3ErrorCode.H3_FRAME_ERROR);
    }

    @Test
    public void testInvalidHttp3SettingsFrame() {
        testInvalidHttp3Frame0(HTTP3_SETTINGS_FRAME_TYPE,
                HTTP3_SETTINGS_FRAME_MAX_LEN + 1, Http3ErrorCode.H3_EXCESSIVE_LOAD);
    }

    @Test
    public void testInvalidHttp3CancelPushFrame() {
        testInvalidHttp3Frame0(HTTP3_CANCEL_PUSH_FRAME_TYPE,
                HTTP3_CANCEL_PUSH_FRAME_MAX_LEN + 1, Http3ErrorCode.H3_FRAME_ERROR);
    }

    @Test
    public void testInvalidHttp3HeadersFrame() {
        testInvalidHttp3Frame0(HTTP3_HEADERS_FRAME_TYPE,
                MAX_HEADER_SIZE + 1, Http3ErrorCode.H3_EXCESSIVE_LOAD);
    }

    @Test
    public void testInvalidHttp3PushPromiseFrame() {
        testInvalidHttp3Frame0(HTTP3_PUSH_PROMISE_FRAME_TYPE,
                MAX_HEADER_SIZE + 9, Http3ErrorCode.H3_EXCESSIVE_LOAD);
    }

    @Test
    public void testSkipUnknown() {
        ByteBuf buffer = Unpooled.buffer();
        writeVariableLengthInteger(buffer, 4611686018427387903L);
        writeVariableLengthInteger(buffer, 10);
        buffer.writeZero(10);

        assertFalse(codecChannel.writeInbound(buffer));
    }

    private void testInvalidHttp3Frame0(int type, int length, Http3ErrorCode code) {
        ByteBuf buffer = Unpooled.buffer();
        writeVariableLengthInteger(buffer, type);
        writeVariableLengthInteger(buffer, length);

        try {
            codecChannel.writeInbound(buffer);
        } catch (Exception e) {
            assertException(code, e);
        }
        verifyClose(code, parent);
    }

    private void relayQPACKEncoderInstructions() throws Exception {
        Object msg;
        while ((msg = encoderStream.readOutbound()) != null) {
            qpackEncoderHandler.channelRead(encoderStream.pipeline().firstContext(), msg);
        }
        while ((msg = decoderStream.readOutbound()) != null) {
            qpackDecoderHandler.channelRead(decoderStream.pipeline().firstContext(), msg);
        }
    }

    private static boolean frameContainsHeaders(Http3Frame frame) {
        return frame instanceof Http3HeadersFrame || frame instanceof Http3PushPromiseFrame;
    }
}
